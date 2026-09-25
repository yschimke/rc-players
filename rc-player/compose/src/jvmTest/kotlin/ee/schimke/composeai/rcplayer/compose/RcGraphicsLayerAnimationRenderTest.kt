package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier
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
import java.io.File
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * `GraphicsLayerModifierOperation` wraps its floats in `AnimatableValue`, so a graphics layer bound
 * to a variable eases to a new value instead of jumping to it. The document says nothing about that
 * — it is the player's behaviour — which is why it is asserted on drawn pixels rather than on a
 * resolved number.
 *
 * The document is a 10px red block under a graphics layer whose `TRANSLATION_X` *is* a named
 * variable, so a host write moves it and nothing else.
 */
class RcGraphicsLayerAnimationRenderTest {
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aHostWriteEasesTheLayerAcrossInsteadOfTeleportingIt() {
    val namedValues = mutableStateMapOf<String, RcNamedValue>()
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(translationDocument(), namedValues = namedValues)
      }
    try {
      assertEquals(0, scene.blockLeftEdge(nanos = 0L), "the block starts at the left")

      namedValues["USER:offset"] = RcNamedValue.FloatValue(TRAVEL)
      // Written outside composition: deliver it now, or the first timed frame may not see it and
      // the tween starts late — on a slow runner, late enough never to move (#283).
      Snapshot.sendApplyNotifications()
      val positions = (1..STEPS).map { scene.blockLeftEdge(nanos = it * FRAME) }

      assertEquals(
        TRAVEL.toInt(),
        positions.last(),
        "the tween must finish at the value the host asked for",
      )
      assertTrue(
        positions.any { it > 0 && it < TRAVEL.toInt() },
        "no frame drew the block in flight — arriving instantly is the bug this pins: $positions",
      )
      assertEquals(
        positions.sorted(),
        positions,
        "an eased value only moves towards its target: $positions",
      )
    } finally {
      scene.close()
    }
  }

  /**
   * The tween runs on Compose's frame clock as an `Animatable`, so it is pinned on the test clock
   * with nothing advancing it but the test: mid-flight it sits on AndroidX's 300ms standard curve,
   * a retarget mid-flight eases on from where the layer is rather than jumping, and each run lands
   * exactly on the value the host asked for.
   */
  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun theTweenFollowsTheStandardCurveOnTheComposeFrameClock() =
    runSkikoComposeUiTest(size = Size(WIDTH.toFloat(), HEIGHT.toFloat()), density = Density(1f)) {
      mainClock.autoAdvance = false
      val namedValues = mutableStateMapOf<String, RcNamedValue>()
      setContent { RcComposePlayer(translationDocument(), namedValues = namedValues) }
      mainClock.advanceTimeByFrame()
      waitForIdle()
      assertEquals(0, blockLeftEdge(), "no tween on the opening pose")

      namedValues["USER:offset"] = RcNamedValue.FloatValue(TRAVEL)
      waitForIdle()
      assertEquals(0, blockLeftEdge(), "the tween starts from where the layer was")

      mainClock.advanceTimeBy(HALF_TWEEN_MILLIS)
      waitForIdle()
      val halfway = blockLeftEdge()
      // The tween's zero is the first frame after the write, so by now it has run at most
      // HALF_TWEEN_MILLIS and at least two frames less.
      val easing = DefaultRcAnimationSpec.rcMotionEasing()
      val earliest = (TRAVEL * easing.transform((HALF_TWEEN_MILLIS - 32f) / 300f)).toInt()
      val latest = ceil(TRAVEL * easing.transform(HALF_TWEEN_MILLIS / 300f)).toInt()
      assertTrue(
        halfway in earliest..latest,
        "mid-tween the layer is on the standard curve: $halfway not in $earliest..$latest",
      )

      // Back to 0 mid-flight: the layer turns around from where it is.
      namedValues["USER:offset"] = RcNamedValue.FloatValue(0f)
      waitForIdle()
      mainClock.advanceTimeByFrame()
      waitForIdle()
      // The running tween may take one more step on the frame that delivers the retarget, so the
      // turn starts within a frame of where the layer was — never back at either end.
      val turned = blockLeftEdge()
      assertTrue(
        turned in halfway until TRAVEL.toInt(),
        "a retarget eases on from the current value, not from either end: $turned vs $halfway",
      )
      // The standard curve starts slowly, so give it a few frames to move a whole pixel.
      repeat(8) { mainClock.advanceTimeByFrame() }
      waitForIdle()
      val returning = blockLeftEdge()
      assertTrue(returning in 1 until turned, "and heads back towards 0: $returning vs $turned")

      mainClock.advanceTimeBy(SETTLE_MILLIS)
      waitForIdle()
      assertEquals(0, blockLeftEdge(), "the retargeted tween lands where the host asked")

      namedValues["USER:offset"] = RcNamedValue.FloatValue(TRAVEL)
      waitForIdle()
      mainClock.advanceTimeBy(SETTLE_MILLIS)
      waitForIdle()
      assertEquals(TRAVEL.toInt(), blockLeftEdge(), "and a settled tween sits on its target")
    }

  @OptIn(ExperimentalTestApi::class)
  private fun ComposeUiTest.blockLeftEdge(): Int {
    val pixels = onRoot().captureToImage().toPixelMap()
    return (0 until WIDTH).firstOrNull { pixels[it, HEIGHT / 2].toArgb() == RED } ?: -1
  }

  /** Writes PR evidence when explicitly requested; ordinary test runs remain side-effect free. */
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun writeGraphicsLayerTweenEvidence() {
    val directory = System.getenv("RC_LAYOUT_EVIDENCE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    val namedValues = mutableStateMapOf<String, RcNamedValue>()
    val scene =
      ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
        RcComposePlayer(translationDocument(), namedValues = namedValues)
      }
    try {
      scene.render(0L)
      namedValues["USER:offset"] = RcNamedValue.FloatValue(TRAVEL)
      Snapshot.sendApplyNotifications()
      // A fixed frame count rather than a wall-clock instant, so the image is the same every run.
      repeat(EVIDENCE_FRAMES - 1) { scene.render((it + 1) * FRAME) }
      val frame = scene.render(EVIDENCE_FRAMES * FRAME)
      directory
        .resolve("graphics-layer-tween.png")
        .writeBytes(
          checkNotNull(frame.encodeToData()) { "Skia declined to encode the render" }.bytes
        )
    } finally {
      scene.close()
    }
  }

  /** The x of the block's left edge, which is the tween made visible. */
  @OptIn(ExperimentalComposeUiApi::class)
  private fun ImageComposeScene.blockLeftEdge(nanos: Long): Int {
    val bitmap = Bitmap().apply { allocN32Pixels(WIDTH, HEIGHT) }
    check(render(nanos).readPixels(bitmap))
    return (0 until WIDTH).firstOrNull { bitmap.getColor(it, HEIGHT / 2) == RED } ?: -1
  }

  private fun translationDocument(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val operations = mutableListOf<RcOperation>()
    operations += RcFloatConstant(OFFSET_ID, RcFloatWord.literal(0f))
    operations += RcNamedVariable(OFFSET_ID, RcNamedVariable.FLOAT_TYPE, "USER:offset")
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcCanvasLayout(5, 50)
    operations += RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(WIDTH.toFloat()))
    operations += RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(HEIGHT.toFloat()))
    operations +=
      RcGraphicsLayerModifier(
        listOf(
          RcGraphicsLayerAttribute.FloatValue(
            RcGraphicsLayerModifier.TRANSLATION_X,
            RcFloatWord(0x7fc00000 or OFFSET_ID),
          )
        )
      )
    operations += RcNoArg(RcOpcodes.CANVAS_OPERATIONS)
    operations += RcPaintData(listOf(4, RED))
    operations +=
      RcDraw4(
        RcOpcodes.DRAW_RECT,
        RcFloatWord.literal(0f),
        RcFloatWord.literal(0f),
        RcFloatWord.literal(10f),
        RcFloatWord.literal(HEIGHT.toFloat()),
      )
    operations += end
    operations += end
    repeat(2) { operations += end }
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = HEIGHT, modern = false),
      operations,
    )
  }

  private companion object {
    const val WIDTH = 60
    const val HEIGHT = 20
    const val OFFSET_ID = 21
    const val RED = 0xffff0000.toInt()
    const val TRAVEL = 40f
    const val FRAME = 16_000_000L
    /** Comfortably past the 300ms tween, so the last frame is the settled pose. */
    const val STEPS = 30
    const val HALF_TWEEN_MILLIS = 150L
    const val SETTLE_MILLIS = 400L
    /** Mid-flight, and reproducible because it counts frames rather than wall-clock time. */
    const val EVIDENCE_FRAMES = 8
  }
}
