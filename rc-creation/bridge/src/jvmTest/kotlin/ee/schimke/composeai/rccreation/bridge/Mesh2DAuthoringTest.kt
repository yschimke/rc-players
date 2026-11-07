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
import androidx.compose.remote.creation.compose.state.cos
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.sin
import ee.schimke.composeai.rcplayer.protocol.RcAddMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDrawMesh2D
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * `drawMesh2D` writes `AddMesh2D` with each channel as RPN — `u` and `v` inline as `VAR1` / `VAR2`,
 * what reads neither as a reference to an ordinary float — followed by the `DrawMesh2D` that draws
 * it, and marks the document experimental so AndroidX's remote-core reads it.
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
    // Green reads only the clock, so it is a reference to an ordinary float over it: evaluated once
    // a frame, not once a vertex.
    val green = mesh.expression(RcAddMesh2D.EXP_COLOR_G).single().referencedId
    val clock = document.operations.filterIsInstance<RcFloatExpression>().single { it.id == green }
    assertTrue(
      clock.expression.any { it.referencedId == AnimationTimeId },
      "green reads the clock: $clock",
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

  /**
   * A `u`-reading sub-expression used in two channels, and twice in one, with the clock mixed in
   * and channels long enough that the ordinary lowering would hoist operands into floats of their
   * own. Every `u`/`v` read stays inside the mesh; the clock-only parts become references.
   */
  private fun captureShared(): ByteArray = runBlocking {
    captureCommonRemoteDocument(RemoteCreationDisplayInfo(360, 200, 160)) {
      RemoteCanvas(RemoteModifier.size(360.rdp, 200.rdp)) {
        drawMesh2D(uCount = 6, vCount = 4) {
          val t = RemoteTimeVariables.animationTime * 0.6f
          xy(u * width, v * height)
          val a = sin(u * 3f + t) * 0.5f + 0.5f
          val b = cos(v * 2.5f - t * 0.7f) * 0.5f + 0.5f
          rgba(0.50f.rf + a * 0.40f, 0.32f.rf - a * 0.25f + b * 0.15f, 1f.rf - b * 0.6f, a * a)
        }
      }
    }
  }

  @Test
  fun aSharedUvSubExpressionIsInlinedIntoEveryChannel() {
    val document = RcDocumentCodec.decode(captureShared())
    val leaks =
      document.operations.filterIsInstance<RcFloatExpression>().filter { expression ->
        (expression.expression + expression.animation.orEmpty()).any { it.payload() in MeshVars }
      }
    assertTrue(leaks.isEmpty(), "floats that read u or v outside the mesh: $leaks")
    val mesh = document.operations.filterIsInstance<RcAddMesh2D>().single()
    for (group in
      listOf(RcAddMesh2D.EXP_COLOR_R, RcAddMesh2D.EXP_COLOR_G, RcAddMesh2D.EXP_COLOR_A)) {
      val channel = mesh.expression(group)
      assertTrue(channel.any { it.payload() == Var1 }, "channel $group reads u inline: $channel")
      assertTrue(channel.size <= 32)
    }
    assertTrue(mesh.expression(RcAddMesh2D.EXP_COLOR_B).any { it.payload() == Var1 + 1 })
    // u is read twice in alpha (a * a), inline both times.
    assertEquals(2, mesh.expression(RcAddMesh2D.EXP_COLOR_A).count { it.payload() == Var1 })
    // AndroidX reads it too.
    val core = CoreDocument()
    core.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(captureShared())))
    assertNotNull(flatten(core.operations).filterIsInstance<AddMesh2D>().singleOrNull())
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

    /** VAR1..VAR3. */
    val MeshVars = setOf(Var1, Var1 + 1, Var1 + 2)

    /** `RemoteContext.ID_ANIMATION_TIME`. */
    const val AnimationTimeId = 30

    /** `Header.DOC_PROFILES`. */
    const val DocProfiles = 14
  }
}
