package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.runtime.RcLinkedDocument
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode
import kotlinx.coroutines.launch

/**
 * Host image loading for URL and file [RcBitmapData] payloads. The player supplies the original
 * operation, including its id, dimensions, encoding and bytes. Coil loads images by default;
 * overrides own fetching, decoding, caching and access policy. [load] must not block the Compose
 * dispatcher. Return null for an unavailable image, and handle recoverable loading errors in the
 * host.
 *
 * Requests run once per bitmap and loader instance. Replacing the document or loader, or removing
 * the player, cancels outstanding requests. An image the host already holds ([cached]) is drawn on
 * the player's first frame; any other is blank until loaded, and completion updates layout and
 * canvas drawing. A replaced document keeps drawing the previous document's image for the same id
 * until the new one loads, so a changed picture swaps rather than blinking; one that loads as null
 * is cleared. Inline images continue to use the player's decoder.
 */
public fun interface RcImageLoader {
  public suspend fun load(bitmap: RcBitmapData): ImageBitmap?

  /**
   * [bitmap]'s image when the host holds it already (a memory cache hit), else null. Called during
   * composition, so it must not suspend, block or do I/O. A non-null result is drawn on the first
   * frame and [load] is not called for that bitmap.
   */
  public fun cached(bitmap: RcBitmapData): ImageBitmap? = null

  public companion object {
    /** Use the app's Coil singleton loader, resolved in the player's composition. */
    public val Default: RcImageLoader = CoilDefault

    /** Disable external image loading. Inline images still use the player's decoder. */
    public val Empty: RcImageLoader = RcImageLoader { null }
  }
}

/**
 * Image loader inherited by players in this composition; may also be passed to [RcComposePlayer].
 */
public val LocalRcImageLoader: androidx.compose.runtime.ProvidableCompositionLocal<RcImageLoader> =
  compositionLocalOf {
    RcImageLoader.Default
  }

private object CoilDefault : RcImageLoader {
  override suspend fun load(bitmap: RcBitmapData): ImageBitmap? = null
}

/** Uses Coil's singleton, including the app's cache, fetchers and request configuration. */
@Composable
public fun rememberCoilRcImageLoader(): RcImageLoader {
  val context = LocalPlatformContext.current
  return remember(context) {
    val coil = SingletonImageLoader.get(context)
    RcImageLoader { bitmap ->
      val source = bitmap.data.decodeToString()
      if (source.isBlank()) return@RcImageLoader null
      val request = ImageRequest.Builder(context).data(source).size(Size.ORIGINAL).build()
      (coil.execute(request) as? SuccessResult)?.image?.toRcImageBitmap()
    }
  }
}

internal expect fun Image.toRcImageBitmap(): ImageBitmap

@Composable
internal fun rememberRcImages(
  document: RcDocument,
  linked: RcLinkedDocument,
  inlineImages: Map<Int, ImageBitmap>,
  onLoaded: () -> Unit,
): MutableMap<Int, ImageBitmap> {
  val configured = LocalRcImageLoader.current
  val loader = if (configured === RcImageLoader.Default) rememberCoilRcImageLoader() else configured
  val notifyLoaded by rememberUpdatedState(onLoaded)
  val external = remember(document, linked) { externalBitmaps(document, linked) }
  // The images the last document drew, so a replacement keeps them while its own load.
  val previous = remember { arrayOf<Map<Int, ImageBitmap>>(emptyMap()) }
  val seeded =
    remember(document, loader) {
      val images = inlineImages.toMutableMap()
      val fromHost = HashSet<Int>()
      for (bitmap in external) {
        val cached = loader.cached(bitmap)
        if (cached != null) fromHost += bitmap.imageId
        (cached ?: previous[0][bitmap.imageId])?.let { images[bitmap.imageId] = it }
      }
      SeededImages(images, fromHost)
    }
  val images = seeded.images
  SideEffect { previous[0] = images }
  LaunchedEffect(document, loader) {
    external
      .filter { it.imageId !in seeded.fromHost }
      .forEach { bitmap ->
        launch {
          val loaded = loader.load(bitmap)
          if (loaded != null) {
            images[bitmap.imageId] = loaded
            notifyLoaded()
          } else if (images.remove(bitmap.imageId) != null) {
            // A carried-over image whose replacement is unavailable: don't keep drawing it.
            notifyLoaded()
          }
        }
      }
  }
  return images
}

/** A document's first images, and the ids among them [RcImageLoader.cached] supplied. */
private class SeededImages(val images: MutableMap<Int, ImageBitmap>, val fromHost: Set<Int>)

/**
 * The URL and file bitmaps [document] declares, one per image id: its wire declarations, including
 * referenced definitions, and expanded macro bodies. Linked operations carry the remapped ids the
 * renderer uses.
 */
private fun externalBitmaps(document: RcDocument, linked: RcLinkedDocument): List<RcBitmapData> {
  val bitmaps = document.operations.filterIsInstance<RcBitmapData>().toMutableList()
  fun visit(node: RcLinkedNode) {
    when (node) {
      is RcLinkedNode.Operation -> (node.operation as? RcBitmapData)?.let(bitmaps::add)
      is RcLinkedNode.Container -> node.children.forEach(::visit)
    }
  }
  linked.operations.forEach(::visit)
  return bitmaps
    .filter {
      it.encoding == RcBitmapData.ENCODING_URL || it.encoding == RcBitmapData.ENCODING_FILE
    }
    .associateBy { it.imageId }
    .values
    .toList()
}
