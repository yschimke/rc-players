// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour dp.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.action.hostAction
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.contentDescription
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.semantics
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteString
import ee.schimke.composeai.rccreation.compose.state.rememberNamedRemoteColor
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rccreation.compose.text.RemoteTextAlign
import ee.schimke.composeai.rccreation.compose.text.RemoteTextOverflow

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
