package ee.schimke.composeai.remote.apple

import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.modifier.RemoteRole
import androidx.compose.remote.creation.compose.modifier.RemoteSemanticsPropertyReceiver
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.state.MutableRemoteState
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.remote.creation.compose.text.RemoteFontWeight

// Three names this prototype's sources use, each AndroidX's own. Until 1.0.0-alpha24 the release
// spelled them differently from androidx-main and there was one of this file per Remote Compose
// lane; if a later bump splits them again, so can this file (see `build.gradle.kts` history).

/** A text style's font weight. */
internal typealias AppleFontWeight = RemoteFontWeight

/** A semantics node's role. */
internal var RemoteSemanticsPropertyReceiver.appleRole: RemoteRole?
  get() = role
  set(value) {
    role = value
  }

/** androidx-main's `valueChangeAction`, which this package resolves before an import. */
internal fun <T> valueChangeAction(
  remoteState: MutableRemoteState<T>,
  updatedValue: RemoteState<T>,
): RemoteAction =
  androidx.compose.remote.creation.compose.action.valueChangeAction(remoteState, updatedValue)
