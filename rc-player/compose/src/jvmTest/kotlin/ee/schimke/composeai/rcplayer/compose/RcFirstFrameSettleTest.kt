package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
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
   * player answered them before; it must neither crash a host that asks nor collapse to nothing
   * because the first answer came before anything was measured.
   */
  @Test
  fun aHostMeasuringIntrinsicsStillSeesTheDocument() {
    val scene =
      ImageComposeScene(width = 80, height = 40, density = Density(1f)) {
        Column(Modifier.width(IntrinsicSize.Max)) { RcComposePlayer(cappedByParentWidth()) }
      }
    try {
      assertEquals(40 * 20, scene.render(0L).greenPixels())
      assertEquals(40 * 20, scene.render(FRAME_NANOS).greenPixels())
    } finally {
      scene.close()
    }
  }

  /**
   * The probes measure under the constraints the host's own modifier leaves, not the raw ones: host
   * padding narrows a filling component to 60, and the cap — half its published width — has to be
   * 30, not the 40 an unpadded probe would publish.
   */
  @Test
  fun hostPaddingShapesTheSettledWidth() {
    val scene =
      ImageComposeScene(width = 80, height = 40, density = Density(1f)) {
        RcComposePlayer(
          cappedByParentWidth(parentWidth = FILL, childWidth = 70f, capToHalf = true),
          modifier = Modifier.padding(horizontal = 10.dp),
        )
      }
    try {
      assertEquals(30 * 20, scene.render(0L).greenPixels())
      assertEquals(30 * 20, scene.render(FRAME_NANOS).greenPixels())
    } finally {
      scene.close()
    }
  }

  /** Parent data only reaches a direct child, so the host's modifier has to stay outermost. */
  @Test
  fun aWeightFromTheHostStillReachesTheRow() {
    val scene =
      ImageComposeScene(width = 80, height = 40, density = Density(1f)) {
        Row(Modifier.fillMaxWidth()) {
          Spacer(Modifier.weight(1f))
          RcComposePlayer(
            cappedByParentWidth(parentWidth = FILL, childWidth = 70f),
            modifier = Modifier.weight(1f),
          )
        }
      }
    try {
      assertEquals(40 * 20, scene.render(0L).greenPixels())
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

  private fun cappedByParentWidth(
    parentWidth: RcWidthModifier = RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
    childWidth: Float = 60f,
    capToHalf: Boolean = false,
  ): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 80, legacyHeight = 40, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(3, -1, 1, 4),
        parentWidth,
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(30f)),
        RcLayoutContent(4),
        RcComponentValue(RcComponentValue.WIDTH, componentId = 4, valueId = PARENT_WIDTH),
        RcFloatExpression(
          HALF_PARENT_WIDTH,
          listOf(reference(PARENT_WIDTH), RcFloatWord.literal(2f), FLOAT_DIV),
          animation = null,
        ),
        RcBoxLayout(5, -1, 1, 4),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(childWidth)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcWidthInModifier(
          RcFloatWord.literal(-1f),
          reference(if (capToHalf) HALF_PARENT_WIDTH else PARENT_WIDTH),
        ),
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

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private companion object {
    const val GREEN = 0xff00ff00.toInt()
    const val PARENT_WIDTH = 42
    const val HALF_PARENT_WIDTH = 43
    /** AndroidX's float `DIV` operator, NaN-encoded the way a document carries it. */
    val FLOAT_DIV = RcFloatWord(0xff800000.toInt() or 0x310004)
    const val FRAME_NANOS = 16_666_667L
    val FILL = RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))
  }
}
