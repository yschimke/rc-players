package ee.schimke.composeai.remote.apple

import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.action.valueChange
import androidx.compose.remote.creation.compose.modifier.RemoteRole
import androidx.compose.remote.creation.compose.modifier.SemanticsPropertyReceiver
import androidx.compose.remote.creation.compose.modifier.remoteRole
import androidx.compose.remote.creation.compose.state.MutableRemoteState
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.ui.text.font.FontWeight

// The three names this prototype needs that the pinned Remote Compose release (1.0.0-alpha23) spells
// differently from androidx-main, for the default `release` lane. `src/remoteSnapshot` has the same
// three for `-Pcomposeai.remoteCompose=snapshot`; `build.gradle.kts` compiles one or the other.
// Everything else in `src/main` is written in a form both lanes accept. Delete both files, and use
// androidx-main's names directly, once the pinned release has them.

/** A text style's font weight: Compose's `FontWeight` in alpha23, `RemoteFontWeight` after it. */
internal typealias AppleFontWeight = FontWeight

/** A semantics node's role: alpha23's `remoteRole`, which androidx-main renames to `role`. */
internal var SemanticsPropertyReceiver.appleRole: RemoteRole?
  get() = remoteRole
  set(value) {
    if (value != null) remoteRole = value
  }

/** androidx-main's `valueChangeAction`: alpha23's `valueChange`, renamed. */
internal fun <T> valueChangeAction(
  remoteState: MutableRemoteState<T>,
  updatedValue: RemoteState<T>,
): RemoteAction = valueChange(remoteState, updatedValue)
