package ee.schimke.composeai.rccreation.bridge

import ee.schimke.composeai.rccreation.common.Profile
import ee.schimke.composeai.rccreation.common.RcProfiles
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.capture.Profile
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocumentInto
import ee.schimke.composeai.rccreation.compose.capture.wireValue
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import kotlinx.coroutines.runBlocking

/**
 * A document that is one canvas of [width] x [height] pixels (density 1) drawing [onDraw], under
 * the AndroidX and experimental profiles, which AndroidX's reader needs for some of what a canvas
 * writes (an offscreen bitmap, a pattern).
 */
internal fun captureCanvasBytes(
  width: Int = 200,
  height: Int = 100,
  onDraw: RemoteDrawScope.() -> Unit,
): ByteArray = runBlocking {
  val display = RemoteCreationDisplayInfo(width, height, 160)
  val writer =
    RemoteDocumentWriter(
      display.width,
      display.height,
      display.densityBehavior.wireValue,
      profiles = ProfileAndroidx or ProfileExperimental,
    )
  captureCommonRemoteDocumentInto(writer, display) {
    RemoteCanvas(RemoteModifier.fillMaxSize(), onDraw)
  }
  writer.encodeToByteArray()
}

/**
 * AndroidX's profile with its experimental operations: what a capture of a pattern, a 2D mesh, a
 * flow row or a custom component needs, as the default profile ([RcPlatformProfiles.ANDROIDX]) has
 * none of them.
 */
internal val ExperimentalProfile: Profile =
  Profile(profileFlags = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL)
