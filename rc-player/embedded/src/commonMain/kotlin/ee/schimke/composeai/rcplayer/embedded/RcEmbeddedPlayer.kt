package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDocumentSession
import androidx.compose.remote.creation.compose.capture.wireValue
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import ee.schimke.composeai.rccreation.bridge.ExperimentalRcCreationApi
import ee.schimke.composeai.rccreation.bridge.LocalRcHostBridge
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcPlayerTheme
import ee.schimke.composeai.rcplayer.compose.RcTypefaceLoader
import ee.schimke.composeai.rcplayer.compose.rememberRcNamedValues
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays `@RemoteComposable` [content] in the same process, without capture.
 *
 * The remote content is composed once and kept alive. An edit recomposes only what changed in the
 * retained remote tree, and the tree is re-rendered into a document after each applied batch.
 * Structure and data take two different routes:
 * - **Structure**: an edit to what the remote content reads goes through that recompose-and-render,
 *   and the player gets a new document.
 * - **Data**: values, actions and host UI declared through [LocalRcHostBridge] (an [RcLiveBridge]
 *   here) skip it. A changed value reaches the player's named values without a new document, so the
 *   player keeps scroll, animation and touch state.
 *
 * The remote content runs on its own [Recomposer], dispatcher and frame clock, not as a child of
 * this composition, as the one-shot capture does. A design that throws while composing or rendering
 * is reported through [onFailure], and the last good document stays on screen. The host composition
 * never sees the exception.
 *
 * @param key what [content] describes; an editor passes its document. The remote content recomposes
 *   whenever it changes, and a failed session is replaced by a fresh one. It has to be explicit: a
 *   composable lambda keeps its identity across recompositions and updates its body in place, and
 *   that update does not reach a composition on another recomposer and thread.
 *
 * The document is built by the common byte writer and decoded. Decode measured 10–30 µs against
 * 0.3–0.5 ms for the recompose-and-render it follows, so a typed writer would remove an encoder
 * rather than a cost.
 *
 * @param encodePng carries bitmaps inline as PNG, as `captureCommonRemoteDocument` does; each
 *   bitmap is encoded once per session rather than once per render.
 * @param onDocument told each time the structure produced a new document.
 * @param onRendered told each time the latest [content] has been composed and any document it
 *   produced handed to the player, including when it changed nothing: the point at which the player
 *   shows what [content] describes.
 */
