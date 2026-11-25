package ee.schimke.composeai.rcplayer.wasm

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RcHostParametersTest {
  @Test
  fun densityAcceptsPositiveDecimals() {
    assertEquals(2.625f, parseDensityParameter("2.625"))
    assertEquals(2f, parseDensityParameter("2"))
    assertEquals(0.5f, parseDensityParameter(".5"))
    assertEquals(1f, parseDensityParameter(" 1 "))
    assertEquals(15f, parseDensityParameter("1.5e1"))
    assertEquals(MAX_DENSITY_PARAMETER, parseDensityParameter("64"))
  }

  @Test
  fun densityRejectsEverythingElse() {
    listOf(
        null,
        "",
        " ",
        "0",
        "0.0",
        "-2",
        "+2",
        "NaN",
        "Infinity",
        "2.6f",
        "0x1p1",
        "2,625",
        "2.625dp",
        "1e999",
      )
      .forEach { raw -> assertNull(parseDensityParameter(raw), "density=$raw") }
  }

  @Test
  fun absentDensityKeepsThePlatformDensity() {
    assertEquals(1f, hostDensity(platformDensity = 1f, requested = null))
    assertEquals(2f, hostDensity(platformDensity = 2f, requested = null))
  }

  @Test
  fun requestedDensityIsMeasuredInCssPixels() {
    // A 454 px capture at density 2.625 is 172.95 dp wide. Shown in a 454 CSS px box, each dp must
    // span 2.625 CSS px: 2.625 device px at devicePixelRatio 1, 5.25 at devicePixelRatio 2.
    assertEquals(2.625f, hostDensity(platformDensity = 1f, requested = 2.625f))
    assertEquals(5.25f, hostDensity(platformDensity = 2f, requested = 2.625f))
  }

  @Test
  fun aDensityPastTheCeilingIsIgnored() {
    // Finite, but large enough that dp values times it overflow layout downstream.
    listOf("64.01", "1.5e3", "1e37", "3e38").forEach { raw ->
      assertNull(parseDensityParameter(raw), "density=$raw")
    }
  }

  @Test
  fun aDensityThatOverflowsWhenScaledFallsBackToThePlatformDensity() {
    // 3e38 parses as a finite float, but × devicePixelRatio 2 overflows to infinity.
    assertEquals(2f, hostDensity(platformDensity = 2f, requested = 3e38f))
  }

  private val bcp47 = Regex("[a-zA-Z]{2,8}(-[a-zA-Z0-9]{1,8})*")

  private fun isValid(tag: String) = bcp47.matches(tag)

  @Test
  fun validLanguageTagsPassThrough() {
    assertEquals(listOf("en-US", "de"), usableLanguageTags(listOf("en-US", "de"), ::isValid))
  }

  @Test
  fun posixDecorationsAreStripped() {
    assertEquals(
      listOf("en-US", "de-DE", "fr-FR"),
      usableLanguageTags(listOf("en-US@posix", "de_DE.UTF-8", "fr_FR.UTF-8@euro"), ::isValid),
    )
  }

  @Test
  fun fallsBackToThePrimarySubtag() {
    assertEquals(listOf("en"), usableLanguageTags(listOf("en-!!"), ::isValid))
  }

  @Test
  fun duplicatesCollapseInOrder() {
    assertEquals(
      listOf("en-US", "en"),
      usableLanguageTags(listOf("en-US", "en-US@posix", "en", "en_US"), ::isValid),
    )
  }

  @Test
  fun neverEmpty() {
    assertEquals(listOf(FALLBACK_LANGUAGE_TAG), usableLanguageTags(emptyList(), ::isValid))
    assertEquals(listOf(FALLBACK_LANGUAGE_TAG), usableLanguageTags(listOf("", "@", "1"), ::isValid))
  }

  @Test
  fun aBooleanIsTheBooleanValueTheIosControllerWrites() {
    assertEquals(RcNamedValue.BooleanValue(true), parseNamedValue("bool", "true"))
    assertEquals(RcNamedValue.BooleanValue(false), parseNamedValue("bool", "false"))
    // The documented embed contract: a boolean does not validate, anything but `true` is false.
    for (value in listOf("1", "TRUE", "yes", "")) {
      assertEquals(RcNamedValue.BooleanValue(false), parseNamedValue("bool", value), value)
    }
  }

  @Test
  fun aBooleanStillReachesTheDocumentAsTheInteger1Or0() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(RcNamedVariable(50, RcNamedVariable.INT_TYPE, "USER:enabled")),
      )
    fun applied(value: String): Int? {
      val namedValue = parseNamedValue("bool", value)!!
      return RcPlayerState(document, namedValues = mapOf("USER:enabled" to namedValue)).integer(50)
    }
    assertEquals(1, applied("true"))
    assertEquals(0, applied("false"))
  }

  @Test
  fun everyOtherKindParsesAsBefore() {
    assertEquals(RcNamedValue.Text("Ready"), parseNamedValue("string", "Ready"))
    assertEquals(RcNamedValue.FloatValue(0.75f), parseNamedValue("float", "0.75"))
    assertEquals(RcNamedValue.FloatValue(12f), parseNamedValue("dp", "12"))
    assertEquals(RcNamedValue.Integer(7), parseNamedValue("int", "7"))
    assertEquals(
      RcNamedValue.LongValue(1_700_000_000_000),
      parseNamedValue("long", "1700000000000"),
    )
    assertNull(parseNamedValue("int", "seven"))
    assertNull(parseNamedValue("long", "soon"))
    assertNull(parseNamedValue("unknown", "1"))
  }

  @Test
  fun aSixDigitColorIsOpaque() {
    assertEquals(0xFFFF8800.toInt(), parseArgbColor("#FF8800"))
    assertEquals(0xFFFF8800.toInt(), parseArgbColor("FF8800"))
    assertEquals(0xFF336699.toInt(), parseArgbColor("#336699"))
    // Surrounding whitespace is trimmed, as the web player's HostApi.parseColor trims it.
    assertEquals(0xFFFF8800.toInt(), parseArgbColor(" #FF8800\n"))
    assertEquals(RcNamedValue.Color(0xFFFF8800.toInt()), parseNamedValue("color", "#FF8800"))
  }

  @Test
  fun anEightDigitColorKeepsItsAlpha() {
    assertEquals(0xFFFF8800.toInt(), parseArgbColor("#FFFF8800"))
    assertEquals(0x80FF8800.toInt(), parseArgbColor("80ff8800"))
    assertEquals(0x00FF8800, parseArgbColor("#00FF8800"))
  }

  @Test
  fun anEightDigitColorIsAlphaFirstNotCss() {
    // RC_PLAYER_EMBED.md's example: CSS would read "#FF880080" as half-transparent orange.
    assertEquals(0xFF880080.toInt(), parseArgbColor("#FF880080"))
    assertEquals(0x80FF8800.toInt(), parseArgbColor("#80FF8800"))
  }

  @Test
  fun anyOtherColorSpellingIsDropped() {
    for (value in
      listOf("", "#", "F80", "#FF880", "#FF88001", "#FFFF88001", "+FF8800", "#GG8800", "orange")) {
      assertNull(parseArgbColor(value), value)
      assertNull(parseNamedValue("color", value), value)
    }
  }

  @Test
  fun aNumberColorIsTheArgbItself() {
    // What JavaScript's String() writes for 0xFFFF8800, its signed form, and 0xFF8800.
    assertEquals(0xFFFF8800.toInt(), argbFromNumber("4294936576"))
    assertEquals(0xFFFF8800.toInt(), argbFromNumber("-30720"))
    assertEquals(0x00FF8800, argbFromNumber("16746496"))
    assertEquals(0xFFFF8800.toInt(), argbFromNumber("4294936576.9"))
    assertEquals(
      RcNamedValue.Color(0xFFFF8800.toInt()),
      parseNamedValue("color", "4294936576", isNumber = true),
    )
  }

  @Test
  fun aNumberColorIsNotReadAsHexDigits() {
    // As a string, "16746496" is eight hex digits; as a number it is 0x00FF8800.
    assertEquals(
      0x16746496,
      parseNamedValue("color", "16746496").let { (it as RcNamedValue.Color).argb },
    )
    assertEquals(
      0x00FF8800,
      (parseNamedValue("color", "16746496", isNumber = true) as RcNamedValue.Color).argb,
    )
  }

  @Test
  fun aNumberColorAtTheEdgesOfThirtyTwoBitsIsKept() {
    assertEquals(Int.MIN_VALUE, argbFromNumber("-2147483648"))
    assertEquals(-1, argbFromNumber("4294967295"))
    assertEquals(Int.MIN_VALUE, argbFromNumber("-2147483648.5"))
  }

  @Test
  fun aNumberColorOutsideThirtyTwoBitsWrapsAsJavaScriptsToInt32() {
    // HostApi.parseColor's `v | 0`; each expected value is what Node prints for `v | 0`.
    for ((value, argb) in
      listOf(
        "8589934591" to -1, // 0x1FFFFFFFF
        "4294967296" to 0,
        "-2147483649" to Int.MAX_VALUE,
        "1e+21" to -559939584,
        "-1e+21" to 559939584,
        "1.7976931348623157e+308" to 0,
        "9007199254740994" to 2,
        "-0.5" to 0,
      )) {
      assertEquals(argb, argbFromNumber(value), value)
      assertEquals(
        RcNamedValue.Color(argb),
        parseNamedValue("color", value, isNumber = true),
        value,
      )
    }
  }

  @Test
  fun aNumberColorThatIsNotFiniteIsDropped() {
    // HostApi.parseColor falls through to the hex pattern for these, which they never match.
    for (value in listOf("NaN", "Infinity", "-Infinity", "")) {
      assertNull(argbFromNumber(value), value)
      assertNull(parseNamedValue("color", value, isNumber = true), value)
    }
  }

  @Test
  fun onlyColorReadsANumberDifferently() {
    for ((typeName, value) in
      listOf(
        "string" to "7",
        "float" to "0.75",
        "dp" to "12",
        "int" to "7",
        "bool" to "true",
        "long" to "17",
      )) {
      assertEquals(
        parseNamedValue(typeName, value),
        parseNamedValue(typeName, value, isNumber = true),
        typeName,
      )
    }
  }
}
