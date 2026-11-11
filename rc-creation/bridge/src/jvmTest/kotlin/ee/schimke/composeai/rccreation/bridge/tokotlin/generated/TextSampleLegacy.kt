// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour legacy.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.asRemoteTextUnit
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rccreation.compose.text.RemoteFontFamily
import ee.schimke.composeai.rccreation.compose.text.RemoteFontStyle
import ee.schimke.composeai.rccreation.compose.text.RemoteFontVariation
import ee.schimke.composeai.rccreation.compose.text.RemoteFontWeight
import ee.schimke.composeai.rccreation.compose.text.RemoteTextAlign
import ee.schimke.composeai.rccreation.compose.text.RemoteTextDecoration
import ee.schimke.composeai.rccreation.compose.text.RemoteTextOverflow
import ee.schimke.composeai.rccreation.compose.text.RemoteTextStyle

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
