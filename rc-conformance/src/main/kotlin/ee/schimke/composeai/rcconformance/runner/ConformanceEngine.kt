package ee.schimke.composeai.rcconformance.runner

import ee.schimke.composeai.rcconformance.corpus.Check
import ee.schimke.composeai.rcconformance.corpus.Gold
import ee.schimke.composeai.rcconformance.corpus.Step
import kotlinx.serialization.json.JsonElement

/**
 * One player lane.
 *
 * This is the whole seam between the corpus and a player, and it is deliberately narrow: an engine
 * *drives* and *observes*, and never decides whether an observation passes. Comparison — tolerance
 * resolution, the tree diff, the antialiasing-aware raster metric — lives in [ConformanceRunner]
 * for every lane alike, because a score computed by two different comparators is not a comparison.
 * `PLAYER_IMPLEMENTATION_GUIDE.md` §10 makes the same point about the raster metric specifically.
 */
public interface ConformanceEngine {
  /** Short, stable name. It is both the `results/<player>/` directory and the reported label. */
  public val name: String

  public val version: String

  /**
   * Prepares a session for one gold.
   *
   * The text-metrics policy in `gold.harness.text_metrics` must be installed *before* the document
   * is constructed, and event recorders before the first paint — see the guide §2. That ordering is
   * the session's responsibility because only it knows how its player is built.
   */
  public fun open(gold: Gold): ConformanceSession
}

/** A player driven through one gold's timeline. */
public interface ConformanceSession : AutoCloseable {
  /**
   * Runs one timeline step.
   *
   * Throwing [UnsupportedStepKind] is the correct response to a kind this player cannot drive: the
   * runner then fails that step's checks as `STEP_NOT_RUN`, which is visible. Returning normally
   * without doing the work would turn them into false passes — the failure mode
   * `CONFORMANCE_FORMAT.md` §3 calls out by name.
   */
  public fun execute(step: Step)

  /** Reads one check's observation. Never compares; see [ConformanceEngine]. */
  public fun observe(check: Check): Observation
}

/** Raised by [ConformanceSession.execute] for a step kind this player does not implement. */
public class UnsupportedStepKind(public val kind: String) :
  UnsupportedOperationException("unsupported step kind: $kind")

/** What a probe saw. */
public sealed interface Observation {
  /**
   * A structural or scalar observation, in the corpus's own JSON vocabulary.
   *
   * Encoding an observation as JSON rather than as a player type is what keeps the comparator
   * shared: the runner diffs a tree the same way whichever engine produced it.
   */
  public data class Value(public val value: JsonElement) : Observation

  /** A rendered frame, straight (non-premultiplied) RGBA, row-major. */
  public data class Raster(
    public val width: Int,
    public val height: Int,
    public val rgba: ByteArray,
  ) : Observation {
    init {
      require(rgba.size == width * height * 4) {
        "raster is ${rgba.size} bytes, expected ${width * height * 4} for ${width}x$height"
      }
    }

    // Data classes over an array need these spelled out; identity equality on `rgba` would make two
    // equal frames compare unequal.
    override fun equals(other: Any?): Boolean =
      this === other ||
        (other is Raster &&
          width == other.width &&
          height == other.height &&
          rgba.contentEquals(other.rgba))

    override fun hashCode(): Int = (width * 31 + height) * 31 + rgba.contentHashCode()
  }

  /**
   * This player does not implement the probe.
   *
   * Always reported as a failing check (`PROBE_NOT_IMPLEMENTED`), never skipped. A runner that
   * skips what it cannot do produces a beautiful, meaningless number — the exact failure the corpus
   * exists to prevent (guide §8).
   */
  public data object NotImplemented : Observation
}
