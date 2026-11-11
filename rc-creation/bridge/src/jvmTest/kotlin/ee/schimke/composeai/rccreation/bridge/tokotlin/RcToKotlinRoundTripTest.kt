package ee.schimke.composeai.rccreation.bridge.tokotlin

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.CorpusButtonFilled
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.CorpusTextBody
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.CorpusTitleCard
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.ImageSampleDp
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.InteractionSampleDp
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.InteractionSampleLegacy
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.LayoutSampleDp
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.LayoutSampleLegacy
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.NamedValuesSampleDp
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.TextSampleDp
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.TextSampleLegacy
import ee.schimke.composeai.rccreation.bridge.tokotlin.generated.VariableLengthsSampleDp
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensityBehavior
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rcplayer.edit.RcKotlinOptions
import ee.schimke.composeai.rcplayer.edit.toRemoteComposeKotlin
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCollapsibleRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFitBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcFlowLayout
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * The round trip `toRemoteComposeKotlin` promises: capture hand-written remote content, turn the
 * document back into Kotlin, and the generated source — committed under `generated/`, so it is
 * compiled with this test — captures to the same document, operation for operation.
 *
 * Catalog corpus documents draw through canvases the generator can only mark, so for those the
 * promise is weaker and checked as such: the source compiles, captures, and lays out the same
 * component tree.
 *
 * Regenerate the committed sources after a generator change with `./gradlew
 * :rc-creation-bridge:jvmTest -Prc.toKotlin.update=true`, then run `ktfmtFormat`; the comparison
 * ignores formatting, so the formatted file still matches.
 */
class RcToKotlinRoundTripTest {
  private class Case(
    val name: String,
    val behavior: RemoteDensityBehavior,
    val source: @Composable @RemoteComposable () -> Unit,
    val generated: @Composable @RemoteComposable () -> Unit,
  )

  private class CorpusCase(
    val name: String,
    val file: String,
    val generated: @Composable @RemoteComposable () -> Unit,
  )

  private val cases =
    listOf(
      Case("LayoutSampleDp", Dp, { LayoutSample() }, { LayoutSampleDp() }),
      Case("LayoutSampleLegacy", Legacy, { LayoutSample() }, { LayoutSampleLegacy() }),
      Case("TextSampleDp", Dp, { TextSample() }, { TextSampleDp() }),
      Case("TextSampleLegacy", Legacy, { TextSample() }, { TextSampleLegacy() }),
      Case("NamedValuesSampleDp", Dp, { NamedValuesSample() }, { NamedValuesSampleDp() }),
      Case("InteractionSampleDp", Dp, { InteractionSample() }, { InteractionSampleDp() }),
      Case(
        "InteractionSampleLegacy",
        Legacy,
        { InteractionSample() },
        { InteractionSampleLegacy() },
      ),
      Case("ImageSampleDp", Dp, { ImageSample() }, { ImageSampleDp() }),
      Case(
        "VariableLengthsSampleDp",
        Dp,
        { VariableLengthsSample() },
        { VariableLengthsSampleDp() },
      ),
    )

  private val corpusCases =
    listOf(
      CorpusCase(
        "CorpusTextBody",
        "text-body__ideal__default__compact.rc",
        { CorpusTextBody() },
      ),
      CorpusCase(
        "CorpusButtonFilled",
        "button-filled__ideal__default__compact.rc",
        { CorpusButtonFilled() },
      ),
      CorpusCase(
        "CorpusTitleCard",
        "titlecard__ideal__content-image__compact.rc",
        { CorpusTitleCard() },
      ),
    )

  private val update = System.getProperty("rc.toKotlin.update") == "true"
  private val generatedDir =
    File("src/jvmTest/kotlin/ee/schimke/composeai/rccreation/bridge/tokotlin/generated")
  private val corpusDir = File("../../scripts/rc-catalog-corpus/corpus")

  private fun capture(
    behavior: RemoteDensityBehavior,
    dpi: Int,
    content: @Composable @RemoteComposable () -> Unit,
  ): RcDocument = runBlocking {
    RcDocumentCodec.decode(
      captureCommonRemoteDocument(
        RemoteCreationDisplayInfo(360, 480, dpi, densityBehavior = behavior),
        encodePng = ::encodePng,
        content = content,
      ),
    )
  }

  /** Checks [code] against its committed file, or rewrites the file; false when they differ. */
  private fun committed(name: String, code: String, failures: MutableList<String>): Boolean {
    val file = File(generatedDir, "$name.kt")
    if (update) {
      file.writeText(code)
      return false
    }
    if (normalize(file.readText()) == normalize(code)) return true
    failures += "$name: generated source changed; rerun with -Prc.toKotlin.update=true\n$code"
    return false
  }

