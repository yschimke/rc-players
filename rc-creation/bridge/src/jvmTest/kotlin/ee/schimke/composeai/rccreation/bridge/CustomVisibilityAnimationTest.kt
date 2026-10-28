package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.FloatFunctionDefine
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.animation.AnimationSpec
import androidx.compose.remote.creation.common.AnimatedFloatExpression
import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.common.Utils
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocumentInto
import androidx.compose.remote.creation.compose.capture.wireValue
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.animateEnterExit
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.visibility
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberMutableRemoteInt
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * A custom exit animation authored through the creation fork, drawn from the component's offscreen
 * buffer, decodes through the CMP protocol codec to the operations remote-core alpha22 runs: the
 * function, defined ahead of the layout with its body inline, and an `AnimationSpec` naming it.
 */
class CustomVisibilityAnimationTest {
  private val displayInfo = RemoteCreationDisplayInfo(360, 480, 160)

  private fun captureBytes(): Pair<Int, ByteArray> = runBlocking {
    val writer =
      RemoteDocumentWriter(
        displayInfo.size.width.toInt(),
        displayInfo.size.height.toInt(),
        displayInfo.densityBehavior.wireValue,
      )
    // The buffer is drawn back wiped from the right as progress grows.
    val dissolve = writer.defineVisibilityAnimation { component, progress, width, height, x, y ->
      val buffer = createOffscreenBitmap(component)
      drawComponentToBitmap(component, buffer)
      val kept = floatExpression(width, 1f, progress, SUB, MUL)
      val right = floatExpression(x, kept, ADD)
      val bottom = floatExpression(y, height, ADD)
      save()
      clipRect(x, y, right, bottom)
      drawBitmap(buffer, x, y, floatExpression(x, width, ADD), bottom, 0)
      restore()
    }
    captureCommonRemoteDocumentInto(writer, displayInfo) {
      RemoteBox(
        RemoteModifier.size(40.rdp)
          .visibility(rememberMutableRemoteInt(1))
          .animateEnterExit(exit = rcCustomExitTransition(dissolve))
      ) {}
    }
    dissolve to writer.encodeToByteArray()
  }

  private fun capture(): Pair<Int, List<RcOperation>> =
    captureBytes().let { (dissolve, bytes) -> dissolve to RcDocumentCodec.decode(bytes).operations }

  @Test
  fun theFunctionIsDefinedAheadOfTheLayoutWithItsBodyInline() {
    val (dissolve, operations) = capture()
    val start = operations.indexOfFirst { it is RcFloatFunctionDefine }
    val end = operations.indexOfFirst { it is RcNoArg && it.opcode == CONTAINER_END }
    val root = operations.indexOfFirst { it is RcRootLayout }
    assert(start in 0 until end && end < root) { "function before the layout: $operations" }

    // Ids follow upstream's `defineVisibilityAnimation`: the function, then its six parameters
    // (progress, width, height, x, y, component), then the body's own declarations, in order.
    val progress = dissolve + 1
    val width = dissolve + 2
    val height = dissolve + 3
    val x = dissolve + 4
    val y = dissolve + 5
    val component = dissolve + 6
    val buffer = dissolve + 7
    val kept = dissolve + 8
    val right = dissolve + 9
    val bottom = dissolve + 10
    val bitmapRight = dissolve + 11
    assertEquals(
      listOf(
        RcFloatFunctionDefine(dissolve, listOf(progress, width, height, x, y, component)),
        RcBitmapData(
          buffer,
          width = 1,
          height = 1,
          type = RcBitmapData.TYPE_RAW8888,
          encoding = RcBitmapData.ENCODING_COMPONENT_OFFSCREEN_BUFFER,
          data = byteArrayOf(0, 0, 0, component.toByte()),
        ),
        RcDrawToBitmap(buffer, mode = 2, color = component),
        RcNoArg(DRAW_CONTENT),
        RcDrawToBitmap(0, 0, 0),
        RcFloatExpression(kept, listOf(ref(width), lit(1f), ref(progress), op(SUB), op(MUL)), null),
        RcFloatExpression(right, listOf(ref(x), ref(kept), op(ADD)), null),
        RcFloatExpression(bottom, listOf(ref(y), ref(height), op(ADD)), null),
        RcNoArg(SAVE),
        RcDraw4(CLIP_RECT, ref(x), ref(y), ref(right), ref(bottom)),
        RcFloatExpression(bitmapRight, listOf(ref(x), ref(width), op(ADD)), null),
      ),
      operations.subList(start, start + 11),
    )
    assertEquals(RcNoArg(RESTORE), operations[end - 1])
  }

  @Test
  fun theComponentNamesTheFunctionAsItsCustomExit() {
    val (dissolve, operations) = capture()
    val spec = operations.filterIsInstance<RcAnimationSpec>().single()
    assertEquals(RcLayoutAnimation.Custom, spec.exitAnimation)
    assertEquals(RcLayoutAnimation.FadeIn, spec.enterAnimation)
    assertEquals(dissolve, spec.exitFunctionId)
    assertEquals(-1, spec.enterFunctionId)
  }

  @Test
  fun nothingTheBodyDeclaresIsReusedByTheContent() {
    val (dissolve, operations) = capture()
    val end = operations.indexOfFirst { it is RcNoArg && it.opcode == CONTAINER_END }
    val bodyIds = (dissolve..dissolve + 11).toSet()
    val referenced =
      operations.drop(end + 1).flatMap { operation ->
        when (operation) {
          is RcFloatExpression -> operation.expression.mapNotNull { it.referencedId }
          else -> emptyList()
        }
      }
    assertEquals(emptyList(), referenced.filter { it in bodyIds })
  }

  /** AndroidX remote-core alpha22 reads the same document, function and spec alike. */
  @Test
  fun androidxReadsTheDocument() {
    val (dissolve, bytes) = captureBytes()
    val document = CoreDocument()
    document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
    val function = document.operations.filterIsInstance<FloatFunctionDefine>().single()
    assertEquals(dissolve, function.id)
    assertEquals(
      RcLayoutAnimation.Custom.wireValue,
      androidxSpec(document.operations).exitAnimation.ordinal,
    )
    assertEquals(dissolve, androidxSpec(document.operations).exitFunctionId)
  }

  /** The one component's spec: AndroidX folds an `AnimationSpec` into the component it follows. */
  private fun androidxSpec(operations: List<Operation>): AnimationSpec =
    androidxSpecs(operations).single { it.exitFunctionId != -1 }

  private fun androidxSpecs(operations: List<Operation>): List<AnimationSpec> =
    operations.flatMap { operation ->
      when (operation) {
        is Component -> listOfNotNull(operation.animationSpec) + androidxSpecs(operation.list)
        is Container -> androidxSpecs(operation.list)
        else -> emptyList()
      }
    }

  private fun ref(id: Int) = RcFloatWord(Utils.asNan(id).toRawBits())

  private fun lit(value: Float) = RcFloatWord.literal(value)

  private fun op(value: Float) = RcFloatWord(value.toRawBits())

  private companion object {
    val ADD = AnimatedFloatExpression.ADD
    val SUB = AnimatedFloatExpression.SUB
    val MUL = AnimatedFloatExpression.MUL
    const val CLIP_RECT = 39
    const val SAVE = 130
    const val RESTORE = 131
    const val DRAW_CONTENT = 139
    const val CONTAINER_END = 214
  }
}
