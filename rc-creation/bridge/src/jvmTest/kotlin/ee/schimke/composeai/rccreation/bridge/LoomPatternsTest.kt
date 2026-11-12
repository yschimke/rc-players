package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.operations.DrawCircle
import androidx.compose.remote.core.operations.FloatConstant
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.PaintData
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.common.Profile
import ee.schimke.composeai.rccreation.common.RcPlatformProfiles
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocumentInto
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteFloatArray
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcIdList
import ee.schimke.composeai.rcplayer.protocol.RcIdLookup
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * `RemoteCanvas.forEach` and `definePattern`: the document as written (read with this repository's
 * codec, which keeps a pattern as it is on the wire), and as AndroidX's own reader (`remote-core`
 * 1.0.0-alpha23) expands it when it loads, one copy of the body per item or call with the body's
 * parameters replaced.
 */
class LoomPatternsTest {
  private fun bytes(onDraw: RemoteDrawScope.() -> Unit): ByteArray = runBlocking {
    val display = RemoteCreationDisplayInfo(200, 100, 160)
    // AndroidX reads the pattern operations only under the experimental profile.
    val writer = RemoteDocumentWriter(200, 100, profiles = ProfileAndroidx or ProfileExperimental)
    captureCommonRemoteDocumentInto(writer, display) {
      RemoteCanvas(RemoteModifier.fillMaxSize(), onDraw)
    }
    writer.encodeToByteArray()
  }

  private val paint
    get() = RemotePaint { color = RemoteColor(Color.Red) }

