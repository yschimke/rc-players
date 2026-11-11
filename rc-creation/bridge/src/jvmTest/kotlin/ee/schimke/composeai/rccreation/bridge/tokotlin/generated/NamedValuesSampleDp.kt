// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour dp.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteColor
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteTextAlign
import androidx.compose.remote.creation.compose.text.RemoteTextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
@RemoteComposable
fun NamedValuesSampleDp() {
  val themeSurface = rememberNamedRemoteColor("theme.surface", RemoteColor(Color(0xFF1C1B1F)))
  val cardTitle = remember { RemoteString.createNamedRemoteString("card.title", "Untitled") }
  val themeOnSurface = rememberNamedRemoteColor("theme.onSurface", RemoteColor(Color(0xFFE6E1E5)))
  RemoteBox(
    modifier =
      RemoteModifier.fillMaxSize()
        .background(themeSurface)
        .clickable(hostAction("open".rs))
        .semantics { contentDescription = "A card".rs },
    contentAlignment = RemoteAlignment.Center,
  ) {
    RemoteText(
      cardTitle,
      color = themeOnSurface,
      fontSize = 16.rsp,
      textAlign = RemoteTextAlign.Start,
      overflow = RemoteTextOverflow.Clip,
    )
  }
}
