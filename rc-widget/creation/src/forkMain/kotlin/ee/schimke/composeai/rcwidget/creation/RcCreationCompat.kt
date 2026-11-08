package ee.schimke.composeai.rcwidget.creation

import androidx.compose.remote.creation.compose.layout.remoteComponent
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemotePaintingStyle
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberNamedComponentFloat(
  name: String,
  domain: RemoteState.Domain,
  value: RcComponentSize.() -> RemoteFloat,
): RemoteFloat {
  val component = remoteComponent()
  return RemoteFloat.createNamedRemoteFloat(
    name,
    RcComponentSize(component.width, component.height).value(),
    domain,
  )
}

internal actual fun RemotePaint.stroke() {
  style = RemotePaintingStyle.Stroke
}
