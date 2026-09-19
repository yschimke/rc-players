package ee.schimke.composeai.rcconformance.engine

import ee.schimke.composeai.rcconformance.corpus.Check
import ee.schimke.composeai.rcconformance.corpus.Gold
import ee.schimke.composeai.rcconformance.corpus.Step
import ee.schimke.composeai.rcconformance.runner.ConformanceEngine
import ee.schimke.composeai.rcconformance.runner.ConformanceSession
import ee.schimke.composeai.rcconformance.runner.Observation
import ee.schimke.composeai.rcconformance.runner.UnsupportedStepKind
import ee.schimke.composeai.rcconformance.runner.toRgba
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The native Swift stack, **AppKit** cut — the macOS sample player.
 *
 * ### What this lane does and does not measure
 *
 * It measures `RcNativePlayerCore` — decode, expressions, resolved layout values — plus the macOS
 * sample's own AppKit drawing. It does **not** measure `RcNativePlayerUIKit`, which is what ships
 * on iOS. Those are two independent renderers over one core: the UIKit one is ~5200 lines, the
 * AppKit view inside `samples/macos-player` is ~1500, and neither imports the other.
 *
 * That distinction decides how to read the raster column. A decode refusal here is a **core** gap
 * and is real for both hosts. A pixel disagreement here is an **AppKit** disagreement, and drawing
 * a conclusion about the iOS player from it — or worse, changing the iOS player because of it —
 * would be unfounded. An iOS lane is separate work; it needs the simulator this one deliberately
 * avoids.
 *
 * ### Why this lane is out-of-process
 *
 * The other two engines call a player on this JVM. This one cannot: the player is Swift. So the
 * seam is a subprocess — the packaged macOS player, driven through its `--conformance-batch` mode,
 * which takes a document and a list of frames and writes one PNG per frame.
 *
 * Running on AppKit rather than an iOS simulator is what makes a full pass affordable: the
 * simulator costs a boot, an install and a launch per run.
 *
 * Batching is per gold rather than per frame for the same reason at a smaller scale: a gold's
 * timeline is short, so one process per gold turns what would be thousands of AppKit startups into
 * hundreds.
 *
 * ### What it observes, and what it does not
 *
 * `raster` only, for now, driving `paint` and `resize`. That is deliberately the same scope as the
 * `androidx-jvm` reference lane, and it carries the same consequence: **no gold in the corpus is
 * raster-only**, so this lane's gold count is zero by construction and its raster column is the
 * number it exists for. It answers one question — where this player's pixels disagree with the
 * corpus, and whether the CMP player disagrees in the same places.
 *
 * `tree` is the natural next channel and is where gold passes would come from: 174 of the 252 golds
 * carry one. It needs the laid-out AppKit geometry surfaced through the batch protocol, which is a
 * change on the Swift side rather than here.
 *
 * Every other step kind throws [UnsupportedStepKind] rather than no-opping, so the checks behind
 * them fail visibly as `STEP_NOT_RUN`.
 */
public class NativeSwiftEngine(private val playerBinary: File) : ConformanceEngine {
  override val name: String = "native-appkit"

  override val version: String = "appkit"

  init {
    require(playerBinary.canExecute()) {
      "the packaged macOS player is not executable at $playerBinary; " +
        "build it with scripts/package-macos-player.sh"
    }
  }

  override fun <T> withSession(gold: Gold, block: (ConformanceSession) -> T): T =
    NativeSwiftSession(gold, playerBinary).use { block(it) }

  public companion object {
    /**
     * Where `scripts/package-macos-player.sh` leaves the app.
     *
     * `rc.macosPlayer` is set by the Gradle task and is already absolute. The environment variable
     * is the same override `scripts/measure-native-appkit.sh` honours, so one setting moves both.
     */
    public fun defaultBinary(): File =
      System.getProperty("rc.macosPlayer")?.let(::File)
        ?: File(
          System.getenv("RC_MACOS_PLAYER_BUILD_DIR") ?: "build/macos-player",
          "Remote Compose Player.app/Contents/MacOS/RemoteComposePlayer",
        )
  }
}

