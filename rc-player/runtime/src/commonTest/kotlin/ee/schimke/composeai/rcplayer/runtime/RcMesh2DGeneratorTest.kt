package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcAddMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcMatrixFromMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `AddMesh2D` expansion and `MatrixFromMesh2D` sampling, against AndroidX's `Mesh2DGenerator`. */
class RcMesh2DGeneratorTest {
  // AndroidX's `Utils.asNan`: the id in the low bits of a negative quiet NaN.
  private fun nan(id: Int) = RcFloatWord(0xff800000.toInt() or id)

  private val var1 = nan(RcFloatExpressionEvaluator.VAR1)
  private val var2 = nan(RcFloatExpressionEvaluator.VAR1 + 1)
  private val mul = nan(RcFloatExpressionEvaluator.OFFSET + 3)
  private val sub = nan(RcFloatExpressionEvaluator.OFFSET + 2)

  private fun word(value: Float) = RcFloatWord.literal(value)

  private fun mesh(vararg groups: Pair<Int, List<RcFloatWord>>, layout: Int = 0, u: Int = 3) =
    RcAddMesh2D(
      meshId = 60,
      type = RcAddMesh2D.TYPE_EXPRESSION,
      layout = layout,
      uCount = u,
      vCount = 2,
      flags = 0,
      aux = 0,
      expressions =
        List(RcAddMesh2D.EXPRESSION_GROUPS) { index ->
          groups.firstOrNull { it.first == index }?.second.orEmpty()
        },
    )

  private fun state(vararg operations: RcAddMesh2D) =
    RcPlayerState(RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), operations.toList()))

  @Test
  fun aGridExpandsOverUAndVWithAndroidxTopology() {
    val operation =
      mesh(
        RcAddMesh2D.EXP_X to listOf(var1, word(100f), mul),
        RcAddMesh2D.EXP_Y to listOf(var2, word(50f), mul),
        RcAddMesh2D.EXP_COLOR_R to listOf(word(1f), var1, sub),
        RcAddMesh2D.EXP_COLOR_B to listOf(var1),
      )
    val geometry = state(operation).applyAddMesh2D(operation)

    assertContentEquals(
      floatArrayOf(0f, 0f, 50f, 0f, 100f, 0f, 0f, 50f, 50f, 50f, 100f, 50f),
      geometry.positions,
    )
    assertContentEquals(
      floatArrayOf(0f, 0f, 0.5f, 0f, 1f, 0f, 0f, 1f, 0.5f, 1f, 1f, 1f),
      geometry.uv,
    )
    // Unset channels default to 1 once any colour channel is set: green is full everywhere.
    assertEquals(0xffffff00.toInt(), geometry.colors[0])
    assertEquals(0xff80ff80.toInt(), geometry.colors[1])
    assertEquals(0xff00ffff.toInt(), geometry.colors[2])
    assertContentEquals(intArrayOf(0, 3, 1, 1, 3, 4, 1, 4, 2, 2, 4, 5), geometry.indices)
  }

  @Test
  fun absentChannelsTakeTheLayoutDefaultsAndNoColours() {
    val operation = mesh(layout = RcAddMesh2D.LAYOUT_FAN, u = 4)
    val geometry = state(operation).applyAddMesh2D(operation)
    // A fan: the centre then a unit-circle rim, one triangle per rim vertex.
    assertEquals(5, geometry.vertexCount)
    assertEquals(0f, geometry.positions[0])
    assertEquals(1f, geometry.positions[2])
    assertTrue(abs(geometry.positions[5] - 1f) < 1e-6f)
    assertEquals(0, geometry.colors.size)
    assertContentEquals(intArrayOf(0, 1, 2, 0, 2, 3, 0, 3, 4, 0, 4, 1), geometry.indices)
  }

  @Test
  fun aChannelReadsTheClock() {
    val operation =
      mesh(
        RcAddMesh2D.EXP_COLOR_G to listOf(nan(RcSystemVariables.ANIMATION_TIME), word(0.5f), mul),
      )
    val state = state(operation)
    state.beginFrame(timeSeconds = 1f)
    assertEquals(0x80, (state.applyAddMesh2D(operation).colors[0] ushr 8) and 0xff)
    state.beginFrame(timeSeconds = 2f)
    assertEquals(0xff, (state.applyAddMesh2D(operation).colors[0] ushr 8) and 0xff)
  }

  @Test
  fun aMeshNotYetPaintedIsExpandedFromTheDocument() {
    val operation = mesh(RcAddMesh2D.EXP_X to listOf(var1, word(10f), mul))
    val state = state(operation)
    assertEquals(10f, state.mesh2D(60)?.positions?.get(4))
    assertNull(state.mesh2D(61))
  }

  @Test
  fun aDocumentScopeMeshIsReExpandedWhenItReadsVariables() {
    val clock =
      mesh(
        RcAddMesh2D.EXP_COLOR_G to listOf(nan(RcSystemVariables.ANIMATION_TIME), word(0.5f), mul),
      )
    val state = state(clock)
    state.beginFrame(timeSeconds = 1f)
    assertEquals(0x80, (state.mesh2D(60)!!.colors[0] ushr 8) and 0xff)
    state.beginFrame(timeSeconds = 2f)
    assertEquals(0xff, (state.mesh2D(60)!!.colors[0] ushr 8) and 0xff)
    // A mesh of literals and u/v alone is expanded once.
    val constant = state(mesh(RcAddMesh2D.EXP_X to listOf(var1, word(10f), mul)))
    assertTrue(constant.mesh2D(60) === constant.mesh2D(60))
  }

  @Test
  fun theFrameOfAScaledGridIsItsScale() {
    val operation =
      mesh(
        RcAddMesh2D.EXP_X to listOf(var1, word(100f), mul),
        RcAddMesh2D.EXP_Y to listOf(var2, word(50f), mul),
      )
    val geometry = state(operation).applyAddMesh2D(operation)
    val full = geometry.frameMatrix(0.5f, 0.5f, RcMatrixFromMesh2D.FLAG_FULL)!!
    assertNear(floatArrayOf(100f, 0f, 0f, 50f, 50f, 25f), full)
    val origin = geometry.frameMatrix(1f, 0f, RcMatrixFromMesh2D.FLAG_ORIGIN)!!
    assertNear(floatArrayOf(1f, 0f, 0f, 1f, 100f, 0f), origin)
    val rotation = geometry.frameMatrix(0.5f, 0.5f, RcMatrixFromMesh2D.FLAG_ROTATION)!!
    assertNear(floatArrayOf(1f, 0f, 0f, 1f, 50f, 25f), rotation)
  }

  private fun assertNear(expected: FloatArray, actual: FloatArray) {
    assertEquals(expected.size, actual.size)
    assertTrue(
      expected.indices.all { abs(expected[it] - actual[it]) < 1e-4f },
      "expected ${expected.toList()}, was ${actual.toList()}",
    )
  }
}
