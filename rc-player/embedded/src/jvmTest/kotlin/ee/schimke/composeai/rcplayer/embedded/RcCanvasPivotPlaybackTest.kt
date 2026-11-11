package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A scale or rotation about a pivot is one operation carrying the pivot, and the player draws it
 * exactly as it draws the translate, operation, translate it used to be written as.
 */
class RcCanvasPivotPlaybackTest {
  private val red = RemotePaint { color = RemoteColor(Color.Red) }
  private val blue = RemotePaint { color = RemoteColor(Color.Blue) }

  /** A host value: the pivot and the factors are expressions, not constants folded at capture. */
  private val level
    get() = RemoteFloat.createNamedRemoteFloat("level", 1f)

  private fun RemoteDrawScope.shapes() {
    drawRect(red, RemoteOffset(30f.rf, 10f.rf), RemoteSize(20f.rf, 8f.rf))
    drawCircle(blue, 3f.rf, RemoteOffset(46f.rf, 14f.rf))
  }

  private val withPivots: RemoteDrawScope.() -> Unit = {
    val pivot = RemoteOffset(30f.rf * level, 10f.rf * level)
    rotate(90f.rf * level, pivot) { shapes() }
    scale(1.5f.rf * level, 2f.rf, RemoteOffset(60f.rf, 30f.rf * level)) { shapes() }
    withTransform(
      {
        translate(4f.rf, 2f.rf)
        rotate(30f.rf, 20f.rf, 40f.rf)
        scale(0.5f.rf, 0.5f.rf, 20f.rf, 40f.rf)
      },
    ) {
      shapes()
    }
  }

  /** The same picture as the port wrote it: the pivot as a translation either side. */
  private val withTranslations: RemoteDrawScope.() -> Unit = {
    val pivot = RemoteOffset(30f.rf * level, 10f.rf * level)
    withTransform(
      {
        translate(pivot.x, pivot.y)
        rotate(90f.rf * level)
        translate(-pivot.x, -pivot.y)
      },
    ) {
      shapes()
    }
    withTransform(
      {
        translate(60f.rf, 30f.rf * level)
        scale(1.5f.rf * level, 2f.rf)
        translate(-60f.rf, -(30f.rf * level))
      },
    ) {
      shapes()
    }
    withTransform(
      {
        translate(4f.rf, 2f.rf)
        translate(20f.rf, 40f.rf)
        rotate(30f.rf)
        translate(-20f.rf, -40f.rf)
        translate(20f.rf, 40f.rf)
        scale(0.5f.rf, 0.5f.rf)
        translate(-20f.rf, -40f.rf)
      },
    ) {
      shapes()
    }
  }

  private fun play(bytes: ByteArray): ImageBitmap = playDocument(bytes, WIDTH, HEIGHT)

  @Test
  fun aPivotIsDrawnAsTheTranslationsItReplaces() {
    val pivots = play(captureCanvas(WIDTH, HEIGHT, withPivots)).toPixelMap()
    val translations = play(captureCanvas(WIDTH, HEIGHT, withTranslations)).toPixelMap()
    var painted = 0
    for (y in 0 until HEIGHT) {
      for (x in 0 until WIDTH) {
        assertEquals(translations[x, y], pivots[x, y], "pixel ($x, $y)")
        if (pivots[x, y].alpha > 0f) painted++
      }
    }
    assertTrue(painted > 300, "the shapes are drawn: $painted pixels")
    // Where the rotation about (30, 10) by 90 degrees puts the rectangle: below the pivot.
    assertColor(Color.Red, pivots[26, 20], "the rotated rectangle")
    assertColor(Color.Transparent, pivots[40, 12], "where it would be without the rotation", 0f)
  }

  /** One operation each: no translation is written for a pivot. */
  @Test
  fun aPivotIsWrittenInItsOperation() {
    val operations = RcDocumentCodec.decode(captureCanvas(WIDTH, HEIGHT, withPivots)).operations
    assertEquals(2, operations.count { it.opcode == RcOpcodes.MATRIX_ROTATE })
    assertEquals(2, operations.count { it.opcode == RcOpcodes.MATRIX_SCALE })
    assertEquals(1, operations.count { it.opcode == RcOpcodes.MATRIX_TRANSLATE })
  }

  private companion object {
    const val WIDTH = 100
    const val HEIGHT = 60
  }
}
