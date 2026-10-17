package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rccreation.bridge.LocalRcHostBridge
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Every kind of recomposition a live session has to follow, after AndroidX's
 * `CaptureRemoteDocumentRecompositionTest` for the streaming capture: the session must produce a
 * new document whenever what the document records changed, equal to a fresh capture of the same
 * content, and must not render when nothing did.
 *
 * A render is counted by a `RemoteCanvas` draw lambda, which runs once per render: the structural
 * comparison of documents would hide a render that changed nothing.
 */
@OptIn(ExperimentalTestApi::class)
class RcEmbeddedPlayerRecompositionTest {
  private val display = RemoteCreationDisplayInfo(100, 100, 160)

  private fun run(block: SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(Size(100f, 100f), Density(1f), block = block)

  /** The session runs on its own dispatcher, so a test waits for what it reports. */
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

  private fun SkikoComposeUiTest.centre(): Color = onRoot().captureToImage().toPixelMap()[50, 50]

  private fun fresh(content: @Composable @RemoteComposable () -> Unit): RcDocument =
    RcDocumentCodec.decode(runBlocking { captureCommonRemoteDocument(display, content = content) })

  /** A full-size rectangle, drawn at render time, counting the renders it is drawn in. */
  @Composable
  @RemoteComposable
  private fun Fill(renders: AtomicInteger?, color: () -> Color) {
    RemoteCanvas(RemoteModifier.fillMaxSize()) {
      renders?.incrementAndGet()
      drawRect(paint = RemotePaint { this.color = color().rc })
    }
  }

  @Test
  fun aStateReadInCompositionProducesANewDocument() = run {
    var text by mutableStateOf("Initial")
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        RemoteText(text.rs)
      }
    }
    awaitDocuments(documents, 1)
    assertEquals(fresh { RemoteText("Initial".rs) }, documents[0])

