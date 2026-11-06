package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.coreTextAsBitmapFonts
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcTextData
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
 * `rc-player-edit`'s `coreTextAsBitmapFonts` held to what the text looked like as `CoreText`: the
 * typeface specimen, its texts in each requested face drawn from glyphs [RcComposeTextRasterizer]
 * drew, played by this player beside the original.
 *
 * With `-Prc.bitmapFonts.out=<dir>` it also writes the pair as a strip over a dark backdrop, the
 * evidence under `renders/rc-bitmap-fonts/`.
 */
class RcBitmapFontsRenderTest {
  private val typefaces = runBlocking {
    RcManifestTypefaceLoader { url -> File(url).readBytes() }.load(FONTS)
  }
  private val rasterizer = RcComposeTextRasterizer(typefaces)

  @Test
  fun textDrawnFromABitmapFontLooksAsItDidAsCoreText() {
    val out = System.getProperty("rc.bitmapFonts.out")?.let(::File)?.apply { mkdirs() }
    val original = RcDocumentCodec.decode(File("$CORPUS/$SAMPLE.rc").readBytes())
    val converted =
      original.coreTextAsBitmapFonts(
        RcTargetProfiles.WEAR_WIDGETS,
        FAMILIES,
        rasterizer,
        RcDisplay(DENSITY),
      )
    assertTrue(converted.texts.isNotEmpty(), "no text was converted; skipped ${converted.skipped}")
    // What a player without the faces shows instead: the same texts in the default family.
    val fallback =
      original.copy(
        operations =
          original.operations.map {
            if (it is RcTextData && it.text.startsWith("google:")) it.copy(text = "sans-serif")
            else it
          },
      )
    val before = render(original)
    val after = render(converted.document)
    val fellBack = render(fallback)
    val diff = (-SHIFT..SHIFT).minOf { differing(before, after, it) }
    val fallbackDiff = (-SHIFT..SHIFT).minOf { differing(before, fellBack, it) }
    out?.let { File(it, "$SAMPLE.png").writeBytes(strip(original, before, after, fellBack)) }
    // Glyphs on whole pixels move each edge up to half a pixel from where `CoreText` lays it out,
    // so the faces match by shape, not pixel for pixel: far closer than the default family.
    assertTrue(
      diff < MAX_DIFF && diff < fallbackDiff / 3,
      "bitmap-font texts differ from CoreText in $diff of pixels, the default family in $fallbackDiff",
    )
  }

  /**
   * The share of pixels whose channels differ by more than [TOLERANCE], with [b] read [dx] pixels
   * along: glyphs a bitmap font places on whole pixels, where `CoreText` lays the line out from its
   * own origin, which this player puts a few pixels apart.
   */
  private fun differing(a: Bitmap, b: Bitmap, dx: Int): Double {
    var count = 0
    for (y in 0 until a.height) {
      for (x in maxOf(0, -dx) until minOf(a.width, a.width - dx)) {
        val p = a.getColor(x, y)
        val q = b.getColor(x + dx, y)
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
    const val SHIFT = 3
    const val MAX_DIFF = 0.06
    const val CORPUS = "../../scripts/rc-catalog-corpus/corpus"
    const val FONTS = "../../rc-player/wasm/dist-assets/fonts"
    const val SAMPLE = "typeface-specimen__ideal__default__compact"
    /**
     * The specimen's faces but its script, Lobster Two: a joined face loses its joins and swashes
     * drawn a glyph at a time, as [coreTextAsBitmapFonts] says.
     */
    val FAMILIES = setOf("Orbitron", "Space Grotesk", "JetBrains Mono")
  }
}
