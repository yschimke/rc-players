@file:Suppress("RestrictedApiAndroidX")

package ee.schimke.composeai.rcplayer.compat

import androidx.compose.remote.core.WireBuffer
import androidx.compose.remote.core.operations.BitmapData
import androidx.compose.remote.core.operations.BitmapFontData
import androidx.compose.remote.core.operations.BitmapTextMeasure
import androidx.compose.remote.core.operations.DrawBitmap
import androidx.compose.remote.core.operations.DrawBitmapFontText
import androidx.compose.remote.core.operations.DrawBitmapFontTextOnPath
import androidx.compose.remote.core.operations.DrawBitmapTextAnchored
import androidx.compose.remote.core.operations.DrawRect
import androidx.compose.remote.core.operations.DrawToBitmap
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.core.operations.IncludeReferencedOperations
import androidx.compose.remote.core.operations.PaintData
import androidx.compose.remote.core.operations.ParticlesCompare
import androidx.compose.remote.core.operations.ParticlesCreate
import androidx.compose.remote.core.operations.ParticlesLoop
import androidx.compose.remote.core.operations.PathData
import androidx.compose.remote.core.operations.PlaySound
import androidx.compose.remote.core.operations.ReferencedOperations
import androidx.compose.remote.core.operations.ShaderData
import androidx.compose.remote.core.operations.Skip
import androidx.compose.remote.core.operations.SoundData
import androidx.compose.remote.core.operations.SoundExpression
import androidx.compose.remote.core.operations.TextData
import androidx.compose.remote.core.operations.layout.ComponentStart
import androidx.compose.remote.core.operations.layout.ContainerEnd
import androidx.compose.remote.core.operations.loom.PatternDefine
import androidx.compose.remote.core.operations.loom.PatternInflation
import androidx.compose.remote.core.operations.paint.PaintBundle
import androidx.compose.remote.core.operations.utilities.AnimatedFloatExpression
import java.io.File
import java.util.Base64

/**
 * Stages the experimental-operation corpus for `scripts/rc-operation-conformance/render-lanes.sh`.
 * Every operation byte is emitted by AndroidX, never by the CMP encoder under test.
 */
public fun main(args: Array<String>) {
  val output = File(requireNotNull(args.firstOrNull()) { "output directory required" })
  output.mkdirs()

  write(output, "structural-reference", ::structuralReference)
  write(output, "loom-macro", ::loomMacro)
  write(output, "bitmap-font", ::bitmapFont)
  write(output, "sound-operation", ::soundOperation)
  write(output, "graphics-offscreen", ::graphicsOffscreen)
  write(output, "graphics-runtime-shader", ::graphicsRuntimeShader)
  write(output, "particle-loop", ::particleLoop)
  File(output, "manifest.json")
    .writeText(
      """
      [
            {"id":"structural-reference","width":96,"height":64,"density":1},
            {"id":"loom-macro","width":96,"height":64,"density":1},
            {"id":"bitmap-font","width":96,"height":64,"density":1},
            {"id":"sound-operation","width":96,"height":64,"density":1},
            {"id":"graphics-offscreen","width":96,"height":64,"density":1},
            {"id":"graphics-runtime-shader","width":96,"height":64,"density":1,"embeddedSoftwareCanvasLimitation":"RuntimeShader requires a hardware-accelerated Android canvas"},
            {"id":"particle-loop","width":96,"height":64,"density":1}
          ]
      """
        .trimIndent()
    )
  File(output, "conformance.json")
    .writeText(
      """
      {
            "schemaVersion": 1,
            "fixtures": {
              "structural-reference": {"comparison":"pixels","operations":["COMPONENT_START","REFERENCED_OPERATIONS","INCLUDE_REFERENCED_OPERATIONS","SKIP"]},
              "loom-macro": {"comparison":"pixels","operations":["MACRO_DEFINE","MACRO_CALL"]},
              "bitmap-font": {"comparison":"pixels","operations":["DATA_BITMAP_FONT","DRAW_BITMAP_FONT_TEXT_RUN","DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH","BITMAP_TEXT_MEASURE","DRAW_BITMAP_TEXT_ANCHORED"]},
              "graphics-offscreen": {"comparison":"pixels","operations":["DRAW_TO_BITMAP"]},
              "graphics-runtime-shader": {"comparison":"pixels","operations":["DATA_SHADER"]},
              "particle-loop": {"comparison":"pixels","operations":["PARTICLE_DEFINE","PARTICLE_LOOP","PARTICLE_COMPARE"]},
              "sound-operation": {
                "comparison":"events",
                "operations":["DATA_SOUND","SOUND_EXPRESSION","PLAY_SOUND"],
                "eventLanes":["cmp-jvm"],
                "parseOnlyLanes":["view","upstream-release","upstream-snapshot","vendored-android","vendored-jvm"],
                "limitation":"Android raster harnesses do not install a SoundEngine recorder; pixels can prove parse/render survival, not playback scheduling."
              }
            }
          }
      """
        .trimIndent()
    )
}

