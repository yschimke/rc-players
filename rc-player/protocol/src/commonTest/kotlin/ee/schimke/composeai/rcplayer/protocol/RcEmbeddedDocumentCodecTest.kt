package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcEmbeddedDocumentCodecTest {

  @Test
  fun binaryDataRoundTrips() {
    val payload = byteArrayOf(1, 2, 3, 4, 5, 6)
    val doc =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 100),
        operations =
          listOf(
            RcBinaryData(id = 42, type = RcBinaryData.TYPE_RAW, flags = 0, data = payload),
            RcBinaryData(id = 43, type = RcBinaryData.TYPE_RC_DOC, flags = 7, data = byteArrayOf()),
          ),
      )

    val decoded = RcDocumentCodec.decode(RcDocumentCodec.encode(doc))
    assertEquals(doc, decoded)
    val first = decoded.operations[0] as RcBinaryData
    assertEquals(42, first.id)
    assertEquals(RcBinaryData.TYPE_RAW, first.type)
    assertEquals(0, first.flags)
    assertContentEquals(payload, first.data)
  }

  @Test
  fun binaryDataRejectsPayloadAboveLimit() {
    val oversized = ByteArray(RcBinaryData.MAX_BYTES + 1)
    val doc =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 100),
        operations =
          listOf(
            RcBinaryData(id = 42, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = oversized),
          ),
      )
    assertFailsWith<IllegalArgumentException> { RcDocumentCodec.encode(doc) }
  }

  @Test
  fun rcDocLayoutAndMapVariablesRoundTrip() {
    val doc =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 120),
        operations =
          listOf(
            RcTextData(id = 42, text = "card://child"),
            RcTextData(id = 43, text = "USER:title"),
            RcTextData(id = 44, text = "Hello"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 42),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(180f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcMapRcDocVariables(
              docId = 10,
              mode = RcMapRcDocVariables.MAP_FORWARD,
              localId = 44,
              remoteTarget = 43,
            ),
            RcMapRcDocVariables(
              docId = 10,
              mode = RcMapRcDocVariables.MAP_REVERSE,
              localId = 45,
              remoteTarget = 43,
            ),
            RcMapRcDocVariables(
              docId = 10,
              mode = RcMapRcDocVariables.MAP_BIDIRECTIONAL,
              localId = 46,
              remoteTarget = 43,
            ),
            RcMapRcDocVariables(
              docId = 10,
              mode = RcMapRcDocVariables.MAP_HOST_ACTION,
              localId = 47,
              remoteTarget = 999,
            ),
          ),
      )

    val decoded = RcDocumentCodec.decode(RcDocumentCodec.encode(doc))
    assertEquals(doc, decoded)
    assertTrue(RcOpcodes.LAYOUT_RC_DOC in RcOpcodes.CONTAINER_STARTS)
  }

  @Test
  fun inventoryAndProfilesClassifyEmbeddedDocOpsAsExperimental() {
    val opcodes =
      listOf(RcOpcodes.BINARY_DATA, RcOpcodes.LAYOUT_RC_DOC, RcOpcodes.MAP_RC_DOC_VARIABLES)
    opcodes.forEach { opcode ->
      val entry = RcOperationInventory.byOpcode[opcode]
      assertEquals(RcOperationStatus.IMPLEMENTED, entry?.status, "opcode $opcode status")
      assertFalse(
        RcOperationProfiles.ANDROIDX_JAVA_ALPHA18.supports(opcode),
        "baseline alpha18 should not advertise experimental opcode $opcode",
      )
      assertTrue(
        RcOperationProfiles.ANDROIDX_EMBEDDED_EXPERIMENTAL_ALPHA18.supports(opcode),
        "experimental profile should include opcode $opcode",
      )
      assertTrue(
        RcOperationProfiles.CMP_DESKTOP_ALPHA18.supports(opcode),
        "CMP profile should include opcode $opcode",
      )
    }
  }
}
