package ee.schimke.composeai.rcplayer.desktop

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RcStructureCommandsTest {
  private val corpus = File("../../scripts/rc-catalog-corpus/corpus")
  private val enabled = File(corpus, "button-filled__ideal__default__compact.rc").path
  private val disabled = File(corpus, "button-filled__ideal__disabled__compact.rc").path

  private fun printed(command: () -> Int): Pair<Int, String> {
    val bytes = ByteArrayOutputStream()
    val previous = System.out
    System.setOut(PrintStream(bytes, true, Charsets.UTF_8))
    try {
      return command() to bytes.toString(Charsets.UTF_8)
    } finally {
      System.setOut(previous)
    }
  }

  @Test
  fun dumpPrintsTheSameTextEveryTime() {
    val (status, first) = printed { dumpCommand(listOf(enabled)) }
    assertEquals(0, status)
    assertContains(first, "\n    RcLayoutContent(componentId=-4)\n")
    assertEquals(first, printed { dumpCommand(listOf(enabled)) }.second)
  }

  @Test
  fun diffExitsLikeDiffAndExplainsTheChange() {
    assertEquals(0 to "identical\n", printed { diffCommand(listOf(enabled, enabled)) })
    val (status, text) = printed { diffCommand(listOf(enabled, disabled)) }
    assertEquals(1, status)
    assertContains(text, "* operations 73 -> 75: -1 MODIFIER_CLICK")
    assertContains(text, "removed \"USER:WearM3.onPrimary\" (color)")
    assertContains(text, "+        RcAccessibilitySemantics(")
    assertContains(text, "enabled=false")
  }

  @Test
  fun aMissingOrUnreadableFileIsStatusTwo() {
    assertEquals(2, diffCommand(listOf(enabled)))
    assertEquals(2, dumpCommand(listOf("does-not-exist.rc")))
  }
}
