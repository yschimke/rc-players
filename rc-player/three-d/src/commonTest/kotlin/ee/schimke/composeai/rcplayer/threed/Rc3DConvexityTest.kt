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

  @Test
  fun aTorusFarFromTheOriginIsStillNotConvex() {
    // Tolerances follow the mesh's size, not its position: moved 1000 units away, a torus's
    // faces must not all fall under the degenerate threshold.
    val far = build(RcPrimitive3D.TORUS, listOf(0.6f, 0.2f, 1000f, 0f, 0f))
    assertFalse(Rc3DConvexity.isConvex(far))
  }

  @Test
  fun twoParallelTrianglesFacingTheSameWayAreNot() {
    // Each triangle has the other wholly on one side, but on opposite sides of the two: both face
    // the camera and overlap on screen, so draw order matters.
    val twoLayers =
      ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData(
        verts =
          floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, -1f, 1f, 0f, -1f, 0f, 1f, -1f),
        normals = FloatArray(0),
        indices = intArrayOf(0, 1, 2, 3, 4, 5),
      )
    assertFalse(Rc3DConvexity.isConvex(twoLayers))
  }

  @Test
  fun aRepeatedTriangleIsNot() {
    val twice =
      ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData(
        verts = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
        normals = FloatArray(0),
        indices = intArrayOf(0, 1, 2, 1, 2, 0),
      )
    assertFalse(Rc3DConvexity.isConvex(twice))
  }
}
