package ee.schimke.composeai.rcconformance

import ee.schimke.composeai.rcconformance.corpus.Results
import ee.schimke.composeai.rcconformance.corpus.parseGold
import ee.schimke.composeai.rcconformance.engine.AndroidxJvmEngine
import ee.schimke.composeai.rcconformance.engine.CmpEngine
import ee.schimke.composeai.rcconformance.engine.NativeSwiftEngine
import ee.schimke.composeai.rcconformance.runner.ConformanceEngine
import ee.schimke.composeai.rcconformance.runner.ConformanceRunner
import ee.schimke.composeai.rcconformance.runner.GoldResult
import java.io.File
import kotlin.test.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Scores one player against the corpus and writes its results and scorecard.
 *
 * A JUnit harness rather than a `main`, because the engines it drives live in the test source set:
 * the Compose test API — `runComposeUiTest`, `mainClock`, `captureToImage` — is the only thing that
 * drives a player the same way on every Compose target, and it resolves from a test configuration.
 * The repo's other rendering harnesses are shaped the same way for the same reason.
 *
 * **A harness, not an assertion test.** With no corpus configured it renders nothing and passes, so
 * `check` stays green on a bare tree; a failing gold is a *result* to report, not a reason to fail
 * the build. The lane is deliberately non-gating — see `docs/design/RC_CONFORMANCE.md`.
 *
 * The corpus directory is **required configuration**, never a path baked into this repository. That
 * is `PLAYER_IMPLEMENTATION_GUIDE.md` §1's instruction, and the reason behind it is the one failure
 * this whole lane exists to prevent: a vendored copy drifts the first time a gold is regenerated,
 * and the drift is silent — the runner keeps passing against a stale expectation and the score
 * still looks fine.
 */
public class RcConformanceHarness {
  @Test
  public fun score() {
    val specDir = System.getProperty(SPEC_PROPERTY)?.let(::File)
    if (specDir == null) {
      println("no $SPEC_PROPERTY configured — nothing to score")
      return
    }
    run(
      Options(
        specDir = specDir,
        player = System.getProperty(PLAYER_PROPERTY) ?: "cmp",
        out = System.getProperty(OUT_PROPERTY) ?: "build/conformance",
        filter = System.getProperty(FILTER_PROPERTY)?.takeIf(String::isNotBlank),
      )
    )
  }

  private companion object {
    const val SPEC_PROPERTY = "rc.specDir"
    const val PLAYER_PROPERTY = "rc.player"
    const val OUT_PROPERTY = "rc.player.out"
    const val FILTER_PROPERTY = "rc.filter"
  }
}

private fun run(options: Options) {
  val goldDir = File(options.specDir, "gold")
  if (!goldDir.isDirectory) {
    // Not an empty run: an absent corpus and a player that renders nothing produce the same zero,
    // and reporting one as the other is how a lane quietly stops measuring anything.
    System.err.println(
      "no corpus at ${goldDir.absolutePath}\n" +
        "  Check out the vendor/androidx-rc-conformance branch and point --spec-dir (or RC_SPEC_DIR)\n" +
        "  at third_party/rc-conformance-spec/compose/remote/specification/conformance."
    )
    error("no corpus at ${goldDir.absolutePath}")
  }

  val json = Json { ignoreUnknownKeys = true }
  val golds =
    goldDir
      .walkTopDown()
      .filter { it.isFile && it.name.endsWith(".gold.json") }
      .sortedBy { it.path }
      .mapNotNull { file ->
        runCatching { parseGold(json.parseToJsonElement(file.readText()).jsonObject) }
          .onFailure { System.err.println("skipping ${file.name}: $it") }
          .getOrNull()
      }
      .filter {
        options.filter == null || options.filter in it.name || options.filter == it.category
      }
      .toList()

  check(golds.isNotEmpty()) { "no golds matched ${options.filter ?: "(no filter)"}" }

  val engine = engineFor(options.player, options.specDir)
  val runner = ConformanceRunner(engine)

  println("corpus: ${golds.size} golds from ${goldDir.absolutePath}")
  println("player: ${engine.name} ${engine.version}")

  val results = mutableListOf<GoldResult>()
  golds.forEachIndexed { index, gold ->
    // Progress on stdout rather than a silent wait: a full corpus run rebuilds a Compose scene per
    // resize step, so a lane that has stopped making progress and one that is merely slow look
    // identical without it. The corpus lane on `main` was changed for the same reason (#170).
    print("\r[${index + 1}/${golds.size}] ${gold.name.padEnd(PROGRESS_WIDTH).take(PROGRESS_WIDTH)}")
    System.out.flush()
    results += runner.run(gold)
  }
  println()

  val outDir = File(options.out).apply { mkdirs() }
  val resultsFile = File(outDir, "conformance-results.json")
  resultsFile.writeText(
    Json { prettyPrint = true }
      .encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        Results.render(engine.name, engine.version, results, goldDir.absolutePath),
      )
  )

  val scorecard = Scorecard.render(engine.name, results)
  File(outDir, "scorecard.md").writeText(scorecard)

  println()
  println(scorecard)
  println(Scorecard.oneLine(engine.name, results))
  println("results:   ${resultsFile.absolutePath}")
  println("scorecard: ${File(outDir, "scorecard.md").absolutePath}")
}

private const val PROGRESS_WIDTH = 48

private fun engineFor(player: String, specDir: File): ConformanceEngine =
  when (player) {
    "cmp" -> CmpEngine(specDir)
    "androidx-jvm" -> AndroidxJvmEngine()
    "native-appkit" -> NativeSwiftEngine(NativeSwiftEngine.defaultBinary(), specDir)
    else ->
      error(
        "unknown player '$player'. Known lanes: cmp, androidx-jvm, native-appkit. " +
          "A new lane is a new ConformanceEngine in the engine package."
      )
  }

private data class Options(
  val specDir: File,
  val player: String,
  val out: String,
  val filter: String?,
)
