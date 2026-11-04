package ee.schimke.composeai.rcplayer.xrsample

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RcXrViewportTest {
  private fun near(expected: Float, actual: Float) =
    assertTrue(abs(expected - actual) < 1e-5f, "expected $expected, got $actual")

  @Test
  fun aPaddedSquarePlayerAtTheTopOfATallPanel() {
    // A 1000 x 1400 px panel, 1 m x 1.4 m; the player is a 900 px square 50 px in from the top.
    val v = RcXrViewport.of(50f, 50f, 950f, 950f, 1000, 1400, 1f, 1.4f)!!
    near(0.9f, v.heightMeters)
    near(0f, v.offsetXMeters)
    // Its centre is 500 px down a 1400 px panel: 200 px, 0.2 m, above the panel's centre.
    near(0.2f, v.offsetYMeters)
  }

  @Test
  fun anUnmeasuredOrEmptyPlayerHasNoViewport() {
    assertNull(RcXrViewport.of(0f, 0f, 0f, 0f, 1000, 1000, 1f, 1f))
    assertNull(RcXrViewport.of(0f, 0f, 10f, 10f, 0, 0, 1f, 1f))
  }
}
