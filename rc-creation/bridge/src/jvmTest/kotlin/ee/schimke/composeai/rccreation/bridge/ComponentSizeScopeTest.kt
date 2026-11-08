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
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * A size a modifier reads (here a circle clip's radius, `min(width, height) / 2`) refers to the
 * component the modifier is on, as AndroidX's do since androidx-main `ab1b76a2f` ("Scope
 * RemoteComponent to container and state scope"): not to the component written before it, which for
 * a box's first modifier is its preceding sibling (or its parent's content).
 */
class ComponentSizeScopeTest {
  @Test
  fun sizesReferToTheClippedComponentItself() = runBlocking {
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

    // A box's modifiers follow its `BoxLayout` operation, before its content section.
    var currentBox = -1
    val expected = mutableListOf<Int>()
    val actual = mutableListOf<Set<Int>>()
    for (operation in operations) {
      when (operation) {
        is RcBoxLayout -> currentBox = operation.componentId
        is RcRoundedClipRectModifier -> {
          expected += currentBox
          actual += components(operation.topStart)
        }
        else -> {}
      }
    }
    assertEquals(3, actual.size)
    // Each 40dp box clips to a circle whose radius is read from its own size.
    assertEquals(3, expected.toSet().size)
    assertEquals(expected.map { setOf(it) }, actual)
  }
}
