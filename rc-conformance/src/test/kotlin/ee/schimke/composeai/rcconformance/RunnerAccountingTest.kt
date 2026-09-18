package ee.schimke.composeai.rcconformance

import ee.schimke.composeai.rcconformance.corpus.Check
import ee.schimke.composeai.rcconformance.corpus.Gold
import ee.schimke.composeai.rcconformance.corpus.Results
import ee.schimke.composeai.rcconformance.corpus.Step
import ee.schimke.composeai.rcconformance.corpus.parseGold
import ee.schimke.composeai.rcconformance.runner.ConformanceEngine
import ee.schimke.composeai.rcconformance.runner.ConformanceRunner
import ee.schimke.composeai.rcconformance.runner.ConformanceSession
import ee.schimke.composeai.rcconformance.runner.Diff
import ee.schimke.composeai.rcconformance.runner.Observation
import ee.schimke.composeai.rcconformance.runner.UnsupportedStepKind
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * The runner's accounting, which is the part that decides whether a score means anything.
 *
 * `PLAYER_IMPLEMENTATION_GUIDE.md` §8 lists six ways to get a wrong answer; three of them are the
 * runner quietly losing a check, and all three produce a *better*-looking number. These tests exist
 * because a dropped check is indistinguishable from a passing one in every output the runner
 * produces — the only place the difference is visible is here.
 */
class RunnerAccountingTest {
  private val json = Json { ignoreUnknownKeys = true }

  /** A minimal format-2 gold. The document is never decoded, because the fake engine ignores it. */
  private fun gold(timeline: String, checks: String): Gold =
    parseGold(
      json
        .parseToJsonElement(
          """
          {
            "format_version": 2,
            "name": "synthetic",
            "category": "test",
            "profile": "core",
            "harness": { "text_metrics": "ahem" },
            "parameters": { "tolerance": 0.5 },
            "document_base64": "",
            "timeline": $timeline,
            "checks": $checks
          }
          """
        )
        .jsonObject
    )

  /** Drives whatever it is given and observes whatever it is told to. */
  private class FakeEngine(
    private val drivable: Set<String>,
    private val observation: (Check) -> Observation,
  ) : ConformanceEngine {
    override val name = "fake"
    override val version = "test"

    override fun <T> withSession(gold: Gold, block: (ConformanceSession) -> T): T =
      block(
        object : ConformanceSession {
          override fun execute(step: Step, onCapture: (String) -> Unit) {
            if (step.kind !in drivable) throw UnsupportedStepKind(step.kind)
          }

          override fun observe(check: Check) = observation(check)
        }
      )
  }

  @Test
  fun aCheckBoundToAnUnrunStepFailsRatherThanVanishing() {
    val result =
      ConformanceRunner(
          FakeEngine(drivable = setOf("paint")) { Observation.Value(JsonPrimitive(1)) }
        )
        .run(
          gold(
            timeline =
              """[{"id":"initial","kind":"paint"},{"id":"later","kind":"click","x":1,"y":1}]""",
            checks =
              """[{"at":"initial","probe":"float","target":"a","expect":1},
                  {"at":"later","probe":"float","target":"b","expect":2}]""",
          )
        )

    assertEquals(1, result.checksFailed, "the check on the undriveable step was lost")
    assertEquals(Diff.STEP_NOT_RUN, result.diffs.single().property)
    assertEquals("later", result.diffs.single().at)
  }

  @Test
  fun anUnimplementedProbeFailsRatherThanPassing() {
    val result =
      ConformanceRunner(FakeEngine(drivable = setOf("paint")) { Observation.NotImplemented })
        .run(
          gold(
            timeline = """[{"id":"initial","kind":"paint"}]""",
            checks = """[{"at":"initial","probe":"tree","expect":[]}]""",
          )
        )

    assertEquals("FAIL", result.status)
    assertEquals(Diff.PROBE_NOT_IMPLEMENTED, result.diffs.single().property)
  }

