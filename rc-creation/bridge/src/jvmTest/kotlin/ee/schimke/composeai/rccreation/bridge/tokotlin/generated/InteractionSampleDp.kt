// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour dp.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCollapsibleRow
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteFitBox
import androidx.compose.remote.creation.compose.layout.RemoteIntrinsicSize
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.visibility
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.asRemoteDp
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteInt
import androidx.compose.remote.creation.compose.state.ri
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

@Composable
@RemoteComposable
fun InteractionSampleDp() {
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
            .clickable(hostAction("toggle".rs, 1.ri), role = Role.Switch)
            .background(RemoteColor(Color(0xFF888888))),
      )
      RemoteBox(modifier = RemoteModifier.weight(1f).height(40.rdp).clickable(hostAction(7)))
    }
    RemoteBox(
      modifier =
        RemoteModifier.fillMaxWidth().height(32.rdp).visibility(panelVisibility).semantics(
          mergeDescendants = true,
        ) {
          contentDescription = "Panel".rs
          role = Role.Image
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
