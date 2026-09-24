package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

class RcLayoutAnimationsRenderTest {
  @Test
  fun aComponentEasesToItsNewPositionByDefault() {
    // No animation spec anywhere: AndroidX still moves a component to a new measure over 300 ms,
    // so on the first frame after the column grows its centred child is where it was.
    val pixels = afterTheColumnGrows(layoutAnimations = true)
    assertEquals(GREEN, pixels.getColor(20, 16))
    assertEquals(0, pixels.getColor(20, 36))
  }

  @Test
  fun aHostThatTurnsLayoutAnimationsOffGetsTheNewPositionAtOnce() {
    val pixels = afterTheColumnGrows(layoutAnimations = false)
    assertEquals(0, pixels.getColor(20, 16))
    assertEquals(GREEN, pixels.getColor(20, 36))
  }

  private fun afterTheColumnGrows(layoutAnimations: Boolean): Bitmap {
    val height = mutableIntStateOf(40)
    val scene =
      ImageComposeScene(width = 40, height = 80, density = Density(1f)) {
        CompositionLocalProvider(LocalRcLayoutAnimations provides layoutAnimations) {
          Box(Modifier.size(40.dp, height.intValue.dp)) {
            RcComposePlayer(document, Modifier.fillMaxSize())
          }
        }
      }
    try {
      scene.render(0L)
      height.intValue = 80
      val bitmap = Bitmap().apply { allocN32Pixels(40, 80) }
      check(scene.render(FRAME_NANOS).readPixels(bitmap))
      return bitmap
    } finally {
      scene.close()
    }
  }

  private val document =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 40, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(3, -1, CENTER, CENTER, RcFloatWord.literal(0f)),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcLayoutContent(4),
        RcCanvasLayout(5, 50),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(10f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(10f)),
        RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
        RcPaintData(listOf(4, GREEN)),
        RcDraw4(
          RcOpcodes.DRAW_RECT,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          RcFloatWord.literal(10f),
          RcFloatWord.literal(10f),
        ),
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
    /** AndroidX `CENTER` for both axes of a column. */
    const val CENTER = 2
    const val FRAME_NANOS = 16_000_000L
  }
}
