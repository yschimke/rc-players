package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.*

class RcInputDocumentTest {
  @Test
  fun formUsesTypedChannelsAndSurvivesWireRoundTrip() {
    val document =
      rcInputDocument(
        listOf(
          RcInputField("room", "Contact / Room", "Kitchen", listOf("Kitchen", "Living room")),
          RcInputField(
            "message",
            "Message",
            "Hello 🏡\nRoom",
            multiline = true,
            submit = "configured-action",
          ),
        )
      )
    assertEquals(document, RcDocumentCodec.decode(RcDocumentCodec.encode(document)))
    assertEquals(RcVersion(1, 2, 0), document.header.version)
    assertEquals(
      RcHeaderValue.IntValue(RcWireProfiles.ANDROIDX_EXPERIMENTAL),
      document.header.properties.single { it.key == 14 /* Header.DOC_PROFILES */ }.value,
    )
    val components = document.operations.filterIsInstance<RcCustomLayout>()
    assertEquals(2, components.size)
    components.forEach { component ->
      val read = component.properties.single { it.type == RcInput.VALUE }
      val write = component.properties.single { it.type == RcInput.VALUE_RETURN }
      assertEquals(RcCustomProperty.STRING_PROP, read.dataType)
      assertEquals(RcCustomProperty.TEXT_RETURN, write.dataType)
      assertEquals(read.intValue, write.intValue)
    }
  }

  @Test
  fun duplicateKeysAndEmbeddedChoiceNewlinesAreRejected() {
    assertFailsWith<IllegalArgumentException> {
      rcInputDocument(listOf(RcInputField("same", "A"), RcInputField("same", "B")))
    }
    assertFailsWith<IllegalArgumentException> {
      rcInputDocument(listOf(RcInputField("room", "Room", options = listOf("not\na choice"))))
    }
  }
}