@ExperimentalRcCreationApi
@Composable
public fun RcEmbeddedPlayer(
  display: RemoteCreationDisplayInfo,
  modifier: Modifier = Modifier,
  theme: RcPlayerTheme = RcPlayerTheme.System,
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  encodePng: ((ImageBitmap) -> ByteArray?)? = null,
  onEvent: (RcPlayerEvent) -> Unit = {},
  onDocument: (RcDocument) -> Unit = {},
  onRendered: () -> Unit = {},
  onFailure: (Throwable) -> Unit = {},
  key: Any? = null,
  content: @Composable @RemoteComposable () -> Unit,
) {
  // Key and content in one state, so the remote composition always sees a matching pair. As two
  // states it could compose one edit's content against the previous edit's key, and pin a failure
  // to the wrong key.
  val currentInput by rememberUpdatedState(remember(key, content) { Input(key, content) })
  val currentOnDocument by rememberUpdatedState(onDocument)
  val currentOnRendered by rememberUpdatedState(onRendered)
  val currentOnFailure by rememberUpdatedState(onFailure)
  var document by remember(display) { mutableStateOf<RcDocument?>(null) }
  // A healthy session follows content changes through recomposition. A failed one is dead, and is
  // replaced (a new generation) once the key moves on from the one it failed at.
  var generation by remember { mutableIntStateOf(0) }
  // Each session declares into a bridge of its own. The player plays through the bridge of the
  // session whose document is up, so a replacement session (after a failure, or for a new display)
  // takes over only once it has rendered; until then the last good document keeps its values,
  // handlers and host content.
  val bridge = remember(display, encodePng, generation) { RcLiveBridge() }
  var shownBridge by remember { mutableStateOf<RcLiveBridge?>(null) }
  var failedAt by remember { mutableStateOf<FailedAt?>(null) }
  failedAt?.let { failure ->
    if (failure.key != key) {
      SideEffect {
        failedAt = null
        generation++
      }
    }
  }

  LaunchedEffect(display, encodePng, generation) {
    // What the session reports is applied back here, on the host's dispatcher.
    val host = this
    fun report(block: () -> Unit) {
      host.launch { block() }
    }
    // The remote content runs off the host entirely, as the one-shot capture does: on a dispatcher
    // and frame clock of its own. On the host's clock, every snapshot change (the player's own
    // frame time included) made the remote recomposer ask for a frame, which advanced the player,
    // which changed a snapshot again: a frame loop that never idled. The remote content never
    // animates, so its clock just answers at once.
    withContext(Dispatchers.Default.limitedParallelism(1)) {
      var frameTime = 0L
      lateinit var clock: BroadcastFrameClock
      clock = BroadcastFrameClock {
        launch {
          frameTime += FRAME_NANOS
          clock.sendFrame(frameTime)
        }
      }
      val recomposer = Recomposer(coroutineContext + clock)
      val session = RemoteDocumentSession(display, recomposer, encodePng = encodePng)
      var last: RcDocument? = null
      // The key the session last composed, on the session's own thread. A failure belongs to it,
      // not to whatever key the host holds by the time the report reaches it: if the host has
      // moved to another key meanwhile, pinning the failure there would leave the dead session up.
      var composedKey: Any? = null
      // Whether the latest render failed. Sticky until one succeeds: an apply that changes no node
      // renders nothing, and the content it composed is still the content that failed to render.
      var renderFailed = false
      fun fail(error: Throwable) {
        val failedKey = composedKey
        report {
          if (failedAt == null) failedAt = FailedAt(failedKey)
          currentOnFailure(error)
        }
      }
      fun renderNow() {
        try {
          val rendered = session.render(writerFor(display))
          val next = RcDocumentCodec.decode(rendered.encodeToByteArray())
          // Structural equality: a recomposition that changed nothing the document records (a
          // bridged value re-declared, say) leaves the player's document, and its state, alone.
          val changed = next != last
          last = next
          renderFailed = false
          bridge.rendered()
          if (changed) {
            report {
              document = next
              shownBridge = bridge
              currentOnDocument(next)
            }
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Throwable) {
          // Throwable, not Exception: a design's `TODO()` throws an Error, and it is as much the
          // design's failure as any other.
          renderFailed = true
          bridge.renderFailed()
          fail(error)
        }
      }
      session.onChangesApplied = ::renderNow
      // A state read only while rendering (in a `RemoteCanvas` draw lambda, say) applies nothing
      // when it changes. It is re-rendered once the recomposer is idle: a change that composition
      // reads too is rendered by its apply, which leaves nothing to render here.
      val renderRequests = Channel<Unit>(Channel.CONFLATED)
      session.onRenderInputsChanged = { renderRequests.trySend(Unit) }
      launch {
        for (request in renderRequests) {
          recomposer.currentState.first { it <= Recomposer.State.Idle }
          if (session.renderInputsChanged) renderNow()
        }
      }
      try {
        // The recomposer runs before the content is set: content composed before it started was
        // composed again as soon as it did, and rendered twice.
        var initialFailure: Throwable? = null
        launch {
          try {
            session.setContent {
              // Read here, not inside the provider, so this scope (and its SideEffect) recomposes
              // with every new key or content.
              val input = currentInput
              composedKey = input.key
              val content = input.content
              CompositionLocalProvider(LocalRcHostBridge provides bridge) { content() }
              // After the latest content is composed and applied, so after any render it caused.
              // A recomposition that changes no node renders nothing, and is reported all the same.
              SideEffect { if (!renderFailed) report { currentOnRendered() } }
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            initialFailure = error
            recomposer.cancel()
          }
        }
        try {
          withContext(clock) { recomposer.runRecomposeAndApplyChanges() }
        } catch (cancelled: CancellationException) {
          // Cancelled for the content's own failure, which is reported below; otherwise the
          // session is being torn down.
          if (initialFailure == null) throw cancelled
        }
        initialFailure?.let { throw it }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        fail(error)
        // Held until the next key replaces this generation, rather than disposed at once.
        awaitCancellation()
      } finally {
        // The bridge may still be the one the last good document plays through, until a
        // replacement session renders. Frozen, disposal leaves its declarations where they are.
        bridge.freeze()
        // Disposing clears the tree, and the applier reports that like any other change:
        // rendered, it would replace the last good document with an empty one.
        session.onChangesApplied = null
        renderRequests.close()
        session.dispose()
        recomposer.cancel()
      }
    }
  }

  val namedValues = rememberRcNamedValues()
  val played = shownBridge ?: bridge
  LaunchedEffect(played, namedValues) {
    // The map holds the bridge's values and nothing else, so it mirrors each snapshot exactly: a
    // name no longer declared stops overriding the document, which then plays its own default.
    snapshotFlow { played.namedValues() }
      .collect { current ->
        (namedValues.keys - current.keys).forEach(namedValues::remove)
        namedValues.putAll(current)
      }
  }

  // One lambda for as long as the bridge is the same, reading the host's latest callback: with the
  // registry also cached, a host recomposing for its own reasons hands the player only arguments it
  // already has, and the player skips.
  val currentOnEvent by rememberUpdatedState(onEvent)
  val dispatch =
    remember(played) {
      { event: RcPlayerEvent -> if (!played.dispatch(event)) currentOnEvent(event) }
    }

  val current = document ?: return
  RcComposePlayer(
    current,
    modifier = modifier,
    theme = theme,
    namedValues = namedValues,
    onEvent = dispatch,
    typefaces = typefaces,
    customComponents = played.customComponents(),
  )
}

private const val FRAME_NANOS = 16_666_667L

private class FailedAt(val key: Any?)

private class Input(val key: Any?, val content: @Composable @RemoteComposable () -> Unit)

// TODO(upstream): a Dp display's header gets pixel dimensions, as AndroidX writes it; see the
// fork's PROVENANCE.md, "Upstream follow-ups".
private fun writerFor(display: RemoteCreationDisplayInfo): RemoteDocumentWriter =
  RemoteDocumentWriter(
    display.size.width.toInt(),
    display.size.height.toInt(),
    display.densityBehavior.wireValue,
  )
