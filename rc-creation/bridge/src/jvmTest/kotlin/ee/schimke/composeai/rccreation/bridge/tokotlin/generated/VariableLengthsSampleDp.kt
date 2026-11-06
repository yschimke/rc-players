// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour dp.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.border
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.asRemoteDp
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

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
