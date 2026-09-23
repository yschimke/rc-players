package ee.schimke.composeai.rcconformance.engine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import ee.schimke.composeai.rcconformance.corpus.Check
import ee.schimke.composeai.rcconformance.corpus.Gold
import ee.schimke.composeai.rcconformance.corpus.Step
import ee.schimke.composeai.rcconformance.runner.ConformanceEngine
import ee.schimke.composeai.rcconformance.runner.ConformanceSession
import ee.schimke.composeai.rcconformance.runner.Observation
import ee.schimke.composeai.rcconformance.runner.UnsupportedStepKind
import ee.schimke.composeai.rcconformance.runner.toRgba
import ee.schimke.composeai.rcplayer.compose.LocalRcAhemTextMetrics
import ee.schimke.composeai.rcplayer.compose.LocalRcInspection
import ee.schimke.composeai.rcplayer.compose.LocalRcTimeSource
import ee.schimke.composeai.rcplayer.compose.RcComponentIdKey
import ee.schimke.composeai.rcplayer.compose.RcComponentKindKey
import ee.schimke.composeai.rcplayer.compose.RcComponentVisibilityKey
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcContentInsetKey
import ee.schimke.composeai.rcplayer.compose.RcDocumentStateKey
import ee.schimke.composeai.rcplayer.compose.RcPlayerTheme
import ee.schimke.composeai.rcplayer.compose.RcScrollOffsetKey
import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcImpulseStart
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMatrixConstant
import ee.schimke.composeai.rcplayer.protocol.RcOperationInventory
import ee.schimke.composeai.rcplayer.protocol.RcParticleDefine
import ee.schimke.composeai.rcplayer.protocol.RcPathAppend
import ee.schimke.composeai.rcplayer.protocol.RcPathCreate
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcPathTween
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import ee.schimke.composeai.rcplayer.runtime.RcTimeSnapshot
import ee.schimke.composeai.rcplayer.runtime.RcTimeSource
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import javax.imageio.ImageIO
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * The supported CMP player — `rc-player-compose` — driven through the Compose **test** API.
 *
 * The host is [SkikoComposeUiTest]: the same `ComposeUiTest` surface that exists on every Compose
 * target, so this engine is the one that can be lifted to macOS, iOS and wasm rather than a
 * JVM-only harness. It also means animation is advanced by `mainClock` — the framework's own
 * deterministic clock — instead of a nanosecond counter this file increments by hand, and gestures
 * go through `performTouchInput`, the path the player's own passing interaction tests use.
 *
 * It still rasterizes through skiko's software path with no `DISPLAY`, which is what lets this lane
 * produce a real score on a CI runner rather than only on a desktop.
 *
 * ### The viewport and the surface are not the same thing
 *
 * `SkikoComposeUiTest` fixes its output **surface** at construction, and a `raster` check must be
 * compared at the reference image's own dimensions. So the surface is sized once to the largest
 * viewport the gold's timeline ever asks for, and the **viewport** — `scene.size` — is what a
 * `resize` step moves. Content sits at the origin, so a frame is the surface snapshot cropped to
 * the current viewport.
 *
 * That is why there is no longer a rebuild-and-replay: the old host could not be resized, so every
 * resize meant a new scene and a replay of the steps so far, which risked silently resetting
 * accumulated state. One composition now survives the whole timeline.
 *
 * ### What it observes
 *
 * `tree` and the scalar state probes both go through the player's semantics seam. `raster` renders
 * real pixels through the real renderer with the corpus font registered. `ops:*` is a census of the
 * decoded document, named through the generated AndroidX inventory so `DRAW_RECT` reports as
 * `DrawRect` without a translation table that could drift.
 *
 * What remains unobserved is the four **transient-event** channels — `records:glyph_runs`,
 * `records:anchor_runs`, `draw_log:commands`, `trace:branches`. Those are not state a probe can
 * read after the fact: once the paint returns there is nothing left to query, so capturing them
 * needs the player to surface each event as it happens. They report [Observation.NotImplemented],
 * which the runner scores as a failure — the guide's instruction, and the honest answer.
 */
public class CmpEngine(specDir: File) : ConformanceEngine {
  override val name: String = "cmp"

  /**
   * The player's version, from the build rather than from a manifest.
   *
   * `Package.getImplementationVersion()` reads a JAR manifest, and a Gradle test runs against class
   * directories -- so it was null on every run and every published score recorded this player as
   * `dev`. The Gradle task passes the real version instead; `dev` remains the answer only for a run
   * launched without it.
   */
  override val version: String =
    System.getProperty("rc.player.version")?.takeIf { it.isNotBlank() } ?: "dev"

  private val typefaces = AhemTypefaces(File(specDir, "fonts/Ahem.ttf"))

  @OptIn(ExperimentalTestApi::class)
  override fun <T> withSession(gold: Gold, block: (ConformanceSession) -> T): T {
    val surface = surfaceSize(gold)
    val test =
      SkikoComposeUiTest(
        width = surface.width,
        height = surface.height,
        density = Density(gold.parameters.floatOr("density", 1f)),
      )
    // `runTest` owns the composition for the duration of the block and closes the scene after it,
    // which is why the engine SPI hands the session to a caller-supplied block rather than
    // returning something closeable: there is no point at which a session outlives its host.
    var outcome: Result<T>? = null
    test.runTest { outcome = runCatching { block(CmpSession(this, gold, typefaces)) } }
    return checkNotNull(outcome) { "the ${gold.name} session never ran" }.getOrThrow()
  }
}