  /**
   * AndroidX reads the pattern operations only under its experimental profile. A capture for that
   * profile declares it in the header and loads in AndroidX's reader; a capture for the default
   * profile, AndroidX's without the experimental operations, is refused the operation, as
   * AndroidX's own writer refuses it.
   */
  @Test
  fun aPatternNeedsTheExperimentalProfile() {
    fun capture(profile: Profile) = runBlocking {
      captureCommonRemoteDocument(RemoteCreationDisplayInfo(200, 100, 160), profile = profile) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) {
          remoteCanvas.forEach(RemoteFloatArray(1f, 2f, 3f)) { item ->
            drawCircle(paint, item + 1f)
          }
        }
      }
    }
    assertEquals(3, radiusInputs(capture(ExperimentalProfile)).size)
    val refused = assertFailsWith<RuntimeException> { capture(RcPlatformProfiles.ANDROIDX) }
    assertEquals("Operation 244 is not supported for this version", refused.message)
  }

  private val level = RemoteFloat.createNamedRemoteFloat("level", 2f)

  private fun written(bytes: ByteArray): List<RcOperation> =
    RcDocumentCodec.decode(bytes).operations

  /** The operations of the container [start] opens, which holds no container of its own here. */
  private fun List<RcOperation>.bodyOf(start: RcOperation): List<RcOperation> =
    drop(indexOf(start) + 1).takeWhile { it.opcode != CONTAINER_END }

  /** The radius expression of each circle AndroidX drew: the ids it reads. */
  private fun radiusInputs(bytes: ByteArray): List<Set<Int>> {
    val operations = androidxOperations(bytes)
    val expressions = operations.all<FloatExpression>().associateBy { it.mId }
    return operations.all<DrawCircle>().map { circle ->
      val radius = circle.field<Float>("mValue3")
      val expression = expressions.getValue(radius.toRawBits() and 0x3fffff)
      expression.mSrcValue
        .filter { it.isNaN() }
        .map { it.toRawBits() and 0x3fffff }
        .filter { it < 0x300000 }
        .toSet()
    }
  }

  @Test
  fun forEachOverAList() {
    val doc = bytes {
      remoteCanvas.forEach(listOf(level + 1f, level * 3f)) { item -> drawCircle(paint, item * 2f) }
    }
    val operations = written(doc)
    val forEach = operations.filterIsInstance<RcMacroForEach>().single()
    val items = operations.filterIsInstance<RcIdList>().single { it.id == forEach.collectionId }
    assertEquals(2, items.ids.size)
    // What the body computes from the item is in the body, where the player can remap it.
    val body = operations.bodyOf(forEach)
    assertTrue(
      body.filterIsInstance<RcFloatExpression>().any { expression ->
        expression.expression.any { it.referencedId == forEach.localItemId }
      },
      "the item's expression is not in the for-each body: $body",
    )
    // AndroidX expands it to one circle per item, each reading its own item.
    assertEquals(items.ids.map { setOf(it) }, radiusInputs(doc))
  }

  @Test
  fun forEachOverAFloatArray() {
    val doc = bytes {
      remoteCanvas.forEach(RemoteFloatArray(1f, 2f, 3f)) { item -> drawCircle(paint, item + 1f) }
    }
    val operations = written(doc)
    val forEach = operations.filterIsInstance<RcMacroForEach>().single()
    val items = operations.filterIsInstance<RcIdList>().single { it.id == forEach.collectionId }
    assertEquals(3, items.ids.size)
    assertEquals(items.ids.map { setOf(it) }, radiusInputs(doc))
  }

  /** Two lists are walked as a list of pairs, each element looked up from the pair in the body. */
  @Test
  fun forEachOverTwoLists() {
    val doc = bytes {
      remoteCanvas.forEach(listOf(level, level * 2f), listOf(level * 4f, level * 5f)) { a, b ->
        drawCircle(paint, a + b)
      }
    }
    val operations = written(doc)
    val forEach = operations.filterIsInstance<RcMacroForEach>().single()
    val pairs = operations.filterIsInstance<RcIdList>().single { it.id == forEach.collectionId }
    assertEquals(2, pairs.ids.size)
    val lookups = operations.bodyOf(forEach).filterIsInstance<RcIdLookup>()
    assertEquals(listOf(0f, 1f), lookups.map { it.index.value })
    assertEquals(setOf(forEach.localItemId), lookups.map { it.listId }.toSet())
    // AndroidX: two circles, each adding the two looked-up elements.
    assertEquals(List(2) { lookups.map { it.outId }.toSet() }, radiusInputs(doc))
  }

  @Test
  fun aPatternDefinedOnceAndInflatedTwice() {
    val doc = bytes {
      val dot = remoteCanvas.definePattern<RemoteFloat> { radius -> drawCircle(paint, radius + 1f) }
      dot(level)
      dot(level * 2f)
    }
    val operations = written(doc)
    val define = operations.filterIsInstance<RcMacroDefine>().single()
    assertEquals(1, define.parameterIds.size)
    assertTrue(define.body.isNotEmpty())
    val calls = operations.filterIsInstance<RcMacroCall>()
    assertEquals(listOf(define.id, define.id), calls.map { it.id })
    assertEquals(2, calls.map { it.argumentIds.single() }.toSet().size)
    // AndroidX expands each call to the body with the parameter replaced by its argument.
    assertEquals(calls.map { it.argumentIds.toSet() }, radiusInputs(doc))
  }

  /**
   * A pattern's body carries its own paint, and what is drawn after a call sends its paint again:
   * as AndroidX expands it, every circle is preceded by a paint, where the canvas's one paint
   * tracker left the pattern's body without one when the paint before it was the same.
   */
  @Test
  fun aPatternBodyCarriesItsPaint() {
    val blue = RemotePaint { color = RemoteColor(Color.Blue) }
    val doc = bytes {
      drawCircle(paint, level)
      val dot = remoteCanvas.definePattern { drawCircle(paint, level + 1f) }
      dot()
      drawCircle(blue, level)
      dot()
      drawCircle(blue, level)
    }
    val order =
      androidxOperations(doc).flattened().mapNotNull {
        when (it) {
          is PaintData -> "paint"
          is DrawCircle -> "circle"
          else -> null
        }
      }
    assertEquals(List(5) { listOf("paint", "circle") }.flatten(), order)
  }

  /**
   * A named float a pattern's body reads first, and a circle after the pattern reads too: it is
   * declared outside the template, so AndroidX, which gives what a template declares a new id each
   * time it expands it, still finds it for the circle. What the body computes from its parameter
   * stays in the body.
   */
  @Test
  fun whatAPatternReadsFromOutsideIsDeclaredOutsideIt() {
    val doc = bytes {
      val dot =
        remoteCanvas.definePattern<RemoteFloat> { scale -> drawCircle(paint, level * scale) }
      dot(level + 1f)
      drawCircle(paint, level)
    }
    // AndroidX: the circle after the pattern reads a float the expanded document declares.
    assertTrue(lastCircleReadsADeclaredFloat(doc), "the circle after the pattern reads no float")
    val operations = written(doc)
    val define = operations.filterIsInstance<RcMacroDefine>().single()
    val named = operations.filterIsInstance<RcNamedVariable>().single { it.name.endsWith("level") }
    assertTrue(
      operations.indexOf(named) < operations.indexOf(define),
      "declared before the pattern",
    )
    val body = RcDocumentCodec.decodeOperations(define.body)
    assertTrue(body.none { it is RcNamedVariable }, "not in the template: $body")
    val product = body.filterIsInstance<RcFloatExpression>().single()
    assertTrue(product.expression.any { it.referencedId == define.parameterIds.single() })
  }

  /** Whether the radius of the last circle AndroidX drew is the id of a float it declared. */
  private fun lastCircleReadsADeclaredFloat(bytes: ByteArray): Boolean {
    val operations = androidxOperations(bytes)
    val declared = operations.all<FloatConstant>().map { it.field<Int>("mId") }
    val radius = operations.all<DrawCircle>().last().field<Float>("mValue3")
    return radius.isNaN() && (radius.toRawBits() and 0x3fffff) in declared
  }

  /** The same for a for-each body. */
  @Test
  fun whatAForEachBodyReadsFromOutsideIsDeclaredOutsideIt() {
    val doc = bytes {
      remoteCanvas.forEach(listOf(level + 1f, level + 2f)) { item ->
        drawCircle(paint, item * level)
      }
      drawCircle(paint, level)
    }
    assertEquals(3, androidxOperations(doc).all<DrawCircle>().size)
    assertTrue(lastCircleReadsADeclaredFloat(doc))
  }

  private companion object {
    const val CONTAINER_END = 214
  }
}
