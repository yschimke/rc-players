@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.launcher

import android.content.Context
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcwidget.RcWidgetActionIds
import ee.schimke.composeai.rcwidget.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.androidx.ProvideRcExpressionNames
import ee.schimke.composeai.rcwidget.creation.androidx.ProvideRcWidgetActions
import ee.schimke.composeai.rcwidget.creation.androidx.ProvideRcWidgetCaptureSize

/**
 * Captures [content] as the document for a launcher widget of [size] on this device — the local
 * path, no server involved — through AndroidX's creation API.
 *
 * [content] is composed for a launcher: its size breakpoints pick the layout for [size] now
 * ([ProvideRcWidgetCaptureSize]), its `rcWidgetHostAction`s become numeric host actions, and its
 * expressions are named reproducibly. The launcher's player can't load an image URL, so each URL
 * the document loads is fetched with [fetchImage] (PNG or JPEG bytes; [RcWidgetImages.png] makes
 * them small enough) and embedded; one that can't be fetched stays a URL, which the launcher draws
 * as nothing: give such content its own fallback. [values] are written in as constants, for content that reads named
 * values a launcher can't bind.
 *
 * Captures run [content] on the calling thread; call this off the main thread, as an
 * `AppWidgetProvider` does from `goAsync`.
 *
 * Content written against rc-players' own creation API (`ee.schimke.composeai.rccreation.*`) is
 * captured by `rc-widget-launcher-cmp`'s function of the same name; the document that comes out,
 * and everything after it ([finishLauncherCapture], [RcLauncherWidgets.remoteViews]), is the same.
 */
public suspend fun captureLauncherWidget(
  context: Context,
  size: RcWidgetCaptureSize,
  profile: Profile = RcLauncherWidgets.profileFor(),
  densityDpi: Int = context.resources.displayMetrics.densityDpi,
  values: Map<String, RcHardcodedValue> = emptyMap(),
  fetchImage: (url: String) -> ByteArray? = { null },
  content: @Composable () -> Unit,
): RcLauncherWidgetCapture {
  val ids = RcWidgetActionIds()
  val captured =
    captureSingleRemoteDocument(
      context = context,
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
  return finishLauncherCapture(captured.bytes, ids, values, fetchImage)
}
