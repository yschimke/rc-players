package ee.schimke.composeai.rcplayer.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pairing rule for a `CoreText` style's font-variation axes: property 20 carries the axis tags
 * (as text ids the caller has already resolved) and property 21 the values, positionally matched. A
 * document can leave either side short or unresolvable, so the rule is that an axis counts only
 * when both halves are present — anything else is dropped rather than paired with a neighbour's
 * value, which would silently apply the wrong instance.
 */
class RcFontVariationSettingsTest {
  @Test
  fun tagsAndValuesPairPositionally() {
    val settings =
      fontVariationSettings(axisTags = listOf("wght", "wdth"), axisValues = listOf(700f, 25f))

    assertEquals(listOf("wght" to 700f, "wdth" to 25f), settings.pairs())
  }

  @Test
  fun aStyleWithNoAxesResolvesToNoSettings() {
    assertNull(fontVariationSettings(axisTags = emptyList(), axisValues = emptyList()))
    // Values with no tags name nothing: there is no axis to apply them to.
    assertNull(fontVariationSettings(axisTags = emptyList(), axisValues = listOf(700f)))
  }

  @Test
  fun anUnresolvableTagDropsItsAxisAndKeepsTheRest() {
    // A tag id the document's text table has no entry for arrives as null.
    val settings =
      fontVariationSettings(axisTags = listOf(null, "wdth"), axisValues = listOf(700f, 151f))

    assertEquals(listOf("wdth" to 151f), settings.pairs())
  }

  @Test
  fun a_tag_without_a_value_is_dropped_rather_than_taking_the_next_one() {
    val settings =
      fontVariationSettings(axisTags = listOf("wght", "wdth"), axisValues = listOf(700f))

    assertEquals(listOf("wght" to 700f), settings.pairs())
  }

  @Test
  fun theStyleWeightBecomesAWghtAxisSoAVariableDefaultFaceRespondsToIt() {
    // `TextStyle.fontWeight` only picks between registered faces; a family carrying one variable
    // file registered at 400 renders every weight at 400 unless the axis is named.
    assertEquals(listOf("wght" to 500f), withWeightAxis(null, 500).pairs())
    assertEquals(
      listOf("wdth" to 25f, "wght" to 500f),
      withWeightAxis(fontVariationSettings(listOf("wdth"), listOf(25f)), 500).pairs(),
    )
  }

  @Test
  fun anExplicitWghtFromTheDocumentWinsOverTheStyleWeight() {
    // A specimen sweeping the axis names the value it wants; the style weight beside it is only
    // there so a non-variable fallback picks a face.
    val declared = fontVariationSettings(listOf("wght"), listOf(700f))

    assertEquals(listOf("wght" to 700f), withWeightAxis(declared, 400).pairs())
  }

  @Test
  fun theWeightAxisStaysInTheRangeAVariationValueAccepts() {
    assertEquals(listOf("wght" to 1f), withWeightAxis(null, 0).pairs())
    assertEquals(listOf("wght" to 1000f), withWeightAxis(null, 5000).pairs())
  }

  @Test
  fun anItalicStyleOnDeclaredAxesNamesTheItalAxis() {
    // androidx/androidx@530b47a2e: a variable instance names every axis it moves, so italic text
    // with only `wdth` declared rendered upright.
    val declared = fontVariationSettings(listOf("wdth"), listOf(25f))
    assertEquals(
      listOf("wdth" to 25f, "ital" to 1f),
      withItalicAxis(declared, italic = true).pairs(),
    )
    assertEquals(listOf("wdth" to 25f), withItalicAxis(declared, italic = false).pairs())
  }

  @Test
  fun italWithoutDeclaredAxesOrAlreadyDeclaredIsLeftAlone() {
    assertEquals(null, withItalicAxis(null, italic = true))
    val explicit = fontVariationSettings(listOf("ital"), listOf(0.5f))
    assertEquals(listOf("ital" to 0.5f), withItalicAxis(explicit, italic = true).pairs())
  }

  private fun RcFontVariations?.pairs(): List<Pair<String, Float>> =
    this?.axes.orEmpty().map { it.tag to it.value }
}

/**
 * Remote Compose writes a text's features into the same list as its axes; the OpenType tag rules
 * split them back apart — registered axes are five lowercase tags, foundry axes start uppercase.
 */
class RcFontFeatureSettingsTest {
  private fun RcFontVariations?.pairs(): List<Pair<String, Float>> =
    this?.axes.orEmpty().map { it.tag to it.value }

  private fun settings(vararg pairs: Pair<String, Float>) =
    fontVariationSettings(pairs.map { it.first }, pairs.map { it.second })

  @Test
  fun featuresAreTheLowercaseTagsThatAreNotRegisteredAxes() {
    listOf("tnum", "liga", "ss01", "cv11", "frac", "smcp").forEach {
      assertEquals(true, isFontFeatureTag(it), it)
    }
    listOf("wght", "wdth", "opsz", "ital", "slnt", "GRAD", "XOPQ", "YTUC", "tnu", "Tnum").forEach {
      assertEquals(false, isFontFeatureTag(it), it)
    }
  }

  @Test
  fun featuresBecomeTheStylesFeatureSettingsAndLeaveTheAxes() {
    val mixed = settings("wght" to 650f, "tnum" to 1f, "liga" to 0f, "GRAD" to -50f, "salt" to 2f)
    assertEquals("tnum, liga 0, salt 2", fontFeatureSettings(mixed))
    assertEquals(listOf("wght" to 650f, "GRAD" to -50f), mixed.withoutFeatures().pairs())
  }

  @Test
  fun italIsBothAnAxisAndAFeature() {
    val italic = settings("ital" to 1f, "tnum" to 1f)
    assertEquals("ital, tnum", fontFeatureSettings(italic))
    assertEquals(listOf("ital" to 1f), italic.withoutFeatures().pairs())
  }

  @Test
  fun aListOfOnlyAxesOrOnlyFeaturesLeavesTheOtherSideEmpty() {
    val axes = settings("wdth" to 25f)
    assertNull(fontFeatureSettings(axes))
    assertEquals(axes, axes.withoutFeatures())
    val features = settings("tnum" to 1f)
    assertEquals("tnum", fontFeatureSettings(features))
    assertNull(features.withoutFeatures())
    assertNull(fontFeatureSettings(null))
  }
}
