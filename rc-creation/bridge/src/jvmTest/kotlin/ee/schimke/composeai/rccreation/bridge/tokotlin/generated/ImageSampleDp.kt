// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour dp.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteContentScale
import androidx.compose.remote.creation.compose.layout.RemoteImage
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.io.encoding.Base64
import org.jetbrains.skia.Image

/** Image 42 of the document, 8 x 8, as PNG. */
private val Image42Png: String =
  listOf(
      "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAABHNCSVQICAgIfAhkiAAAAAFzUkdCAK7OHOkAAAAYSURBVBiVY/zPwPCfAQ9gwidJHQWDAQAAD8cCBrTvd0gAAAAASUVORK5CYII="
    )
    .joinToString("")

@Composable
@RemoteComposable
fun ImageSampleDp() {
  val image42 = remember {
    Image.makeFromEncoded(Base64.decode(Image42Png)).toComposeImageBitmap().rb
  }
  RemoteBox(modifier = RemoteModifier.fillMaxSize(), contentAlignment = RemoteAlignment.Center) {
    RemoteImage(
      image42,
      contentDescription = null,
      modifier = RemoteModifier.size(48.rdp),
      contentScale = RemoteContentScale.Crop,
      alpha = 0.5f.rf,
    )
  }
}
