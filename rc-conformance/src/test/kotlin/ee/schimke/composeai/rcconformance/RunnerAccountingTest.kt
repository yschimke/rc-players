package ee.schimke.composeai.rcconformance

import ee.schimke.composeai.rcconformance.corpus.Check
import ee.schimke.composeai.rcconformance.corpus.Gold
import ee.schimke.composeai.rcconformance.corpus.Step
import ee.schimke.composeai.rcconformance.corpus.parseGold
import ee.schimke.composeai.rcconformance.runner.ConformanceEngine
import ee.schimke.composeai.rcconformance.runner.ConformanceRunner
import ee.schimke.composeai.rcconformance.runner.ConformanceSession
import ee.schimke.composeai.rcconformance.runner.Diff
import ee.schimke.composeai.rcconformance.runner.Observation
import ee.schimke.composeai.rcconformance.runner.UnsupportedStepKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
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

    override fun open(gold: Gold) =
      object : ConformanceSession {
        override fun execute(step: Step) {
          if (step.kind !in drivable) throw UnsupportedStepKind(step.kind)
        }

        override fun observe(check: Check) = observation(check)

        override fun close() = Unit
      }
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
}
