package ee.schimke.composeai.rcplayer.protocol

/**
 * The ids AndroidX's `RemoteContext` reserves for values the **player** supplies, not the document.
 *
 * A document never allocates in this range: writers start their own ids well above it (42 in every
 * catalog this repo publishes), and a `NaN`-boxed word pointing here is a request for something the
 * host knows — the wall clock, the animation clock, the viewport. Because the reference is an
 * ordinary float word, a player that does not load these ids does not fail loudly: the reference
 * resolves to its own raw `NaN` bits and the arithmetic downstream quietly produces `NaN`, so the
 * shape built from it is never drawn. That is exactly how the `remote-m3` catalog's indeterminate
 * circular progress indicator came to render as an empty frame in the CMP/Wasm lane (#4264) while
 * the AndroidX and TypeScript players — both of which load these — animated it.
 *
 * Only the ids this player actually supplies are named here. The sensor, touch and viewport blocks
 * AndroidX also reserves are deliberately absent: naming an id we do not load would suggest a
 * support this player does not have.
 */
public object RcSystemVariables {
  /** Seconds within the hour, fractional — `minute * 60 + second + millis / 1000`. */
  public const val CONTINUOUS_SEC: Int = 1

  /** Seconds within the hour, whole — `minute * 60 + second`. */
  public const val TIME_IN_SEC: Int = 2

  /** Minutes within the day — `hour * 60 + minute`. */
  public const val TIME_IN_MIN: Int = 3

  /** Hour of the day, 0..23. */
  public const val TIME_IN_HR: Int = 4

  /** Calendar month, 1..12. */
  public const val CALENDAR_MONTH: Int = 9

  /** The local zone's offset from UTC, in seconds. */
  public const val OFFSET_TO_UTC: Int = 10

  /** ISO day of week, 1 (Monday)..7 (Sunday). */
  public const val WEEK_DAY: Int = 11

  public const val DAY_OF_MONTH: Int = 12

  /** Seconds since the document was loaded — the player's own animation clock. */
  public const val ANIMATION_TIME: Int = 30

  /** Seconds between this frame and the previous one. */
  public const val ANIMATION_DELTA_TIME: Int = 31

  /** Whole seconds since the Unix epoch, loaded as an integer. */
  public const val EPOCH_SECOND: Int = 32

  public const val DAY_OF_YEAR: Int = 34

  public const val YEAR: Int = 35

  /** Every id this player supplies, so a value store can tell one from a document's own. */
  public val ALL: Set<Int> =
    setOf(
      CONTINUOUS_SEC,
      TIME_IN_SEC,
      TIME_IN_MIN,
      TIME_IN_HR,
      CALENDAR_MONTH,
      OFFSET_TO_UTC,
      WEEK_DAY,
      DAY_OF_MONTH,
      ANIMATION_TIME,
      ANIMATION_DELTA_TIME,
      EPOCH_SECOND,
      DAY_OF_YEAR,
      YEAR,
    )

  /**
   * The subset whose value moves while a document is on screen, so a reference to one of them means
   * the player has to keep drawing frames rather than paint once and stop.
   *
   * The date fields are excluded: they change at most once a day, which a player that redraws on
   * its own schedule picks up without spinning a frame loop for it.
   */
  public val MOVING: Set<Int> =
    setOf(
      CONTINUOUS_SEC,
      TIME_IN_SEC,
      TIME_IN_MIN,
      TIME_IN_HR,
      ANIMATION_TIME,
      ANIMATION_DELTA_TIME,
      EPOCH_SECOND,
    )
}

/**
 * Whether this document reads a system variable whose value moves — i.e. whether painting it once
 * would freeze an animation.
 *
 * Float expressions and path expressions are the two places a document can name one of these ids
 * and turn it into geometry, and they are what the `remote-m3` progress indicators use.
 *
 * The particle operations carry expressions of their own, and one of them is here for a sharper
 * reason than completeness: a particle system that reads the clock and is not given frames cannot
 * always reach the state that would ask for them. Which of them need it is decided by what the
 * runtime already requests for itself, and that rules out all but three fields:
 *
 * `RcParticleRuntime.forEach` ends with `if (hasActiveParticles) requestNextFrame()`, so a loop
 * with any unfrozen particle keeps itself alive and needs nothing from here. Claiming otherwise
 * would be worse than redundant: once every particle hits `maxLifetimeFrames` and freezes — or in a
 * system with no particles at all — `forEach` stops asking, and a flag set here would repaint that
 * document for the rest of its life.
 *
 * `compare` requests a frame only when a comparison actually MATCHED, deliberately, matching the
 * reference's `ParticlesCompare` guarding its own `needsRepaint()` on having run a child. That is
 * the one shape that can deadlock: a standalone comparison whose condition reads `CONTINUOUS_SEC`
 * starts false, asks for nothing, and the clock never advances to make it true, so a document the
 * AndroidX player animates holds its first pose here forever. The index bounds are resolved per
 * paint by `resolvedIndex` and gate the same way — `maximumIndex = ANIMATION_TIME` selects an empty
 * range, matches nothing, and never reaches the frame that would widen it.
 *
 * The comparison's RESULT equations are not scheduled, for the same reason the loop is not: they
 * are evaluated only on a match, and a match already asks for the next frame. A clock read by
 * results the condition never selects animates nothing.
 *
 * An operation that references a moving id *directly* in one of its own float words (rather than
 * through an expression list) is still not detected; no writer emits that shape today, and a scan
 * of every word of every operation would need the model to expose them generically.
 */
