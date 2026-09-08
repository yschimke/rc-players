package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcPlaySound
import ee.schimke.composeai.rcplayer.protocol.RcSoundData
import ee.schimke.composeai.rcplayer.protocol.RcSoundExpression
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RcSoundRuntimeTest {
  @Test
  fun toneSynthesisIsBoundedDeterministicWav() {
    val first = requireNotNull(RcToneSynthesizer.synthesizeWav(440f, 0.01f, 0f))
    val second = requireNotNull(RcToneSynthesizer.synthesizeWav(440f, 0.01f, 0f))

    assertContentEquals(first, second)
    assertEquals("RIFF", first.copyOfRange(0, 4).decodeToString())
    assertEquals("WAVE", first.copyOfRange(8, 12).decodeToString())
    assertEquals(44 + 220 * 2, first.size)
    assertNull(RcToneSynthesizer.synthesizeWav(440f, Float.POSITIVE_INFINITY, 0f))
    assertNull(RcToneSynthesizer.synthesizeWav(440f, 60f, 0f))
  }

  @Test
  fun preparationLoadsInlineAndSynthesizedResourcesInWireOrder() {
    val effects = mutableListOf<RcSoundEffect>()
    val inline = byteArrayOf(0x53, 0x43, 1, 16)

    RcPlayerState(document(inline), soundSink = effects::add)

    assertEquals(2, effects.size)
    assertContentEquals(inline, assertIs<RcSoundEffect.Load>(effects[0]).data)
    val tone = assertIs<RcSoundEffect.Load>(effects[1])
    assertEquals(8, tone.soundId)
    assertEquals("RIFF", tone.data.copyOfRange(0, 4).decodeToString())
  }

  @Test
  fun playRebuildsOnlyWhenADynamicParameterChangesThenDispatchesInOrder() {
    val effects = mutableListOf<RcSoundEffect>()
    val state = RcPlayerState(document(byteArrayOf()), soundSink = effects::add)
    effects.clear()

    state.playSound(RcPlaySound(8))
    assertEquals(listOf<RcSoundEffect>(RcSoundEffect.Play(8)), effects)

    effects.clear()
    state.setFloat(42, 880f)
    state.playSound(RcPlaySound(8))

    assertEquals(2, effects.size)
    assertIs<RcSoundEffect.Load>(effects[0])
    assertEquals(RcSoundEffect.Play(8), effects[1])
  }

  @Test
  fun playSoundIsAnExecutableClickAction() {
    val effects = mutableListOf<RcSoundEffect>()
    val state = RcPlayerState(document(byteArrayOf()), soundSink = effects::add)
    effects.clear()

    state.executeClick(RcClickActionBlock(listOf(RcLinkedNode.Operation(RcPlaySound(8)))))

    assertTrue(effects.last() == RcSoundEffect.Play(8))
  }

  private fun document(inline: ByteArray): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0)),
      listOf(
        RcFloatConstant(42, RcFloatWord.literal(440f)),
        RcSoundData(7, inline),
        RcSoundExpression(
          id = 8,
          leftVolume = RcFloatWord.literal(1f),
          rightVolume = RcFloatWord.literal(1f),
          rate = RcFloatWord.literal(1f),
          parameters =
            listOf(
              RcSoundExpression.TYPE_TONE_WORD,
              RcFloatWord(0x7fc00000 or 42),
              RcFloatWord.literal(0.01f),
              RcFloatWord.literal(0f),
            ),
        ),
      ),
    )
}
