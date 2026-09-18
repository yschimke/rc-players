package ee.schimke.composeai.rcconformance

import ee.schimke.composeai.rcconformance.runner.DiffHeatmap
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The diff heatmap is evidence, and evidence that contradicts its verdict is worse than none.
 *
 * Two properties carry the whole file: equal pixels are transparent (so a passing raster's panel is
 * empty rather than covered in ink), and the ramp's ends are pinned, because they are what a reader
 * calibrates against — dark blue for a barely-perceptible shift, bright yellow for a channel that
 * moved its full range.
 */
class DiffHeatmapTest {
  @OptIn(ExperimentalEncodingApi::class)
  private fun decode(dataUri: String): BufferedImage {
    val bytes = Base64.decode(dataUri.substringAfter("base64,"))
    return ImageIO.read(ByteArrayInputStream(bytes)) ?: error("the heatmap did not decode as a PNG")
  }

  @Test
  fun equalPixelsAreTransparent() {
    val heat = decode(DiffHeatmap.encode(2, 1, byteArrayOf(0, 0)))

    assertEquals(0, heat.getRGB(0, 0), "a matched pixel must stay transparent")
    assertEquals(0, heat.getRGB(1, 0))
  }

  @Test
  fun theRampRunsFromDarkBlueToYellow() {
    val heat = decode(DiffHeatmap.encode(3, 1, byteArrayOf(0, 1, 0xFF.toByte())))

    assertEquals(0, heat.getRGB(0, 0))
    assertEquals(0x000179, heat.getRGB(1, 0) and 0xFFFFFF, "delta 1 — the ramp's quiet end")
    assertEquals(0xFFFF00, heat.getRGB(2, 0) and 0xFFFFFF, "delta 255 — the ramp's loud end")
  }

  @Test
  fun theRampIsMonotonicInBrightness() {
    // No dipping: a reader scanning "worse here than there" must be able to trust intensity.
    val magnitude = (1..255).map { it.toByte() }.toByteArray()
    val heat = decode(DiffHeatmap.encode(255, 1, magnitude))
    var previous = -1
    for (x in 0 until 255) {
      val brightness =
        (heat.getRGB(x, 0) and 0xFFFFFF).let { colour ->
          (((colour shr 16) and 0xFF) * 299 +
            ((colour shr 8) and 0xFF) * 587 +
            (colour and 0xFF) * 114) / 1000
        }
      assertTrue(
        brightness >= previous,
        "channel delta ${x + 1} darkened the ramp: $brightness < $previous",
      )
      previous = brightness
    }
  }

  @Test
  fun aWrongSizeIsRefusedRatherThanEncoded() {
    assertFailsWith<IllegalArgumentException> { DiffHeatmap.encode(2, 2, ByteArray(3)) }
  }
}
