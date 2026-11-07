package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import kotlin.test.Test
import kotlin.test.assertEquals

/** Malformed expressions degrade to 0 instead of throwing out of the draw pass. */
class RcFloatExpressionEvaluatorRobustnessTest {
  private val evaluator = RcFloatExpressionEvaluator()

  private fun op(id: Int) =
    RcFloatWord(0xff800000.toInt() or (RcFloatExpressionEvaluator.OFFSET + id))

  private fun word(value: Float) = RcFloatWord.literal(value)

  @Test
  fun meshVariablesWithoutBindingsReadZero() {
    // VAR1 * 2 + 1, evaluated as a plain float with no u/v supplied: what a hoisted mesh
    // sub-expression looks like to the document's float runtime.
    val expression = listOf(op(70), word(2f), op(3), word(1f), op(1))
    assertEquals(1f, evaluator.evaluate(expression))
    assertEquals(0f, evaluator.evaluate(listOf(op(72)), FloatArray(2)))
    assertEquals(7f, evaluator.evaluate(listOf(op(71)), floatArrayOf(3f, 7f)))
  }

  @Test
  fun anOperatorWithTooFewOperandsReadsZero() {
    assertEquals(0f, evaluator.evaluate(listOf(op(1))))
    assertEquals(0f, evaluator.evaluate(listOf(word(1f), op(25))))
  }

  @Test
  fun anEmptyExpressionReadsZero() {
    assertEquals(0f, evaluator.evaluate(emptyList()))
  }

  @Test
  fun aLongExpressionDoesNotOverflowTheStack() {
    val expression = List(200) { word(1f) } + List(199) { op(1) }
    assertEquals(200f, evaluator.evaluate(expression))
  }
}
