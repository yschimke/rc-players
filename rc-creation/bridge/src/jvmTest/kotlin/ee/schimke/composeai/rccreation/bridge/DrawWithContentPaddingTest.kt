package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.drawWithContent
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.ui.unit.LayoutDirection
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcTransform2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * A `drawWithContent` after padding draws inside the padding, as AndroidX writes it: translated
 * past it with its scope's size reduced by it, its content drawn back at the component's origin.
 * Without padding before it, nothing extra is written.
 */
class DrawWithContentPaddingTest {
  private fun capture(
    modifier: RemoteModifier,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
  ): List<RcOperation> = runBlocking {
    val bytes =
      captureCommonRemoteDocument(
        RemoteCreationDisplayInfo(360, 480, 160),
        layoutDirection = layoutDirection,
      ) {
        RemoteBox(
          modifier.drawWithContent {
            drawRect(RemotePaint())
            drawContent()
          }
        ) {}
      }
    RcDocumentCodec.decode(bytes).operations
  }

  private fun canvasOperations(modifier: RemoteModifier): List<String> =
    capture(modifier).mapNotNull(::shape)

  private fun firstTranslate(modifier: RemoteModifier, layoutDirection: LayoutDirection) =
    capture(modifier, layoutDirection).filterIsInstance<RcTransform2>().first {
      it.opcode == TRANSLATE
    }

  private fun shape(operation: RcOperation): String? =
    when (operation) {
      is RcNoArg ->
        when (operation.opcode) {
          SAVE -> "save"
          RESTORE -> "restore"
          DRAW_CONTENT -> "content"
          else -> null
        }
      is RcTransform2 -> if (operation.opcode == TRANSLATE) "translate" else null
      is RcDraw4 -> "rect"
      else -> null
    }

  @Test
  fun aDrawAfterPaddingDrawsInsideIt() {
    assertEquals(
      listOf("save", "translate", "rect", "translate", "content", "translate", "restore"),
      canvasOperations(RemoteModifier.size(100.rdp).padding(6.rdp)),
    )
  }

  @Test
  fun aDrawWithNoPaddingBeforeItIsUnchanged() {
    assertEquals(
      listOf("rect", "content"),
      canvasOperations(RemoteModifier.size(100.rdp)),
    )
  }

  @Test
  fun chainedDrawsNestAroundOneContentMarker() {
    val shapes =
      canvasOperations(
        RemoteModifier.size(100.rdp).drawWithContent {
          drawRect(RemotePaint())
          drawContent()
        }
      )
    assertEquals(listOf("rect", "rect", "content"), shapes)
  }

  @Test
  fun aRightToLeftDrawIsInsetByTheEndPadding() {
    val rtl =
      firstTranslate(
        RemoteModifier.size(100.rdp).padding(start = 6.rdp, end = 20.rdp),
        LayoutDirection.Rtl,
      )
    val mirroredLtr =
      firstTranslate(
        RemoteModifier.size(100.rdp).padding(start = 20.rdp, end = 6.rdp),
        LayoutDirection.Ltr,
      )
    assertEquals(mirroredLtr, rtl)
  }

  private companion object {
    const val TRANSLATE = 127
    const val SAVE = 130
    const val RESTORE = 131
    const val DRAW_CONTENT = 139
  }
}
