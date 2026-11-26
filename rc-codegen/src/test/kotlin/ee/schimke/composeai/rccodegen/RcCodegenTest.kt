package ee.schimke.composeai.rccodegen

import androidx.compose.remote.generator.RemoteSerializationCodeGenerator
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcCodegenTest {
  private val schema = RemoteSerializationCodeGenerator.loadBundledSchema()

  /** The repository root: Gradle runs tests from the module's directory. */
  private val root: Path = Path.of("").toAbsolutePath().parent

  private fun constants(): String =
    SwiftWireConstantsTarget(root.resolve(RcCodegenMain.MANIFEST))
      .generate(schema)
      .getValue(SwiftWireConstantsTarget.OUTPUT)

  @Test
  fun everyOpcodeTheManifestOrTheSchemaKnowsIsNamed() {
    val generated =
      Regex("""static let \w+ = (\d+)""")
        .findAll(constants().substringAfter("enum RemoteOpcode {").substringBefore("}"))
        .map { it.groupValues[1].toInt() }
        .toSet()
    val manifest =
      Files.readAllLines(root.resolve(RcCodegenMain.MANIFEST))
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.substringBefore("|").toInt() }
    val missing = (manifest + schema.operations.map { it.id }).toSet() - generated
    assertTrue("opcodes without a Swift constant: $missing", missing.isEmpty())
  }

  /**
   * The fork's schema, local patches included, passes upstream's own validation: what
   * `:compose:remote:remote-generator:updateGeneratedCode` runs in androidx-main. Its entry point
   * is `internal`, so it is reached by name.
   */
  @Test
  fun schemaPassesUpstreamValidation() {
    val updater = Class.forName("androidx.compose.remote.generator.AndroidXCodegenUpdater")
    updater.getMethod("main", Array<String>::class.java).invoke(null, arrayOf(root.toString()))
  }

  /** Every value the Swift writer needs comes from the schema: nothing is left by hand. */
  @Test
  fun schemaCarriesTheValuesThatWereHandWritten() {
    val swift = constants()
    for (namespace in
      listOf(
        "RemoteFontStyle",
        "RemoteModifierColorFlag",
        "RemoteBorderShape",
        "RemoteBorderWireVersion",
        "RemoteHeaderValueType",
        "RemoteHeaderVersion",
      )) {
      assertTrue("$namespace is not generated", "enum $namespace {" in swift)
    }
    assertTrue("static let noID = -1" in swift)
    assertTrue("static let magicNumber = 0x048C_0000" in swift)
  }

  @Test
  fun generationIsDeterministic() {
    assertEquals(constants(), constants())
  }

  @Test
  fun portableSwiftUsesNoFoundation() {
    val target = RcCodegenMain.allTargets(null).first { it.id == "swift-portable" }
    val swift = target.generate(schema).values.single()
    assertFalse("imports Foundation", "import Foundation" in swift)
    assertFalse("throws NSError", "NSError(" in swift)
    assertFalse("uses Data", Regex("""\bData\b""").containsMatchIn(swift))
    assertTrue("defines the typed error", "struct RemoteComposeWireError: Error" in swift)
  }

  @Test
  fun upstreamSwiftKeepsFoundation() {
    val target = RcCodegenMain.allTargets(null).first { it.id == "swift" }
    assertTrue("import Foundation" in target.generate(schema).values.single())
  }

  @Test
  fun targetIdsAreUnique() {
    val ids = RcCodegenMain.allTargets(null).map { it.id }
    assertEquals(ids.distinct(), ids)
  }

  @Test
  fun namesAreLowerCamel() {
    assertEquals("fillParentMaxWidth", lowerCamel("FILL_PARENT_MAX_WIDTH"))
    assertEquals("strokeWidth", lowerCamelFromPascal("StrokeWidth"))
    assertEquals("docWidth", lowerCamel("DOC_WIDTH"))
  }
}
