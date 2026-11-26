package ee.schimke.composeai.rccodegen

import androidx.compose.remote.generator.RemoteSerializationCodeGenerator
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcCodegenTest {
  private val schema = RemoteSerializationCodeGenerator.loadBundledSchema()

  /** The repository root: Gradle runs tests from the module's directory. */
  private val root: Path = Path.of("").toAbsolutePath().parent

  private fun swift(): String =
    RcCodegenMain.allTargets().first { it.id == "swift" }.generate(schema).values.single()

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

  /**
   * The schema's field conditions name every value they compare against: a number in one would be
   * copied into generated Swift as a wire magic number. Zero stays, as the test of a flag.
   */
  @Test
  fun conditionsNameEveryValue() {
    val conditions =
      (schema.operations.flatMap { it.fields } + schema.structs.values.flatMap { it.fields })
        .flatMap { field ->
          listOfNotNull(field.presentIf) +
            field.conditionalTypes.map { it.condition } +
            field.conditionalRoles.map { it.condition }
        }
    val literal = Regex("""(?<![\w.])(?:0x[0-9A-Fa-f]+|[1-9][0-9]*)(?![\w.])""")
    val offending = conditions.filter { literal.containsMatchIn(it) }
    assertTrue("conditions with bare numbers: $offending", offending.isEmpty())
    val names =
      Regex("""\b[A-Z][A-Z0-9_]+\b""").findAll(conditions.joinToString(" ")).map { it.value }
    val unknown = names.toSet() - schema.conditionConstants.keys
    assertTrue("conditions name unknown constants: $unknown", unknown.isEmpty())
  }

  /** Every value RcCreation writes comes from the schema, through the bindings: none by hand. */
  @Test
  fun bindingsCarryEveryValueTheWriterNeeds() {
    val swift = swift()
    for (declaration in
      listOf(
        "public static let noId: Int32 = -1",
        "public static let magicNumber: Int32 = 76283904",
        "public static let roundedRectangle: Int32 = 2",
        "public static let version1: Int32 = 1",
        "public static let visibilityGone: Int = 0",
        "public static let inlineValueShift: Int32 = 16",
        "public enum FloatExpressionOperator",
        "public enum IntegerExpressionOperator",
      )) {
      assertTrue("missing: $declaration", declaration in swift)
    }
  }

  @Test
  fun generationIsDeterministic() {
    assertEquals(swift(), swift())
  }

  @Test
  fun swiftUsesNoFoundation() {
    val target = RcCodegenMain.allTargets().first { it.id == "swift" }
    val swift = target.generate(schema).values.single()
    assertFalse("imports Foundation", "import Foundation" in swift)
    assertFalse("throws NSError", "NSError(" in swift)
    val code = swift.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
    assertFalse("uses Data", Regex("""\bData\b""").containsMatchIn(code))
    assertTrue("defines the typed error", "enum WireError: Error, Sendable, Hashable" in swift)
    assertFalse("untyped throws", Regex("""throws(?!\(WireError\))""").containsMatchIn(swift))
  }

  /**
   * The portable Swift bindings carry no wire magic numbers: every number other than 0 and 1 is in
   * a named definition (`static let`, an enum case) or an error code. AGENTS.md's rule for native
   * wire constants, held over the whole generated file.
   */
  @Test
  fun swiftNamesEveryNumber() {
    val target = RcCodegenMain.allTargets().first { it.id == "swift" }
    val number = Regex("""(?<![\w.])(0x[0-9A-Fa-f_]+|\d[\d_]*)(?![\w.])""")
    val offending =
      target.generate(schema).values.single().lines().withIndex().filter { (_, raw) ->
        val line = raw.trim()
        if (line.startsWith("//") || "#available(" in line || "static let" in line) {
          return@filter false
        }
        if (Regex("""case \w+ = -?\d+""").matches(line)) return@filter false
        val code =
          line.replace(Regex(""""(?:[^"\\]|\\.)*""""), "\"\"").replace(Regex("""code: \d+"""), "")
        number.findAll(code).any { it.value != "0" && it.value != "1" }
      }
    assertTrue(
      "numbers in generated Swift: ${offending.take(5).map { "${it.index + 1}: ${it.value.trim()}" }}",
      offending.isEmpty(),
    )
  }

  @Test
  fun swiftOperationsAreAClosedEnum() {
    val swift =
      RcCodegenMain.allTargets().first { it.id == "swift" }.generate(schema).values.single()
    assertTrue("public enum WireOperation: Sendable, Hashable" in swift)
    assertTrue("public var operations: [WireOperation]" in swift)
    assertFalse("existential operations", "[RemoteOperation]" in swift)
    assertFalse("SCREAMING_SNAKE document limits", "MAX_OPERATION_COUNT" in swift)
  }

  @Test
  fun targetIdsAreUnique() {
    val ids = RcCodegenMain.allTargets().map { it.id }
    assertEquals(ids.distinct(), ids)
  }
}
