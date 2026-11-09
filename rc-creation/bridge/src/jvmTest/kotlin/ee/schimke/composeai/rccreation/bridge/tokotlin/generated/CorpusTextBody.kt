// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 454 x 200, version 1.1.0, density behaviour legacy.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteFontFamily
import androidx.compose.remote.creation.compose.text.RemoteFontVariation
import androidx.compose.remote.creation.compose.text.RemoteFontWeight
import androidx.compose.remote.creation.compose.text.RemoteTextAlign
import androidx.compose.remote.creation.compose.text.RemoteTextOverflow
import androidx.compose.remote.creation.compose.text.RemoteTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

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
