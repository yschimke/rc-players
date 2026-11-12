package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.shaders.RemoteRuntimeShader
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A runtime shader whose uniform is computed from a pattern's parameter, or a for-each's item, is
 * each call's and each item's own: the shader is declared in the body the player expands, so one
 * call paints red and the next blue.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcPatternShaderPlaybackTest {
  /** Host values, so that nothing is folded at capture. */
  private val zero
    get() = RemoteFloat.createNamedRemoteFloat("zero", 0f)

  private val one
    get() = RemoteFloat.createNamedRemoteFloat("one", 1f)

  /** A 20-pixel square at ([x], 10): red by [level], blue by what is left. */
  private fun RemoteDrawScope.square(x: RemoteFloat, level: RemoteFloat) {
    drawRect(
      RemotePaint {
        shader =
          RemoteRuntimeShader(
            "uniform float level; half4 main(float2 p) { return half4(level, 0, 1 - level, 1); }",
            mapOf("level" to listOf(level * 2f - level)),
            emptyMap(),
          )
      },
      RemoteOffset(x, 10f.rf),
      RemoteSize(20f.rf, 20f.rf),
    )
  }

  private fun play(onDraw: RemoteDrawScope.() -> Unit): PixelMap {
    val bytes = runBlocking {
      captureCommonRemoteDocument(
        RemoteCreationDisplayInfo(100, 40, 160),
        profile = RcEmbeddedPlayerProfile,
      ) {
        RemoteCanvas(RemoteModifier.fillMaxSize(), onDraw)
      }
    }
    val document = RcDocumentCodec.decode(bytes)
    lateinit var pixels: PixelMap
    runSkikoComposeUiTest(Size(100f, 40f), Density(1f)) {
      setContent { RcComposePlayer(document, Modifier.fillMaxSize()) }
      waitForIdle()
      pixels = onRoot().captureToImage().toPixelMap()
    }
    return pixels
  }

  private fun PixelMap.assertBlueThenRed() {
    val first = this[20, 20]
    val second = this[70, 20]
    assertTrue(first.blue > 0.95f && first.red < 0.05f, "the first is blue: $first")
    assertTrue(second.red > 0.95f && second.blue < 0.05f, "the second is red: $second")
  }

  @Test
  fun eachCallOfAPatternPaintsWithItsOwnUniform() {
    play {
      val square =
        remoteCanvas.definePattern<RemoteFloat> { level -> square(level * 50f + 10f, level) }
      square(zero)
      square(one)
    }
      .assertBlueThenRed()
  }

  @Test
  fun eachItemOfAForEachPaintsWithItsOwnUniform() {
    play { remoteCanvas.forEach(listOf(zero, one)) { level -> square(level * 50f + 10f, level) } }
      .assertBlueThenRed()
  }
}
