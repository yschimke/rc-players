package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.operations.ColorExpression
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.PathData
import androidx.compose.remote.core.operations.ShaderData
import androidx.compose.remote.core.operations.TextFromFloat
import androidx.compose.remote.core.operations.TextMerge
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.common.TextAttribute as TextAttributeType
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocumentInto
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.path.RemotePath
import ee.schimke.composeai.rccreation.compose.shaders.RemoteRuntimeShader
import ee.schimke.composeai.rccreation.compose.state.DecimalFormat
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.lerp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcMacroForEach
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcTextAttribute
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextMerge
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Whatever a pattern or for-each body declares from its parameter or item is declared in the body,
 * for every kind of declaration: the player expands the body once per call or item with those ids
 * replaced, so a declaration left ahead of the layout would read an id nothing binds, and every
 * call would share it. Each kind is written in a pattern called twice and in a for-each over two
 * items; the document is read as written (this repository's codec, which keeps a body as it is on
 * the wire) and as AndroidX's own reader (`remote-core` 1.0.0-alpha23) expands it.
 */
class BodyOwnedDeclarationsTest {
  private val level = RemoteFloat.createNamedRemoteFloat("level", 2f)

  private fun bytes(onDraw: RemoteDrawScope.() -> Unit): ByteArray = runBlocking {
    val display = RemoteCreationDisplayInfo(200, 100, 160)
    val writer = RemoteDocumentWriter(200, 100, profiles = ProfileAndroidx or ProfileExperimental)
    captureCommonRemoteDocumentInto(writer, display) {
      RemoteCanvas(RemoteModifier.fillMaxSize(), onDraw)
    }
    writer.encodeToByteArray()
  }

  /** [body] in a pattern called with two values, and in a for-each over two. */
  private fun documents(
    body: RemoteDrawScope.(RemoteFloat) -> Unit,
  ): Map<String, ByteArray> =
    mapOf(
      "pattern" to
        bytes {
          val pattern = remoteCanvas.definePattern<RemoteFloat> { value -> body(value) }
          pattern(level + 1f)
          pattern(level * 3f)
        },
      "for-each" to
        bytes { remoteCanvas.forEach(listOf(level + 1f, level * 3f)) { item -> body(item) } },
    )

  /** The operations of the document's one pattern or for-each body, and those outside it. */
  private fun bodyAndRest(bytes: ByteArray): Pair<List<RcOperation>, List<RcOperation>> {
    val operations = RcDocumentCodec.decode(bytes).operations
    val define = operations.filterIsInstance<RcMacroDefine>().singleOrNull()
    if (define != null) return RcDocumentCodec.decodeOperations(define.body) to operations
    val forEach = operations.filterIsInstance<RcMacroForEach>().single()
    val start = operations.indexOf(forEach) + 1
    var depth = 0
    var end = start
    while (depth > 0 || operations[end].opcode != CONTAINER_END) {
      if (operations[end].opcode == CONTAINER_END) depth--
      else if (operations[end] is RcMacroForEach) depth++
      end++
    }
    return operations.subList(start, end) to operations.take(start) + operations.drop(end)
  }

  /**
   * Each kind of [declared] (as this repository's codec names it, and as AndroidX's reader does) is
   * declared in the body and nowhere else, and AndroidX's expansion has the body's declarations
   * once per call or item.
   */
  private fun assertOwned(
    body: RemoteDrawScope.(RemoteFloat) -> Unit,
    vararg declared: Pair<KClass<out RcOperation>, KClass<out Operation>>,
  ) {
    for ((form, bytes) in documents(body)) {
      val (inBody, outside) = bodyAndRest(bytes)
      val operations = androidxOperations(bytes).flattened()
      for ((written, expanded) in declared) {
        val count = inBody.count { written.isInstance(it) }
        assertTrue(count > 0, "$form: no ${written.simpleName} in the body")
        assertTrue(
          outside.none { written.isInstance(it) },
          "$form: a ${written.simpleName} outside the body",
        )
        assertEquals(
          2 * count,
          operations.count { expanded.isInstance(it) },
          "$form: ${expanded.simpleName}s after expansion",
        )
      }
    }
  }

  @Test
  fun aRuntimeShaderWithAUniformOfTheParameter() {
    assertOwned(
      body = { value ->
        drawRect(
          RemotePaint {
            shader =
              RemoteRuntimeShader(
                "uniform float level; half4 main(float2 p) { return half4(level, 0, 0, 1); }",
                mapOf("level" to listOf(value * 2f)),
                emptyMap(),
              )
          },
        )
      },
      RcShaderData::class to ShaderData::class,
    )
  }

