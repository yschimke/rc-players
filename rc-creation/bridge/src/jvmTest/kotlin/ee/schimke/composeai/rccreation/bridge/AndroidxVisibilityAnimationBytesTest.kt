package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.common.AnimatedFloatExpression
import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.common.Utils
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.createRawRcBuffer
import androidx.compose.remote.creation.profile.Profile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The creation fork writes component offscreen buffers and visibility animation functions byte for
 * byte as AndroidX remote-creation-core 1.0.0-alpha22 does: its `RemoteComposeWriter`
 * (`createOffscreenBitmap`, `drawComponentToBitmap`, `createFloatFunction`, `callFloatFunction`)
 * against the fork's `RemoteDocumentWriter`, and its `RcScope.defineVisibilityAnimation` DSL
 * against this module's [defineVisibilityAnimation].
 *
 * Each side's operations are compared after its own header, which the two writers word differently.
 * A function written first lands in the same place in both: the fork moves a definition ahead of
 * the layout, where AndroidX writes it only when it is defined before the layout.
 */
class AndroidxVisibilityAnimationBytesTest {
  private fun androidx(block: RemoteComposeWriter.() -> Unit): ByteArray {
    val header = newAndroidxWriter().encodeToByteArray().size
    val bytes = newAndroidxWriter().apply(block).encodeToByteArray()
    return bytes.copyOfRange(header, bytes.size)
  }

  /**
   * The fork's operations, its id counter first advanced to where AndroidX's writer starts: its
   * constructor allocates ids for its header that the fork's does not.
   */
  private fun fork(
    firstId: Int = newAndroidxWriter().createID(0),
    block: RemoteDocumentWriter.() -> Unit,
  ): ByteArray {
    fun writer() =
      RemoteDocumentWriter(100, 100).apply { repeat(firstId - FirstForkId) { createFloatId() } }
    val header = writer().encodeToByteArray().size
    val bytes = writer().apply(block).encodeToByteArray()
    return bytes.copyOfRange(header, bytes.size)
  }

  private fun newAndroidxWriter() = RemoteComposeWriter(100, 100, "", RcPlatformServices.None)

  @Test
  fun offscreenBuffersMatch() {
    assertContentEquals(
      androidx {
        createOffscreenBitmap()
        createOffscreenBitmap(-12)
        createOffscreenBitmap(0x01020304)
      },
      fork {
        createOffscreenBitmap()
        createOffscreenBitmap(-12)
        createOffscreenBitmap(0x01020304)
      },
    )
  }

  @Test
  fun drawingAComponentToABitmapMatches() {
    assertContentEquals(
      androidx {
        val bitmap = createOffscreenBitmap()
        drawComponentToBitmap(-5, bitmap)
        drawComponentToBitmap(bitmap)
      },
      fork {
        val bitmap = createOffscreenBitmap()
        drawComponentToBitmap(-5, bitmap)
        drawComponentToBitmap(bitmap)
      },
    )
  }

  @Test
  fun aFunctionWithExpressionsAndAnOffscreenBufferMatches() {
    val androidxArgs = FloatArray(6)
    val forkArgs = FloatArray(6)
    var androidxId = 0
    var forkId = 0
    val expected = androidx {
      androidxId = createFloatFunction(androidxArgs)
      androidxExitBody(this, androidxArgs)
      endFloatFunction()
      callFloatFunction(androidxId, 0.5f, 20f, 10f, 0f, 0f, 0f)
    }
    val actual = fork {
      forkId = createFloatFunction(forkArgs)
      forkExitBody(this, forkArgs)
      endFloatFunction()
      callFloatFunction(forkId, 0.5f, 20f, 10f, 0f, 0f, 0f)
    }
    assertEquals(androidxId, forkId)
    assertContentEquals(androidxArgs.map { it.toRawBits() }, forkArgs.map { it.toRawBits() })
    assertContentEquals(expected, actual)
  }

  @Test
  fun theVisibilityAnimationDslMatches() {
    // The DSL's public entry point, without a root; its header differs from the fork's.
    val header = createRawRcBuffer(androidxProfile) {}.size
    var androidxId = 0
    val expected =
      createRawRcBuffer(androidxProfile) {
          androidxId = defineVisibilityAnimation { component, _, width, height, x, y ->
            val buffer = createOffscreenBitmap()
            drawComponentToBitmap(component, buffer)
            clipRect(x, y, width, height)
            drawComponentToBitmap(buffer)
            drawComponentContent()
          }
        }
        .let { it.copyOfRange(header, it.size) }

    var forkId = 0
    val actual =
      fork(firstId = androidxId) {
        forkId = defineVisibilityAnimation { component, _, width, height, x, y ->
          val buffer = createOffscreenBitmap()
          drawComponentToBitmap(component, buffer)
          clipRect(x, y, width, height)
          drawComponentToBitmap(buffer)
          drawComponentContent()
        }
      }
    assertEquals(androidxId, forkId)
    assertContentEquals(expected, actual)
  }

  /** A wipe from the right drawn from the component's offscreen buffer. */
  private fun androidxExitBody(writer: RemoteComposeWriter, args: FloatArray) =
    with(writer) {
      val component = Utils.idFromNan(args[5])
      val buffer = createOffscreenBitmap(component)
      drawComponentToBitmap(component, buffer)
      val kept = floatExpression(args[1], 1f, args[0], SUB, MUL)
      val right = floatExpression(args[3], kept, ADD)
      save()
      clipRect(args[3], args[4], right, args[2])
      drawComponentContent()
      restore()
    }

  private fun forkExitBody(writer: RemoteDocumentWriter, args: FloatArray) =
    with(writer) {
      val component = Utils.idFromNan(args[5])
      val buffer = createOffscreenBitmap(component)
      drawComponentToBitmap(component, buffer)
      val kept = floatExpression(args[1], 1f, args[0], SUB, MUL)
      val right = floatExpression(args[3], kept, ADD)
      save()
      clipRect(args[3], args[4], right, args[2])
      drawComponentContent()
      restore()
    }

  private companion object {
    /** The first id the fork's writer allocates, as `RemoteComposeState.START_ID`. */
    const val FirstForkId = 42
    /** `RcProfiles.PROFILE_ANDROIDX`, which `DrawToBitmap` needs. */
    const val ProfileAndroidx = 0x200
    @Suppress("RestrictedApi")
    val androidxProfile =
      RcProfile(
        Profile(8, ProfileAndroidx, RcPlatformServices.None) { _, _, _ -> error("not used") },
        experimental = true,
      )
    val ADD = AnimatedFloatExpression.ADD
    val SUB = AnimatedFloatExpression.SUB
    val MUL = AnimatedFloatExpression.MUL
  }
}
