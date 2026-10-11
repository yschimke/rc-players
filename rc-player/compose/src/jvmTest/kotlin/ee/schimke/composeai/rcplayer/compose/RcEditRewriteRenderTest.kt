package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.edit.colorThemesAsFallbacks
import ee.schimke.composeai.rcplayer.edit.lowerMatrixExpressions
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcMatrixExpression
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * `rc-player-edit`'s rewrites that claim to draw what the original drew, held to it: every catalog
 * document they touch, rendered before and after.
 */
class RcEditRewriteRenderTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  /**
   * A lowered shader matrix draws the same image in the same shape. The only pixels allowed to
   * differ are partly covered ones on a curved edge, where a curve rasterized under a scaled canvas
   * can land a fraction of a pixel's coverage differently.
   */
  @Test
  fun aLoweredShaderMatrixDrawsTheSameImage() {
    val documents =
      corpus.map(::decode).filter { doc -> doc.operations.any { it is RcMatrixExpression } }
    assertTrue(documents.isNotEmpty(), "no corpus document uses a matrix")
    documents.forEach { original ->
      val lowered = original.lowerMatrixExpressions()
      assertTrue(lowered.operations.none { it is RcMatrixExpression })
      val before = render(original, RcPlayerTheme.Light)
      val after = render(lowered, RcPlayerTheme.Light)
      val differing = before.indices.filter { before[it] != after[it] }
      assertTrue(differing.size <= before.size / 1000, "${differing.size} pixels differ")
      differing.forEach {
        assertTrue(
          before[it] ushr 24 != 0xff && after[it] ushr 24 != 0xff,
          "a fully covered pixel changed: ${before[it].toUInt().toString(16)} -> " +
            after[it].toUInt().toString(16),
        )
      }
    }
  }

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
