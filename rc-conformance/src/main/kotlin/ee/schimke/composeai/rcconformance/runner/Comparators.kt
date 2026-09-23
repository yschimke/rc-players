package ee.schimke.composeai.rcconformance.runner

import ee.schimke.composeai.rcconformance.corpus.Check
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** One entry of the result file's `diffs` array (`CONFORMANCE_FORMAT.md` §5.1). */
public data class Diff(
  public val at: String,
  public val probe: String,
  public val target: String?,
  public val property: String,
  public val expected: JsonElement,
  public val actual: JsonElement,
  public val tolerance: Double?,
) {
  public companion object {
    /** The check's step never executed. Always a failure — never a silent pass (§4). */
    public const val STEP_NOT_RUN: String = "STEP_NOT_RUN"

    /** The player does not implement the probe. Always a failure (§5.1). */
    public const val PROBE_NOT_IMPLEMENTED: String = "PROBE_NOT_IMPLEMENTED"
  }
}

/**
 * Compares one observation against its gold expectation.
 *
 * Shared by every lane on purpose: see [ConformanceEngine]. Each probe family that needs more than
 * a structural diff gets its own branch here, and everything else falls through to [compareValue],
 * which walks the expectation and applies the tolerance to any number it meets.
 */
public object Comparators {
  /** What one check's comparison produced: its diffs and, for rasters, the evidence beside them. */
  public data class Comparison(
    public val diffs: List<Diff>,
    /** Populated only for a `raster` check that produced a comparison at all. */
    public val raster: RasterComparison? = null,
  )

  public fun compare(
    check: Check,
    observation: Observation,
    tolerance: Double,
    renderedCanvas: String? = null,
  ): Comparison =
    when (observation) {
      is Observation.NotImplemented ->
        Comparison(listOf(diff(check, Diff.PROBE_NOT_IMPLEMENTED, check.expect, JsonNull, null)))
      is Observation.Raster -> compareRaster(check, observation, tolerance, renderedCanvas)
      is Observation.Value ->
        Comparison(
          when {
            check.probe == "tree" -> compareTree(check, observation.value, tolerance)
            check.key == "draw_log:commands" -> compareSubsequence(check, observation.value)
            check.key == "ops:present" -> comparePresence(check, observation.value, present = true)
            check.key == "ops:absent" -> comparePresence(check, observation.value, present = false)
            check.probe == "relation" -> compareRelations(check, observation.value, tolerance)
            else -> compareValue(check, "", check.expect, observation.value, tolerance)
          }
        )
    }

  // ---------------------------------------------------------------- tree