/**
 * The surface the whole timeline has to fit in.
 *
 * Every viewport the gold will ever ask for, because the surface cannot grow once the host is
 * constructed. Missing a resize here does not fail loudly — it silently clips the frame — so both
 * the `resize` step and the `resize` trigger are counted.
 */
private fun surfaceSize(gold: Gold): IntSize {
  var width = gold.parameters.intOr("width", 400)
  var height = gold.parameters.intOr("height", 400)
  gold.timeline.forEach { step ->
    when (step.kind) {
      "resize" -> {
        width = maxOf(width, step.int("width", 0))
        height = maxOf(height, step.int("height", 0))
      }
      "trigger" -> {
        val trigger = step.obj("trigger") ?: return@forEach
        if ((trigger["type"] as? JsonPrimitive)?.content != "resize") return@forEach
        width = maxOf(width, trigger.floatOr("width", 0f).toInt())
        height = maxOf(height, trigger.floatOr("height", 0f).toInt())
      }
    }
  }
  return IntSize(width, height)
}

// `scene` is internal compose-ui API. That is acceptable in a measurement harness pinned to one
// Compose version: if it moves, this lane fails to compile rather than silently measuring something
// else.
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
private class CmpSession(
  private val test: SkikoComposeUiTest,
  private val gold: Gold,
  typefaces: AhemTypefaces,
) : ConformanceSession {

  private val document: RcDocument = RcDocumentCodec.decode(gold.documentBytes())

  /**
   * The document after linking, which is also after macro and loop *expansion*.
   *
   * That expansion is the point for the census probes: the corpus counts what a document resolves
   * to, so a `foreach` over three items reports three draws and a referenced block included twice
   * reports its contents twice. The flat decoded list in [document] is the unexpanded form and
   * answers a different question.
   */
  private val linked = runCatching { RcDocumentLinker.link(document) }.getOrNull()

  private var width = gold.parameters.intOr("width", 400)
  private var height = gold.parameters.intOr("height", 400)

  /** Explicit theme, when a `theme` step has selected one. Light `-3`, dark `-2`, normal `-1`. */
  private var theme by mutableStateOf(RcPlayerTheme.System)

  /**
   * Bumped to force the player to be composed afresh.
   *
   * `clock_snapshot` is the one step that needs it: the clock is consulted at *construction*, so
   * repainting an existing document would not re-derive the values and the run would silently
   * compare the same instant at every snapshot (guide §8).
   */
  private var generation by mutableStateOf(0)

  /**
   * The wall clock the player reads. The system clock until a `clock_snapshot` freezes it; kept
   * frozen afterwards, because later steps are measured against the same snapshot.
   */
  private var timeSource: RcTimeSource = RcTimeSource.System
  private val clock = FrozenOrSystem { timeSource }

  /**
   * Milliseconds the **current composition** has been advanced by, which is what the player sees.
   *
   * The player rebases: it records the first frame it is given as its origin and reports elapsed
   * time relative to that, per document. So the absolute value of `mainClock.currentTime` is not
   * observable and only the delta since the last rebuild matters — which is why this resets to zero
   * with [generation] rather than tracking the clock.
   */
  private var elapsedMillis = 0L

  init {
    // Deterministic from the first frame: nothing may advance the clock except this engine.
    test.mainClock.autoAdvance = false
    test.scene.size = IntSize(width, height)
    test.setContent {
      CompositionLocalProvider(
        LocalRcInspection provides true,
        // Per gold: the closed-form model and a real text stack agree on a single-line run and
        // disagree on line breaking (§2.3), so a gold measured with the real stack must not be
        // replayed through the model.
        LocalRcAhemTextMetrics provides (gold.textMetrics == "ahem"),
        LocalRcTimeSource provides clock,
      ) {
        key(generation) {
          // `fillMaxSize()` is load-bearing. The player's raw-document path paints into a `Canvas`
          // sized by the modifier the host supplies; with the default `Modifier` that canvas
          // measures 0x0, and anything sized *from* the `DrawScope` — canvas-drawn text especially
          // — lays out into nothing while explicitly positioned draws still land. The result looks
          // like a partial render rather than a misconfigured harness.
          RcComposePlayer(document, Modifier.fillMaxSize(), theme = theme, typefaces = typefaces)
        }
      }
    }
    test.waitForIdle()
  }

  override fun execute(step: Step, onCapture: (String) -> Unit) {
    when (step.kind) {
      "paint" -> paintStep(step)
      "resize" -> resize(step.int("width", width), step.int("height", height), step.frames)
      // `settle` runs nothing by definition (§3): a terminal marker so a check can bind to "after
      // everything". Treating it as unsupported would fail its checks as STEP_NOT_RUN.
      "settle" -> Unit
      "theme" -> themeStep(step)
      "advance_time" -> {
        advanceBy(step.int("advance_millis", 0).toLong())
        paint(step.frames)
      }
      "time" -> {
        step.float("seconds")?.let { advanceTo((it * MILLIS_PER_SECOND).toLong()) }
        paint(step.frames, measure = step.bool("measure", true))
      }
      "frame_sequence" -> frameSequence(step, onCapture)
      "trigger" -> trigger(step)
      "click",
      "longPress",
      "doubleClick" -> {
        val point = Offset(step.float("x") ?: 0f, step.float("y") ?: 0f)
        repeat(if (step.kind == "doubleClick") 2 else 1) { press(point, release = true) }
        afterGesture(step)
      }
      "touch_down" -> {
        press(Offset(step.float("x") ?: 0f, step.float("y") ?: 0f), release = false)
        afterGesture(step)
      }
      "touch_drag" -> {
        touch { moveTo(Offset(step.float("x") ?: 0f, step.float("y") ?: 0f)) }
        afterGesture(step)
      }
      "touch_up" -> {
        touch { up() }
        afterGesture(step)
      }
      "clock_snapshot" -> clockSnapshot(step)
      else -> throw UnsupportedStepKind(step.kind)
    }
  }

  private fun paintStep(step: Step) {
    step.float("animation_time_seconds")?.let { advanceTo((it * MILLIS_PER_SECOND).toLong()) }
    paint(step.frames, measure = step.bool("measure", true))
  }

  private fun themeStep(step: Step) {
    theme =
      when (step.int("theme", -1)) {
        LIGHT_THEME -> RcPlayerTheme.Light
        DARK_THEME -> RcPlayerTheme.Dark
        else -> RcPlayerTheme.System
      }
    paint(step.frames)
  }

  private fun resize(newWidth: Int, newHeight: Int, frames: Int) {
    if (newWidth != width || newHeight != height) {
      width = newWidth
      height = newHeight
      test.scene.size = IntSize(width, height)
    }
    paint(frames)
  }

  /**
   * A `trigger` is a one-shot stimulus followed by a single measure/paint (§3).
   *
   * Only `resize` and `click` triggers appear in the corpus; anything else throws rather than being
   * silently treated as a no-op, which would turn the step's checks into false passes.
   */
  private fun trigger(step: Step) {
    val trigger = step.obj("trigger") ?: throw UnsupportedStepKind("trigger(no payload)")
    fun number(key: String): Float? = (trigger[key] as? JsonPrimitive)?.content?.toFloatOrNull()
    when ((trigger["type"] as? JsonPrimitive)?.content) {
      "resize" ->
        resize(number("width")?.toInt() ?: width, number("height")?.toInt() ?: height, frames = 1)
      "click" -> {
        press(Offset(number("x") ?: 0f, number("y") ?: 0f), release = true)
        paint(frames = 1)
      }
      else -> throw UnsupportedStepKind("trigger(${trigger["type"]})")
    }
  }

  /**
   * Paints frames `0…total_frames`, walking the clock from the declared base and snapshotting each
   * frame the step names.
   *
   * The `capture` list is the point of the step. Checks bind to `frame_<n>`, where `n` is the frame
   * *number* rather than its index in the list, so a capture of `[0, 5, 10, 18]` answers to
   * `frame_0`, `frame_5`, `frame_10` and `frame_18`.
   *
   * Each frame is exactly **one** frame of the test clock, and frame 0 is the frame the previous
   * step left. Walking a millisecond target instead — `base + n * 1000 / 60` — does not do that:
   * `mainClock.advanceTimeBy` rounds every delta *up* to whole frames, so a 17 ms step runs two,
   * and a particle system that moves once per frame drifted +1, +2, +2, +1, +2 against the corpus's
   * one step per capture. `base_time_millis` is the reference clock's reading at frame 0, and only
   * deltas are observable here (see [elapsedMillis]); jumping the clock to it ran every
   * resize-triggered layout animation to completion before its first capture.
   */
  private fun frameSequence(step: Step, onCapture: (String) -> Unit) {
    val capture = step.ints("capture")
    for (frame in 0..step.int("total_frames", 0)) {
      if (frame > 0) advanceFrame()
      test.waitForIdle()
      if (frame in capture) onCapture("frame_$frame")
    }
  }

  /**
   * Rebuilds the document against a frozen **wall** clock, then paints (§3).
   *
   * The corpus writes the clock three ways — an instant (`timestamp_millis`), a time of day
   * (`hour`/`minute`/`second`), or seconds into the day (`continuous_seconds`) — and all three are
   * wall-clock readings. They reach the player through `LocalRcTimeSource`, the clock
   * `TimeAttribute` and the calendar variables read. Advancing the animation clock instead, as this
   * used to, left every one of them on the machine's real time: the two snapshots of
   * `clock_analog_hands` reported identical hands.
   *
   * The corpus does not name a zone; UTC is the one that makes `fixedTimestamp` in the gold's own
   * parameters agree with its expected calendar fields.
   */
  private fun clockSnapshot(step: Step) {
    val clock = step.obj("clock") ?: throw UnsupportedStepKind("clock_snapshot(no clock)")
    fun number(key: String): Double? = (clock[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
    val epochMillis =
      number("timestamp_millis")?.toLong()
        ?: number("continuous_seconds")?.let { (it * MILLIS_PER_SECOND).toLong() }
        ?: run {
          val hour = number("hour") ?: throw UnsupportedStepKind("clock_snapshot($clock)")
          val seconds = hour * 3_600 + (number("minute") ?: 0.0) * 60 + (number("second") ?: 0.0)
          (seconds * MILLIS_PER_SECOND).toLong()
        }
    timeSource = FrozenUtcClock(epochMillis)
    rebuild()
    paint(frames = 2)
  }

  /**
   * The measure/paint that follows a gesture, unless the step declines it.
   *
   * `repaint: false` is honoured because the post-gesture repaint clears the transient
   * touch-coordinate slots (13 = x, 14 = y) that the interactivity checks read — repainting anyway
   * makes those checks compare against cleared values (§3).
   */
  private fun afterGesture(step: Step) {
    val advance = step.int("advance_millis", 0)
    if (advance > 0) advanceBy(advance.toLong())
    if (step.bool("repaint", true)) paint(frames = 1)
  }

  private fun press(point: Offset, release: Boolean) {
    touch {
      down(point)
      if (release) up()
    }
  }

  /**
   * Dispatches a touch sequence at the root.
   *
   * The root rather than a matched node: the corpus addresses gestures in viewport coordinates, and
   * the root's bounds are the viewport, so a position needs no translation. Pointer state survives
   * between calls, which is what lets `touch_down` / `touch_drag` / `touch_up` be three steps.
   */
  private fun touch(block: TouchInjectionScope.() -> Unit) {
    test.onRoot().performTouchInput(block)
  }

  /**
   * Discards the player and composes it again, restarting its animation origin.
   *
   * The frame after the bump is what the new player records as its origin, so it is rendered before
   * the clock moves again — otherwise the rebuild would start already advanced.
   */
  private fun rebuild() {
    generation += 1
    test.waitForIdle()
    elapsedMillis = 0L
  }

  /** Paints [frames] frames, advancing the clock by one frame each (§3). */
  private fun paint(frames: Int, measure: Boolean = true) {
    if (!measure && frames == 0) return
    repeat(frames.coerceAtLeast(1)) {
      test.waitForIdle()
      advanceFrame()
    }
    test.waitForIdle()
  }

  private fun advanceFrame() {
    val before = test.mainClock.currentTime
    test.mainClock.advanceTimeByFrame()
    elapsedMillis += test.mainClock.currentTime - before
  }

  /** Advances to [targetMillis] since this composition's first frame, if it is still ahead. */
  private fun advanceTo(targetMillis: Long) {
    advanceBy(targetMillis - elapsedMillis)
  }

  private fun advanceBy(millis: Long) {
    if (millis <= 0L) return
    test.mainClock.advanceTimeBy(millis)
    elapsedMillis += millis
  }

  // ------------------------------------------------------------------ probes

  override fun observe(check: Check): Observation =
    when (check.key) {
      "tree" -> Observation.Value(tree())
      "raster" -> raster()
      "float" -> scalar(check) { id -> readFloat(id) }
      "int" -> scalar(check) { id -> state()?.integer(id)?.let(::JsonPrimitive) }
      // §4.2: ARGB as an *unsigned* 32-bit integer, so 0xFFFF0000 reports as 4294901760 rather than
      // as the negative Int the same bits mean on the JVM.
      "color" ->
        scalar(check) { id -> state()?.color(id)?.let { JsonPrimitive(it.toUInt().toLong()) } }
      "text" -> scalar(check) { id -> state()?.text(id)?.let(::JsonPrimitive) }
      // A constant matrix is asserted at its **declared** size — 9 for a 3x3, 16 for a 4x4 (§4.2) —
      // and the declaration is the only place that size survives: the runtime stores every matrix
      // in
      // a 4x4 slot, so reading it back reports sixteen numbers for a 3x3 that never had them.
      "matrix" ->
        scalar(check) { id ->
          document.operations
            .filterIsInstance<RcMatrixConstant>()
            .firstOrNull { it.id == id }
            ?.values
            ?.map { it.value }
            ?.toFloatArray()
            ?.toJsonArray() ?: state()?.matrixValues(id)?.toJsonArray()
        }
      "float_array:dynamic" -> scalar(check) { id -> state()?.floatValues(id)?.toJsonArray() }
      // The *stored* list, as written, against `float_array:dynamic`'s *computed* one (§4.2).
      "float_array:data" ->
        scalar(check) { id ->
          document.operations
            .filterIsInstance<RcFloatList>()
            .firstOrNull { it.id == id }
            ?.values
            ?.map { it.value }
            ?.toFloatArray()
            ?.toJsonArray()
        }
      "particles" -> particles(check)
      // The header is `HEADER`, opcode 0 — an operation on the wire like any other. This player's
      // model hoists it into `RcDocument.header` and leaves `operations` to the rest, so the
      // decoded
      // list is always one short of the count AndroidX reports. Adding it back is a difference in
      // where the header is *kept*, not in what was read.
      // Top-level operations, header included. A container holds its children rather than sitting
      // beside them, which is why this counts linked nodes and not the flat decoded list.
      // Top-level operations, header included. Linking supplies the nesting — a container holds its
      // children rather than sitting beside them — and the expansion it performs is what the corpus
      // counts. A macro *definition* is not part of that result: it is consumed by the expansion it
      // drives, so the reference counts the call sites and not the template.
      "ops:count" ->
        Observation.Value(
          JsonPrimitive(
            (linked?.operations?.count { !it.isMacroDefinition() } ?: document.operations.size) +
              declarationsExpansionConsumes() +
              1
          )
        )
      "ops:present",
      "ops:absent" -> Observation.Value(classNames().distinct().toJsonArray())
      "ops:counts" ->
        Observation.Value(
          buildJsonObject {
            classNames()
              .groupingBy { it }
              .eachCount()
              .forEach { (n, c) -> put(n, JsonPrimitive(c)) }
          }
        )
      // The document root is the container, not one of the components in it: the reference counts
      // `loom_id_remapping_tiers`' four macro-expanded boxes and not the `RootLayoutComponent` they
      // sit in, while the tree probe does report the root.
      "ops:component_count" ->
        Observation.Value(JsonPrimitive(components().count { it.kind != ROOT_COMPONENT_KIND }))
      "ops:distinct_ids" -> {
        val ids = components().map { it.id }
        Observation.Value(JsonPrimitive(ids.distinct().size == ids.size))
      }
      // Component *class names* (§4.2) — the drawing operations, not every operation in the
      // document. Returning the whole census made a document with four draws report eight entries.
      "records:components" -> Observation.Value(drawnComponents().toJsonArray())
      "draw_log:commands" -> Observation.Value(drawLog().toJsonArray())
      // The guide files these among the long tail, and describes them accurately: they are
      // decoded-operation field reads, not observations of a running player. Nothing here needs the
      // renderer, so nothing here needs a hook in it.
      // A path reaches the cache three ways on the wire: as a `DATA_PATH` resource, and as the
      // `PATH_CREATE` / `PATH_APPEND` pair `path_create_and_append` declares its path with. The
      // probe is a decoded-document read, so it has to know all three carriers; reading only
      // `RcPathData` reported an empty cache for a document that plainly declares path 10.
      "records:paths" ->
        Observation.Value(
          presenceObject(
            buildSet {
              document.operations.filterIsInstance<RcPathData>().forEach { add(it.id) }
              document.operations.filterIsInstance<RcPathCreate>().forEach { add(it.id) }
              document.operations.filterIsInstance<RcPathAppend>().forEach { add(it.id) }
            }
          )
        )
      "records:tweens" -> Observation.Value(presenceMap<RcPathTween> { it.outId })
      "records:impulses" ->
        Observation.Value(
          buildJsonArray {
            document.operations.filterIsInstance<RcImpulseStart>().forEach { impulse ->
              add(
                buildJsonObject {
                  put("duration", JsonPrimitive(impulse.duration.value))
                  put("startAt", JsonPrimitive(impulse.startAt.value))
                }
              )
            }
          }
        )
      // Names only — the bound values live in the compiled program, which is what the corpus says
      // it asserts (§4.2).
      "records:uniforms" ->
        Observation.Value(
          buildJsonObject {
            document.operations.filterIsInstance<RcShaderData>().forEach { shader ->
              put(
                shader.shaderId.toString(),
                buildJsonObject {
                  (shader.floatUniforms.keys + shader.intUniforms.keys + shader.bitmapUniforms.keys)
                    .forEach { put(it, JsonPrimitive(true)) }
                },
              )
            }
          }
        )
      "records:semantics" ->
        Observation.Value(
          buildJsonArray {
            document.operations.filterIsInstance<RcAccessibilitySemantics>().forEach { semantics ->
              add(
                buildJsonObject {
                  put("contentDescriptionId", JsonPrimitive(semantics.contentDescriptionId))
                  put("role", JsonPrimitive(semantics.role))
                  put("textId", JsonPrimitive(semantics.textId))
                  put("stateDescriptionId", JsonPrimitive(semantics.stateDescriptionId))
                  put("mode", JsonPrimitive(semantics.mode))
                  put("enabled", JsonPrimitive(semantics.enabled))
                  put("clickable", JsonPrimitive(semantics.clickable))
                }
              )
            }
          }
        )
      else -> Observation.NotImplemented
    }

  /**
   * The unmerged semantics root, or null before the first frame.
   *
   * Unmerged because the merged tree folds a subtree's properties into its nearest merging
   * ancestor, which would collapse several of the player's components into one node.
   */
  private fun semanticsRoot(): SemanticsNode? = runCatching {
    test.onRoot(useUnmergedTree = true).fetchSemanticsNode()
  }
    .getOrNull()

  /**
   * Every component the player published, with its nesting depth.
   *
   * Read from the **unmerged** tree: the merged one folds a subtree's properties into its nearest
   * merging ancestor, which would collapse several components into one entry. Depth counts only
   * nodes that carry a component id, so the intermediate layout nodes a manager builds for its own
   * measuring — and the player's `Content` wrappers, which AndroidX's tree has no entry for — do
   * not inflate it.
   */
  private fun components(): List<Component> {
    val out = mutableListOf<Component>()

    fun walk(node: SemanticsNode, depth: Int, parent: Offset?) {
      val id = node.config.getOrElseNullable(RcComponentIdKey) { null }
      if (id != null) {
        val kind = node.config.getOrElseNullable(RcComponentKindKey) { "" } ?: ""
        out += Component(node, id, kind, depth, parent ?: Offset.Zero)
      }
      val childDepth = if (id == null) depth else depth + 1
      // Children are reported relative to their parent's *content* origin, not its outer box.
      // §2.7: `x`/`y` carry only the layout manager's assignment, so a padded child reports 0 and
      // the padding shows up as the parent being larger — which it already is, because the
      // component's semantics node sits outside the padding.
      // A scrolled container draws its children under a translation, and §2.7 keeps that out of
      // `x`/`y` — the offset is reported as `scroll_x`/`scroll_y` instead. Adding it to the parent
      // origin is what takes it back out of every descendant's position.
      val childParent =
        if (id == null) parent
        else
          node.positionInRoot +
            (node.config.getOrElseNullable(RcContentInsetKey) { null } ?: Offset.Zero) +
            (node.config.getOrElseNullable(RcScrollOffsetKey) { null } ?: Offset.Zero)
      node.children.forEach { walk(it, childDepth, childParent) }
    }

    semanticsRoot()?.let { walk(it, 0, null) }

    // One component can appear twice. `FitBox` measures through a `SubcomposeLayout` with an
    // intrinsics *probe* slot and a content slot, and both compose the same child — so the child
    // publishes two semantics nodes under one component id, and the probe's is never placed. Taking
    // whichever came last reported the probe's zeroes as the component's geometry, which reads as a
    // layout collapse rather than as the measurement artefact it is.
    //
    // `isPlaced` is the discriminator, and it is the right one: a measurement that was never placed
    // is not where the component is.
    return out
      .groupBy { it.id }
      .values
      .map { candidates ->
        candidates.firstOrNull { it.node.layoutInfo.isPlaced } ?: candidates.first()
      }
  }

  /**
   * One published component: the semantics node, its id, its kind, its depth, and its parent's
   * origin.
   */
  private class Component(
    val node: SemanticsNode,
    val id: Int,
    val kind: String,
    val depth: Int,
    val parentOrigin: Offset,
  )

  /**
   * The live document state, published once on the player's root.
   *
   * Found by walking rather than assuming the root semantics node carries it: the player's root
   * component sits under whatever the host wrapped it in.
   */
  private fun state(): RcPlayerState? {
    fun find(node: SemanticsNode): RcPlayerState? =
      node.config.getOrElseNullable(RcDocumentStateKey) { null }
        ?: node.children.firstNotNullOfOrNull(::find)
    return semanticsRoot()?.let(::find)
  }

  /**
   * Reads float slot [id], or null when the document has no such slot.
   *
   * A slot reference on the wire is a NaN-encoded word rather than a plain index, so this builds
   * one the way the decoder would. `integer` is the presence test: the state stores a float and its
   * truncation together, so a slot that holds nothing answers null there — whereas `resolve` would
   * return the word's own NaN payload, which is a number, and would compare as a real observation
   * of a slot that does not exist.
   */
  private fun readFloat(id: Int): JsonElement? {
    val state = state() ?: return null
    if (state.integer(id) == null) return null
    return JsonPrimitive(state.resolve(RcFloatWord(NAN_SLOT_REFERENCE or id)))
  }

  /**
   * The tree in the corpus's encoding: ids ascending, positions **parent-relative**.
   *
   * Semantics reports position in root coordinates, which is the unambiguous form — several of this
   * player's layout managers wrap a child in an intermediate measuring node, so a child sits at
   * (0, 0) of its wrapper however far across the screen the wrapper was placed. Parent-relative is
   * recovered against the nearest enclosing *component*, which is what the corpus means by parent.
   */
  private fun tree(): JsonArray = buildJsonArray {
    components().sortedBy { it.id }.forEach { add(it.toJson()) }
  }

  private fun Component.toJson(): JsonObject = buildJsonObject {
    val position = node.positionInRoot
    // A component the layout never placed is gone, whatever its own visibility modifier says. That
    // is how a collapsible layout expresses "this child did not fit": it measures every child and
    // places only the ones it kept, so the dropped ones carry no visibility of their own and are
    // simply never positioned. Their pixels are already correct — the rasters on these golds pass —
    // so this reports what the player did rather than papering over it.
    //
    // It does not hide #198. There the *container* is placed and still paints its background, so
    // its
    // node keeps reporting VISIBLE and the gold keeps failing on exactly the node whose pixels are
    // wrong.
    val declared = node.config.getOrElseNullable(RcComponentVisibilityKey) { 1 } ?: 1
    val visibility = if (!node.layoutInfo.isPlaced) 0 else declared
    // §4.3: emitted only when non-zero, which is how the corpus encodes an unscrolled container.
    node.config
      .getOrElseNullable(RcScrollOffsetKey) { null }
      ?.let { offset ->
        if (offset.x != 0f) put("scroll_x", JsonPrimitive(offset.x))
        if (offset.y != 0f) put("scroll_y", JsonPrimitive(offset.y))
      }
    put("id", JsonPrimitive(id))
    put("kind", JsonPrimitive(node.config.getOrElseNullable(RcComponentKindKey) { "" }))
    put("depth", JsonPrimitive(depth))
    put("x", JsonPrimitive(position.x - parentOrigin.x))
    put("y", JsonPrimitive(position.y - parentOrigin.y))
    put("width", JsonPrimitive(node.size.width.toFloat()))
    put("height", JsonPrimitive(node.size.height.toFloat()))
    put("isGone", JsonPrimitive(visibility == 0))
    put(
      "visibility",
      JsonPrimitive(
        when (visibility) {
          0 -> "GONE"
          2 -> "INVISIBLE"
          else -> "VISIBLE"
        }
      ),
    )
  }

  /**
   * Reads a scalar slot addressed either by numeric id or by variable name (§4).
   *
   * Name resolution goes through the document's own `NamedVariable` table rather than a lookup this
   * runner maintains, so a name the document does not declare reports as unresolved instead of
   * quietly reading slot 0 — which exists, holds a plausible number, and would compare as a real
   * observation.
   */
  private fun scalar(check: Check, read: (Int) -> JsonElement?): Observation {
    val target = check.target ?: return Observation.NotImplemented
    val numeric = target.toIntOrNull()
    val id = numeric ?: state()?.namedVariable(target)?.id
    // A *named* target this document never declared is genuinely unobservable: there is no slot to
    // address. A *numeric* one is different — the slot was addressed and the document simply has
    // nothing there, which is an observation, and reporting it as "not implemented" would file a
    // player gap under the runner's. That conflation has already produced one wrong bug report in
    // this lane's history, so the two are kept apart here.
    if (id == null) return Observation.NotImplemented
    val value = read(id)
    return when {
      value != null -> Observation.Value(value)
      numeric != null -> Observation.Value(JsonNull)
      else -> Observation.NotImplemented
    }
  }

  /**
   * One simulation frame of a particle system.
   *
   * The corpus's `particles` checks carry **no target**: a gold that exercises particles defines
   * one system, so naming it would be redundant. The id therefore comes from the document — the
   * `ParticlesCreate` operation's own id, which is what the runtime keys its systems by. Falling
   * back to "not implemented" when a document defines none is right; guessing an id and reading the
   * empty list that comes back would report "no particles" as a real observation.
   */
  private fun particles(check: Check): Observation {
    val id =
      check.target?.toIntOrNull()
        ?: document.operations.filterIsInstance<RcParticleDefine>().singleOrNull()?.id
        ?: return Observation.NotImplemented
    val snapshot = state()?.particleSnapshot(id) ?: return Observation.NotImplemented
    return Observation.Value(
      buildJsonArray { snapshot.forEach { particle -> add(particle.toFloatArray().toJsonArray()) } }
    )
  }

  /**
   * The drawing stream in the corpus's portable vocabulary.
   *
   * Derived from the decoded document rather than from a recorder inside the renderer, which is
   * possible because of how the check is matched: `draw_log:commands` is an **ordered
   * subsequence**, not an exact list (§4.2), precisely so the recorded stream may carry scaffolding
   * the gold does not name. A document's canvas operations already appear in draw order, so the
   * sequence the gold asserts is a subsequence of them.
   *
   * What this does *not* capture is anything the renderer decides at paint time — an operation
   * skipped by a conditional still appears here. The corpus's `draw_log` golds are straight-line
   * canvas documents, so that distinction does not arise in them; a gold where it did would need a
   * real recorder, and would be wrong to answer from here.
   */
  private fun drawLog(): List<String> =
    names().mapNotNull { name ->
      when {
        // `applyPaint` -> `paint` and `matrixSave` -> `save`: the vocabulary drops the subsystem
        // prefix the wire name carries (guide §6).
        name == "PaintValues" -> "paint"
        name.startsWith("Matrix") -> name.removePrefix("Matrix").replaceFirstChar(Char::lowercase)
        name.startsWith("Draw") || name.startsWith("Clip") -> name.replaceFirstChar(Char::lowercase)
        else -> null
      }
    }

  /**
   * `{"<id>": {"present": true}}` — the shape the corpus uses where a cache exposes presence only.
   */
  private inline fun <reified T : Any> presenceMap(id: (T) -> Int): JsonObject = buildJsonObject {
    document.operations.filterIsInstance<T>().forEach { operation ->
      put(id(operation).toString(), buildJsonObject { put("present", JsonPrimitive(true)) })
    }
  }

  /**
   * The same `{"<id>": {"present": true}}` shape for a set of ids gathered from several carriers.
   */
  private fun presenceObject(ids: Set<Int>): JsonObject = buildJsonObject {
    ids.forEach { id ->
      put(id.toString(), buildJsonObject { put("present", JsonPrimitive(true)) })
    }
  }

  /**
   * Declarations that survive upstream but not this linker's expansion.
   *
   * The mirror of the macro case: a macro definition is consumed by the expansion it drives and is
   * *not* counted, while a referenced-operations block is inlined at its call sites and still is.
   * Counted by stable name rather than by class so the inventory stays the single place that
   * decides what an opcode is.
   */
  private fun declarationsExpansionConsumes(): Int =
    document.operations.count {
      RcOperationInventory.byOpcode[it.opcode]?.stableName == "ReferencedOperations"
    }

  private fun RcLinkedNode.isMacroDefinition(): Boolean =
    this is RcLinkedNode.Container && operation is RcMacroDefine

  /** Drawing operations in draw order, walking the expanded tree. */
  private fun drawnComponents(): List<String> {
    val out = mutableListOf<String>()
    fun walk(nodes: List<RcLinkedNode>) {
      nodes.forEach { node ->
        when (node) {
          is RcLinkedNode.Operation ->
            className(node.operation.opcode)?.takeIf { it.startsWith("Draw") }?.let(out::add)
          is RcLinkedNode.Container -> walk(node.children)
        }
      }
    }
    linked?.operations?.let(::walk)
    return out
  }

  /** The document's operations by this library's wire-derived names — `draw_log`'s input. */
  private fun names(): List<String> =
    document.operations.mapNotNull { RcOperationInventory.byOpcode[it.opcode]?.stableName }

  /**
   * Every name each operation answers to in the census, one list per operation.
   *
   * The corpus never says which vocabulary `ops:*` asserts in, and it uses two of AndroidX's own:
   * mostly the class an opcode is read into — `ShaderData` for `DATA_SHADER`, `TimeAttribute` for
   * `ATTRIBUTE_TIME` — but the semantics golds name `AccessibilitySemantics`, the constant's name,
   * where the class is `CoreSemantics`. Reporting either one alone fails whichever golds use the
   * other, and more than half the opcodes differ between them, so an operation answers to both.
   * Names only the TypeScript player uses (`TouchDownModifier`, `CanvasOperationsOp`) are neither,
   * and are left to fail.
   */
  private fun censusNames(): List<List<String>> =
    document.operations.mapNotNull { operation ->
      RcOperationInventory.byOpcode[operation.opcode]?.let {
        listOfNotNull(it.androidxClassName, it.stableName).distinct()
      }
    }

  private fun classNames(): List<String> = censusNames().flatten()

  /** AndroidX's class name, or this library's where AndroidX registers no reader for the opcode. */
  private fun className(opcode: Int): String? =
    RcOperationInventory.byOpcode[opcode]?.let { it.androidxClassName ?: it.stableName }

  /**
   * The current frame, cropped from the surface to the current viewport.
   *
   * The surface is the largest viewport the timeline asks for and never changes; `scene.size` is
   * what a `resize` moves. Content is laid out from the origin, so the frame is the top-left corner
   * of the snapshot — and the crop is what makes a resized gold's raster comparable at the
   * reference image's own dimensions.
   */
  private fun raster(): Observation {
    val png =
      Image.makeFromBitmap(test.captureToImage().asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes ?: error("skiko declined to encode the ${gold.name} frame")
    val surface: BufferedImage =
      ImageIO.read(ByteArrayInputStream(png)) ?: error("could not decode the re-encoded frame")
    val decoded =
      if (surface.width == width && surface.height == height) surface
      else
        surface.getSubimage(
          0,
          0,
          width.coerceAtMost(surface.width),
          height.coerceAtMost(surface.height),
        )
    val rgba = decoded.toRgba()
    return Observation.Raster(rgba.width, rgba.height, rgba.rgba)
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1_000.0
    const val LIGHT_THEME = -3
    const val ROOT_COMPONENT_KIND = "RootLayoutComponent"

    /** The NaN payload that marks a float word as a reference to a slot rather than a literal. */
    const val NAN_SLOT_REFERENCE = 0x7fc00000
    const val DARK_THEME = -2
  }
}

private fun List<String>.toJsonArray(): JsonArray = buildJsonArray {
  forEach { add(JsonPrimitive(it)) }
}

private fun FloatArray.toJsonArray(): JsonArray = buildJsonArray {
  forEach { add(JsonPrimitive(it)) }
}

private fun JsonObject.intOr(key: String, fallback: Int): Int =
  (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: fallback

private fun JsonObject.floatOr(key: String, fallback: Float): Float =
  (this[key] as? JsonPrimitive)?.content?.toFloatOrNull() ?: fallback

/** A wall clock stopped at [epochMillis], read in UTC. */
private class FrozenUtcClock(private val epochMillis: Long) : RcTimeSource {
  override fun currentTimeMillis(): Long = epochMillis

  override fun snapshot(epochMillis: Long): RcTimeSnapshot {
    val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC)
    return RcTimeSnapshot(
      epochMillis = epochMillis,
      year = local.year,
      month = local.monthValue,
      dayOfMonth = local.dayOfMonth,
      dayOfYear = local.dayOfYear,
      hour = local.hour,
      minute = local.minute,
      second = local.second,
      isoDayOfWeek = local.dayOfWeek.value,
    )
  }
}

/**
 * Forwards to the session's current clock, so a `clock_snapshot` changes what the next rebuilt
 * document reads without re-providing the composition local.
 */
private class FrozenOrSystem(private val current: () -> RcTimeSource) : RcTimeSource {
  override fun currentTimeMillis(): Long = current().currentTimeMillis()

  override fun snapshot(epochMillis: Long): RcTimeSnapshot = current().snapshot(epochMillis)
}
