package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.ConditionalOperations
import androidx.compose.remote.core.operations.DrawBitmapFontText
import androidx.compose.remote.core.operations.DrawBitmapFontTextOnPath
import androidx.compose.remote.core.operations.DrawRect
import androidx.compose.remote.core.operations.MatrixRotate
import androidx.compose.remote.core.operations.MatrixScale
import androidx.compose.remote.core.operations.PaintData
import androidx.compose.remote.core.operations.TextAttribute
import androidx.compose.remote.core.operations.layout.LoopOperation
import androidx.compose.remote.creation.common.PaintBundle
import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.common.TextAttribute as TextAttributeType
import androidx.compose.remote.creation.common.Utils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The writer operations added for the creation API the fork now carries, read back by AndroidX's
 * own reader (`remote-core` 1.0.0-alpha23, the release this repository depends on), field by field.
 */
class NewWriterOperationsReadBackTest {
  private fun written(
    profiles: Int = ProfileAndroidx,
    content: RemoteDocumentWriter.() -> Unit,
  ) =
    androidxOperations(
      RemoteDocumentWriter(200, 100, profiles = profiles).apply(content).encodeToByteArray(),
    )

  @Test
  fun aBitmapFontTextRunWithoutGlyphSpacing() {
    val run = written {
      val text = addText("12:34")
      val font = addBitmapFont(emptyList(), emptyMap())
      root { drawBitmapFontTextRun(text, font, 1, -1, 10f, 20f, 0f) }
      check(text == 42 && font == 43)
    }
      .only<DrawBitmapFontText>()
    assertEquals(42, run.textID)
    assertEquals(43, run.bitmapFontID)
    assertEquals(1, run.start)
    assertEquals(-1, run.end)
    assertEquals(10f, run.field<Float>("mX"))
    assertEquals(20f, run.field<Float>("mY"))
    assertEquals(0f, run.field<Float>("mGlyphSpacing"))
  }

  /** A glyph spacing that is not zero is flagged in the text id's sign bit and written after it. */
  @Test
  fun aBitmapFontTextRunWithGlyphSpacing() {
    val run = written {
      val text = addText("12:34")
      val font = addBitmapFont(emptyList(), emptyMap())
      root { drawBitmapFontTextRun(text, font, 0, 5, 1f, 2f, 3.5f) }
    }
      .only<DrawBitmapFontText>()
    assertEquals(42, run.textID)
    assertEquals(43, run.bitmapFontID)
    assertEquals(0, run.start)
    assertEquals(5, run.end)
    assertEquals(1f, run.field<Float>("mX"))
    assertEquals(2f, run.field<Float>("mY"))
    assertEquals(3.5f, run.field<Float>("mGlyphSpacing"))
  }

  @Test
  fun aBitmapFontTextRunOnAPath() {
    var pathId = -1
    val runs = written {
      val text = addText("12:34")
      val font = addBitmapFont(emptyList(), emptyMap())
      pathId = addPathData(floatArrayOf(Utils.asNan(10), 0f, 0f))
      root {
        drawBitmapFontTextRunOnPath(text, font, pathId, 0, -1, 4f, 0f)
        drawBitmapFontTextRunOnPath(text, font, pathId, 2, 3, 5f, 1.5f)
      }
    }
      .all<DrawBitmapFontTextOnPath>()
    assertEquals(2, runs.size)
    val (plain, spaced) = runs
    assertEquals(42, plain.textID)
    assertEquals(43, plain.bitmapFontID)
    assertEquals(pathId, plain.pathID)
    assertEquals(0, plain.start)
    assertEquals(-1, plain.end)
    assertEquals(4f, plain.field<Float>("mYAdj"))
    assertEquals(0f, plain.field<Float>("mGlyphSpacing"))
    assertEquals(42, spaced.textID)
    assertEquals(2, spaced.start)
    assertEquals(3, spaced.end)
    assertEquals(5f, spaced.field<Float>("mYAdj"))
    assertEquals(1.5f, spaced.field<Float>("mGlyphSpacing"))
  }