private fun graphicsOffscreen(buffer: WireBuffer) {
  header(buffer)
  paint(buffer, 0xfff6f2ff.toInt())
  DrawRect.apply(buffer, 0f, 0f, 96f, 64f)
  BitmapData.apply(
    buffer,
    60,
    BitmapData.TYPE_RAW8888,
    48,
    BitmapData.ENCODING_INLINE,
    32,
    ByteArray(48 * 32 * 4),
  )
  DrawToBitmap.apply(buffer, 60, 0, 0xff386a20.toInt())
  paint(buffer, 0xffffb4ab.toInt())
  DrawRect.apply(buffer, 8f, 6f, 40f, 26f)
  DrawToBitmap.apply(buffer, 0, 0, 0)
  DrawBitmap.apply(buffer, 60, 24f, 16f, 72f, 48f, 0)
}

private fun graphicsRuntimeShader(buffer: WireBuffer) {
  header(buffer)
  TextData.apply(
    buffer,
    70,
    "uniform float4 tint; half4 main(float2 position) { return half4(tint); }",
  )
  ShaderData.apply(
    buffer,
    71,
    70,
    hashMapOf("tint" to floatArrayOf(0.23f, 0.42f, 0.13f, 1f)),
    hashMapOf(),
    hashMapOf(),
  )
  PaintData.apply(
    buffer,
    PaintBundle().apply {
      setShader(71)
      setStyle(PaintBundle.STYLE_FILL)
    },
  )
  DrawRect.apply(buffer, 8f, 8f, 88f, 56f)
}

private fun particleLoop(buffer: WireBuffer) {
  header(buffer)
  paint(buffer, 0xfff6f2ff.toInt())
  DrawRect.apply(buffer, 0f, 0f, 96f, 64f)
  val variables = intArrayOf(81, 82, 83, 84)
  ParticlesCreate.apply(
    buffer,
    80,
    variables,
    arrayOf(floatArrayOf(20f), floatArrayOf(16f), floatArrayOf(76f), floatArrayOf(48f)),
    1,
  )
  ParticlesLoop.apply(
    buffer,
    80,
    floatArrayOf(0f),
    variables.map { floatArrayOf(AnimatedFloatExpression.asNan(it)) }.toTypedArray(),
  )
  paint(buffer, 0xff6750a4.toInt())
  DrawRect.apply(
    buffer,
    AnimatedFloatExpression.asNan(81),
    AnimatedFloatExpression.asNan(82),
    AnimatedFloatExpression.asNan(83),
    AnimatedFloatExpression.asNan(84),
  )
  ContainerEnd.apply(buffer)
  ParticlesCompare.apply(
    buffer,
    80,
    0.toShort(),
    0f,
    1f,
    floatArrayOf(0f),
    variables.map { floatArrayOf(AnimatedFloatExpression.asNan(it)) }.toTypedArray(),
    emptyArray(),
  )
  ContainerEnd.apply(buffer)
}

private fun structuralReference(buffer: WireBuffer) {
  header(buffer)
  paint(buffer, 0xffe8def8.toInt())
  DrawRect.apply(buffer, 0f, 0f, 96f, 64f)
  ReferencedOperations.apply(buffer, 20)
  paint(buffer, 0xff6750a4.toInt())
  DrawRect.apply(buffer, 16f, 12f, 80f, 52f)
  ContainerEnd.apply(buffer)
  IncludeReferencedOperations.apply(buffer, 20)

  // The condition is false for all currently tracked players. Keeping a real framed Skip in the
  // corpus catches parsers that accidentally consume or execute the following operation.
  val skip = Skip.apply(buffer, Skip.SKIP_IF_API_LESS_THAN, 1, 0)
  paint(buffer, 0xffff0000.toInt())
  DrawRect.apply(buffer, 0f, 0f, 8f, 8f)
  Skip.applyEndSkip(buffer, skip)

  // Visually inert, but it makes every lane exercise AndroidX's framed legacy-component reader.
  ComponentStart.apply(buffer, ComponentStart.LAYOUT_BOX, 21, 96f, 64f)
  ContainerEnd.apply(buffer)
}

private fun loomMacro(buffer: WireBuffer) {
  header(buffer)
  paint(buffer, 0xffe8def8.toInt())
  DrawRect.apply(buffer, 0f, 0f, 96f, 64f)
  val definition = PatternDefine.apply(buffer, 30, intArrayOf())
  paint(buffer, 0xff386a20.toInt())
  DrawRect.apply(buffer, 20f, 14f, 76f, 50f)
  PatternDefine.applyEnd(buffer, definition)
  PatternInflation.apply(buffer, 30, intArrayOf())
  ContainerEnd.apply(buffer)
}

