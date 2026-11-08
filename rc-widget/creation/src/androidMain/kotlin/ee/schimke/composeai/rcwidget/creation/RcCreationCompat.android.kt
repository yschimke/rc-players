@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.creation

import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.PaintingStyle

@Composable
internal actual fun rememberNamedComponentFloat(
  name: String,
  domain: RemoteState.Domain,
  value: RcComponentSize.() -> RemoteFloat,
): RemoteFloat =
  RemoteFloat.createNamedRemoteFloatExpression(name, domain) {
    RcComponentSize(componentWidth(), componentHeight()).value()
  }

internal actual fun RemotePaint.stroke() {
  style = PaintingStyle.Stroke
}
