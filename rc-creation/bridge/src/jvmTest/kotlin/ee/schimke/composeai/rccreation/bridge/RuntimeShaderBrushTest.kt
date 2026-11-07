package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.ShaderData
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.shaders.RemoteBrush
import androidx.compose.remote.creation.compose.shaders.runtimeShader
import androidx.compose.remote.creation.compose.state.RemoteTimeVariables
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * `RemoteBrush.runtimeShader` writes AGSL as `TextData` plus `ShaderData`, with its float uniforms
 * as the variables the player resolves every frame, and sets it as the paint's shader.
 */
class RuntimeShaderBrushTest {
  private fun capture(): ByteArray = runBlocking {
    captureCommonRemoteDocument(RemoteCreationDisplayInfo(200, 100, 160)) {
      RemoteBox(
        RemoteModifier.size(200.rdp, 100.rdp)
          .background(
            RemoteBrush.runtimeShader(
              Source,
              floatUniforms =
                mapOf(
                  "time" to listOf(RemoteTimeVariables.animationTime),
                  "tint" to listOf(0.25f.rf, 0.5f.rf, 0.75f.rf, 1f.rf),
                ),
            ),
          ),
      ) {}
    }
  }

  @Test
  fun theShaderIsWrittenWithItsUniforms() {
    val operations = RcDocumentCodec.decode(capture()).operations
    val shader = operations.filterIsInstance<RcShaderData>().single()
    val source = operations.filterIsInstance<RcTextData>().single { it.id == shader.shaderTextId }
    assertEquals(Source, source.text)
    assertEquals(setOf("time", "tint", "size"), shader.floatUniforms.keys)
    // animationTime stays a variable for the player to resolve; the constants are written as is.
    assertEquals(AnimationTimeId, shader.floatUniforms.getValue("time").single().referencedId)
    assertEquals(
      listOf(0.25f, 0.5f, 0.75f, 1f),
      shader.floatUniforms.getValue("tint").map { it.value },
    )
    assertEquals(2, shader.floatUniforms.getValue("size").size)
    // The paint that fills the background uses it.
    assertTrue(
      operations.filterIsInstance<RcPaintData>().any { paint ->
        paint.words.windowed(2).any { (key, value) ->
          key and 0xffff == PaintShader && value == shader.shaderId
        }
      },
    )
  }

  @Test
  fun androidxRemoteCoreReadsTheShader() {
    val document = CoreDocument()
    document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(capture())))
    assertNotNull(document.operations.filterIsInstance<ShaderData>().singleOrNull())
  }

  private companion object {
    const val Source =
      "uniform float time; uniform float4 tint; uniform float2 size;" +
        " half4 main(float2 p) { return half4(tint.rgb * (0.5 + 0.5 * sin(time)), 1); }"

    /** `RemoteContext.ID_ANIMATION_TIME`. */
    const val AnimationTimeId = 30

    /** `PaintBundle.SHADER`. */
    const val PaintShader = 9
  }
}
