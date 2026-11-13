package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap

/**
 * [RcPreparation.Background] never prepares a document in composition: a document the cache already
 * holds plays on its first frame, and one it does not is prepared off the composition and drawn
 * when it is ready.
 */
class RcPreparationTest {

  @Test
  fun aDocumentPreparedAheadDrawsOnTheFirstFrameWithoutPreparingAgain() {
    val cache = RcPreparedDocumentCache()
    val bytes = greenSquare()
    runBlocking { cache.prepare(bytes) }
    val scene =
      ImageComposeScene(width = 20, height = 20, density = Density(1f)) {
        CompositionLocalProvider(LocalRcPreparation provides RcPreparation.Background(cache)) {
          // Equal bytes, another array: the cache is keyed by content, not identity.
          RcComposePlayer(bytes.copyOf(), Modifier.size(20.dp))
        }
      }
    try {
      assertEquals(400, scene.render(0L).greenPixels())
      assertEquals(1, cache.prepared)
      assertEquals(0, cache.misses)
    } finally {
      scene.close()
    }
  }

  @Test
  fun aDocumentNotYetPreparedIsPreparedOffTheCompositionAndThenDrawn() {
    val cache = RcPreparedDocumentCache()
    val bytes = greenSquare()
    val scene =
      ImageComposeScene(width = 20, height = 20, density = Density(1f)) {
        CompositionLocalProvider(
          LocalRcPreparation provides RcPreparation.Background(cache, Dispatchers.Default),
        ) {
          RcComposePlayer(bytes, Modifier.size(20.dp))
        }
      }
    try {
      assertEquals(0, scene.render(0L).greenPixels(), "drawn before it was prepared")
      assertEquals(1, cache.misses)
      var frame = 1L
      var green = 0
      while (green == 0 && frame < 200) {
        Thread.sleep(5)
        green = scene.render(frame++ * FRAME_NANOS).greenPixels()
      }
      assertEquals(400, green)
      assertEquals(1, cache.prepared)
    } finally {
      scene.close()
    }
  }

  @Test
  fun theCacheReturnsOnePreparedDocumentPerContent() {
    val cache = RcPreparedDocumentCache()
    val bytes = greenSquare()
    val first = runBlocking { cache.prepare(bytes) }
    assertSame(first, runBlocking { cache.prepare(bytes.copyOf()) })
    assertSame(first, cache.peek(bytes.copyOf()))
    assertEquals(1, cache.prepared)
  }

  @Test
  fun concurrentPreparationsOfOneDocumentShareOne() {
    val cache = RcPreparedDocumentCache()
    val bytes = greenSquare()
    val all = runBlocking {
      List(8) { async(Dispatchers.Default) { cache.prepare(bytes.copyOf()) } }.awaitAll()
    }
    assertEquals(1, cache.prepared)
    all.forEach { assertSame(all.first(), it) }
  }

  private fun org.jetbrains.skia.Image.greenPixels(): Int {
    val image = this
    val bitmap = Bitmap().apply { allocN32Pixels(image.width, image.height) }
    check(image.readPixels(bitmap))
    var count = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) if (bitmap.getColor(x, y) == GREEN) count++
    }
    return count
  }

  private fun greenSquare(): ByteArray =
    RcDocumentCodec.encode(
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 20, legacyHeight = 20, modern = false),
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          RcBoxLayout(3, -1, 1, 4),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
          RcBackgroundModifier(
            flags = 0,
            colorId = 0,
            reserved1 = 0,
            reserved2 = 0,
            red = RcFloatWord.literal(0f),
            green = RcFloatWord.literal(1f),
            blue = RcFloatWord.literal(0f),
            alpha = RcFloatWord.literal(1f),
            shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
          ),
          RcLayoutContent(4),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      ),
    )

  private companion object {
    const val GREEN = 0xff00ff00.toInt()
    const val FRAME_NANOS = 16_666_667L
  }
}
