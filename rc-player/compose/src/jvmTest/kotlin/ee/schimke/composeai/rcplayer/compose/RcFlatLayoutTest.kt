package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Flattened layout ([LocalRcFlattenLayout]) draws what the per-component path draws: every catalog
 * document it applies to, rendered both ways, is the same to the pixel; and the texts it draws
 * itself are still in the semantics tree.
 */
@OptIn(ExperimentalRcPlayerApi::class)
class RcFlatLayoutTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  @Test
  fun aFlattenedDocumentDrawsTheSameAsItsComponents() {
    var compared = 0
    val changed = mutableListOf<String>()
    for (file in corpus) {
      val prepared =
        runCatching { RcPreparedDocument.decode(file.readBytes()) }.getOrNull() ?: continue
      // Only a document that defers bounds animations is flattened.
      if (prepared.plainComponents.isEmpty() || prepared.changesLayoutOnItsOwn) continue
      val header = prepared.document.header
      if (header.width !in 1..1024 || header.height !in 1..1024) continue
      val components = render(prepared, flatten = false) ?: continue
      if (!components.contentEquals(render(prepared, flatten = false))) continue
      compared++
      if (!components.contentEquals(render(prepared, flatten = true))) changed += file.name
    }
    assertTrue(compared > 100, "only $compared corpus documents were compared")
    assertTrue(changed.isEmpty(), "${changed.size} of $compared drew differently: $changed")
  }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun aTextTheContainerDrawsIsStillInTheSemanticsTree() =
    runSkikoComposeUiTest(size = Size(454f, 200f), density = Density(DENSITY)) {
      val prepared = RcPreparedDocument.decode(corpus.single { it.name == SUBHEADER }.readBytes())
      assertTrue(prepared.plainComponents.isNotEmpty())
      setContent {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides true,
        ) {
          RcComposePlayer(prepared, systemColors = { null })
        }
      }
      waitForIdle()
      onNodeWithText("Subtitle").assertExists()
    }

  private fun render(prepared: RcPreparedDocument, flatten: Boolean): IntArray? {
    val width = prepared.document.header.width
    val height = prepared.document.header.height
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(DENSITY)) {
        CompositionLocalProvider(
          LocalRcDeferLayoutAnimations provides true,
          LocalRcFlattenLayout provides flatten,
        ) {
          RcComposePlayer(prepared, systemColors = { null })
        }
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
    const val SUBHEADER = "listsubheader__ideal__centred__compact.rc"
  }
}
