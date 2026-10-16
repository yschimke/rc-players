package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.MaxExplicitComponentId
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.componentId
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rccreation.bridge.LocalRcHostBridge
import ee.schimke.composeai.rccreation.bridge.RcComponentIds
import ee.schimke.composeai.rcplayer.compose.LocalRcComponentCoordinates
import ee.schimke.composeai.rcplayer.compose.RcComponentCoordinatesListener
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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

  /**
   * The remote session composes and renders on its own dispatcher, which `waitForIdle` cannot see,
   * so a test waits for what the session reports instead.
   */
  private fun SkikoComposeUiTest.awaitDocuments(documents: List<RcDocument>, count: Int) {
    waitUntil(timeoutMillis = 5_000) { documents.size >= count }
    waitForIdle()
    assertEquals(count, documents.size)
  }

  /** Real time for work the session might still owe, before asserting it did none. */
  private fun SkikoComposeUiTest.settleOffThread() {
    Thread.sleep(250)
    waitForIdle()
  }

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
    awaitDocuments(documents, 1)
    assertEquals(Color.Red, centre())

    swatch = Color.Blue
    waitUntil { centre() == Color.Blue }
    // Time for a render the remote session might owe (it runs off the test's clock).
    settleOffThread()
    // The value went through the player's named values: no recomposition of the remote content,
    // no render, no new document, so the player kept its state.
    assertEquals(1, documents.size)
  }

  /**
   * Content under a new `key` that captures a plain value rebinds the same name. The player's
   * override has to follow the new reader, or it keeps painting the old value over the new
   * document's default.
   */
  @Test
  fun aRebindingToAPlainValueReachesThePlayer() = run {
    var host by mutableStateOf(Color.Red)
    setContent {
      val plain = host
      RcEmbeddedPlayer(display, Modifier.size(100.dp), key = plain) { Swatch { plain } }
    }
    waitUntil { centre() == Color.Red }

    host = Color.Blue
    waitUntil { centre() == Color.Blue }
  }

  /**
   * A bridged name the content stops declaring stops overriding the player: content that then
   * declares the same name directly plays its own default.
   */
  @Test
  fun aNameNoLongerBridgedPlaysTheDocumentsDefault() = run {
    var bridged by mutableStateOf(true)
    setContent {
      val declare = bridged
      RcEmbeddedPlayer(display, Modifier.size(100.dp), key = declare) {
        if (declare) Swatch { Color.Red }
        else
          RemoteBox(
            RemoteModifier.fillMaxSize()
              .background(RemoteColor.createNamedRemoteColor("swatch", Color.Blue))
          )
      }
    }
    waitUntil { centre() == Color.Red }

    bridged = false
    waitUntil { centre() == Color.Blue }
  }

  @Test
  fun explicitIdsStayBelowTheDocumentsOwn() {
    // The writer allocates from -2 (the root among them), composition from -1_000_000.
    assertFailsWith<IllegalArgumentException> { RemoteModifier.componentId(-2) }
    assertFailsWith<IllegalArgumentException> { RemoteModifier.componentId(-1_000_001) }
    assertFailsWith<IllegalArgumentException> { RcComponentIds<String>(first = -5) }
    assertEquals(MaxExplicitComponentId, RcComponentIds<String>().idFor("first"))
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
    awaitDocuments(documents, 1)

    rows = 3
    awaitDocuments(documents, 2)
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
    awaitDocuments(documents, 1)

    // The default bridge is RcDocumentBridge: the self-contained document another process plays.
    val documentMode = runBlocking {
      captureCommonRemoteDocument(display) { Swatch { Color.Green } }
    }

    assertEquals(RcDocumentCodec.decode(documentMode), documents.single())
  }

  @Test
  fun aBridgedActionCallsItsHandler() = run {
    var clicks = 0
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        val bridge = LocalRcHostBridge.current
        RemoteBox(
          RemoteModifier.fillMaxSize()
            .background(Color.Gray.rc)
            .clickable(bridge.action("tapped") { clicks++ })
        )
      }
    }
    awaitDocuments(documents, 1)

    onRoot().performTouchInput { click(center) }
    waitUntil { clicks == 1 }
  }

  /**
   * A declaration lasts as long as the call that makes it. Content under a new key that no longer
   * bridges an action leaves its name to the host, rather than a stale handler catching it.
   */
  @Test
  fun aDeclarationThatLeavesStopsProvidingItsName() = run {
    var bridged by mutableStateOf(true)
    var clicks = 0
    var rendered = 0
    val events = mutableListOf<RcPlayerEvent>()
    setContent {
      val declare = bridged
      RcEmbeddedPlayer(
        display,
        Modifier.size(100.dp),
        onEvent = { events += it },
        onRendered = { rendered++ },
        key = declare,
      ) {
        val bridge = LocalRcHostBridge.current
        val action = if (declare) bridge.action("tapped") { clicks++ } else hostAction("tapped".rs)
        RemoteBox(RemoteModifier.fillMaxSize().background(Color.Gray.rc).clickable(action))
      }
    }
    waitUntil { rendered >= 1 }
    onRoot().performTouchInput { click(center) }
    waitUntil { clicks == 1 }

    val before = rendered
    bridged = false
    waitUntil { rendered > before }
    settleOffThread()
    onRoot().performTouchInput { click(center) }
    waitUntil { events.any { it is RcPlayerEvent.HostNamedAction } }
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
    awaitDocuments(documents, 1)
    waitUntil { onAllNodesWithText("host says first").fetchSemanticsNodes().isNotEmpty() }

    label = "second"
    waitUntil { onAllNodesWithText("host says second").fetchSemanticsNodes().isNotEmpty() }
    settleOffThread()
    assertEquals(1, documents.size)
  }

  @Test
  fun aFailingDesignIsReportedAndRecoversOnTheNextEdit() = run {
    var broken by mutableStateOf(false)
    var rows by mutableStateOf(1)
    val failures = mutableListOf<Throwable>()
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(
        display,
        Modifier.size(100.dp),
        onDocument = { documents += it },
        onFailure = { failures += it },
        key = rows,
      ) {
        check(!broken) { "bad design" }
        RemoteColumn(RemoteModifier.fillMaxSize()) {
          repeat(rows) { RemoteBox(RemoteModifier.fillMaxWidth().size(10.rdp)) }
        }
      }
    }
    waitUntil { documents.size == 1 }

    broken = true
    waitUntil { failures.isNotEmpty() }
    // The host composition is still alive, and the last good document is still playing.
    assertEquals("bad design", failures.single().message)
    assertEquals(1, documents.size)

    broken = false
    rows = 2
    waitUntil { documents.size == 2 }
  }

  /** An Error from the design (a `TODO()`, say) is the design's failure too, not the host's. */
  @Test
  fun aDesignThrowingAnErrorIsReportedNotRethrown() = run {
    val failures = mutableListOf<Throwable>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onFailure = { failures += it }) {
        TODO("unfinished design")
      }
    }
    waitUntil { failures.isNotEmpty() }
    assertTrue(failures.single() is NotImplementedError)
  }

  /**
   * Content that captures a plain value read in the host, as an editor's does with its document.
   * The lambda keeps its identity across recompositions; [RcEmbeddedPlayer]'s `key` carries the
   * change.
   */
  @Test
  fun contentCapturingAHostValueFollowsItsKey() = run {
    var rows by mutableStateOf(1)
    val documents = mutableListOf<RcDocument>()
    setContent {
      val count = rows
      RcEmbeddedPlayer(
        display,
        Modifier.size(100.dp),
        onDocument = { documents += it },
        key = count,
      ) {
        RemoteColumn(RemoteModifier.fillMaxSize()) {
          repeat(count) { RemoteBox(RemoteModifier.fillMaxWidth().size(10.rdp)) }
        }
      }
    }
    awaitDocuments(documents, 1)

    rows = 2
    awaitDocuments(documents, 2)
  }

  /**
   * The editor's path: content tagged with ids from a host-side registry, played, and its geometry
   * read back per key through the player's coordinates listener.
   */
  @Test
  fun hostKeysMapToLaidOutBounds() = run {
    val ids = RcComponentIds<String>()
    val bounds = mutableMapOf<String, Rect>()
    val documents = mutableListOf<RcDocument>()
    setContent {
      CompositionLocalProvider(
        LocalRcComponentCoordinates provides
          RcComponentCoordinatesListener { id, coordinates ->
            ids.keyFor(id)?.let { bounds[it] = coordinates.boundsInRoot() }
          }
      ) {
        RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
          RemoteColumn(ids.component(RemoteModifier.fillMaxSize(), "column")) {
            RemoteBox(ids.component(RemoteModifier.fillMaxWidth().size(10.rdp), "first"))
            RemoteBox(ids.component(RemoteModifier.fillMaxWidth().size(20.rdp), "second"))
          }
        }
      }
    }
    awaitDocuments(documents, 1)
    waitUntil { bounds.keys.containsAll(listOf("column", "first", "second")) }

    assertEquals(Rect(0f, 0f, 100f, 100f), bounds.getValue("column"))
    assertEquals(10f, bounds.getValue("first").height)
    assertEquals(10f, bounds.getValue("second").top)
    assertEquals(20f, bounds.getValue("second").height)
  }
}