  @Test
  fun aPerturbedExpectationFails() {
    // The guide's §11.2 self-check: "a check that cannot fail is not a check." If this passes, the
    // comparator is not comparing and every other result in the suite is meaningless.
    val engine = FakeEngine(drivable = setOf("paint")) { Observation.Value(JsonPrimitive(41.0)) }
    val checks = """[{"at":"initial","probe":"float","target":"answer","expect":42}]"""
    val timeline = """[{"id":"initial","kind":"paint"}]"""

    val perturbed = ConformanceRunner(engine).run(gold(timeline, checks))
    assertEquals("FAIL", perturbed.status)

    val matching =
      ConformanceRunner(
          FakeEngine(drivable = setOf("paint")) { Observation.Value(JsonPrimitive(42.0)) }
        )
        .run(gold(timeline, checks))
    assertEquals("PASS", matching.status)
  }

  @Test
  fun toleranceComesFromTheCheckThenTheFileThenExact() {
    val engine = FakeEngine(drivable = setOf("paint")) { Observation.Value(JsonPrimitive(42.4)) }
    val timeline = """[{"id":"initial","kind":"paint"}]"""

    // The file's 0.5 covers a 0.4 difference.
    assertEquals(
      "PASS",
      ConformanceRunner(engine)
        .run(gold(timeline, """[{"at":"initial","probe":"float","target":"a","expect":42}]"""))
        .status,
    )
    // A per-check override wins over it, in the tightening direction.
    assertEquals(
      "FAIL",
      ConformanceRunner(engine)
        .run(
          gold(
            timeline,
            """[{"at":"initial","probe":"float","target":"a","expect":42,"tolerance":0.01}]""",
          )
        )
        .status,
    )
  }

  @Test
  fun aSuspiciousGoldKeepsItsRealVerdictButLeavesTheRate() {
    val suspicious =
      parseGold(
        json
          .parseToJsonElement(
            """
            {
              "format_version": 2, "name": "disputed", "category": "test", "profile": "core",
              "harness": {}, "parameters": {}, "document_base64": "",
              "tags": ["suspicious"], "suspicious_reason": "reference believed wrong",
              "timeline": [{"id":"initial","kind":"paint"}],
              "checks": [{"at":"initial","probe":"float","target":"a","expect":1}]
            }
            """
          )
          .jsonObject
      )

    val result =
      ConformanceRunner(
          FakeEngine(drivable = setOf("paint")) { Observation.Value(JsonPrimitive(9)) }
        )
        .run(suspicious)

    assertEquals("SUSPICIOUS", result.status, "a disputed gold must not count against the player")
    assertEquals("FAIL", result.rawStatus, "…but the real verdict is still recorded")
    assertTrue(result.diffs.isNotEmpty())
  }

  // ---------------------------------------------------------------- raster evidence

