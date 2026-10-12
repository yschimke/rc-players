package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * A `CanvasContent` whose own width and height the document reads back through `ComponentValue`.
 *
 * The AndroidX `RemoteCanvas` writer emits a `LAYOUT_CANVAS_CONTENT` inside the canvas layout's
 * content and binds the gauge's `ComponentValue` WIDTH/HEIGHT to *that* component, not to the
 * `LayoutContent` around it as older writers did. Every coordinate of the pressure gauge — the
 * centre, the tick ring's radius, the needle and the labels — is an expression over those two
 * values. The player drew the canvas content without publishing its geometry, so both stayed 0: the
 * ring and needle collapsed to nothing and every label was drawn at the origin.
 */
class RcCanvasContentGeometryRenderTest {

  @Test
  fun canvasContentPublishesItsSizeToTheDocument() {
    val document = fixture()
    val canvasContentId =
      document.operations.filterIsInstance<RcCanvasContent>().single().componentId
    assertTrue(
      document.operations.filterIsInstance<RcComponentValue>().all {
        it.componentId == canvasContentId
      },
      "the fixture no longer binds its geometry to the canvas content",
    )

    val scene =
      ImageComposeScene(width = SIZE, height = SIZE, density = Density(1f)) {
        RcComposePlayer(document)
      }
    try {
      val bitmap = Bitmap().apply { allocN32Pixels(SIZE, SIZE) }
      assertTrue(scene.render(0L).readPixels(bitmap))
      // Collapsed at the origin, everything the gauge draws lands in the top-left corner and the
      // bottom half is a flat background fill. Laid out at 500x500 the tick ring and the Low/High
      // labels cover a good share of it.
      val bottomHalf =
        (0 until SIZE).flatMap { x -> (SIZE / 2 until SIZE).map { y -> bitmap.getColor(x, y) } }
      val background = bottomHalf.groupingBy { it }.eachCount().maxBy { it.value }.key
      val drawn = bottomHalf.count { it != background }
      assertTrue(
        drawn > bottomHalf.size / 50,
        "only $drawn of ${bottomHalf.size} bottom-half pixels drawn: canvas content size not published",
      )
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
    const val FIXTURE = "PressureGaugeCanvasContent-500x500.rc"
    const val SIZE = 500
  }
}
