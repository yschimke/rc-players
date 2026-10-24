package ee.schimke.composeai.rcplayer.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
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

  @Test
  fun fillWindowFitsTheDocumentToTheWindow() =
    runSkikoComposeUiTest(size = Size(300f, 300f), density = Density(1f)) {
      val state = viewerState()
      setContent { RcViewer(state, onOpenRequest = {}) }
      val bounds = onNodeWithTag(RC_VIEWER_DOCUMENT_TAG).getBoundsInRoot()
      assertEquals(300.dp, bounds.width)
      assertTrue(bounds.height < 300.dp, "the toolbar and status bar take their share")
    }

  private fun viewerState() =
    RcViewerState(fixture, loadRcViewerDocument(fixture, downloadFonts = false))
}
