package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A spring-animated `FloatExpression` behaves as AndroidX's does: settled at its first target,
 * springing toward later ones, and convergent at any frame interval.
 *
 * The spring parameters are the ones `remote-m3`'s selection controls record (stiffness 1400,
 * critically damped, 0.001 stop threshold): their `progress` is `remoteSpring` over the `checked`
 * flag, and a split switch row drew unchecked and greyed because this runtime started the spring at
 * 0 and then integrated it to ±infinity.
 */
class RcFloatExpressionRuntimeTest {
  @Test
  fun aTweenRetargetsFromItsCurrentValue() {
    var target = 0f
    val tween =
      RcFloatExpressionRuntime(
        RcFloatExpression(
          59,
          listOf(RcFloatWord(SOURCE_REFERENCE)),
          listOf(RcFloatWord.literal(1f), RcFloatWord(4)),
        ),
        { null },
      )
    fun at(time: Float) = tween.evaluate(time) { target }
    assertEquals(0f, at(0f))
    target = 100f
    at(1f)
    val middle = at(1.5f)
    assertEquals(50f, middle, 0.01f)
    target = 0f
    assertEquals(middle, at(1.5f), 0.01f)
    assertEquals(25f, at(2f), 0.01f)
    assertEquals(0f, at(2.5f))
  }

  private var source = 1f

  private val runtime =
    RcFloatExpressionRuntime(
      RcFloatExpression(
        id = 59,
        expression = listOf(RcFloatWord(SOURCE_REFERENCE)),
        animation =
          listOf(
            RcFloatWord.literal(0f),
            RcFloatWord.literal(1400f),
            RcFloatWord.literal(74.83315f),
            RcFloatWord.literal(0.001f),
            RcFloatWord(0),
          ),
      ),
      arrays = { null },
    )

  private fun at(seconds: Float): Float =
    runtime.evaluate(seconds) { word -> if (word.bits == SOURCE_REFERENCE) source else word.value }

  @Test
  fun aSpringStartsSettledAtItsFirstTarget() {
    assertEquals(1f, at(0f))
    assertFalse(runtime.isAnimating(0f), "a spring at its first target has nothing to animate")
    assertEquals(1f, at(5f))
  }

  @Test
  fun aSpringConvergesAtCoarseFrameIntervals() {
    source = 0f
    at(0f)
    source = 1f
    // 100ms frames — a test clock's step, or a stalled device. Each step is ~3.7 radians of this
    // spring's natural frequency, past the midpoint integrator's stability limit unless it
    // over-samples.
    var time = 0f
    repeat(20) {
      time += 0.1f
      val value = at(time)
      assertTrue(value in -0.01f..1.01f, "the spring left [0, 1] at t=$time: $value")
    }
    assertEquals(1f, at(time))
    assertFalse(runtime.isAnimating(time))
  }

  @Test
  fun aRetargetAfterIdleSpringsFromNowRatherThanJumping() {
    at(0f)
    // Nothing evaluated the spring for ten seconds: it was settled, so no frames were requested.
    source = 0f
    val first = at(10f)
    assertTrue(abs(first - 1f) < 0.01f, "the idle gap was integrated as one step: $first")
    assertTrue(runtime.isAnimating(10f))
    val next = at(10.016f)
    assertTrue(next < first && next > 0f, "the spring did not start moving: $next")
  }

  private companion object {
    /** A NaN-boxed reference to id 58, the integer `checked` flag in the captured document. */
    const val SOURCE_REFERENCE: Int = 0xff80003a.toInt()
  }
}
