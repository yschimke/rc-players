package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthInModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/**
 * A document that constrains a child by a parent's measured `ComponentValue` lays out settled on
 * its very first frame, the way AndroidX does.
 *
 * The shape is `remote-m3`'s edge button, reduced: the button publishes its own width and caps its
 * label's with `widthIn(max = <that width>)`. Before the first layout that width is 0, and a player
 * that composes against it lays the label out with no room — then, a frame later, animates it open
 * over the default bounds spec. See [RcSettledLayout].
 */
class RcFirstFrameSettleTest {

  @Test
  fun aWidthCappedByAComponentValueIsSettledOnTheFirstFrame() {
    val scene =
      ImageComposeScene(width = 80, height = 40, density = Density(1f)) {
        RcComposePlayer(cappedByParentWidth())
      }
    try {
      // 40 wide (the parent's published width, not the 60 it asks for) by 20 tall, on frame 0 and
      // unchanged afterwards: nothing is laid out at the unsettled width, so nothing animates
      // from it.
      assertEquals(40 * 20, scene.render(0L).greenPixels())
      assertEquals(40 * 20, scene.render(FRAME_NANOS).greenPixels())
      assertEquals(40 * 20, scene.render(10 * FRAME_NANOS).greenPixels())
    } finally {
      scene.close()
    }
  }

  /**
   * The settled tree is subcomposed, and a `SubcomposeLayout` throws when asked for intrinsics. The
   * player answered them before; it must not start crashing a host that asks.
   */
  @Test
  fun aHostMeasuringIntrinsicsDoesNotCrash() {
    val scene =
      ImageComposeScene(width = 80, height = 40, density = Density(1f)) {
        Column(Modifier.width(IntrinsicSize.Max)) { RcComposePlayer(cappedByParentWidth()) }
      }
    try {
      scene.render(0L)
      scene.render(FRAME_NANOS)
    } finally {
      scene.close()
    }
  }

  private fun org.jetbrains.skia.Image.greenPixels(): Int {
    val image = this
    val bitmap = Bitmap().apply { allocN32Pixels(image.width, image.height) }
    check(image.readPixels(bitmap))
    var count = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) if (bitmap.getColor(x, y) == GREEN) count++
    }
    return count
  }

  private fun cappedByParentWidth(): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 80, legacyHeight = 40, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(3, -1, 1, 4),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(30f)),
        RcLayoutContent(4),
        RcComponentValue(RcComponentValue.WIDTH, componentId = 4, valueId = PARENT_WIDTH),
        RcBoxLayout(5, -1, 1, 4),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcWidthInModifier(RcFloatWord.literal(-1f), RcFloatWord(0x7fc00000 or PARENT_WIDTH)),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(0f),
          green = RcFloatWord.literal(1f),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(6),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
      ),
    )

  private companion object {
    const val GREEN = 0xff00ff00.toInt()
    const val PARENT_WIDTH = 42
    const val FRAME_NANOS = 16_666_667L
  }
}
