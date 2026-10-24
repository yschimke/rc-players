package ee.schimke.composeai.rcplayer.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Image

@OptIn(ExperimentalTestApi::class)
class RcViewerLayoutTest {
  private val fixture =
    File(
      requireNotNull(System.getProperty("rc.fixtures")),
      "PressureGaugeCanvasContent-500x500.rc",
    )

  /** "Document size" keeps the declared geometry in a window smaller than the document. */
  @Test
  fun documentSizeOverflowsASmallWindowRatherThanShrinking() =
    runSkikoComposeUiTest(size = Size(300f, 300f), density = Density(1f)) {
      val state = viewerState().apply { sizing = RcViewerSizing.Document }
      setContent { RcViewer(state, onOpenRequest = {}) }
      // The laid-out size, which bounds-in-root would clip to the visible 300×300.
      val size = onNodeWithTag(RC_VIEWER_DOCUMENT_TAG).fetchSemanticsNode().size
      assertEquals(IntSize(500, 500), size)
    }

  /**
   * A document generated at density 2 shows in "Document size" exactly as `--render-png` writes it:
   * its declared pixel size, drawn at its own density rather than the window's.
   */
  @Test
  fun documentSizeMatchesTheHeadlessRenderAtTheDocumentsDensity() =
    runSkikoComposeUiTest(size = Size(600f, 400f), density = Density(1f)) {
      val file = File(fixture.parentFile, "TextRemoteButton-454x200.rc")
      val load = loadRcViewerDocument(file, network = false) as RcViewerLoad.Loaded
      assertEquals(2f, load.document.header.density, "the fixture this test is about")
      val state = RcViewerState(file, load).apply { sizing = RcViewerSizing.Document }
      setContent { RcViewer(state, onOpenRequest = {}) }

      val node = onNodeWithTag(RC_VIEWER_DOCUMENT_TAG)
      assertEquals(IntSize(454, 200), node.fetchSemanticsNode().size)
      val window = node.captureToImage().toPixelMap()
      val headless =
        Image.makeFromEncoded(renderRcPng(load.document, 454, 200, load.typefaces))
          .toComposeImageBitmap()
          .toPixelMap()
      // Only where the document paints opaquely: around it the window shows the viewer's
      // background and border, where the PNG is transparent.
      var painted = 0
      var differing = 0
      for (y in 1 until 199) for (x in 1 until 453) {
        if (headless[x, y].alpha < 1f) continue
        painted++
        if (window[x, y] != headless[x, y]) differing++
      }
      assertTrue(painted > 2_000, "the document paints something ($painted pixels)")
      assertTrue(differing <= painted / 100, "$differing of $painted painted pixels differ")
    }

  @Test
  fun fillWindowFitsTheDocumentToTheWindow() =
    runSkikoComposeUiTest(size = Size(300f, 300f), density = Density(1f)) {
      val state = viewerState()
      setContent { RcViewer(state, onOpenRequest = {}) }
      val bounds = onNodeWithTag(RC_VIEWER_DOCUMENT_TAG).getBoundsInRoot()
      assertEquals(300.dp, bounds.width)
      assertTrue(bounds.height < 300.dp, "the toolbar and status bar take their share")
    }

  private fun viewerState() = RcViewerState(fixture, loadRcViewerDocument(fixture, network = false))
}
