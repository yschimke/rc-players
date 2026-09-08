@file:Suppress("RestrictedApiAndroidX")

package ee.schimke.composeai.rcplayer.compat

import androidx.compose.remote.core.WireBuffer
import androidx.compose.remote.core.operations.BitmapFontData as AndroidxBitmapFontData
import androidx.compose.remote.core.operations.BitmapTextMeasure as AndroidxBitmapTextMeasure
import androidx.compose.remote.core.operations.DrawBitmapFontText
import androidx.compose.remote.core.operations.DrawBitmapFontTextOnPath
import androidx.compose.remote.core.operations.DrawBitmapTextAnchored
import androidx.compose.remote.core.operations.DrawToBitmap
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.core.operations.IncludeReferencedOperations
import androidx.compose.remote.core.operations.ParticlesCompare
import androidx.compose.remote.core.operations.ParticlesCreate
import androidx.compose.remote.core.operations.ParticlesLoop
import androidx.compose.remote.core.operations.PlaySound
import androidx.compose.remote.core.operations.ReferencedOperations
import androidx.compose.remote.core.operations.ShaderData
import androidx.compose.remote.core.operations.Skip
import androidx.compose.remote.core.operations.SoundData
import androidx.compose.remote.core.operations.SoundExpression
import androidx.compose.remote.core.operations.layout.ComponentStart
import androidx.compose.remote.core.operations.layout.ContainerEnd
import androidx.compose.remote.core.operations.loom.PatternArgument
import androidx.compose.remote.core.operations.loom.PatternBlock
import androidx.compose.remote.core.operations.loom.PatternDefine
import androidx.compose.remote.core.operations.loom.PatternForEach
import androidx.compose.remote.core.operations.loom.PatternInflation
import androidx.compose.remote.core.operations.utilities.AnimatedFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcBitmapFontData
import ee.schimke.composeai.rcplayer.protocol.RcBitmapTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcComponentStart
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRunOnPath
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapTextAnchored
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcIncludeReferencedOperations
import ee.schimke.composeai.rcplayer.protocol.RcMacroArgument
import ee.schimke.composeai.rcplayer.protocol.RcMacroBlock
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcParticleCompare
import ee.schimke.composeai.rcplayer.protocol.RcParticleDefine
import ee.schimke.composeai.rcplayer.protocol.RcParticleLoop
import ee.schimke.composeai.rcplayer.protocol.RcPlaySound
import ee.schimke.composeai.rcplayer.protocol.RcReferencedOperations
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcSkip
import ee.schimke.composeai.rcplayer.protocol.RcSoundData
import ee.schimke.composeai.rcplayer.protocol.RcSoundExpression
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Cross-checks experimental operation bytes against the AndroidX writer, independently of CMP's
 * encoder. These are deliberately separate from renderer snapshots: a renderer can ignore an
 * operation and still produce plausible pixels, while these tests pin every field on the wire.
 */
class AndroidxExperimentalOperationCompatibilityTest {
  @Test
  fun structuralAndReferenceOperationsUseAndroidxWriterBytes() {
    val buffer = header()
    ComponentStart.apply(buffer, ComponentStart.LAYOUT_BOX, 41, 120f, 80f)
    ContainerEnd.apply(buffer)
    ReferencedOperations.apply(buffer, 42)
    ContainerEnd.apply(buffer)
    IncludeReferencedOperations.apply(buffer, 42)
    val skipOffset = Skip.apply(buffer, Skip.SKIP_IF_API_LESS_THAN, 1, 0)
    Skip.applyEndSkip(buffer, skipOffset)

    val bytes = bytes(buffer)
    val operations = RcDocumentCodec.decode(bytes).operations

    assertIs<RcComponentStart>(operations[0])
    assertEquals(41, (operations[0] as RcComponentStart).componentId)
    assertIs<RcReferencedOperations>(operations[2])
    assertIs<RcIncludeReferencedOperations>(operations[4])
    // Skip is a stream directive, not a retained document operation. Its false, empty frame still
    // proves the AndroidX header is consumed without corrupting the following stream boundary.
    assertTrue(operations.none { it is RcSkip })
  }

