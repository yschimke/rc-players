package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RcColorThemeFallbacksTest {
  private val themed =
    RcColorTheme(
      outId = COLOR,
      colorGroupId = GROUP,
      lightModeIndex = 153,
      darkModeIndex = 150,
      lightModeFallback = LIGHT,
      darkModeFallback = DARK,
    )

  /** AndroidX `addThemedColor`'s shape, with the fallbacks inline. */
  private val sections: List<RcOperation> =
    listOf(
      RcTheme(RcTheme.DARK),
      RcLoopOperation(0, RcFloatWord.literal(0f), RcFloatWord.literal(1f), RcFloatWord.literal(1f)),
      RcColorExpression(
        COLOR,
        RcColorExpression.COLOR_COLOR_INTERPOLATE,
        DARK,
        LIGHT,
        0f.toRawBits(),
      ),
      END,
      RcTheme(RcTheme.LIGHT),
      RcLoopOperation(0, RcFloatWord.literal(0f), RcFloatWord.literal(1f), RcFloatWord.literal(1f)),
      RcColorExpression(
        COLOR,
        RcColorExpression.COLOR_COLOR_INTERPOLATE,
        DARK,
        LIGHT,
        1f.toRawBits(),
      ),
      END,
      RcTheme(RcTheme.UNSPECIFIED),
    )

  private val group = RcTextData(GROUP, "android")

  @Test
  fun aTopLevelThemedColorBecomesThemeSectionsInPlace() {
    val source = document(group, themed, *rootLayout(*box(20).toTypedArray()).toTypedArray())

    val result = assertRoundTrips(source.colorThemesAsFallbacks())

    assertEquals(
      listOf(group) + sections + rootLayout(*box(20).toTypedArray()),
      result.operations,
    )
  }

  @Test
  fun aThemedColorInsideALayoutMovesToTheTopLevel() {
    // AndroidX gates theme sections only among the document's top-level operations.
    val source = document(group, *rootLayout(themed, *box(20).toTypedArray()).toTypedArray())

    val result = assertRoundTrips(source.colorThemesAsFallbacks())

    assertEquals(listOf(group) + sections + rootLayout(*box(20).toTypedArray()), result.operations)
  }

  @Test
  fun theSectionsGiveEachThemeItsFallback() {
    val result = document(group, *rootLayout(themed).toTypedArray()).colorThemesAsFallbacks()
    for ((theme, expected) in listOf(RcTheme.DARK to DARK, RcTheme.LIGHT to LIGHT)) {
      val state = RcPlayerState(result)
      state.applyLayoutContentStateOperations(RcDocumentLinker.link(result).operations, theme)
      assertEquals(expected, state.color(COLOR), "theme $theme")
    }
  }

  @Test
  fun aFixedThemeIsThatThemesFallbackInPlace() {
    val source = document(group, *rootLayout(themed).toTypedArray())

    assertEquals(
      listOf(group) + rootLayout(RcColorConstant(COLOR, DARK)),
      source.colorThemesAsFallbacks(fixedTheme = RcTheme.DARK).operations,
    )
    assertEquals(
      listOf(group) + rootLayout(RcColorConstant(COLOR, LIGHT)),
      source.colorThemesAsFallbacks(fixedTheme = RcTheme.LIGHT).operations,
    )
  }

  @Test
  fun aThemedColorUnderAThemeMarkerOrSharingItsIdStays() {
    val sources =
      listOf(
        document(group, RcTheme(RcTheme.DARK), themed, RcTheme(RcTheme.UNSPECIFIED)),
        document(group, RcColorConstant(COLOR, 0), themed),
      )
    sources.forEach { assertSame(it, it.colorThemesAsFallbacks()) }
  }

  @Test
  fun adaptingApproximatesColorsOnlyWhenAsked() {
    val source = document(group, *rootLayout(themed).toTypedArray())

    val exact = source.adaptTo(RcTargetProfiles.WIDGETS_V6)
    assertTrue(exact.violations.any { it.opcode == RcOpcodes.COLOR_THEME })
    assertEquals(emptyList(), exact.colorFallbacks)

    val sectioned = source.adaptTo(RcTargetProfiles.WIDGETS_V6, approximateColors = true)
    assertTrue(sectioned.violations.none { it.opcode == RcOpcodes.COLOR_THEME })
    assertEquals(listOf(COLOR), sectioned.colorFallbacks)
    assertTrue(sectioned.document.operations.any { it is RcTheme })

    // Wear has no theme sections, and draws dark.
    val wear = source.adaptTo(RcTargetProfiles.WEAR_WIDGETS, approximateColors = true)
    assertTrue(wear.violations.none { it.opcode == RcOpcodes.COLOR_THEME })
    assertTrue(RcColorConstant(COLOR, DARK) in wear.document.operations)
    assertTrue(wear.document.operations.none { it is RcTheme })

    // A target with ColorTheme keeps the system palette.
    val androidx = source.adaptTo(RcTargetProfiles.ANDROIDX, approximateColors = true)
    assertTrue(themed in androidx.document.operations)
    assertTrue(androidx.document.operations.first() !is RcRootLayout)
  }

  private fun document(vararg operations: RcOperation): RcDocument =
    ee.schimke.composeai.rcplayer.edit.document(*operations)

  private companion object {
    const val COLOR = 42
    const val GROUP = 43
    const val LIGHT = 0xFFECE6F0.toInt()
    const val DARK = 0xFF2B2930.toInt()
  }
}
