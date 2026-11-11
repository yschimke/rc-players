// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour dp.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.border
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.asRemoteDp
import ee.schimke.composeai.rccreation.compose.state.rdp

@Composable
@RemoteComposable
fun VariableLengthsSampleDp() {
  val layoutGap = remember { RemoteFloat.createNamedRemoteFloat("layout.gap", 6f) }
  RemoteColumn(
    modifier = RemoteModifier.fillMaxSize().padding(layoutGap.asRemoteDp(), 4.rdp, 4.rdp, 4.rdp),
    verticalArrangement = RemoteArrangement.spacedBy(layoutGap.asRemoteDp()),
  ) {
    RemoteBox(
      modifier =
        RemoteModifier.size(16.rdp).border(layoutGap.asRemoteDp(), RemoteColor(Color(0xFF000000))),
    )
    RemoteBox(modifier = RemoteModifier.size(16.rdp).padding(layoutGap.asRemoteDp()))
  }
}
