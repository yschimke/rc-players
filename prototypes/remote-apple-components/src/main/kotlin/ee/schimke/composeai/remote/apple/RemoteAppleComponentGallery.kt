package ee.schimke.composeai.remote.apple

import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.rememberMutableRemoteBoolean
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable

@Composable
public fun RemoteAppleComponentGallery(modifier: RemoteModifier = RemoteModifier) {
  RemoteAppleTheme {
    val wifi = rememberMutableRemoteBoolean(true)
    val colors = RemoteAppleTheme.colors
    RemoteColumn(
      modifier = modifier.fillMaxSize().background(colors.background).padding(20.rf),
      verticalArrangement = RemoteArrangement.spacedBy(24.rf),
    ) {
      RemoteAppleSection("Connectivity".rs) {
        RemoteAppleToggle("Wi-Fi".rs, wifi)
        RemoteAppleLabel("Network".rs, detail = "Studio".rs)
      }
      RemoteAppleSection("Playback".rs) {
        RemoteAppleProgressView(
          progress = 0.64f.rf,
          modifier = RemoteModifier.fillMaxWidth().padding(0.rf, 12.rf),
          label = "Downloading".rs,
        )
        RemoteAppleButton(
          action = hostAction("play".rs),
          title = "Play".rs,
          modifier = RemoteModifier.fillMaxWidth(),
          style = RemoteAppleButtonStyle.Prominent,
        )
        RemoteAppleButton(
          action = hostAction("remove".rs),
          title = "Remove Download".rs,
          modifier = RemoteModifier.fillMaxWidth(),
          style = RemoteAppleButtonStyle.Destructive,
        )
      }
    }
  }
}
