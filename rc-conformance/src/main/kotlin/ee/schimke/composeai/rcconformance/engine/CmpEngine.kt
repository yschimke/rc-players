package ee.schimke.composeai.rcconformance.engine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
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
import ee.schimke.composeai.rcplayer.compose.RcComponentIdKey
import ee.schimke.composeai.rcplayer.compose.RcComponentKindKey
import ee.schimke.composeai.rcplayer.compose.RcComponentVisibilityKey
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcContentInsetKey
import ee.schimke.composeai.rcplayer.compose.RcDocumentStateKey
import ee.schimke.composeai.rcplayer.compose.RcPlayerTheme
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
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcPathTween
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLinkedNode
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.skia.EncodedImageFormat

/**
 * The supported CMP player — `rc-player-compose` — driven headless through `ImageComposeScene`.
 *
 * `ImageComposeScene` rasterizes through skiko's software path with no `DISPLAY`, the same property
 * `:rc-player-profile` relies on, which is what lets this lane produce a real score on a CI runner
 * rather than only on a desktop.
 *
 * ### What it observes
 *
 * `tree` and the scalar state probes both go through `RcPlayerInspector`, the observation seam on
 * the player. `raster` renders real pixels through the real renderer with the corpus font
 * registered. `ops:*` is a census of the decoded document, named through the generated AndroidX
 * inventory so `DRAW_RECT` reports as `DrawRect` without a translation table that could drift.
 *
 * What remains unobserved is the four **transient-event** channels — `records:glyph_runs`,
 * `records:anchor_runs`, `draw_log:commands`, `trace:branches`. Those are not state a probe can
 * read after the fact: once the paint returns there is nothing left to query, so capturing them
 * needs the player to surface each event as it happens. They report [Observation.NotImplemented],
 * which the runner scores as a failure — the guide's instruction, and the honest answer.
 */
public class CmpEngine(specDir: File) : ConformanceEngine {
  override val name: String = "cmp"

  override val version: String = CmpEngine::class.java.`package`?.implementationVersion ?: "dev"

  private val typefaces = AhemTypefaces(File(specDir, "fonts/Ahem.ttf"))

  override fun open(gold: Gold): ConformanceSession = CmpSession(gold, typefaces)
}

