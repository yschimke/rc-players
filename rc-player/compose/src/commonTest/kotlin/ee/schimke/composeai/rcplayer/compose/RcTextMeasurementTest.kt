package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RcTextMeasurementTest {
  @Test
  fun selectsEveryAndroidXTextAttributeMode() {
    val expected = listOf(30f, 12f, -2f, 28f, -4f, 8f, 7f)

    expected.forEachIndexed { type, value ->
      assertEquals(value, selectTextMeasurement(type, -2f, -4f, 28f, 8f, 7))
    }
  }

  @Test
  fun textMeasureDoesNotImplementTextLength() {
    assertNull(selectTextMeasurement(6, 0f, 0f, 0f, 0f, 7, supportsLength = false))
  }

  @Test
  fun unknownMeasurementModeLeavesDestinationUntouched() {
    assertNull(selectTextMeasurement(7, 0f, 0f, 0f, 0f, 0, supportsLength = true))
  }

  // A run whose ink sits inside its advance (side bearings) and inside the font's line extents.
  private fun bounds(flags: Int) =
    androidXTextBounds(
      flags,
      inkLeft = 1.4f,
      inkTop = -10.2f,
      inkRight = 20.3f,
      inkBottom = 0.4f,
      advance = 22.5f,
      ascent = -14.6f,
      descent = 3.7f,
    )

  @Test
  fun withoutFlagsMeasuresInkRoundedOutAsPaintGetTextBounds() {
    assertEquals(listOf(1f, -11f, 21f, 1f), bounds(0))
  }

  @Test
  fun fontHeightTakesRoundedAscentAndDescent() {
    assertEquals(listOf(1f, -15f, 21f, 4f), bounds(TEXT_MEASURE_FONT_HEIGHT))
  }

  @Test
  fun spacesMeasureTheAdvanceFromZero() {
    assertEquals(listOf(0f, -11f, 22.5f, 1f), bounds(TEXT_MEASURE_SPACES))
  }

  @Test
  fun monospaceWidthEndsAtTheAdvanceLessTheInkLeft() {
    assertEquals(listOf(1f, -11f, 21.5f, 1f), bounds(TEXT_MEASURE_MONOSPACE_WIDTH))
  }

  @Test
  fun spacesWinOverMonospaceWidth() {
    assertEquals(
      listOf(0f, -15f, 22.5f, 4f),
      bounds(TEXT_MEASURE_SPACES or TEXT_MEASURE_MONOSPACE_WIDTH or TEXT_MEASURE_FONT_HEIGHT),
    )
  }

  @Test
  fun inkIsMovedToTheBaseline() {
    assertEquals(Rect(1f, -30f, 20f, 2f), baselineRelativeInk(Rect(1f, 10f, 20f, 42f), 40f))
  }

  @Test
  fun noInkStaysAnEmptyRectangleAtTheOrigin() {
    assertEquals(Rect.Zero, baselineRelativeInk(Rect.Zero, 37.4f))
  }
}
