package ee.schimke.composeai.rcplayer.wasm

import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.join
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.rawRow
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.read
import ee.schimke.composeai.rcplayer.wasm.NamedValueRowsFixture.row
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Edge cases of [namedValuesFromRows], each fed the exact row `Main.kt`'s
 * `flattenNamedValuesFromLocation` writes for a given JSON element (checked by running that `js()`
 * block in Node). The browser half of the same inputs is `scripts/wasm-smoke`'s
 * `named-values-elements` case.
 */
class NamedValueRowsEdgeCaseTest {
  private val orangeRow = row("color", "WearM3.primary", "#FFFF8800")
  private val blueRow = row("color", "WearM3.primary", "#FF0000FF")
  private val orange = mapOf("USER:WearM3.primary" to RcNamedValue.Color(0xFFFF8800.toInt()))
  private val blue = mapOf("USER:WearM3.primary" to RcNamedValue.Color(0xFF0000FF.toInt()))

  @Test
  fun anElementThatIsNotAnObjectIsDroppedOnItsOwn() {
    // The JS writes no row for `5`, `"color"`, `[...]`, `true` or `{}` (no string `kind`); the
    // shapeless row the one-`try` version wrote for them is still dropped on its own.
    val shapeless = row("", "", "")
    assertEquals(orange, read(join(shapeless, orangeRow, shapeless)))
  }

  @Test
  fun aNestedObjectValueIsItsStringForm() {
    // `String({...})`, as the web player's `HostApi` stringifies a value: a colour drops, a string
    // takes the text literally.
    assertEquals(orange, read(join(orangeRow, row("color", "WearM3.primary", "[object Object]"))))
    assertEquals(
      mapOf("USER:label" to RcNamedValue.Text("[object Object]")),
      read(row("string", "label", "[object Object]")),
    )
  }

  @Test
  fun anArrayValueIsItsCommaJoinedForm() {
    // `String(["#FF0000FF"])` is the colour itself; `String([1, 2])` is `1,2`, not a float.
    assertEquals(blue, read(join(orangeRow, row("color", "WearM3.primary", "#FF0000FF"))))
    assertEquals(emptyMap(), read(row("float", "progress", "1,2")))
  }

  @Test
  fun aMissingValueIsTheEmptyString() {
    // `value.value == null ? ''`: no value at all overrides a string to "" and a bool to false,
    // and drops the kinds that do not parse "".
    assertEquals(mapOf("USER:label" to RcNamedValue.Text("")), read(row("string", "label", "")))
    assertEquals(mapOf("USER:on" to RcNamedValue.BooleanValue(false)), read(row("bool", "on", "")))
    for (kind in listOf("color", "float", "int", "long", "dp")) {
      assertEquals(orange, read(join(orangeRow, row(kind, "WearM3.primary", ""))), kind)
    }
  }

  @Test
  fun anEmptyNameIsDropped() {
    // The JS writes no row for a missing or non-string name; an empty one names no variable, while
    // the text `0` is a name like any other.
    assertEquals(orange, read(join(orangeRow, row("color", "", "#FF0000FF"))))
    assertEquals(mapOf("USER:0" to RcNamedValue.Text("x")), read(row("string", "0", "x")))
  }

  @Test
  fun unicodeNamesAndValuesRoundTripExactly() {
    val name = "😀ש́‮label"
    val value = "é 😀🏽 של‮"
    assertEquals(mapOf("USER:$name" to RcNamedValue.Text(value)), read(row("string", name, value)))
  }

  @Test
  fun namesAreNotUnicodeNormalised() {
    // Precomposed and decomposed é are two variables, as the document's own names are compared.
    val composed = row("string", "café", "a")
    val decomposed = row("string", "café", "b")
    assertEquals(
      mapOf("USER:café" to RcNamedValue.Text("a"), "USER:café" to RcNamedValue.Text("b")),
      read(join(composed, decomposed)),
    )
  }

  @Test
  fun aColourIsNotReadThroughNonWhitespaceMarks() {
    // U+200F (RLM) is not JavaScript whitespace; U+2028 is.
    assertEquals(orange, read(join(orangeRow, row("color", "WearM3.primary", "#FF0000FF‏"))))
    assertEquals(blue, read(join(orangeRow, row("color", "WearM3.primary", " #FF0000FF "))))
  }

  @Test
  fun veryLongValuesRoundTrip() {
    val long = "😀".repeat(100_000)
    assertEquals(
      mapOf("USER:${"n".repeat(5_000)}" to RcNamedValue.Text(long)),
      read(row("string", "n".repeat(5_000), long)),
    )
    assertEquals(blue, read(row("color", "WearM3.primary", " ".repeat(100_000) + "#FF0000FF")))
  }

  @Test
  fun aBadDuplicateBetweenTwoGoodOnesLeavesTheLastGoodOne() {
    val bad = row("color", "WearM3.primary", "#nope")
    assertEquals(orange, read(join(blueRow, bad, orangeRow)))
    assertEquals(orange, read(join(orangeRow, bad)))
    // A lone surrogate never becomes a row (the JS `try` skips it), nor does a shapeless one count.
    assertEquals(orange, read(join(blueRow, row("", "", ""), orangeRow)))
    assertEquals(blue, read(join(orangeRow, blueRow)))
  }

  @Test
  fun aKindNameOrValueIsDecodedExactlyOnce() {
    // JSON `"%63olor"` is written `%2563olor`: one decode gives `%63olor`, not `color`.
    assertEquals(orange, read(join(orangeRow, row("%63olor", "WearM3.primary", "#FF0000FF"))))
    // A name is kept as decoded once: `WearM3%2Eprimary` is its own (absent) variable.
    assertEquals(
      orange + ("USER:WearM3%2Eprimary" to RcNamedValue.Color(0xFF0000FF.toInt())),
      read(join(orangeRow, row("color", "WearM3%2Eprimary", "#FF0000FF"))),
    )
    assertEquals(orange, read(join(orangeRow, row("color", "WearM3.primary", "%23FF0000FF"))))
  }

  @Test
  fun malformedPercentSequencesAreLiteralText() {
    for (text in listOf("%", "%%%", "%E0%A4%A", "%uD800", "100%")) {
      assertEquals(mapOf("USER:t" to RcNamedValue.Text(text)), read(row("string", "t", text)), text)
    }
  }

  @Test
  fun aColourNumberWithAnExponentWrapsAsJavaScriptDoes() {
    // `String(1e21)` is `1e+21`; `1e21 | 0` is 0xDEA00000.
    assertEquals(
      mapOf("USER:WearM3.primary" to RcNamedValue.Color(0xDEA00000.toInt())),
      read(row("color", "WearM3.primary", "1e+21", isNumber = true)),
    )
  }

  @Test
  fun aRowThatIsNotPercentEncodingDropsOnlyItself() {
    // The KDoc promises every malformed row is dropped on its own. `Main.kt` always writes
    // `encodeURIComponent` output, so this row cannot come from the bundle today, but the function
    // takes any `String`: a decoder that throws on it must not take the good row down too.
    val notEncoded = rawRow("%", "x", "y", NAMED_VALUE_TEXT_MARK)
    assertEquals(orange, read(join(notEncoded, orangeRow)))
  }
}
