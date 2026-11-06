package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.coreTextAsBitmapFonts
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnPath
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
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

  @Test
  fun textOnAPathDrawnFromABitmapFontLooksAsItDidAsText() {
    val out = System.getProperty("rc.bitmapFonts.out")?.let(::File)?.apply { mkdirs() }
    fun bezel(family: String) =
      RcDocument(
        RcHeader(RcVersion(1, 1, 0)),
        listOf(
          RcTextData(50, family),
          RcTextData(42, "ORBITRON 12:30"),
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
          ),
          // In a canvas, as a watch face's bezel lettering would be.
          RcRootLayout(-1),
          RcLayoutContent(-2),
          RcCanvasLayout(-3, -1),
          RcWidthModifier(RcDimensionType.FILL, f(0f)),
          RcHeightModifier(RcDimensionType.FILL, f(0f)),
          RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
          RcPaintData(listOf(16, 50, 1, f(22f).bits, 4, 0xffffffff.toInt())),
          RcDrawTextOnPath(42, 70, f(30f), f(0f)),
        ) + List(4) { RcNoArg(RcOpcodes.CONTAINER_END) },
      )
    val original = bezel("google:Orbitron")
    val converted =
      original.coreTextAsBitmapFonts(RcTargetProfiles.ANDROIDX, setOf("Orbitron"), rasterizer)
    assertTrue(converted.texts.singleOrNull()?.onPath == true, "skipped ${converted.skippedOnPath}")
    val before = render(original)
    val after = render(converted.document)
    val fellBack = render(bezel("sans-serif"))
    out?.let { File(it, "bezel.png").writeBytes(strip(original, before, after, fellBack)) }
    // Glyphs turned along a curve put every edge pixel somewhere else at a fraction of a pixel's
    // offset, so compare where the ink falls: where the run starts and ends, and its middle. This
    // player rounds each glyph's advance on a path up to a pixel where AndroidX doesn't, which the
    // conversion follows, so a run of 14 may end a few pixels apart.
    val ink = Ink(before)
    val drawn = Ink(after)
    val fallback = Ink(fellBack)
    assertTrue(
      ink.near(drawn, RUN_TOLERANCE) && !ink.near(fallback, RUN_TOLERANCE),
      "ink $ink, from a bitmap font $drawn, in the default family $fallback",
    )
  }

  private fun f(value: Float) = RcFloatWord.literal(value)

  /** Where a frame's bright ink is: its horizontal extent and its centre. */
  private data class Ink(val left: Int, val right: Int, val x: Float, val y: Float) {
    fun near(other: Ink, tolerance: Float): Boolean =
      abs(left - other.left) <= tolerance &&
        abs(right - other.right) <= tolerance &&
        abs(x - other.x) <= tolerance / 2 &&
        abs(y - other.y) <= tolerance / 2

    companion object {
      operator fun invoke(bitmap: Bitmap): Ink {
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var sumX = 0L
        var sumY = 0L
        var count = 0
        for (y in 0 until bitmap.height) {
          for (x in 0 until bitmap.width) {
            val c = bitmap.getColor(x, y)
            if ((c ushr 24 and 0xff) > 150 && (c and 0xff) > 150) {
              left = minOf(left, x)
              right = maxOf(right, x)
              sumX += x
              sumY += y
              count++
            }
          }
        }
        return Ink(left, right, sumX.toFloat() / count, sumY.toFloat() / count)
      }
    }
  }

  private fun command(id: Int) = RcFloatWord(0xff800000.toInt() or id)

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
    /** Pixels a run's ends may lie apart, and half that for its middle. */
    const val RUN_TOLERANCE = 8f
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
