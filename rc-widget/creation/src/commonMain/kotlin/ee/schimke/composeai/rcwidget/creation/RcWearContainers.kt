@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.creation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteDp
import ee.schimke.composeai.rccreation.compose.state.RemoteState
import ee.schimke.composeai.rcwidget.RcWearContainer
import ee.schimke.composeai.rcwidget.RcWearFrame
import ee.schimke.composeai.rcwidget.RcWearHost

/**
 * A Wear widget container in a known frame: [fill] and [background] as the host's rounded rect of
 * [cornerRadius], an optional [level] in [look], and [content] inset by the padding. Use it where
 * the frame is known (an app drawing its card as the widget will look); capture a template with
 * [RcWearTemplateContainer] instead, which leaves the frame to the watch.
 */
@Composable
public fun RcWearContainerFrame(
  fill: RemoteColor,
  cornerRadius: RemoteDp,
  horizontalPadding: RemoteDp,
  verticalPadding: RemoteDp,
  modifier: RemoteModifier = RemoteModifier,
  background: RcWearBackground? = null,
  level: RcWearLevel? = null,
  look: RcWearLevelLook? = null,
  content: @Composable () -> Unit,
) {
  RcWearDecoratedContainer(background, fill, cornerRadius, modifier, level, look) {
    RemoteBox(modifier = RemoteModifier.fillMaxSize().padding(horizontalPadding, verticalPadding)) {
      content()
    }
  }
}

/**
 * A Wear widget container whose frame is the watch's: its padding, corner radius and fill are the
 * [RcWearContainer] named values, each defaulting to [defaults] (a Large round container) and to
 * [background]'s base (else Glance Wear's default surface), so one capture fits every host once
 * `RcWearTemplates.instantiate` writes a host's values in. Capture a template's content inside it.
 *
 * Everything [background] and [level] draw follows the named corner radius, so a style's glows,
 * bands, edges and levels hug a 26 dp corner, a 4 dp one and a pill alike.
 */
@Composable
public fun RcWearTemplateContainer(
  modifier: RemoteModifier = RemoteModifier,
  background: RcWearBackground? = null,
  level: RcWearLevel? = null,
  look: RcWearLevelLook? = null,
  defaults: RcWearFrame = RcWearHost.Default.large,
  content: @Composable () -> Unit,
) {
  fun named(name: String, default: Float) =
    RemoteDp.createNamedRemoteDp(name, default, RemoteState.Domain.User)
  val defaultFill = background?.base ?: Color(RcWearContainer.DEFAULT_SURFACE_ARGB)
  RcWearContainerFrame(
    fill =
      RemoteColor.createNamedRemoteColor(
        RcWearContainer.COLOR,
        defaultFill.toArgb(),
        RemoteState.Domain.User,
      ),
    cornerRadius = named(RcWearContainer.CORNER_RADIUS, defaults.cornerRadiusDp),
    horizontalPadding = named(RcWearContainer.HORIZONTAL_PADDING, defaults.horizontalPaddingDp),
    verticalPadding = named(RcWearContainer.VERTICAL_PADDING, defaults.verticalPaddingDp),
    modifier = modifier,
    background = background,
    level = level,
    look = look,
    content = content,
  )
}
