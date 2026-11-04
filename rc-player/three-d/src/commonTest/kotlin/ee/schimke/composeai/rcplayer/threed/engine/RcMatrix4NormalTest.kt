package ee.schimke.composeai.rcplayer.threed.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class RcMatrix4NormalTest {
  @Test
  fun aNonUniformScaleKeepsNormalsPerpendicular() {
    // Scale x by 2. A surface whose normal is (1, 1, 0) has tangent (1, -1, 0), which goes to
    // (2, -1, 0); the transformed normal must stay perpendicular to it, so along (0.5, 1, 0).
    val m = FloatArray(16).also(RcMatrix4::identity)
    RcMatrix4.scale(m, 2f, 1f, 1f)
    val nm = FloatArray(16)
    RcMatrix4.normalMatrix(nm, m)
    val n = FloatArray(3)
    RcMatrix4.transformDirection(nm, 1f, 1f, 0f, n)
    val t = FloatArray(3)
    RcMatrix4.transformDirection(m, 1f, -1f, 0f, t)
    val dot = n[0] * t[0] + n[1] * t[1] + n[2] * t[2]
    assertTrue(abs(dot) < 1e-6f, "normal $n not perpendicular to tangent: dot $dot")
    assertTrue(abs(n[0] / n[1] - 0.5f) < 1e-6f, "direction ${n.toList()}")
  }

  @Test
  fun aMirrorKeepsTheNormalOnTheSameSide() {
    // A reflection's normal matrix is the reflection itself: the sign survives.
    val m = FloatArray(16).also(RcMatrix4::identity)
    RcMatrix4.scale(m, -1f, 1f, 1f)
    val nm = FloatArray(16)
    RcMatrix4.normalMatrix(nm, m)
    val n = FloatArray(3)
    RcMatrix4.transformDirection(nm, 1f, 0f, 0f, n)
    assertTrue(abs(n[0] + 1f) < 1e-6f, "${n.toList()}")
  }
}
