@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rccreation.coexistence

import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.runtime.Composable

// What `AndroidxContent` writes differently on the pinned Remote Compose release (1.0.0-alpha23)
// than on androidx-main, for the default `release` lane. `src/androidHostTestRemoteSnapshot` has
// androidx-main's spelling for `-Pcomposeai.remoteCompose=snapshot`; `build.gradle.kts` compiles
// one or the other. Delete both, and inline androidx-main's, once the pinned release has it.

/**
 * A named float that starts as the enclosing component's measured width. alpha23 reads the width
 * through a `RemoteFloatContext` lambda; androidx-main through `remoteComponent()`.
 */
@Composable
internal fun androidxNamedComponentWidth(name: String): RemoteFloat =
  RemoteFloat.createNamedRemoteFloatExpression(name, RemoteState.Domain.User) { componentWidth() }
