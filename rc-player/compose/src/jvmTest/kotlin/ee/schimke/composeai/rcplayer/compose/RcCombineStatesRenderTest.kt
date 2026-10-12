package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.edit.combineAsStates
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * `rc-player-edit`'s `combineAsStates` held to what each document drew on its own: catalog pairs
 * combined, each branch rendered against its source, and a shared element seen mid-flight.
 */
class RcCombineStatesRenderTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }
      .map { RcDocumentCodec.decode(it.readBytes()) }

  /** Each branch draws exactly what its document drew alone: combining moved ids, not pixels. */
  @Test
  fun eachBranchOfACombinedCatalogPairDrawsItsOwnDocument() {
    val pairs =
      corpus
        .groupBy { it.header.width to it.header.height }
        .values
        .filter { it.size >= 2 }
        .flatMap { sameSize -> sameSize.zipWithNext().take(2) }
        .take(16)
    assertTrue(pairs.size >= 8, "too few same-size catalog pairs: ${pairs.size}")
    pairs.forEach { (first, second) ->
      val combined = first.combineAsStates(second).document
      val reread = RcDocumentCodec.decode(RcDocumentCodec.encode(combined))
      val pair = "${first.header.width}x${first.header.height} pair"
      assertTrue(render(first).contentEquals(render(reread, state = 0)), "$pair: first changed")
      assertTrue(render(second).contentEquals(render(reread, state = 1)), "$pair: second changed")
    }
  }

  /** The same square on both screens moves across between them, not cross-fading in place. */
  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun aSquareBothScreensShowMovesBetweenThem() =
    runSkikoComposeUiTest(size = Size(60f, 60f), density = Density(1f)) {
      mainClock.autoAdvance = false
      val combined =
        square(horizontal = 1, vertical = 4)
          .combineAsStates(square(horizontal = 3, vertical = 5), matches = listOf(-20 to -20))
      assertTrue(combined.sharedElements.size == 1)
      val values = mutableStateMapOf<String, RcNamedValue>("rcState" to RcNamedValue.Integer(0))
      setContent { RcComposePlayer(combined.document, namedValues = values) }
      mainClock.advanceTimeByFrame()
      waitForIdle()
      assertTrue(onRoot().captureToImage().toPixelMap()[10, 10].red > 0.5f, "top-left at first")

      values["rcState"] = RcNamedValue.Integer(1)
      mainClock.advanceTimeBy(150)
      waitForIdle()
      val halfway = onRoot().captureToImage().toPixelMap()
      assertTrue(halfway[30, 30].red > 0.05f, "mid-flight in the middle, was ${halfway[30, 30]}")
      assertTrue(halfway[5, 5].red < 0.05f, "it has left the corner it started in")

      mainClock.advanceTimeBy(1000)
      waitForIdle()
      val after = onRoot().captureToImage().toPixelMap()
      assertTrue(after[55, 55].red > 0.5f, "and lands bottom-right")
      assertTrue(after[10, 10].red < 0.05f, "with nothing left behind")
    }

  /** A 60x60 screen parking a 20px red square (component -20) where [horizontal]/[vertical] say. */
  private fun square(horizontal: Int, vertical: Int): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    fun size(value: Float): List<RcOperation> =
      listOf(
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(value)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(value)),
      )
    val operations =
      listOf(RcRootLayout(-2), RcLayoutContent(-3), RcBoxLayout(-4, -1, horizontal, vertical)) +
        size(60f) +
        listOf(RcLayoutContent(-5), RcCanvasLayout(-20, -1)) +
        size(20f) +
        listOf(
          RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
          RcPaintData(listOf(4, 0xffff0000.toInt())),
          RcDraw4(
            RcOpcodes.DRAW_RECT,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(20f),
            RcFloatWord.literal(20f),
          ),
        ) +
        List(6) { end }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 60, modern = false),
      operations,
    )
  }

  private fun render(document: RcDocument, state: Int? = null): IntArray {
    val width = document.header.width
    val height = document.header.height
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(2f)) {
        val values = mutableStateMapOf<String, RcNamedValue>()
        if (state != null) values["rcState"] = RcNamedValue.Integer(state)
        RcComposePlayer(document, namedValues = values, systemColors = { null })
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
