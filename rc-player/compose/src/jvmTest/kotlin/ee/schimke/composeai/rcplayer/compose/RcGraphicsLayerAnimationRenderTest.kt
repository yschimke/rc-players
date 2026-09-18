package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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
    /** Mid-flight, and reproducible because it counts frames rather than wall-clock time. */
    const val EVIDENCE_FRAMES = 8
  }
}
