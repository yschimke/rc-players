package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Rc3DConvexityTest {
  private fun build(type: Int, params: List<Float>, segments: Float = 16f) =
    RcPrimitive3D.build(type, segments, 0, listOf(params.toFloatArray()))

  @Test
  fun aCubeAndASphereAreConvex() {
    assertTrue(Rc3DConvexity.isConvex(build(RcPrimitive3D.CUBE, listOf(1f, 1f, 1f, 0f, 0f, 0f))))
    assertTrue(Rc3DConvexity.isConvex(build(RcPrimitive3D.SPHERE, listOf(1f, 0f, 0f, 0f))))
  }

  @Test
  fun aTorusIsNot() {
    assertFalse(Rc3DConvexity.isConvex(build(RcPrimitive3D.TORUS, listOf(0.6f, 0.2f, 0f, 0f, 0f))))
  }
}
