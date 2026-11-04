package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.CENTROID
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.EDGE1
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.EDGE2
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.FLOATS_PER_VERTEX
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.POSITION
import ee.schimke.composeai.rcplayer.threed.Rc3DShaderMeshData.Companion.UV
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcMatrix4
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
}
