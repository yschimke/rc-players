package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.operations.DrawBitmapFontText
import androidx.compose.remote.core.operations.DrawBitmapFontTextOnPath
import androidx.compose.remote.core.operations.DrawCircle
import androidx.compose.remote.core.operations.DrawPath
import androidx.compose.remote.core.operations.DrawRect
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.MatrixRestore
import androidx.compose.remote.core.operations.MatrixSave
import androidx.compose.remote.core.operations.MatrixScale
import androidx.compose.remote.core.operations.MatrixSkew
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.core.operations.PaintData
import androidx.compose.remote.core.operations.PathData
import androidx.compose.remote.core.operations.TextAttribute
import androidx.compose.remote.core.operations.TextFromFloat
import androidx.compose.remote.core.operations.layout.managers.StateLayout
import androidx.compose.remote.creation.common.Operations
import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.common.RemoteModifierData
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocumentInto
import androidx.compose.remote.creation.compose.capture.currentProfileSupports
import androidx.compose.remote.creation.compose.capture.currentProfileSupportsPathClip
import androidx.compose.remote.creation.compose.capture.currentProfileSupportsThemedColors
import androidx.compose.remote.creation.compose.capture.currentProfileSupportsTouchModifiers
import androidx.compose.remote.creation.compose.capture.wireValue
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteStateLayout
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.layout.rememberRemoteTextMeasurer
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.path.RemotePath
import androidx.compose.remote.creation.compose.state.Hoist
import androidx.compose.remote.creation.compose.state.RemoteBitmapFont
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteTextOverflow
import androidx.compose.remote.creation.compose.vector.RemoteAnimatedVector
import androidx.compose.remote.creation.compose.vector.RemoteAnimatedVectorGroup
import androidx.compose.remote.creation.compose.vector.RemoteAnimatedVectorPath
import androidx.compose.remote.creation.compose.vector.RemoteAnimatedVectorTarget
import androidx.compose.remote.creation.compose.vector.RemoteKeyframe
import androidx.compose.remote.creation.compose.vector.RemotePropertyAnimator
import androidx.compose.remote.creation.compose.vector.defaultProgress
import androidx.compose.remote.creation.compose.vector.painterRemoteAnimatedVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The creation API carried in realignment step 4, captured and read back by AndroidX's own reader
 * (`remote-core` 1.0.0-alpha23, the release this repository depends on).
 */
class CarriedCreationApiTest {
  /**
   * Captured into a writer that declares the AndroidX and experimental profiles: AndroidX's reader
   * takes text, id lookups, bitmap fonts on a path and the pattern operations under no other.
   */
  private fun captureBytes(
    supportedOperations: Set<Int> = emptySet(),
    content: @Composable @RemoteComposable () -> Unit,
  ): ByteArray = runBlocking {
    val display = RemoteCreationDisplayInfo(200, 100, 160)
    val writer =
      RemoteDocumentWriter(
        display.width,
        display.height,
        display.densityBehavior.wireValue,
        profiles = ProfileAndroidx or ProfileExperimental,
      )
    captureCommonRemoteDocumentInto(
      writer,
      display,
      supportedOperations = supportedOperations,
      content = content,
    )
    writer.encodeToByteArray()
  }

  private fun capture(content: @Composable @RemoteComposable () -> Unit): List<Operation> =
    androidxOperations(captureBytes(content = content))

  private fun draw(onDraw: RemoteDrawScope.() -> Unit): List<Operation> = capture {
    RemoteCanvas(RemoteModifier.fillMaxSize(), onDraw)
  }

  private val paint
    get() = RemotePaint { color = RemoteColor(Color.Red) }

  @Test
  fun skew() {
    val skew = draw {
      remoteCanvas.skew(0.5f.rf, 0.25f.rf)
      drawRect(paint)
    }
      .only<MatrixSkew>()
    assertEquals(0.5f, skew.field<Float>("mValue1"))
    assertEquals(0.25f, skew.field<Float>("mValue2"))
  }

  /** `save` returns what `restoreToCount` takes back, and every save written is restored. */
  @Test
  fun saveCountAndRestoreToCount() {
    val counts = mutableListOf<Int>()
    val operations = draw {
      val canvas = remoteCanvas
      counts += canvas.saveCount
      val outer = canvas.save()
      canvas.translate(4f.rf, 0f.rf)
      canvas.save()
      canvas.scale(2f.rf, 2f.rf)
      drawRect(paint)
      counts += canvas.saveCount
      canvas.restoreToCount(outer)
      counts += canvas.saveCount
      drawCircle(paint, 3f.rf)
    }
    assertEquals(listOf(0, 2, 0), counts)
    val order =
      operations.flattened().mapNotNull {
        when (it) {
          is MatrixSave -> "save"
          is MatrixRestore -> "restore"
          is DrawRect -> "rect"
          is DrawCircle -> "circle"
          else -> null
        }
      }
    assertEquals(listOf("save", "save", "rect", "restore", "restore", "circle"), order)
  }

