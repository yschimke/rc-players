package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * A document that is one canvas of [width] x [height] pixels (density 1) drawing [onDraw], with the
 * bitmaps it draws carried inline.
 */
internal fun captureCanvas(width: Int, height: Int, onDraw: RemoteDrawScope.() -> Unit): ByteArray =
  runBlocking {
    captureCommonRemoteDocument(
      RemoteCreationDisplayInfo(width, height, 160),
      encodePng = { bitmap ->
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
      },
    ) {
      RemoteCanvas(RemoteModifier.fillMaxSize(), onDraw)
    }
  }

/** The first frame the CMP player draws of [bytes], on a transparent background. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
internal fun playDocument(bytes: ByteArray, width: Int, height: Int): ImageBitmap {
  val document = RcDocumentCodec.decode(bytes)
  lateinit var image: ImageBitmap
  runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), Density(1f)) {
    setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
    waitForIdle()
    image = onRoot().captureToImage()
    // The player decodes a bitmap off the frame and repaints when it has it: wait until the
    // picture has stopped changing.
    if (document.operations.any { it is RcBitmapData }) {
      var stable = 0
      var polls = 0
      while (stable < 8 && polls++ < 80) {
        Thread.sleep(25)
        waitForIdle()
        val next = onRoot().captureToImage()
        stable = if (next.samePixelsAs(image)) stable + 1 else 0
        image = next
      }
    }
  }
  return image
}

private fun ImageBitmap.samePixelsAs(other: ImageBitmap): Boolean {
  val mine = toPixelMap()
  val theirs = other.toPixelMap()
  return mine.buffer.contentEquals(theirs.buffer)
}

/** [captureCanvas] played by [playDocument]. */
internal fun playCanvas(width: Int, height: Int, onDraw: RemoteDrawScope.() -> Unit): PixelMap =
  playDocument(captureCanvas(width, height, onDraw), width, height).toPixelMap()

internal fun assertColor(expected: Color, actual: Color, what: String, tolerance: Float = 0.04f) {
  assertTrue(
    abs(expected.red - actual.red) <= tolerance &&
      abs(expected.green - actual.green) <= tolerance &&
      abs(expected.blue - actual.blue) <= tolerance &&
      abs(expected.alpha - actual.alpha) <= tolerance,
    "$what: expected $expected, was $actual",
  )
}

/**
 * Writes [cells] side by side, each enlarged [scale] times on a grey checkerboard, to
 * `<RC_LAYOUT_EVIDENCE_DIR>/<name>.png`. Does nothing when the variable is not set.
 */
internal fun writeEvidence(name: String, cells: List<ImageBitmap>, scale: Int = 4) {
  val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
  directory.mkdirs()
  val gap = 8
  val width = cells.sumOf { it.width * scale } + gap * (cells.size + 1)
  val height = cells.maxOf { it.height * scale } + gap * 2
  val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
  val graphics = sheet.createGraphics()
  graphics.color = java.awt.Color.WHITE
  graphics.fillRect(0, 0, width, height)
  var x = gap
  for (cell in cells) {
    val cellWidth = cell.width * scale
    val cellHeight = cell.height * scale
    for (cy in 0 until cellHeight step 8) {
      for (cx in 0 until cellWidth step 8) {
        graphics.color =
          if ((cx / 8 + cy / 8) % 2 == 0) java.awt.Color(0xEE, 0xEE, 0xEE)
          else java.awt.Color(0xCC, 0xCC, 0xCC)
        graphics.fillRect(x + cx, gap + cy, minOf(8, cellWidth - cx), minOf(8, cellHeight - cy))
      }
    }
    graphics.drawImage(cell.toAwtImage(), x, gap, cellWidth, cellHeight, null)
    x += cellWidth + gap
  }
  graphics.dispose()
  ImageIO.write(sheet, "png", directory.resolve("$name.png"))
}
