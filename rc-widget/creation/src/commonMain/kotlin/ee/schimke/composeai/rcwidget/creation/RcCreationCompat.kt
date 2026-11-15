package ee.schimke.composeai.rcwidget.creation

import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.compose.layout.remoteComponent
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.RemotePaintingStyle

/**
 * The enclosing layout component's size, as expressions the document evaluates as it plays. They
 * are not named: nothing outside the document can write them, and no reader takes them for data.
 * Call it inside the content of the component it measures.
 *
 * One of the two functions this file holds, the ones whose body depends on the creation library:
 * `:rc-widget-creation-androidx` generates every other file of its binding from this module's and
 * writes this one by hand, against the AndroidX release it is built on.
 */
@Composable
internal fun rememberComponentSize(): RcComponentSize {
  val component = remoteComponent()
  return RcComponentSize(component.width, component.height)
}

/** Paints strokes rather than fills. */
internal fun RemotePaint.stroke() {
  style = RemotePaintingStyle.Stroke
}
