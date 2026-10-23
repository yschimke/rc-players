package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred

/**
 * A live player given the next version of its document — the same card, a camera's next frame — as
 * hosts do on every data change. The fixtures are a Home Assistant camera card captured at two
 * frames: a 16:9 picture whose height follows its own measured width, differing only in the
 * picture's URL.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcDocumentSwapTest {
  @Test
  fun aNewDocumentKeepsTheCardsSizeAndItsPictureUntilTheNextLoads() =
    runSkikoComposeUiTest(size = Size(420f, 800f), density = Density(1f)) {
      val next = CompletableDeferred<ImageBitmap?>()
      val loader =
        object : RcImageLoader {
          override suspend fun load(bitmap: RcBitmapData): ImageBitmap? =
            if (bitmap.isFrame(2)) next.await() else solid(Color.Red)

          override fun cached(bitmap: RcBitmapData): ImageBitmap? =
            if (bitmap.isFrame(1)) solid(Color.Red) else null
        }
      var document by mutableStateOf(fixture("AspectRatioPicture-frame1.rc"))
      setContent {
        Box(Modifier.width(320.dp).testTag("card")) { RcComposePlayer(document, loader) }
      }
      waitForIdle()
      val height = cardHeight()
      assertEquals(180.dp, height)
      assertTrue(red() > 0, "the first frame's picture is drawn")

      mainClock.autoAdvance = false
      document = fixture("AspectRatioPicture-frame2.rc")
      // Longer than any of the document's 300 ms layout animations.
      repeat(30) { frame ->
        mainClock.advanceTimeByFrame()
        assertEquals(height, cardHeight(), "frame $frame: the card keeps its height")
        assertTrue(red() > 0, "frame $frame: the previous picture is drawn until the next loads")
      }

      next.complete(solid(Color.Blue))
      mainClock.autoAdvance = true
      waitForIdle()
      assertEquals(height, cardHeight())
      assertEquals(0, red(), "the next picture replaced the previous one")
    }

  private fun SkikoComposeUiTest.cardHeight(): Dp =
    onNodeWithTag("card").getUnclippedBoundsInRoot().let { it.bottom - it.top }

  private fun SkikoComposeUiTest.red(): Int {
    val pixels = onNodeWithTag("card").captureToImage().toPixelMap()
    var count = 0
    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
      if (pixels[x, y] == Color.Red) count++
    }
    return count
  }

  private fun RcBitmapData.isFrame(frame: Int) = data.decodeToString().endsWith("frame=$frame")

  private fun fixture(name: String): RcDocument =
    RcDocumentCodec.decode(
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$name")) { "missing fixture $name" }
        .use { it.readBytes() }
    )

  private fun solid(color: Color): ImageBitmap =
    ImageBitmap(64, 36).also {
      Canvas(it).drawRect(Rect(0f, 0f, 64f, 36f), Paint().apply { this.color = color })
    }
}
