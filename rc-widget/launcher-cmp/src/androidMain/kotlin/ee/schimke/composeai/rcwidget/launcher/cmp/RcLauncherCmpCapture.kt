@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.launcher.cmp

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.common.Profile
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcwidget.RcWidgetActionIds
import ee.schimke.composeai.rcwidget.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.ProvideRcExpressionNames
import ee.schimke.composeai.rcwidget.creation.ProvideRcWidgetActions
import ee.schimke.composeai.rcwidget.creation.ProvideRcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.RcCaptureProfiles
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgetCapture
import ee.schimke.composeai.rcwidget.launcher.RcPlatformWidgets
import ee.schimke.composeai.rcwidget.launcher.RcWidgetSizing
import ee.schimke.composeai.rcwidget.launcher.finishLauncherCapture

/**
 * The capture profile for this device's launcher on rc-players' creation API: system colours where
 * it resolves them ([RcCaptureProfiles.systemThemedLauncherWidgets]), else what Android 16 reads
 * ([RcCaptureProfiles.launcherWidgets]). The counterpart of AndroidX's
 * `RcLauncherWidgets.profileFor`, with the same level, bits and operations.
 */
public fun launcherCaptureProfile(sdkInt: Int = Build.VERSION.SDK_INT): Profile =
  RcCaptureProfiles.forTarget(RcPlatformWidgets.widgetTargetFor(sdkInt))

/**
 * Captures [content], written against rc-players' creation API, as the document for a launcher
 * widget of [size] on a display of [densityDpi] — the local path, no server involved, and no
 * `Context`: rc-players' capture needs none, so this runs as it does on a design server.
 *
 * What [captureLauncherWidget] (`rc-widget-launcher`) does for AndroidX's creation API, with the
 * same result: [content] is composed for a launcher (its size breakpoints pick the layout for
 * [size] now, its `rcWidgetHostAction`s become numeric host actions, its expressions are named
 * reproducibly), then [finishLauncherCapture] fetches and embeds each image URL with [fetchImage]
 * and writes [values] in as constants.
 *
 * Captures run [content] on the calling thread; call this off the main thread, as an
 * `AppWidgetProvider` does from `goAsync`.
 */
public suspend fun captureLauncherWidget(
  size: RcWidgetCaptureSize,
  profile: Profile = launcherCaptureProfile(),
  densityDpi: Int = Resources.getSystem().displayMetrics.densityDpi,
  values: Map<String, RcHardcodedValue> = emptyMap(),
  fetchImage: (url: String) -> ByteArray? = { null },
  content: @Composable () -> Unit,
): RcLauncherWidgetCapture {
  val ids = RcWidgetActionIds()
  val captured =
    captureCommonRemoteDocument(
      creationDisplayInfo =
        RemoteCreationDisplayInfo(
          RcWidgetSizing.dpToPx(size.widthDp, densityDpi),
          RcWidgetSizing.dpToPx(size.heightDp, densityDpi),
          densityDpi,
        ),
      profile = profile,
    ) {
      ProvideRcWidgetCaptureSize(size) {
        ProvideRcWidgetActions(ids) { ProvideRcExpressionNames(content) }
      }
    }
  return finishLauncherCapture(captured, ids, values, fetchImage)
}

/**
 * [captureLauncherWidget] on this [context]'s display, for a call site that has one at hand and
 * reads as `rc-widget-launcher`'s does: `captureLauncherWidget(context, slot) { … }`.
 */
public suspend fun captureLauncherWidget(
  context: Context,
  size: RcWidgetCaptureSize,
  profile: Profile = launcherCaptureProfile(),
  densityDpi: Int = context.resources.displayMetrics.densityDpi,
  values: Map<String, RcHardcodedValue> = emptyMap(),
  fetchImage: (url: String) -> ByteArray? = { null },
  content: @Composable () -> Unit,
): RcLauncherWidgetCapture =
  captureLauncherWidget(size, profile, densityDpi, values, fetchImage, content)
