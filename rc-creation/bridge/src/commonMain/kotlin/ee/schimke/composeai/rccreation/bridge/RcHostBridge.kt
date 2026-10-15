package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteCustomComponent
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * PROTOTYPE: how remote content reaches what its host owns: values, actions and real UI.
 *
 * The same `@RemoteComposable` content runs in two modes, and only this interface differs between
 * them:
 * - **[RcBridgeMode.Document]**: the output is a self-contained document for some other process.
 *   Values are read during composition and written as named-variable defaults, so a change
 *   recomposes and a new document is captured. Action handlers stay with whoever plays the
 *   document; only the name is written. Host content is replaced by its remote fallback.
 * - **[RcBridgeMode.Live]**: the player is in this process. The document still declares every name,
 *   so it stays complete and playable, but values flow straight into the player's named values
 *   without recomposing the remote content or rebuilding the document. Handlers are called
 *   directly, and host content is real Compose drawn through a custom component.
 *
 * Names are the whole contract. Both sides already speak them: named variables in
 * `RcComposePlayer(namedValues)`, `HostNamedAction` events, and `LAYOUT_CUSTOM` config names in
 * `RcCustomComponentRegistry`. Nothing here adds a wire concept.
 */
public interface RcHostBridge {
  public val mode: RcBridgeMode

  /** A float the host owns, declared in the document as the named variable [name]. */
  public fun float(name: String, value: () -> Float): RemoteFloat

  public fun color(name: String, value: () -> Color): RemoteColor

  public fun text(name: String, value: () -> String): RemoteString

  public fun boolean(name: String, value: () -> Boolean): RemoteBoolean

  /** A host action. The document carries [name]; in live mode, [handler] runs when it fires. */
  public fun action(name: String, handler: () -> Unit): Action

  /**
   * Host UI placed in the remote layout. Live mode draws [content] (ordinary Compose) in a custom
   * component slot, laid out by the remote tree. Document mode writes [fallback] instead, because a
   * document cannot carry arbitrary Compose.
   */
  @Composable
  @RemoteComposable
  public fun HostContent(
    name: String,
    modifier: RemoteModifier,
    fallback: @Composable @RemoteComposable () -> Unit,
    content: @Composable (Modifier) -> Unit,
  )
}

public enum class RcBridgeMode {
  Document,
  Live,
}

/** The bridge the current remote content writes through. Defaults to [RcDocumentBridge]. */
public val LocalRcHostBridge: ProvidableCompositionLocal<RcHostBridge> = staticCompositionLocalOf {
  RcDocumentBridge
}

/** The name a bridged value has in the document and in the player's named values. */
public fun rcBridgeName(name: String): String = RemoteState.Domain.User.prefixed(name)

/** The `LAYOUT_CUSTOM` config name a bridged [RcHostBridge.HostContent] slot is written under. */
public fun rcHostContentConfig(name: String): String = "rc-host:$name"

/** Document mode: everything is written into the document. */
public object RcDocumentBridge : RcHostBridge {
  override val mode: RcBridgeMode = RcBridgeMode.Document

  override fun float(name: String, value: () -> Float): RemoteFloat =
    RemoteFloat.createNamedRemoteFloat(name, value())

  override fun color(name: String, value: () -> Color): RemoteColor =
    RemoteColor.createNamedRemoteColor(name, value())

  override fun text(name: String, value: () -> String): RemoteString =
    RemoteString.createNamedRemoteString(name, value())

  override fun boolean(name: String, value: () -> Boolean): RemoteBoolean =
    RemoteBoolean.createNamedRemoteBoolean(name, value())

  override fun action(name: String, handler: () -> Unit): Action = hostAction(name.rs)

  @Composable
  @RemoteComposable
  override fun HostContent(
    name: String,
    modifier: RemoteModifier,
    fallback: @Composable @RemoteComposable () -> Unit,
    content: @Composable (Modifier) -> Unit,
  ) {
    fallback()
  }
}

/**
 * Live-mode helper for implementations outside this module: declares [name] in the document as a
 * host-content slot, so the player asks its custom-component registry to draw it.
 */
@Composable
@RemoteComposable
@Suppress("RestrictedApi")
public fun RcHostContentSlot(name: String, modifier: RemoteModifier) {
  RemoteCustomComponent(name = rcHostContentConfig(name), modifier = modifier)
}
