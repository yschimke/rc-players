package ee.schimke.composeai.rcplayer.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcDecompileCommandsTest {
  private val corpus = File("../../scripts/rc-catalog-corpus/corpus")
  private val out = Files.createTempDirectory("rc-decompile").toFile()

  @Test
  fun toKotlinWritesAComposableNamedAfterTheFile() {
    val input = File(corpus, "text-body__ideal__default__compact.rc")
    val output = File(out, "TextBody.kt")
    assertEquals(0, decompileCommand(listOf(input.path, output.path), uid = false))
    val code = output.readText()
    assertContains(code, "fun TextBodyIdealDefaultCompact() {")
    assertContains(code, "RemoteText(")
  }

  @Test
  fun toUidWritesTheDesignAndItsImagesBesideIt() {
    val input = File(corpus, "titlecard__ideal__content-image__compact.rc")
    val output = File(out, "card.uid")
    assertEquals(
      0,
      decompileCommand(listOf("--density", "1", input.path, output.path), uid = true),
    )
    assertContains(output.readText(), "\"systemId\": \"remote-m3\"")
    val assets = File(out, "card.assets").listFiles().orEmpty()
    assertTrue(assets.any { it.name.endsWith(".png") }, assets.toList().toString())
  }

  @Test
  fun aMissingArgumentIsAUsageError() {
    assertEquals(2, decompileCommand(emptyList(), uid = false))
    assertEquals(2, decompileCommand(listOf("a.rc", "--density"), uid = true))
  }

  @Test
  fun functionNamesAreKotlinIdentifiers() {
    assertEquals("ButtonFilledDefault", functionName(File("button-filled__default.rc")))
    assertEquals("Document3dCube", functionName(File("3d-cube.rc")))
  }
}
