// Generated from a Remote Compose document by rc-player-edit's toRemoteComposeKotlin.
// Document: 454 x 400, version 1.1.0, density behaviour legacy.
// 3 construct(s) could not be expressed: search for TODO(rc-to-kotlin).

package ee.schimke.composeai.rccreation.bridge.tokotlin.generated

import androidx.compose.remote.creation.compose.action.combinedAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteContentScale
import androidx.compose.remote.creation.compose.layout.RemoteImage
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.RemoteRole
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.heightIn
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.modifier.wrapContentHeight
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rb
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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.sp
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
  val wearM3OnSurface = rememberNamedRemoteColor("WearM3.onSurface", Color(0xFFF6EDFF))
  val wearM3OnSurfaceVariant =
    rememberNamedRemoteColor("WearM3.onSurfaceVariant", Color(0xFFCAC4D0))
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
