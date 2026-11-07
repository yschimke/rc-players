package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * The player declares one Ktor engine per platform (see `rc-player/compose/build.gradle.kts`), and
 * coil-network-ktor3 fetches documents' remote images with `HttpClient()`, which takes whichever
 * engine the classpath offers. On the JVM that must be OkHttp, and only OkHttp: a second engine
 * makes the choice arbitrary, for the player and for the app's own default clients. An app that
 * configures its own client injects it instead.
 */
class RcHttpEngineTest {
  @Test
  fun defaultClientUsesOkHttp() {
    HttpClient().use { client -> assertEquals("OkHttpEngine", client.engine::class.simpleName) }
  }

  @Test
  fun injectedClientFetchesDocumentImages() {
    val requested = mutableListOf<String>()
    val png =
      Image.makeFromBitmap(Bitmap().apply { allocN32Pixels(2, 2) })
        .encodeToData(EncodedImageFormat.PNG)!!
        .bytes
    val client =
      HttpClient(MockEngine) {
        engine {
          addHandler { request ->
            requested += request.url.toString()
            respond(png, headers = headersOf(HttpHeaders.ContentType, "image/png"))
          }
        }
      }
    var loader: RcImageLoader? = null
    val scene =
      ImageComposeScene(width = 10, height = 10, density = Density(1f)) {
        loader = rememberCoilRcImageLoader(client)
      }
    try {
      scene.render()
    } finally {
      scene.close()
    }
    // Unique per run: the default singleton keeps a disk cache across runs.
    val url = "https://images.example/injected-${java.util.UUID.randomUUID()}.png"
    val image = runBlocking {
      assertNotNull(loader)
        .load(RcBitmapData(1, 2, 2, 0, RcBitmapData.ENCODING_URL, url.encodeToByteArray()))
    }
    assertEquals(listOf(url), requested)
    assertNotNull(image)
    assertEquals(2, image.width)
  }

  @Test
  fun injectedClientKeepsTheSingletonsComponents() {
    // An app's own interceptor on its Coil singleton still runs when the client is injected.
    val intercepted = mutableListOf<String>()
    val png =
      Image.makeFromBitmap(Bitmap().apply { allocN32Pixels(2, 2) })
        .encodeToData(EncodedImageFormat.PNG)!!
        .bytes
    val requested = mutableListOf<String>()
    val client =
      HttpClient(MockEngine) {
        engine {
          addHandler { request ->
            requested += request.url.toString()
            respond(png, headers = headersOf(HttpHeaders.ContentType, "image/png"))
          }
        }
      }
    val context = PlatformContext.INSTANCE
    SingletonImageLoader.setUnsafe(
      ImageLoader.Builder(context)
        .components {
          add(
            Interceptor { chain ->
              intercepted += chain.request.data.toString()
              chain.proceed()
            },
          )
        }
        .build(),
    )
    try {
      var loader: RcImageLoader? = null
      val scene =
        ImageComposeScene(width = 10, height = 10, density = Density(1f)) {
          loader = rememberCoilRcImageLoader(client)
        }
      try {
        scene.render()
      } finally {
        scene.close()
      }
      val url = "https://images.example/intercepted-${java.util.UUID.randomUUID()}.png"
      val image = runBlocking {
        assertNotNull(loader)
          .load(RcBitmapData(1, 2, 2, 0, RcBitmapData.ENCODING_URL, url.encodeToByteArray()))
      }
      assertNotNull(image)
      assertEquals(listOf(url), intercepted)
      assertEquals(listOf(url), requested)
    } finally {
      SingletonImageLoader.reset()
    }
  }
}
