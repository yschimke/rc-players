package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontGlyph
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

class RcBitmapFontRenderTest {
  @Test
  fun drawsIndependentGlyphBitmapsAtRecordedMetrics() {
    val document =
      RcDocument(
        RcHeader(
          RcVersion(1, 0, 0),
          legacyWidth = WIDTH,
          legacyHeight = HEIGHT,
          modern = false,
        ),
        listOf(
          RcBitmapData(
            imageId = 50,
            width = 1,
            height = 1,
            type = RcBitmapData.TYPE_RAW8888,
            encoding = RcBitmapData.ENCODING_INLINE,
            data = byteArrayOf(0xff.toByte(), 0, 0, 0xff.toByte()),
          ),
          RcTextData(41, "AA"),
          RcBitmapFontData(
            40,
            listOf(RcBitmapFontGlyph("A", 50, 1, 2, 1, 0, 4, 5)),
          ),
          RcDrawBitmapFontTextRun(
            41,
            40,
            0,
            -1,
            RcFloatWord.literal(2f),
            RcFloatWord.literal(3f),
            RcFloatWord.literal(2f),
          ),
        ),
      )
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document)
      }

    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      assertTrue(scene.render(0L).readPixels(bitmap))
      assertRed(bitmap.getColor(3, 5))
      assertRed(bitmap.getColor(11, 5))
      assertTrue(bitmap.getColor(8, 5).ushr(24) == 0, "glyph spacing should remain transparent")
    } finally {
      scene.close()
    }
  }

  @Test
  fun glyphsDrawWithThePaintsColourFilterAndColourAlpha() {
    // AndroidX draws each glyph through `drawBitmap` with the whole paint. A blue SrcIn tint turns
    // the red glyph blue, and the COLOR's half alpha fades it; both were dropped.
    val document =
      RcDocument(
        RcHeader(
          RcVersion(1, 0, 0),
          legacyWidth = WIDTH,
          legacyHeight = HEIGHT,
          modern = false,
        ),
        listOf(
          RcBitmapData(
            imageId = 50,
            width = 1,
            height = 1,
            type = RcBitmapData.TYPE_RAW8888,
            encoding = RcBitmapData.ENCODING_INLINE,
            data = byteArrayOf(0xff.toByte(), 0, 0, 0xff.toByte()),
          ),
          RcTextData(41, "A"),
          RcBitmapFontData(40, listOf(RcBitmapFontGlyph("A", 50, 1, 2, 1, 0, 4, 5))),
          RcPaintData(
            listOf(
              PAINT_COLOR,
              0x80000000.toInt(),
              PAINT_COLOR_FILTER or (SRC_IN shl 16),
              0xff0000ff.toInt(),
            )
          ),
          RcDrawBitmapFontTextRun(
            41,
            40,
            0,
            -1,
            RcFloatWord.literal(2f),
            RcFloatWord.literal(3f),
            RcFloatWord.literal(0f),
          ),
        ),
      )
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document)
      }

    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      assertTrue(scene.render(0L).readPixels(bitmap))
      val color = bitmap.getColor(3, 5)
      val hex = color.toUInt().toString(16)
      assertTrue((color and 0xff) > ((color shr 16) and 0xff), "expected a blue glyph, got $hex")
      assertTrue((color ushr 24) in 0x70..0x90, "expected a half-alpha glyph, got $hex")
    } finally {
      scene.close()
    }
  }

  private fun assertRed(color: Int) {
    assertTrue((color ushr 24) == 0xff, "expected opaque pixel, got ${color.toUInt().toString(16)}")
    assertTrue(
      ((color shr 16) and 0xff) > 240,
      "expected red pixel, got ${color.toUInt().toString(16)}",
    )
  }

  private companion object {
    const val WIDTH = 16
    const val HEIGHT = 12
    const val PAINT_COLOR = 4
    const val PAINT_COLOR_FILTER = 13
    const val SRC_IN = 5
  }
}
