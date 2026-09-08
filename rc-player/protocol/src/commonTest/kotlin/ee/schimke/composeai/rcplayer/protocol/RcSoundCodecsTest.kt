package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RcSoundCodecsTest {
  @Test
  fun allSoundOperationsRoundTripRawFloatWordsAndBytes() {
    val operations =
      listOf(
        RcSoundData(42, byteArrayOf(0x53, 0x43, 1, 16)),
        RcSoundExpression(
          id = 43,
          leftVolume = RcFloatWord(0x7fc0002a),
          rightVolume = RcFloatWord.literal(0.75f),
          rate = RcFloatWord.literal(1f),
          parameters =
            listOf(
              RcSoundExpression.TYPE_TONE_WORD,
              RcFloatWord.literal(440f),
              RcFloatWord.literal(0.1f),
              RcFloatWord.literal(RcSoundExpression.WAVEFORM_SINE.toFloat()),
            ),
        ),
        RcPlaySound(43),
      )
    val document = RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), operations)

    val encoded = RcDocumentCodec.encode(document)
    val decoded = RcDocumentCodec.decode(encoded)

    assertEquals(document, decoded)
    assertContentEquals(encoded, RcDocumentCodec.encode(decoded))
  }

  @Test
  fun playSoundHasTheReleasedAlpha16FiveByteEncoding() {
    val output = RcWireWriter()

    RcDocumentCodec.encodeOperation(output, RcPlaySound(0x01020304))

    assertContentEquals(byteArrayOf(141.toByte(), 1, 2, 3, 4), output.toByteArray())
  }

  @Test
  fun soundDataRejectsTheAndroidxLimitBeforeReadingThePayload() {
    val header =
      RcDocumentCodec.encode(RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), emptyList()))
    val malformed =
      header +
        byteArrayOf(
          RcOpcodes.DATA_SOUND.toByte(),
          0,
          0,
          0,
          42,
          0,
          4,
          0,
          1,
        )

    val failure = assertFailsWith<RcWireException> { RcDocumentCodec.decode(malformed) }

    assertEquals("data.length", failure.fieldName)
  }

  @Test
  fun soundExpressionRejectsMoreThan64Parameters() {
    val header =
      RcDocumentCodec.encode(RcDocument(RcHeader(RcVersion(1, 0, 0), modern = false), emptyList()))
    val fixedFields = ByteArray(20)
    fixedFields[19] = 65

    val failure =
      assertFailsWith<RcWireException> {
        RcDocumentCodec.decode(header + RcOpcodes.SOUND_EXPRESSION.toByte() + fixedFields)
      }

    assertEquals("parameters.length", failure.fieldName)
  }
}
