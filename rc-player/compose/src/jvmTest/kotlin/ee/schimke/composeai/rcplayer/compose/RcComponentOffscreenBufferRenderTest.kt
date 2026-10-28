package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
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
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionCall
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
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
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcVisibilityModifier
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/**
 * remote-core alpha22's component offscreen buffers
 * (`BitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER` with `createOffscreenBitmap` /
 * `drawComponentToBitmap`): a buffer sized to a component each time it is drawn into, which
 * `DrawContent` paints the component into and which then draws like any other bitmap.
 *
 * Every component here is two-tone — red on its left half, blue on its right — and the documents
 * draw the captured bitmap mirrored (a `DrawBitmap` whose left is past its right), so a capture
 * shows as blue-then-red where the component itself is red-then-blue. Before this change the player
 * had no bitmap for such a buffer and every one of these documents threw "DrawToBitmap references
 * missing bitmap" on its first draw into it.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcComponentOffscreenBufferRenderTest {
  /**
   * The upstream shape (`RcPlayerCustomVisibilityAnimationTest`): a custom exit that captures the
   * animating component — named by the function's component argument — into a buffer and draws it
   * mirrored, shrinking toward its centre with progress.
   */
  @Test
  fun aCustomExitCapturesTheComponentIntoABufferAndDrawsItTransformed() =
    runSkikoComposeUiTest(size = Size(40f, 20f), density = Density(1f)) {
      start(visibilityDocument(declareInFunction = true))

      val halfway = sample(150)
      // Scale 0.75 about the component's centre (x 25): the mirrored capture spans x 14 to 36.
      assertTrue(halfway.isBlue(16) && halfway.isBlue(23), "mirrored left: ${halfway.row()}")
      assertTrue(halfway.isRed(27) && halfway.isRed(34), "mirrored right: ${halfway.row()}")
      assertTrue(halfway.isClear(11) && halfway.isClear(38), "scaled in: ${halfway.row()}")

      val done = sample(250)
      assertTrue((10 until 40).all { done.isClear(it) }, "gone once the exit ends: ${done.row()}")
    }

  /**
   * The crash this change fixes, in the form a document is most likely to take: the buffer declared
   * once at the top of the document rather than inside the function. It used to throw on the exit's
   * first frame; now the buffer is sized to the component when the function draws into it.
   */
  @Test
  fun aBufferDeclaredOutsideTheFunctionNoLongerThrowsAndCaptures() =
    runSkikoComposeUiTest(size = Size(40f, 20f), density = Density(1f)) {
      start(visibilityDocument(declareInFunction = false))

      val opening = sample(25)
      // Barely scaled: the whole component, mirrored.
      assertTrue(opening.isBlue(12) && opening.isRed(37), "mirrored: ${opening.row()}")
      val halfway = sample(125)
      assertTrue(halfway.isBlue(16) && halfway.isRed(34), "mirrored: ${halfway.row()}")
    }

  /**
   * Beyond visibility animations: a component's draw modifier calls a function that captures
   * ANOTHER component by id. The buffer is sized to the captured component (40 px), not to the one
   * drawing it (60 px) — stretched over the 60 px, its two halves meet in the middle.
   */
  @Test
  fun aDrawModifierCapturesAnotherComponentByIdSizedToThatComponent() =
    runSkikoComposeUiTest(size = Size(100f, 20f), density = Density(1f)) {
      start(captureByIdDocument(targetFirst = true), click = false)

      val frame = sample(32)
      // The target, as it is.
      assertTrue(frame.isRed(5) && frame.isBlue(35), "target: ${frame.row()}")
      // Its capture, mirrored and stretched over x 40 to 100.
      assertTrue(frame.isBlue(45) && frame.isBlue(66), "capture left: ${frame.row()}")
      assertTrue(frame.isRed(74) && frame.isRed(95), "capture right: ${frame.row()}")
    }

  /**
   * The same capture with the function defined at the document's top level, ahead of the layout,
   * where writers put it: the draw modifier's call resolves it document-wide, as core does.
   */
  @Test
  fun aDrawModifierCallsAFunctionTheDocumentDefinesAheadOfTheLayout() =
    runSkikoComposeUiTest(size = Size(100f, 20f), density = Density(1f)) {
      start(captureByIdDocument(targetFirst = true, functionAtTopLevel = true), click = false)

      val frame = sample(32)
      assertTrue(frame.isRed(5) && frame.isBlue(35), "target: ${frame.row()}")
      assertTrue(frame.isBlue(45) && frame.isBlue(66), "capture left: ${frame.row()}")
      assertTrue(frame.isRed(74) && frame.isRed(95), "capture right: ${frame.row()}")
    }

  /**
   * A capture of a component that draws after the capturing one: its recording does not exist on
   * the capturing component's first draw, and the capture follows once it does.
   */
  @Test
  fun aComponentDrawnAfterItsCaptureIsCapturedOnceItHasDrawn() =
    runSkikoComposeUiTest(size = Size(100f, 20f), density = Density(1f)) {
      start(captureByIdDocument(targetFirst = false), click = false)

      val frame = sample(64)
      assertTrue(frame.isBlue(5) && frame.isRed(55), "capture: ${frame.row()}")
      assertTrue(frame.isRed(65) && frame.isBlue(95), "target: ${frame.row()}")
    }

  /**
   * Without a component id, the buffer is the active component's: a box's draw modifier captures
   * its own content (`drawComponentToBitmap(bitmap)`) and draws only the mirrored capture.
   */
  @Test
  fun aDrawModifierCapturesItsOwnContentIntoTheActiveComponentsBuffer() =
    runSkikoComposeUiTest(size = Size(40f, 20f), density = Density(1f)) {
      start(activeCaptureDocument(), click = false)

      val frame = sample(32)
      assertTrue(frame.isBlue(5) && frame.isBlue(15), "mirrored left: ${frame.row()}")
      assertTrue(frame.isRed(25) && frame.isRed(35), "mirrored right: ${frame.row()}")
    }

  /**
   * Evidence for `renders/cmp-custom-visibility-animation/` (the component shown, then the
   * capturing exit at 0 to 100 % of its progress) and `renders/cmp-component-offscreen-buffer/` (a
   * capture by id and an active-component capture). Before this change both documents threw on
   * their first draw into the buffer, so there is no "before" frame to render.
   */
  @Test
  fun writeComponentOffscreenEvidence() {
    val root = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    val visibility = root.resolve("cmp-custom-visibility-animation").apply { mkdirs() }
    runSkikoComposeUiTest(size = Size(EVIDENCE_WIDTH, EVIDENCE_HEIGHT), density = Density(1f)) {
      start(visibilityDocument(declareInFunction = true, scale = EVIDENCE_SCALE), click = false)
      val shown = frame(400)
      onNode(hasClickAction()).performTouchInput { click() }
      mainClock.advanceTimeByFrame()
      waitForIdle()
      val frames =
        listOf(shown) + (0..4).map { step -> if (step == 0) capture() else frame(EVIDENCE_STEP) }
      visibility.resolve("after-offscreen-capture-exit.png").writeBytes(strip(frames))
    }
    val general = root.resolve("cmp-component-offscreen-buffer").apply { mkdirs() }
    runSkikoComposeUiTest(size = Size(100f, 20f), density = Density(1f)) {
      start(captureByIdDocument(targetFirst = true), click = false)
      general.resolve("after-capture-by-id.png").writeBytes(strip(listOf(frame(64))))
    }
    runSkikoComposeUiTest(size = Size(40f, 20f), density = Density(1f)) {
      start(activeCaptureDocument(), click = false)
      general.resolve("after-capture-active.png").writeBytes(strip(listOf(frame(64))))
    }
  }

  private fun ComposeUiTest.start(document: RcDocument, click: Boolean = true) {
    mainClock.autoAdvance = false
    setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
    mainClock.advanceTimeByFrame()
    waitForIdle()
    if (!click) return
    mainClock.advanceTimeBy(400)
    waitForIdle()
    onNode(hasClickAction()).performTouchInput { click() }
    mainClock.advanceTimeByFrame()
    waitForIdle()
  }

  private fun ComposeUiTest.sample(millis: Long): PixelMap = frame(millis).toPixelMap()

  private fun ComposeUiTest.capture(): ImageBitmap = onRoot().captureToImage()

  private fun ComposeUiTest.frame(millis: Long): ImageBitmap {
    mainClock.advanceTimeBy(millis)
    waitForIdle()
    return capture()
  }

  /** [frames] side by side, each blown up [EVIDENCE_ZOOM] times without smoothing. */
  private fun strip(frames: List<ImageBitmap>): ByteArray {
    val width = (frames.sumOf { it.width } * EVIDENCE_ZOOM) + EVIDENCE_GAP * (frames.size - 1)
    val surface =
      org.jetbrains.skia.Surface.makeRasterN32Premul(width, frames.first().height * EVIDENCE_ZOOM)
    surface.canvas.clear(0xffffffff.toInt())
    var x = 0f
    for (frame in frames) {
      val zoomed = Rect.makeXYWH(x, 0f, frame.width * ZOOM, frame.height * ZOOM)
      val image = Image.makeFromBitmap(frame.asSkiaBitmap())
      val source = Rect.makeWH(frame.width.toFloat(), frame.height.toFloat())
      surface.canvas.drawImageRect(image, source, zoomed, SamplingMode.DEFAULT, null, true)
      x += frame.width * ZOOM + EVIDENCE_GAP
    }
    return surface.makeImageSnapshot().encodeToData()!!.bytes
  }

  /**
   * A row: a 10 px spacer (the click), then the 30 px two-tone component (id [COMPONENT]) whose
   * click-driven exit runs [CAPTURE]: capture the component into buffer [BUFFER] by its component
   * argument, then draw the buffer mirrored and scaled by `1 - progress * scale` about its centre.
   */
  private fun visibilityDocument(declareInFunction: Boolean, scale: Float = .5f): RcDocument {
    val p = ref(CAPTURE + 1)
    val w = ref(CAPTURE + 2)
    val h = ref(CAPTURE + 3)
    val x = ref(CAPTURE + 4)
    val y = ref(CAPTURE + 5)
    val component = CAPTURE + 6
    val half = CAPTURE + 7
    val center = CAPTURE + 8
    val left = CAPTURE + 9
    val right = CAPTURE + 10
    val bottom = CAPTURE + 11
    val operations = mutableListOf<RcOperation>()
    operations += RcIntegerConstant(VISIBILITY, 1)
    if (!declareInFunction) operations += buffer(BUFFER)
    operations += RcFloatFunctionDefine(CAPTURE, (1..6).map { CAPTURE + it })
    if (declareInFunction) operations += buffer(BUFFER)
    operations += RcDrawToBitmap(BUFFER, RcDrawToBitmap.MODE_COMPONENT_ID, component)
    operations += RcNoArg(RcOpcodes.DRAW_CONTENT)
    operations += RcDrawToBitmap(0, 0, 0)
    // half = w * (1 - p * scale) / 2; mirrored: left is the right edge and right the left one.
    operations +=
      RcFloatExpression(
        half,
        listOf(
          w,
          RcFloatWord.literal(1f),
          p,
          RcFloatWord.literal(scale),
          MUL,
          SUB,
          MUL,
          RcFloatWord.literal(2f),
          DIV,
        ),
        null,
      )
    operations += RcFloatExpression(center, listOf(x, w, RcFloatWord.literal(2f), DIV, ADD), null)
    operations += RcFloatExpression(left, listOf(ref(center), ref(half), ADD), null)
    operations += RcFloatExpression(right, listOf(ref(center), ref(half), SUB), null)
    operations += RcFloatExpression(bottom, listOf(y, h, ADD), null)
    operations += RcDrawBitmap(BUFFER, ref(left), y, ref(right), ref(bottom), 0)
    operations += END
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f))
    operations += RcLayoutContent(4)
    operations +=
      canvas(
        SPACER,
        10f,
        listOf(RcClickModifier, RcValueIntegerChangeAction(VISIBILITY, 0), END),
        tones = listOf(WHITE),
      )
    operations +=
      canvas(
        COMPONENT,
        30f,
        listOf(
          RcAnimationSpec(
            animationId = -1,
            motionDurationMillis = RcFloatWord.literal(300f),
            motionEasingType = LINEAR,
            visibilityDurationMillis = RcFloatWord.literal(300f),
            visibilityEasingType = LINEAR,
            enterAnimation = RcLayoutAnimation.FadeIn,
            exitAnimation = RcLayoutAnimation.Custom,
            exitFunctionId = CAPTURE,
          ),
          RcVisibilityModifier(VISIBILITY),
        ),
      )
    repeat(4) { operations += END }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 20, modern = false),
      operations,
    )
  }

  /**
   * A row of a 40 px two-tone target (id [TARGET]) and a 60 px canvas whose draw modifier defines
   * and calls [BY_ID] with the target's id: the function captures the component its argument names
   * into [BUFFER] (declared with that id, so sized to it), and the modifier draws the buffer
   * mirrored over its own 60 px. [targetFirst] puts the target before the capturing canvas.
   */
  private fun captureByIdDocument(
    targetFirst: Boolean,
    functionAtTopLevel: Boolean = false,
  ): RcDocument {
    val named = BY_ID + 1
    val function =
      listOf(
        RcFloatFunctionDefine(BY_ID, listOf(named)),
        RcDrawToBitmap(BUFFER, RcDrawToBitmap.MODE_COMPONENT_ID, named),
        RcNoArg(RcOpcodes.DRAW_CONTENT),
        RcDrawToBitmap(0, 0, 0),
        END,
      )
    val operations = mutableListOf<RcOperation>()
    operations += buffer(BUFFER, component = TARGET)
    if (functionAtTopLevel) operations += function
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcRowLayout(3, -1, 1, 4, RcFloatWord.literal(0f))
    operations += RcLayoutContent(4)
    val target = canvas(TARGET, 40f, emptyList())
    val capturing =
      listOf(
        RcCanvasLayout(CAPTURING, -1),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
      ) +
        (if (functionAtTopLevel) emptyList() else function) +
        listOf(
          RcFloatFunctionCall(BY_ID, listOf(RcFloatWord.literal(TARGET.toFloat()))),
          RcDrawBitmap(BUFFER, literal(60f), literal(0f), literal(0f), literal(20f), 0),
          END,
          END,
        )
    operations += if (targetFirst) target + capturing else capturing + target
    repeat(4) { operations += END }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 20, modern = false),
      operations,
    )
  }

  /**
   * A 40 px box whose draw modifier captures its own content — a two-tone canvas — into a buffer
   * declared without a component, and draws only the mirrored capture.
   */
  private fun activeCaptureDocument(): RcDocument {
    val operations = mutableListOf<RcOperation>()
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcBoxLayout(BOX, -1, 1, 1)
    operations += RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f))
    operations += RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f))
    operations += RcNoArg(RcOpcodes.CANVAS_OPERATIONS)
    operations += buffer(BUFFER)
    operations += RcDrawToBitmap(BUFFER, 0, 0)
    operations += RcNoArg(RcOpcodes.DRAW_CONTENT)
    operations += RcDrawToBitmap(0, 0, 0)
    operations += RcDrawBitmap(BUFFER, literal(40f), literal(0f), literal(0f), literal(20f), 0)
    operations += END
    operations += RcLayoutContent(BOX + 1)
    operations += canvas(TARGET, 40f, emptyList())
    repeat(4) { operations += END }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 20, modern = false),
      operations,
    )
  }

  /** `createOffscreenBitmap(id[, component])`, as AndroidX's writer encodes it. */
  private fun buffer(id: Int, component: Int = 0) =
    RcBitmapData(
      id,
      1,
      1,
      RcBitmapData.TYPE_RAW8888,
      RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER,
      if (component == 0) ByteArray(0) else ByteArray(4) { (component shr (24 - it * 8)).toByte() },
    )

  /**
   * A [width] x 20 canvas painted in [tones], side by side: red then blue unless told otherwise.
   */
  private fun canvas(
    id: Int,
    width: Float,
    modifiers: List<RcOperation>,
    tones: List<Int> = listOf(RED, BLUE),
  ): List<RcOperation> =
    listOf(
      RcCanvasLayout(id, -1),
      RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(width)),
      RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
    ) +
      modifiers +
      listOf(RcNoArg(RcOpcodes.CANVAS_OPERATIONS)) +
      tones.flatMapIndexed { index, tone ->
        val step = width / tones.size
        listOf(RcPaintData(listOf(4, tone)), rect(step * index, 0f, step * (index + 1), 20f))
      } +
      listOf(END, END)

  private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
    RcDraw4(RcOpcodes.DRAW_RECT, literal(left), literal(top), literal(right), literal(bottom))

  private fun literal(value: Float) = RcFloatWord.literal(value)

  private fun ref(id: Int) = RcFloatWord(0x7fc00000 or id)

  private fun PixelMap.isRed(x: Int, y: Int = 10) =
    this[x, y].let { it.alpha > .5f && it.red > .8f && it.green < .2f && it.blue < .2f }

  private fun PixelMap.isBlue(x: Int, y: Int = 10) =
    this[x, y].let { it.alpha > .5f && it.blue > .8f && it.red < .2f && it.green < .2f }

  private fun PixelMap.isClear(x: Int, y: Int = 10) = this[x, y].alpha < .05f

  private fun PixelMap.row(y: Int = 10): String =
    (0 until width).joinToString("") { x ->
      when {
        isClear(x, y) -> "."
        isRed(x, y) -> "R"
        isBlue(x, y) -> "B"
        this[x, y].let { it.red > .9f && it.green > .9f && it.blue > .9f } -> "w"
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
    const val TARGET = 20
    const val CAPTURING = 21
    const val BOX = 30
    const val BUFFER = 820
    const val CAPTURE = 500
    const val BY_ID = 600
    const val RED = 0xffff0000.toInt()
    const val BLUE = 0xff0000ff.toInt()
    const val WHITE = 0xffffffff.toInt()
    const val EVIDENCE_WIDTH = 40f
    const val EVIDENCE_HEIGHT = 20f
    const val EVIDENCE_SCALE = .8f
    const val EVIDENCE_STEP = 75L
    const val EVIDENCE_GAP = 8
    const val EVIDENCE_ZOOM = 4
    const val ZOOM = EVIDENCE_ZOOM.toFloat()
  }
}
