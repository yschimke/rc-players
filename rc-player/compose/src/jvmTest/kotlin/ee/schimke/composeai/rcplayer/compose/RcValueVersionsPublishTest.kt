package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
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

/**
 * A host value lands with one composition of the player's top level, on the frame it lands.
 *
 * The top level evaluates the document and publishes the values that moved ([RcValueVersions]).
 * Publishing bumps a version by reading it; read in the top level's composition, that read
 * subscribed the top level to every version it published, so each change recomposed the top level a
 * second time, on the next frame, with nothing left to do: a second wave on every update of every
 * card in a live feed.
 */
class RcValueVersionsPublishTest {

  @Test
  fun aHostValueRecomposesTheTopLevelOnce() {
    val namedValues = mutableStateMapOf<String, RcNamedValue>()
    val scene =
      ImageComposeScene(width = 60, height = 20, density = Density(1f)) {
        CompositionLocalProvider(LocalRcLayoutAnimations provides false) {
          RcComposePlayer(document(), namedValues = namedValues)
        }
      }
    try {
      var time = 0L
      repeat(3) {
        scene.render(time).close()
        time += FRAME_NANOS
      }
      repeat(3) { change ->
        val perFrame =
          countingCompositions(TOP_LEVEL) { count ->
            namedValues["USER:width"] = RcNamedValue.FloatValue(30f + change * 10f)
            Snapshot.sendApplyNotifications()
            List(6) {
              val before = count()
              scene.render(time).close()
              time += FRAME_NANOS
              count() - before
            }
          }
        assertEquals(1, perFrame.sum(), "change $change composed the top level: $perFrame")
      }
    } finally {
      scene.close()
    }
  }

  private fun document(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 20, modern = false),
      listOf(
        RcFloatConstant(WIDTH, RcFloatWord.literal(20f)),
        RcNamedVariable(WIDTH, RcNamedVariable.FLOAT_TYPE, "USER:width"),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(3, -1, 1, 1),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord(0x7fc00000 or WIDTH)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(10f)),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(1f),
          green = RcFloatWord.literal(0f),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(4),
        end,
        end,
        end,
        end,
      ),
    )
  }

  private companion object {
    const val TOP_LEVEL = "ee.schimke.composeai.rcplayer.compose.RcComposePlayerResolved"
    const val WIDTH = 42
    const val FRAME_NANOS = 16_000_000L
  }
}
