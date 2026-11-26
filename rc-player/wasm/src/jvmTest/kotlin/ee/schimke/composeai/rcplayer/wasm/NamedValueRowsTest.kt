package ee.schimke.composeai.rcplayer.wasm

import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.join
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.markedRow
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.rawRow
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.read
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.row
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Kotlin half of `?namedValues=`: the rows `Main.kt`'s `flattenNamedValuesFromLocation` writes,
 * read back, decoded by [decodeUriComponent] as the browser's `decodeURIComponent` would. The
 * JavaScript half (`JSON.parse`, the per-element checks, the encoding of every field) only runs in
 * a browser and is covered by `scripts/wasm-smoke`'s `named-values-elements` case.
 */
class NamedValueRowsTest {
  private val orange = "USER:WearM3.primary" to RcNamedValue.Color(0xFFFF8800.toInt())

  @Test
  fun noRowsAreNoOverrides() {
    assertEquals(emptyMap(), read(""))
  }

  @Test
  fun aWellFormedRowIsRead() {
    assertEquals(mapOf(orange), read(row("color", "WearM3.primary", "#FFFF8800")))
  }

  @Test
  fun everyFieldIsDecodedByTheSameRule() {
    // `kind` is encoded like `name` and `value`, so it is decoded like them too: `%64p` is `dp`.
    assertEquals(
      mapOf("USER:name" to RcNamedValue.FloatValue(1f)),
      read(
        rawRow("%64p", "%6Eame", "%31", NAMED_VALUE_TEXT_MARK),
      ),
    )
  }

  @Test
  fun aMalformedRowIsDroppedOnItsOwn() {
    val good = row("color", "WearM3.primary", "#FFFF8800")
    for (bad in
      listOf(
        "",
        rawRow("color", "x"),
        rawRow("a", "b", "c", "d", "e"),
        row("nope", "x", "1"),
      )) {
      assertEquals(mapOf(orange), read(join(bad, good)), "before: $bad")
      assertEquals(mapOf(orange), read(join(good, bad)), "after: $bad")
    }
  }

  @Test
  fun controlCharactersInAKindCannotSpellAnotherRow() {
    // Unescaped, this kind would split into a junk row and a `color` row overriding the colour
    // with blue. Escaped, it is one row whose kind is not a kind, dropped beside the good one.
    val forged =
      row(
        rawRow("x", "a", "b", NAMED_VALUE_TEXT_MARK) + NAMED_VALUE_ROW_SEPARATOR + "color",
        "WearM3.primary",
        "#FF0000FF",
      )
    val good = row("color", "WearM3.primary", "#FFFF8800")
    assertEquals(mapOf(orange), read(join(good, forged)))
    assertEquals(mapOf(orange), read(join(forged, good)))
    assertEquals(
      emptyMap(),
      read(
        row(
          "color$NAMED_VALUE_FIELD_SEPARATOR$NAMED_VALUE_ROW_SEPARATOR",
          "WearM3.primary",
          "#FF0000FF",
        ),
      ),
    )
  }

  @Test
  fun aNumberOrTextMarkIsRead() {
    // `color` is the one kind that reads the two differently: a number is ARGB as an integer.
    assertEquals(mapOf(orange), read(row("color", "WearM3.primary", "4294936576", isNumber = true)))
    assertEquals(mapOf(orange), read(row("color", "WearM3.primary", "#FFFF8800", isNumber = false)))
    // Not swapped: the number read as text is not a colour string, so that row is dropped.
    assertEquals(emptyMap(), read(row("color", "WearM3.primary", "4294936576", isNumber = false)))
  }

  @Test
  fun aRowWithAnUnknownOrEmptyMarkIsDroppedOnItsOwn() {
    val good = row("color", "WearM3.primary", "#FFFF8800")
    for (mark in listOf("x", "", "N", "ns")) {
      val bad = markedRow("color", "WearM3.primary", "#FF0000FF", mark)
      assertEquals(emptyMap(), read(bad), "alone: mark '$mark'")
      assertEquals(mapOf(orange), read(join(bad, good)), "before: mark '$mark'")
      assertEquals(mapOf(orange), read(join(good, bad)), "after: mark '$mark'")
    }
  }

  @Test
  fun aMixedArrayKeepsEveryRowButTheOneWithABadMark() {
    val label = row("string", "label", "Ready")
    val count = row("int", "count", "3", isNumber = true)
    val badMark = markedRow("color", "WearM3.primary", "#FF0000FF", "x")
    assertEquals(
      mapOf(
        "USER:label" to RcNamedValue.Text("Ready"),
        "USER:count" to RcNamedValue.Integer(3),
        orange,
      ),
      // Last, so a reader that kept it would override the orange with blue.
      read(join(label, row("color", "WearM3.primary", "#FFFF8800"), count, badMark)),
    )
  }

  /**
   * The row format, pinned: `scripts/wasm-smoke/smoke.mjs` spells these characters itself in its "a
   * kind that spells another row" case, so a change here must change it too, or that case stops
   * testing anything.
   */
  @Test
  fun theRowFormatIsTheOneTheSmokeRunForges() {
    assertEquals("\u0001", NAMED_VALUE_ROW_SEPARATOR)
    assertEquals("\u0000", NAMED_VALUE_FIELD_SEPARATOR)
    assertEquals(4, NAMED_VALUE_FIELD_COUNT)
    assertEquals("n", NAMED_VALUE_NUMBER_MARK)
    assertEquals("s", NAMED_VALUE_TEXT_MARK)
  }

  @Test
  fun theLaterOfTwoRowsForOneNameWins() {
    val blue = row("color", "WearM3.primary", "#FF0000FF")
    assertEquals(mapOf(orange), read(join(blue, row("color", "WearM3.primary", "#FFFF8800"))))
  }
}
