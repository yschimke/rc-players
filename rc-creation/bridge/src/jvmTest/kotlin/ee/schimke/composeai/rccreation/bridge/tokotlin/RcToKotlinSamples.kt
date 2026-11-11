package ee.schimke.composeai.rccreation.bridge.tokotlin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.sp
import ee.schimke.composeai.rccreation.compose.action.hostAction
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteCollapsibleRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteContentScale
import ee.schimke.composeai.rccreation.compose.layout.RemoteFitBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteFlowRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteImage
import ee.schimke.composeai.rccreation.compose.layout.RemoteIntrinsicSize
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.RemoteRole
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.border
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.clip
import ee.schimke.composeai.rccreation.compose.modifier.contentDescription
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.heightIn
import ee.schimke.composeai.rccreation.compose.modifier.offset
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.modifier.role
import ee.schimke.composeai.rccreation.compose.modifier.semantics
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.modifier.visibility
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.modifier.wrapContentHeight
import ee.schimke.composeai.rccreation.compose.shapes.RemoteRoundedCornerShape
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteString
import ee.schimke.composeai.rccreation.compose.state.asRemoteDp
import ee.schimke.composeai.rccreation.compose.state.asRemoteTextUnit
import ee.schimke.composeai.rccreation.compose.state.rb
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rememberNamedRemoteColor
import ee.schimke.composeai.rccreation.compose.state.rememberNamedRemoteImageBitmap
import ee.schimke.composeai.rccreation.compose.state.rememberNamedRemoteInt
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.ri
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rccreation.compose.text.RemoteFontFamily
import ee.schimke.composeai.rccreation.compose.text.RemoteFontStyle
import ee.schimke.composeai.rccreation.compose.text.RemoteFontVariation
import ee.schimke.composeai.rccreation.compose.text.RemoteFontWeight
import ee.schimke.composeai.rccreation.compose.text.RemoteTextAlign
import ee.schimke.composeai.rccreation.compose.text.RemoteTextDecoration
import ee.schimke.composeai.rccreation.compose.text.RemoteTextOverflow
import ee.schimke.composeai.rccreation.compose.text.RemoteTextStyle

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
      RemoteBox(RemoteModifier.weight(1f.rf).height(24.rdp).background(RemoteColor(Color.Green)))
      RemoteBox(
        RemoteModifier.size(24.rdp)
          .clip(RemoteRoundedCornerShape(6.rdp))
          .background(RemoteColor(Color.Blue)),
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
    // 18sp at density 2 is 36px, the wire default a legacy capture omits.
    RemoteText("Eighteen".rs, fontSize = 18.rsp, overflow = RemoteTextOverflow.Clip)
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
          textDecoration = RemoteTextDecoration.Underline,
        ),
      fontVariationSettings =
        RemoteFontVariation.Settings(RemoteFontVariation.Setting("wdth", 75f.rf)),
    )
  }
}

@Composable
@RemoteComposable
fun NamedValuesSample() {
  val surface = rememberNamedRemoteColor("theme.surface", RemoteColor(Color(0xFF1C1B1F)))
  val onSurface = rememberNamedRemoteColor("theme.onSurface", RemoteColor(Color(0xFFE6E1E5)))
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
          .clickable(hostAction("toggle".rs, 1.ri), role = RemoteRole.Switch)
          .background(RemoteColor(Color.Gray)),
      )
      RemoteBox(RemoteModifier.weight(1f.rf).height(40.rdp).clickable(hostAction(7)))
    }
    RemoteBox(
      RemoteModifier.fillMaxWidth().height(32.rdp).visibility(shown).semantics(
        mergeDescendants = true,
      ) {
        contentDescription = "Panel".rs
        role = RemoteRole.Image
      },
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
    // A named image keeps its name, so a host can still replace it.
    RemoteImage(
      rememberNamedRemoteImageBitmap("hero") { sampleBitmap },
      contentDescription = null,
      modifier = RemoteModifier.size(24.rdp),
      contentScale = RemoteContentScale.Fit,
      alpha = 1f.rf,
    )
  }
}

/**
 * Lengths read from a host value, in a dp document: each must stay a dp, which the creation API
 * writes only through its `RemoteDp` overloads.
 */
@Composable
@RemoteComposable
fun VariableLengthsSample() {
  val gap = remember { RemoteFloat.createNamedRemoteFloat("layout.gap", 6f) }
  RemoteColumn(
    RemoteModifier.fillMaxSize().padding(gap.asRemoteDp(), 4.rdp, 4.rdp, 4.rdp),
    verticalArrangement = RemoteArrangement.spacedBy(gap.asRemoteDp()),
  ) {
    RemoteBox(RemoteModifier.size(16.rdp).border(gap.asRemoteDp(), RemoteColor(Color.Black)))
    RemoteBox(RemoteModifier.size(16.rdp).padding(gap.asRemoteDp()))
  }
}
