package ee.schimke.composeai.rcplayer.xrsample

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.threed.Rc3DExtension
import ee.schimke.composeai.rcplayer.threed.RcMeshPrimitive3D
import ee.schimke.composeai.rcplayer.threed.engine.Rc3DMeshData
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcXrMeshPackingTest {
  @Test
  fun aPrimitivePacksPositionAndNormalPerVertexWithBounds() {
    val cube =
      RcPrimitive3D.build(RcPrimitive3D.CUBE, 0f, 0, listOf(floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f)))
    val packed = RcXrMeshPacking.pack(cube)
    assertEquals(6, packed.stride)
    assertEquals(cube.vertexCount, packed.vertexCount)
    assertEquals(cube.indices.toList(), packed.indices.toList())
    // Positions and normals interleave, the layout the SceneCore host declares.
    for (i in 0 until cube.vertexCount) {
      for (k in 0 until 3) {
        assertEquals(cube.verts[i * 3 + k], packed.vertices[i * 6 + k])
        assertEquals(cube.normals[i * 3 + k], packed.vertices[i * 6 + 3 + k])
      }
    }
    // The cube's parameters are its full size, so a unit cube spans -0.5..0.5.
    assertEquals(listOf(-0.5f, -0.5f, -0.5f), packed.boundsMin.toList())
    assertEquals(listOf(0.5f, 0.5f, 0.5f), packed.boundsMax.toList())
  }

  @Test
  fun aMeshWithoutNormalsGetsUnitVertexNormalsFacingOut() {
    // One triangle in the z = 0 plane, wound counter-clockwise seen from +z.
    val triangle =
      Rc3DMeshData(
        verts = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
        normals = FloatArray(0),
        indices = intArrayOf(0, 1, 2),
      )
    val packed = RcXrMeshPacking.pack(triangle)
    for (i in 0 until 3) {
      val nx = packed.vertices[i * 6 + 3]
      val ny = packed.vertices[i * 6 + 4]
      val nz = packed.vertices[i * 6 + 5]
      assertTrue(abs(sqrt(nx * nx + ny * ny + nz * nz) - 1f) < 1e-5f)
      assertEquals(1f, nz, "facing +z, out of the counter-clockwise face")
    }
  }

  @Test
  fun texturedMeshesCarryUvWithVFlippedForSceneCore() {
    val uvFlag = 1 shl RcPrimitive3D.FLAG_UV_SHIFT
    val sphere =
      RcPrimitive3D.build(RcPrimitive3D.SPHERE, 8f, uvFlag, listOf(floatArrayOf(1f, 0f, 0f, 0f)))
    val packed = RcXrMeshPacking.pack(sphere)
    assertEquals(8, packed.stride)
    val uv = sphere.uv!!
    for (i in 0 until sphere.vertexCount) {
      assertEquals(uv[i * 2], packed.vertices[i * 8 + 6])
      assertEquals(1f - uv[i * 2 + 1], packed.vertices[i * 8 + 7])
    }
  }

  @Test
  fun linearColourMatchesTheSrgbCurve() {
    val rgba = RcXrMeshPacking.linearRgba(0x80FF8000.toInt())
    assertEquals(1f, rgba[0])
    assertTrue(abs(rgba[1] - 0.2158605f) < 1e-5f, "sRGB 0x80 is about 0.216 linear: ${rgba[1]}")
    assertEquals(0f, rgba[2])
    assertTrue(abs(rgba[3] - 128 / 255f) < 1e-6f)
  }

  @Test
  fun theSampleDocumentDecodesWithTheExtensionAndHasTwoMeshes() {
    val document = RcDocumentCodec.decode(RcXrSampleDocument.bytes(), Rc3DExtension.wireExtensions)
    val primitives = document.operations.filterIsInstance<RcMeshPrimitive3D>()
    assertEquals(
      listOf(RcPrimitive3D.CUBE, RcPrimitive3D.TORUS),
      primitives.map { it.type },
    )
  }
}
