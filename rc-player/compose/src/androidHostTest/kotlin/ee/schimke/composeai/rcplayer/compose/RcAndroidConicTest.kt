package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A conic on Android: Skia's exact primitive from API 34, where `android.graphics.Path` exposes it,
 * and the quad approximation before.
 *
 * A weight-√½ conic from (0, 20) through (0, 0) to (20, 0) is the exact quarter circle of radius 20
 * about (20, 20), so how far its points stray from that circle is how far the path is from exact.
 * `PathMeasure.length` is no use here: it measures to its own coarse tolerance either way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RcAndroidConicTest {
  @Test
  @Config(sdk = [34])
  fun api34DrawsTheExactConic() {
    val error = radialError(quarterCircle())
    assertTrue("the conic is exact, strayed $error px", error < 1e-4f)
  }

  @Test
  @Config(sdk = [33])
  fun olderReleasesStayWithinTheQuadTolerance() {
    val error = radialError(quarterCircle())
    assertTrue("the quads stay within the chop tolerance, strayed $error px", error < 0.25f)
  }

  private fun quarterCircle(): android.graphics.Path =
    Path()
      .apply {
        moveTo(0f, 20f)
        rcConicTo(0f, 20f, 0f, 0f, 20f, 0f, sqrt(0.5f))
      }
      .asAndroidPath()

  /** The furthest any point of [path] strays from the circle of radius 20 about (20, 20). */
  private fun radialError(path: android.graphics.Path): Float {
    val points = path.approximate(0.0001f)
    var worst = 0f
    for (i in points.indices step 3) {
      val dx = points[i + 1] - 20f
      val dy = points[i + 2] - 20f
      worst = maxOf(worst, abs(sqrt(dx * dx + dy * dy) - 20f))
    }
    return worst
  }
}
