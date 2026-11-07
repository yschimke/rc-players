package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.AddMesh2D
import androidx.compose.remote.core.operations.DrawMesh2D
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.drawMesh2D
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemoteTimeVariables
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import ee.schimke.composeai.rcplayer.protocol.RcAddMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDrawMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * `drawMesh2D` writes `AddMesh2D` with each channel as the raw RPN of its `RemoteFloat` — `u` and
 * `v` as `VAR1` / `VAR2`, the clock as its id — followed by the `DrawMesh2D` that draws it, and
 * marks the document experimental so AndroidX's remote-core reads it.
 */
class Mesh2DAuthoringTest {
  private fun capture(): ByteArray = runBlocking {
    captureCommonRemoteDocument(RemoteCreationDisplayInfo(200, 100, 160)) {
      RemoteCanvas(RemoteModifier.size(200.rdp, 100.rdp)) {
        drawMesh2D(uCount = 3, vCount = 2) {
          xy(u * width, v * height)
          rgba(1f.rf - u, RemoteTimeVariables.animationTime.min(1f), u, 1f.rf)
        }
      }
    }
  }

  @Test
  fun theMeshIsWrittenWithItsChannelExpressions() {
    val document = RcDocumentCodec.decode(capture())
    val mesh = document.operations.filterIsInstance<RcAddMesh2D>().single()
    assertEquals(RcAddMesh2D.TYPE_EXPRESSION, mesh.type)
    assertEquals(RcAddMesh2D.LAYOUT_GRID, mesh.layout)
    assertEquals(3 to 2, mesh.uCount to mesh.vCount)
    // u and v stay the mesh's own variables, inlined, never hoisted into a float of their own.
    assertTrue(mesh.expression(RcAddMesh2D.EXP_X).any { it.payload() == Var1 })
    assertTrue(mesh.expression(RcAddMesh2D.EXP_Y).any { it.payload() == Var1 + 1 })
    assertTrue(mesh.expression(RcAddMesh2D.EXP_COLOR_R).any { it.payload() == Var1 })
    assertTrue(
      mesh.expression(RcAddMesh2D.EXP_COLOR_G).any { it.referencedId == AnimationTimeId },
      "green reads the clock: ${mesh.expression(RcAddMesh2D.EXP_COLOR_G)}",
    )
    assertEquals(listOf(1f), mesh.expression(RcAddMesh2D.EXP_COLOR_A).map { it.value })
    assertTrue(mesh.expression(RcAddMesh2D.EXP_TEX_U).isEmpty())
    val draw = document.operations.filterIsInstance<RcDrawMesh2D>().single()
    assertEquals(mesh.meshId, draw.meshId)
    assertEquals(RcDrawMesh2D.NO_IMAGE, draw.imageId)
    val profiles =
      document.header.properties.single { it.key == DocProfiles }.value as RcHeaderValue.IntValue
    assertEquals(0x201, profiles.value)
  }

  @Test
  fun androidxRemoteCoreReadsTheMesh() {
    val document = CoreDocument()
    document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(capture())))
    val operations = flatten(document.operations)
    assertNotNull(operations.filterIsInstance<AddMesh2D>().singleOrNull())
    assertNotNull(operations.filterIsInstance<DrawMesh2D>().singleOrNull())
  }

  private fun flatten(operations: List<Operation>): List<Operation> = operations.flatMap {
    listOf(it) + ((it as? Container)?.list?.let(::flatten) ?: emptyList())
  }

  private fun RcFloatWord.payload(): Int = bits and 0x7fffff

  private companion object {
    /** `AnimatedFloatExpression.VAR1`'s id. */
    const val Var1 = 0x310000 + 70

    /** `RemoteContext.ID_ANIMATION_TIME`. */
    const val AnimationTimeId = 30

    /** `Header.DOC_PROFILES`. */
    const val DocProfiles = 14
  }
}
