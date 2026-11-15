package ee.schimke.composeai.rccreation.bridge.tokotlin

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import ee.schimke.composeai.rccreation.bridge.ExperimentalProfile
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensityBehavior
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rcplayer.edit.structuralDiff
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * A capture is a pure function of its content and its display: the same composable captures to the
 * same bytes, down to the bit, every time — in one JVM after other captures have run, and in a JVM
 * of its own. A build that commits or publishes captures (Livery's sample catalogs, a catalog's
 * blobs, content-addressed by hash) depends on it: a capture that differs from run to run is a
 * spurious change in every review and a cache miss on every build.
 *
 * On a mismatch the failure explains the difference structurally (`structuralDiff`).
 */
class RcCaptureDeterminismTest {
  @Test
  fun aCaptureIsTheSameBytesEveryTimeInOneJvm() {
    // Interleaved, so that what one capture leaves behind (a cache, a counter, a hash seed) is
    // there for the next capture of another sample.
    val first = SAMPLES.mapValues { (_, content) -> capture(content) }
    val second = SAMPLES.mapValues { (_, content) -> capture(content) }
    val third = SAMPLES.entries.reversed().associate { (name, content) -> name to capture(content) }
    val failures =
      SAMPLES.keys.flatMap { name ->
        listOfNotNull(
          mismatch("$name (second capture)", first.getValue(name), second.getValue(name)),
          mismatch(
            "$name (third capture, reversed order)",
            first.getValue(name),
            third.getValue(name),
          ),
        )
      }
    if (failures.isNotEmpty()) fail(failures.joinToString("\n\n"))
  }

  @Test
  fun aCaptureIsTheSameBytesInAnotherJvm() {
    val here = SAMPLES.mapValues { (_, content) -> capture(content) }
    val out = Files.createTempDirectory("rc-determinism").toFile()
    try {
      val java = File(System.getProperty("java.home"), "bin/java").path
      val process =
        ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            // Another hash seed, locale and time zone than the test JVM's defaults.
            "-Duser.language=ar",
            "-Duser.country=EG",
            "-Duser.timezone=Pacific/Kiritimati",
            "-Dfile.encoding=ISO-8859-1",
            RcCaptureDeterminismMain::class.java.name,
            out.path,
          )
          .redirectErrorStream(true)
          .start()
      val log = process.inputStream.bufferedReader().readText()
      check(process.waitFor() == 0) { "the forked capture failed:\n$log" }
      val failures = here.mapNotNull { (name, bytes) ->
        mismatch("$name (forked JVM)", bytes, File(out, "$name.rc").readBytes())
      }
      if (failures.isNotEmpty()) fail(failures.joinToString("\n\n"))
    } finally {
      out.deleteRecursively()
    }
  }

  private fun mismatch(label: String, expected: ByteArray, actual: ByteArray): String? {
    if (expected.contentEquals(actual)) return null
    val diff = runCatching {
      RcDocumentCodec.decode(expected)
        .structuralDiff(RcDocumentCodec.decode(actual), "first", "again")
        .toString()
    }
      .getOrElse { "(could not decode: $it)" }
    return "$label: ${expected.size} bytes, then ${actual.size}\n$diff"
  }

  internal companion object {
    val SAMPLES: Map<String, @Composable @RemoteComposable () -> Unit> =
      linkedMapOf(
        "LayoutSample" to { LayoutSample() },
        "TextSample" to { TextSample() },
        "NamedValuesSample" to { NamedValuesSample() },
        "InteractionSample" to { InteractionSample() },
        "ImageSample" to { ImageSample() },
        "VariableLengthsSample" to { VariableLengthsSample() },
      )

    fun capture(content: @Composable @RemoteComposable () -> Unit): ByteArray = runBlocking {
      captureCommonRemoteDocument(
        RemoteCreationDisplayInfo(360, 480, 320, densityBehavior = RemoteDensityBehavior.Dp),
        profile = ExperimentalProfile,
        encodePng = ::encodePng,
        content = content,
      )
    }

    private fun encodePng(bitmap: ImageBitmap): ByteArray? =
      Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
  }
}

/**
 * The forked half of [RcCaptureDeterminismTest]: captures every sample into the directory given.
 */
object RcCaptureDeterminismMain {
  @JvmStatic
  fun main(args: Array<String>) {
    val out = File(args.single())
    RcCaptureDeterminismTest.SAMPLES.forEach { (name, content) ->
      File(out, "$name.rc").writeBytes(RcCaptureDeterminismTest.capture(content))
    }
  }
}
