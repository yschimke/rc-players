package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.edit.RcProfileViolation.Kind
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPlaySound
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RcOperationRewriteTest {
  private val hello = RcTextData(1, "hello")
  private val world = RcTextData(2, "world")

  @Test
  fun keepingEverythingReturnsTheSameDocument() {
    val doc = document(*rootLayout(*box(10, hello).toTypedArray()).toTypedArray())
    assertSame(doc, doc.rewriteOperations { _, _ -> RcRewrite.Keep })
  }

  @Test
  fun aRemovedContainerTakesItsBodyWithIt() {
    val doc =
      document(
        *rootLayout(*box(10, hello).toTypedArray(), *box(20, world).toTypedArray()).toTypedArray()
      )
    val rewritten = doc.rewriteOperations { _, operation ->
      if (
        operation is ee.schimke.composeai.rcplayer.protocol.RcBoxLayout &&
          operation.componentId == 10
      )
        RcRewrite.Remove
      else RcRewrite.Keep
    }
    assertEquals(document(*rootLayout(*box(20, world).toTypedArray()).toTypedArray()), rewritten)
    assertRoundTrips(rewritten)
  }

  @Test
  fun aReplacementTakesTheOperationsPlace() {
    val doc = document(*rootLayout(*box(10, hello).toTypedArray()).toTypedArray())
    val rewritten = doc.rewriteOperations { _, operation ->
      if (operation == hello) RcRewrite.Replace(world) else RcRewrite.Keep
    }
    assertEquals(document(*rootLayout(*box(10, world).toTypedArray()).toTypedArray()), rewritten)
  }

  @Test
  fun anUnbalancedDocumentIsLeftAlone() {
    val doc = document(*box(10, hello).dropLast(1).toTypedArray())
    assertSame(doc, doc.rewriteOperations { _, _ -> RcRewrite.Remove })
  }

  @Test
  fun adaptingCanDropWhatTheTargetCannotPlayButNeverDraws() {
    // Android 16's launcher player has no sound; a sound draws nothing, so the widget looks the
    // same.
    val doc = document(*rootLayout(hello, RcPlaySound(5)).toTypedArray())
    val target = RcTargetProfiles.WIDGETS_V6
    val silentOpcodes = setOf(RcOpcodes.PLAY_SOUND)
    check(silentOpcodes.none { it in target.supportedOpcodes })

    // By default an operation the target lacks stays, and is reported.
    val kept = doc.adaptTo(target, emptySet(), RcDisplay.DEFAULT)
    assertTrue(
      kept.violations.any { it.kind == Kind.UNSUPPORTED_OPERATION && it.opcode in silentOpcodes }
    )

    val dropped =
      doc.adaptTo(target, emptySet(), RcDisplay.DEFAULT, RcUnsupportedOperations.dropSilent)
    assertTrue(dropped.document.operations.none { it.opcode in silentOpcodes })
    assertTrue(dropped.violations.none { it.opcode in silentOpcodes })
    assertTrue(hello in dropped.document.operations)
  }

  @Test
  fun aPolicyCanReplaceAnUnsupportedOperation() {
    val doc = document(*rootLayout(RcPlaySound(5)).toTypedArray())
    val adapted =
      doc.adaptTo(RcTargetProfiles.WIDGETS_V6, emptySet(), RcDisplay.DEFAULT) {
        RcRewrite.Replace(world)
      }
    assertTrue(world in adapted.document.operations)
    assertTrue(adapted.document.operations.none { it.opcode == RcOpcodes.PLAY_SOUND })
  }
}