  @Test
  fun loomMacroOperationsUseAndroidxWriterBytes() {
    val buffer = header()
    val definitionOffset = PatternDefine.apply(buffer, 50, intArrayOf(60, 61))
    PatternArgument.apply(buffer, 0)
    PatternBlock.apply(buffer, 1)
    ContainerEnd.apply(buffer)
    PatternDefine.applyEnd(buffer, definitionOffset)
    PatternInflation.apply(buffer, 50, intArrayOf(70, 71))
    ContainerEnd.apply(buffer)
    PatternForEach.apply(buffer, 80, 81)
    ContainerEnd.apply(buffer)

    val bytes = bytes(buffer)
    val operations = RcDocumentCodec.decode(bytes).operations

    val definition = assertIs<RcMacroDefine>(operations[0])
    assertEquals(50, definition.id)
    assertEquals(listOf(60, 61), definition.parameterIds)
    assertIs<RcMacroArgument>(RcDocumentCodec.decodeOperations(definition.body)[0])
    assertIs<RcMacroBlock>(RcDocumentCodec.decodeOperations(definition.body)[1])
    assertIs<RcMacroCall>(operations[1])
    assertIs<RcMacroForEach>(operations[3])
    assertContentEquals(bytes, RcDocumentCodec.encode(RcDocumentCodec.decode(bytes)))
  }

  @Test
  fun bitmapFontOperationsUseAndroidxWriterBytes() {
    val buffer = header()
    val glyphs =
      arrayOf(
        AndroidxBitmapFontData.Glyph("AB", 91, (-1).toShort(), 2, 3, 4, 10, 12),
        AndroidxBitmapFontData.Glyph("A", 92, 0, 0, 1, 0, 8, 12),
      )
    AndroidxBitmapFontData.apply(buffer, 90, glyphs, linkedMapOf("AA" to (-2).toShort()))
    DrawBitmapFontText.apply(buffer, 100, 90, 0, -1, 10f, 20f, 1.5f)
    DrawBitmapFontTextOnPath.apply(buffer, 100, 90, 101, 0, -1, -2f, 1.5f)
    AndroidxBitmapTextMeasure.apply(
      buffer,
      102,
      100,
      90,
      AndroidxBitmapTextMeasure.MEASURE_WIDTH,
      1.5f,
    )
    DrawBitmapTextAnchored.apply(buffer, 100, 90, 0f, -1f, 40f, 30f, 0f, 1f, 1.5f)

    val bytes = bytes(buffer)
    val operations = RcDocumentCodec.decode(bytes).operations

    assertEquals(2, assertIs<RcBitmapFontData>(operations[0]).glyphs.size)
    assertIs<RcDrawBitmapFontTextRun>(operations[1])
    assertIs<RcDrawBitmapFontTextRunOnPath>(operations[2])
    assertIs<RcBitmapTextMeasure>(operations[3])
    assertIs<RcDrawBitmapTextAnchored>(operations[4])
    assertContentEquals(bytes, RcDocumentCodec.encode(RcDocumentCodec.decode(bytes)))
  }

  @Test
  fun soundOperationsUseAndroidxWriterBytes() {
    val buffer = header()
    SoundData.apply(buffer, 110, byteArrayOf(0x53, 0x43, 1, 16))
    SoundExpression.apply(
      buffer,
      111,
      .75f,
      .5f,
      1.25f,
      floatArrayOf(SoundExpression.TYPE_TONE_NAN, 440f, .1f, SoundExpression.WAVEFORM_SINE),
    )
    PlaySound.apply(buffer, 110)
    PlaySound.apply(buffer, 111)

    val bytes = bytes(buffer)
    val operations = RcDocumentCodec.decode(bytes).operations

    assertIs<RcSoundData>(operations[0])
    assertIs<RcSoundExpression>(operations[1])
    assertEquals(listOf(110, 111), operations.filterIsInstance<RcPlaySound>().map { it.soundId })
    assertContentEquals(bytes, RcDocumentCodec.encode(RcDocumentCodec.decode(bytes)))
  }

