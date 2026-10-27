@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.composeai.rcwidget.launcher

import android.content.Context
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.edit.embedBitmapUrls
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetActionIds
import ee.schimke.composeai.rcwidget.RcWidgetDocuments
import ee.schimke.composeai.rcwidget.creation.ProvideRcExpressionNames
import ee.schimke.composeai.rcwidget.creation.ProvideRcWidgetActions
import ee.schimke.composeai.rcwidget.creation.ProvideRcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize

/** A launcher widget captured on the device: its document, and its taps' payloads by action id. */
public class RcLauncherWidgetCapture(public val bytes: ByteArray, public val actions: Map<Int, String>)

/**
 * Captures [content] as the document for a launcher widget of [size] on this device — the local
 * path, no server involved.
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
          dpToPx(size.widthDp, densityDpi),
          dpToPx(size.heightDp, densityDpi),
          densityDpi,
        ),
      profile = profile,
    ) {
      ProvideRcWidgetCaptureSize(size) {
        ProvideRcWidgetActions(ids) { ProvideRcExpressionNames(content) }
      }
    }
  val document = RcDocumentCodec.decode(captured.bytes, RcWireLimits())
  val baked = RcWidgetDocuments.bake(document.embedBitmapUrls(fetchImage), values)
  return RcLauncherWidgetCapture(RcDocumentCodec.encode(baked), ids.entries)
}

internal fun dpToPx(dp: Int, densityDpi: Int): Int = (dp * densityDpi / 160f).toInt()
