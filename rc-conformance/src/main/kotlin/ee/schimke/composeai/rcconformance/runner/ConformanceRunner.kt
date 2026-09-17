package ee.schimke.composeai.rcconformance.runner

import ee.schimke.composeai.rcconformance.corpus.Check
import ee.schimke.composeai.rcconformance.corpus.Gold
import kotlin.system.measureTimeMillis
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** One gold's verdict, in the vocabulary of `CONFORMANCE_FORMAT.md` §5. */
public data class GoldResult(
  public val gold: Gold,
  public val status: String,
  public val rawStatus: String,
  /** **Binding** assertions only. Advisory ones are counted separately — see [Check.advisory]. */
  public val checksTotal: Int,
  public val checksFailed: Int,
  public val advisoryTotal: Int,
  public val advisoryFailed: Int,
  public val durationMs: Long,
  public val diffs: List<Diff>,
  public val observed: Map<String, Map<String, JsonElement>>,
  public val attachments: Map<String, String>,
  public val error: String? = null,
)

/**
 * Drives one player across one gold, exactly as `PLAYER_IMPLEMENTATION_GUIDE.md` §2 specifies.
 *
 * The loop is small; what matters is the three ways it refuses to lose a check, each of which is a
 * mistake the reference implementation made first (guide §8):
 *
 * * **Checks are evaluated at their own step, not at the end of the run.** Most probes read mutable
 *   state, so a colour check bound to a light-theme step has to run before the dark-theme step
 *   overwrites the slot — otherwise every light-theme assertion quietly compares dark values, and
 *   nothing looks wrong because they are still real assertions.
 * * **A check bound to a step that never ran fails as `STEP_NOT_RUN`.** A silently dropped check is
 *   indistinguishable from a passing one.
 * * **An unimplemented probe fails as `PROBE_NOT_IMPLEMENTED`.** A runner that skips what it cannot
 *   do produces a beautiful, meaningless number.
 */
public class ConformanceRunner(private val engine: ConformanceEngine) {
  public fun run(gold: Gold): GoldResult {
    // Format version 2 is the contract this runner implements. Anything else is reported as a skip
    // rather than played on the assumption that the shape is close enough.
    if (gold.formatVersion != 2) {
      return skipped(gold, "unsupported format_version ${gold.formatVersion}")
    }

    val diffs = mutableListOf<Diff>()
    val observed = mutableMapOf<String, MutableMap<String, JsonElement>>()
    val attachments = mutableMapOf<String, String>()
    val checksByStep = gold.checks.groupBy(Check::at)
    val executed = mutableSetOf<String>()
    // Kept apart because they mean different things. A failing binding check is a conformance gap;
    // a failing advisory one is a number the corpus wants reported and has declined to fail a gold
    // on. Both are recorded in `diffs`, which is what upstream's own results file does.
    var failedChecks = 0
    var advisoryFailed = 0
    var error: String? = null

    val duration = measureTimeMillis {
      try {
        engine.withSession(gold) { session ->
          for (step in gold.timeline) {
            try {
              // A capture is a step id in its own right as far as the checks are concerned, so it
              // is marked executed and evaluated inline — see `ConformanceSession.execute`.
              session.execute(step) { captureId ->
                executed += captureId
                for (check in checksByStep[captureId].orEmpty()) {
                  if (evaluate(gold, session, check, diffs, observed, attachments)) {
                    if (check.advisory) advisoryFailed++ else failedChecks++
                  }
                }
              }
            } catch (unsupported: UnsupportedStepKind) {
              // Left out of `executed`, so this step's checks fail as STEP_NOT_RUN below. The kind
              // is recorded so the report can say which capability is missing rather than only that
              // something is.
              observed.getOrPut(step.id) { mutableMapOf() }["step:unsupported"] =
                JsonPrimitive(unsupported.kind)
              continue
            }
            executed += step.id

            for (check in checksByStep[step.id].orEmpty()) {
              if (evaluate(gold, session, check, diffs, observed, attachments)) {
                if (check.advisory) advisoryFailed++ else failedChecks++
              }
            }
          }
        }
      } catch (failure: Throwable) {
        // An engine that throws mid-timeline has errored on this gold, not failed it. The
        // distinction matters: FAIL is a conformance gap, ERROR is a crash, and averaging them
        // together hides which one a lane is actually suffering from.
        error = failure.toString()
      }
    }

    for ((stepId, checks) in checksByStep) {
      if (stepId in executed) continue
      checks.forEach { check ->
        if (check.advisory) advisoryFailed++ else failedChecks++
        diffs +=
          Diff(
            at = check.at,
            probe = check.key,
            target = check.target,
            property = Diff.STEP_NOT_RUN,
            expected = check.expect,
            actual = JsonNull,
            tolerance = null,
          )
      }
    }

    // `failedChecks`, not `diffs`: a gold whose only disagreements are advisory passes. That is the
    // corpus's own rule and upstream's own results follow it — 38 of its TypeScript golds pass with
    // advisory failures recorded against them.
    val rawStatus =
      when {
        error != null -> "ERROR"
        failedChecks == 0 -> "PASS"
        else -> "FAIL"
      }

    return GoldResult(
      gold = gold,
      // §2.9: a suspicious gold pins disputed reference behaviour. The real verdict is kept in
      // `rawStatus` and the gold is left out of the pass rate, rather than being quietly counted as
      // a failure this player is responsible for.
      status = if (gold.isSuspicious) "SUSPICIOUS" else rawStatus,
      rawStatus = rawStatus,
      checksTotal = gold.checks.count { !it.advisory },
      checksFailed = failedChecks,
      advisoryTotal = gold.checks.count(Check::advisory),
      advisoryFailed = advisoryFailed,
      durationMs = duration,
      diffs = diffs,
      observed = observed.mapValues { it.value.toMap() },
      attachments = attachments,
      error = error,
    )
  }

