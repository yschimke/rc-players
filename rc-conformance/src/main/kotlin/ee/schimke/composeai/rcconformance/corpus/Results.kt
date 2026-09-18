package ee.schimke.composeai.rcconformance.corpus

import ee.schimke.composeai.rcconformance.runner.Diff
import ee.schimke.composeai.rcconformance.runner.GoldResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The `conformance-results.json` a player publishes (`CONFORMANCE_FORMAT.md` §5).
 *
 * This file is the entire deliverable: the corpus's own report generators are player-agnostic and
 * render any player's results, so nothing downstream needs to know a thing about this repository.
 */
public object Results {
  public fun render(
    playerName: String,
    playerVersion: String,
    results: List<GoldResult>,
    corpusPath: String,
  ): JsonObject = buildJsonObject {
    putJsonObject("player") {
      put("name", playerName)
      put("version", playerVersion)
    }
    putJsonObject("corpus") {
      put("path", corpusPath)
      put("tests", results.size)
      put("gold_count", results.size)
    }
    putJsonObject("summary") {
      put("passed", results.count { it.status == "PASS" })
      put("failed", results.count { it.status == "FAIL" })
      put("errored", results.count { it.status == "ERROR" })
      put("skipped", results.count { it.status == "SKIP" })
      put("suspicious", results.count { it.status == "SUSPICIOUS" })
      // Binding and advisory, apart and both named, which is the shape upstream's own results file
      // uses. Folding them together is what made this repository's numbers incomparable with it.
      put("checks_total", results.sumOf { it.checksTotal })
      put("checks_failed", results.sumOf { it.checksFailed })
      put("advisory_total", results.sumOf { it.advisoryTotal })
      put("advisory_failed", results.sumOf { it.advisoryFailed })
    }
    putJsonArray("results") { results.forEach { add(renderOne(it)) } }
  }

  private fun renderOne(result: GoldResult): JsonObject = buildJsonObject {
    put("name", result.gold.name)
    put("category", result.gold.category)
    put("description", result.gold.description ?: "")
    put("profile", result.gold.profile)
    put("status", result.status)
    put("raw_status", result.rawStatus)
    put("suspicious", result.gold.isSuspicious)
    result.gold.suspiciousReason?.let { put("suspicious_reason", it) }
    result.error?.let { put("error", it) }
    // The corpus's own report generators read these names, and nothing else. Publishing the frames
    // under a name of this runner's invention is what made every image pane in the generated audit
    // render empty, and publishing the pixel metric only where it failed is what made every badge
    // read "?" and every passing comparison read "n/a". The first comparison rides at the top level
    // — that is the one `renderOneComparison` draws for the initial step — and the same numbers
    // repeat per step under `rasterComparisons`, which is where the resize/animation views read.
    result.rasterComparisons.firstOrNull()?.let { first ->
      first.goldImageBase64?.let { put("goldImageBase64", it) }
      put("renderedCanvasBase64", first.renderedCanvasBase64)
      put("hasRasterBaseline", first.goldImageBase64 != null)
      first.aaPixels?.let { put("aaPixels", it) }
      first.differingPixels?.let { put("differingPixels", it) }
      put("totalPixels", first.totalPixels)
      put("rasterTolerance", first.rasterTolerance)
      first.rmse?.let { put("rmse", it) }
      first.maxDelta?.let { put("maxDelta", it) }
      first.diffHeatmapBase64?.let { put("diffHeatmapBase64", it) }
    }
    if (result.rasterComparisons.isNotEmpty()) {
      put(
        "rasterComparisons",
        buildJsonArray {
          result.rasterComparisons.forEach { comparison ->
            add(
              buildJsonObject {
                put("at", comparison.at)
                comparison.goldImageBase64?.let { put("goldImageBase64", it) }
                put("renderedCanvasBase64", comparison.renderedCanvasBase64)
                comparison.diffHeatmapBase64?.let { put("diffHeatmapBase64", it) }
                put("totalPixels", comparison.totalPixels)
                put("rasterTolerance", comparison.rasterTolerance)
                comparison.aaPixels?.let { put("aaPixels", it) }
                comparison.differingPixels?.let { put("differingPixels", it) }
                comparison.rmse?.let { put("rmse", it) }
                comparison.maxDelta?.let { put("maxDelta", it) }
              }
            )
          }
        },
      )
    }
    result.componentCount?.let { put("componentCount", it) }
    put("checks_total", result.checksTotal)
    put("checks_failed", result.checksFailed)
    put("advisory_total", result.advisoryTotal)
    put("advisory_failed", result.advisoryFailed)
    put("duration_ms", result.durationMs)
    putJsonArray("diffs") { result.diffs.forEach { add(renderDiff(it)) } }
    putJsonObject("observed") {
      result.observed.forEach { (step, byProbe) ->
        putJsonObject(step) { byProbe.forEach { (probe, value) -> put(probe, value) } }
      }
    }
    putJsonObject("attachments") { result.attachments.forEach { (key, value) -> put(key, value) } }
  }

  private fun renderDiff(diff: Diff): JsonObject = buildJsonObject {
    put("at", diff.at)
    put("probe", diff.probe)
    diff.target?.let { put("target", it) }
    put("property", diff.property)
    put("expected", diff.expected)
    put("actual", diff.actual)
    diff.tolerance?.let { put("tolerance", it) }
  }

  /**
   * The pass rate.
   *
   * The denominator is `total − suspicious − skipped` (guide §7): a gold whose reference behaviour
   * is disputed, or whose format this runner does not implement, is not something the player can be
   * held to. Everything else counts, including `ERROR` — a crash is not a pass.
   */
  public fun passRate(results: List<GoldResult>): Double {
    val scored = results.filter { it.status != "SUSPICIOUS" && it.status != "SKIP" }
    if (scored.isEmpty()) return 0.0
    return scored.count { it.status == "PASS" }.toDouble() / scored.size
  }
}
