package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Per-value invalidation ([RcValueVersions]) held to the whole-player invalidation it replaces:
 * every catalog document that renders deterministically and declares named values, its values
 * changed while it plays, draws exactly what a fresh player given the new values draws. A read the
 * player did not observe would leave a component showing the old value. Documents that already
 * differ with per-value invalidation off (the whole player invalidated, as before) are not this
 * test's: they are counted, and must stay few.
 */
class RcPerValueInvalidationCorpusTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  @AfterTest
  fun restore() {
    RcValueVersions.enabled = true
  }

  @Test
  fun aDocumentWhoseValuesChangeWhileItPlaysDrawsWhatAFreshPlayerDraws() {
    var compared = 0
    var perValue = 0
    val changed = mutableListOf<String>()
    val alreadyDifferent = mutableListOf<String>()
    for (file in corpus) {
      val document =
        runCatching { RcDocumentCodec.decode(file.readBytes()) }.getOrNull() ?: continue
      if (document.header.width !in 1..1024 || document.header.height !in 1..1024) continue
      val values = newValues(document)
      if (values.isEmpty()) continue
      val fresh = runCatching { fresh(document, values) }.getOrNull() ?: continue
      // A document that reads the clock or runs an animation draws differently twice over.
      if (!fresh.contentEquals(runCatching { fresh(document, values) }.getOrNull())) continue
      compared++
      if (RcPreparedDocument(document).let { it.layout != null && !it.settlesFirstLayout })
        perValue++
      val updated = runCatching { updated(document, values) }.getOrNull()
      if (fresh.contentEquals(updated)) continue
      RcValueVersions.enabled = false
      val whole = runCatching { updated(document, values) }.getOrNull()
      RcValueVersions.enabled = true
      if (fresh.contentEquals(whole)) changed += file.name else alreadyDifferent += file.name
    }
    println(
      "per-value invalidation: $compared compared, $perValue per value, ${changed.size} changed, ${alreadyDifferent.size} already different: $alreadyDifferent",
    )
    assertTrue(compared > 50, "only $compared corpus documents were compared")
    assertTrue(perValue > 0, "no compared document plays per value")
    assertTrue(
      changed.isEmpty(),
      "${changed.size} of $compared drew differently per value than whole: $changed",
    )
  }

  /** A value of the declared type that differs from any document's own default. */
  private fun newValues(document: RcDocument): Map<String, RcNamedValue> =
    document.operations
      .filterIsInstance<RcNamedVariable>()
      .mapNotNull { variable ->
        val value =
          when (variable.type) {
            RcNamedVariable.STRING_TYPE -> RcNamedValue.Text("Changed 42 ✓")
            RcNamedVariable.FLOAT_TYPE -> RcNamedValue.FloatValue(0.37f)
            RcNamedVariable.INT_TYPE -> RcNamedValue.Integer(3)
            RcNamedVariable.COLOR_TYPE -> RcNamedValue.Color(0xFF2196F3.toInt())
            else -> null
          } ?: return@mapNotNull null
        variable.name to value
      }
      .toMap()

  /** [document] given [values] from its first frame. */
  private fun fresh(document: RcDocument, values: Map<String, RcNamedValue>): IntArray? =
    play(document) { named ->
      named.putAll(values);
      {}
    }

  /** [document] played with its own values, then given [values] once it has drawn. */
  private fun updated(document: RcDocument, values: Map<String, RcNamedValue>): IntArray? =
    play(document) { named -> { named.putAll(values) } }

  /**
   * Renders [document] for a while: [setUp] seeds the named-value map and returns what to do to it
   * after the first frames. Layout animations are off, so a change lands at once and both players
   * settle on the same frame.
   */
  private fun play(
    document: RcDocument,
    setUp: (MutableMap<String, RcNamedValue>) -> () -> Unit,
  ): IntArray? {
    val width = document.header.width
    val height = document.header.height
    val named = mutableStateMapOf<String, RcNamedValue>()
    val later = setUp(named)
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(DENSITY)) {
        CompositionLocalProvider(LocalRcLayoutAnimations provides false) {
          RcComposePlayer(
            RcPreparedDocument(document),
            namedValues = named,
            systemColors = { null },
          )
        }
      }
    return try {
      var time = 0L
      repeat(3) {
        scene.render(time).close()
        time += FRAME_NANOS
      }
      later()
      Snapshot.sendApplyNotifications()
      repeat(SETTLE_FRAMES) {
        scene.render(time).close()
        time += FRAME_NANOS
      }
      val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
      if (!scene.render(time).readPixels(bitmap)) return null
      IntArray(width * height) { bitmap.getColor(it % width, it / width) }
    } finally {
      scene.close()
    }
  }

  private companion object {
    const val DENSITY = 2f
    const val FRAME_NANOS = 16_000_000L
    const val SETTLE_FRAMES = 12
  }
}
