package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.MatrixRotate
import androidx.compose.remote.core.operations.MatrixScale
import androidx.compose.remote.core.operations.MatrixTranslate
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A scale or rotation about a pivot, as AndroidX's own reader (`remote-core` 1.0.0-alpha23) reads
 * it and hands it to its paint context: one operation with the pivot in it, no translation.
 */
class CanvasPivotReadBackTest {
  private val paint
    get() = RemotePaint { color = RemoteColor(Color.Red) }

  @Test
  fun aScaleCarriesItsPivot() {
    val bytes = captureCanvasBytes {
      scale(2f.rf, 3f.rf, RemoteOffset(40f.rf, 50f.rf)) { drawRect(paint) }
    }
    val operations = androidxOperations(bytes)
    val scale = operations.only<MatrixScale>()
    assertEquals(2f, scale.field<Float>("mX1"))
    assertEquals(3f, scale.field<Float>("mY1"))
    assertEquals(40f, scale.field<Float>("mX2"))
    assertEquals(50f, scale.field<Float>("mY2"))
    assertTrue(operations.all<MatrixTranslate>().isEmpty())
    val call = androidxPaints(bytes, 200, 100).single { it.name == "matrixScale" }
    assertEquals(listOf(2f, 3f, 40f, 50f), call.arguments)
  }

  @Test
  fun aRotationCarriesItsPivot() {
    val bytes = captureCanvasBytes {
      rotate(90f.rf, RemoteOffset(40f.rf, 50f.rf)) { drawRect(paint) }
    }
    val operations = androidxOperations(bytes)
    val rotate = operations.only<MatrixRotate>()
    assertEquals(90f, rotate.field<Float>("mV1"))
    assertEquals(40f, rotate.field<Float>("mV2"))
    assertEquals(50f, rotate.field<Float>("mV3"))
    assertTrue(operations.all<MatrixTranslate>().isEmpty())
    val call = androidxPaints(bytes, 200, 100).single { it.name == "matrixRotate" }
    assertEquals(listOf(90f, 40f, 50f), call.arguments)
  }

  /** Without a pivot the operation says so: AndroidX reads "none", not the origin. */
  @Test
  fun aScaleAndARotationAboutTheOriginHaveNoPivot() {
    val bytes = captureCanvasBytes { scale(2f.rf, 3f.rf) { rotate(90f.rf) { drawRect(paint) } } }
    val operations = androidxOperations(bytes)
    assertTrue(operations.only<MatrixScale>().field<Float>("mX2").isNaN())
    assertTrue(operations.only<MatrixRotate>().field<Float>("mV2").isNaN())
  }
}
