package ee.schimke.composeai.rcplayer.embedded

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.shaders.RemoteBrush
import ee.schimke.composeai.rccreation.compose.shaders.runtimeShader
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An AGSL shader authored with `RemoteBrush.runtimeShader` plays in the CMP player: its float
 * uniforms arrive, and so does the painted area's size.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcRuntimeShaderPlaybackTest {
  private val display = RemoteCreationDisplayInfo(100, 100, 160)

  private fun run(block: SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(Size(100f, 100f), Density(1f), block = block)

  @Test
  fun aRuntimeShaderGetsItsUniformsAndTheSize() = run {
    val documents = mutableListOf<RcDocument>()
    setContent {
      RcEmbeddedPlayer(display, Modifier.size(100.dp), onDocument = { documents += it }) {
        RemoteBox(
          RemoteModifier.fillMaxSize()
            .background(
              RemoteBrush.runtimeShader(
                // Red from the uniform; blue from where the pixel sits across the size.
                "uniform float red; uniform float2 size;" +
                  " half4 main(float2 p) { return half4(red, 0, p.x / size.x, 1); }",
                floatUniforms = mapOf("red" to listOf(1f.rf)),
              ),
            ),
        )
      }
    }
    waitUntil(timeoutMillis = 5_000) { documents.isNotEmpty() }
    waitForIdle()
    val pixels = onRoot().captureToImage().toPixelMap()
    val left = pixels[10, 50]
    val right = pixels[90, 50]
    assertTrue(abs(left.red - 1f) < 0.02f && left.green < 0.02f, "left $left")
    assertTrue(abs(left.blue - 0.1f) < 0.05f, "left $left")
    assertTrue(abs(right.blue - 0.9f) < 0.05f, "right $right")
  }
}
