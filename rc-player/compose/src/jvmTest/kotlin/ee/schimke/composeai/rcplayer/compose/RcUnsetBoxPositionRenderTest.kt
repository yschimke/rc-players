package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * `render/material-toggle-button` from `yschimke/RemoteCompose-Examples`: an rcJson-authored
 * document with a box that writes 0 for both positions. AndroidX's `BoxLayout` switches send any
 * out-of-table position to their default, start/top, so the AndroidX players draw it; the support
 * check reported "horizontal position 0 is not implemented" and the Wasm lane refused the whole
 * document over that one box. Here it has to pass the check and draw its purple toggle button.
 */
class RcUnsetBoxPositionRenderTest {

  @Test
  fun materialToggleButtonIsRenderableAndDrawsItsButton() {
    val document = fixture()
    assertTrue(
      document.operations.filterIsInstance<RcBoxLayout>().any {
        it.horizontalPositioning == 0 && it.verticalPositioning == 0
      },
      "the fixture no longer carries an unset box position",
    )
    assertEquals(
      emptyList(),
      document.composeSupportReport().issues.map { "${it.operation}: ${it.detail}" },
    )

    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
      assertTrue(scene.render(0L).readPixels(bitmap))
      val pixels =
        (0 until WIDTH).flatMap { x -> (0 until HEIGHT).map { y -> bitmap.getColor(x, y) } }
      val background = pixels.groupingBy { it }.eachCount().maxBy { it.value }.key
      assertEquals(BACKGROUND, background and 0xFFFFFF, "page background")
      // The 200x56 #6200EE button, less its rounded ends and label.
      val purple = pixels.count { it and 0xFFFFFF == BUTTON }
      assertTrue(purple > 200 * 56 / 2, "only $purple #6200EE pixels: the toggle button is missing")
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
    const val FIXTURE = "MaterialToggleButton-400x450.rc"
    const val WIDTH = 400
    const val HEIGHT = 450
    const val BACKGROUND = 0x121212
    const val BUTTON = 0x6200EE
  }
}