  @Test
  fun bitmapFontTextRuns() {
    val font = RemoteBitmapFont(emptyList())
    val path =
      RemotePath().apply {
        moveTo(0f, 0f)
        lineTo(50f, 0f)
      }
    val operations = draw {
      remoteCanvas.drawBitmapFontTextRun("12:34".rs, font, 0, -1, 5f.rf, 6f.rf, 0f.rf, paint)
      remoteCanvas.drawBitmapFontTextRunOnPath("12:34".rs, font, path, 1, 4, 2f, 0.5f, paint)
    }
    val run = operations.only<DrawBitmapFontText>()
    assertEquals(listOf(0, -1), listOf(run.start, run.end))
    assertEquals(listOf(5f, 6f), listOf(run.field<Float>("mX"), run.field("mY")))
    val onPath = operations.only<DrawBitmapFontTextOnPath>()
    assertEquals(run.textID, onPath.textID)
    assertEquals(run.bitmapFontID, onPath.bitmapFontID)
    assertEquals(operations.only<PathData>().id, onPath.pathID)
    assertEquals(listOf(1, 4), listOf(onPath.start, onPath.end))
    assertEquals(2f, onPath.field<Float>("mYAdj"))
    assertEquals(0.5f, onPath.field<Float>("mGlyphSpacing"))
  }

  /** A state layout written from a canvas takes the modifier data and the state's id as given. */
  @Test
  fun startAndEndStateLayout() {
    val operations = draw {
      remoteCanvas.startStateLayout(RemoteModifierData(), 77)
      remoteCanvas.endStateLayout()
    }
    assertEquals(1, operations.all<StateLayout>().size)
  }

  /**
   * `Hoist` writes a state no component reads, and ahead of the layout that might hide its reader.
   * (The fork's writer puts every declaration ahead of the layout, so a state is there with or
   * without it; what `Hoist` adds is that the state is written at all.)
   */
  @Test
  fun hoistWritesItsStates() {
    fun order(hoist: Boolean, read: Boolean): List<String> = capture {
      val flag = remember { RemoteBoolean.createNamedRemoteBoolean("flag", true) }
      val level = remember { RemoteFloat.createNamedRemoteFloat("level", 1f) }
      val label = remember(level) { level.toRemoteString() }
      RemoteColumn {
        if (hoist) Hoist(label)
        RemoteStateLayout(flag) { on ->
          if (on && read) {
            RemoteText(label, fontSize = 12.rsp, overflow = RemoteTextOverflow.Clip)
          } else {
            RemoteBox(RemoteModifier.size(4.rdp))
          }
        }
      }
    }
      .flattened()
      .mapNotNull {
        when (it) {
          is TextFromFloat -> "text"
          is StateLayout -> "states"
          else -> null
        }
      }
    assertEquals(listOf("states"), order(hoist = false, read = false))
    assertEquals(listOf("text", "states"), order(hoist = true, read = false))
    assertEquals(listOf("text", "states"), order(hoist = true, read = true))
  }

  /** Each measurement sets the text paint, then asks the player for one attribute of the text. */
  @Test
  fun textMeasurer() {
    val operations = capture {
      val measurer = rememberRemoteTextMeasurer()
      RemoteCanvas(RemoteModifier.fillMaxSize()) {
        val measured = measurer.measure("Hello".rs, fontSize = 20.rsp)
        drawRect(paint, size = measured.size)
        drawCircle(paint, measured.ascent + measured.descent + measured.monospaceWidth)
      }
    }
    val attributes = operations.all<TextAttribute>()
    // width with spaces, font-box height, ascent, descent, monospace width
    assertEquals(
      setOf<Short>(0x400, 0x201, 0x204, 0x205, 0x100),
      attributes.map { it.mType }.toSet(),
    )
    assertEquals(1, attributes.map { it.mTextId }.toSet().size)
    val flat = operations.flattened()
    attributes.forEach { attribute ->
      val before = flat[flat.indexOf(attribute) - 1]
      assertTrue(before is PaintData, "a paint precedes each measurement, not $before")
      assertTrue("TextSize(20.0)" in before.mPaintData.toString(), before.mPaintData.toString())
    }
  }

  /** A triangle whose fill fades in over 100 ms. */
  private val fadingTriangle =
    RemoteAnimatedVector(
      name = "v",
      width = 48f,
      height = 48f,
      viewportWidth = 24f,
      viewportHeight = 24f,
      rootGroup =
        RemoteAnimatedVectorGroup(
          children =
            listOf(
              RemoteAnimatedVectorPath(
                name = "p",
                pathData = "M0,0 L24,0 L12,24 Z",
                fillColor = 0xFF0000FF.toInt(),
              ),
            ),
        ),
      targets =
        listOf(
          RemoteAnimatedVectorTarget(
            "p",
            listOf(
              RemotePropertyAnimator(
                "fillAlpha",
                duration = 100,
                keyframes = listOf(RemoteKeyframe(0f, 0f), RemoteKeyframe(1f, 1f)),
              ),
            ),
          ),
        ),
    )

