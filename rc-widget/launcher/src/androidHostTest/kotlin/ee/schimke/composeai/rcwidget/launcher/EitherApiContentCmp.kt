@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteContentScale
import ee.schimke.composeai.rccreation.compose.layout.RemoteImage
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.layout.RemoteStateLayout
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.border
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rb
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rcwidget.creation.RcSizeBreakpoint
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction

/** The widget's colours, the same in both bindings' content. */
internal const val RED: Int = 0xFFE53935.toInt()
internal const val BLUE: Int = 0xFF1E58D8.toInt()

/** One widget's content, written against rc-players' creation API. */
@Composable
internal fun CmpContent() {
  val show = RemoteBoolean.createNamedRemoteBoolean("show_bar", false)
  val tap = rcWidgetHostAction("open", "{\"open\":\"kitchen\"}")
  RemoteColumn(RemoteModifier.fillMaxSize().clickable(tap)) {
    // A canvas that reads its measured width (CanvasContent and its ComponentValue, level 7).
    RemoteBox(RemoteModifier.fillMaxWidth().height(20.rdp)) {
      RemoteStateLayout(show.toRemoteInt(), 0, 1, modifier = RemoteModifier.fillMaxSize()) { shown ->
        if (shown == 1) {
          RemoteCanvas(RemoteModifier.fillMaxSize()) {
            drawRect(
              RemotePaint { color = RemoteColor(androidx.compose.ui.graphics.Color(RED)) },
              RemoteOffset(0f.rf, 0f.rf),
              RemoteSize(width * 0.5f.rf, height),
            )
          }
        } else {
          RemoteBox(RemoteModifier.fillMaxSize())
        }
      }
    }
    RemoteRow(RemoteModifier.fillMaxWidth().height(40.rdp)) {
      RemoteBox(
        RemoteModifier.size(40.rdp).border(3.rdp, RemoteColor(androidx.compose.ui.graphics.Color(BLUE))),
      )
      // Breakpoints pick the layout for the slot at capture time, on either binding.
      RcSizeBreakpoint(intArrayOf(140)) { tier ->
        RemoteText(if (tier == 0) "Compact".rs else "Roomy".rs, fontSize = 20.rsp)
      }
      RemoteCanvas(RemoteModifier.size(80.rdp, 40.rdp)) {
        drawCircle(
          RemotePaint { color = RemoteColor(androidx.compose.ui.graphics.Color(RED)) },
          12f.rf,
          RemoteOffset(20f.rf, 20f.rf),
        )
        drawText(
          "Ab".rs,
          40f.rf,
          28f.rf,
          RemotePaint {
            color = RemoteColor(androidx.compose.ui.graphics.Color(BLUE))
            textSize = 22f.rf
          },
        )
      }
    }
  }
}

/** A widget that is one inline bitmap, which the capture has to carry in the document. */
@Composable
internal fun CmpImageContent(image: ImageBitmap) {
  RemoteImage(
    remember { image.rb },
    contentDescription = null,
    modifier = RemoteModifier.fillMaxSize(),
    contentScale = RemoteContentScale.FillBounds,
  )
}
