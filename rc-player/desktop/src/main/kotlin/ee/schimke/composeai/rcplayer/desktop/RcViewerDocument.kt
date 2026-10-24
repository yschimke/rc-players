package ee.schimke.composeai.rcplayer.desktop

import ee.schimke.composeai.rcplayer.compose.RcComposeSupportIssue
import ee.schimke.composeai.rcplayer.compose.RcImageLoader
import ee.schimke.composeai.rcplayer.compose.RcTypefaceLoader
import ee.schimke.composeai.rcplayer.compose.composeSupportReport
import ee.schimke.composeai.rcplayer.compose.rcDownloadableFontRequests
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcOperationProfiles
import java.io.File

/**
 * One attempt at opening a `.rc` file, decided before anything is drawn.
 *
 * The viewer is lenient in the sense `RcComposeSupportReport.requirePlayable` means: a document
 * whose only gaps are operations this backend skips still opens, and the skipped operations are
 * listed beside it. A document with a blocking issue would throw from inside the draw pass and take
 * the window's composition with it, so it is refused here instead, with the reason on screen.
 */
internal sealed interface RcViewerLoad {
  val name: String

  class Loaded(
    override val name: String,
    val bytes: ByteArray,
    val document: RcDocument,
    /** The faces for the document's `google:` families, downloaded or falling back to default. */
    val typefaces: RcTypefaceLoader,
    /** URL and file bitmaps: Coil online; offline, local files only and URLs left blank. */
    val images: RcImageLoader,
    /** Operations the CMP desktop backend knows but draws nothing for. */
    val skipped: List<RcComposeSupportIssue>,
  ) : RcViewerLoad {
    val widthPx: Int
      get() = document.header.width

    val heightPx: Int
      get() = document.header.height
  }

  class Failed(override val name: String, val message: String) : RcViewerLoad
}

/**
 * [network] lets the `google:` families a document names be fetched from Google Fonts and its URL
 * bitmaps be loaded. Off, nothing reaches the network: the families draw in the player's default
 * face (as they do when a fetch fails), URL bitmaps stay blank, and file bitmaps still load.
 */
internal fun loadRcViewerDocument(file: File, network: Boolean = true): RcViewerLoad {
  val bytes =
    try {
      file.readBytes()
    } catch (e: Exception) {
      return RcViewerLoad.Failed(file.name, "Could not read ${file.path}: ${e.message ?: e}")
    }
  return loadRcViewerDocument(file.name, bytes, network)
}

internal fun loadRcViewerDocument(
  name: String,
  bytes: ByteArray,
  network: Boolean = true,
): RcViewerLoad {
  val document =
    try {
      RcDocumentCodec.decode(bytes)
    } catch (e: Exception) {
      return RcViewerLoad.Failed(name, "Not a Remote Compose document: ${e.message ?: e}")
    }
  val typefaces =
    RcGoogleFonts.typefaces(rcDownloadableFontRequests(document).map { it.family }, network)
  val report =
    try {
      document.composeSupportReport(
        RcOperationProfiles.CMP_DESKTOP_ALPHA18,
        availableFontFamilies = typefaces.families,
        // The JVM image loader fetches URL bitmaps itself (Coil), as the macOS host's does.
        allowExternalImagePlaceholders = true,
      )
    } catch (e: Exception) {
      return RcViewerLoad.Failed(name, "Could not check the document: ${e.message ?: e}")
    }
  val blocking = report.blockingIssues
  if (blocking.isNotEmpty()) {
    return RcViewerLoad.Failed(
      name,
      "The CMP player cannot draw this document:\n" +
        blocking.joinToString("\n") { "• ${it.operation}[${it.operationIndex}]: ${it.detail}" },
    )
  }
  return RcViewerLoad.Loaded(
    name,
    bytes,
    document,
    typefaces,
    if (network) RcImageLoader.Default else RcPreloadedImages.of(document, network = false),
    report.issues - blocking.toSet(),
  )
}
