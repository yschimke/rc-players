@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rccreation.coexistence

import androidx.compose.remote.creation.compose.layout.remoteComponent
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.runtime.Composable

// The `-Pcomposeai.remoteCompose=snapshot` lane's androidx-main spelling of what
// `src/androidHostTestRemoteRelease` writes for the pinned release. See that file.

/** A named float that starts as the enclosing component's measured width. */
@Composable
internal fun androidxNamedComponentWidth(name: String): RemoteFloat =
  RemoteFloat.createNamedRemoteFloat(name, remoteComponent().width)
