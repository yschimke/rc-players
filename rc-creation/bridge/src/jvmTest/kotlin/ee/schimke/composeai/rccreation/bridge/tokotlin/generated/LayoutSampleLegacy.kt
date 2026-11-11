// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 360 x 480, version 1.1.0, density behaviour legacy.

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteFlowRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.border
import ee.schimke.composeai.rccreation.compose.modifier.clip
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.heightIn
import ee.schimke.composeai.rccreation.compose.modifier.offset
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.modifier.wrapContentHeight
import ee.schimke.composeai.rccreation.compose.shapes.RemoteRoundedCornerShape
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf

@Composable
@RemoteComposable
fun LayoutSampleLegacy() {
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
