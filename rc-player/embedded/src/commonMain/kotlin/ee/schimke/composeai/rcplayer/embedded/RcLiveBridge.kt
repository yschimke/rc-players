package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ee.schimke.composeai.rccreation.bridge.ExperimentalRcCreationApi
import ee.schimke.composeai.rccreation.bridge.RcBridgeMode
import ee.schimke.composeai.rccreation.bridge.RcHostBridge
import ee.schimke.composeai.rccreation.bridge.RcHostContentSlot
import ee.schimke.composeai.rccreation.bridge.rcBridgeName
import ee.schimke.composeai.rccreation.bridge.rcHostContentConfig
import ee.schimke.composeai.rcplayer.compose.RcCustomComponentRegistry
import ee.schimke.composeai.rcplayer.compose.RcCustomContent
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent

/**
 * The [RcHostBridge] for a player in the same process.
 *
 * The document it helps write is the same document [RcDocumentBridge] would write: every value is
 * still declared as a named variable, with its value at first composition as the default. What
 * changes is the route a later value takes. The remote composition never reads a bridged value (it
 * is read here without observation), so a host-side change does not recompose the remote content,
 * re-render, or hand the player a new document. It goes straight into the player's named values
 * instead, and the player keeps its state.
 *
 * @see ee.schimke.composeai.rccreation.bridge.RcDocumentBridge
 */
@ExperimentalRcCreationApi
public class RcLiveBridge : RcHostBridge {
  override val mode: RcBridgeMode = RcBridgeMode.Live

  /**
   * What is declared, by name, for exactly as long as the declaring call is in the composition: a
   * declaration registers when it enters and is removed when it leaves, so content that stops
   * declaring a name (an editor deleting a node, say) leaves nothing behind to be read or to catch
   * an action. One name declared twice resolves to the latest declaration still present.
   *
   * Snapshot state, because the remote content declares on its own thread and the host reads here.
   */
  private class Declarations<T : Any> {
    private val byName = mutableStateMapOf<String, List<T>>()

    fun add(name: String, item: T) {
      byName[name] = Snapshot.withoutReadObservation { byName[name].orEmpty() } + item
    }

    fun remove(name: String, item: T) {
      val rest = Snapshot.withoutReadObservation { byName[name].orEmpty() } - item
      if (rest.isEmpty()) byName.remove(name) else byName[name] = rest
    }

    operator fun get(name: String): T? = byName[name]?.lastOrNull()

    fun latest(): Map<String, T> = byName.mapValues { it.value.last() }
  }

  // Each declaration is a remembered object whose behaviour reads the call's latest arguments
  // through `rememberUpdatedState`: a recomposition that passes a new lambda (new content under a
  // new `key`, capturing a plain value) is a snapshot write the host's `snapshotFlow` sees.
  private class Binding(val read: () -> RcNamedValue)

  private class Handler(val run: () -> Unit)

  private class Content(val content: State<@Composable (Modifier) -> Unit>)

  private val values = Declarations<Binding>()
  private val handlers = Declarations<Handler>()
  private val contents = Declarations<Content>()

  /** Every bridged value, read now. Snapshot reads, so a `snapshotFlow` over it tracks the host. */
  public fun namedValues(): Map<String, RcNamedValue> =
    values.latest().mapValues { it.value.read() }

  /** Player events for bridged actions go to their handlers; everything else is returned. */
  public fun dispatch(event: RcPlayerEvent): Boolean {
    val name = (event as? RcPlayerEvent.HostNamedAction)?.name ?: return false
    val handler = handlers[name] ?: return false
    handler.run()
    return true
  }

  /** Custom-component renderers for every [HostContent] slot currently declared. */
  public fun customComponents(): RcCustomComponentRegistry =
    RcCustomComponentRegistry(
      contents
        .latest()
        .mapKeys { rcHostContentConfig(it.key) }
        .mapValues { (_, holder) ->
          val content: RcCustomContent = { _, modifier -> holder.content.value(modifier) }
          content
        }
    )

  @Composable
  private fun <T : Any> Declare(declarations: Declarations<T>, name: String, item: T) {
    DisposableEffect(declarations, name, item) {
      declarations.add(name, item)
      onDispose { declarations.remove(name, item) }
    }
  }

  @Composable
  private fun bind(name: String, read: () -> RcNamedValue) {
    val latest = rememberUpdatedState(read)
    val binding = remember { Binding { latest.value() } }
    Declare(values, rcBridgeName(name), binding)
  }

  private fun <T> initial(value: () -> T): T = Snapshot.withoutReadObservation(value)

  @Composable
  override fun float(name: String, value: () -> Float): RemoteFloat {
    bind(name) { RcNamedValue.FloatValue(value()) }
    return RemoteFloat.createNamedRemoteFloat(name, initial(value))
  }

  @Composable
  override fun color(name: String, value: () -> Color): RemoteColor {
    bind(name) { RcNamedValue.Color(value().toArgb()) }
    return RemoteColor.createNamedRemoteColor(name, initial(value))
  }

  @Composable
  override fun text(name: String, value: () -> String): RemoteString {
    bind(name) { RcNamedValue.Text(value()) }
    return RemoteString.createNamedRemoteString(name, initial(value))
  }

  @Composable
  override fun boolean(name: String, value: () -> Boolean): RemoteBoolean {
    bind(name) { RcNamedValue.BooleanValue(value()) }
    return RemoteBoolean.createNamedRemoteBoolean(name, initial(value))
  }

  @Composable
  override fun action(name: String, handler: () -> Unit): Action {
    val latest = rememberUpdatedState(handler)
    Declare(handlers, name, remember { Handler { latest.value() } })
    return hostAction(name.rs)
  }

  @Composable
  @RemoteComposable
  override fun HostContent(
    name: String,
    modifier: RemoteModifier,
    fallback: @Composable @RemoteComposable () -> Unit,
    content: @Composable (Modifier) -> Unit,
  ) {
    val latest = rememberUpdatedState(content)
    Declare(contents, name, remember { Content(latest) })
    RcHostContentSlot(name, modifier)
  }
}
