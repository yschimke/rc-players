package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSequence
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawText
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcFontData
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Image

/**
 * remote-core alpha22's custom visibility animations ("Add custom visibility animation support",
 * androidx `b34eed8a88b`) and enter/exit sequencing, as the embedded player ports them
 * (`92da47b3ac6`, `2ed28fbdae4`).
 *
 * Every document is a row of 20 px tall canvases on a manual clock: a spacer, then the animating
 * component, then sometimes a sibling whose position shows what the layout is doing. Times are from
 * the click that changes the component's visibility; the transition starts on the frame after it,
 * so expectations leave a frame's slack.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcCustomVisibilityAnimationRenderTest {
  @Test
  fun aCustomExitDrawsTheComponentThroughItsFunctionAtItsProgress() =
    runSkikoComposeUiTest(size = Size(40f, 20f), density = Density(1f)) {
      start(exitDocument(exit = RcLayoutAnimation.Custom, exitFunctionId = WIPE))

      val opening = sample(25)
      // The function wipes from the right as progress grows. It is bound in the parent's space: the
      // component sits after a 10 px spacer, and the wipe starts at its right edge, not 10 px past.
      assertTrue(opening.isRed(11) && opening.isRed(28), "barely started: ${opening.row()}")

      val halfway = sample(125)
      val edge = halfway.firstGreen()
      assertTrue(
        edge in 20..30,
        "the wipe edge is near the middle of the component: ${halfway.row()}",
      )
      assertTrue(halfway.isRed(12) && halfway.isRed(edge - 2), "kept part: ${halfway.row()}")
      assertTrue(halfway.isClear(edge + 5) && halfway.isClear(38), "wiped part: ${halfway.row()}")
      // The function's own drawing past the content: a bar as tall as the component id is.
      assertTrue(halfway.isGreen(edge, y = 2) && !halfway.isGreen(edge, y = 18), halfway.row(18))

      val done = sample(250)
      assertTrue((10 until 40).all { done.isClear(it) }, "gone once the exit ends: ${done.row()}")
    }

  /**
   * A function whose paint names the document's embedded `FontData` font draws its text in it,
   * rather than failing to find the font: a function runs with the document's fonts like any other
   * draw stream.
   */
  @Test
  fun aCustomExitDrawsTextInTheDocumentsEmbeddedFont() =
    runSkikoComposeUiTest(size = Size(40f, 20f), density = Density(1f)) {
      start(
        exitDocument(
          exit = RcLayoutAnimation.Custom,
          exitFunctionId = FONT_EXIT,
          prelude = fontExit(FONT_EXIT),
        )
      )

      val halfway = sample(150)
      val ink =
        (10 until 40).count { halfway.color(it, 12).let { c -> c.alpha > .5f && c.red < .3f } }
      assertTrue(ink > 0, "the function's text drew: ${halfway.row(12)}")
    }

  @Test
  fun aCustomEnterRunsTheEnterFunctionFromNothingToTheComponent() =
    runSkikoComposeUiTest(size = Size(40f, 20f), density = Density(1f)) {
      start(
        exitDocument(
          initiallyVisible = false,
          enter = RcLayoutAnimation.Custom,
          enterFunctionId = WIPE_IN,
        )
      )

      val halfway = sample(150)
      val edge = halfway.firstGreen()
      assertTrue(
        edge in 18..32,
        "the wipe edge is near the middle of the component: ${halfway.row()}",
      )
      assertTrue(halfway.isRed(12) && halfway.isClear(38), halfway.row())

      val done = sample(250)
      assertTrue((10 until 40).all { done.isRed(it) }, "fully entered: ${done.row()}")
    }

  @Test
  fun aFunctionTheDocumentDoesNotDefinePaintsTheComponentUntilTheExitEnds() =
    runSkikoComposeUiTest(size = Size(40f, 20f), density = Density(1f)) {
      start(exitDocument(exit = RcLayoutAnimation.Custom, exitFunctionId = 999))

      val halfway = sample(150)
      assertTrue((10 until 40).all { halfway.isRed(it) }, "painted as it is: ${halfway.row()}")
      val done = sample(200)
      assertTrue(done.isClear(25), "gone once the exit ends: ${done.row()}")
    }

  @Test
  fun anExitBeforeTheLayoutKeepsItsSpaceUntilItHasPlayed() =
    runSkikoComposeUiTest(size = Size(60f, 20f), density = Density(1f)) {
      start(
        exitDocument(
          exit = RcLayoutAnimation.FadeOut.sequenced(RcAnimationSequence.Before),
          sibling = true,
        )
      )

      val halfway = sample(150)
      assertTrue(halfway.isBlue(45), "the sibling has not moved: ${halfway.row()}")
      assertTrue(halfway[20, 10].alpha in 0.2f..0.8f, "half faded: ${halfway[20, 10]}")
      // Its exit over, its space closes over the motion duration.
      val closing = sample(300)
      assertTrue(closing.isBlue(35) && !closing.isBlue(52), "moving in: ${closing.row()}")
      val closed = sample(300)
      assertTrue(closed.isBlue(15) && closed.isClear(45), "closed: ${closed.row()}")
    }

  @Test
  fun anExitAfterTheLayoutPaintsTheComponentAsItIsWhileItsSpaceCloses() =
    runSkikoComposeUiTest(size = Size(60f, 20f), density = Density(1f)) {
      start(
        exitDocument(
          exit = RcLayoutAnimation.FadeOut.sequenced(RcAnimationSequence.After),
          sibling = true,
        )
      )

      val moving = sample(150)
      assertTrue(!moving.isBlue(52), "the sibling moves at once: ${moving.row()}")
      // Not yet fading, at its own size, where it was, under the sibling sliding over it.
      assertTrue(moving[12, 10].alpha > .95f && moving.isRed(12), "unfaded: ${moving.row()}")
      assertTrue(moving.isRed(20), "still its full width: ${moving.row()}")
    }

  @Test
  fun aConcurrentExitFadesAtItsOwnSizeWhileItsSpaceCloses() =
    runSkikoComposeUiTest(size = Size(60f, 20f), density = Density(1f)) {
      start(exitDocument(exit = RcLayoutAnimation.FadeOut, sibling = true))

      val moving = sample(150)
      assertTrue(!moving.isBlue(52), "the sibling moves at once: ${moving.row()}")
      assertTrue(moving[12, 10].alpha in 0.2f..0.8f, "half faded: ${moving[12, 10]}")
      assertTrue(moving[20, 10].red > .1f, "still its full width: ${moving.row()}")
    }

  @Test
  fun anEnterAfterTheLayoutPaintsNothingUntilItsSpaceHasOpened() =
    runSkikoComposeUiTest(size = Size(60f, 20f), density = Density(1f)) {
      start(
        exitDocument(
          initiallyVisible = false,
          enter = RcLayoutAnimation.Custom.sequenced(RcAnimationSequence.After),
          enterFunctionId = WIPE_IN,
          sibling = true,
        )
      )

      // Not even the function's own drawing: at progress 0 it would still draw its bar.
      val opening = sample(150)
      assertTrue(
        (0 until 60).none { opening.isRed(it) || opening.isGreen(it) },
        "nothing yet: ${opening.row()}",
      )
      val entering = sample(300)
      assertTrue(entering.isBlue(45), "its space is open: ${entering.row()}")
      assertTrue(entering.firstGreen() in 16..34, "wiping in: ${entering.row()}")
      val entered = sample(350)
      assertTrue((10 until 40).all { entered.isRed(it) }, "entered: ${entered.row()}")
    }

  @Test
  fun anEnterBeforeTheLayoutPlaysBeforeItTakesItsSpace() =
    runSkikoComposeUiTest(size = Size(60f, 20f), density = Density(1f)) {
      start(
        exitDocument(
          initiallyVisible = false,
          enter = RcLayoutAnimation.FadeIn.sequenced(RcAnimationSequence.Before),
          sibling = true,
        )
      )

      val entering = sample(150)
      assertTrue(entering.isBlue(15), "the sibling has not moved: ${entering.row()}")
      val opened = sample(700)
      assertTrue(opened.isRed(15) && opened.isBlue(45), "its space is open: ${opened.row()}")
    }

  /**
   * Before/after evidence for `renders/cmp-custom-visibility-animation/`: a custom exit at 0, 25,
   * 50, 75 and 100 % of its progress. Before this change the spec's function ids were not read, and
   * a `CUSTOM` exit fell to AndroidX's `ParticleAnimation` branch, which hides the component at
   * once; that branch is what the "before" strip renders.
   */
  @Test
  fun writeCustomVisibilityEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    for ((name, exit) in
      listOf("before" to RcLayoutAnimation.Particle, "after" to RcLayoutAnimation.Custom)) {
      runSkikoComposeUiTest(size = Size(EVIDENCE_WIDTH, EVIDENCE_HEIGHT), density = Density(1f)) {
        start(evidenceDocument(exit))
        val frames = (0..4).map { step -> if (step == 0) capture() else frame(EVIDENCE_STEP) }
        directory.resolve("$name-custom-exit.png").writeBytes(strip(frames))
      }
    }
  }

  private fun ComposeUiTest.start(document: RcDocument) {
    mainClock.autoAdvance = false
    setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
    mainClock.advanceTimeByFrame()
    waitForIdle()
    mainClock.advanceTimeBy(400)
    waitForIdle()
    onNode(hasClickAction()).performTouchInput { click() }
    mainClock.advanceTimeByFrame()
    waitForIdle()
  }

  /** The frame [millis] after the previous sample. */
  private fun ComposeUiTest.sample(millis: Long): PixelMap {
    mainClock.advanceTimeBy(millis)
    waitForIdle()
    return onRoot().captureToImage().toPixelMap()
  }

  private fun ComposeUiTest.capture(): ImageBitmap = onRoot().captureToImage()

  private fun ComposeUiTest.frame(millis: Long): ImageBitmap {
    mainClock.advanceTimeBy(millis)
    waitForIdle()
    return capture()
  }

  private fun strip(frames: List<ImageBitmap>): ByteArray {
    val width = frames.sumOf { it.width } + EVIDENCE_GAP * (frames.size - 1)
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(width, frames.first().height)
    surface.canvas.clear(0xffffffff.toInt())
    var x = 0f
    for (frame in frames) {
      surface.canvas.drawImage(Image.makeFromBitmap(frame.asSkiaBitmap()), x, 0f)
      x += frame.width + EVIDENCE_GAP
    }
    return surface.makeImageSnapshot().encodeToData()!!.bytes
  }

  /**
   * A row: a 10 px spacer, the 30 px red component (id [COMPONENT]) whose visibility the click
   * changes, and with [sibling] a 20 px blue component after it. The click is on the spacer, which
   * never moves.
   */
  private fun exitDocument(
    initiallyVisible: Boolean = true,
    enter: RcLayoutAnimation = RcLayoutAnimation.FadeIn,
    exit: RcLayoutAnimation = RcLayoutAnimation.FadeOut,
    enterFunctionId: Int = -1,
    exitFunctionId: Int = -1,
    sibling: Boolean = false,
    prelude: List<RcOperation> = emptyList(),
  ): RcDocument {
    val operations = mutableListOf<RcOperation>()
    operations += prelude
    operations += RcIntegerConstant(VISIBILITY, if (initiallyVisible) 1 else 0)
    operations += wipe(WIPE, entering = false)
    operations += wipe(WIPE_IN, entering = true)
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f))
    operations += RcLayoutContent(4)
    operations +=
      canvas(
        SPACER,
        10f,
        0xffffffff.toInt(),
        listOf(
          RcClickModifier,
          RcValueIntegerChangeAction(VISIBILITY, if (initiallyVisible) 0 else 1),
          END,
        ),
      )
    operations +=
      canvas(
        COMPONENT,
        30f,
        RED,
        listOf(
          spec(enter, exit, enterFunctionId, exitFunctionId, LINEAR),
          RcVisibilityModifier(VISIBILITY),
        ),
      )
    if (sibling) operations += canvas(SIBLING, 20f, BLUE, listOf(spec(duration = 300f)))
    repeat(4) { operations += END }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 20, modern = false),
      operations,
    )
  }

  /**
   * A custom wipe: the component's content clipped to the part progress has not wiped, with a 3 px
   * green bar at the edge as tall as the component id. [entering] wipes in from the left.
   */
  private fun wipe(id: Int, entering: Boolean): List<RcOperation> {
    val p = ref(id + 1)
    val w = ref(id + 2)
    val h = ref(id + 3)
    val x = ref(id + 4)
    val y = ref(id + 5)
    val component = ref(id + 6)
    val kept = id + 7
    val edge = id + 8
    val bottom = id + 9
    val barBottom = id + 10
    val barLeft = id + 11
    return listOf(
      RcFloatFunctionDefine(id, (1..6).map { id + it }),
      // kept = w * p entering, w * (1 - p) exiting
      RcFloatExpression(
        kept,
        if (entering) listOf(w, p, MUL) else listOf(w, RcFloatWord.literal(1f), p, SUB, MUL),
        null,
      ),
      RcFloatExpression(edge, listOf(x, ref(kept), ADD), null),
      RcFloatExpression(bottom, listOf(y, h, ADD), null),
      RcFloatExpression(barBottom, listOf(y, component, ADD), null),
      RcFloatExpression(barLeft, listOf(ref(edge), RcFloatWord.literal(3f), SUB), null),
      RcNoArg(RcOpcodes.MATRIX_SAVE),
      RcDraw4(RcOpcodes.CLIP_RECT, x, y, ref(edge), ref(bottom)),
      RcNoArg(RcOpcodes.DRAW_CONTENT),
      RcNoArg(RcOpcodes.MATRIX_RESTORE),
      RcPaintData(listOf(4, GREEN)),
      RcDraw4(RcOpcodes.DRAW_RECT, ref(barLeft), y, ref(edge), ref(barBottom)),
      END,
    )
  }

  /** An exit drawing black text in the embedded Roboto Flex (font-data paint typeface) at x, y. */
  private fun fontExit(id: Int): List<RcOperation> {
    val face =
      File(VARIABLE_FACE_PATH).takeIf { it.isFile }?.readBytes()
        ?: error("vendored Roboto Flex not found at $VARIABLE_FACE_PATH")
    val baseline = id + 7
    return listOf(
      RcFontData(FONT_ID, 0, face),
      RcTextData(FONT_TEXT, "H"),
      RcFloatFunctionDefine(id, (1..6).map { id + it }),
      RcFloatExpression(baseline, listOf(ref(id + 5), RcFloatWord.literal(18f), ADD), null),
      RcPaintData(
        listOf(
          4,
          0xff000000.toInt(),
          1,
          RcFloatWord.literal(18f).bits,
          16 or ((400 or PAINT_TYPEFACE_FONT_DATA) shl 16),
          FONT_ID,
        )
      ),
      RcDrawText(FONT_TEXT, 0, 1, 0, 1, ref(id + 4), ref(baseline), false),
      END,
    )
  }

  /**
   * A card that exits by shrinking toward its centre while a wipe eats it from the right, with an
   * orange bar riding the wipe's edge: the function transforms and clips the component's content
   * and draws past it.
   */
  private fun evidenceDocument(exit: RcLayoutAnimation): RcDocument {
    val id = WIPE
    val p = ref(id + 1)
    val w = ref(id + 2)
    val h = ref(id + 3)
    val x = ref(id + 4)
    val y = ref(id + 5)
    val scale = id + 7
    val centerX = id + 8
    val centerY = id + 9
    val edge = id + 10
    val bottom = id + 11
    val barLeft = id + 12
    val function =
      listOf(
        RcFloatFunctionDefine(id, (1..6).map { id + it }),
        // scale = 1 - p * 0.4
        RcFloatExpression(
          scale,
          listOf(RcFloatWord.literal(1f), p, RcFloatWord.literal(.4f), MUL, SUB),
          null,
        ),
        RcFloatExpression(centerX, listOf(x, w, RcFloatWord.literal(2f), DIV, ADD), null),
        RcFloatExpression(centerY, listOf(y, h, RcFloatWord.literal(2f), DIV, ADD), null),
        RcFloatExpression(
          edge,
          listOf(x, w, RcFloatWord.literal(1f), p, SUB, MUL, ADD),
          null,
        ),
        RcFloatExpression(bottom, listOf(y, h, ADD), null),
        RcFloatExpression(barLeft, listOf(ref(edge), RcFloatWord.literal(6f), SUB), null),
        RcNoArg(RcOpcodes.MATRIX_SAVE),
        RcDraw4(RcOpcodes.MATRIX_SCALE, ref(scale), ref(scale), ref(centerX), ref(centerY)),
        RcNoArg(RcOpcodes.MATRIX_SAVE),
        RcDraw4(RcOpcodes.CLIP_RECT, x, y, ref(edge), ref(bottom)),
        RcNoArg(RcOpcodes.DRAW_CONTENT),
        RcNoArg(RcOpcodes.MATRIX_RESTORE),
        RcPaintData(listOf(4, ORANGE)),
        RcDraw4(RcOpcodes.DRAW_RECT, ref(barLeft), y, ref(edge), ref(bottom)),
        RcNoArg(RcOpcodes.MATRIX_RESTORE),
        END,
      )
    val operations = mutableListOf<RcOperation>()
    operations += RcIntegerConstant(VISIBILITY, 1)
    operations += function
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f))
    operations += RcLayoutContent(4)
    operations +=
      canvas(
        SPACER,
        20f,
        0xffffffff.toInt(),
        listOf(RcClickModifier, RcValueIntegerChangeAction(VISIBILITY, 0), END),
        height = EVIDENCE_HEIGHT,
      )
    operations += card(exit)
    repeat(4) { operations += END }
    return RcDocument(
      RcHeader(
        RcVersion(1, 0, 0),
        legacyWidth = EVIDENCE_WIDTH.toInt(),
        legacyHeight = EVIDENCE_HEIGHT.toInt(),
        modern = false,
      ),
      operations,
    )
  }

  /** The evidence card: a 120 x 80 teal canvas with a darker inner square, so scaling shows. */
  private fun card(exit: RcLayoutAnimation): List<RcOperation> =
    listOf(
      RcCanvasLayout(COMPONENT, -1),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(120f)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
      spec(RcLayoutAnimation.FadeIn, exit, -1, if (exit == RcLayoutAnimation.Custom) WIPE else -1),
      RcVisibilityModifier(VISIBILITY),
      RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
      RcPaintData(listOf(4, TEAL)),
      rect(0f, 0f, 120f, 80f),
      RcPaintData(listOf(4, NAVY)),
      rect(40f, 20f, 80f, 60f),
      END,
      END,
    )

  private fun canvas(
    id: Int,
    width: Float,
    color: Int,
    modifiers: List<RcOperation>,
    height: Float = 20f,
  ): List<RcOperation> =
    listOf(
      RcCanvasLayout(id, -1),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(width)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(height)),
    ) +
      modifiers +
      listOf(
        RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
        RcPaintData(listOf(4, color)),
        rect(0f, 0f, width, height),
        END,
        END,
      )

  private fun spec(
    enter: RcLayoutAnimation = RcLayoutAnimation.FadeIn,
    exit: RcLayoutAnimation = RcLayoutAnimation.FadeOut,
    enterFunctionId: Int = -1,
    exitFunctionId: Int = -1,
    easing: Int = LINEAR,
    duration: Float = 300f,
  ) =
    RcAnimationSpec(
      animationId = -1,
      motionDurationMillis = RcFloatWord.literal(duration),
      motionEasingType = easing,
      visibilityDurationMillis = RcFloatWord.literal(duration),
      visibilityEasingType = easing,
      enterAnimation = enter,
      exitAnimation = exit,
      enterFunctionId = enterFunctionId,
      exitFunctionId = exitFunctionId,
    )

  private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
    RcDraw4(
      RcOpcodes.DRAW_RECT,
      RcFloatWord.literal(left),
      RcFloatWord.literal(top),
      RcFloatWord.literal(right),
      RcFloatWord.literal(bottom),
    )

  private fun ref(id: Int) = RcFloatWord(0x7fc00000 or id)

  private fun PixelMap.color(x: Int, y: Int = 10): Color = this[x, y]

  private fun PixelMap.isRed(x: Int, y: Int = 10) =
    color(x, y).let { it.alpha > .5f && it.red > .8f && it.green < .2f && it.blue < .2f }

  private fun PixelMap.isGreen(x: Int, y: Int = 10) =
    color(x, y).let { it.alpha > .5f && it.green > .4f && it.red < .2f && it.blue < .2f }

  private fun PixelMap.isBlue(x: Int, y: Int = 10) =
    color(x, y).let { it.alpha > .5f && it.blue > .8f && it.red < .2f && it.green < .2f }

  /** Nothing drawn there: the transparent host background. */
  private fun PixelMap.isClear(x: Int, y: Int = 10) = color(x, y).alpha < .05f

  private fun PixelMap.firstGreen(y: Int = 10) =
    (0 until width).firstOrNull { isGreen(it, y) } ?: -1

  /** The row of pixels as letters, for a failure message. */
  private fun PixelMap.row(y: Int = 10): String =
    (0 until width).joinToString("") { x ->
      when {
        isClear(x, y) -> "."
        isRed(x, y) -> "R"
        isGreen(x, y) -> "G"
        isBlue(x, y) -> "B"
        color(x, y).let { it.red > .9f && it.green > .9f && it.blue > .9f } -> "w"
        else -> "?"
      }
    }

  private companion object {
    val END = RcNoArg(RcOpcodes.CONTAINER_END)
    // AndroidX `AnimatedFloatExpression` operators, NaN-boxed.
    val ADD = RcFloatWord(0xffb10001.toInt())
    val SUB = RcFloatWord(0xffb10002.toInt())
    val MUL = RcFloatWord(0xffb10003.toInt())
    val DIV = RcFloatWord(0xffb10004.toInt())
    const val LINEAR = 4
    const val VISIBILITY = 50
    const val SPACER = 14
    const val COMPONENT = 15
    const val SIBLING = 16
    const val WIPE = 500
    const val WIPE_IN = 600
    const val FONT_EXIT = 700
    const val FONT_ID = 43
    const val FONT_TEXT = 44
    const val VARIABLE_FACE_PATH = "../../rc-player/wasm/dist-assets/fonts/RobotoFlex.ttf"
    const val RED = 0xffff0000.toInt()
    const val GREEN = 0xff00c000.toInt()
    const val BLUE = 0xff0000ff.toInt()
    const val TEAL = 0xff00897b.toInt()
    const val NAVY = 0xff1a237e.toInt()
    const val ORANGE = 0xffff9800.toInt()
    const val EVIDENCE_WIDTH = 140f
    const val EVIDENCE_HEIGHT = 80f
    const val EVIDENCE_STEP = 75L
    const val EVIDENCE_GAP = 8
  }
}
