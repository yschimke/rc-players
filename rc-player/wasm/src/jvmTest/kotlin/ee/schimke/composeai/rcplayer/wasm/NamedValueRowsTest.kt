package ee.schimke.composeai.rcplayer.wasm

import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
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
      read(listOf("%64p", "%6Eame", "%31", "s").joinToString("\u0000")),
    )
  }

  @Test
  fun aMalformedRowIsDroppedOnItsOwn() {
    val good = row("color", "WearM3.primary", "#FFFF8800")
    for (bad in
      listOf("", "color\u0000x", "a\u0000b\u0000c\u0000d\u0000e", row("nope", "x", "1"))) {
      assertEquals(mapOf(orange), read(join(bad, good)), "before: $bad")
      assertEquals(mapOf(orange), read(join(good, bad)), "after: $bad")
    }
  }

  @Test
  fun controlCharactersInAKindCannotSpellAnotherRow() {
    // Unescaped, this kind would split into a junk row and a `color` row overriding the colour
    // with blue. Escaped, it is one row whose kind is not a kind, dropped beside the good one.
    val forged = row("x\u0000a\u0000b\u0000s\u0001color", "WearM3.primary", "#FF0000FF")
    val good = row("color", "WearM3.primary", "#FFFF8800")
    assertEquals(mapOf(orange), read(join(good, forged)))
    assertEquals(mapOf(orange), read(join(forged, good)))
    assertEquals(emptyMap(), read(row("color\u0000\u0001", "WearM3.primary", "#FF0000FF")))
  }

  @Test
  fun theLaterOfTwoRowsForOneNameWins() {
    val blue = row("color", "WearM3.primary", "#FF0000FF")
    assertEquals(mapOf(orange), read(join(blue, row("color", "WearM3.primary", "#FFFF8800"))))
  }

  private fun read(rows: String) = namedValuesFromRows(rows, ::decodeUriComponent)

  private fun join(vararg rows: String) = rows.joinToString("\u0001")

  /**
   * A row as `flattenNamedValuesFromLocation` writes it, each text field escaped as
   * `encodeURIComponent`.
   */
  private fun row(kind: String, name: String, value: String, isNumber: Boolean = false) =
    listOf(
        encodeUriComponent(kind),
        encodeUriComponent(name),
        encodeUriComponent(value),
        if (isNumber) "n" else "s",
      )
      .joinToString("\u0000")
}
