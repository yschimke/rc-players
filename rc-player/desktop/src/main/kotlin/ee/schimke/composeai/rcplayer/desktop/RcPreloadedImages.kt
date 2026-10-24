package ee.schimke.composeai.rcplayer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import ee.schimke.composeai.rcplayer.compose.RcImageLoader
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

/**
 * A document's URL and file bitmaps fetched before the first frame, for the headless render.
 *
 * The window can let Coil fill images in as they arrive, but `--render-png` encodes the second
 * frame and closes the scene, which would cancel those requests and leave the images blank. Handing
 * the player a loader whose [cached] already holds every bitmap draws them on the first frame,
 * which is the contract [RcImageLoader.cached] states. Keyed by the bitmap's source rather than its
 * id, so a macro-expanded or remapped copy of the same image still hits; [load] fetches anything
 * the pre-pass did not see, for completeness rather than for this render.
 */
internal class RcPreloadedImages
private constructor(private val images: Map<String, ImageBitmap>, private val network: Boolean) :
  RcImageLoader {
  override fun cached(bitmap: RcBitmapData): ImageBitmap? = images[bitmap.source]

  override suspend fun load(bitmap: RcBitmapData): ImageBitmap? =
    withContext(Dispatchers.IO) { fetch(bitmap.source, network) }

  companion object {
    private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024

    private val client: HttpClient by lazy {
      HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    }

    /** [network] off keeps the render off the network: URL images stay blank, files still load. */
    fun of(document: RcDocument, network: Boolean): RcPreloadedImages =
      RcPreloadedImages(
        document.operations
          .filterIsInstance<RcBitmapData>()
          .filter {
            it.encoding == RcBitmapData.ENCODING_URL || it.encoding == RcBitmapData.ENCODING_FILE
          }
          .map { it.source }
          .distinct()
          .mapNotNull { source -> fetch(source, network)?.let { source to it } }
          .toMap(),
        network,
      )

    private val RcBitmapData.source: String
      get() = data.decodeToString().trim()

    /** An unreachable or undecodable image stays blank, as it does in the window. */
    private fun fetch(source: String, network: Boolean): ImageBitmap? = runCatching {
      val bytes =
        when {
          source.startsWith("https://") || source.startsWith("http://") ->
            if (network) download(source) else null
          source.startsWith("file:") -> File(URI(source)).readBytes()
          else -> File(source).readBytes()
        } ?: return null
      Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }
      .getOrNull()

    private fun download(url: String): ByteArray? {
      val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(20)).GET().build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
      response.body().use { body ->
        if (response.statusCode() != 200) return null
        return body.readNBytes(MAX_IMAGE_BYTES + 1).takeIf { it.size <= MAX_IMAGE_BYTES }
      }
    }
  }
}