  /**
   * The layout tree (§4.3), which is 69% of the corpus by gold count.
   *
   * Matched by component id rather than by position, so a player that emits nodes in a different
   * order is reported as the geometry disagreement it may be, rather than as 40 spurious diffs from
   * one shifted index. Two rules from the spec are load-bearing:
   * * class names are compared with leading underscores stripped, so a player's own name mangling
   *   does not leak into the corpus; and
   * * the geometry of a node with `isGone: true` is **not** compared — a gone node's bounds are
   *   undefined and the engines legitimately disagree about them.
   */
  private fun compareTree(check: Check, actual: JsonElement, tolerance: Double): List<Diff> {
    val expected = check.expect as? JsonArray ?: return listOf(malformedExpectation(check))
    val actualNodes =
      (actual as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.associateBy { it["id"]?.numberOrNull()?.toInt() } ?: emptyMap()

    val diffs = mutableListOf<Diff>()
    for (element in expected) {
      val node = element as? JsonObject ?: continue
      val id = node["id"]?.numberOrNull()?.toInt()
      val target = id?.toString()
      val observed =
        actualNodes[id]
          ?: run {
            diffs += diff(check, "missing", node, JsonNull, null, target)
            continue
          }

      val gone = node["isGone"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
      for ((property, expectedValue) in node) {
        if (property == "id") continue
        if (gone && property in GEOMETRY) continue
        val actualValue = observed[property] ?: JsonNull
        val mismatch =
          when (property) {
            "kind" ->
              stripUnderscores(expectedValue.stringOrNull()) !=
                stripUnderscores(actualValue.stringOrNull())
            in NUMERIC_NODE_FIELDS -> !numbersMatch(expectedValue, actualValue, tolerance)
            else -> expectedValue != actualValue
          }
        if (mismatch) {
          diffs += diff(check, property, expectedValue, actualValue, tolerance, target)
        }
      }
    }
    return diffs
  }

  private val GEOMETRY = setOf("x", "y", "width", "height", "scroll_x", "scroll_y")
  private val NUMERIC_NODE_FIELDS = GEOMETRY + "depth"

  /** `_BoxLayout` and `BoxLayout` are the same class (§4.2). */
  private fun stripUnderscores(name: String?): String? = name?.trimStart('_')

  // ---------------------------------------------------------------- raster

  /**
   * The pixel metric (§2.5) and the evidence the report renders beside it, from one comparison.
   *
   * The verdict is [Pixelmatch]'s AA-aware count against the check's tolerance, exactly as before.
   * What is new is that the same walk's numbers are kept: the raw differing-pixel count, RMSE, the
   * largest channel delta, and the heatmap — so a report shows what a raster check *did* rather
   * than only what it failed. Publishing the count only when it was a failure is what made every
   * passing raster card read "n/a" and every badge show "?".
   */
  private fun compareRaster(
    check: Check,
    observation: Observation.Raster,
    tolerance: Double,
    renderedCanvas: String?,
  ): Comparison {
    val reference =
      decodeDataUriPng(check.expect.stringOrNull())
        ?: return Comparison(listOf(diff(check, "reference_image", check.expect, JsonNull, null)))

    // A differing size is itself a failure: scaling either image, or cropping both to the
    // document's declared size, scores a resize step's frame against the wrong box and reports zero
    // while proving nothing (guide §10). With no aligned pixels there is no comparison to publish
    // evidence for — the diff row says why.
    if (reference.width != observation.width || reference.height != observation.height) {
      return Comparison(
        listOf(
          diff(
            check,
            "dimensions",
            JsonPrimitive("${reference.width}x${reference.height}"),
            JsonPrimitive("${observation.width}x${observation.height}"),
            null,
          )
        )
      )
    }

    val walk =
      Pixelmatch.compare(observation.rgba, reference.rgba, reference.width, reference.height)
    val comparison =
      RasterComparison(
        at = check.at,
        // Already a data URI in the gold, so the reference frame is free here — no decode, and
        // no second copy of the corpus's own bytes.
        goldImageBase64 = check.expect.stringOrNull(),
        // Encoded once for the attachment and handed back in, so the frame in the report is the
        // frame that was scored.
        renderedCanvasBase64 = renderedCanvas.orEmpty(),
        totalPixels = reference.width * reference.height,
        rasterTolerance = check.tolerance ?: DEFAULT_RASTER_PIXELS,
        differingPixels = walk.rawDiffs,
        aaPixels = walk.hardDiffs,
        rmse = walk.rmse,
        maxDelta = walk.maxDelta,
        diffHeatmapBase64 = DiffHeatmap.encode(reference.width, reference.height, walk.magnitude),
      )
    // §2.5: the check's tolerance *is* the maximum differing-pixel count, defaulting to 16.
    val budget = if (check.tolerance != null) tolerance else DEFAULT_RASTER_PIXELS
    val diffs =
      if (walk.hardDiffs <= budget) {
        emptyList()
      } else {
        listOf(
          diff(
            check,
            "differing_pixels",
            JsonPrimitive(budget),
            JsonPrimitive(walk.hardDiffs),
            budget,
            check.target,
          )
        )
      }
    return Comparison(diffs, comparison)
  }

  private const val DEFAULT_RASTER_PIXELS = 16.0
  private const val LABEL = "label"

  // ---------------------------------------------------------------- sequences and sets

  /**
   * `draw_log:commands` is an **ordered subsequence**, not an exact list (§4.2): the recorded
   * stream also carries the root layout's own matrix scaffolding, which is not part of what the
   * gold asserts.
   */
  private fun compareSubsequence(check: Check, actual: JsonElement): List<Diff> {
    val expected = check.expect as? JsonArray ?: return listOf(malformedExpectation(check))
    val observed = (actual as? JsonArray).orEmpty().map { it.stringOrNull() }
    var cursor = 0
    for (element in expected) {
      val wanted = element.stringOrNull()
      val found = observed.subList(cursor, observed.size).indexOf(wanted)
      if (found < 0) {
        return listOf(diff(check, "missing_command", element, actual, null))
      }
      cursor += found + 1
    }
    return emptyList()
  }

  private fun comparePresence(check: Check, actual: JsonElement, present: Boolean): List<Diff> {
    val expected = check.expect as? JsonArray ?: return listOf(malformedExpectation(check))
    val observed = (actual as? JsonArray).orEmpty().mapNotNull { it.stringOrNull() }.toSet()
    return expected.mapNotNull { element ->
      val name = element.stringOrNull()
      if ((name in observed) == present) {
        null
      } else {
        diff(
          check,
          if (present) "missing" else "unexpected",
          element,
          JsonPrimitive(name in observed),
          null,
          name,
        )
      }
    }
  }

  // ---------------------------------------------------------------- structural fallback

  /**
   * Walks the expectation and reports where the observation departs from it.
   *
   * Driven by the *expected* shape rather than the observed one, so a player that reports extra
   * fields is not penalised for them — the corpus asserts what it names and nothing more. Numbers
   * compare within [tolerance]; everything else is exact.
   */
  private fun compareValue(
    check: Check,
    path: String,
    expected: JsonElement,
    actual: JsonElement,
    tolerance: Double,
  ): List<Diff> =
    when (expected) {
      is JsonObject -> {
        val observed = actual as? JsonObject
        if (observed == null) {
          listOf(diff(check, path.ifEmpty { "shape" }, expected, actual, tolerance))
        } else {
          expected
            // `label` is the corpus's human-readable annotation (§3), not an observation — the
            // record golds name each entry ("first box spec") for the reader. It is compared only
            // where the player reports one, as `records:glyph_runs` does.
            .filterKeys { key -> key != LABEL || LABEL in observed }
            .flatMap { (key, value) ->
              compareValue(check, join(path, key), value, observed[key] ?: JsonNull, tolerance)
            }
        }
      }
      is JsonArray -> {
        val observed = actual as? JsonArray
        when {
          observed == null ->
            listOf(diff(check, path.ifEmpty { "shape" }, expected, actual, tolerance))
          observed.size != expected.size ->
            listOf(
              diff(
                check,
                join(path, "length"),
                JsonPrimitive(expected.size),
                JsonPrimitive(observed.size),
                null,
              )
            )
          else ->
            expected.flatMapIndexed { index, value ->
              compareValue(check, join(path, "[$index]"), value, observed[index], tolerance)
            }
        }
      }
      else ->
        if (matches(expected, actual, tolerance)) {
          emptyList()
        } else {
          listOf(diff(check, path.ifEmpty { "value" }, expected, actual, tolerance))
        }
    }

  private fun matches(expected: JsonElement, actual: JsonElement, tolerance: Double): Boolean {
    val expectedNumber = expected.numberOrNull()
    return if (expectedNumber != null) {
      numbersMatch(expected, actual, tolerance)
    } else {
      expected == actual
    }
  }

  private fun numbersMatch(expected: JsonElement, actual: JsonElement, tolerance: Double): Boolean {
    val e = expected.numberOrNull() ?: return expected == actual
    val a = actual.numberOrNull() ?: return false
    return abs(e - a) <= tolerance
  }

  private fun join(path: String, part: String) =
    if (path.isEmpty()) part else if (part.startsWith("[")) "$path$part" else "$path.$part"

  // ---------------------------------------------------------------- relations

  /**
   * Relationships between recorded runs rather than their absolute coordinates (§4.2), so the check
   * survives two text stacks measuring a glyph differently.
   *
   * Each relation names an axis and the runs it spans, by index into the observed list.
   * `equal_gaps` holds when every consecutive difference along that axis matches the first within
   * [tolerance]. A relation over a run the player did not report fails as that relation, not as a
   * crash.
   */
  private fun compareRelations(check: Check, actual: JsonElement, tolerance: Double): List<Diff> {
    val runs =
      actual as? JsonArray ?: return listOf(diff(check, "shape", check.expect, actual, null))
    val relations = check.expect as? JsonArray ?: return listOf(malformedExpectation(check))
    return relations.mapNotNull { element ->
      val relation = element as? JsonObject ?: return@mapNotNull malformedExpectation(check)
      val kind = relation["kind"]?.stringOrNull()
      val axis = relation["axis"]?.stringOrNull()
      val indices = (relation["runs"] as? JsonArray)?.mapNotNull { it.numberOrNull()?.toInt() }
      if (kind == null || axis == null || indices == null)
        return@mapNotNull malformedExpectation(check)
      val values = indices.map { index ->
        ((runs.getOrNull(index) as? JsonObject)?.get(axis))?.numberOrNull()
      }
      val holds =
        values.none { it == null } &&
          values.filterNotNull().let { v ->
            val steps = v.zipWithNext { a, b -> b - a }
            when (kind) {
              "strictly_increasing" -> steps.all { it > tolerance }
              "strictly_decreasing" -> steps.all { it < -tolerance }
              "equal_gaps" -> steps.all { abs(it - steps.first()) <= tolerance }
              else -> false
            }
          }
      if (holds) null
      else
        diff(
          check,
          "$kind:$axis",
          relation,
          JsonArray(values.map { it?.let(::JsonPrimitive) ?: JsonNull }),
          tolerance,
        )
    }
  }

  // ---------------------------------------------------------------- helpers

  private fun malformedExpectation(check: Check) =
    diff(check, "malformed_expectation", check.expect, JsonNull, null)

  private fun diff(
    check: Check,
    property: String,
    expected: JsonElement,
    actual: JsonElement,
    tolerance: Double?,
    target: String? = check.target,
  ) =
    Diff(
      at = check.at,
      probe = check.key,
      target = target,
      property = property,
      expected = expected,
      actual = actual,
      tolerance = tolerance,
    )
}

internal fun JsonElement.numberOrNull(): Double? = (this as? JsonPrimitive)?.doubleOrNull

internal fun JsonElement.stringOrNull(): String? =
  (this as? JsonPrimitive)?.takeIf { it.isString }?.content
