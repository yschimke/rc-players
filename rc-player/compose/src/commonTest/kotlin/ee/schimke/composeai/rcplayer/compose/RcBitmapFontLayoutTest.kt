package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.graphics.Path
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontGlyph
import ee.schimke.composeai.rcplayer.protocol.RcBitmapTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals

class RcBitmapFontLayoutTest {
  private val font =
    RcBitmapFontData(
      40,
      listOf(
        glyph("A", 1, left = 1, top = -2, right = 2, bottom = 1, width = 6, height = 8),
        glyph("AB", 3, left = 1, top = -2, right = 2, bottom = 1, width = 10, height = 8),
        glyph("B", 2, left = 2, top = -1, right = 1, bottom = 2, width = 5, height = 7),
        glyph(" ", -1, left = 2, right = 3),
      ),
      mapOf("AB" to (-2).toShort()),
    )

  @Test
  fun longestGlyphWinsAndUnknownTextResetsKerning() {
    assertEquals("AB", font.lookupGlyph("AB", 0)?.chars)

    val placements = layoutBitmapTextRun(font, "A?B", 10f, 20f, 1f)

    assertEquals(11f, placements[0].left)
    assertEquals(22f, placements[1].left)
  }

  @Test
  fun measureIncludesMarginsKerningAndTrailingSpacing() {
    val bounds = measureBitmapText(font, "AB", 3f)

    // The longest-match AB glyph is one element. AndroidX's string-pair kerning key has no
    // delimiter, so the "AB" pair also applies to a first glyph whose own chars are "AB".
    assertEquals(14f, bounds.right)
    assertEquals(-2f, bounds.top)
    assertEquals(7f, bounds.bottom)
  }

  @Test
  fun bitmapMeasurementPublishesAResolvableRuntimeFloat() {
    val measure =
      RcBitmapTextMeasure(
        70,
        41,
        40,
        RcBitmapTextMeasure.MEASURE_WIDTH,
        RcFloatWord.literal(3f),
      )
    val state =
      RcPlayerState(
        RcDocument(
          RcHeader(RcVersion(1, 0, 0), modern = false),
          listOf(RcTextData(41, "AB"), font, measure),
        )
      )

    applyBitmapTextMeasure(measure, state)

    assertEquals(14f, state.resolve(RcFloatWord(0x7fc00046)))
  }

  @Test
  fun anchoredPanUsesAndroidXMeasurementBeforeDrawingKerning() {
    val placements = layoutAnchoredBitmapText(font, "A B", 100f, 50f, 1f, -1f, 2f)

    assertEquals(73f, placements.first().left)
    assertEquals(40f, placements.first().top)
  }

  @Test
  fun pathPlacesGlyphCenterAtItsRunFractionAndRotatesToTangent() {
    val path =
      Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 100f)
      }

    val placement = layoutBitmapTextOnPath(font, "A", path, -3f, 0f).single()

    assertEquals(0f, placement.rotationCenter.x)
    assertEquals(44.444f, placement.rotationCenter.y, 0.001f)
    assertEquals(-3f, placement.left)
    assertEquals(39.444f, placement.top, 0.001f)
    assertEquals(90f, placement.angleDegrees, 0.001f)
  }

  private fun glyph(
    chars: String,
    bitmapId: Int,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    width: Int = 0,
    height: Int = 0,
  ): RcBitmapFontGlyph =
    RcBitmapFontGlyph(
      chars,
      bitmapId,
      left.toShort(),
      top.toShort(),
      right.toShort(),
      bottom.toShort(),
      width.toShort(),
      height.toShort(),
    )
}
