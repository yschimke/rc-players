package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * One [RcPreparedDocument] shared by many players is how a feed draws many cards of one template.
 * Sharing must stay invisible: each player shows its own named values on its very first frame, and
 * nothing a player can write to is shared.
 */
class RcPreparedDocumentRenderTest {

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun playersSharingAPreparedDocumentEachDrawTheirOwnValuesOnTheFirstFrame() =
    runSkikoComposeUiTest(size = Size(60f, 40f), density = Density(1f)) {
      mainClock.autoAdvance = false
      val prepared = RcPreparedDocument(widthDrivenDocument())
      val narrow = mutableStateMapOf<String, RcNamedValue>(WIDTH to RcNamedValue.FloatValue(10f))
      val wide = mutableStateMapOf<String, RcNamedValue>(WIDTH to RcNamedValue.FloatValue(50f))
      setContent {
        Column {
          RcComposePlayer(prepared, Modifier.size(60.dp, 20.dp), namedValues = narrow)
          RcComposePlayer(prepared, Modifier.size(60.dp, 20.dp), namedValues = wide)
        }
      }
      mainClock.advanceTimeByFrame()
      waitForIdle()

      val first = onRoot().captureToImage().toPixelMap()
      assertEquals(RED, first[5, 10].toArgb())
      assertEquals(0, first[30, 10].toArgb(), "the first card drew the second card's value")
      assertEquals(RED, first[45, 30].toArgb(), "the second card did not draw its own value")
    }

  @Test
  fun aBitmapTheDocumentDrawsIntoIsFreshForEachPlayer() {
    val canvas = RcBitmapData(7, 4, 4, 0, RcBitmapData.ENCODING_EMPTY, ByteArray(0))
    val prepared = RcPreparedDocument(RcDocument(header(), listOf(canvas)))

    val first = prepared.inlineImagesForPlayer()
    val second = prepared.inlineImagesForPlayer()

    assertNotSame(first.getValue(7), second.getValue(7))
    assertSame(prepared.linked, prepared.linked)
  }

  /** One canvas whose drawn width is the named variable [WIDTH] (recorded as 20). */
  private fun widthDrivenDocument(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val operations = mutableListOf<RcOperation>()
    operations += RcFloatConstant(21, RcFloatWord.literal(20f))
    operations += RcNamedVariable(21, RcNamedVariable.FLOAT_TYPE, WIDTH)
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcCanvasLayout(5, 50)
    operations += RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f))
    operations += RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f))
    operations += RcNoArg(RcOpcodes.CANVAS_OPERATIONS)
    operations += RcPaintData(listOf(4, RED))
    operations +=
      RcDraw4(
        RcOpcodes.DRAW_RECT,
        RcFloatWord.literal(0f),
        RcFloatWord.literal(0f),
        RcFloatWord(0x7fc00015),
        RcFloatWord.literal(20f),
      )
    operations += end
    operations += end
    repeat(2) { operations += end }
    return RcDocument(header(), operations)
  }

  private fun header() =
    RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 20, modern = false)

  private companion object {
    const val WIDTH = "USER:width"
    val RED = 0xffff0000.toInt()
  }
}