  /**
   * Evaluates one check, returning whether it failed.
   *
   * One check, one verdict: a `tree` check that disagrees about forty nodes is a single failed
   * assertion with forty diffs underneath it, not forty failures. `checks_failed` counts
   * assertions, which is what makes it comparable with the corpus's own declared check count.
   */
  private fun evaluate(
    gold: Gold,
    session: ConformanceSession,
    check: Check,
    diffs: MutableList<Diff>,
    observed: MutableMap<String, MutableMap<String, JsonElement>>,
    attachments: MutableMap<String, String>,
  ): Boolean {
    val observation =
      try {
        session.observe(check)
      } catch (failure: Throwable) {
        // A probe that throws is this check's failure, not the gold's: the remaining checks still
        // carry information, and losing them would understate what the player does support.
        diffs +=
          Diff(
            at = check.at,
            probe = check.key,
            target = check.target,
            property = "PROBE_ERROR",
            expected = check.expect,
            actual = JsonPrimitive(failure.toString()),
            tolerance = null,
          )
        return true
      }

    val failures = Comparators.compare(check, observation, check.resolveTolerance(gold))
    diffs += failures
    record(check, observation, observed, attachments)
    return failures.isNotEmpty()
  }

  /**
   * Populates `observed` (and, for rasters, `attachments`).
   *
   * Nominally optional, and populated anyway: 250 of the 252 golds assert values rather than
   * pixels, and `observed` is the only thing the HTML report has to show for them — the expected
   * side comes from the corpus, the actual side comes from here. A player that omits it renders
   * every non-drawing subsystem as a blank page (guide §7).
   */
  private fun record(
    check: Check,
    observation: Observation,
    observed: MutableMap<String, MutableMap<String, JsonElement>>,
    attachments: MutableMap<String, String>,
  ) {
    val step = observed.getOrPut(check.at) { mutableMapOf() }
    when (observation) {
      is Observation.Value ->
        // §7: scalar probes nest one level further, by target, so several slots read at the same
        // step do not overwrite each other.
        if (check.target != null && check.probe in SCALAR_PROBES) {
          val existing = step[check.probe] as? JsonObject
          step[check.probe] = buildJsonObject {
            existing?.forEach { (key, value) -> put(key, value) }
            put(check.target, observation.value)
          }
        } else {
          step[check.key] = observation.value
        }
      is Observation.Raster ->
        attachments["raster_actual@${check.at}"] =
          encodeRgbaAsDataUri(observation.width, observation.height, observation.rgba)
      Observation.NotImplemented -> step[check.key] = JsonPrimitive("PROBE_NOT_IMPLEMENTED")
    }
  }

  private val SCALAR_PROBES = setOf("float", "int", "color", "text", "matrix")

  private fun skipped(gold: Gold, reason: String) =
    GoldResult(
      gold = gold,
      status = "SKIP",
      rawStatus = "SKIP",
      checksTotal = gold.checks.size,
      checksFailed = 0,
      advisoryTotal = 0,
      advisoryFailed = 0,
      durationMs = 0,
      diffs = emptyList(),
      observed = emptyMap(),
      attachments = emptyMap(),
      error = reason,
    )
}
