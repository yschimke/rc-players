@file:OptIn(ExperimentalRcCreationApi::class)

package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.capture.*
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.*
import androidx.compose.ui.unit.*
import ee.schimke.composeai.rcplayer.protocol.*
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class RemoteInputTest {
  private val display = RemoteCreationDisplayInfo(360, 480, 160)

  @Test
  fun publishedCreationHelperCapturesEveryControlWithTypedReturnBindings() = runBlocking {
    val fields =
      listOf(
        RcInputs.text("message", "Message"),
        RcInputs.choice("room", "Room", listOf("Kitchen"), "Kitchen"),
        RcInputs.toggle("urgent", "Urgent"),
        RcInputs.number("temperature", "Temperature", 21.5),
        RcInputs.range("brightness", "Brightness", 50.0),
      )
    val bytes =
      captureCommonRemoteDocument(display) {
        RemoteColumn {
          fields.forEach { field ->
            RemoteInput(field, field.value.rs, RemoteModifier.size(80.rdp))
          }
        }
      }
    val doc = RcDocumentCodec.decode(bytes)
    assertEquals(RcVersion(1, 2, 0), doc.header.version)
    assertEquals(
      RcHeaderValue.IntValue(RcWireProfiles.ANDROIDX_EXPERIMENTAL),
      doc.header.properties.single { it.key == 14 }.value,
    )
    val controls = doc.operations.filterIsInstance<RcCustomLayout>()
    assertEquals(5, controls.size)
    controls.forEach { control ->
      val value = control.properties.single { it.type == RcInput.VALUE }
      val returned = control.properties.single { it.type == RcInput.VALUE_RETURN }
      assertEquals(RcCustomProperty.STRING_PROP, value.dataType)
      assertEquals(RcCustomProperty.TEXT_RETURN, returned.dataType)
      assertEquals(value.intValue, returned.intValue)
    }
  }

  @Test
  fun unsupportedWriterCallsFallbackWithoutCreatingDeadControls() = runBlocking {
    var fallback = false
    val bytes =
      captureCommonRemoteDocument(display, supportedOperations = setOf(RcOpcodes.DATA_TEXT)) {
        RemoteInput(RcInputs.text("message", "Message"), "".rs, fallback = { fallback = true })
      }
    assertTrue(fallback)
    assertTrue(RcDocumentCodec.decode(bytes).operations.none { it is RcCustomLayout })
  }
}