  @Test
  fun graphicsResourceOperationsUseAndroidxWriterBytes() {
    val buffer = header()
    ShaderData.apply(
      buffer,
      120,
      121,
      hashMapOf("tint" to floatArrayOf(0.25f, 0.5f, 0.75f, 1f)),
      hashMapOf("mode" to intArrayOf(2, 3)),
      hashMapOf("image" to 122),
    )
    DrawToBitmap.apply(buffer, 122, DrawToBitmap.MODE_NO_INITIALIZE, 0xff336699.toInt())

    val bytes = bytes(buffer)
    val operations = RcDocumentCodec.decode(bytes).operations

    val shader = assertIs<RcShaderData>(operations[0])
    assertEquals(120, shader.shaderId)
    assertEquals(121, shader.shaderTextId)
    assertEquals(listOf(2, 3), shader.intUniforms.getValue("mode"))
    assertEquals(122, shader.bitmapUniforms.getValue("image"))
    val target = assertIs<RcDrawToBitmap>(operations[1])
    assertEquals(DrawToBitmap.MODE_NO_INITIALIZE, target.mode)
    assertEquals(0xff336699.toInt(), target.color)
    assertContentEquals(bytes, RcDocumentCodec.encode(RcDocumentCodec.decode(bytes)))
  }

  @Test
  fun particleOperationsUseAndroidxWriterBytes() {
    val buffer = header()
    val variables = intArrayOf(131, 132)
    val references =
      variables.map { floatArrayOf(AnimatedFloatExpression.asNan(it)) }.toTypedArray()
    ParticlesCreate.apply(
      buffer,
      130,
      variables,
      arrayOf(floatArrayOf(12f), floatArrayOf(24f)),
      2,
    )
    ParticlesLoop.apply(buffer, 130, floatArrayOf(0f), references)
    ContainerEnd.apply(buffer)
    ParticlesCompare.apply(
      buffer,
      130,
      0x102.toShort(),
      0f,
      2f,
      floatArrayOf(1f),
      references,
      emptyArray(),
    )
    ContainerEnd.apply(buffer)

    val bytes = bytes(buffer)
    val operations = RcDocumentCodec.decode(bytes).operations

    val definition = assertIs<RcParticleDefine>(operations[0])
    assertEquals(2, definition.particleCount)
    assertEquals(listOf(131, 132), definition.variableIds)
    assertIs<RcParticleLoop>(operations[1])
    val compare = assertIs<RcParticleCompare>(operations[3])
    assertEquals(0x102, compare.flags)
    assertEquals(2, compare.firstEquations.size)
    assertTrue(compare.secondEquations.isEmpty())
    assertContentEquals(bytes, RcDocumentCodec.encode(RcDocumentCodec.decode(bytes)))
  }

  private fun header(): WireBuffer =
    WireBuffer().also {
      Header.apply(
        it,
        8,
        shortArrayOf(
          Header.DOC_WIDTH,
          Header.DOC_HEIGHT,
          Header.DOC_DENSITY_AT_GENERATION,
          Header.DOC_PROFILES,
        ),
        arrayOf<Any>(120, 80, 1f, ANDROIDX_EXPERIMENTAL_PROFILE),
      )
    }

  private fun bytes(buffer: WireBuffer): ByteArray = buffer.buffer.copyOf(buffer.size())

  private companion object {
    /** AndroidX namespace (0x200) plus its experimental bit (0x1). */
    const val ANDROIDX_EXPERIMENTAL_PROFILE = 0x201
  }
}
