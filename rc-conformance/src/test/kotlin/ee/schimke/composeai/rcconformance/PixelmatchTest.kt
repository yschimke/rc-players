package ee.schimke.composeai.rcconformance

import ee.schimke.composeai.rcconformance.runner.Pixelmatch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two self-checks `PLAYER_IMPLEMENTATION_GUIDE.md` §10 specifies, plus the case they exist to
 * catch.
 *
 * A permissive `antialiased()` is completely silent in normal use: every raster check still passes,
 * the score still looks plausible, and the whole channel proves nothing. The guide's second
 * self-check is the one that finds it — a one-pixel shift must score in the *hundreds*, not single
 * digits — so it is pinned here rather than left as advice in a comment.
 */
class PixelmatchTest {
  // 400x400, the size the corpus's own golds are drawn at. The scale matters for the shift test:
  // a one-pixel shift moves roughly one pixel per pixel of *perimeter*, so the guide's "hundreds"
  // bar is a statement about a 400-wide shape and would be an unfair bar on a small one.
  private val width = 400
  private val height = 400

  /** A shape with real edges: a filled disc on white, which is what produces antialiasing. */
  private fun disc(offsetX: Int = 0): ByteArray {
    val out = ByteArray(width * height * 4)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val dx = x - offsetX - width / 2
        val dy = y - height / 2
        val inside = dx * dx + dy * dy <= (width / 3) * (width / 3)
        val offset = (y * width + x) * 4
        val level = if (inside) 0 else 255
        out[offset] = level.toByte()
        out[offset + 1] = level.toByte()
        out[offset + 2] = level.toByte()
        out[offset + 3] = 255.toByte()
      }
    }
    return out
  }

  @Test
  fun anImageComparedAgainstItselfScoresZero() {
    val image = disc()
    assertEquals(0, Pixelmatch.countDiff(image, image.copyOf(), width, height))
  }

  @Test
  fun aOnePixelShiftScoresInTheHundreds() {
    val differing = Pixelmatch.countDiff(disc(offsetX = 1), disc(), width, height)
    // The guide's exact bar: "a number in the hundreds, not single digits — if it does not,
    // `antialiased()` is excusing real differences and your whole raster channel is permissive."
    assertTrue(
      differing >= 100,
      "a one-pixel shift scored only $differing; the metric is permissive",
    )
  }

  @Test
  fun aTransparentPixelAndAWhiteOneAreTheSamePicture() {
    // Both images are composited onto white before comparison, so a document that leaves its
    // background unpainted does not score its own background as an error.
    val transparent = ByteArray(width * height * 4)
    val white = ByteArray(width * height * 4) { 255.toByte() }
    assertEquals(0, Pixelmatch.countDiff(transparent, white, width, height))
  }

  @Test
  fun noiseIsNotExcusedAsAntialiasing() {
    // The failure mode the disc cannot catch: a detector that returns true too often would forgive
    // an image that shares no structure with its reference at all.
    val random = Random(seed = 1)
    val noise =
      ByteArray(width * height * 4) {
        if (it % 4 == 3) 255.toByte() else random.nextInt(256).toByte()
      }
    val differing = Pixelmatch.countDiff(noise, disc(), width, height)
    assertTrue(
      differing > width * height / 2,
      "random noise scored only $differing against a disc; the metric forgives almost anything",
    )
  }
}
