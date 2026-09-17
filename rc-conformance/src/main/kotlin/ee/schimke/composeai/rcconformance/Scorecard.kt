package ee.schimke.composeai.rcconformance

import ee.schimke.composeai.rcconformance.corpus.Results
import ee.schimke.composeai.rcconformance.runner.Diff
import ee.schimke.composeai.rcconformance.runner.GoldResult

/**
 * The scorecard: what one lane scored, and — the part that makes it actionable — *why* it did not
 * score higher.
 *
 * A bare pass rate is close to useless on a partial implementation, because it cannot distinguish
 * "the player draws this wrong" from "the runner cannot see this yet". So every table here is
 * broken down by the thing that caused the failure: by subsystem, by probe, and by the two
 * structural reasons the format reserves — `PROBE_NOT_IMPLEMENTED` and `STEP_NOT_RUN`, which are
 * runner gaps rather than player defects and are counted separately for exactly that reason.
 */
public object Scorecard {
  public fun render(playerName: String, results: List<GoldResult>): String {
    val scored = results.filter { it.status != "SUSPICIOUS" && it.status != "SKIP" }
    val core = scored.filter { it.gold.profile == "core" }
    val extended = scored.filter { it.gold.profile == "extended" }

    return buildString {
      appendLine("# Conformance scorecard — $playerName")
      appendLine()
      appendLine("| | golds | passed | pass rate |")
      appendLine("| --- | ---: | ---: | ---: |")
      appendLine(row("Core profile", core))
      appendLine(row("Extended profile", extended))
      appendLine(row("**All scored**", scored))
      appendLine()
      appendLine(
        "Excluded from the rate: " +
          "${results.count { it.status == "SUSPICIOUS" }} suspicious " +
          "(disputed reference behaviour), " +
          "${results.count { it.status == "SKIP" }} skipped. " +
          "${results.count { it.status == "ERROR" }} errored — a crash, not a conformance gap."
      )
      appendLine()
      appendLine(
        "**${results.sumOf { it.advisoryFailed }} of ${results.sumOf { it.advisoryTotal }} " +
          "advisory checks disagree**, and none of them fails a gold. The corpus marks them so " +
          "(§4): they are all `raster`, and an antialiasing-aware pixel walk across two text " +
          "stacks and two GPU backends disagrees for reasons that are not conformance gaps. " +
          "Binding checks: ${results.sumOf { it.checksFailed }} of " +
          "${results.sumOf { it.checksTotal }} failing."
      )
      appendLine()

      appendLine("## Why the failures fail")
      appendLine()
      appendLine("| cause | diffs | share |")
      appendLine("| --- | ---: | ---: |")
      val diffs = results.flatMap { it.diffs }
      val byCause = diffs.groupingBy {
        when (it.property) {
          Diff.PROBE_NOT_IMPLEMENTED -> "Probe not implemented (this runner cannot observe it)"
          Diff.STEP_NOT_RUN -> "Step not run (this runner cannot drive it)"
          "PROBE_ERROR" -> "Probe threw"
          else -> "Real disagreement with the reference"
        }
      }
      byCause
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .forEach { (cause, count) ->
          appendLine("| $cause | $count | ${percent(count, diffs.size)} |")
        }
      appendLine()
      appendLine(
        "The first two rows are **this runner's** gaps, not the player's. They are reported as " +
          "failures on purpose — the corpus exists to stop a player scoring well by declining to " +
          "look — but they are the work list, not the verdict."
      )
      appendLine()

      appendLine("## By subsystem")
      appendLine()
      appendLine("| subsystem | golds | passed | pass rate |")
      appendLine("| --- | ---: | ---: | ---: |")
      scored
        .groupBy { it.gold.category }
        .entries
        .sortedBy { it.key }
        .forEach { (category, group) -> appendLine(row(category, group)) }
      appendLine()

      appendLine("## By probe")
      appendLine()
      appendLine(
        "Counted as **diffs**, not checks: one `particles` check compares a whole emitter and can " +
          "produce dozens. `raster` is advisory throughout — reported, never binding."
      )
      appendLine()
      appendLine("| probe | diffs | unimplemented |")
      appendLine("| --- | ---: | ---: |")
      diffs
        .groupBy { it.probe }
        .entries
        .sortedByDescending { it.value.size }
        .forEach { (probe, group) ->
          val unimplemented = group.count { it.property == Diff.PROBE_NOT_IMPLEMENTED }
          appendLine("| `$probe` | ${group.size} | $unimplemented |")
        }
      appendLine()

      val unsupportedSteps =
        results
          .flatMap { result ->
            result.observed.values.mapNotNull { it["step:unsupported"] }.map { it.toString() }
          }
          .groupingBy { it.trim('"') }
          .eachCount()
      if (unsupportedSteps.isNotEmpty()) {
        appendLine("## Timeline step kinds this runner cannot drive")
        appendLine()
        appendLine("| kind | occurrences |")
        appendLine("| --- | ---: |")
        unsupportedSteps.entries
          .sortedByDescending { it.value }
          .forEach { (kind, count) -> appendLine("| `$kind` | $count |") }
        appendLine()
      }

      val errors = results.filter { it.status == "ERROR" }
      if (errors.isNotEmpty()) {
        appendLine("## Errors")
        appendLine()
        errors.take(ERROR_SAMPLE).forEach { appendLine("- `${it.gold.name}` — ${it.error}") }
        if (errors.size > ERROR_SAMPLE) {
          appendLine("- …and ${errors.size - ERROR_SAMPLE} more")
        }
        appendLine()
      }
    }
  }

  private const val ERROR_SAMPLE = 10

  private fun row(label: String, group: List<GoldResult>): String {
    val passed = group.count { it.status == "PASS" }
    return "| $label | ${group.size} | $passed | ${percent(passed, group.size)} |"
  }

  private fun percent(part: Int, whole: Int): String =
    if (whole == 0) "—" else "%.1f%%".format(100.0 * part / whole)

  /** One line, for a CI log or a commit status. */
  public fun oneLine(playerName: String, results: List<GoldResult>): String {
    val scored = results.filter { it.status != "SUSPICIOUS" && it.status != "SKIP" }
    return "$playerName: %.1f%% (%d/%d golds)"
      .format(100.0 * Results.passRate(results), scored.count { it.status == "PASS" }, scored.size)
  }
}
