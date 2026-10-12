package ee.schimke.composeai.rcplayer.wasm

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
}
