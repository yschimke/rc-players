package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcImpulseStart
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPlaySound
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `PlaySound` is a paint-list operation as well as an action: AndroidX applies it wherever it
 * appears, and an impulse is the reference engine's way to play a sound at a time or after a touch
 * (a click sets a float, an impulse keyed on it plays). The draw loop used to drop it silently.
 */
class RcImpulseSoundRenderTest {
  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun impulseInitializationPlaysItsSoundOnce() =
    runSkikoComposeUiTest(size = Size(20f, 20f), density = Density(1f)) {
      mainClock.autoAdvance = false
      val calls = mutableListOf<String>()
      val host =
        object : RcSoundHost {
          override fun loadSound(soundId: Int, data: ByteArray) {
            calls += "load:$soundId"
          }

          override fun playSound(soundId: Int) {
            calls += "play:$soundId"
          }
        }
      val end = RcNoArg(RcOpcodes.CONTAINER_END)
      val document =
        RcDocument(
          RcHeader(RcVersion(1, 0, 0), legacyWidth = 20, legacyHeight = 20, modern = false),
          listOf(
            RcRootLayout(1),
            RcLayoutContent(2),
            RcCanvasLayout(3, 30),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
            RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
            RcImpulseStart(RcFloatWord.literal(1f), RcFloatWord.literal(0f)),
            RcPlaySound(42),
            end,
            end,
            end,
            end,
            end,
          ),
        )
      setContent {
        CompositionLocalProvider(LocalRcSoundHost provides host) { RcComposePlayer(document) }
      }
      mainClock.advanceTimeByFrame()
      waitForIdle()
      assertEquals(listOf("play:42"), calls.filter { it.startsWith("play:") })

      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()
      waitForIdle()
      assertEquals(1, calls.count { it == "play:42" })
    }
}
