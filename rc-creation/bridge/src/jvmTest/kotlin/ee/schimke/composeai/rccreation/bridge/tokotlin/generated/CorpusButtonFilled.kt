// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 454 x 200, version 1.1.0, density behaviour legacy.
// 3 construct(s) could not be expressed: search for TODO(rc-to-kotlin).

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import ee.schimke.composeai.rccreation.compose.action.combinedAction
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.RemoteRole
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.clip
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.heightIn
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.modifier.role
import ee.schimke.composeai.rccreation.compose.modifier.semantics
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.modifier.widthIn
import ee.schimke.composeai.rccreation.compose.shapes.RemoteRoundedCornerShape
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.asRemoteTextUnit
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rememberNamedRemoteColor
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rccreation.compose.text.RemoteFontFamily
import ee.schimke.composeai.rccreation.compose.text.RemoteFontVariation
import ee.schimke.composeai.rccreation.compose.text.RemoteFontWeight
import ee.schimke.composeai.rccreation.compose.text.RemoteTextOverflow
import ee.schimke.composeai.rccreation.compose.text.RemoteTextStyle

@Composable
@RemoteComposable
fun CorpusButtonFilled() {
  val wearM3OnPrimary = rememberNamedRemoteColor("WearM3.onPrimary", RemoteColor(Color(0xFF210F48)))
  RemoteBox(modifier = RemoteModifier.fillMaxSize(), contentAlignment = RemoteAlignment.Center) {
    // TODO(rc-to-kotlin): click action ValueIntegerExpressionChangeAction is not generated
    RemoteRow(
      modifier =
        RemoteModifier.widthIn(min = 12.rdp)
          .heightIn(min = 52.rdp)
          .width(172.rdp)
          .clip(RemoteRoundedCornerShape(52.rdp))
          .clickable(combinedAction(), role = RemoteRole.Button)
          .padding(horizontal = 28.rdp, vertical = 12.rdp)
          .semantics(mergeDescendants = true) { role = RemoteRole.Button },
      horizontalArrangement = RemoteArrangement.Center,
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      // TODO(rc-to-kotlin): drawWithContent drawing is not generated: PaintValues x2, DrawPath x2,
      // DrawContent
      // TODO(rc-to-kotlin): text reads id 74 (TextLookupInt); its value is not generated
      RemoteText(
        "".rs,
        color = wearM3OnPrimary,
        fontSize = 30.rsp,
        fontWeight = RemoteFontWeight.Medium,
        fontFamily = RemoteFontFamily.Named("google:Roboto Flex"),
        overflow = RemoteTextOverflow.Clip,
        style =
          RemoteTextStyle(
            letterSpacing = 0.8f.sp.asRemoteTextUnit(),
            lineHeight = 36f.sp.asRemoteTextUnit(),
          ),
        fontVariationSettings =
          RemoteFontVariation.Settings(RemoteFontVariation.Setting("pnum", 1f.rf)),
      )
    }
  }
}
