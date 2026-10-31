package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.edit.RcProfileViolation.Kind
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcRecursiveDrawContentTest {
  private fun widget(vararg operations: RcOperation) =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 100, legacyCapabilities = 0),
      operations.toList(),
    )

  private fun RcDocument.recursive() =
    validateFor(RcTargetProfiles.WIDGETS_V6).filter { it.kind == Kind.RECURSIVE_DRAW_CONTENT }

  /** What `drawWithContent { …; drawContent() }` writes into a component's content. */
  private val drawWithContent =
    widget(
      RcNoArg(RcOpcodes.CANVAS_OPERATIONS),
      RcNoArg(RcOpcodes.DRAW_CONTENT),
      RcNoArg(RcOpcodes.CONTAINER_END),
    )

  @Test
  fun drawContentInsideCanvasOperationsIsReportedOnAndroid16() {
    val violations = drawWithContent.recursive()
    assertEquals(1, violations.size)
    assertEquals(1, violations.single().operationIndex)
    assertEquals(RcOpcodes.DRAW_CONTENT, violations.single().opcode)
  }

  @Test
  fun laterPlayersAreNotAffected() {
    assertTrue(
      drawWithContent.validateFor(RcTargetProfiles.WIDGETS_V7).none {
        it.kind == Kind.RECURSIVE_DRAW_CONTENT
      }
    )
  }

  @Test
  fun drawContentOutsideCanvasOperationsIsNotReported() {
    assertTrue(widget(RcNoArg(RcOpcodes.DRAW_CONTENT)).recursive().isEmpty())
  }

  @Test
  fun violationsStayInDocumentOrder() {
    val indices =
      drawWithContent.validateFor(RcTargetProfiles.WIDGETS_V6).map { it.operationIndex ?: -1 }
    assertEquals(indices.sorted(), indices)
  }
}
