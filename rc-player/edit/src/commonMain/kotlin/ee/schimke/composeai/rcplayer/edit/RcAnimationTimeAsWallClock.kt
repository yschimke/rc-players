package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables

/**
 * This document with the player's animation clock ([RcSystemVariables.ANIMATION_TIME]) replaced by
 * the wall clock ([RcSystemVariables.CONTINUOUS_SEC], the second of the hour), so that it animates
 * on Android 16's launcher.
 *
 * That player loads `ANIMATION_TIME` on every draw but schedules the next draw only for a document
 * that reads `CONTINUOUS_SEC` (every frame), `TIME_IN_SEC` or `TIME_IN_MIN`: an expression over the
 * animation clock is evaluated once and the widget never moves again. Reading the wall clock
 * instead keeps it drawing.
 *
 * With [loadedAt] — the second of the hour, `minute * 60 + second + fraction`, at which the
 * document is served — each read becomes the seconds since then, `(CONTINUOUS_SEC - loadedAt) mod
 * 3600`, defined once under a fresh id: a one-shot animation still starts from its first frame,
 * provided the document is shown within the hour. Without it each read becomes `CONTINUOUS_SEC`
 * itself: repeating animations look the same, but a one-shot animation plays at whatever point of
 * the hour the document happens to be shown, usually already finished.
 *
 * Either way the clock wraps every hour, so a repeating animation whose period does not divide 3600
 * jumps once an hour.
 *
 * Only float-expression operands are rewritten. A field holding the animation clock directly rather
 * than through an expression is left as it is; creation lowers `RemoteFloat` arithmetic into
 * expressions, so that is rare.
 */
public fun RcDocument.animationTimeAsWallClock(loadedAt: Float? = null): RcDocument {
  if (operations.none { it is RcFloatExpression && it.readsAnimationTime() }) return this
  if (loadedAt != null) {
    require(loadedAt.isFinite() && loadedAt >= 0f && loadedAt < SECONDS_PER_HOUR) {
      "loadedAt must be a second of the hour: $loadedAt"
    }
  }
  val clockId: Int
  val definition: RcFloatExpression?
  if (loadedAt == null) {
    clockId = RcSystemVariables.CONTINUOUS_SEC
    definition = null
  } else {
    val used = operations.flatMapTo(mutableSetOf()) { it.mentionedValues() ?: emptySet() }
    clockId = RcFreshIds(used, componentIds(operations)).next()
    val hour = RcFloatWord.literal(SECONDS_PER_HOUR)
    definition =
      RcFloatExpression(
        clockId,
        listOf(
          reference(RcSystemVariables.CONTINUOUS_SEC),
          RcFloatWord.literal(loadedAt),
          operator(SUB),
          hour,
          operator(ADD),
          hour,
          operator(MOD),
        ),
        null,
      )
  }
  val rewritten = operations.map { operation ->
    if (operation !is RcFloatExpression || !operation.readsAnimationTime()) return@map operation
    operation.copy(
      expression =
        operation.expression.map { word ->
          if (word.referencedId == RcSystemVariables.ANIMATION_TIME) word.withId(clockId) else word
        }
    )
  }
  return copy(operations = listOfNotNull(definition) + rewritten)
}

/**
 * The [RcSystemVariables.ANIMATION_TIME] reads [animationTimeAsWallClock] would rewrite: whether
 * this document stops animating on Android 16's launcher.
 */
public fun RcDocument.readsAnimationTime(): Boolean = operations.any {
  it is RcFloatExpression && it.readsAnimationTime()
}

private fun RcFloatExpression.readsAnimationTime(): Boolean = expression.any {
  it.referencedId == RcSystemVariables.ANIMATION_TIME
}

/** The same NaN box, naming [id] instead: the reference keeps its own encoding. */
private fun RcFloatWord.withId(id: Int): RcFloatWord = RcFloatWord((bits and ID_BITS.inv()) or id)

private fun reference(id: Int): RcFloatWord = RcFloatWord(REFERENCE or id)

private fun operator(code: Int): RcFloatWord = RcFloatWord(OPERATOR or (OPERATOR_OFFSET + code))

private const val SECONDS_PER_HOUR = 3600f
private const val ID_BITS = 0x003fffff
private const val REFERENCE = 0x7fc00000
private const val OPERATOR = 0xff800000.toInt()
private const val OPERATOR_OFFSET = 0x310000
private const val ADD = 1
private const val SUB = 2
private const val MOD = 5
