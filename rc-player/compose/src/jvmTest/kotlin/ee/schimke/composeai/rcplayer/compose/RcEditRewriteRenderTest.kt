package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.edit.colorThemesAsFallbacks
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * `rc-player-edit`'s ColorTheme fallback held to what a palette-less host drew: every catalog
 * document it touches, rendered before and after.
 */
class RcEditRewriteRenderTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  /** On a host with no system palette, a `ColorTheme` already draws its fallbacks: so must they. */
  @Test
  fun colorThemeFallbacksDrawWhatAPaletteLessHostDrew() {
    val documents = corpus.map(::decode).filter { doc -> doc.operations.any { it is RcColorTheme } }
    assertTrue(documents.isNotEmpty(), "no corpus document uses ColorTheme")
    documents.forEach { original ->
      val fallback = original.colorThemesAsFallbacks()
      assertTrue(fallback.operations.none { it is RcColorTheme })
      for (theme in listOf(RcPlayerTheme.Light, RcPlayerTheme.Dark)) {
        assertTrue(
          render(original, theme).contentEquals(render(fallback, theme)),
          "$theme rendering changed",
        )
      }
    }
  }

  @Test
  fun theFallbackSwatchesFollowTheTheme() {
    val swatches =
      corpus
        .map(::decode)
        .first { doc -> doc.operations.any { it is RcColorTheme } }
        .colorThemesAsFallbacks()
    val light = render(swatches, RcPlayerTheme.Light)
    val dark = render(swatches, RcPlayerTheme.Dark)
    assertEquals(light.size, dark.size)
    assertTrue(!light.contentEquals(dark), "the fallback did not follow the theme")
  }

  private fun decode(file: File): RcDocument = RcDocumentCodec.decode(file.readBytes())

  private fun render(document: RcDocument, theme: RcPlayerTheme): IntArray {
    val width = document.header.width
    val height = document.header.height
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(2f)) {
        RcComposePlayer(document, theme = theme, systemColors = { null })
      }
    return try {
      val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
      assertTrue(scene.render(0L).readPixels(bitmap))
      IntArray(width * height) { bitmap.getColor(it % width, it / width) }
    } finally {
      scene.close()
    }
  }
}