private class NativeSwiftSession(private val gold: Gold, private val playerBinary: File) :
  ConformanceSession, AutoCloseable {
  private var width = gold.parameters.intOrDefault("width", 400)
  private var height = gold.parameters.intOrDefault("height", 400)

  private val workingDirectory: File = Files.createTempDirectory("rc-conformance-swift").toFile()

  /** Frames requested so far, in order, each with the viewport that was current when asked for. */
  private val requested = mutableListOf<Frame>()

  private var captured: Map<String, ByteArray>? = null

  /** The batch's own JSON, so the two probes share one subprocess rather than starting two. */
  private var batch: JsonObject? = null

  /**
   * Each frame's laid-out tree, as the player reported it.
   *
   * The corpus's `tree` probe reads this: one node per named component with its laid-out geometry,
   * visibility and depth. The player emits it beside the PNG from the same view, so the two
   * channels cannot disagree about what was rendered.
   */
  private var capturedTrees: Map<String, JsonElement>? = null

  private class Frame(val id: String, val width: Int, val height: Int, val time: Double)

  override fun execute(step: Step, onCapture: (String) -> Unit) {
    when (step.kind) {
      "paint" -> request(step.id)
      "resize" -> {
        width = step.int("width", width)
        height = step.int("height", height)
        request(step.id)
      }
      // Nothing to settle in a still capture, and saying "unsupported" would fail checks this lane
      // can in fact answer at that point.
      "settle" -> Unit
      // A gesture to dispatch, a clock to move, a frame to advance — all need the player driven
      // rather than captured. The batch protocol renders one still frame per request, so these are
      // refused rather than silently treated as a repaint.
      else -> throw UnsupportedStepKind(step.kind)
    }
  }

  private fun request(id: String) {
    requested += Frame(id, width, height, 0.0)
    // The captures are taken lazily, in one subprocess, the first time a probe asks for one. A
    // check bound to an earlier step still reads that step's own frame, because each is captured at
    // the viewport recorded when the step ran.
    captured = null
    capturedTrees = null
    batch = null
  }

  override fun observe(check: Check): Observation =
    when (check.key) {
      "raster" -> raster(check.at)
      "tree" -> tree(check.at)
      else -> Observation.NotImplemented
    }

  private fun tree(stepId: String): Observation {
    val trees = capturedTrees ?: captureTrees().also { capturedTrees = it }
    val nodes = trees[stepId] ?: return Observation.NotImplemented
    return Observation.Value(nodes)
  }

  private fun raster(stepId: String): Observation {
    val frames = captured ?: capture().also { captured = it }
    val png = frames[stepId] ?: return Observation.NotImplemented
    val decoded =
      ImageIO.read(ByteArrayInputStream(png))
        ?: error("could not decode the ${gold.name} frame for $stepId")
    val rgba = decoded.toRgba()
    return Observation.Raster(rgba.width, rgba.height, rgba.rgba)
  }

  /**
   * Runs the batch once, on the first probe that asks for a frame.
   *
   * Both channels come out of the same subprocess: the PNGs land on disk and the trees come back in
   * its JSON, so a gold whose checks are all `tree` or all `raster` pays for one player start.
   */
  private fun runBatch(): JsonObject {
    batch?.let {
      return it
    }
    val job = buildJsonObject {
      put("document", gold.documentBase64)
      put("output", workingDirectory.absolutePath)
      put(
        "frames",
        buildJsonArray {
          requested.forEach { frame ->
            add(
              buildJsonObject {
                put("id", frame.id)
                put("width", frame.width)
                put("height", frame.height)
                put("time", frame.time)
              }
            )
          }
        },
      )
    }
    val jobFile = File(workingDirectory, "job.json").apply { writeText(job.toString()) }
    val process =
      ProcessBuilder(playerBinary.absolutePath, "--conformance-batch", jobFile.absolutePath)
        .redirectErrorStream(false)
        .start()
    process.outputStream.close()
    val stdout = process.inputStream.readBytes().decodeToString()
    val stderr = process.errorStream.readBytes().decodeToString()
    val exit = process.waitFor()
    // A player that refuses the whole batch leaves every frame unobserved, which the runner reports
    // as failing checks. That is the right outcome and it must not be mistaken for a runner fault,
    // so the player's own message is carried into the failure.
    if (exit != 0) {
      error("the macOS player failed for ${gold.name} (exit $exit): ${stderr.trim()}")
    }
    // A frame the player refused comes back as an error rather than a PNG, and the check behind it
    // fails. Saying so on stderr matters: without it a refused document is indistinguishable in the
    // scorecard from a probe this lane never implemented, and those are opposite findings -- one is
    // the player's gap, the other is the lane's.
    val refused =
      requested.filterNot { File(workingDirectory, "${it.id}.png").isFile }.map(Frame::id)
    if (refused.isNotEmpty()) {
      System.err.println(
        "native-appkit: ${gold.name} produced no frame for ${refused.joinToString(", ")}" +
          (parseErrors(stdout).takeIf { it.isNotEmpty() }?.let { " -- $it" } ?: "")
      )
    }
    val parsed = Json.parseToJsonElement(stdout).jsonObject
    batch = parsed
    return parsed
  }

  /** The frame the player rendered, keyed by step id. */
  private fun capture(): Map<String, ByteArray> {
    if (requested.isEmpty()) return emptyMap()
    runBatch()
    return requested
      .mapNotNull { frame ->
        val png = File(workingDirectory, "${frame.id}.png")
        if (png.isFile) frame.id to png.readBytes() else null
      }
      .toMap()
  }

  /** The laid-out tree the player reported for each frame, keyed by step id. */
  private fun captureTrees(): Map<String, JsonElement> {
    if (requested.isEmpty()) return emptyMap()
    val frames = runBatch()["frames"]?.jsonArray ?: return emptyMap()
    return frames
      .mapNotNull { it as? JsonObject }
      .mapNotNull { entry ->
        val id = (entry["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        val tree = entry["tree"] ?: return@mapNotNull null
        id to tree
      }
      .toMap()
  }

  /**
   * The player reports a per-frame failure in its own words; carry those rather than paraphrase.
   */
  private fun parseErrors(stdout: String): String =
    Regex("\"error\"\\s*:\\s*\"(.*?)\"")
      .findAll(stdout)
      .map { it.groupValues[1] }
      .distinct()
      .joinToString("; ")

  override fun close() {
    captured = null
    workingDirectory.deleteRecursively()
  }
}

private fun JsonObject.intOrDefault(key: String, fallback: Int): Int =
  (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: fallback
