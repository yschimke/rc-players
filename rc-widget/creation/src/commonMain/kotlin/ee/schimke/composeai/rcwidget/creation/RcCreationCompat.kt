package ee.schimke.composeai.rcwidget.creation

import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.compose.layout.remoteComponent
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.RemotePaintingStyle
import ee.schimke.composeai.rccreation.compose.state.RemoteState

/**
 * A float named [name] in [domain], initialised to [value] of the enclosing layout component's
 * size. Call it inside the content of the component it measures.
 *
 * One of the two functions this file holds, the ones whose body depends on the creation library:
 * `:rc-widget-creation-androidx` generates every other file of its binding from this module's and
 * writes this one by hand, against the AndroidX release it is built on.
 */
@Composable
internal fun rememberNamedComponentFloat(
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

/** Paints strokes rather than fills. */
internal fun RemotePaint.stroke() {
  style = RemotePaintingStyle.Stroke
}
