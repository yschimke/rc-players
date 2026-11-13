package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * A host that lets players defer the lookahead pass ([LocalRcDeferLayoutAnimations]) still sees a
 * change it hands a static document animate to its new bounds, as AndroidX animates it; and a
 * document that can change its layout on its own is never deferred.
 */
class RcDeferredLayoutAnimationsTest {

  @Test
  fun aHostValueChangeStillAnimatesWhenTheLookaheadPassWasDeferred() {
    val heights = heightsAfterTheValueGrows(defer = true)
    assertEquals(SHORT, heights.first(), "the box changed before the change landed")
    assertTrue(heights.any { it in (SHORT + 1) until TALL }, "the box snapped: $heights")
    assertEquals(TALL, heights.last())
  }

  @Test
  fun withoutDeferringTheSameChangeAnimatesToo() {
    val heights = heightsAfterTheValueGrows(defer = false)
    assertTrue(heights.any { it in (SHORT + 1) until TALL }, "the box snapped: $heights")
    assertEquals(TALL, heights.last())
  }

  @Test
  fun aStaticDocumentDoesNotChangeItsLayoutOnItsOwn() {
    assertFalse(RcPreparedDocument(document).changesLayoutOnItsOwn)
  }

  /** The green box's height on each frame after the host grows `USER:h` from SHORT to TALL. */
  private fun heightsAfterTheValueGrows(defer: Boolean): List<Int> {
    val values = mutableStateMapOf<String, RcNamedValue>()
    val scene =
      ImageComposeScene(width = 40, height = 40, density = Density(1f)) {
        CompositionLocalProvider(LocalRcDeferLayoutAnimations provides defer) {
          RcComposePlayer(RcPreparedDocument(document), Modifier.size(40.dp), namedValues = values)
        }
      }
    try {
      var time = 0L
      scene.render(time)
      values["USER:h"] = RcNamedValue.FloatValue(TALL.toFloat())
      Snapshot.sendApplyNotifications()
      return List(40) {
        time += FRAME_NANOS
        val bitmap = Bitmap().apply { allocN32Pixels(40, 40) }
        check(scene.render(time).readPixels(bitmap))
        (0 until 40).count { y -> bitmap.getColor(20, y) == GREEN }
      }
    } finally {
      scene.close()
    }
  }

  private val document =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 40, modern = false),
      listOf(
        RcFloatConstant(HEIGHT, RcFloatWord.literal(SHORT.toFloat())),
        RcNamedVariable(HEIGHT, RcNamedVariable.FLOAT_TYPE, "USER:h"),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(3, -1, 1, 1, RcFloatWord.literal(0f)),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcLayoutContent(4),
        RcBoxLayout(5, -1, 1, 1),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord(0x7fc00000 or HEIGHT)),
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
    const val HEIGHT = 42
    const val SHORT = 10
    const val TALL = 30
    const val FRAME_NANOS = 16_000_000L
  }
}