  /** Each expansion's shader reads its own uniform expression, which reads its own argument. */
  @Test
  fun eachCallGetsItsOwnShaderUniform() {
    val bytes = documents { value ->
      drawRect(
        RemotePaint {
          shader =
            RemoteRuntimeShader(
              "uniform float level; half4 main(float2 p) { return half4(level, 0, 0, 1); }",
              mapOf("level" to listOf(value * 2f)),
              emptyMap(),
            )
        },
      )
    }
      .getValue("pattern")
    val operations = androidxOperations(bytes).flattened()
    val expressions = operations.filterIsInstance<FloatExpression>().associateBy { it.mId }
    val shaders = operations.filterIsInstance<ShaderData>()
    assertEquals(2, shaders.map { it.field<Int>("mShaderID") }.toSet().size)
    val uniformInputs = shaders.map { shader ->
      val uniform = shader.getUniformFloats("level").single()
      assertTrue(uniform.isNaN(), "the uniform is a variable")
      expressions
        .getValue(uniform.toRawBits() and 0x3fffff)
        .mSrcValue
        .filter { it.isNaN() }
        .map { it.toRawBits() and 0x3fffff }
    }
    assertEquals(
      2,
      uniformInputs.toSet().size,
      "each shader reads its own argument: $uniformInputs",
    )
  }

  /** A path with a coordinate that is the parameter. */
  @Test
  fun aPathBuiltFromTheParameter() {
    assertOwned(
      body = { value ->
        val x = value.getFloatIdForCreationState(remoteComposeCreationState)
        val path =
          RemotePath().apply {
            moveTo(0f, 0f)
            lineTo(x, 10f)
            lineTo(0f, 10f)
            close()
          }
        drawPath(path, RemotePaint { color = RemoteColor(Color.Red) })
      },
      RcPathData::class to PathData::class,
    )
  }

  /** A text formatted from the parameter and merged with another. */
  @Test
  fun aTextFormattedAndMergedFromTheParameter() {
    assertOwned(
      body = { value ->
        val text = "v=".rs + (value * 2f).toRemoteString(DecimalFormat())
        drawText(text, 10f.rf, 20f.rf, RemotePaint { color = RemoteColor(Color.Red) })
      },
      RcTextFromFloat::class to TextFromFloat::class,
      RcTextMerge::class to TextMerge::class,
    )
  }

  /** A colour expression of the parameter. */
  @Test
  fun aColourExpressionOfTheParameter() {
    assertOwned(
      body = { value ->
        val color = lerp(RemoteColor(Color.Red), RemoteColor(Color.Blue), value * 0.1f)
        drawRect(RemotePaint { this.color = color })
      },
      RcColorExpression::class to ColorExpression::class,
    )
  }

  /**
   * What reads an id an operation written in the body defines is the body's too: a text attribute
   * is measured where it is called, and an expression of it is declared with it, not ahead of the
   * layout where the attribute's id means nothing after expansion.
   */
  @Test
  fun anExpressionOfSomethingDefinedInTheBody() {
    val writer = RemoteDocumentWriter(200, 100, profiles = ProfileAndroidx or ProfileExperimental)
    with(writer) {
      val text = addText("measured")
      root {
        val parameter = nextId()
        definePattern(nextId(), intArrayOf(parameter))
        val width = textAttribute(text, TextAttributeType.MEASURE_WIDTH)
        val doubled = floatExpression(width, 2f, Mul)
        drawRect(0f, 0f, doubled, 10f)
        endPatternDefine()
        // After the body the same expression is asked for again: it is not the body's id.
        val again = floatExpression(width, 2f, Mul)
        assertTrue(again.toRawBits() != doubled.toRawBits(), "an id of the ended body was reused")
        assertTrue(isRetired(doubled.toRawBits() and 0x3fffff))
      }
    }
    val operations = RcDocumentCodec.decode(writer.encodeToByteArray()).operations
    val define = operations.filterIsInstance<RcMacroDefine>().single()
    val body = RcDocumentCodec.decodeOperations(define.body)
    val attribute = body.filterIsInstance<RcTextAttribute>().single()
    assertTrue(
      body.filterIsInstance<RcFloatExpression>().any { expression ->
        expression.expression.any { it.referencedId == attribute.outId }
      },
      "the expression of the attribute is not in the body: $body",
    )
  }

  /**
   * An id of an expression of the parameter taken while the body is being recorded (for a path's
   * coordinate, say) is declared before the writer knows the pattern exists. That cannot be put
   * right afterwards, so it fails where it happens instead of writing a pattern that reads an id
   * nothing binds.
   */
  @Test
  fun anExpressionOfTheParameterDeclaredBeforeItsBodyIsRefused() {
    val failure =
      assertFailsWith<IllegalStateException> {
        bytes {
          val pattern =
            remoteCanvas.definePattern<RemoteFloat> { value ->
              val x = (value * 2f).getFloatIdForCreationState(remoteComposeCreationState)
              drawRect(RemotePaint { color = RemoteColor(Color.Red) })
              check(x.isNaN())
            }
          pattern(level)
        }
      }
    assertTrue("before its body" in failure.message.orEmpty(), failure.message)
  }

  private companion object {
    const val CONTAINER_END = 214
    val Mul: Float = Float.fromBits(0x7fc00000 or 0x310003)
  }
}
