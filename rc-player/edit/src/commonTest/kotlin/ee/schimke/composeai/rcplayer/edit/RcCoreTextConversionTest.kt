package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.BooleanValue
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.FloatArrayValue
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.FloatValue
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.IntArrayValue
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty.IntValue
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcCoreTextConversionTest {
  private fun androidx(vararg operations: RcOperation) =
    RcDocument(
      RcHeader(
        RcVersion(1, 2, 0),
        properties =
          listOf(
            RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(200)),
            RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(100)),
            RcHeaderProperty(DOC_PROFILES, RcHeaderValue.IntValue(RcProfiles.ANDROIDX)),
          ),
      ),
      operations.toList(),
    )

  /** A `CoreText` component as AndroidX writes one: the text, its content wrapper, two ends. */
  private fun text(vararg properties: ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty) =
    arrayOf(
      RcCoreText(42, listOf(IntValue(1, 7), *properties)),
      RcLayoutContent(-7),
      END,
      END,
    )

  private fun f(value: Float) = RcFloatWord.literal(value)

  private fun RcDocument.layout() = operations.filterIsInstance<RcTextLayout>().single()

  @Test
  fun aCoreTextTheOlderLayoutCanSayIsConvertedFieldForField() {
    val adaptation =
      androidx(
          *text(
            IntValue(3, 0xff112233.toInt()),
            FloatValue(5, f(30f)),
            IntValue(6, 1),
            FloatValue(7, f(500f)),
            IntValue(8, 66),
            IntValue(9, 3),
            IntValue(10, 3),
            IntValue(11, 2),
          )
        )
        .adaptTo(RcTargetProfiles.WEAR_WIDGETS)

    assertTrue(adaptation.isPlayable, adaptation.violations.toString())
    assertEquals(emptyList(), adaptation.approximations)
    assertEquals(
      RcTextLayout(
        componentId = 7,
        animationId = -1,
        textId = 42,
        color = 0xff112233.toInt(),
        fontSize = f(30f),
        fontStyle = 1,
        fontWeight = f(500f),
        fontFamilyId = 66,
        textAlignAndFlags = 3,
        overflow = 3,
        maxLines = 2,
      ),
      assertRoundTrips(adaptation.document).layout(),
    )
  }

  @Test
  fun aThemeColourBecomesTheDynamicColourFlag() {
    val layout =
      androidx(*text(IntValue(4, 65))).adaptTo(RcTargetProfiles.WEAR_WIDGETS).document.layout()
    assertEquals(65, layout.color)
    assertEquals(RcTextLayout.FLAG_DYNAMIC_COLOR, layout.flags)
    assertEquals(1, layout.textAlign)
  }

  @Test
  fun aStyleChainIsAppliedAndItsStylesDroppedWhereTheTargetHasNone() {
    val base = RcTextStyle(listOf(IntValue(1, 5), FloatValue(5, f(40f))))
    val bold = RcTextStyle(listOf(IntValue(1, 6), IntValue(24, 5), FloatValue(7, f(700f))))
    val adapted =
      androidx(base, bold, *text(IntValue(24, 6))).adaptTo(RcTargetProfiles.WEAR_WIDGETS)

    assertTrue(adapted.isPlayable, adapted.violations.toString())
    assertTrue(adapted.document.operations.none { it is RcTextStyle })
    assertEquals(f(40f) to f(700f), adapted.document.layout().let { it.fontSize to it.fontWeight })
  }

  @Test
  fun typographicRefinementsConvertOnlyWhenApproximationIsAskedFor() {
    val document =
      androidx(
        *text(
          FloatValue(12, f(0.025f)),
          FloatValue(14, f(1.2f)),
          IntArrayValue(20, listOf(1)),
          FloatArrayValue(21, listOf(f(1f))),
        )
      )
    assertEquals(
      listOf(RcProfileViolation.Kind.UNSUPPORTED_OPERATION),
      document.adaptTo(RcTargetProfiles.WEAR_WIDGETS).violations.map { it.kind },
    )

    val approximated = document.adaptTo(RcTargetProfiles.WEAR_WIDGETS, approximateText = true)

    assertTrue(approximated.isPlayable, approximated.violations.toString())
    assertEquals(
      listOf(
        RcTextApproximation(
          componentId = 7,
          textId = 42,
          dropped = setOf("fontAxis", "fontAxisValues", "letterSpacing", "lineHeightMultiplier"),
        )
      ),
      approximated.approximations,
    )
  }

  @Test
  fun decorationAlwaysBlocksAConversion() {
    val adapted =
      androidx(*text(BooleanValue(18, true)))
        .adaptTo(RcTargetProfiles.WEAR_WIDGETS, approximateText = true)
    assertTrue(adapted.document.operations.any { it is RcCoreText })
    assertEquals(false, adapted.isPlayable)
  }

  @Test
  fun aTextThePlayersWouldStyleDifferentlyIsLeftAlone() {
    // AndroidX lets the style's 40 win over an explicit default; the CMP player keeps the 36.
    val style = RcTextStyle(listOf(IntValue(1, 5), FloatValue(5, f(40f))))
    val adapted =
      androidx(style, *text(IntValue(24, 5), FloatValue(5, f(36f))))
        .adaptTo(RcTargetProfiles.WEAR_WIDGETS)
    assertTrue(adapted.document.operations.any { it is RcCoreText })
  }

  @Test
  fun aStyleWhoseParentIsDeclaredLaterIsNotConverted() {
    // The player applies the child before it has loaded the parent, so it never inherits it.
    val child = RcTextStyle(listOf(IntValue(1, 6), IntValue(24, 5)))
    val parent = RcTextStyle(listOf(IntValue(1, 5), FloatValue(5, f(40f))))
    val adapted =
      androidx(child, *text(IntValue(24, 6)), parent).adaptTo(RcTargetProfiles.WEAR_WIDGETS)
    assertTrue(adapted.document.operations.any { it is RcCoreText })
  }
}
