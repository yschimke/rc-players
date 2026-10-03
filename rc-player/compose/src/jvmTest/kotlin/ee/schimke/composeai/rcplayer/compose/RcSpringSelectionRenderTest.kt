package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * The `remote-m3` split selection rows, from the exact bytes the published catalog ships, and the
 * frames a spring-animated document asks for.
 *
 * Each row's selection progress is `remoteSpring` over a constant `checked = 1`, and the colours of
 * the whole row — container, split segment, the control — interpolate on it. The AndroidX players
 * start such a spring settled at its first target, so the published capture is the checked row.
 * This player started it at 0 and then integrated it with too few sub-steps, so the row drew
 * unchecked, or at a diverged progress that came out greyed.
 *
 * Expected colours are the published capture's (the AndroidX embedded player), sampled away from
 * any text or edge so the check is about the progress and not about anti-aliasing.
 */
class RcSpringSelectionRenderTest {

  @Test fun splitSwitchRowDrawsChecked() = assertChecked("SwitchRowSplit-454x200.rc")

  @Test fun splitCheckboxRowDrawsChecked() = assertChecked("CheckboxRowSplit-454x200.rc")

  @Test fun splitRadioRowDrawsChecked() = assertChecked("RadioRowSplit-454x200.rc")

  /**
   * A document that declares a spring but is not moving asks for no frames, so a host — a Compose
   * test waiting for idle, a preview daemon capturing a still — sees it settle.
   */
  @Test
  fun aSettledSpringDocumentStopsRequestingFrames() {
    val document = fixture("TextRemoteButton-454x200.rc")
    assertTrue(
      document.operations.filterIsInstance<RcFloatExpression>().any { it.animation != null },
      "the fixture no longer declares an animated float, so it no longer covers the idle check",
    )
    val scene = ImageComposeScene(WIDTH, HEIGHT, Density(2f)) { RcComposePlayer(document) }
    try {
      var time = 0L
      repeat(10) {
        scene.render(time)
        time += FRAME_NANOS
      }
      assertFalse(scene.hasInvalidations(), "a static document is still scheduling frames")
    } finally {
      scene.close()
    }
  }

  private fun assertChecked(name: String) {
    val document = fixture(name)
    val scene = ImageComposeScene(WIDTH, HEIGHT, Density(2f)) { RcComposePlayer(document) }
    try {
      // The first frame, and one well after any animation could have finished: a spring that
      // started anywhere but its target fails the first, one that diverged fails the second.
      for (time in listOf(0L, 1_000_000_000L)) {
        val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
        check(scene.render(time).readPixels(bitmap))
        assertColor(bitmap, 150, 60, 0xFF4D3D76.toInt(), "$name main container at t=$time")
        assertColor(bitmap, 300, 60, 0xFF615286.toInt(), "$name split segment at t=$time")
      }
    } finally {
      scene.close()
    }
  }

  private fun assertColor(bitmap: Bitmap, x: Int, y: Int, expected: Int, what: String) {
    val actual = bitmap.getColor(x, y)
    val close =
      (0..24 step 8).all { shift ->
        abs((actual ushr shift and 0xff) - (expected ushr shift and 0xff)) <= TOLERANCE
      }
    assertTrue(
      close,
      "$what: expected #${expected.toUInt().toString(16)}, was #${actual.toUInt().toString(16)}",
    )
  }

  private fun fixture(name: String): RcDocument =
    RcDocumentCodec.decode(
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$name")) { "missing $name" }
        .use { it.readBytes() }
    )

  private companion object {
    const val WIDTH = 454
    const val HEIGHT = 200
    const val TOLERANCE = 6
    const val FRAME_NANOS = 16_000_000L
  }
}
