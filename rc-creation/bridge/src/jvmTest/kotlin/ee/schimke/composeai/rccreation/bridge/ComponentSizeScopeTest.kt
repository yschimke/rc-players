package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteImage
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberRemoteImageBitmap
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * A size a modifier reads (here a circle clip's radius, `min(width, height) / 2`) refers to the
 * last component written before the modifier, as AndroidX's `RemoteComposeBuffer.lastComponentId`
 * does: so the fork's documents draw the same shapes as AndroidX's.
 */
class ComponentSizeScopeTest {
  @Test
  fun sizesReferToTheLastComponentWrittenAsAndroidxDoes() = runBlocking {
    val bytes =
      captureCommonRemoteDocument(RemoteCreationDisplayInfo(360, 480, 160)) {
        RemoteRow {
          RemoteBox(RemoteModifier.size(40.rdp).clip(RemoteCircleShape)) {
            RemoteBox(RemoteModifier.size(10.rdp)) {}
          }
          RemoteBox(RemoteModifier.size(40.rdp).clip(RemoteCircleShape)) {}
          RemoteImage(rememberRemoteImageBitmap("https://example.test/a.png"), null)
          RemoteBox(RemoteModifier.size(40.rdp).clip(RemoteCircleShape)) {}
        }
      }
    val operations = RcDocumentCodec.decode(bytes).operations
    val expressions = operations.filterIsInstance<RcFloatExpression>().associateBy { it.id }
    val componentOfValue =
      operations.filterIsInstance<RcComponentValue>().associate { it.valueId to it.componentId }

    fun components(word: RcFloatWord): Set<Int> {
      val id = word.bits and 0x3fffff
      if (word.bits and 0x7f800000 != 0x7f800000 || word.bits and 0x7fffff == 0) return emptySet()
      componentOfValue[id]?.let {
        return setOf(it)
      }
      return expressions[id]?.expression.orEmpty().flatMap(::components).toSet()
    }

    var lastComponent = -1
    val expected = mutableListOf<Int>()
    val actual = mutableListOf<Set<Int>>()
    for (operation in operations) {
      when (operation) {
        is RcLayoutContent -> lastComponent = operation.componentId
        // An image is written with no content section: it is itself the last component.
        is RcImageLayout -> lastComponent = operation.componentId
        is RcRoundedClipRectModifier -> {
          expected += lastComponent
          actual += components(operation.topStart)
        }
        else -> {}
      }
    }
    assertEquals(3, actual.size)
    // The first clip reads the row's content, the second the first box's inner box's content and
    // the third the image, which has none.
    assertEquals(expected.map { setOf(it) }, actual)
  }
}
