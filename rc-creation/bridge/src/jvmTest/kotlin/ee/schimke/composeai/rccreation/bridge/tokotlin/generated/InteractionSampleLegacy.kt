// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour legacy.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.action.hostAction
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteCollapsibleRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteFitBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteIntrinsicSize
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.RemoteRole
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.contentDescription
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.role
import ee.schimke.composeai.rccreation.compose.modifier.semantics
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.modifier.visibility
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.asRemoteDp
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rememberNamedRemoteInt
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.ri
import ee.schimke.composeai.rccreation.compose.state.rs

@Composable
@RemoteComposable
fun InteractionSampleLegacy() {
  val panelVisibility = rememberNamedRemoteInt("panel.visibility", 0)
  val panelSide = remember { RemoteFloat.createNamedRemoteFloat("panel.side", 24f) }
  RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
    RemoteCollapsibleRow(
      modifier = RemoteModifier.fillMaxWidth(),
      horizontalArrangement = RemoteArrangement.End,
    ) {
      RemoteBox(
        modifier =
          RemoteModifier.size(40.rdp)
            .collapsiblePriority(2f)
            .clickable(hostAction("toggle".rs, 1.ri), role = RemoteRole.Switch)
            .background(RemoteColor(Color(0xFF888888))),
      )
      RemoteBox(modifier = RemoteModifier.weight(1f.rf).height(40.rdp).clickable(hostAction(7)))
    }
    RemoteBox(
      modifier =
        RemoteModifier.fillMaxWidth().height(32.rdp).visibility(panelVisibility).semantics(
          mergeDescendants = true,
        ) {
          contentDescription = "Panel".rs
          role = RemoteRole.Image
        },
    )
    RemoteRow(modifier = RemoteModifier.width(RemoteIntrinsicSize.Max)) {
      RemoteBox(modifier = RemoteModifier.width(panelSide.asRemoteDp()).height(8.rdp))
    }
    RemoteFitBox(modifier = RemoteModifier.size(64.rdp)) {
      RemoteBox(modifier = RemoteModifier.size(128.rdp).background(RemoteColor(Color(0xFF444444))))
    }
  }
}
