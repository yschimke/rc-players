package ee.schimke.composeai.rccreation.bridge

import ee.schimke.composeai.rccreation.common.RemoteContext
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.horizontalScroll
import ee.schimke.composeai.rccreation.compose.modifier.rememberRemoteScrollState
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.modifier.verticalScroll
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * `verticalScroll` and `horizontalScroll` write the wire's direction: 0 for vertical and 1 for
 * horizontal, `remote-creation-core`'s `ScrollModifier.VERTICAL` / `HORIZONTAL`, which
 * `ScrollModifierOperation` and this repository's player both read that way. The touch expression
 * that drives the position reads the matching axis. The port had the two constants the other way
 * round, so a vertical scroll was written, and played, as a horizontal one.
 */
class ScrollDirectionTest {
  private fun capture(vertical: Boolean): RcDocument = runBlocking {
    RcDocumentCodec.decode(
      captureCommonRemoteDocument(RemoteCreationDisplayInfo(200, 200, 160)) {
        val state = rememberRemoteScrollState()
        val scroll =
          if (vertical) RemoteModifier.verticalScroll(state)
          else RemoteModifier.horizontalScroll(state)
        RemoteBox(RemoteModifier.size(100.rdp).then(scroll)) {
          RemoteBox(RemoteModifier.size(400.rdp)) {}
        }
      },
    )
  }

  private fun RcDocument.touchWords(): Set<Int> =
    operations
      .filterIsInstance<RcTouchExpression>()
      .flatMap { touch -> touch.expression.map { it.bits } }
      .toSet()

  @Test
  fun aVerticalScrollIsWrittenAsVerticalAndFollowsTheYAxis() {
    val document = capture(vertical = true)
    val scroll = document.operations.filterIsInstance<RcScrollModifier>().single()
    assertEquals(RcScrollModifier.VERTICAL, scroll.direction)
    assertTrue(RemoteContext.FLOAT_TOUCH_POS_Y.toRawBits() in document.touchWords())
  }

  @Test
  fun aHorizontalScrollIsWrittenAsHorizontalAndFollowsTheXAxis() {
    val document = capture(vertical = false)
    val scroll = document.operations.filterIsInstance<RcScrollModifier>().single()
    assertEquals(RcScrollModifier.HORIZONTAL, scroll.direction)
    assertTrue(RemoteContext.FLOAT_TOUCH_POS_X.toRawBits() in document.touchWords())
  }
}
