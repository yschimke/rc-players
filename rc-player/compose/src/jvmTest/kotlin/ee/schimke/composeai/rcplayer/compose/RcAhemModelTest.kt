package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class RcAhemModelTest {
  @Test
  fun aWordLongerThanALineIsCutIntoLineSizedPieces() {
    // Six characters a line: "autosize" becomes "autosi" and a short "ze" that the next word joins
    // when it fits, as the reference's Ahem layout does.
    assertEquals(
      listOf("Very", "long", "autosi", "ze", "text"),
      rcAhemWrap("Very long autosize text", availableWidthPx = 100f, fontSizePx = 14.5f),
    )
    assertEquals(listOf("abcdef", "ghi"), rcAhemWrap("abcdefghi", 60f, 10f))
  }

  @Test
  fun anEllipsizedRunIsAsWideAsItsWidestKeptLine() {
    val text = "Ahem font wraps and truncates with ellipsis"
    // Twenty a line: "truncates with..." (17) is narrower than "Ahem font wraps and" (19).
    assertEquals(Size(304f, 32f), rcAhemTruncatedBlock(text, 320f, 16f, maxLines = 2))
    // Ten a line: "wraps and..." does not fit, so it is cut back to "wraps a...".
    assertEquals(Size(160f, 32f), rcAhemTruncatedBlock(text, 160f, 16f, maxLines = 2))
  }

  @Test
  fun aClippedRunKeepsItsLinesWithoutDots() {
    assertEquals(
      Size(58f, 29f),
      rcAhemTruncatedBlock(
        "Very long autosize text that exceeds small box bounds",
        100f,
        14.5f,
        maxLines = 2,
        ellipsis = false,
      ),
    )
  }
}