private class CmpSession(private val gold: Gold, private val typefaces: AhemTypefaces) :
  ConformanceSession {

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
  private val density = gold.parameters.floatOr("density", 1f)

  /** Explicit theme, when a `theme` step has selected one. Light `-3`, dark `-2`, normal `-1`. */
  private var theme = RcPlayerTheme.System

  /**
   * The steps run so far, replayed whenever the scene has to be rebuilt.
   *
   * `ImageComposeScene` fixes its output surface at construction, and a `raster` check must be
   * compared at the reference image's own dimensions — so a resize needs a new scene. Rebuilding
   * and replaying, rather than carrying the old scene forward, is what keeps that from silently
   * resetting accumulated state: a gold whose third step asserts the effect of its first two would
   * otherwise be scored against a document that had only ever seen the resize.
   */
  private val history = mutableListOf<Step>()

  private var scene: ImageComposeScene = newScene()

  /**
   * Animation time, in nanoseconds, which is what `ImageComposeScene.render` is driven by.
   *
   * Held here rather than read back from the scene because several step kinds move it in ways a
   * frame count cannot express — `advance_time` jumps it, `frame_sequence` walks it from a declared
   * base, and `time` sets it outright.
   */
  private var frameNanos = 0L

  private fun newScene(): ImageComposeScene =
    ImageComposeScene(width = width, height = height, density = Density(density)) {
      CompositionLocalProvider(
        LocalRcInspection provides true,
        // Per gold: the closed-form model and a real text stack agree on a single-line run and
        // disagree on line breaking (§2.3), so a gold measured with the real stack must not be
        // replayed through the model.
        LocalRcAhemTextMetrics provides (gold.textMetrics == "ahem"),
      ) {
        // `fillMaxSize()` is load-bearing. The player's raw-document path paints into a `Canvas`
        // sized by the modifier the host supplies; with the default `Modifier` that canvas measures
        // 0x0, and anything sized *from* the `DrawScope` — canvas-drawn text especially — lays out
        // into nothing while explicitly positioned draws still land. The result looks like a
        // partial
        // render rather than a misconfigured harness.
        RcComposePlayer(document, Modifier.fillMaxSize(), theme = theme, typefaces = typefaces)
      }
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
        frameNanos += step.int("advance_millis", 0).toLong() * NANOS_PER_MILLI
        paint(step.frames)
      }
      "time" -> {
        step.float("seconds")?.let { frameNanos = (it * NANOS_PER_SECOND).toLong() }
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
        scene.sendPointerEvent(
          PointerEventType.Move,
          Offset(step.float("x") ?: 0f, step.float("y") ?: 0f),
        )
        afterGesture(step)
      }
      "touch_up" -> {
        scene.sendPointerEvent(
          PointerEventType.Release,
          Offset(step.float("x") ?: 0f, step.float("y") ?: 0f),
        )
        afterGesture(step)
      }
      "clock_snapshot" -> clockSnapshot(step)
      else -> throw UnsupportedStepKind(step.kind)
    }
    history += step
  }

  private fun paintStep(step: Step) {
    step.float("animation_time_seconds")?.let { frameNanos = (it * NANOS_PER_SECOND).toLong() }
    paint(step.frames, measure = step.bool("measure", true))
  }

  private fun themeStep(step: Step) {
    theme =
      when (step.int("theme", -1)) {
        LIGHT_THEME -> RcPlayerTheme.Light
        DARK_THEME -> RcPlayerTheme.Dark
        else -> RcPlayerTheme.System
      }
    // The theme is a composition input, so it takes a rebuild rather than a repaint.
    rebuildAndReplay()
    paint(step.frames)
  }

  private fun resize(newWidth: Int, newHeight: Int, frames: Int) {
    if (newWidth != width || newHeight != height) {
      width = newWidth
      height = newHeight
      rebuildAndReplay()
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
   * Paints frames `0…total_frames`, walking wall-clock from the declared base and snapshotting each
   * frame the step names.
   *
   * The `capture` list is the point of the step. Checks bind to `frame_<n>`, where `n` is the frame
   * *number* rather than its index in the list, so a capture of `[0, 5, 10, 18]` answers to
   * `frame_0`, `frame_5`, `frame_10` and `frame_18`.
   */
  private fun frameSequence(step: Step, onCapture: (String) -> Unit) {
    val base = step.int("base_time_millis", 0).toLong() * NANOS_PER_MILLI
    val captures = step.ints("capture").toSet()
    for (frame in 0..step.int("total_frames", 0)) {
      frameNanos = base + frame.toLong() * FRAME_INTERVAL_NANOS
      scene.render(frameNanos)
      if (frame in captures) onCapture("frame_$frame")
    }
  }

  /**
   * Rebuilds the document against a frozen clock, then paints.
   *
   * The rebuild is not optional: the clock is consulted at *construction*, so repainting an
   * existing document would not re-derive the values and the run would silently compare the same
   * instant at every snapshot (guide §8).
   */
  private fun clockSnapshot(step: Step) {
    val seconds =
      (step.obj("clock")?.get("continuous_seconds") as? JsonPrimitive)?.content?.toDoubleOrNull()
        ?: 0.0
    rebuildAndReplay()
    frameNanos = (seconds * NANOS_PER_SECOND).toLong()
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
    if (advance > 0) frameNanos += advance.toLong() * NANOS_PER_MILLI
    if (step.bool("repaint", true)) paint(frames = 1)
  }

  private fun press(point: Offset, release: Boolean) {
    scene.sendPointerEvent(PointerEventType.Press, point)
    if (release) scene.sendPointerEvent(PointerEventType.Release, point)
  }

  private fun rebuildAndReplay() {
    scene.close()
    // A fresh inspector too: the old one's newest-pass bookkeeping belongs to a scene that no
    // longer
    // exists, and carrying it over would let a stale tree answer the next probe.
    scene = newScene()
    frameNanos = 0L
    history.forEach { earlier ->
      when (earlier.kind) {
        "paint",
        "resize" -> paint(earlier.frames)
        else -> Unit
      }
    }
  }

  /** Paints [frames] frames, advancing animation time by 1/60 s per frame (§3). */
  private fun paint(frames: Int, measure: Boolean = true) {
    if (!measure && frames == 0) return
    repeat(frames.coerceAtLeast(1)) {
      scene.render(frameNanos)
      frameNanos += FRAME_INTERVAL_NANOS
    }
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
      "ops:absent" -> Observation.Value(names().distinct().toJsonArray())
      "ops:counts" ->
        Observation.Value(
          buildJsonObject {
            names().groupingBy { it }.eachCount().forEach { (n, c) -> put(n, JsonPrimitive(c)) }
          }
        )
      "ops:component_count" -> Observation.Value(JsonPrimitive(components().size))
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
      "records:paths" -> Observation.Value(presenceMap<RcPathData> { it.id })
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
   * `semanticsOwners` is experimental on `ImageComposeScene`, which is fine for a measurement
   * harness: if it changes, this lane fails to compile rather than silently measuring the wrong
   * thing.
   */
  @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
  private fun semanticsRoot(): SemanticsNode? =
    scene.semanticsOwners.firstOrNull()?.unmergedRootSemanticsNode

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
        out += Component(node, id, depth, parent ?: Offset.Zero)
      }
      val childDepth = if (id == null) depth else depth + 1
      // Children are reported relative to their parent's *content* origin, not its outer box.
      // §2.7: `x`/`y` carry only the layout manager's assignment, so a padded child reports 0 and
      // the padding shows up as the parent being larger — which it already is, because the
      // component's semantics node sits outside the padding.
      val childParent =
        if (id == null) parent
        else
          node.positionInRoot +
            (node.config.getOrElseNullable(RcContentInsetKey) { null } ?: Offset.Zero)
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

  /** One published component: the semantics node, its id, its depth, and its parent's origin. */
  private class Component(
    val node: SemanticsNode,
    val id: Int,
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
            RcOperationInventory.byOpcode[node.operation.opcode]
              ?.stableName
              ?.takeIf { it.startsWith("Draw") }
              ?.let(out::add)
          is RcLinkedNode.Container -> walk(node.children)
        }
      }
    }
    linked?.operations?.let(::walk)
    return out
  }

  private fun names(): List<String> =
    document.operations.mapNotNull { RcOperationInventory.byOpcode[it.opcode]?.stableName }

  private fun raster(): Observation {
    val image = scene.render(frameNanos)
    val png =
      image.encodeToData(EncodedImageFormat.PNG)?.bytes
        ?: error("skiko declined to encode the ${gold.name} frame")
    val decoded: BufferedImage =
      ImageIO.read(ByteArrayInputStream(png)) ?: error("could not decode the re-encoded frame")
    val rgba = decoded.toRgba()
    return Observation.Raster(rgba.width, rgba.height, rgba.rgba)
  }

  override fun close() {
    scene.close()
  }

  private companion object {
    const val FRAME_INTERVAL_NANOS = 1_000_000_000L / 60
    const val NANOS_PER_MILLI = 1_000_000L
    const val NANOS_PER_SECOND = 1_000_000_000.0
    const val LIGHT_THEME = -3

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
