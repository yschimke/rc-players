@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rccreation.coexistence

import androidx.compose.remote.creation.compose.state.RemoteFloat as AndroidxRemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat as RcRemoteFloat
import kotlin.reflect.KClass

/**
 * One file, compiled for Android and for the JVM, that names the same API in both libraries:
 * rc-players' Compose Multiplatform creation modules and AndroidX's own. It compiles only while
 * both are on one classpath, and its types are different classes only while neither library
 * declares the other's packages; `verifyNoSharedClasses` checks the second directly.
 */
internal object BothCreationApis {
  val rcPlayers: KClass<RcRemoteFloat> = RcRemoteFloat::class
  val androidx: KClass<AndroidxRemoteFloat> = AndroidxRemoteFloat::class

  /** A value typed in each library, in one signature: neither is an alias of the other. */
  fun describe(ours: RcRemoteFloat?, theirs: AndroidxRemoteFloat?): String =
    "${rcPlayers.simpleName} from ee.schimke.composeai.rccreation is ${ours != null}, " +
      "from androidx.compose.remote is ${theirs != null}"
}
