package ee.schimke.composeai.rcplayer.threed.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class RcRasterizerTest {
  private val w = 64
  private val h = 64

  private fun line(x0: Float, y0: Float, x1: Float, y1: Float): IntArray {
    val z = FloatArray(w * h) { Float.POSITIVE_INFINITY }
    val img = IntArray(w * h)
    RcRasterizer.drawLineDepthTested(z, img, -1, w, h, x0, y0, 0.5f, x1, y1, 0.5f, 0f)
    return img
  }

  @Test
  fun aHugeOffscreenEdgeIsClippedRatherThanStepped() {
    val start = TimeSource.Monotonic.markNow()
    // A horizontal edge two billion pixels long through the middle row.
    val img = line(-1e9f, 32.5f, 1e9f, 32.5f)
    assertTrue(start.elapsedNow().inWholeMilliseconds < 2_000, "clipped, not stepped")
    assertEquals(w, (0 until w).count { img[32 * w + it] != 0 }, "the visible part is drawn")
    assertEquals(w, img.count { it != 0 })
  }

  @Test
  fun anEdgeEntirelyOffScreenDrawsNothing() {
    assertEquals(0, line(-1e9f, -500f, 1e9f, -400f).count { it != 0 })
  }

  @Test
  fun anOnScreenEdgeIsUnchanged() {
    val img = line(2f, 2f, 60f, 40f)
    assertTrue(img[2 * w + 2] != 0 && img[40 * w + 60] != 0)
  }
}