  private fun options(name: String, density: Float) =
    RcKotlinOptions(
      packageName = "ee.schimke.composeai.rccreation.bridge.tokotlin.generated",
      functionName = name,
      density = density,
    )

  @Test
  fun generatedSourceCapturesToTheDocumentItCameFrom() {
    val failures = mutableListOf<String>()
    cases.forEach { case ->
      val original = capture(case.behavior, DENSITY_DPI, case.source)
      val source = original.toRemoteComposeKotlin(options(case.name, DENSITY_DPI / 160f))
      assertTrue(source.isComplete, "${case.name}: ${source.unsupported}")
      if (!committed(case.name, source.code, failures)) return@forEach
      val recaptured = capture(case.behavior, DENSITY_DPI, case.generated)
      if (pixels(recaptured) != pixels(original)) {
        failures +=
          "${case.name}: the generated source captures a different document\n" +
            diff(original, recaptured)
      }
    }
    if (failures.isNotEmpty()) fail(failures.joinToString("\n\n"))
  }

  @Test
  fun corpusDocumentsBecomeSourceThatLaysOutTheSameTree() {
    val failures = mutableListOf<String>()
    corpusCases.forEach { case ->
      val original = RcDocumentCodec.decode(File(corpusDir, case.file).readBytes())
      // The corpus records no capture density, so it reads back at 1.
      val source = original.toRemoteComposeKotlin(options(case.name, 1f))
      if (!committed(case.name, source.code, failures)) return@forEach
      val recaptured = capture(Legacy, 160, case.generated)
      assertEquals(skeleton(original), skeleton(recaptured), case.name)
    }
    if (failures.isNotEmpty()) fail(failures.joinToString("\n\n"))
  }

  @Test
  fun everyCaseHasACommittedSource() {
    assertEquals(
      (cases.map { it.name } + corpusCases.map { it.name }).map { "$it.kt" }.toSet(),
      generatedDir.listFiles()!!.map { it.name }.toSet(),
    )
  }

  /**
   * [document] with each embedded PNG replaced by its pixels: a bitmap goes through a decode and
   * re-encode on the way round, which keeps every pixel but not the encoder's metadata chunks.
   */
  private fun pixels(document: RcDocument): RcDocument =
    document.copy(
      operations =
        document.operations.map { operation ->
          if (operation !is RcBitmapData || operation.encoding != RcBitmapData.ENCODING_INLINE) {
            return@map operation
          }
          val bitmap = Bitmap.makeFromImage(Image.makeFromEncoded(operation.data))
          operation.copy(type = -1, data = bitmap.readPixels()!!)
        },
    )

  /** The kinds of layout component a document builds, in document order. */
  private fun skeleton(document: RcDocument): List<String> =
    document.operations.mapNotNull { operation ->
      when (operation) {
        is RcBoxLayout -> "Box"
        is RcRowLayout -> "Row"
        is RcColumnLayout -> "Column"
        is RcFlowLayout -> "FlowRow"
        is RcFitBoxLayout -> "FitBox"
        is RcCollapsibleRowLayout -> "CollapsibleRow"
        is RcCollapsibleColumnLayout -> "CollapsibleColumn"
        is RcCanvasLayout -> "Canvas"
        is RcImageLayout -> "Image"
        is RcCoreText,
        is RcTextLayout -> "Text"
        else -> null
      }
    }

  /**
   * Whitespace, trailing commas, statement semicolons and where a long comment wraps are ktfmt's,
   * not the generator's.
   */
  private fun normalize(code: String): String =
    code
      .replace(Regex("\\s+"), "")
      .replace("//", "")
      .replace(";", "")
      .replace(",)", ")")
      .replace(",]", "]")

  private fun diff(expected: RcDocument, actual: RcDocument): String = buildString {
    if (expected.header != actual.header)
      appendLine("header: ${expected.header} != ${actual.header}")
    val size = maxOf(expected.operations.size, actual.operations.size)
    for (index in 0 until size) {
      val e = expected.operations.getOrNull(index)
      val a = actual.operations.getOrNull(index)
      if (e != a) appendLine("#$index\n  expected $e\n  actual   $a")
    }
  }

  private companion object {
    const val DENSITY_DPI = 320
    val Dp = RemoteDensityBehavior.Dp
    val Legacy = RemoteDensityBehavior.Legacy

    fun encodePng(bitmap: ImageBitmap): ByteArray? =
      Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
  }
}
