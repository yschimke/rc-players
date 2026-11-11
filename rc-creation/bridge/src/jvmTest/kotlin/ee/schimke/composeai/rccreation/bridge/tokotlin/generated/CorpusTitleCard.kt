// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 454 x 400, version 1.1.0, density behaviour legacy.
// 3 construct(s) could not be expressed: search for TODO(rc-to-kotlin).

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.sp
import ee.schimke.composeai.rccreation.compose.action.combinedAction
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteContentScale
import ee.schimke.composeai.rccreation.compose.layout.RemoteImage
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.RemoteRole
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.clip
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.heightIn
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.modifier.wrapContentHeight
import ee.schimke.composeai.rccreation.compose.shapes.RemoteRoundedCornerShape
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.asRemoteTextUnit
import ee.schimke.composeai.rccreation.compose.state.rb
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
import kotlin.io.encoding.Base64
import org.jetbrains.skia.Image

/** Image 70 of the document, 8 x 8, as PNG. */
private val Image70Png: String =
  listOf(
      "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAAXNSR0IArs4c6QAAAARzQklUCAgICHwIZIgAAAAWSURBVBiVY3zz5s1/BjyACZ/k8FEAAOEJA9MK/fh5AAAAAElFTkSuQmCC",
    )
    .joinToString("")

@Composable
@RemoteComposable
fun CorpusTitleCard() {
  val wearM3OnSurface = rememberNamedRemoteColor("WearM3.onSurface", RemoteColor(Color(0xFFF6EDFF)))
  val wearM3OnSurfaceVariant =
    rememberNamedRemoteColor("WearM3.onSurfaceVariant", RemoteColor(Color(0xFFCAC4D0)))
  val image70 = remember {
    Image.makeFromEncoded(Base64.decode(Image70Png)).toComposeImageBitmap().rb
  }
  RemoteBox(modifier = RemoteModifier.fillMaxSize(), contentAlignment = RemoteAlignment.Center) {
    // TODO(rc-to-kotlin): click action ValueIntegerExpressionChangeAction is not generated
    RemoteColumn(
      modifier =
        RemoteModifier.width(172.rdp)
          .fillMaxWidth()
          .heightIn(min = 64.rdp)
          .wrapContentHeight()
          .clip(RemoteRoundedCornerShape(52.rdp))
          .clickable(combinedAction(), role = RemoteRole.Button)
          .padding(24.rdp),
    ) {
      // TODO(rc-to-kotlin): drawWithContent drawing is not generated: PaintValues, DrawPath,
      // DrawContent
      RemoteRow(modifier = RemoteModifier.fillMaxWidth()) {
        RemoteRow(modifier = RemoteModifier.weight(1f.rf)) {
          // TODO(rc-to-kotlin): text reads id 64 (TextLookupInt); its value is not generated
          RemoteText(
            "".rs,
            color = wearM3OnSurface,
            fontSize = 32.rsp,
            fontWeight = RemoteFontWeight(550),
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
        RemoteBox(modifier = RemoteModifier.width(4.rdp))
        RemoteText(
          "XXm".rs,
          color = wearM3OnSurfaceVariant,
          fontSize = 28.rsp,
          fontWeight = RemoteFontWeight(450),
          fontFamily = RemoteFontFamily.Named("google:Roboto Flex"),
          overflow = RemoteTextOverflow.Clip,
          style =
            RemoteTextStyle(
              letterSpacing = 0.8f.sp.asRemoteTextUnit(),
              lineHeight = 32f.sp.asRemoteTextUnit(),
            ),
          fontVariationSettings =
            RemoteFontVariation.Settings(RemoteFontVariation.Setting("pnum", 1f.rf)),
        )
      }
      RemoteBox(modifier = RemoteModifier.height(2.rdp))
      RemoteImage(
        image70,
        contentDescription = null,
        modifier =
          RemoteModifier.width(148.rdp).height(64.rdp).clip(RemoteRoundedCornerShape(28.rdp)),
        contentScale = RemoteContentScale.FillBounds,
        alpha = 1f.rf,
      )
    }
  }
}
