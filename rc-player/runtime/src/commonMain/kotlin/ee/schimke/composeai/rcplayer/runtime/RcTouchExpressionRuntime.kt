package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHapticType
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import kotlin.math.abs
import kotlin.math.roundToInt

/** Stateful, platform-neutral evaluation of AndroidX touch expressions. */
public class RcTouchExpressionRuntime(
  private val operation: RcTouchExpression,
  arrays: (Int) -> FloatArray? = { null },
) {
  private val evaluator = RcFloatExpressionEvaluator(arrays)
  private var valueAtDown: Float = 0f
  private var expressionAtDown: Float = 0f
  /** The value [touchEffect] last saw: AndroidX's `mLastValue`, which also starts at zero. */
  private var lastValue: Float = 0f

  public fun onDown(currentValue: Float, x: Float, y: Float, resolve: (RcFloatWord) -> Float) {
    valueAtDown = currentValue
    // AndroidX re-reads the value every frame, so a crossing is measured from where the touch
    // began.
    lastValue = currentValue
    expressionAtDown = evaluate(x, y, 0f, 0f, resolve)
  }

  public fun onDrag(
    x: Float,
    y: Float,
    velocityX: Float,
    velocityY: Float,
    resolve: (RcFloatWord) -> Float,
  ): Float {
    val evaluated = evaluate(x, y, velocityX, velocityY, resolve)
    val value =
      if (operation.stopMode == RcTouchExpression.STOP_ABSOLUTE_POS) evaluated
      else valueAtDown + evaluated - expressionAtDown
    return constrain(value, resolve)
  }

  public fun stopTarget(
    currentValue: Float,
    minimum: Float,
    maximum: Float,
    resolve: (RcFloatWord) -> Float,
  ): Float {
    val specs = operation.stopSpec.map(resolve)
    val target =
      when (operation.stopMode) {
        RcTouchExpression.STOP_ENDS ->
          if (currentValue > (minimum + maximum) / 2f) maximum else minimum
        RcTouchExpression.STOP_NOTCHES_EVEN,
        RcTouchExpression.STOP_NOTCHES_SINGLE_EVEN -> {
          val count = specs.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
          val notchMaximum = specs.getOrNull(1) ?: maximum
          val step = (notchMaximum - minimum) / count
          if (step == 0f) minimum
          else {
            var snapped = minimum + ((currentValue - minimum) / step).roundToInt() * step
            if (operation.stopMode == RcTouchExpression.STOP_NOTCHES_SINGLE_EVEN) {
              snapped = snapped.coerceIn(valueAtDown - step, valueAtDown + step)
            }
            snapped
          }
        }
        RcTouchExpression.STOP_NOTCHES_PERCENTS ->
          specs.map { minimum + it * (maximum - minimum) }.minByOrNull { abs(it - currentValue) }
            ?: currentValue
        RcTouchExpression.STOP_NOTCHES_ABSOLUTE ->
          specs.minByOrNull { abs(it - currentValue) } ?: currentValue
        else -> currentValue
      }
    return target.coerceIn(minimum, maximum)
  }

  /**
   * The haptic the expression's `touchEffects` asks for as its value moves from the last value this
   * saw to [value], or null when it asks for none here: AndroidX's
   * `TouchExpression.crossNotchCheck`, which runs on every value update (a drag and the settle
   * after it). An instant stop fires on every update; ends fire as the value enters or leaves
   * `[minimum, maximum]`; notches and percent or absolute stops fire as the value crosses one.
   *
   * `touchEffects` carries an `RcHapticType` wire value in its low byte, or with bit 15 set, the id
   * of an integer variable holding one, which [integer] resolves. Zero is `RcHapticType.None`.
   */
  public fun touchEffect(
    value: Float,
    minimum: Float,
    maximum: Float,
    resolve: (RcFloatWord) -> Float,
    integer: (Int) -> Int? = { null },
  ): RcHapticType? {
    val previous = lastValue
    lastValue = value
    val effects = operation.touchEffects
    if (effects == 0 || !crossed(previous, value, minimum, maximum, resolve)) return null
    val type =
      if (effects and TOUCH_EFFECT_VARIABLE != 0) integer(effects and 0x7FFF) ?: return null
      else effects and 0xFF
    return RcHapticType(type).takeIf { it != RcHapticType.None }
  }

  private fun crossed(
    previous: Float,
    next: Float,
    outMinimum: Float,
    maximum: Float,
    resolve: (RcFloatWord) -> Float,
  ): Boolean {
    val minimum = if (wraps) 0f else outMinimum
    val specs = operation.stopSpec.map(resolve)
    return when (operation.stopMode) {
      RcTouchExpression.STOP_ENDS ->
        ((minimum - previous) * (maximum - previous) < 0f) !=
          ((minimum - next) * (maximum - next) < 0f)
      RcTouchExpression.STOP_INSTANTLY -> true
      RcTouchExpression.STOP_NOTCHES_EVEN,
      RcTouchExpression.STOP_NOTCHES_SINGLE_EVEN -> {
        val spacing = specs.firstOrNull()?.toInt() ?: 0
        val step = if (spacing == 0) 0f else (maximum - minimum) / spacing
        step != 0f &&
          step.isFinite() &&
          ((previous - minimum) / step).toInt() != ((next - minimum) / step).toInt()
      }
      RcTouchExpression.STOP_NOTCHES_PERCENTS ->
        specs.any {
          val stop = outMinimum + it * (maximum - outMinimum)
          (previous - stop) * (next - stop) < 0f
        }
      RcTouchExpression.STOP_NOTCHES_ABSOLUTE -> specs.any { (previous - it) * (next - it) < 0f }
      else -> false
    }
  }

  /** AndroidX's wrap mode: a minimum that is the NaN-encoded id 0. */
  private val wraps: Boolean
    get() = operation.min.isNaNEncoded && operation.min.referencedId == 0

  private fun evaluate(
    x: Float,
    y: Float,
    velocityX: Float,
    velocityY: Float,
    resolve: (RcFloatWord) -> Float,
  ): Float =
    evaluator.evaluate(operation.expression) { word ->
      when (word.referencedId) {
        ID_TOUCH_POS_X -> x
        ID_TOUCH_POS_Y -> y
        ID_TOUCH_VEL_X -> velocityX
        ID_TOUCH_VEL_Y -> velocityY
        else -> resolve(word)
      }
    }

  private fun constrain(value: Float, resolve: (RcFloatWord) -> Float): Float {
    val maximum = resolve(operation.max)
    val minimum = resolve(operation.min)
    if (wraps) {
      val range = maximum
      if (range == 0f) return 0f
      return ((value % range) + range) % range
    }
    return value.coerceIn(minimum, maximum)
  }

  public companion object {
    public const val ID_TOUCH_POS_X: Int = 13
    public const val ID_TOUCH_POS_Y: Int = 14
    public const val ID_TOUCH_VEL_X: Int = 15
    public const val ID_TOUCH_VEL_Y: Int = 16

    /** Set in `touchEffects` when its low 15 bits name an integer variable, not a haptic type. */
    public const val TOUCH_EFFECT_VARIABLE: Int = 1 shl 15
  }
}
