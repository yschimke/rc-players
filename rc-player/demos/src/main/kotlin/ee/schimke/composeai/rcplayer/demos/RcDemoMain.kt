package ee.schimke.composeai.rcplayer.demos

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * `./gradlew :rc-player-demos:run` — the demos in a desktop window, where the field is typeable.
 */
public fun main() {
  application {
    Window(
      onCloseRequest = ::exitApplication,
      title = "Remote Compose custom components",
      state = rememberWindowState(size = DpSize(400.dp, 420.dp)),
    ) {
      RcCustomComponentDemosPreview()
    }
  }
}