    text = "Updated"
    awaitDocuments(documents, 2)
    assertEquals(fresh { RemoteText("Updated".rs) }, documents[1])
  }

  /** Effects run on the session's own recomposer, and what they write recomposes the content. */
  @Test
  fun anEffectInTheRemoteContentProducesNewDocuments() = run {
    var trigger by mutableStateOf(0)
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        var text by remember { mutableStateOf("Initial") }
        val current = trigger
        LaunchedEffect(current) { if (current > 0) text = "Updated $current" }
        RemoteText(text.rs)
      }
    }
    awaitDocuments(documents, 1)

    trigger = 1
    awaitDocuments(documents, 2)
    assertEquals(fresh { RemoteText("Updated 1".rs) }, documents[1])

    trigger = 2
    awaitDocuments(documents, 3)
    assertEquals(fresh { RemoteText("Updated 2".rs) }, documents[2])
  }

  /**
   * A flow collected in the remote content writes from a coroutine, outside any composition: no
   * test code sends apply notifications, so the session has to see the write by itself.
   */
  @Test
  fun aFlowCollectedInTheRemoteContentProducesANewDocument() = run {
    val flow = MutableStateFlow("Initial")
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        val text by flow.collectAsState()
        RemoteText(text.rs)
      }
    }
    awaitDocuments(documents, 1)

    flow.value = "Updated"
    awaitDocuments(documents, 2)
    assertEquals(fresh { RemoteText("Updated".rs) }, documents[1])
  }

  /**
   * A `RemoteCanvas` draw lambda runs at render time, so a state it reads changes no node and
   * applies nothing. The session must still re-render when that state changes.
   */
  @Test
  fun aStateReadOnlyWhileRenderingProducesANewDocument() = run {
    var fill by mutableStateOf(Color.Red)
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        Fill(renders = null) { fill }
      }
    }
    awaitDocuments(documents, 1)
    waitUntil { centre() == Color.Red }

    fill = Color.Blue
    awaitDocuments(documents, 2)
    assertEquals(fresh { Fill(renders = null) { Color.Blue } }, documents[1])
    waitUntil { centre() == Color.Blue }
  }

  /**
   * A render's own writes are no reason to render again, but a later change to a state it both read
   * and wrote is.
   */
  @Test
  fun aStateTheRenderAlsoWritesStillReRendersOnAnOutsideChange() = run {
    val seen = mutableStateOf(0)
    val renders = AtomicInteger()
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) {
          renders.incrementAndGet()
          // Read, then written by the render itself.
          seen.value = seen.value + 1
          drawRect(paint = RemotePaint { color = Color.Red.rc })
        }
      }
    }
    awaitDocuments(documents, 1)
    settleOffThread()
    assertEquals(1, renders.get())

    Snapshot.withMutableSnapshot { seen.value = 100 }
    waitUntil(timeoutMillis = 5_000) { renders.get() == 2 }
    settleOffThread()
    assertEquals(2, renders.get())
  }

  /** A write that neither the remote composition nor its render reads costs nothing. */
  @Test
  fun anUnrelatedWriteDoesNotRender() = run {
    var unrelated by mutableStateOf(0)
    val renders = AtomicInteger()
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        Fill(renders) { Color.Red }
      }
    }
    awaitDocuments(documents, 1)
    assertEquals(1, renders.get())

    repeat(3) {
      unrelated++
      settleOffThread()
    }
    assertEquals(1, renders.get())
    assertEquals(1, documents.size)
  }

  /**
   * The content is composed once to start with, and rendered once. A recomposition whose output is
   * identical applies nothing, so nothing renders and the player keeps its document.
   */
  @Test
  fun aRecompositionThatAppliesNothingDoesNotRender() = run {
    var trigger by mutableStateOf(0)
    val compositions = AtomicInteger()
    val renders = AtomicInteger()
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        // Read in composition, so a write recomposes this scope, without feeding any node.
        trigger
        compositions.incrementAndGet()
        Fill(renders) { Color.Red }
      }
    }
    awaitDocuments(documents, 1)
    settleOffThread()
    assertEquals(1, compositions.get())
    assertEquals(1, renders.get())

    trigger++
    waitUntil(timeoutMillis = 5_000) { compositions.get() == 2 }
    settleOffThread()
    assertEquals(1, renders.get())
    assertEquals(1, documents.size)
  }

  /**
   * The session keeps one composition alive: state remembered in the remote content survives an
   * edit elsewhere in it, where a fresh capture per edit would start over.
   */
  @Test
  fun rememberedStateSurvivesAStructuralEdit() = run {
    var rows by mutableStateOf(1)
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        var label by remember { mutableStateOf("first") }
        LaunchedEffect(Unit) { label = "kept" }
        RemoteColumn(RemoteModifier.fillMaxSize()) {
          RemoteText(label.rs)
          repeat(rows) { RemoteBox(RemoteModifier.fillMaxWidth().size(10.rdp)) }
        }
      }
    }
    waitUntil(timeoutMillis = 5_000) {
      documents.lastOrNull() == fresh { Rows(label = "kept", rows = 1) }
    }
    val settled = documents.size

    rows = 2
    awaitDocuments(documents, settled + 1)
    assertEquals(fresh { Rows(label = "kept", rows = 2) }, documents.last())
  }

  @Composable
  @RemoteComposable
  private fun Rows(label: String, rows: Int) {
    RemoteColumn(RemoteModifier.fillMaxSize()) {
      RemoteText(label.rs)
      repeat(rows) { RemoteBox(RemoteModifier.fillMaxWidth().size(10.rdp)) }
    }
  }

  /** Keyed children that move are moved in the retained tree, and render as a fresh capture. */
  @Test
  fun movedKeyedChildrenRenderLikeAFreshCapture() = run {
    var order by mutableStateOf(listOf(Color.Red, Color.Green, Color.Blue))
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        Stripes(order)
      }
    }
    awaitDocuments(documents, 1)
    assertEquals(fresh { Stripes(listOf(Color.Red, Color.Green, Color.Blue)) }, documents[0])

    order = listOf(Color.Blue, Color.Red, Color.Green)
    awaitDocuments(documents, 2)
    assertEquals(fresh { Stripes(listOf(Color.Blue, Color.Red, Color.Green)) }, documents[1])

    order = listOf(Color.Blue, Color.Green)
    awaitDocuments(documents, 3)
    assertEquals(fresh { Stripes(listOf(Color.Blue, Color.Green)) }, documents[2])
  }

  @Composable
  @RemoteComposable
  private fun Stripes(colors: List<Color>) {
    RemoteColumn(RemoteModifier.fillMaxSize()) {
      colors.forEach { color ->
        key(color) { RemoteBox(RemoteModifier.fillMaxWidth().size(10.rdp).background(color.rc)) }
      }
    }
  }

  /**
   * A host recomposing for its own reasons (an editor's selection, say) with the same content and
   * key changes nothing the remote side or the player shows: neither the remote content nor host
   * content placed in it recomposes.
   */
  @Test
  fun aHostRecompositionThatChangesNothingRecomposesNothingInside() = run {
    var selection by mutableStateOf(0)
    val remoteCompositions = AtomicInteger()
    val hostContentCompositions = AtomicInteger()
    val documents = mutableListOf<RcDocument>()
    val content: @Composable @RemoteComposable () -> Unit = {
      remoteCompositions.incrementAndGet()
      LocalRcHostBridge.current.HostContent(
        name = "label",
        modifier = RemoteModifier.fillMaxWidth().size(20.rdp),
        fallback = { RemoteBox(RemoteModifier.size(20.rdp)) },
      ) { modifier ->
        hostContentCompositions.incrementAndGet()
        BasicText("host", modifier)
      }
    }
    setContent {
      // Read by the host, beside the player: a new value recomposes this scope, and with it the
      // call to RcEmbeddedPlayer and its fresh callback lambdas.
      BasicText("selected $selection")
      RcEmbeddedPlayer(
        display,
        Modifier.size(100.dp),
        onDocument = { documents += it },
        key = "design",
        content = content,
      )
    }
    awaitDocuments(documents, 1)
    waitUntil { hostContentCompositions.get() > 0 }
    settleOffThread()
    val remoteBefore = remoteCompositions.get()
    val hostContentBefore = hostContentCompositions.get()

    repeat(3) {
      selection++
      waitUntil { onAllNodesWithText("selected $selection").fetchSemanticsNodes().isNotEmpty() }
      settleOffThread()
    }
    assertEquals(remoteBefore, remoteCompositions.get())
    assertEquals(hostContentBefore, hostContentCompositions.get())
    assertEquals(1, documents.size)
  }

  /** Writes applied together are one edit: one new document, not one per write. */
  @Test
  fun writesAppliedTogetherProduceOneDocument() = run {
    var first by mutableStateOf("a")
    var second by mutableStateOf("b")
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        RemoteColumn {
          RemoteText(first.rs)
          RemoteText(second.rs)
        }
      }
    }
    awaitDocuments(documents, 1)

    Snapshot.withMutableSnapshot {
      first = "c"
      second = "d"
    }
    awaitDocuments(documents, 2)
    settleOffThread()
    assertEquals(2, documents.size)
    assertEquals(
      fresh {
        RemoteColumn {
          RemoteText("c".rs)
          RemoteText("d".rs)
        }
      },
      documents[1],
    )
  }
}
