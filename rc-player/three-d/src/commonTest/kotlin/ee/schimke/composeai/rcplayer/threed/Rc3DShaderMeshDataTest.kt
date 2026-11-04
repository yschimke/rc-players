package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.CENTROID
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.EDGE1
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.EDGE2
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.FLOATS_PER_VERTEX
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.POSITION
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.UV
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcMatrix4
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import ee.schimke.composeai.rcplayer.threed.engine.RcSoftware3D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Rc3DShaderMeshDataTest {
  private val triangle =
    Rc3DMeshData(
      verts = floatArrayOf(0f, 0f, 0f, 3f, 0f, 0f, 0f, 3f, 0f),
      normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
      indices = intArrayOf(0, 1, 2, 0, 1, 9),
      uv = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
    )

  @Test
  fun eachVertexCarriesItsFacesEdgesAndCentroid() {
    val data = Rc3DShaderMeshData(triangle)
    // The second triangle names a vertex that does not exist: dropped, as the engine drops it.
    assertEquals(3, data.vertexCount)
    assertEquals(3 * FLOATS_PER_VERTEX, data.vertices.size)
    val v = data.vertices
    for (k in 0 until 3) {
      val o = k * FLOATS_PER_VERTEX
      assertEquals(listOf(3f, 0f, 0f), v.slice(o + EDGE1 until o + EDGE1 + 3))
      assertEquals(listOf(0f, 3f, 0f), v.slice(o + EDGE2 until o + EDGE2 + 3))
      assertEquals(listOf(1f, 1f, 0f), v.slice(o + CENTROID until o + CENTROID + 3))
    }
    assertEquals(
      listOf(3f, 0f, 0f),
      v.slice(FLOATS_PER_VERTEX + POSITION until FLOATS_PER_VERTEX + 3),
    )
    assertEquals(
      listOf(0f, 1f),
      v.slice(2 * FLOATS_PER_VERTEX + UV until 2 * FLOATS_PER_VERTEX + UV + 2),
    )
  }

  @Test
  fun aMeshBehindTheNearPlaneIsLeftToTheCpuPath() {
    val data = Rc3DShaderMeshData(triangle)
    val proj = FloatArray(16)
    val view = FloatArray(16)
    val mvp = FloatArray(16)
    RcMatrix4.perspective(proj, 0.9f, 1f, 0.1f, 100f)
    RcMatrix4.lookAt(view, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
    RcMatrix4.multiply(mvp, proj, view)
    assertTrue(data.inFrontOfNearPlane(mvp))
    // The camera inside the triangle's plane, looking along it: part of it is behind.
    RcMatrix4.lookAt(view, 1f, 1f, 0f, 1f, 1f, -5f, 0f, 1f, 0f)
    RcMatrix4.multiply(mvp, proj, view)
    assertFalse(data.inFrontOfNearPlane(mvp))
  }

  @Test
  fun theScreenRectHoldsEveryPixelTheRasterizerDraws() {
    val engine =
      RcSoftware3D().apply {
        setSize(200, 120)
        setCamera(
          Rc3D.PROJECTION_PERSPECTIVE,
          floatArrayOf(0.9f, 200f / 120f, 0.1f, 100f),
          floatArrayOf(1f, 0.4f, 4f, 0f, 0f, 0f, 0f, 1f, 0f),
        )
        setLights(listOf(Rc3D.LIGHT_DIRECTIONAL), listOf(-1), floatArrayOf(-0.3f, -0.5f, -0.8f, 1f))
        matrixOp(Rc3D.M3_TRANSLATE, floatArrayOf(0.7f, 0.2f, 0f))
        matrixOp(Rc3D.M3_ROTATE_AXIS, floatArrayOf(0.6f, 0f, 1f, 0f))
      }
    val cube =
      RcPrimitive3D.build(RcPrimitive3D.CUBE, 0f, 0, listOf(floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f)))
    engine.defineMesh(1, cube)
    engine.drawMesh(1, Rc3D.MODE_SOFTWARE_SMOOTH)
    engine.prepareShading()
    val rect = IntArray(4)
    assertTrue(Rc3DShaderMeshData(cube).screenRect(engine.modelViewProjection, 200, 120, rect))
    // The pixels the rasterizer wrote, not its dirty rectangle, which keeps slack of its own.
    var left = 200
    var top = 120
    var right = 0
    var bottom = 0
    for (y in 0 until 120) for (x in 0 until 200) {
      if (engine.colorBuffer[y * 200 + x] ushr 24 == 0) continue
      left = minOf(left, x)
      top = minOf(top, y)
      right = maxOf(right, x + 1)
      bottom = maxOf(bottom, y + 1)
    }
    assertTrue(left < right, "the cube is on screen")
    assertTrue(rect[0] <= left && rect[2] >= right, "x: ${rect.toList()} drawn $left until $right")
    assertTrue(rect[1] <= top && rect[3] >= bottom, "y: ${rect.toList()} drawn $top until $bottom")
    // Not the whole viewport, or the rectangle would save nothing.
    assertTrue((rect[2] - rect[0]) * (rect[3] - rect[1]) < 200 * 120 / 2, "${rect.toList()}")
  }
}
