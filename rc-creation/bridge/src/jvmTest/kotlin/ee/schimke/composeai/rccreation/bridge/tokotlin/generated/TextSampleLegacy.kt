// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour legacy.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteFontFamily
import androidx.compose.remote.creation.compose.text.RemoteFontStyle
import androidx.compose.remote.creation.compose.text.RemoteFontVariation
import androidx.compose.remote.creation.compose.text.RemoteFontWeight
import androidx.compose.remote.creation.compose.text.RemoteTextAlign
import androidx.compose.remote.creation.compose.text.RemoteTextDecoration
import androidx.compose.remote.creation.compose.text.RemoteTextOverflow
import androidx.compose.remote.creation.compose.text.RemoteTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
@RemoteComposable
fun TextSampleLegacy() {
  RemoteColumn(
    modifier = RemoteModifier.fillMaxWidth().padding(horizontal = 12.rdp, vertical = 6.rdp),
  ) {
    RemoteText(
      "Hello, \"remote\" \$world".rs,
      color = RemoteColor(Color(0xFF202124)),
      fontSize = 20.rsp,
      fontWeight = RemoteFontWeight.Bold,
      textAlign = RemoteTextAlign.Start,
      overflow = RemoteTextOverflow.Ellipsis,
      maxLines = 1,
    )
    RemoteText(
      "Eighteen".rs,
      fontSize = 18.rsp,
      textAlign = RemoteTextAlign.Start,
      overflow = RemoteTextOverflow.Clip,
    )
    RemoteText(
      "Body text that wraps".rs,
      fontSize = 14.rsp,
      textAlign = RemoteTextAlign.Center,
      overflow = RemoteTextOverflow.Clip,
    )
    RemoteText(
      "Styled".rs,
      fontSize = 16.rsp,
      fontStyle = RemoteFontStyle.Italic,
      fontFamily = RemoteFontFamily.Monospace,
      textAlign = RemoteTextAlign.Start,
      overflow = RemoteTextOverflow.Clip,
      style =
        RemoteTextStyle(
          letterSpacing = 0.5f.sp.asRemoteTextUnit(),
          lineHeight = 24f.sp.asRemoteTextUnit(),
          textDecoration = RemoteTextDecoration.Underline,
        ),
      fontVariationSettings =
        RemoteFontVariation.Settings(RemoteFontVariation.Setting("wdth", 75f.rf)),
    )
  }
}
