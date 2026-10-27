@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.creation

import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import ee.schimke.composeai.rcwidget.RcWidgetActionIds
import ee.schimke.composeai.rcwidget.RcWidgetActionMetadata

private val LocalRcWidgetActionIds = staticCompositionLocalOf<RcWidgetActionIds?> { null }

/**
 * [content] captured for a launcher widget: its [rcWidgetHostAction]s become numeric host actions
 * registered in [ids], which the app then attaches `PendingIntent`s to.
 */
@Composable
public fun ProvideRcWidgetActions(ids: RcWidgetActionIds, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalRcWidgetActionIds provides ids, content = content)
}

/** Whether this composition is a launcher-widget capture ([ProvideRcWidgetActions]). */
@Composable public fun isRcWidgetActionCapture(): Boolean = LocalRcWidgetActionIds.current != null

/**
 * A tap whose [payload] the app decodes, encoded for the host being captured for.
 *
 * An app's own player runs named host actions, so outside a launcher capture this is the named
 * action [name] with [payload]. A launcher widget's `RemoteViews` player sends only numeric ones,
 * so inside [ProvideRcWidgetActions] this registers [payload] and is the numeric action of its id,
 * whose metadata is [metadata] — by default the payload itself; pass [rcWidgetActionMetadata] to
 * also deliver values the player resolves when tapped.
 */
@Composable
public fun rcWidgetHostAction(
  name: String,
  payload: String,
  metadata: RemoteString? = null,
): Action {
  val ids = LocalRcWidgetActionIds.current ?: return hostAction(name.rs, payload.rs)
  return hostAction(ids.register(payload), metadata ?: payload.rs)
}

/**
 * The metadata envelope [RcWidgetActionMetadata.decode] reads: [payload], then the [subject] the
 * tap is about, its [currentValue] as the player shows it when tapped and the player's
 * [documentTime], each a Remote Compose string evaluated at interaction time.
 */
public fun rcWidgetActionMetadata(
  payload: RemoteString,
  subject: String? = null,
  currentValue: RemoteString = "".rs,
  documentTime: RemoteString = "".rs,
): RemoteString =
  payload +
    RcWidgetActionMetadata.METADATA_MARKER +
    (subject ?: "") +
    RcWidgetActionMetadata.VALUE_MARKER +
    currentValue +
    RcWidgetActionMetadata.TIME_MARKER +
    documentTime
