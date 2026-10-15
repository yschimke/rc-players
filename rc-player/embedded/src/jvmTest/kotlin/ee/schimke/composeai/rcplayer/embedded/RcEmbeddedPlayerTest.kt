package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rccreation.bridge.LocalRcHostBridge
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcEmbeddedPlayerTest {
  private val display = RemoteCreationDisplayInfo(100, 100, 160)

  /** Remote content whose background colour the host owns, through whichever bridge is current. */
  @Composable
  @RemoteComposable
  private fun Swatch(color: () -> Color) {
    val bridge = LocalRcHostBridge.current
    RemoteBox(RemoteModifier.fillMaxSize().background(bridge.color("swatch", color)))
  }

  private fun SkikoComposeUiTest.centre(): Color = onRoot().captureToImage().toPixelMap()[50, 50]

  private fun run(block: SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(Size(100f, 100f), Density(1f), block = block)

  @Test
  fun aBridgedValueReachesThePlayerWithoutANewDocument() = run {
    var swatch by mutableStateOf(Color.Red)
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        Swatch { swatch }
      }
    }
    waitForIdle()
    assertEquals(Color.Red, centre())
    assertEquals(1, documents.size)

    swatch = Color.Blue
    waitForIdle()

    assertEquals(Color.Blue, centre())
    // The value went through the player's named values: no recomposition of the remote content,
    // no render, no new document, so the player kept its state.
    assertEquals(1, documents.size)
  }

  @Test
  fun aStructuralEditProducesANewDocument() = run {
    var rows by mutableStateOf(1)
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        RemoteColumn(RemoteModifier.fillMaxSize()) {
          repeat(rows) { RemoteBox(RemoteModifier.fillMaxWidth().size(10.rdp)) }
        }
      }
    }
    waitForIdle()
    assertEquals(1, documents.size)

    rows = 3
    waitForIdle()

    assertEquals(2, documents.size)
    // Re-rendering the retained tree gives the same document a fresh capture would.
    val fresh = runBlocking {
      captureCommonRemoteDocument(display) {
        RemoteColumn(RemoteModifier.fillMaxSize()) {
          repeat(3) { RemoteBox(RemoteModifier.fillMaxWidth().size(10.rdp)) }
        }
      }
    }
    assertEquals(RcDocumentCodec.decode(fresh), documents.last())
  }

  @Test
  fun liveAndDocumentModesWriteTheSameDocument() = run {
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        Swatch { Color.Green }
      }
    }
    waitForIdle()

    // The default bridge is RcDocumentBridge: the self-contained document another process plays.
    val documentMode = runBlocking {
      captureCommonRemoteDocument(display) { Swatch { Color.Green } }
    }

    assertEquals(RcDocumentCodec.decode(documentMode), documents.single())
  }

  @Test
  fun aBridgedActionCallsItsHandler() = run {
    var clicks = 0
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp)) {
        val bridge = LocalRcHostBridge.current
        RemoteBox(
          RemoteModifier.fillMaxSize()
            .background(Color.Gray.rc)
            .clickable(bridge.action("tapped") { clicks++ })
        )
      }
    }
    waitForIdle()

    onRoot().performTouchInput { click(center) }
    waitForIdle()

    assertEquals(1, clicks)
  }

  @Test
  fun hostContentIsRealComposeLaidOutByTheRemoteTree() = run {
    var label by mutableStateOf("first")
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        val bridge = LocalRcHostBridge.current
        RemoteColumn(RemoteModifier.fillMaxSize()) {
          bridge.HostContent(
            name = "label",
            modifier = RemoteModifier.fillMaxWidth().size(20.rdp),
            fallback = { RemoteBox(RemoteModifier.size(20.rdp)) },
          ) { modifier ->
            BasicText("host says $label", modifier)
          }
        }
      }
    }
    waitForIdle()
    onNodeWithText("host says first").assertExists()

    label = "second"
    waitForIdle()

    onNodeWithText("host says second").assertExists()
    assertEquals(1, documents.size)
  }
}
