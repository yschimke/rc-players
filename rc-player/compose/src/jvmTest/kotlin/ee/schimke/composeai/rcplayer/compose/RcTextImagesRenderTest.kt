package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.edit.RcApproximation
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.adaptTo
import ee.schimke.composeai.rcplayer.edit.coreTextAsImages
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

/**
 * `rc-player-edit`'s `coreTextAsImages` held to what the text looked like as `CoreText`: catalog
 * documents served to Wear widgets, which have no `CoreText`, with their styled texts drawn by
 * [RcComposeTextRasterizer] at the widths [coreTextWidths] measured — against the same documents
 * with that styling dropped instead.
 *
 * With `-Prc.textImages.out=<dir>` it also writes each document as a strip — the `CoreText`
 * original, the images, the dropped styling — over a dark backdrop, the evidence under
 * `renders/rc-text-images/`.
 */
class RcTextImagesRenderTest {
  private val typefaces = runBlocking {
    RcManifestTypefaceLoader { url -> File(url).readBytes() }.load(FONTS)
  }
  private val rasterizer = RcComposeTextRasterizer(typefaces)

  @Test
  fun styledTextDrawnAsImagesLooksAsItDidAsCoreText() {
    val out = System.getProperty("rc.textImages.out")?.let(::File)?.apply { mkdirs() }
    for (name in SAMPLES) {
      // Named colours stay live: the Wear host sets them, so a text in one becomes a tinted mask.
      val original = RcDocumentCodec.decode(File("$CORPUS/$name.rc").readBytes())
      val widths = original.coreTextWidths(DENSITY, typefaces)
      val drawn = original.coreTextAsImages(TARGET, rasterizer, RcDisplay(DENSITY)) { widths[it] }
      assertTrue(drawn.images.isNotEmpty(), "$name: no text was drawn")
      val dropped = original.adaptTo(TARGET, setOf(RcApproximation.TEXT)).document

      val before = render(original)
      val images = render(drawn.document)
      val approximated = render(dropped)
      val imageDiff = differing(before, images)
      val droppedDiff = differing(before, approximated)
      out?.let { File(it, "$name.png").writeBytes(strip(original, before, images, approximated)) }
      assertTrue(
        imageDiff < 0.01 && imageDiff < droppedDiff,
        "$name: images differ from CoreText in $imageDiff of pixels, dropped styling in $droppedDiff",
      )
    }
  }

  /**
   * The share of pixels whose channels differ by more than [TOLERANCE]: an image of text blended
   * over a card rounds its antialiased edges apart from the text drawn there directly, and a white
   * mask tinted to the text's colour further still, by up to 16 levels at a glyph's edge.
   */
  private fun differing(a: Bitmap, b: Bitmap): Double {
    var count = 0
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val p = a.getColor(x, y)
        val q = b.getColor(x, y)
        if ((0..24 step 8).any { abs((p ushr it and 0xff) - (q ushr it and 0xff)) > TOLERANCE }) {
          count++
        }
      }
    }
    return count.toDouble() / (a.width * a.height)
  }

  private fun render(document: RcDocument): Bitmap {
    val width = document.header.width
    val height = document.header.height
    val scene =
      ImageComposeScene(width, height, density = Density(DENSITY)) {
        RcComposePlayer(document, typefaces = typefaces)
      }
    return try {
      Bitmap().apply {
        allocN32Pixels(width, height)
        assertTrue(scene.render(0L).readPixels(this))
      }
    } finally {
      scene.close()
    }
  }

  private fun strip(document: RcDocument, vararg frames: Bitmap): ByteArray {
    val width = document.header.width
    val surface =
      Surface.makeRasterN32Premul(
        width * frames.size + GAP * (frames.size - 1),
        document.header.height,
      )
    surface.canvas.clear(Color.makeRGB(40, 40, 48))
    frames.forEachIndexed { index, frame ->
      surface.canvas.drawImage(Image.makeFromBitmap(frame), (index * (width + GAP)).toFloat(), 0f)
    }
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
  }

  private companion object {
    const val DENSITY = 2f
    const val GAP = 12
    const val TOLERANCE = 16
    const val CORPUS = "../../scripts/rc-catalog-corpus/corpus"
    const val FONTS = "../../rc-player/wasm/dist-assets/fonts"
    val TARGET = RcTargetProfiles.WEAR_WIDGETS
    val SAMPLES =
      listOf(
        "appcard__ideal__default__compact",
        "theme-specimen-droidcon__ideal__default__compact",
        "typeface-variablewidth__ideal__default__compact",
        "typeface-specimen__ideal__default__compact",
        "text-maxlines-truncated__ideal__default__compact",
        "scaffold__ideal__default__compact",
      )
  }
}
