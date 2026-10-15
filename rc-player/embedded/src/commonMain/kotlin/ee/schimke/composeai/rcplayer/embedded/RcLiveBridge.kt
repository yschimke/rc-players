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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
 * PROTOTYPE: the [RcHostBridge] for a player in the same process.
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
public class RcLiveBridge : RcHostBridge {
  override val mode: RcBridgeMode = RcBridgeMode.Live

  // Holders are registered once per name and updated in place, so a recomposition of the remote
  // content that re-declares a binding does not churn the maps the player observes.
  private class ValueHolder(var read: () -> RcNamedValue)

  private class ContentHolder(content: @Composable (Modifier) -> Unit) {
    var content by mutableStateOf(content)
  }

  private val values = mutableStateMapOf<String, ValueHolder>()
  // Snapshot maps throughout: the remote content declares bindings on its own thread, and the host
  // reads them on its.
  private val handlers = mutableStateMapOf<String, () -> Unit>()
  private val contents = mutableStateMapOf<String, ContentHolder>()

  /** Every bridged value, read now. Snapshot reads, so a `snapshotFlow` over it tracks the host. */
  public fun namedValues(): Map<String, RcNamedValue> = values.mapValues { it.value.read() }

  /** Player events for bridged actions go to their handlers; everything else is returned. */
  public fun dispatch(event: RcPlayerEvent): Boolean {
    val name = (event as? RcPlayerEvent.HostNamedAction)?.name ?: return false
    val handler = handlers[name] ?: return false
    handler()
    return true
  }

  /** Custom-component renderers for every [HostContent] slot declared so far. */
  public fun customComponents(): RcCustomComponentRegistry =
    RcCustomComponentRegistry(
      contents
        .mapKeys { rcHostContentConfig(it.key) }
        .mapValues { (_, holder) ->
          val content: RcCustomContent = { _, modifier -> holder.content(modifier) }
          content
        }
    )

  private fun bind(name: String, read: () -> RcNamedValue) {
    val key = rcBridgeName(name)
    val holder = Snapshot.withoutReadObservation { values[key] }
    if (holder == null) values[key] = ValueHolder(read) else holder.read = read
  }

  private fun <T> initial(value: () -> T): T = Snapshot.withoutReadObservation(value)

  override fun float(name: String, value: () -> Float): RemoteFloat {
    bind(name) { RcNamedValue.FloatValue(value()) }
    return RemoteFloat.createNamedRemoteFloat(name, initial(value))
  }

  override fun color(name: String, value: () -> Color): RemoteColor {
    bind(name) { RcNamedValue.Color(value().toArgb()) }
    return RemoteColor.createNamedRemoteColor(name, initial(value))
  }

  override fun text(name: String, value: () -> String): RemoteString {
    bind(name) { RcNamedValue.Text(value()) }
    return RemoteString.createNamedRemoteString(name, initial(value))
  }

  override fun boolean(name: String, value: () -> Boolean): RemoteBoolean {
    bind(name) { RcNamedValue.BooleanValue(value()) }
    return RemoteBoolean.createNamedRemoteBoolean(name, initial(value))
  }

  override fun action(name: String, handler: () -> Unit): Action {
    Snapshot.withoutReadObservation { handlers[name] = handler }
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
    val holder = Snapshot.withoutReadObservation { contents[name] }
    if (holder == null) contents[name] = ContentHolder(content) else holder.content = content
    RcHostContentSlot(name, modifier)
  }
}
