package ee.schimke.composeai.rcplayer.threed

import ee.schimke.composeai.rcplayer.threed.engine.RcMatrix4
import ee.schimke.composeai.rcplayer.threed.engine.RcPrimitive3D
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Rc3DXrHelpersTest {
  private val cube =
    RcPrimitive3D.build(RcPrimitive3D.CUBE, 0f, 0, listOf(floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f)))

  private fun draw(projection: FloatArray): Rc3DMeshDraw {
    val view = FloatArray(16).also { RcMatrix4.lookAt(it, 0f, 0f, 4f, 0f, 0f, 0f, 0f, 1f, 0f) }
    val model = FloatArray(16).also(RcMatrix4::identity)
    return Rc3DMeshDraw(1, cube, model, view, projection, -1, 0, 1, 100f, 100f)
  }

  private fun apply(m: FloatArray, x: Float, y: Float, z: Float): FloatArray =
    FloatArray(4).also { RcMatrix4.transformPoint(m, x, y, z, it) }

  private fun assertNear(expected: Float, actual: Float, what: String) =
    assertTrue(abs(expected - actual) < 1e-4f, "$what: expected $expected, got $actual")

  @Test
  fun aPerspectiveSceneLinesUpWithTheFlatRenderOnThePanelPlane() {
    val projection = FloatArray(16).also { RcMatrix4.perspective(it, 0.9f, 1f, 0.1f, 100f) }
    val d = draw(projection)
    val height = 0.5f
    val panel = Rc3DPanelPlacement.panelTransform(d, height)
    // The focus point sits at the panel's centre, on its plane.
    val origin = apply(panel, 0f, 0f, 0f)
    assertNear(0f, origin[0], "x")
    assertNear(0f, origin[1], "y")
    assertNear(0f, origin[2], "z")
    // A point on the focus plane lands where the flat render draws it: NDC times half the panel.
    val clip = FloatArray(4)
    val pv = FloatArray(16).also { RcMatrix4.multiply(it, projection, d.view) }
    RcMatrix4.transformPoint(pv, 0.7f, -0.3f, 0f, clip)
    val onPanel = apply(panel, 0.7f, -0.3f, 0f)
    assertNear(clip[0] / clip[3] * height / 2, onPanel[0], "panel x")
    assertNear(clip[1] / clip[3] * height / 2, onPanel[1], "panel y")
    // Geometry nearer the camera comes out of the panel towards the viewer.
    assertTrue(apply(panel, 0f, 0f, 1f)[2] > 0f)
  }

  @Test
  fun anOrthographicSceneMapsItsViewHeightOntoThePanel() {
    val projection = FloatArray(16).also { RcMatrix4.ortho(it, -2f, 2f, -2f, 2f, 0.1f, 100f) }
    val panel = Rc3DPanelPlacement.panelTransform(draw(projection), 1f)
    val corner = apply(panel, 2f, 2f, 0f)
    assertNear(0.5f, corner[0], "x")
    assertNear(0.5f, corner[1], "y")
    assertNear(0f, corner[2], "z")
  }

  private fun le(bytes: ByteArray, at: Int): Int =
    (bytes[at].toInt() and 0xFF) or
      ((bytes[at + 1].toInt() and 0xFF) shl 8) or
      ((bytes[at + 2].toInt() and 0xFF) shl 16) or
      ((bytes[at + 3].toInt() and 0xFF) shl 24)

  @Test
  fun aMeshExportsAsAWellFormedGlb() {
    val glb = Rc3DGltf.toGlb(cube, 0xFFEE8844.toInt())
    assertEquals(0x46546C67, le(glb, 0), "magic")
    assertEquals(2, le(glb, 4), "version")
    assertEquals(glb.size, le(glb, 8), "length")
    val jsonLength = le(glb, 12)
    assertEquals(0x4E4F534A, le(glb, 16), "JSON chunk")
    assertEquals(0, jsonLength % 4)
    val json = glb.copyOfRange(20, 20 + jsonLength).decodeToString()
    assertTrue("\"POSITION\":0" in json && "\"NORMAL\":1" in json, json)
    assertTrue("\"count\":${cube.vertexCount}" in json)
    assertTrue("\"count\":${cube.indices.size}" in json)
    val binAt = 20 + jsonLength
    val binLength = le(glb, binAt)
    assertEquals(0x004E4942, le(glb, binAt + 4), "BIN chunk")
    assertEquals(glb.size, binAt + 8 + binLength)
    assertEquals(0, binLength % 4)
    // vertices x3 floats, normals x3, indices: all 4 bytes each.
    assertEquals((cube.vertexCount * 6 + cube.indices.size) * 4, binLength)
  }

  @Test
  fun aTexturedMeshCarriesItsImageAndFlipsV() {
    val textured =
      RcPrimitive3D.build(
        RcPrimitive3D.SPHERE,
        8f,
        1 shl RcPrimitive3D.FLAG_UV_SHIFT,
        listOf(floatArrayOf(1f, 0f, 0f, 0f)),
      )
    val glb = Rc3DGltf.toGlb(textured, -1, texturePng = ByteArray(7) { it.toByte() })
    val json = glb.copyOfRange(20, 20 + le(glb, 12)).decodeToString()
    assertTrue("\"TEXCOORD_0\"" in json && "\"mimeType\":\"image/png\"" in json, json)
    assertTrue("\"baseColorTexture\"" in json)
  }
}
