package ee.schimke.composeai.rcconformance.engine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcconformance.corpus.Check
import ee.schimke.composeai.rcconformance.corpus.Gold
import ee.schimke.composeai.rcconformance.corpus.Step
import ee.schimke.composeai.rcconformance.runner.ConformanceEngine
import ee.schimke.composeai.rcconformance.runner.ConformanceSession
import ee.schimke.composeai.rcconformance.runner.Observation
import ee.schimke.composeai.rcconformance.runner.UnsupportedStepKind
import ee.schimke.composeai.rcconformance.runner.toRgba
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcPlayerTheme
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcOperationInventory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.skia.EncodedImageFormat

/**
 * The supported CMP player — `rc-player-compose` — driven headless through `ImageComposeScene`.
 *
 * `ImageComposeScene` rasterizes through skiko's software path with no `DISPLAY`, which is the same
 * property `:rc-player-profile` relies on and what lets this lane produce a real score on a CI
 * runner rather than only on a desktop.
 *
 * ### What this lane can and cannot observe yet
 *
 * Two probe families work against the player exactly as it ships:
 *
 * * **`raster`** — the scene renders real pixels through the real renderer, with the corpus font
 *   registered ([AhemTypefaces]). 633 of the corpus's 1515 checks are rasters.
 * * **`ops:*` and `records:components`** — an operation census off the decoded document. The
 *   generated AndroidX inventory maps each opcode to the corpus's own class vocabulary, so
 *   `DRAW_RECT` is reported as `DrawRect` without a hand-written translation table that could
 *   drift.
 *
 * Everything else reports [Observation.NotImplemented], which the runner scores as a **failed**
 * check. That is the guide's instruction (§8) and it is the honest answer: the CMP player has no
 * public seam that exposes its laid-out component geometry or its float slots to a host, so this
 * lane genuinely cannot observe them today. `Modifier.trackComponentGeometry` publishes geometry
 * only for components the *document* binds a `ComponentValue` to, which is a small minority of
 * nodes and not the tree the `tree` probe asks for.
 *
 * Reporting them as unimplemented rather than skipping them is the whole point. A runner that
 * scores 100% by skipping what it cannot do has told you nothing; one that reports a partial score
 * with named gaps has told you something true, and the gaps are the work list.
 */
public class CmpEngine(private val specDir: File) : ConformanceEngine {
  override val name: String = "cmp"

  override val version: String = CmpEngine::class.java.`package`?.implementationVersion ?: "dev"

  private val typefaces = AhemTypefaces(File(specDir, "fonts/Ahem.ttf"))

  override fun open(gold: Gold): ConformanceSession = CmpSession(gold, typefaces)
}

private class CmpSession(private val gold: Gold, private val typefaces: AhemTypefaces) :
  ConformanceSession {

  private val document: RcDocument = RcDocumentCodec.decode(gold.documentBytes())

  /**
   * Viewport, seeded from the gold's authoring parameters.
   *
   * Falls back to the corpus's prevailing 400x400 rather than to the document header: a gold's
   * `parameters` are what the reference engine was driven at, and the header's own size is a
   * *declaration* the document may scale away from.
   */
  private var width = gold.parameters.intOr("width", 400)
  private var height = gold.parameters.intOr("height", 400)
  private val density = gold.parameters.floatOr("density", 1f)

  /**
   * The steps run so far, replayed whenever the viewport changes.
   *
   * `ImageComposeScene` fixes its output surface at construction, and a `raster` check must be
   * compared at the reference image's own dimensions — so a resize needs a new scene. Rebuilding
   * and replaying, rather than carrying the old scene forward, is what keeps that from silently
   * resetting accumulated state: a gold whose third step asserts the effect of its first two would
   * otherwise pass or fail against a document that had only ever seen the resize. It costs time,
   * which is the right thing to spend to keep a step's history intact.
   */
  private val history = mutableListOf<Step>()

  private var scene: ImageComposeScene = newScene()
  private var frameNanos = 0L

  private fun newScene(): ImageComposeScene =
    ImageComposeScene(width = width, height = height, density = Density(density)) {
      // `fillMaxSize()` is load-bearing. The player's raw-document path paints into a `Canvas`
      // sized by the modifier the host supplies; with the default `Modifier` that canvas measures
      // 0x0, and anything sized *from* the `DrawScope` — canvas-drawn text especially — lays out
      // into nothing while explicitly positioned draws still land. The result looks like a partial
      // render rather than a misconfigured harness.
      RcComposePlayer(
        document,
        Modifier.fillMaxSize(),
        theme = RcPlayerTheme.System,
        typefaces = typefaces,
      )
    }

  override fun execute(step: Step) {
    when (step.kind) {
      "paint" -> paint(step.frames)
      "resize" -> {
        val newWidth = step.int("width", width)
        val newHeight = step.int("height", height)
        if (newWidth != width || newHeight != height) {
          width = newWidth
          height = newHeight
          rebuildAndReplay()
        }
        paint(step.frames)
      }
      // `settle` runs nothing by definition (§3): it is a terminal marker so a check can bind to
      // "after everything". Treating it as unsupported would fail its checks as STEP_NOT_RUN.
      "settle" -> Unit
      else -> throw UnsupportedStepKind(step.kind)
    }
    history += step
  }

  private fun rebuildAndReplay() {
    scene.close()
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
  private fun paint(frames: Int) {
    repeat(frames) {
      scene.render(frameNanos)
      frameNanos += FRAME_INTERVAL_NANOS
    }
  }

  override fun observe(check: Check): Observation =
    when (check.key) {
      "raster" -> raster()
      "ops:count" -> Observation.Value(JsonPrimitive(document.operations.size))
      "ops:present",
      "ops:absent" -> Observation.Value(names().distinct().toJsonArray())
      "ops:counts" ->
        Observation.Value(
          buildJsonObject {
            names()
              .groupingBy { it }
              .eachCount()
              .forEach { (name, count) -> put(name, JsonPrimitive(count)) }
          }
        )
      "records:components" -> Observation.Value(names().toJsonArray())
      else -> Observation.NotImplemented
    }

  /**
   * The document's operations in the corpus's own class vocabulary.
   *
   * Taken from the generated AndroidX inventory rather than from this player's Kotlin class names:
   * the inventory is derived from `Operations.java`, so `DRAW_RECT` becomes `DrawRect` by the same
   * rule the corpus was authored under, and a renamed Kotlin type cannot silently change a score.
   */
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
  }
}

private fun List<String>.toJsonArray(): JsonArray = buildJsonArray {
  forEach { add(JsonPrimitive(it)) }
}

private fun kotlinx.serialization.json.JsonObject.intOr(key: String, fallback: Int): Int =
  (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: fallback

private fun kotlinx.serialization.json.JsonObject.floatOr(key: String, fallback: Float): Float =
  (this[key] as? JsonPrimitive)?.content?.toFloatOrNull() ?: fallback
