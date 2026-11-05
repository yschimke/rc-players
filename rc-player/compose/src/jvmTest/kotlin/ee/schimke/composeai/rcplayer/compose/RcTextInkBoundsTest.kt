package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The ink `measureTextOperation` reads for `TextMeasure` / `TextAttribute` are ink, as
 * `Paint.getTextBounds` measures it, not the line box: an `x` stands well inside its line.
 */
class RcTextInkBoundsTest {
  private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

  @Test
  fun inkOfLowercaseXSitsInsideTheLine() {
    val layout = measurer.measure("x", TextStyle(fontSize = 100.sp))
    val ink = rcTextInk(layout)
    val baseline = layout.firstBaseline
    val detail =
      "ink $ink, baseline $baseline, line ${layout.getLineTop(0)}..${layout.getLineBottom(0)}"
    assertTrue(ink.height > 0f, "x has ink: $detail")
    // An x-height glyph: its top is far below the line's, and it ends at the baseline.
    assertTrue(ink.top > layout.getLineTop(0) + 20f, "ink top below the line top: $detail")
    assertTrue(abs(ink.bottom - baseline) <= 1.5f, "ink ends at the baseline: $detail")
    assertTrue(ink.left >= 0f && ink.right <= layout.size.width + 1f, "inside the advance: $detail")
  }

  @Test
  fun descenderInkPassesTheBaseline() {
    val layout = measurer.measure("g", TextStyle(fontSize = 100.sp))
    val ink = rcTextInk(layout)
    assertTrue(ink.bottom > layout.firstBaseline + 10f, "g descends: $ink")
  }

  @Test
  fun blankTextHasNoInk() {
    assertEquals(Rect.Zero, rcTextInk(measurer.measure("   ", TextStyle(fontSize = 40.sp))))
  }

  @Test
  fun hugeTextIsMeasuredWithinTheRasterCap() {
    // 3000 px: uncapped, the padded raster would be ~9000 x 10000 px.
    val layout = measurer.measure("x", TextStyle(fontSize = 3000.sp))
    val ink = rcTextInk(layout)
    val baseline = layout.firstBaseline
    assertTrue(ink.height > 1000f && ink.top > layout.getLineTop(0) + 300f, "x ink: $ink")
    assertTrue(abs(ink.bottom - baseline) <= 20f, "ends at the baseline: $ink, $baseline")
  }

  @Test
  fun aColourChangeReusesTheMeasuredInk() {
    val red = measurer.measure("colour", TextStyle(fontSize = 37.sp, color = Color.Red))
    val blue = measurer.measure("colour", TextStyle(fontSize = 37.sp, color = Color.Blue))
    // Same object: the second lookup is a cache hit rather than another raster.
    assertSame(rcTextInk(red), rcTextInk(blue))
  }
}
