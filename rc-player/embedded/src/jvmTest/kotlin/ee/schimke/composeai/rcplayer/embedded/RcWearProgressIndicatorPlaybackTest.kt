package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.wear.material3.RemoteCircularProgressIndicator
import ee.schimke.composeai.rccreation.wear.material3.RemoteMaterialTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Wear Remote Material 3's circular progress indicator, played by the CMP player at the progress
 * values upstream's `8ca84206303` (alpha24) changes: empty, a sliver, half, nearly full, full.
 *
 * A full indicator is a closed ring: the gap shrinks with the track and is gone at progress 1, so
 * the pixel at the top of the ring, where the gap was, is drawn. With `RC_LAYOUT_EVIDENCE_DIR` set
 * it also writes the five frames, `renders/creation-alpha24/` in the refresh's pull request.
 */
class RcWearProgressIndicatorPlaybackTest {
  private fun play(progress: Float): ImageBitmap {
    val bytes = runBlocking {
      captureCommonRemoteDocument(
        RemoteCreationDisplayInfo(SIZE, SIZE, 160),
        profile = RcEmbeddedPlayerProfile,
      ) {
        RemoteMaterialTheme { RemoteCircularProgressIndicator(progress = progress.rf) }
      }
    }
    return playDocument(bytes, SIZE, SIZE)
  }

  @Test
  fun aFullIndicatorIsAClosedRing() {
    val frames = PROGRESS.map(::play)
    writeEvidence("wear-circular-progress-indicator", frames, scale = 2)

    val full = frames.last().toPixelMap()
    // The ring's top centre, half a stroke in from the edge: inside the gap until alpha24.
    val top = full[SIZE / 2, 2]
    assertTrue(top.alpha > 0.5f && top != Color.Transparent, "the top of a full ring is $top")
  }

  private companion object {
    const val SIZE = 120
    val PROGRESS = listOf(0f, 0.01f, 0.5f, 0.99f, 1f)
  }
}