public fun RcDocument.referencesMovingSystemVariable(): Boolean {
  // The last definition wins, as it does in the runtime's own `define`.
  val definitions = operations.filterIsInstance<RcParticleDefine>().associateBy { it.id }
  val particleCounts = definitions.mapValues { it.value.particleCount }
  return operations.any { operation ->
    when (operation) {
      is RcFloatExpression ->
        operation.expression.movesWithSystemTime() ||
          operation.animation?.movesWithSystemTime() == true
      is RcPathExpression ->
        operation.expressionX.movesWithSystemTime() || operation.expressionY.movesWithSystemTime()
      // A range too small for the comparison's own shape is the third case that cannot deadlock.
      // `compare` reaches its condition only inside a loop over `system.particles`: one particle is
      // enough in single mode, but pair mode nests `firstIndex in secondIndex + 1 until end` and so
      // needs two. Below that the condition is never evaluated, `changed` stays false, no frame is
      // requested — and unlike an empty index range, no later clock value can conjure a particle
      // that would change it. Scheduling anyway repaints at the display rate for the life of the
      // document.
      is RcParticleCompare ->
        operation.canEverEvaluate(particleCounts) &&
          // The condition is evaluated THROUGH the system: `evaluate` looks each word up in
          // `system.variableIds` first and only falls back to the global store, so a particle
          // variable whose id happens to equal a clock's shadows it and is not a clock read at all.
          // The bounds are not shadowed — `resolvedIndex` calls `resolve` directly — so they stay
          // global.
          (operation.condition.movesWithSystemTime(
            definitions[operation.id]?.variableIds.orEmpty()
          ) ||
            operation.minimumIndex.movesWithSystemTime() ||
            operation.maximumIndex.movesWithSystemTime())
      else -> false
    }
  }
}

/**
 * Whether this comparison's SELECTED RANGE can ever hold enough particles for it to evaluate
 * anything.
 *
 * `compare` reaches its condition only inside a loop over `start until end`, and how much of that
 * range it needs depends on the comparison's own shape: single mode evaluates from the first
 * particle, while pair mode nests `firstIndex in secondIndex + 1 until end` and evaluates nothing
 * until the range holds two. Below that the condition never runs, `changed` stays false, and no
 * frame is requested — with nothing a later frame could change, which is what separates this from
 * the deadlock the scan exists for.
 *
 * The bounds are read the way `resolvedIndex` reads them: a negative minimum means 0, a negative
 * maximum means the whole system, and anything else is clamped into the system. A bound that is a
 * REFERENCE is taken at its widest — 0 for the minimum, the system size for the maximum — because
 * its value can move, and assuming the widest range can only keep frames coming.
 *
 * A system this document never defines is left scheduled: `requireSystem` throws on the first
 * paint, so the document is refused before the frame loop matters, and guessing here would only
 * replace one failure with another.
 */
private fun RcParticleCompare.canEverEvaluate(particleCounts: Map<Int, Int>): Boolean {
  val size = particleCounts[id] ?: return true
  val start = minimumIndex.staticIndex(negativeDefault = 0, size = size) ?: 0
  val end = maximumIndex.staticIndex(negativeDefault = size, size = size) ?: size
  return end - start >= if (secondEquations.isEmpty()) 1 else 2
}

/** The index this word resolves to before any frame runs, or null if it can move. */
private fun RcFloatWord.staticIndex(negativeDefault: Int, size: Int): Int? {
  if (referencedId != null || value.isNaN()) return null
  return if (value < 0f) negativeDefault else value.toInt().coerceIn(0, size)
}

private fun RcFloatWord.movesWithSystemTime(): Boolean = referencedId in RcSystemVariables.MOVING

private fun List<List<RcFloatWord>>.anyMovesWithSystemTime(): Boolean = any {
  it.movesWithSystemTime()
}

private fun List<RcFloatWord>.movesWithSystemTime(shadowed: List<Int> = emptyList()): Boolean =
  any {
    it.referencedId in RcSystemVariables.MOVING && it.referencedId !in shadowed
  }
