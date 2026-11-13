package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.edit.forDisplay
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * `rc-player-edit`'s `forDisplay` — density hardcoded, unbound values frozen, constants folded —
 * held to what the player drew before it: every catalog document whose own render is deterministic,
 * rendered as decoded and as rewritten for the display it is drawn on.
 */
class RcForDisplayRenderTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  @Test
  fun aDocumentRewrittenForItsDisplayDrawsTheSame() {
    var compared = 0
    val changed = mutableListOf<String>()
    for (file in corpus) {
      val original =
        runCatching { RcDocumentCodec.decode(file.readBytes()) }.getOrNull() ?: continue
      if (original.header.width !in 1..1024 || original.header.height !in 1..1024) continue
      val rewritten = runCatching { original.forDisplay(DENSITY, 1f) }.getOrNull() ?: continue
      val before = render(original) ?: continue
      // A document that reads the clock or runs an animation draws differently twice over.
      if (!before.contentEquals(render(original))) continue
      compared++
      if (!before.contentEquals(render(rewritten))) changed += file.name
    }
    assertTrue(compared > 100, "only $compared corpus documents were compared")
    assertTrue(changed.isEmpty(), "${changed.size} of $compared drew differently: $changed")
  }

  private fun render(document: RcDocument): IntArray? {
    val width = document.header.width
    val height = document.header.height
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(DENSITY)) {
        RcComposePlayer(RcPreparedDocument(document), systemColors = { null })
      }
    return try {
      val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
      if (!scene.render(0L).readPixels(bitmap)) return null
      IntArray(width * height) { bitmap.getColor(it % width, it / width) }
    } catch (_: Exception) {
      null
    } finally {
      scene.close()
    }
  }

  private companion object {
    const val DENSITY = 2f
  }
}