  /** One frame of an animated vector, drawn straight into a writer. */
  @Test
  fun animatedVector() {
    val vector = fadingTriangle
    assertEquals(100, vector.totalDuration)
    val writer = RemoteDocumentWriter(48, 48)
    writer.root { vector.draw(this, 0.5f) }
    val operations = androidxOperations(writer.encodeToByteArray())
    val scale = operations.only<MatrixScale>()
    assertEquals(listOf(2f, 2f), listOf(scale.field<Float>("mX1Value"), scale.field("mY1Value")))
    val path = operations.only<PathData>()
    assertEquals(
      RemotePath("M0,0 L24,0 L12,24 Z").createFloatArray().toList(),
      path.floatPath.toList().map { it },
    )
    assertEquals(path.id, operations.only<DrawPath>().pathId)
    val fill = operations.only<PaintData>().mPaintData.toString()
    // Half way through, the fill's alpha is a half.
    assertTrue("Color(0x7f0000ff)" in fill, fill)
    assertTrue("Style(0)" in fill, fill)
  }

  /** The painter draws the vector's path with its animation as expressions over the progress. */
  @Test
  fun animatedVectorPainter() {
    val progress = RemoteFloat.createNamedRemoteFloat("progress", 0.25f)
    val operations = draw {
      with(painterRemoteAnimatedVector(fadingTriangle, progress)) { onDraw() }
    }
    assertEquals(1, operations.all<DrawPath>().size)
    assertEquals(operations.only<PathData>().id, operations.only<DrawPath>().pathId)
    // The fill's alpha follows the named progress: some expression reads it.
    val named = operations.all<NamedVariable>().single { it.mVarName == "USER:progress" }.mVarId
    assertTrue(
      operations.all<FloatExpression>().any { expression ->
        expression.mSrcValue.any { it.isNaN() && (it.toRawBits() and 0x3fffff) == named }
      },
      "no expression reads the progress",
    )
    assertEquals(
      0f,
      defaultProgress(RemoteAnimatedVector("still", 1f, 1f, 1f, 1f, RemoteAnimatedVectorGroup()))
        .constantValueOrNull,
    )
  }

  /** The string form reads as `RemotePath(pathData)` does on the JVM in AndroidX. */
  @Test
  fun aPathFromItsString() {
    val built =
      RemotePath().apply {
        moveTo(1f, 2f)
        lineTo(3f, 4f)
        lineTo(5f, 4f)
        cubicTo(1f, 1f, 2f, 2f, 3f, 3f)
        cubicTo(4f, 4f, 5f, 5f, 6f, 6f)
        quadTo(7f, 7f, 8f, 8f)
        close()
      }
    val parsed = RemotePath("M1,2 L3,4 5 4 C1 1,2 2,3 3 S5,5 6,6 Q7,7 8,8 Z")
    assertEquals(
      built.createFloatArray().toList().map(Float::toRawBits),
      parsed.createFloatArray().toList().map(Float::toRawBits),
    )
    // A half circle from (0,0) to (10,0) is two cubics ending on the end point.
    val arc = RemotePath("M0,0 A5,5 0 0 1 10,0").createFloatArray()
    assertEquals(3 + 2 * 9, arc.size)
    assertEquals(10f, arc[arc.size - 2], 1e-4f)
    assertEquals(0f, arc[arc.size - 1], 1e-4f)
    assertFailsWith<IllegalArgumentException> { RemotePath("M0,0 l1,1") }
  }

  /**
   * The fork has no `Profile`: the answer is whether the host named the operations among those its
   * reader takes, and nothing is named by default.
   */
  @Test
  fun profileCapabilitiesAnswerFromTheOperationsTheHostNamed() {
    fun answers(supported: Set<Int>): List<Boolean> {
      var answers = emptyList<Boolean>()
      captureBytes(supported) {
        answers =
          listOf(
            currentProfileSupportsThemedColors(),
            currentProfileSupportsTouchModifiers(),
            currentProfileSupportsPathClip(),
            currentProfileSupports(Operations.CLIP_PATH, Operations.COLOR_THEME),
          )
      }
      return answers
    }
    assertEquals(listOf(false, false, false, false), answers(emptySet()))
    assertEquals(
      listOf(true, false, true, true),
      answers(setOf(Operations.COLOR_THEME, Operations.CLIP_PATH, Operations.MODIFIER_TOUCH_DOWN)),
    )
    assertEquals(
      listOf(false, true, false, false),
      answers(
        setOf(
          Operations.MODIFIER_TOUCH_DOWN,
          Operations.MODIFIER_TOUCH_UP,
          Operations.MODIFIER_TOUCH_CANCEL,
        ),
      ),
    )
  }
}
