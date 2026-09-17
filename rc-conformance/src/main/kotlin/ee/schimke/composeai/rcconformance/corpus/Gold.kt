package ee.schimke.composeai.rcconformance.corpus

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * One gold file, as `CONFORMANCE_FORMAT.md` §2 defines it.
 *
 * The parse is deliberately tolerant of keys it does not name. Format version 2 says a gold
 * contains only the documented keys (§2.11), but 40-odd `expected_*` keys from the pre-v2 era still
 * sit in many files, and a strict parse would reject the corpus over fields that are, by the
 * format's own account, not asserted. What is *read* here is exactly the asserted surface:
 * [timeline] and [checks].
 */
public data class Gold(
  public val name: String,
  public val category: String,
  public val description: String?,
  public val profile: String,
  public val formatVersion: Int,
  public val textMetrics: String,
  public val parameters: JsonObject,
  public val documentBase64: String,
  public val timeline: List<Step>,
  public val checks: List<Check>,
  public val tags: List<String>,
  public val suspiciousReason: String?,
) {
  public val isSuspicious: Boolean
    get() = "suspicious" in tags

  /** The file's default tolerance. Per-check overrides win; absent both, comparison is exact. */
  public val defaultTolerance: Double?
    get() = (parameters["tolerance"] as? JsonPrimitive)?.content?.toDoubleOrNull()

  @OptIn(ExperimentalEncodingApi::class)
  public fun documentBytes(): ByteArray = Base64.decode(documentBase64)
}

/**
 * A timeline step.
 *
 * The kind is kept as a string rather than an enum: the corpus already carries a `time` kind that
 * `CONFORMANCE_FORMAT.md` §3 does not document, and an enum would turn "the spec and the corpus
 * disagree" into a parse crash that hides every other result in the file. An engine that does not
 * understand a kind reports it as an unexecuted step, which fails that step's checks as
 * `STEP_NOT_RUN` — visible, and never a silent pass.
 */
public data class Step(public val id: String, public val kind: String, public val raw: JsonObject) {
  public fun int(key: String, default: Int): Int =
    (raw[key] as? JsonPrimitive)?.intOrNull ?: default

  public fun float(key: String): Float? = (raw[key] as? JsonPrimitive)?.floatOrNull

  public fun bool(key: String, default: Boolean): Boolean =
    (raw[key] as? JsonPrimitive)?.booleanOrNull ?: default

  public fun obj(key: String): JsonObject? = raw[key] as? JsonObject

  public fun ints(key: String): List<Int> =
    (raw[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull }

  /** `frames` defaults to 2 for both `paint` and `resize` (§3). */
  public val frames: Int
    get() = int("frames", 2)
}

/** A single assertion, bound to the step named by [at] (§4). */
public data class Check(
  public val at: String,
  public val probe: String,
  public val channel: String?,
  public val target: String?,
  public val expect: JsonElement,
  public val tolerance: Double?,
  /**
   * Whether the corpus declares this assertion **advisory** — reported, never binding.
   *
   * The corpus sets it on 605 of its 1515 checks, every one of them `raster`, and the reason is the
   * raster metric itself: an antialiasing-aware pixel walk across two different text stacks and two
   * different GPU backends disagrees for reasons that are not conformance gaps, so upstream reports
   * the number and declines to fail a gold on it. Its own published TypeScript results do exactly
   * that — 910 binding checks beside 605 advisory ones.
   *
   * Ignoring the flag does not make a runner stricter in a useful way; it makes its score
   * incomparable with every other player's, which is the one thing a conformance number is for.
   */
  public val advisory: Boolean = false,
) {
  /** `probe[:channel]` — the key both the report and `observed` are indexed by. */
  public val key: String
    get() = if (channel == null) probe else "$probe:$channel"

  public fun resolveTolerance(gold: Gold): Double = tolerance ?: gold.defaultTolerance ?: 0.0
}

/** Parses one gold file. Throws [IllegalArgumentException] if a required key is missing. */
public fun parseGold(root: JsonObject): Gold {
  fun str(key: String): String? = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

  val timeline =
    (root["timeline"] as? JsonArray).orEmpty().map { element ->
      val step = element.jsonObject
      Step(
        id = requireNotNull(str(step, "id")) { "timeline step without an id" },
        kind = requireNotNull(str(step, "kind")) { "timeline step without a kind" },
        raw = step,
      )
    }

  val checks =
    (root["checks"] as? JsonArray).orEmpty().map { element ->
      val check = element.jsonObject
      Check(
        at = requireNotNull(str(check, "at")) { "check without an `at`" },
        probe = requireNotNull(str(check, "probe")) { "check without a probe" },
        channel = str(check, "channel"),
        // A target may be a number or a variable name, so it is normalised to its text form.
        target = (check["target"] as? JsonPrimitive)?.content,
        expect = check["expect"] ?: JsonNull,
        tolerance = (check["tolerance"] as? JsonPrimitive)?.content?.toDoubleOrNull(),
        advisory = (check["advisory"] as? JsonPrimitive)?.booleanOrNull ?: false,
      )
    }

  return Gold(
    name = requireNotNull(str("name")) { "gold without a name" },
    category = str("category") ?: "uncategorised",
    description = str("description"),
    profile = str("profile") ?: "core",
    formatVersion = (root["format_version"] as? JsonPrimitive)?.intOrNull ?: 0,
    // Absent means the closed-form model, which is what the corpus's own default was before the
    // key existed.
    textMetrics = (root["harness"] as? JsonObject)?.let { str(it, "text_metrics") } ?: "ahem",
    parameters = (root["parameters"] as? JsonObject) ?: JsonObject(emptyMap()),
    documentBase64 = requireNotNull(str("document_base64")) { "gold without a document" },
    timeline = timeline,
    checks = checks,
    tags = (root["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
    suspiciousReason = str("suspicious_reason"),
  )
}

private fun str(obj: JsonObject, key: String): String? =
  (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
