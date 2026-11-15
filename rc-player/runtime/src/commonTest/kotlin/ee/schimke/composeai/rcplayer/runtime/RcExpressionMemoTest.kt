package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A float expression whose inputs did not move keeps its value without evaluating again, as
 * Compose's `derivedStateOf` does; one whose input moved is evaluated again. Either way its inputs
 * are read, so whatever evaluates it (a draw) still depends on them.
 */
class RcExpressionMemoTest {
  private val mul = RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 3)
  private val doubled =
    RcFloatExpression(OUT, listOf(reference(IN), RcFloatWord.literal(2f), mul), null)
  private val state =
    RcPlayerState(
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(RcFloatConstant(IN, RcFloatWord.literal(3f)), doubled),
      ),
    )

  @Test
  fun anInputThatMovedIsEvaluatedAgain() {
    state.applyFloatExpression(doubled)
    assertEquals(6f, state.resolve(reference(OUT)))
    state.setFloat(IN, 5f)
    state.applyFloatExpression(doubled)
    assertEquals(10f, state.resolve(reference(OUT)))
  }

  @Test
  fun anExpressionWhoseInputsStoodStillStillReadsThem() {
    state.applyFloatExpression(doubled)
    val reads = mutableListOf<Int>()
    state.valueReadObserver = reads::add
    state.applyFloatExpression(doubled)
    assertTrue(IN in reads, "a memoized evaluation did not read its input: $reads")
    assertEquals(6f, state.resolve(reference(OUT)))
  }

  @Test
  fun aWriteToTheOutputIsReplacedByTheNextEvaluationAsBefore() {
    state.applyFloatExpression(doubled)
    state.setFloat(OUT, 1f)
    state.applyFloatExpression(doubled)
    assertEquals(
      6f,
      state.resolve(reference(OUT)),
      "the memo kept a value the expression did not compute",
    )
  }

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private companion object {
    const val IN = 42
    const val OUT = 43
  }
}
