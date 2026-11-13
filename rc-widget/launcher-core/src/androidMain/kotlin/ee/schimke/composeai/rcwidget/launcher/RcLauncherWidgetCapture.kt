package ee.schimke.composeai.rcwidget.launcher

import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.edit.embedBitmapUrls
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWidgetActionIds
import ee.schimke.composeai.rcwidget.RcWidgetDocuments

/** A launcher widget captured on the device: its document, and its taps' payloads by action id. */
public class RcLauncherWidgetCapture(public val bytes: ByteArray, public val actions: Map<Int, String>)

/**
 * The half of a local launcher capture that doesn't depend on which creation API wrote the
 * document: [captured], the bytes the capture wrote (`captureSingleRemoteDocument` on AndroidX's,
 * `captureCommonRemoteDocument` on rc-players'), made into what a launcher plays.
 *
 * Each image URL the document loads is fetched with [fetchImage] (PNG or JPEG bytes;
 * [RcWidgetImages.png] makes them small enough) and embedded, because the launcher's player can't
 * load a URL: one that can't be fetched stays a URL, which the launcher draws as nothing. [values]
 * are written in as constants, for content that reads named values a launcher can't bind. [ids] are
 * the numeric host actions the composition registered ([RcWidgetActionIds]).
 *
 * `rc-widget-launcher`'s and `rc-widget-launcher-cmp`'s `captureLauncherWidget` call this; call it
 * directly to capture through a writer of your own.
 */
public fun finishLauncherCapture(
  captured: ByteArray,
  ids: RcWidgetActionIds,
  values: Map<String, RcHardcodedValue> = emptyMap(),
  fetchImage: (url: String) -> ByteArray? = { null },
): RcLauncherWidgetCapture {
  val document = RcDocumentCodec.decode(captured, RcWireLimits())
  val baked = RcWidgetDocuments.bake(document.embedBitmapUrls(fetchImage), values)
  return RcLauncherWidgetCapture(RcDocumentCodec.encode(baked), ids.entries)
}
