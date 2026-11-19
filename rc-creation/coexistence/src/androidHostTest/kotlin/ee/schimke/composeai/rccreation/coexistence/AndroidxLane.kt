@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rccreation.coexistence

import androidx.compose.remote.creation.compose.layout.remoteComponent
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.runtime.Composable

// What `AndroidxContent` writes through this file rather than directly. Until 1.0.0-alpha24 the
// release read a component's width through a `RemoteFloatContext` lambda, one file per Remote
// Compose lane; both lanes now read it through `remoteComponent()`.

/** A named float that starts as the enclosing component's measured width. */
@Composable
internal fun androidxNamedComponentWidth(name: String): RemoteFloat =
  RemoteFloat.createNamedRemoteFloat(name, remoteComponent().width)
