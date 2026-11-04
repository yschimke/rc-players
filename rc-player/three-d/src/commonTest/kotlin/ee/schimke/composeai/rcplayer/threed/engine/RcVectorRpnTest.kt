package ee.schimke.composeai.rcplayer.threed.engine

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.threed.engine.RcVectorRpn.Companion.OP_ADD
import ee.schimke.composeai.rcplayer.threed.engine.RcVectorRpn.Companion.OP_COS
import ee.schimke.composeai.rcplayer.threed.engine.RcVectorRpn.Companion.OP_VBUILD3
import ee.schimke.composeai.rcplayer.threed.engine.RcVectorRpn.Companion.OP_VLEN
import ee.schimke.composeai.rcplayer.threed.engine.RcVectorRpn.Companion.word
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcVectorRpnTest {
  private fun eval(vararg program: Any): Pair<FloatArray, Int> {
    val words = program.map {
      if (it is Int) word(it) else RcFloatWord.literal((it as Number).toFloat())
    }
    val out = FloatArray(RcVectorRpn.DIM)
    val dim = RcVectorRpn().apply(words, out)
    return out to dim
  }

  private fun near(expected: Float, actual: Float) =
    assertTrue(abs(expected - actual) < 1e-5f, "expected $expected, got $actual")

  @Test
  fun aScalarAddedToAVectorLeavesItsPaddingAlone() {
    // The reference adds the scalar to the hidden fourth lane too, giving sqrt(30).
    val (out, dim) = eval(1f, 2f, 3f, OP_VBUILD3, 1f, OP_ADD, OP_VLEN)
    assertEquals(1, dim)
    near(sqrt(29f), out[0])
  }

  @Test
  fun aUnaryOperatorLeavesPaddingAtZero() {
    // cos(0) is 1 in every lane: unmasked, the padding lane makes the length sqrt(4), not sqrt(3).
    val (out, _) = eval(0f, 0f, 0f, OP_VBUILD3, OP_COS, OP_VLEN)
    near(sqrt(3f) * cos(0f), out[0])
  }

  @Test
  fun aScalarStaysBroadcast() {
    val (out, dim) = eval(2f, 3f, OP_ADD)
    assertEquals(1, dim)
    assertEquals(listOf(5f, 5f, 5f, 5f), out.toList())
  }
}
