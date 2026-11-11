// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour dp.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toComposeImageBitmap
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteContentScale
import ee.schimke.composeai.rccreation.compose.layout.RemoteImage
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rememberNamedRemoteImageBitmap
import ee.schimke.composeai.rccreation.compose.state.rf
import kotlin.io.encoding.Base64
import org.jetbrains.skia.Image

/** Image 42 of the document, 8 x 8, as PNG. */
private val Image42Png: String =
  listOf(
      "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAABHNCSVQICAgIfAhkiAAAAAFzUkdCAK7OHOkAAAAYSURBVBiVY/zPwPCfAQ9gwidJHQWDAQAAD8cCBrTvd0gAAAAASUVORK5CYII=",
    )
    .joinToString("")

@Composable
@RemoteComposable
fun ImageSampleDp() {
  val image42 =
    rememberNamedRemoteImageBitmap("hero") {
      Image.makeFromEncoded(Base64.decode(Image42Png)).toComposeImageBitmap()
    }
  RemoteBox(modifier = RemoteModifier.fillMaxSize(), contentAlignment = RemoteAlignment.Center) {
    RemoteImage(
      image42,
      contentDescription = null,
      modifier = RemoteModifier.size(48.rdp),
      contentScale = RemoteContentScale.Crop,
      alpha = 0.5f.rf,
    )
    RemoteImage(
      image42,
      contentDescription = null,
      modifier = RemoteModifier.size(24.rdp),
      contentScale = RemoteContentScale.Fit,
      alpha = 1f.rf,
    )
  }
}
