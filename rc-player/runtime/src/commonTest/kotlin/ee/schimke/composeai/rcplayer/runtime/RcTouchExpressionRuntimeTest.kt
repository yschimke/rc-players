package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHapticType
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RcTouchExpressionRuntimeTest {
  @Test
  fun evaluatesDeltaTouchCoordinatesAgainstValueAtDown() {
    val operation = touch(RcTouchExpression.STOP_INSTANTLY, expression = listOf(reference(13)))
    val runtime = RcTouchExpressionRuntime(operation)

    runtime.onDown(currentValue = 20f, x = 100f, y = 0f, ::resolve)

    assertEquals(5f, runtime.onDrag(85f, 0f, 0f, 0f, ::resolve))
  }

  @Test
  fun snapsAllAuthoritativeNotchKinds() {
    val even = RcTouchExpressionRuntime(touch(RcTouchExpression.STOP_NOTCHES_EVEN, listOf(4f)))
    assertEquals(75f, even.stopTarget(68f, 0f, 100f, ::resolve))

    val percent =
      RcTouchExpressionRuntime(
        touch(RcTouchExpression.STOP_NOTCHES_PERCENTS, listOf(.1f, .6f, .9f)),
      )
    assertEquals(60f, percent.stopTarget(68f, 0f, 100f, ::resolve), absoluteTolerance = .001f)

    val absolute =
      RcTouchExpressionRuntime(
        touch(RcTouchExpression.STOP_NOTCHES_ABSOLUTE, listOf(12f, 48f, 92f)),
      )
    assertEquals(48f, absolute.stopTarget(55f, 0f, 100f, ::resolve))
  }

  @Test
  fun aTouchEffectFiresAsTheValueCrossesANotchAsAndroidXDoes() {
    // Kobento's `convert` writes touchEffects = 3: AndroidX's KEYBOARD_TAP haptic.
    val even =
      RcTouchExpressionRuntime(touch(RcTouchExpression.STOP_NOTCHES_EVEN, listOf(4f), effects = 3))
    even.onDown(currentValue = 10f, x = 0f, y = 0f, ::resolve)
    // Within the first quarter: no notch crossed.
    assertNull(even.touchEffect(20f, 0f, 100f, ::resolve))
    // Across 25: one tap, of the type the document named.
    assertEquals(RcHapticType.KeyboardTap, even.touchEffect(30f, 0f, 100f, ::resolve))
    assertNull(even.touchEffect(40f, 0f, 100f, ::resolve))
  }

  @Test
  fun eachStopModeFiresWhereAndroidXFires() {
    fun first(mode: Int, spec: List<Float>, from: Float, to: Float): RcHapticType? {
      val runtime = RcTouchExpressionRuntime(touch(mode, spec, effects = 1))
      runtime.onDown(currentValue = from, x = 0f, y = 0f, ::resolve)
      return runtime.touchEffect(to, 0f, 100f, ::resolve)
    }
    val tap = RcHapticType.LongPress
    assertEquals(tap, first(RcTouchExpression.STOP_INSTANTLY, emptyList(), 10f, 11f))
    assertEquals(tap, first(RcTouchExpression.STOP_ENDS, emptyList(), 50f, 120f))
    assertNull(first(RcTouchExpression.STOP_ENDS, emptyList(), 50f, 60f))
    assertEquals(tap, first(RcTouchExpression.STOP_NOTCHES_PERCENTS, listOf(.5f), 40f, 60f))
    assertNull(first(RcTouchExpression.STOP_NOTCHES_PERCENTS, listOf(.5f), 60f, 70f))
    assertEquals(tap, first(RcTouchExpression.STOP_NOTCHES_ABSOLUTE, listOf(48f), 40f, 50f))
    assertNull(first(RcTouchExpression.STOP_GENTLY, emptyList(), 0f, 100f))
  }

  @Test
  fun noTouchEffectAndAVariableEffectAreHonoured() {
    val none = RcTouchExpressionRuntime(touch(RcTouchExpression.STOP_INSTANTLY))
    assertNull(none.touchEffect(5f, 0f, 100f, ::resolve))

    // Bit 15 names an integer variable holding the haptic type.
    val variable =
      RcTouchExpressionRuntime(
        touch(
          RcTouchExpression.STOP_INSTANTLY,
          effects = RcTouchExpressionRuntime.TOUCH_EFFECT_VARIABLE or 7,
        ),
      )
    assertEquals(
      RcHapticType.KeyboardRelease,
      variable.touchEffect(5f, 0f, 100f, ::resolve) { id -> if (id == 7) 7 else null },
    )
    assertNull(variable.touchEffect(6f, 0f, 100f, ::resolve) { null })
  }

  private fun touch(
    stopMode: Int,
    stopSpec: List<Float> = emptyList(),
    expression: List<RcFloatWord> = emptyList(),
    effects: Int = 0,
  ) =
    RcTouchExpression(
      id = 41,
      defaultValue = RcFloatWord.literal(0f),
      min = RcFloatWord.literal(0f),
      max = RcFloatWord.literal(100f),
      velocityId = RcFloatWord.literal(Float.NaN),
      touchEffects = effects,
      expression = expression,
      stopMode = stopMode,
      stopSpec = stopSpec.map(RcFloatWord::literal),
      easingSpec = emptyList(),
    )

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private fun resolve(word: RcFloatWord): Float = word.value
}
