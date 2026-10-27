@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.wear

import android.content.Context
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensity
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.runtime.Composable
import androidx.glance.wear.WearWidgetBrush
import androidx.glance.wear.WearWidgetDocument
import androidx.glance.wear.core.WearWidgetParams
import androidx.glance.wear.core.WearWidgetRawContent
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWearContainerType
import ee.schimke.composeai.rcwidget.RcWearFrame
import ee.schimke.composeai.rcwidget.RcWearTemplates
import ee.schimke.composeai.rcwidget.creation.ProvideRcExpressionNames
import ee.schimke.composeai.rcwidget.creation.RcWearBackground
import ee.schimke.composeai.rcwidget.creation.RcWearLevel
import ee.schimke.composeai.rcwidget.creation.RcWearLevelLook
import ee.schimke.composeai.rcwidget.creation.RcWearTemplateContainer
import ee.schimke.composeai.rcwidget.creation.androidXExperimentalWrap

/**
 * Wear widget templates captured on the watch (or the phone) itself: one capture per design, with
 * the widget container's padding, corner radius and fill left as `RcWearContainer` named values, made
 * into each widget's document by [instantiate].
 *
 * The capture uses the app's own Remote Compose profile (so its text may be `CoreText`) at host
 * density, which lets the same template also play in the app ([RcWearTemplates.forApp]) with live
 * values. Instantiating follows Glance Wear's own `WearWidgetDocument.captureRawContent`: the
 * template takes the header Glance Wear writes for the widget's params, the watch's density and
 * font scale are folded in, and what the Wear widget player can't read is rewritten into what it
 * can.
 */
public object RcWearWidgetTemplate {
  /** The capture canvas, at mdpi: [instantiate] replaces it with the widget's. */
  private val CANVAS = RemoteCreationDisplayInfo(200, 112, 160)

  /**
   * [content] inside a host-framed Wear container ([RcWearTemplateContainer]) with [background],
   * [level] and [look], captured as a template. Expressions are named in composition order, so a
   * design drawn on its own or wrapped by another names them alike and recaptures to the same bytes.
   */
  public suspend fun capture(
    context: Context,
    background: RcWearBackground? = null,
    level: RcWearLevel? = null,
    look: RcWearLevelLook? = null,
    profile: Profile = androidXExperimentalWrap,
    content: @Composable () -> Unit,
  ): ByteArray =
    captureSingleRemoteDocument(
        context = context,
        creationDisplayInfo = CANVAS,
        remoteDensity = RemoteDensity.Host,
        profile = profile,
      ) {
        ProvideRcExpressionNames {
          RcWearTemplateContainer(background = background, level = level, look = look) { content() }
        }
      }
      .bytes

  /**
   * [template] as the document for a widget with [params] on this watch, with [values] written in
   * and the container filled with [containerArgb] (a state tint; null keeps the template's default).
   * Null when it can't be made playable as a Wear widget: capture through Glance Wear instead.
   */
  public suspend fun instantiate(
    context: Context,
    template: ByteArray,
    params: WearWidgetParams,
    isPreview: Boolean = false,
    containerArgb: Int? = null,
    values: Map<String, RcHardcodedValue> = emptyMap(),
  ): WearWidgetRawContent? {
    // Glance Wear's own capture of an empty container: its header (canvas, density, profile) and
    // extras are exactly what this widget's document carries, whichever Glance Wear is installed.
    val frame =
      WearWidgetDocument(WearWidgetBrush.Companion) {}.captureRawContent(context, params, isPreview)
    val resources = context.resources
    val display = RcDisplay(resources.displayMetrics.density, resources.configuration.fontScale)
    val bytes =
      runCatching {
          val header = RcDocumentCodec.decode(frame.rcDocument, RcWireLimits()).header
          val adapted =
            RcWearTemplates.instantiate(
              RcDocumentCodec.decode(template, RcWireLimits()),
              params.toRcWearFrame(),
              display,
              containerArgb,
              values,
              header,
            )
          if (adapted.isPlayable) RcDocumentCodec.encode(adapted.document) else null
        }
        .getOrNull() ?: return null
    return WearWidgetRawContent(bytes, frame.extras, frame.widgetTitle)
  }
}

/** These params as the frame `rc-widget-core` reads: the host's container on this watch. */
public fun WearWidgetParams.toRcWearFrame(): RcWearFrame =
  RcWearFrame(
    RcWearContainerType.ofGlance(containerType) ?: RcWearContainerType.LARGE,
    widthDp,
    heightDp,
    horizontalPaddingDp,
    verticalPaddingDp,
    cornerRadiusDp,
  )
