package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The support report and alpha22's component offscreen buffers: the player sizes and allocates
 * them, so a document using them is renderable — except where there is no component to size one to,
 * which fails in core as it does here.
 */
class RcComponentOffscreenSupportTest {
  private val header = RcHeader(RcVersion(1, 0, 0), modern = false)

  @Test
  fun aCustomVisibilityFunctionCapturingItsComponentIsRenderable() {
    val document =
      layoutDocument(
        before =
          listOf(
            RcFloatFunctionDefine(500, (501..506).toList()),
            buffer(820),
            RcDrawToBitmap(820, RcDrawToBitmap.MODE_COMPONENT_ID, 506),
            RcNoArg(RcOpcodes.DRAW_CONTENT),
            RcDrawToBitmap(0, 0, 0),
            drawBitmap(820),
            END,
          ),
        modifiers =
          listOf(
            RcAnimationSpec(
              animationId = -1,
              motionDurationMillis = RcFloatWord.literal(300f),
              motionEasingType = 4,
              visibilityDurationMillis = RcFloatWord.literal(300f),
              visibilityEasingType = 4,
              enterAnimation = RcLayoutAnimation.FadeIn,
              exitAnimation = RcLayoutAnimation.Custom,
              exitFunctionId = 500,
            )
          ),
      )

    val report = document.composeSupportReport()

    assertTrue(report.fullyRenderable, report.issues.toString())
  }

  @Test
  fun aDrawModifierCapturingAComponentByIdIsRenderable() {
    val document =
      layoutDocument(
        before = listOf(buffer(821, component = 5)),
        canvas =
          listOf(
            RcDrawToBitmap(821, RcDrawToBitmap.MODE_COMPONENT_ID, 5),
            RcNoArg(RcOpcodes.DRAW_CONTENT),
            RcDrawToBitmap(0, 0, 0),
            drawBitmap(821),
          ),
      )

    val report = document.composeSupportReport()

    assertTrue(report.fullyRenderable, report.issues.toString())
  }

  /** `createBitmap(id, width, height)`: a blank bitmap the player allocates to draw into. */
  @Test
  fun anEmptyBitmapToDrawIntoIsRenderable() {
    val document =
      RcDocument(
        header,
        listOf(
          RcBitmapData(
            7,
            8,
            8,
            RcBitmapData.TYPE_RAW8888,
            RcBitmapData.ENCODING_EMPTY,
            ByteArray(0),
          ),
          RcDrawToBitmap(7, 0, 0xff00ff00.toInt()),
          RcDrawToBitmap(0, 0, 0),
          drawBitmap(7),
        ),
      )

    val report = document.composeSupportReport()

    assertTrue(report.fullyRenderable, report.issues.toString())
  }

  /**
   * A document without a layout has no component, so a buffer drawn into has nothing to be sized
   * to: core's `DrawToBitmap` then has no bitmap to draw into, and neither does the player.
   */
  @Test
  fun aBufferWithNoComponentToSizeItToIsBlocking() {
    val document =
      RcDocument(
        header,
        listOf(buffer(820), RcDrawToBitmap(820, 0, 0), RcDrawToBitmap(0, 0, 0)),
      )

    val issues = document.composeSupportReport().issues

    assertEquals(
      listOf(
        RcComposeSupportIssue(
          1,
          "DrawToBitmap",
          "component offscreen buffer 820 has no component to size it to",
        )
      ),
      issues,
    )
    assertEquals(RcComposeSupportSeverity.BLOCKING, issues.single().severity)
  }

  private fun layoutDocument(
    before: List<RcOperation> = emptyList(),
    modifiers: List<RcOperation> = emptyList(),
    canvas: List<RcOperation> = emptyList(),
  ): RcDocument =
    RcDocument(
      header,
      before +
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          RcCanvasLayout(5, -1),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        ) +
        modifiers +
        listOf(RcNoArg(RcOpcodes.CANVAS_OPERATIONS)) +
        canvas +
        listOf(END, END, END, END),
    )

  private fun buffer(id: Int, component: Int = 0) =
    RcBitmapData(
      id,
      1,
      1,
      RcBitmapData.TYPE_RAW8888,
      RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER,
      if (component == 0) ByteArray(0) else ByteArray(4) { (component shr (24 - it * 8)).toByte() },
    )

  private fun drawBitmap(id: Int) =
    RcDrawBitmap(
      id,
      RcFloatWord.literal(0f),
      RcFloatWord.literal(0f),
      RcFloatWord.literal(40f),
      RcFloatWord.literal(20f),
      0,
    )

  private companion object {
    val END = RcNoArg(RcOpcodes.CONTAINER_END)
  }
}
