package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDocumentSession
import androidx.compose.remote.creation.compose.capture.wireValue
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import ee.schimke.composeai.rccreation.bridge.LocalRcHostBridge
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.rememberRcNamedValues
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent

/**
 * PROTOTYPE: plays `@RemoteComposable` [content] in the same process, without capture.
 *
 * The remote content is composed as a child of this composition, on this composition's recomposer
 * and frame. It is never captured from scratch. Structure and data take two different routes:
 * - **Structure**: an edit recomposes only what changed in the retained remote tree. After each
 *   applied batch, the tree is re-rendered into a document and handed to the player.
 * - **Data**: values, actions and host UI declared through [LocalRcHostBridge] (an [RcLiveBridge]
 *   here) skip that route. A changed value reaches the player's named values without a new
 *   document, so the player keeps scroll, animation and touch state.
 *
 * The document is built by the common byte writer and decoded. Decode measured 10–30 µs against
 * 0.3–0.5 ms for the recompose-and-render it follows, so a typed writer would remove an encoder
 * rather than a cost.
 *
 * @param onDocument told each time the structure produced a new document (for tooling and tests).
 */
@Composable
public fun RcEmbeddedPlayer(
  display: RemoteCreationDisplayInfo,
  modifier: Modifier = Modifier,
  onEvent: (RcPlayerEvent) -> Unit = {},
  onDocument: (RcDocument) -> Unit = {},
  content: @Composable @RemoteComposable () -> Unit,
) {
  val bridge = remember { RcLiveBridge() }
  val parent = rememberCompositionContext()
  val currentContent by rememberUpdatedState(content)
  val currentOnDocument by rememberUpdatedState(onDocument)
  var document by remember { mutableStateOf<RcDocument?>(null) }

  val session = remember(display) { RemoteDocumentSession(display, parent) }
  DisposableEffect(session) {
    session.onChangesApplied = {
      val rendered = session.render(writerFor(display))
      val next = RcDocumentCodec.decode(rendered.encodeToByteArray())
      // Structural equality: a recomposition that changed nothing the document records (a bridged
      // value re-declared, say) leaves the player's document, and so its state, alone.
      if (next != document) {
        document = next
        currentOnDocument(next)
      }
    }
    session.setContent {
      CompositionLocalProvider(LocalRcHostBridge provides bridge) { currentContent() }
    }
    onDispose { session.dispose() }
  }

  val namedValues = rememberRcNamedValues()
  LaunchedEffect(bridge, namedValues) {
    snapshotFlow { bridge.namedValues() }.collect { namedValues.putAll(it) }
  }

  val current = document ?: return
  RcComposePlayer(
    current,
    modifier = modifier,
    namedValues = namedValues,
    onEvent = { event -> if (!bridge.dispatch(event)) onEvent(event) },
    customComponents = bridge.customComponents(),
  )
}

private fun writerFor(display: RemoteCreationDisplayInfo): RemoteDocumentWriter =
  RemoteDocumentWriter(
    display.size.width.toInt(),
    display.size.height.toInt(),
    display.densityBehavior.wireValue,
  )
