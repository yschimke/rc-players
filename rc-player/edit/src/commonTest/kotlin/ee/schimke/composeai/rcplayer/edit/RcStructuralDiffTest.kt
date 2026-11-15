package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcClickArea
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedActionValue
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcRemark
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcStructuralDiffTest {
  private val card: RcDocument =
    RcDocument(
      RcHeader(
        RcVersion(1, 1, 0),
        listOf(
          RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(200)),
          RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(100)),
        ),
      ),
      listOf(
        RcTextData(42, "Hello"),
        RcNamedVariable(43, RcNamedVariable.STRING_TYPE, "title"),
        RcFloatConstant(44, RcFloatWord.literal(1.5f)),
        RcBitmapData(
          45,
          1,
          1,
          RcBitmapData.TYPE_PNG,
          RcBitmapData.ENCODING_INLINE,
          byteArrayOf(1, 2, 3),
        ),
      ) + rootLayout(*box(10, RcColorConstant(46, 0xff00ff00.toInt())).toTypedArray()),
    )

  @Test
  fun textIsNestedByContainerWithoutEnds() {
    val text = card.toStructuralText()
    val lines = text.lines()
    assertEquals("header version=1.1.0", lines[0])
    assertEquals("  DOC_WIDTH=200", lines[1])
    assertContains(text, "\nRcTextData(id=42, text=\"Hello\")\n")
    assertContains(text, "\n    RcBoxLayout(componentId=10")
    assertContains(text, "\n        RcColorConstant(id=46")
    assertFalse("CONTAINER_END" in text, text)
  }

  @Test
  fun floatWordsPrintAsLiteralsOrReferences() {
    val text =
      document(
          RcFloatConstant(1, RcFloatWord.literal(2.5f)),
          RcFloatConstant(2, RcFloatWord(0x7fc00000 or 44)),
        )
        .toStructuralText()
    assertContains(text, "RcFloatConstant(id=1, value=2.5)")
    assertContains(text, "RcFloatConstant(id=2, value=@44)")
  }

  @Test
  fun embeddedBytesPrintAsSizeAndHashSoTheTextIsStable() {
    val again = RcDocumentCodec.decode(RcDocumentCodec.encode(card))
    assertEquals(card.toStructuralText(), again.toStructuralText())
    assertContains(card.toStructuralText(), "data=<3 bytes fnv1a:")
    assertFalse(Regex("""\[B@""").containsMatchIn(card.toStructuralText()))
  }

  @Test
  fun identicalDocumentsHaveNoDiff() {
    val diff = card.structuralDiff(RcDocumentCodec.decode(RcDocumentCodec.encode(card)))
    assertTrue(diff.isIdentical, diff.toString())
    assertEquals("identical\n", diff.toString())
  }

  @Test
  fun aChangedTextIsExplainedByContent() {
    val after =
      card.copy(
        operations =
          card.operations.map { if (it is RcTextData) RcTextData(it.id, "Goodbye") else it },
      )
    val diff = card.structuralDiff(after)
    assertContains(diff.summary, "text: added \"Goodbye\"; removed \"Hello\"")
    assertContains(
      diff.unified,
      "-RcTextData(id=42, text=\"Hello\")\n+RcTextData(id=42, text=\"Goodbye\")\n",
    )
  }

  @Test
  fun headerAndOperationCountsAreExplained() {
    val after =
      card.copy(
        header =
          card.header.copy(
            properties = listOf(RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(300))),
          ),
        operations =
          card.operations +
            RcTextData(99, "More") +
            RcNamedVariable(98, RcNamedVariable.COLOR_TYPE, "accent"),
      )
    val summary = card.structuralDiff(after).summary
    assertContains(summary, "header DOC_WIDTH: 200 -> 300")
    assertContains(summary, "header DOC_HEIGHT: removed (was 100)")
    assertContains(summary, "operations 13 -> 15: +1 DATA_TEXT, +1 NAMED_VARIABLE")
    assertContains(summary, "named value: added \"accent\" (color)")
  }

  @Test
  fun renumberedIdsAreReportedAsOnlyNumbers() {
    val after =
      card.copy(
        operations = card.operations.map { if (it is RcTextData) RcTextData(142, it.text) else it },
      )
    val diff = card.structuralDiff(after)
    assertEquals(1, diff.summary.size, diff.toString())
    assertContains(diff.summary.single(), "only ids differ")
  }

  @Test
  fun anInsertionEarlyIsNotShownAsRenumberingEverythingAfterIt() {
    // As a writer allocates them: one more text first shifts every later id by one.
    fun card(extra: Boolean) =
      document(
        *listOfNotNull(
            if (extra) RcTextData(42, "Subtitle") else null,
            RcTextData(if (extra) 43 else 42, "Title"),
            RcFloatConstant(
              if (extra) 44 else 43,
              RcFloatWord(0x7fc00000 or if (extra) 43 else 42),
            ),
          )
          .toTypedArray(),
      )
    val diff = card(false).structuralDiff(card(true))
    assertContains(diff.summary, "text: added \"Subtitle\"")
    assertContains(
      diff.summary.last(),
      "1 structural lines differ once ids are matched (5 as written)",
    )
    val changed = diff.unified.lines().filter { it.startsWith("+") || it.startsWith("-") }.drop(2)
    assertEquals(listOf("+RcTextData(id=42', text=\"Subtitle\")"), changed, diff.toString())
  }

  @Test
  fun stringsPrintQuotedAndAreNeverRewritten() {
    val a = document(RcRemark("RcFloatWord(bits=1065353216)"))
    val b = document(RcRemark("1.0"))
    assertContains(a.toStructuralText(), "RcRemark(text=\"RcFloatWord(bits=1065353216)\")")
    assertFalse(a.structuralDiff(b).isIdentical)
  }

  @Test
  fun idsInsideStringsAreContentNotIds() {
    listOf("id=42" to "id=43", "@42" to "@43", "ids=[42]" to "ids=[43]").forEach { (was, now) ->
      val diff =
        document(RcTextData(42, "x"), RcRemark(was))
          .structuralDiff(document(RcTextData(43, "x"), RcRemark(now)))
      assertTrue("+RcRemark(text=\"$now\")" in diff.unified, diff.toString())
      assertFalse(diff.summary.any { "only ids differ" in it }, diff.toString())
    }
  }

  @Test
  fun aSystemVariableInAnIdFieldIsNeverRenumbered() {
    // 5 and 6 are the window's width and height (RcSystemVariables), not allocated ids.
    val diff =
      document(RcHostNamedAction(42, RcHostNamedActionValue.FloatValue(5)))
        .structuralDiff(document(RcHostNamedAction(42, RcHostNamedActionValue.FloatValue(6))))
    assertFalse(diff.summary.any { "only ids differ" in it }, diff.toString())
    assertTrue("valueId=6" in diff.unified, diff.toString())
  }

  @Test
  fun aChangedClickAreaIdIsAChangeButItsReferencesStillMatch() {
    fun area(id: Int, text: Int) =
      document(
        RcTextData(text, "Tap"),
        RcClickArea(
          id,
          0,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(1f),
          RcFloatWord.literal(1f),
          text,
        ),
      )
    val changed = area(42, 50).structuralDiff(area(43, 50))
    assertFalse(changed.summary.any { "only ids differ" in it }, changed.toString())
    assertTrue("RcClickArea(id=43" in changed.unified, changed.toString())
    // The text it reads is renumbered: that alone is only ids.
    val renumbered = area(42, 50).structuralDiff(area(42, 51))
    assertTrue(renumbered.summary.any { "only ids differ" in it }, renumbered.toString())
  }

  @Test
  fun aChangedHostActionIsAChangeNotARenumbering() {
    val diff = document(RcHostAction(1)).structuralDiff(document(RcHostAction(2)))
    assertContains(diff.unified, "-RcHostAction(actionId=1)\n+RcHostAction(actionId=2)\n")
    assertFalse(diff.summary.any { "only ids differ" in it }, diff.toString())
  }

  @Test
  fun aLongDiffIsBoundedAndStillCorrect() {
    // Every line differs: past MAX_EDIT_DISTANCE the span is reported as replaced.
    val a = List(MAX_EDIT_DISTANCE + 10) { "a$it" }
    val b = List(MAX_EDIT_DISTANCE + 10) { "b$it" }
    val edits = diffLines(listOf("same") + a, listOf("same") + b)
    assertEquals(EditKind.Same, edits.first().kind)
    assertEquals(
      a,
      edits.filter { it.kind == EditKind.Removed }.map { (listOf("same") + a)[it.aIndex] },
    )
    assertEquals(
      b,
      edits.filter { it.kind == EditKind.Added }.map { (listOf("same") + b)[it.bIndex] },
    )
  }

  @Test
  fun hunksAreHeadedByTheirContainer() {
    val after =
      card.copy(
        operations =
          card.operations.map {
            if (it is RcColorConstant) RcColorConstant(it.id, 0xff0000ff.toInt()) else it
          },
      )
    val unified = card.structuralDiff(after, "a.rc", "b.rc").unified
    assertTrue(unified.startsWith("--- a.rc\n+++ b.rc\n@@ "), unified)
    assertContains(unified.lines()[2], "RcLayoutContent(componentId=-990)")
  }

  @Test
  fun lineDiffIsAShortestEditScript() {
    val a = listOf("a", "b", "c", "a", "b", "b", "a")
    val b = listOf("c", "b", "a", "b", "a", "c")
    val edits = diffLines(a, b)
    // Myers' example: D = 5.
    assertEquals(5, edits.count { it.kind != EditKind.Same })
    assertEquals(b, edits.filter { it.kind != EditKind.Removed }.map { b[it.bIndex] })
    assertEquals(a, edits.filter { it.kind != EditKind.Added }.map { a[it.aIndex] })
  }
}
