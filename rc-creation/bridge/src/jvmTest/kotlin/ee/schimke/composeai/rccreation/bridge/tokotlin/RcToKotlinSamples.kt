package ee.schimke.composeai.rccreation.bridge.tokotlin

import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCollapsibleRow
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteContentScale
import androidx.compose.remote.creation.compose.layout.RemoteFitBox
import androidx.compose.remote.creation.compose.layout.RemoteFlowRow
import androidx.compose.remote.creation.compose.layout.RemoteImage
import androidx.compose.remote.creation.compose.layout.RemoteIntrinsicSize
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.border
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.heightIn
import androidx.compose.remote.creation.compose.modifier.offset
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.visibility
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.modifier.wrapContentHeight
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.asRemoteDp
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteColor
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteInt
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.ri
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteFontFamily
import androidx.compose.remote.creation.compose.text.RemoteFontStyle
import androidx.compose.remote.creation.compose.text.RemoteFontWeight
import androidx.compose.remote.creation.compose.text.RemoteTextAlign
import androidx.compose.remote.creation.compose.text.RemoteTextOverflow
import androidx.compose.remote.creation.compose.text.RemoteTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Hand-written remote content the round trip starts from. Each is captured, turned back into Kotlin
 * by `toRemoteComposeKotlin`, and the generated function — committed under `generated/` — is
 * captured again and must write the same document.
 */
@Composable
@RemoteComposable
fun LayoutSample() {
  RemoteColumn(
    RemoteModifier.fillMaxSize().padding(8.rdp).background(RemoteColor(Color(0xFFEEEEEE))),
    verticalArrangement = RemoteArrangement.spacedBy(4.rdp),
    horizontalAlignment = RemoteAlignment.CenterHorizontally,
  ) {
    RemoteRow(
      RemoteModifier.fillMaxWidth().height(48.rdp),
      horizontalArrangement = RemoteArrangement.SpaceBetween,
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      RemoteBox(RemoteModifier.size(24.rdp).background(RemoteColor(Color.Red)))
      RemoteBox(RemoteModifier.weight(1f).height(24.rdp).background(RemoteColor(Color.Green)))
      RemoteBox(
        RemoteModifier.size(24.rdp)
          .clip(RemoteRoundedCornerShape(6.rdp))
          .background(RemoteColor(Color.Blue))
      )
    }
    RemoteBox(
      RemoteModifier.width(120.rdp)
        .heightIn(min = 32.rdp)
        .border(2.rdp, RemoteColor(Color.Black))
        .offset(4.rdp, 2.rdp),
      contentAlignment = RemoteAlignment.BottomEnd,
    ) {
      RemoteBox(RemoteModifier.size(10.rdp).background(RemoteColor(Color.Magenta)))
    }
    RemoteFlowRow(RemoteModifier.fillMaxWidth().wrapContentHeight(), maxLines = 2) {
      RemoteBox(RemoteModifier.size(16.rdp).background(RemoteColor(Color.Cyan)))
      RemoteBox(RemoteModifier.size(16.rdp).background(RemoteColor(Color.Yellow)))
    }
  }
}

@Composable
@RemoteComposable
fun TextSample() {
  RemoteColumn(RemoteModifier.fillMaxWidth().padding(horizontal = 12.rdp, vertical = 6.rdp)) {
    RemoteText(
      "Hello, \"remote\" \$world".rs,
      color = RemoteColor(Color(0xFF202124)),
      fontSize = 20.rsp,
      fontWeight = RemoteFontWeight.Bold,
      overflow = RemoteTextOverflow.Ellipsis,
      maxLines = 1,
    )
    RemoteText(
      "Body text that wraps".rs,
      fontSize = 14.rsp,
      textAlign = RemoteTextAlign.Center,
      overflow = RemoteTextOverflow.Clip,
    )
    RemoteText(
      "Styled".rs,
      fontSize = 16.rsp,
      fontStyle = RemoteFontStyle.Italic,
      fontFamily = RemoteFontFamily.Monospace,
      overflow = RemoteTextOverflow.Clip,
      style =
        RemoteTextStyle(
          letterSpacing = 0.5.sp.asRemoteTextUnit(),
          lineHeight = 24.sp.asRemoteTextUnit(),
          textDecoration = TextDecoration.Underline,
        ),
      fontVariationSettings = FontVariation.Settings(FontVariation.Setting("wdth", 75f)),
    )
  }
}

@Composable
@RemoteComposable
fun NamedValuesSample() {
  val surface = rememberNamedRemoteColor("theme.surface", Color(0xFF1C1B1F))
  val onSurface = rememberNamedRemoteColor("theme.onSurface", Color(0xFFE6E1E5))
  val title = remember { RemoteString.createNamedRemoteString("card.title", "Untitled") }
  RemoteBox(
    RemoteModifier.fillMaxSize().background(surface).clickable(hostAction("open".rs)).semantics {
      contentDescription = "A card".rs
    },
    contentAlignment = RemoteAlignment.Center,
  ) {
    RemoteText(title, color = onSurface, fontSize = 16.rsp, overflow = RemoteTextOverflow.Clip)
  }
}

@Composable
@RemoteComposable
fun InteractionSample() {
  val shown = rememberNamedRemoteInt("panel.visibility", 0)
  RemoteColumn(RemoteModifier.fillMaxSize()) {
    RemoteCollapsibleRow(
      RemoteModifier.fillMaxWidth(),
      horizontalArrangement = RemoteArrangement.End,
    ) {
      RemoteBox(
        RemoteModifier.size(40.rdp)
          .collapsiblePriority(2f)
          .clickable(hostAction("toggle".rs, 1.ri), role = Role.Switch)
          .background(RemoteColor(Color.Gray))
      )
      RemoteBox(RemoteModifier.weight(1f).height(40.rdp).clickable(hostAction(7)))
    }
    RemoteBox(
      RemoteModifier.fillMaxWidth().height(32.rdp).visibility(shown).semantics(
        mergeDescendants = true
      ) {
        contentDescription = "Panel".rs
        role = Role.Image
      }
    )
    val side = remember { RemoteFloat.createNamedRemoteFloat("panel.side", 24f) }
    RemoteRow(RemoteModifier.width(RemoteIntrinsicSize.Max)) {
      RemoteBox(RemoteModifier.width(side.asRemoteDp()).height(8.rdp))
    }
    RemoteFitBox(RemoteModifier.size(64.rdp)) {
      RemoteBox(RemoteModifier.size(128.rdp).background(RemoteColor(Color.DarkGray)))
    }
  }
}

/** An 8 x 8 bitmap, red over its top half. */
private val sampleBitmap: ImageBitmap by lazy {
  ImageBitmap(8, 8).also { bitmap ->
    Canvas(bitmap).drawRect(Rect(0f, 0f, 8f, 4f), Paint().apply { color = Color.Red })
  }
}

@Composable
@RemoteComposable
fun ImageSample() {
  RemoteBox(RemoteModifier.fillMaxSize(), contentAlignment = RemoteAlignment.Center) {
    RemoteImage(
      remember { sampleBitmap.rb },
      contentDescription = null,
      modifier = RemoteModifier.size(48.rdp),
      contentScale = RemoteContentScale.Crop,
      alpha = 0.5f.rf,
    )
  }
}
