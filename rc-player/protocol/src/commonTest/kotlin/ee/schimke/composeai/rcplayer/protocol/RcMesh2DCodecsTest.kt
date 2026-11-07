package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcMesh2DCodecsTest {
  private fun roundTrip(vararg operations: RcOperation): List<RcOperation> {
    val document = RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), operations.toList())
    return RcDocumentCodec.decode(RcDocumentCodec.encode(document)).operations
  }

  private fun word(value: Float) = RcFloatWord.literal(value)

  private fun id(id: Int) = RcFloatWord(0x7fc00000 or id)

  @Test
  fun aParametricMeshRoundTripsItsNineGroups() {
    val mesh =
      RcAddMesh2D(
        meshId = 50,
        type = RcAddMesh2D.TYPE_EXPRESSION,
        layout = RcAddMesh2D.LAYOUT_GRID,
        uCount = 3,
        vCount = 2,
        flags = 0,
        aux = 0,
        expressions =
          List(RcAddMesh2D.EXPRESSION_GROUPS) {
            if (it == RcAddMesh2D.EXP_COLOR_R) listOf(id(30), word(2f)) else emptyList()
          },
      )
    assertEquals(listOf(mesh), roundTrip(mesh))
  }

  @Test
  fun anAntialiasedMeshCarriesItsSkirtWidth() {
    val mesh =
      RcAddMesh2D(
        meshId = 51,
        type = RcAddMesh2D.TYPE_EXPRESSION,
        layout = RcAddMesh2D.LAYOUT_POLAR,
        uCount = 8,
        vCount = 2,
        flags = RcAddMesh2D.FLAG_ANTIALIAS,
        aux = 0,
        expressions = List(RcAddMesh2D.EXPRESSION_GROUPS) { listOf(word(1f)) },
        antialiasWidth = word(1.5f),
      )
    assertEquals(listOf(mesh), roundTrip(mesh))
  }

  @Test
  fun literalMeshesRoundTripAndHalfFloatsWiden() {
    val values =
      RcAddMesh2D(
        meshId = 52,
        type = RcAddMesh2D.TYPE_VALUES,
        layout = RcAddMesh2D.LAYOUT_GRID,
        uCount = 2,
        vCount = 2,
        flags = 0,
        aux = 0,
        indices = listOf(0, 1, 2, 2, 1, 3),
        vertices = listOf(0f, 0f, 10f, 0f, 0f, 10f, 10f, 10f).map(::word),
        uv = listOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f),
        colors = listOf(0xffff0000.toInt(), -1, -1, 0xff0000ff.toInt()),
      )
    assertEquals(listOf(values), roundTrip(values))
    val halves = values.copy(meshId = 53, type = RcAddMesh2D.TYPE_F16_VALUES, colors = emptyList())
    assertEquals(listOf(halves), roundTrip(halves))
    // 1/3 is not a half: it arrives as the nearest one.
    val inexact = halves.copy(vertices = List(8) { word(1f / 3f) })
    val widened = roundTrip(inexact).single() as RcAddMesh2D
    assertTrue(widened.vertices.all { kotlin.math.abs(it.value - 1f / 3f) < 1e-3f })
  }

  @Test
  fun aSplineStripRoundTripsItsWidths() {
    val strip =
      RcAddMesh2D(
        meshId = 54,
        type = RcAddMesh2D.TYPE_PATH_SPLINE_STRIP,
        layout = RcAddMesh2D.LAYOUT_PATH_STRIP,
        uCount = 9,
        vCount = 2,
        flags = 0,
        aux = 77,
        widths = listOf(word(1f), id(40)),
        widthPositions = listOf(word(0f), word(1f)),
      )
    assertEquals(listOf(strip), roundTrip(strip))
  }

  @Test
  fun drawAndMatrixRoundTrip() {
    val draw = RcDrawMesh2D(50, RcDrawMesh2D.BLEND_MODULATE, 7)
    val matrix = RcMatrixFromMesh2D(50, word(0.5f), id(41), RcMatrixFromMesh2D.FLAG_ROTATION)
    assertEquals(listOf(draw, matrix), roundTrip(draw, matrix))
  }

  @Test
  fun anOverlongExpressionGroupIsRejected() {
    val mesh =
      RcAddMesh2D(
        meshId = 55,
        type = RcAddMesh2D.TYPE_EXPRESSION,
        layout = RcAddMesh2D.LAYOUT_GRID,
        uCount = 2,
        vCount = 2,
        flags = 0,
        aux = 0,
        expressions = listOf(List(33) { word(1f) }),
      )
    assertFailsWith<IllegalArgumentException> {
      RcDocumentCodec.encode(RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), listOf(mesh)))
    }
  }

  @Test
  fun meshesAreExperimentalOnlyInTheAndroidxProfiles() {
    for (opcode in
      listOf(RcOpcodes.ADD_MESH_2D, RcOpcodes.DRAW_MESH_2D, RcOpcodes.MATRIX_FROM_MESH_2D)) {
      assertFalse(RcOperationProfiles.ANDROIDX_JAVA_ALPHA18.supports(opcode))
      assertTrue(RcOperationProfiles.ANDROIDX_EMBEDDED_EXPERIMENTAL_ALPHA18.supports(opcode))
      assertTrue(RcOperationProfiles.CMP_DESKTOP_ALPHA18.supports(opcode))
      assertTrue(RcOperationProfiles.CMP_WASM_ALPHA18.supports(opcode))
    }
  }
}
