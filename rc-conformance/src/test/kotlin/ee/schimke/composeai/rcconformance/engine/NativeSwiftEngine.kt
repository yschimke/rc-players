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
 * original reference lane, and it carries the same consequence: **no gold in the corpus is
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
public class NativeSwiftEngine(private val playerBinary: File, private val specDir: File) :
  ConformanceEngine {
  override val name: String = "native-appkit"

  override val version: String = "appkit"

  init {
    require(playerBinary.canExecute()) {
      "the packaged macOS player is not executable at $playerBinary; " +
        "build it with scripts/package-macos-player.sh"
    }
  }

  override fun <T> withSession(gold: Gold, block: (ConformanceSession) -> T): T =
    NativeSwiftSession(gold, playerBinary, specDir).use { block(it) }

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

private const val MILLIS_PER_SECOND = 1000.0

/** The corpus's frame rate for `frame_sequence` (§3): one frame per sixtieth of a second. */
private const val FRAMES_PER_SECOND = 60.0

private class NativeSwiftSession(
  private val gold: Gold,
  private val playerBinary: File,
  private val specDir: File,
) : ConformanceSession, AutoCloseable {
  private var width = gold.parameters.intOrDefault("width", 400)
  private var height = gold.parameters.intOrDefault("height", 400)

  /** The logical clock a frame is captured at, in seconds, and the input driven before it. */
  private var clock = 0.0
  private var wallClock: JsonObject? = null
  private val driven = mutableListOf<JsonObject>()

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

  /** Each frame's document values, as the player resolved them. */
  private var capturedValues: Map<String, JsonObject>? = null

  /** Whether the final input replayed before each frame was delivered to the document. */
  private var capturedInputHandled: Map<String, JsonPrimitive>? = null
  private var capturedRecords: Map<String, JsonObject>? = null

  private class Frame(
    val id: String,
    val width: Int,
    val height: Int,
    val time: Double,
    val wallClock: JsonObject?,
    /**
     * The input driven before this frame, replayed by the player: it opens a document per frame.
     */
    val steps: List<JsonObject>,
  )

  override fun execute(step: Step, onCapture: (String) -> Unit) {
    when (step.kind) {
      "paint" -> {
        step.float("animation_time_seconds")?.let { clock = it.toDouble() }
        request(step.id)
      }
      "resize" -> {
        width = step.int("width", width)
        height = step.int("height", height)
        request(step.id)
      }
      // Nothing to settle in a still capture, and saying "unsupported" would fail checks this lane
      // can in fact answer at that point.
      "settle" -> request(step.id)
      "advance_time" -> {
        clock += step.int("advance_millis", 0) / MILLIS_PER_SECOND
        request(step.id)
      }
      "time" -> {
        step.float("seconds")?.let { clock = it.toDouble() }
        request(step.id)
      }
      "frame_sequence" -> frameSequence(step, onCapture)
      "clock_snapshot" -> clockSnapshot(step)
      "click",
      "longPress",
      "doubleClick",
      "touch_down",
      "touch_drag",
      "touch_up" -> gesture(step)
      "trigger" -> trigger(step)
      // A theme this player does not model, and a clock it does not carry: refused rather than
      // silently treated as a repaint, which would turn the step's checks into false passes.
      else -> throw UnsupportedStepKind(step.kind)
    }
  }

  /**
   * A gesture the lane drives, recorded with the clock it happens at.
   *
   * The list accumulates and travels with every later frame, because the player opens the document
   * fresh for each capture: a frame is always "the document after everything the timeline has done
   * to it so far", which is what a check bound to that step asserts.
   */
  private fun gesture(step: Step) {
    val eventTime = clock
    // Input is dispatched before this delay; the corpus observes the repaint after it. Keep both
    // instants so actions read the event clock while layout/animation refreshes at the capture
    // clock.
    clock += step.int("advance_millis", 0) / MILLIS_PER_SECOND
    driven += buildJsonObject {
      put("kind", step.kind)
      step.float("x")?.let { put("x", it) }
      step.float("y")?.let { put("y", it) }
      step.float("dx")?.let { put("dx", it) }
      step.float("dy")?.let { put("dy", it) }
      put("at", eventTime)
      put("capture_at", clock)
      put("width", width)
      put("height", height)
    }
    request(step.id)
  }

  /**
   * A `trigger` is a one-shot stimulus followed by a single measure/paint (§3).
   *
   * Only `resize` and `click` triggers appear in the corpus; anything else is refused rather than
   * silently treated as a no-op.
   */
  private fun trigger(step: Step) {
    val trigger = step.obj("trigger") ?: throw UnsupportedStepKind("trigger(no payload)")
    fun number(key: String): Double? = (trigger[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
    when ((trigger["type"] as? JsonPrimitive)?.content) {
      "resize" -> {
        width = number("width")?.toInt() ?: width
        height = number("height")?.toInt() ?: height
        request(step.id)
      }
      "click" -> {
        driven += buildJsonObject {
          put("kind", "click")
          number("x")?.let { put("x", it) }
          number("y")?.let { put("y", it) }
          put("at", clock)
          put("capture_at", clock)
          put("width", width)
          put("height", height)
        }
        request(step.id)
      }
      else -> throw UnsupportedStepKind("trigger(${trigger["type"]})")
    }
  }

  /**
   * Paints frames `0…total_frames`, walking the clock a frame at a time and capturing the ones the
   * step names. A check binds to `frame_<n>`, the frame's *number* rather than its index in the
   * capture list.
   */
  private fun frameSequence(step: Step, onCapture: (String) -> Unit) {
    val base = step.int("base_time_millis", 0) / MILLIS_PER_SECOND
    val captures = step.ints("capture").toSet()
    for (frame in 0..step.int("total_frames", 0)) {
      clock = base + frame / FRAMES_PER_SECOND
      request("frame_$frame")
      // Particle systems advance once per rendered frame. Keep uncaptured frames in the native
      // batch so retained state reaches frame N exactly as the reference does; only bind checks to
      // the captures the gold requested.
      if (captures.isEmpty() || frame in captures) onCapture("frame_$frame")
    }
  }

  /** Freezes both elapsed and calendar time for the clock corpus. */
  private fun clockSnapshot(step: Step) {
    wallClock = step.obj("clock")
    (wallClock?.get("continuous_seconds") as? JsonPrimitive)?.content?.toDoubleOrNull()?.let {
      clock = it
    }
    request(step.id)
  }

  private fun request(id: String) {
    requested += Frame(id, width, height, clock, wallClock, driven.toList())
    // The captures are taken lazily, in one subprocess, the first time a probe asks for one. A
    // check bound to an earlier step still reads that step's own frame, because each is captured at
    // the viewport recorded when the step ran.
    captured = null
    capturedTrees = null
    capturedValues = null
    capturedInputHandled = null
    capturedRecords = null
    batch = null
  }

  override fun observe(check: Check): Observation =
    when (check.key) {
      "raster" -> raster(check.at)
      "tree" -> tree(check.at)
      "float",
      "int",
      "text",
      "color",
      "matrix",
      "float_array:dynamic",
      "float_array:data" -> scalar(check)
      "particles" -> particles(check.at)
      "ops:count" -> operationCount(check.at)
      "ops:counts" -> operationMetric(check.at, "ops_counts")
      "ops:present",
      "ops:absent" -> operationPresence(check.at)
      "draw_log:commands" -> drawLog(check.at)
      "ops:component_count" -> operationMetric(check.at, "component_count")
      "ops:total_glyphs" -> operationMetric(check.at, "total_glyphs")
      "ops:distinct_ids" -> operationMetric(check.at, "distinct_ids")
      "trace:handled" -> inputHandled(check.at)
      "trace:host_actions" -> records(check)
      "trace:branches" -> records(check)
      "records:animation_specs",
      "records:anchor_runs",
      "records:glyph_runs",
      "records:component_bindings",
      "records:components",
      "records:semantics",
      "records:paths",
      "records:tweens",
      "records:uniforms" -> records(check)
      else -> Observation.NotImplemented
    }

  /**
   * A document's own value, as the corpus's scalar and float-array probes read it.
   *
   * The player resolves the slots this gold asserts at each frame's instant, so the value and the
   * pixels describe one moment. Two absences are kept apart: a *numeric* target the document left
   * empty is an observation and reports null, while a *named* one the document never declared is
   * unobservable — the player leaves the key out, and this lane reports its own gap rather than a
   * wrong value.
   */
  private fun scalar(check: Check): Observation {
    val target = check.target ?: return Observation.NotImplemented
    val frames = capturedValues ?: captureValues().also { capturedValues = it }
    val values = frames[check.at] ?: return Observation.NotImplemented
    val bucket = values[bucketName(check.key)] as? JsonObject ?: return Observation.NotImplemented
    val value = bucket[target] ?: return Observation.NotImplemented
    return Observation.Value(value)
  }

  private fun bucketName(probe: String): String =
    when (probe) {
      "float" -> "floats"
      "int" -> "integers"
      "text" -> "texts"
      "color" -> "colors"
      "matrix" -> "matrices"
      "float_array:dynamic" -> "float_arrays_dynamic"
      "float_array:data" -> "float_arrays_data"
      else -> error("Not a native Swift value probe: $probe")
    }

  private fun operationCount(stepId: String): Observation {
    val frames = capturedRecords ?: captureRecords().also { capturedRecords = it }
    return frames[stepId]?.get("ops_count")?.let(Observation::Value) ?: Observation.NotImplemented
  }

  private fun operationPresence(stepId: String): Observation {
    val frames = capturedRecords ?: captureRecords().also { capturedRecords = it }
    return frames[stepId]?.get("ops_present")?.let(Observation::Value) ?: Observation.NotImplemented
  }

  /** Structural LOOM probes: materialised component count and ID uniqueness. */
  private fun operationMetric(stepId: String, metric: String): Observation {
    val frames = capturedRecords ?: captureRecords().also { capturedRecords = it }
    return frames[stepId]?.get(metric)?.let(Observation::Value) ?: Observation.NotImplemented
  }

  /** The AppKit host publishes particle rows from the first declared system in wire order. */
  private fun particles(stepId: String): Observation {
    val frames = capturedRecords ?: captureRecords().also { capturedRecords = it }
    return frames[stepId]?.get("particles")?.let(Observation::Value) ?: Observation.NotImplemented
  }

  private fun drawLog(stepId: String): Observation {
    val frames = capturedRecords ?: captureRecords().also { capturedRecords = it }
    return frames[stepId]?.get("draw_log_commands")?.let(Observation::Value)
      ?: Observation.NotImplemented
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

  private fun inputHandled(stepId: String): Observation {
    val handled = capturedInputHandled ?: captureInputHandled().also { capturedInputHandled = it }
    return handled[stepId]?.let(Observation::Value) ?: Observation.NotImplemented
  }

  private fun records(check: Check): Observation {
    val channel = check.channel ?: return Observation.NotImplemented
    val frames = capturedRecords ?: captureRecords().also { capturedRecords = it }
    return frames[check.at]?.get(channel)?.let(Observation::Value) ?: Observation.NotImplemented
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
      put("fontPath", File(specDir, "fonts/Ahem.ttf").absolutePath)
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
                frame.wallClock?.let { put("wall_clock", it) }
                if (frame.steps.isNotEmpty()) {
                  put("steps", buildJsonArray { frame.steps.forEach { add(it) } })
                }
                if (valueTargets.isNotEmpty()) {
                  put(
                    "values",
                    buildJsonObject {
                      valueTargets.forEach { (bucket, targets) ->
                        put(
                          bucket,
                          buildJsonArray { targets.forEach { add(JsonPrimitive(it)) } },
                        )
                      }
                    },
                  )
                }
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
    parseErrors(stdout)
      .takeIf { it.isNotEmpty() }
      ?.let { System.err.println("native-appkit: ${gold.name} frame error -- $it") }
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

  /** The document values the player reported for each frame, keyed by step id. */
  private fun captureValues(): Map<String, JsonObject> {
    if (valueTargets.isEmpty()) return emptyMap()
    val frames = runBatch()["frames"]?.jsonArray ?: return emptyMap()
    return frames
      .mapNotNull { it as? JsonObject }
      .mapNotNull { entry ->
        val id = (entry["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        val values = entry["values"] as? JsonObject ?: return@mapNotNull null
        id to values
      }
      .toMap()
  }

  /** Decoded operation records emitted beside each captured frame. */
  private fun captureRecords(): Map<String, JsonObject> {
    val frames = runBatch()["frames"]?.jsonArray ?: return emptyMap()
    return frames
      .mapNotNull { it as? JsonObject }
      .mapNotNull { entry ->
        val id = (entry["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        val records = entry["records"] as? JsonObject ?: return@mapNotNull null
        id to records
      }
      .toMap()
  }

  /** The targets this gold's value probes assert, one list per slot kind. */
  private val valueTargets: Map<String, List<String>> by lazy {
    gold.checks
      .filter {
        it.key in
          setOf(
            "float",
            "int",
            "text",
            "color",
            "matrix",
            "float_array:dynamic",
            "float_array:data",
          )
      }
      .mapNotNull { check -> check.target?.let { bucketName(check.key) to it } }
      .groupBy({ it.first }, { it.second })
      .mapValues { (_, targets) -> targets.distinct() }
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

  private fun captureInputHandled(): Map<String, JsonPrimitive> {
    if (requested.isEmpty()) return emptyMap()
    val frames = runBatch()["frames"]?.jsonArray ?: return emptyMap()
    return frames
      .mapNotNull { it as? JsonObject }
      .mapNotNull { entry ->
        val id = (entry["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        val handled = entry["input_handled"] as? JsonPrimitive ?: return@mapNotNull null
        id to handled
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
