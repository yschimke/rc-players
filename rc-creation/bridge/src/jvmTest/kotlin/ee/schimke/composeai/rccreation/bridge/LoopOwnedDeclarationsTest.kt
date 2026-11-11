package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.operations.ColorExpression
import androidx.compose.remote.core.operations.PathData
import androidx.compose.remote.core.operations.ShaderData
import androidx.compose.remote.core.operations.TextFromFloat
import androidx.compose.remote.core.operations.TextMerge
import androidx.compose.remote.core.operations.layout.LoopOperation
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.path.RemotePath
import ee.schimke.composeai.rccreation.compose.shaders.RemoteRuntimeShader
import ee.schimke.composeai.rccreation.compose.state.DecimalFormat
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.lerp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whatever a loop's body declares from its index is declared in the loop, for every kind of
 * declaration, as `BodyOwnedDeclarationsTest` shows for a pattern and a for-each: AndroidX's own
 * reader (`remote-core` 1.0.0-alpha23) finds each among the loop's own operations, which are the
 * ones it runs again for each value of the index.
 */
class LoopOwnedDeclarationsTest {
  private fun assertInLoop(
    vararg kinds: KClass<out Operation>,
    body: RemoteDrawScope.(RemoteFloat) -> Unit,
  ) {
    val operations =
      androidxOperations(captureCanvasBytes { loop(0f.rf, 3f.rf, 1f.rf) { index -> body(index) } })
    val inLoop = operations.only<LoopOperation>().children().flattened()
    for (kind in kinds) {
      val count = inLoop.count { kind.isInstance(it) }
      assertTrue(count > 0, "no ${kind.simpleName} in the loop")
      assertEquals(
        count,
        operations.flattened().count { kind.isInstance(it) },
        "a ${kind.simpleName} outside the loop",
      )
    }
  }

  @Test
  fun aRuntimeShaderWithAUniformOfTheIndex() =
    assertInLoop(ShaderData::class) { index ->
      drawRect(
        RemotePaint {
          shader =
            RemoteRuntimeShader(
              "uniform float level; half4 main(float2 p) { return half4(level, 0, 0, 1); }",
              mapOf("level" to listOf(index * 0.5f)),
              emptyMap(),
            )
        },
      )
    }

  @Test
  fun aPathBuiltFromTheIndex() =
    assertInLoop(PathData::class) { index ->
      val x = index.getFloatIdForCreationState(remoteComposeCreationState)
      val path =
        RemotePath().apply {
          moveTo(0f, 0f)
          lineTo(x, 10f)
          lineTo(0f, 10f)
          close()
        }
      drawPath(path, RemotePaint { color = RemoteColor(Color.Red) })
    }

  @Test
  fun aTextFormattedAndMergedFromTheIndex() =
    assertInLoop(TextFromFloat::class, TextMerge::class) { index ->
      val text = "v=".rs + (index * 2f).toRemoteString(DecimalFormat())
      drawText(text, 10f.rf, 20f.rf, RemotePaint { color = RemoteColor(Color.Red) })
    }

  @Test
  fun aColourExpressionOfTheIndex() =
    assertInLoop(ColorExpression::class) { index ->
      val color = lerp(RemoteColor(Color.Red), RemoteColor(Color.Blue), index * 0.5f)
      drawRect(RemotePaint { this.color = color })
    }
}