  @Test
  fun aTextAttribute() {
    var result = 0f
    val attribute = written {
      val text = addText("measured")
      val type =
        (TextAttributeType.MEASURE_TOP.toInt() or TextAttributeType.MEASURE_MAX_HEIGHT_FLAG)
          .toShort()
      result = textAttribute(text, type)
      root {}
    }
      .only<TextAttribute>()
    assertTrue(result.isNaN())
    assertEquals(Utils.idFromNan(result), attribute.mId)
    assertEquals(42, attribute.mTextId)
    assertEquals(0x204.toShort(), attribute.mType)
  }

  /** `rcPaint` collects values and writes them as one paint at `commit`, then starts again. */
  @Test
  fun thePaintBuilderCommitsWhatWasSetSince() {
    val paints = written {
      root {
        rcPaint.setColor(0xFF112233.toInt()).setStyle(PaintBundle.STYLE_STROKE)
        rcPaint.setStrokeWidth(3f).setStrokeCap(1).setStrokeJoin(2)
        rcPaint.commit()
        rcPaint.setTextSize(24f).setTypeface(0, 700, true).commit()
      }
    }
      .all<PaintData>()
      .map { it.mPaintData.toString() }
    assertEquals(2, paints.size)
    val (stroke, text) = paints
    assertTrue("Color(0xff112233)" in stroke, stroke)
    assertTrue("Style(1)" in stroke, stroke)
    assertTrue("StrokeWidth(3.0)" in stroke, stroke)
    assertTrue("StrokeCap(1)" in stroke, stroke)
    assertTrue("StrokeJoin(2)" in stroke, stroke)
    assertTrue("TextSize" !in stroke, stroke)
    assertTrue("TextSize(24.0)" in text, text)
    assertTrue("Color" !in text, text)
  }

  @Test
  fun idsFromNextIdAreNotReused() {
    val writer = RemoteDocumentWriter(10, 10)
    val first = writer.nextId()
    val text = writer.addText("x")
    val second = writer.nextId()
    assertEquals(setOf(first, text, second).size, 3)
    assertNotEquals(Utils.idFromNan(writer.createFloatId()), second)
  }

  @Test
  fun aScaleAndARotationAboutTheOrigin() {
    val operations = written {
      root {
        scale(2f, 3f)
        rotate(45f)
      }
    }
    val scale = operations.only<MatrixScale>()
    assertEquals(listOf(2f, 3f), listOf(scale.field<Float>("mX1Value"), scale.field("mY1Value")))
    assertTrue(scale.field<Float>("mX2Value").isNaN())
    assertTrue(scale.field<Float>("mY2Value").isNaN())
    val rotate = operations.only<MatrixRotate>()
    assertEquals(45f, rotate.field<Float>("mValue1"))
    assertTrue(rotate.field<Float>("mValue2").isNaN())
  }

  @Test
  fun aLoopAndAConditionalAroundTheirContent() {
    val operations = written {
      root {
        val index = Utils.idFromNan(createFloatId())
        loop(index, 1f, 2f, 9f) { drawRect(0f, 0f, 1f, 1f) }
        conditionalOperations(1, 4f, 0f) { drawRect(0f, 0f, 2f, 2f) }
      }
    }
    val loop = operations.only<LoopOperation>()
    assertEquals(listOf(1f, 2f, 9f), listOf(loop.from, loop.step, loop.until))
    assertEquals(1, loop.children().filterIsInstance<DrawRect>().size)
    val conditional = operations.only<ConditionalOperations>()
    assertEquals(ConditionalOperations.TYPE_NEQ, conditional.type)
    assertEquals(listOf(4f, 0f), listOf(conditional.varA, conditional.varB))
    assertEquals(1, conditional.children().filterIsInstance<DrawRect>().size)
  }
}
