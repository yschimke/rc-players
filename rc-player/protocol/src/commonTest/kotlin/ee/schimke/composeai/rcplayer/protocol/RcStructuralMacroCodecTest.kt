package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RcStructuralMacroCodecTest {
  private val header = RcHeader(RcVersion(0, 1, 0))

  @Test
  fun componentAndReferencedOperationHeadersRoundTrip() {
    val operations =
      listOf<RcOperation>(
        RcComponentStart(2, 41, RcFloatWord.literal(120f), RcFloatWord.literal(80f)),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcReferencedOperations(7),
        RcTheme(RcTheme.DARK),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcIncludeReferencedOperations(7),
      )
    val document = RcDocument(header, operations)

    assertEquals(document, RcDocumentCodec.decode(RcDocumentCodec.encode(document)))
  }

  @Test
  fun macroFamilyRoundTripsAndRejectsBadDefinitionLengths() {
    val body = byteArrayOf(RcOpcodes.THEME.toByte(), 0, 0, 0, RcTheme.LIGHT.toByte())
    val operation = RcMacroDefine(9, listOf(21, 22), body)
    val output = RcWireWriter()
    MacroDefineCodec.encode(output, operation)

    val decoded = MacroDefineCodec.decode(RcWireReader(output.toByteArray()))

    assertEquals(operation.id, decoded.id)
    assertEquals(operation.parameterIds, decoded.parameterIds)
    assertContentEquals(body, decoded.body)
    val document =
      RcDocument(
        header,
        listOf(
          operation,
          RcMacroCall(9, listOf(30, 31)),
          RcMacroBlock(0),
          RcMacroArgument(0),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcMacroForEach(40, 41),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
      )
    assertEquals(document, RcDocumentCodec.decode(RcDocumentCodec.encode(document)))
    assertFailsWith<RcWireException> {
      MacroDefineCodec.decode(
        RcWireReader(
          RcWireWriter()
            .apply {
              writeInt(1)
              writeInt(0)
              writeInt(-1)
            }
            .toByteArray()
        )
      )
    }
  }

  @Test
  fun skipConditionallyAdvancesAndIsNotRetained() {
    val tail =
      RcWireWriter()
        .apply { RcDocumentCodec.encodeOperation(this, RcTheme(RcTheme.LIGHT)) }
        .toByteArray()
    val bytes =
      RcWireWriter()
        .apply {
          writeU8(RcOpcodes.SKIP)
          writeInt(RcSkip.IF_API_EQUAL_TO)
          writeInt(8)
          writeInt(1)
          writeU8(255)
          writeRawBytes(tail)
        }
        .toByteArray()

    assertEquals(
      listOf(RcTheme(RcTheme.LIGHT)),
      RcDocumentCodec.decodeOperationsForReader(
        bytes,
        libraryApiLevel = 8,
        profile = RcWireProfiles.ANDROIDX_EXPERIMENTAL,
      ),
    )
    assertFailsWith<RcWireException> {
      RcDocumentCodec.decodeOperationsForReader(
        bytes,
        libraryApiLevel = 7,
        profile = RcWireProfiles.ANDROIDX_EXPERIMENTAL,
      )
    }

    val invalid = bytes.copyOf().also { it[12] = 0xff.toByte() }
    assertFailsWith<RcWireException> { RcDocumentCodec.decodeOperations(invalid) }
  }

  @Test
  fun allSkipConditionKindsUseReaderSystemInfo() {
    fun decode(
      condition: Int,
      value: Int,
      libraryApiLevel: Int = 8,
      profile: Int = RcWireProfiles.ANDROIDX_EXPERIMENTAL,
    ): List<RcOperation> {
      val bytes =
        RcWireWriter()
          .apply {
            writeU8(RcOpcodes.SKIP)
            writeInt(condition)
            writeInt(value)
            writeInt(1)
            writeU8(255)
          }
          .toByteArray()
      return RcDocumentCodec.decodeOperationsForReader(
        bytes,
        libraryApiLevel = libraryApiLevel,
        profile = profile,
      )
    }

    assertEquals(emptyList(), decode(RcSkip.IF_API_LESS_THAN, 8, libraryApiLevel = 7))
    assertEquals(emptyList(), decode(RcSkip.IF_API_GREATER_THAN, 8, libraryApiLevel = 9))
    assertEquals(emptyList(), decode(RcSkip.IF_API_EQUAL_TO, 8, libraryApiLevel = 8))
    assertEquals(emptyList(), decode(RcSkip.IF_API_NOT_EQUAL_TO, 8, libraryApiLevel = 7))
    assertEquals(
      emptyList(),
      decode(RcSkip.IF_PROFILE_INCLUDES, 0x200, profile = 0x201),
    )
    assertEquals(
      emptyList(),
      decode(RcSkip.IF_PROFILE_EXCLUDES, 0x200, profile = 0x001),
    )
  }
}
