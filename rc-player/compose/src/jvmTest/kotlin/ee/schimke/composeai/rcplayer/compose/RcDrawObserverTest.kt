package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcDrawTextOnPath
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals

class RcDrawObserverTest {
  @Test
  fun anAnchoredRunIsReportedWithItsOrigin() {
    // The baseline origin a `DrawTextAnchored` resolves to leaves nothing behind once painted;
    // the observer is how an inspector learns it. Anchored left (pan -1) on its baseline, the
    // origin
    // is the anchor itself.
    val runs = mutableListOf<RcTextRun>()
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 30, modern = false),
        listOf(
          RcTextData(10, "Hi"),
          RcDrawTextAnchored(
            10,
            RcFloatWord.literal(12f),
            RcFloatWord.literal(20f),
            RcFloatWord.literal(-1f),
            RcFloatWord.literal(0f),
            RcDrawTextAnchored.BASELINE_RELATIVE,
          ),
        ),
      )
    val scene =
      ImageComposeScene(width = 60, height = 30, density = Density(1f)) {
        CompositionLocalProvider(LocalRcDrawObserver provides RcDrawObserver { runs += it }) {
          RcComposePlayer(document, Modifier.fillMaxSize())
        }
      }
    try {
      scene.render()
    } finally {
      scene.close()
    }

    val run = runs.last()
    assertEquals("Hi", run.text)
    assertEquals(2, run.glyphCount)
    assertEquals(20f, run.originY, 0.01f)
  }

  @Test
  fun pathTextThatRunsOffItsPathStillReportsTheGlyphsItDrew() {
    // A 30 px path cannot hold the whole string. The glyphs that fit are drawn, and the observer
    // must hear about them even though the walk stops early.
    val runs = mutableListOf<RcTextRun>()
    val command = { it: Int -> RcFloatWord(0x7fc00000 or it) }
    val pad = RcFloatWord.literal(0f)
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 60, legacyHeight = 30, modern = false),
        listOf(
          RcTextData(10, "Hello world, this is long"),
          RcPathData(
            20,
            listOf(
              command(RcPathCommands.MOVE),
              RcFloatWord.literal(0f),
              RcFloatWord.literal(20f),
              command(RcPathCommands.LINE),
              pad,
              pad,
              RcFloatWord.literal(30f),
              RcFloatWord.literal(20f),
              command(RcPathCommands.DONE),
            ),
          ),
          RcDrawTextOnPath(10, 20, RcFloatWord.literal(0f), RcFloatWord.literal(0f)),
        ),
      )
    val scene =
      ImageComposeScene(width = 60, height = 30, density = Density(1f)) {
        CompositionLocalProvider(LocalRcDrawObserver provides RcDrawObserver { runs += it }) {
          RcComposePlayer(document, Modifier.fillMaxSize())
        }
      }
    try {
      scene.render()
    } finally {
      scene.close()
    }

    val run = runs.last()
    check(run.glyphCount in 1 until 25) { "expected a partial run, got ${run.glyphCount} glyphs" }
    assertEquals(run.glyphCount, run.glyphs.size)
  }
}
