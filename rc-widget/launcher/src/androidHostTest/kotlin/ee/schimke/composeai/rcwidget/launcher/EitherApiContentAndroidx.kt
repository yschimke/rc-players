@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteImage
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.layout.RemoteStateLayout
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.border
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import ee.schimke.composeai.rcwidget.creation.androidx.RcSizeBreakpoint
import ee.schimke.composeai.rcwidget.creation.androidx.rcWidgetHostAction

/** The same widget's content as `EitherApiContentCmp.kt`, written against AndroidX's creation API. */
@Composable
internal fun AndroidxContent() {
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
internal fun AndroidxImageContent(image: ImageBitmap) {
  RemoteImage(
    remember { image.rb },
    contentDescription = null,
    modifier = RemoteModifier.fillMaxSize(),
    contentScale = ContentScale.FillBounds,
  )
}
