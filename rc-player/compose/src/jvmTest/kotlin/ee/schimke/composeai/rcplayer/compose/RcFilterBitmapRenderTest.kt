package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * A card whose container painter is an image. Its paint bundle is the image-background button's
 * plus one entry: `FILTER_BITMAP` (17), emitted between `BLEND_MODE` and the `TEXTURE` that carries
 * the bitmap. The player rejected command 17 — `paintIssue` reported the whole `PaintData`
 * unsupported and `applyPaint` threw on it — so a card that names a background image was
 * unrenderable.
 *
 * The catalog's image is a solid `#ECECEC` 8x8, so "did the texture draw?" cannot be asked as "is
 * the fill varied?" — flat is the correct answer here. What it can be asked as: the container is
 * the texture under the document's own 50% black scrim, and *not* the `Color(0xff000000)` the paint
 * carries as its base before the texture replaces it.
 */
class RcFilterBitmapRenderTest {

  @Test
  fun filterBitmapAheadOfTheTextureDoesNotCostTheCardItsImage() {
    val document = fixture()
    val paints = document.operations.filterIsInstance<RcPaintData>()
    val textured =
      paints.singleOrNull { paint -> paint.words.any { it and 0xffff == TEXTURE } }
        ?: error("the fixture no longer has exactly one textured paint")
    assertTrue(
      textured.words.indexOfFirst { it and 0xffff == FILTER_BITMAP } in
        0 until textured.words.indexOfFirst { it and 0xffff == TEXTURE },
      "the fixture no longer emits FILTER_BITMAP ahead of TEXTURE; it cannot pin this regression",
    )

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
      // Low on the card, below the last line of text and inside the rounded clip.
      val container = bitmap.getColor(WIDTH / 2, HEIGHT - 24)
      val red = (container shr 16) and 0xff
      val alpha = (container ushr 24) and 0xff
      assertTrue(alpha == 0xff, "the container is not opaque: ${container.toUInt().toString(16)}")
      assertTrue(
        red in SCRIMMED_SOURCE_RED,
        "the container is ${container.toUInt().toString(16)}, not the #ececec texture under the " +
          "document's 50% scrim — the texture did not reach the paint",
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
    const val FIXTURE = "TitleCardBackgroundImage-454x400.rc"
    const val WIDTH = 454
    const val HEIGHT = 400
    const val FILTER_BITMAP = 17
    const val TEXTURE = 24
    /** `#ececec` (236) halved by the scrim is 118; the window allows for rounding and blending. */
    val SCRIMMED_SOURCE_RED = 108..128
  }
}
