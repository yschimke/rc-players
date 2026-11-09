// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour dp.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteFlowRow
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.border
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.heightIn
import androidx.compose.remote.creation.compose.modifier.offset
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.modifier.wrapContentHeight
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
@RemoteComposable
fun LayoutSampleDp() {
  RemoteColumn(
    modifier =
      RemoteModifier.fillMaxSize().padding(8.rdp).background(RemoteColor(Color(0xFFEEEEEE))),
    verticalArrangement = RemoteArrangement.spacedBy(4.rdp),
    horizontalAlignment = RemoteAlignment.CenterHorizontally,
  ) {
    RemoteRow(
      modifier = RemoteModifier.fillMaxWidth().height(48.rdp),
      horizontalArrangement = RemoteArrangement.SpaceBetween,
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      RemoteBox(modifier = RemoteModifier.size(24.rdp).background(RemoteColor(Color(0xFFFF0000))))
      RemoteBox(
        modifier =
          RemoteModifier.weight(1f.rf).height(24.rdp).background(RemoteColor(Color(0xFF00FF00))),
      )
      RemoteBox(
        modifier =
          RemoteModifier.size(24.rdp)
            .clip(RemoteRoundedCornerShape(6.rdp))
            .background(RemoteColor(Color(0xFF0000FF))),
      )
    }
    RemoteBox(
      modifier =
        RemoteModifier.width(120.rdp)
          .heightIn(min = 32.rdp)
          .border(2.rdp, RemoteColor(Color(0xFF000000)))
          .offset(4.rdp, 2.rdp),
      contentAlignment = RemoteAlignment.BottomEnd,
    ) {
      RemoteBox(modifier = RemoteModifier.size(10.rdp).background(RemoteColor(Color(0xFFFF00FF))))
    }
    RemoteFlowRow(modifier = RemoteModifier.fillMaxWidth().wrapContentHeight(), maxLines = 2) {
      RemoteBox(modifier = RemoteModifier.size(16.rdp).background(RemoteColor(Color(0xFF00FFFF))))
      RemoteBox(modifier = RemoteModifier.size(16.rdp).background(RemoteColor(Color(0xFFFFFF00))))
    }
  }
}
