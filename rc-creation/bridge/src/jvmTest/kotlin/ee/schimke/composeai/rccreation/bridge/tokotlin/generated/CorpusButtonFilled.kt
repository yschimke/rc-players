// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 454 x 200, version 1.1.0, density behaviour legacy.
// 3 construct(s) could not be expressed: search for TODO(rc-to-kotlin).

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.action.combinedAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.RemoteRole
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.heightIn
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.modifier.widthIn
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteColor
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteFontFamily
import androidx.compose.remote.creation.compose.text.RemoteFontVariation
import androidx.compose.remote.creation.compose.text.RemoteFontWeight
import androidx.compose.remote.creation.compose.text.RemoteTextOverflow
import androidx.compose.remote.creation.compose.text.RemoteTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
@RemoteComposable
fun CorpusButtonFilled() {
  val wearM3OnPrimary = rememberNamedRemoteColor("WearM3.onPrimary", Color(0xFF210F48))
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
