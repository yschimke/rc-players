package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcImageAttribute
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/** The published remote-m3 image-background button that must render in every browser player. */
class RcImageBackgroundRenderTest {

  @Test
  fun imageTextureAndShaderMatrixRenderThePublishedButton() {
    val document = fixture()
    assertTrue(document.operations.any { it is RcImageAttribute })
    assertTrue(document.operations.filterIsInstance<RcPaintData>().any { 24 in it.words })
    assertTrue(document.operations.filterIsInstance<RcPaintData>().any { 22 in it.words })
    val support = document.composeSupportReport()
    assertTrue(
      support.issues.none { it.operation == "PaintData" },
      "paint capability diverged: ${support.issues}",
    )

    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(2f)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      assertTrue(scene.render(0L).readPixels(bitmap))
      val centreLine = (0 until WIDTH).map { bitmap.getColor(it, HEIGHT / 2) }
      assertTrue(centreLine.toSet().size > 4, "the image texture collapsed to a flat fill")
    } finally {
      scene.close()
    }
  }

  private fun fixture(): RcDocument {
    val bytes =
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$FIXTURE")) {
          "missing fixture /rc-fixtures/$FIXTURE"
        }
        .use { it.readBytes() }
    return RcDocumentCodec.decode(bytes)
  }

  private companion object {
    const val FIXTURE = "ImageBackgroundRemoteButton-454x200.rc"
    const val WIDTH = 454
    const val HEIGHT = 200
  }
}
