package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnPath
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface

/**
 * Text on a path draws inside a canvas component as it does in the document's own draw stream: the
 * glyphs a canvas lays along its path reach the frame, not just the draw observer.
 *
 * With `-Prc.canvasPathText.out=<dir>` it also writes the canvas's frame, the evidence under
 * `renders/rc-canvas-path-text/`.
 */
class RcCanvasTextOnPathRenderTest {
  @Test
  fun textOnAPathInACanvasDraws() {
    val bare = inked(listOf(TEXT, PATH) + DRAWS)
    val canvas =
      inked(
        out = System.getProperty("rc.canvasPathText.out")?.let { File(it, "canvas.png") },
        operations =
          listOf(
            TEXT,
            PATH,
            RcRootLayout(-1),
            RcLayoutContent(-2),
            RcCanvasLayout(-3, -1),
            RcWidthModifier(RcDimensionType.FILL, f(0f)),
            RcHeightModifier(RcDimensionType.FILL, f(0f)),
            RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
          ) + DRAWS + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
      )
    assertTrue(bare > 200, "the text draws in the document's stream: $bare pixels")
    assertTrue(canvas > bare / 2, "and in a canvas: $canvas pixels against $bare")
  }

  /** How many pixels [operations] ink in a 256px square. */
  private fun inked(operations: List<RcOperation>, out: File? = null): Int {
    val document = RcDocument(RcHeader(RcVersion(1, 1, 0)), operations)
    val scene =
      ImageComposeScene(256, 256, density = Density(2f)) {
        RcComposePlayer(document, Modifier.fillMaxSize())
      }
    return try {
      val bitmap = Bitmap().apply { allocN32Pixels(256, 256) }
      val frame = scene.render(0L)
      assertTrue(frame.readPixels(bitmap))
      out
        ?.apply { parentFile.mkdirs() }
        ?.let { file ->
          val surface = Surface.makeRasterN32Premul(256, 256)
          surface.canvas.clear(Color.makeRGB(40, 40, 48))
          surface.canvas.drawImage(frame, 0f, 0f)
          file.writeBytes(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
      var count = 0
      for (y in 0 until 256) for (x in 0 until 256) if (bitmap.getColor(x, y) ushr 24 != 0) count++
      count
    } finally {
      scene.close()
    }
  }

  private companion object {
    fun f(value: Float) = RcFloatWord.literal(value)

    fun command(id: Int) = RcFloatWord(0xff800000.toInt() or id)

    val TEXT = RcTextData(42, "ORBITRON")
    val PATH =
      RcPathData(
        70,
        listOf(
          command(RcPathCommands.MOVE),
          f(24f),
          f(200f),
          command(RcPathCommands.CUBIC),
          f(24f),
          f(200f),
          f(24f),
          f(40f),
          f(232f),
          f(40f),
          f(232f),
          f(200f),
        ),
      )
    val DRAWS =
      listOf(
        RcPaintData(listOf(1, f(22f).bits, 4, 0xffffffff.toInt())),
        RcDrawTextOnPath(42, 70, f(30f), f(0f)),
      )
  }
}