  /** An opaque, single-colour frame as a `data:image/png;base64,…` URI, the gold's own format. */
  private fun solidDataUri(argb: Int): String {
    val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, 8, 8, IntArray(64) { argb }, 0, 8)
    val bytes =
      ByteArrayOutputStream().use { out ->
        ImageIO.write(image, "png", out)
        out.toByteArray()
      }
    @OptIn(ExperimentalEncodingApi::class)
    return "data:image/png;base64," + Base64.encode(bytes)
  }

  /** A straight-RGBA frame, per pixel. */
  private fun frame(pixel: (Int) -> Int) =
    ByteArray(8 * 8 * 4).also { out ->
      for (p in 0 until 8 * 8) {
        val argb = pixel(p)
        out[p * 4] = ((argb shr 16) and 0xFF).toByte()
        out[p * 4 + 1] = ((argb shr 8) and 0xFF).toByte()
        out[p * 4 + 2] = (argb and 0xFF).toByte()
        out[p * 4 + 3] = ((argb shr 24) and 0xFF).toByte()
      }
    }

  private fun rasterGold(expect: String, tolerance: Int = 16): Gold =
    gold(
      timeline = """[{"id":"initial","kind":"paint"}]""",
      checks = """[{"at":"initial","probe":"raster","expect":"$expect","tolerance":$tolerance}]""",
    )

  @Test
  fun aPassingRasterStillPublishesItsEvidence() {
    // The old runner published the differing-pixel count only where the check failed, so every
    // passing card in the generated audit read "Differing px: n/a" under a "?" badge. A check that
    // passed is exactly where a reader most wants the numbers that say why.
    val red = 0xFFE53935.toInt() // the corpus's own Material red
    val nearRed = { p: Int ->
      // Four pixels one channel-step off: raw-different, sub-threshold, and no verdict input.
      if (p < 4) 0xFFE43935.toInt() else red
    }
    val result =
      ConformanceRunner(
          FakeEngine(drivable = setOf("paint")) { Observation.Raster(8, 8, frame(nearRed)) }
        )
        .run(rasterGold(solidDataUri(red)))

    assertEquals("PASS", result.status)
    val comparison = result.rasterComparisons.single()
    assertEquals(4, comparison.differingPixels, "the raw deltas are still counted")
    assertEquals(0, comparison.aaPixels, "nothing reached the verdict's threshold")
    assertEquals(1, comparison.maxDelta)
    assertEquals(64, comparison.totalPixels)
    assertTrue(comparison.rmse != null && comparison.rmse > 0.0)
    assertTrue(comparison.diffHeatmapBase64!!.startsWith("data:image/png;base64,"))
  }

  @Test
  fun aFailingRasterReportsTheNumberItFailedOn() {
    // The pixel badge the audit renders is coloured from `aaPixels` against the tolerance, so it
    // must be the count the verdict used — the badge and the PASS/FAIL row cannot be allowed to
    // tell two different stories about the same frames.
    val result =
      ConformanceRunner(
          FakeEngine(drivable = setOf("paint")) {
            Observation.Raster(8, 8, frame { 0xFF2196F3.toInt() }) // blue where the gold is red
          }
        )
        .run(rasterGold(solidDataUri(0xFFE53935.toInt())))

    assertEquals("FAIL", result.status)
    val comparison = result.rasterComparisons.single()
    val counted = comparison.aaPixels
    checkNotNull(counted)
    assertEquals(64, counted, "every pixel moved three channels at full strength")
    assertEquals(196, comparison.maxDelta) // 0xE5 → 0x21 in red, the widest single-channel move
    val diffRow = result.diffs.single { it.property == "differing_pixels" }
    assertEquals(counted.toDouble(), (diffRow.actual as JsonPrimitive).content.toDouble())
  }

  @Test
  fun theResultsFileCarriesTheFieldsTheAuditGeneratorReads() {
    val red = 0xFFE53935.toInt()
    val rendered =
      ConformanceRunner(
          FakeEngine(drivable = setOf("paint")) {
            Observation.Raster(8, 8, frame { 0xFF2196F3.toInt() })
          }
        )
        .run(rasterGold(solidDataUri(red)))

    val file = Results.render("fake", "test", listOf(rendered), "corpus")
    val entry = (file["results"] as JsonArray).single().jsonObject
    // Top level: what renderOneComparison draws for the initial step.
    assertEquals(64, (entry["aaPixels"] as JsonPrimitive).content.toInt())
    assertEquals(64, (entry["differingPixels"] as JsonPrimitive).content.toInt())
    assertEquals(64, (entry["totalPixels"] as JsonPrimitive).content.toInt())
    assertEquals(16.0, (entry["rasterTolerance"] as JsonPrimitive).content.toDouble())
    assertTrue((entry["rmse"] as JsonPrimitive).content.toDouble() > 0.0)
    assertEquals(196, (entry["maxDelta"] as JsonPrimitive).content.toInt())
    assertTrue((entry["diffHeatmapBase64"] as JsonPrimitive).content.startsWith("data:image/png"))
    assertTrue(
      (entry["renderedCanvasBase64"] as JsonPrimitive).content.startsWith("data:image/png")
    )
    // And per step, which is what the resize/animation views read.
    val stepComparison = (entry["rasterComparisons"] as JsonArray).single().jsonObject
    assertEquals("initial", (stepComparison["at"] as JsonPrimitive).content)
    assertEquals(64, (stepComparison["aaPixels"] as JsonPrimitive).content.toInt())
    assertTrue(
      (stepComparison["diffHeatmapBase64"] as JsonPrimitive).content.startsWith("data:image/png")
    )
  }
}
