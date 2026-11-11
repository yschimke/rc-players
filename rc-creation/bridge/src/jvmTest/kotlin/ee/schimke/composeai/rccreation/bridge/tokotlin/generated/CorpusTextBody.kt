// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 454 x 200, version 1.1.0, density behaviour legacy.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteString
import ee.schimke.composeai.rccreation.compose.state.asRemoteTextUnit
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rccreation.compose.text.RemoteFontFamily
import ee.schimke.composeai.rccreation.compose.text.RemoteFontVariation
import ee.schimke.composeai.rccreation.compose.text.RemoteFontWeight
import ee.schimke.composeai.rccreation.compose.text.RemoteTextAlign
import ee.schimke.composeai.rccreation.compose.text.RemoteTextOverflow
import ee.schimke.composeai.rccreation.compose.text.RemoteTextStyle

@Composable
@RemoteComposable
fun CorpusTextBody() {
  val text = remember {
    RemoteString.createNamedRemoteString("text", "Body text lorem ipsum dolor sit amet")
  }
  RemoteBox(modifier = RemoteModifier.fillMaxSize(), contentAlignment = RemoteAlignment.Center) {
    RemoteText(
      text,
      modifier = RemoteModifier.width(160.rdp),
      color = RemoteColor(Color(0xFFFFFFFF)),
      fontSize = 28.rsp,
      fontWeight = RemoteFontWeight(450),
      fontFamily = RemoteFontFamily.Named("google:Roboto Flex"),
      textAlign = RemoteTextAlign.Center,
      overflow = RemoteTextOverflow.Clip,
      style =
        RemoteTextStyle(
          letterSpacing = 0.8f.sp.asRemoteTextUnit(),
          lineHeight = 32f.sp.asRemoteTextUnit(),
        ),
      fontVariationSettings =
        RemoteFontVariation.Settings(RemoteFontVariation.Setting("pnum", 1f.rf)),
    )
  }
}
