package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch

/**
 * Host image loading for URL and file [RcBitmapData] payloads. The player supplies the original
 * operation, including its id, dimensions, encoding and bytes. Coil loads images by default;
 * overrides own fetching, decoding, caching and access policy. [load] must not block the Compose
 * dispatcher. Return null for an unavailable image, and handle recoverable loading errors in the
 * host.
 *
 * Requests run once per bitmap and loader instance. Replacing the document or loader, or removing
 * the player, cancels outstanding requests. Images are blank until loaded; completion updates
 * layout and canvas drawing. Inline images continue to use the player's decoder.
 */
public fun interface RcImageLoader {
  public suspend fun load(bitmap: RcBitmapData): ImageBitmap?

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
  inlineImages: Map<Int, ImageBitmap>,
  onLoaded: () -> Unit,
): MutableMap<Int, ImageBitmap> {
  val configured = LocalRcImageLoader.current
  val loader = if (configured === RcImageLoader.Default) rememberCoilRcImageLoader() else configured
  val notifyLoaded by rememberUpdatedState(onLoaded)
  val images = remember(document, loader) { inlineImages.toMutableMap() }
  LaunchedEffect(document, loader) {
    document.operations
      .filterIsInstance<RcBitmapData>()
      .filter {
        it.encoding == RcBitmapData.ENCODING_URL || it.encoding == RcBitmapData.ENCODING_FILE
      }
      .associateBy { it.imageId }
      .values
      .forEach { bitmap ->
        launch {
          loader.load(bitmap)?.let {
            images[bitmap.imageId] = it
            notifyLoaded()
          }
        }
      }
  }
  return images
}