private fun bitmapFont(buffer: WireBuffer) {
  header(buffer)
  paint(buffer, 0xfff6f2ff.toInt())
  DrawRect.apply(buffer, 0f, 0f, 96f, 64f)
  // Encoded PNGs avoid making bitmap-font conformance depend on each lane's RAW8888 channel
  // interpretation. The bytes are deterministic pngjs output for solid 6x10 purple and 8x10
  // coral images; BitmapData.apply is still the authoritative AndroidX wire writer.
  BitmapData.apply(buffer, 41, 6, 10, Base64.getDecoder().decode(PURPLE_GLYPH_PNG))
  BitmapData.apply(buffer, 42, 8, 10, Base64.getDecoder().decode(CORAL_GLYPH_PNG))
  val glyphs =
    arrayOf(
      BitmapFontData.Glyph("A", 41, 0, 0, 1, 0, 6, 10),
      BitmapFontData.Glyph("B", 42, 0, 0, 1, 0, 8, 10),
    )
  BitmapFontData.apply(buffer, 40, glyphs, linkedMapOf("AB" to (-1).toShort()))
  TextData.apply(buffer, 43, "ABBA")
  DrawBitmapFontText.apply(buffer, 43, 40, 0, -1, 8f, 18f, 1f)
  PathData.apply(
    buffer,
    44,
    floatArrayOf(
      PathData.MOVE_NAN,
      8f,
      34f,
      PathData.LINE_NAN,
      0f,
      0f,
      88f,
      34f,
      PathData.DONE_NAN,
    ),
  )
  DrawBitmapFontTextOnPath.apply(buffer, 43, 40, 44, 0, 2, 0f, 1f)
  BitmapTextMeasure.apply(buffer, 45, 43, 40, BitmapTextMeasure.MEASURE_WIDTH, 1f)
  DrawBitmapTextAnchored.apply(buffer, 43, 40, 0f, -1f, 48f, 58f, 0f, 1f, 1f)
}

private fun soundOperation(buffer: WireBuffer) {
  header(buffer)
  paint(buffer, 0xfff6f2ff.toInt())
  DrawRect.apply(buffer, 0f, 0f, 96f, 64f)
  SoundData.apply(buffer, 49, Base64.getDecoder().decode(SILENT_WAV))
  SoundExpression.apply(
    buffer,
    50,
    .75f,
    .5f,
    1f,
    floatArrayOf(SoundExpression.TYPE_TONE_NAN, 440f, .05f, SoundExpression.WAVEFORM_SINE),
  )
  PlaySound.apply(buffer, 50)
}

private fun header(buffer: WireBuffer) =
  Header.apply(
    buffer,
    8,
    shortArrayOf(
      Header.DOC_WIDTH,
      Header.DOC_HEIGHT,
      Header.DOC_DENSITY_AT_GENERATION,
      Header.DOC_PROFILES,
    ),
    arrayOf<Any>(96, 64, 1f, ANDROIDX_EXPERIMENTAL_PROFILE),
  )

/** AndroidX namespace (0x200) plus its experimental bit (0x1). */
private const val ANDROIDX_EXPERIMENTAL_PROFILE = 0x201

private fun paint(buffer: WireBuffer, color: Int) {
  PaintData.apply(
    buffer,
    PaintBundle().apply {
      setColor(color)
      setStyle(PaintBundle.STYLE_FILL)
    },
  )
}

private fun write(output: File, id: String, build: (WireBuffer) -> Unit) {
  val buffer = WireBuffer()
  build(buffer)
  File(output, "$id.rc").writeBytes(buffer.buffer.copyOf(buffer.size()))
}

private const val PURPLE_GLYPH_PNG =
  "iVBORw0KGgoAAAANSUhEUgAAAAYAAAAKCAYAAACXDi8zAAAAJ0lEQVR4AXXBAQEAIAyAMCShLSxmT19AtnX2fXxIkCBBggQJEiRIGE/RAm7A8/zIAAAAAElFTkSuQmCC"
private const val CORAL_GLYPH_PNG =
  "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAKCAYAAACJxx+AAAAAJ0lEQVR4AYXBMQEAIAzAsFL/TnjxNxysyZl3h4UECRIkSJAgQYKED1voA3GNlHfFAAAAAElFTkSuQmCC"
private const val SILENT_WAV = "UklGRiQAAABXQVZFZm10IBAAAAABAAEAIlYAAESsAAACABAAZGF0YQAAAAA="
