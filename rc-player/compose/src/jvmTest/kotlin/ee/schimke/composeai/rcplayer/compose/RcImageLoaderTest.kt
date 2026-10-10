package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcImageLoaderTest {
  @Test
  fun coilLoadsAFileByDefault() =
    runSkikoComposeUiTest(size = Size(8f, 8f), density = Density(1f)) {
      val file = Files.createTempFile("rc-coil-image", ".png").toFile()
      try {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 8) for (x in 0 until 8) image.setRGB(x, y, 0xffff0000.toInt())
        ImageIO.write(image, "png", file)
        val bitmap =
          externalBitmap()
            .copy(
              encoding = RcBitmapData.ENCODING_FILE,
              data = file.toURI().toString().encodeToByteArray(),
            )
        setContent {
          RcComposePlayer(
            document(listOf(bitmap, RcDrawBitmapInt(42, 0, 0, 8, 8, 0, 0, 8, 8, 0))),
            Modifier.testTag("player"),
          )
        }
        waitUntil(timeoutMillis = 5_000) {
          onNodeWithTag("player").captureToImage().toPixelMap()[4, 4] == Color.Red
        }
      } finally {
        file.delete()
      }
    }

  @Test
  fun compositionLocalOverridesTheCoilDefault() =
    runSkikoComposeUiTest(size = Size(8f, 8f), density = Density(1f)) {
      var called = false
      val loader = RcImageLoader {
        called = true
        redImage()
      }
      setContent {
        CompositionLocalProvider(LocalRcImageLoader provides loader) {
          RcComposePlayer(document(listOf(externalBitmap())))
        }
      }
      waitForIdle()
      assertTrue(called)
    }

  @Test
  fun replacingTheLoaderCancelsTheOldRequest() =
    runSkikoComposeUiTest(size = Size(8f, 8f), density = Density(1f)) {
      val pending = CompletableDeferred<ImageBitmap?>()
      var cancelled = false
      val old = RcImageLoader {
        try {
          pending.await()
        } finally {
          cancelled = true
        }
      }
      val current = mutableStateOf(old)
      val document = document(listOf(externalBitmap()))
      setContent { RcComposePlayer(document, current.value) }
      waitForIdle()
      runOnIdle { current.value = RcImageLoader.Empty }
      waitForIdle()
      assertTrue(cancelled)
    }

  @Test
  fun completionRepaintsACanvasAndTheHostReceivesTheWireRequest() =
    runSkikoComposeUiTest(size = Size(80f, 80f), density = Density(1f)) {
      val result = CompletableDeferred<ImageBitmap?>()
      var request: RcBitmapData? = null
      val loader = RcImageLoader {
        request = it
        result.await()
      }
      val bitmap = externalBitmap()
      val document =
        document(listOf(bitmap, RcDrawBitmapInt(42, 0, 0, 8, 8, 0, 0, 80, 80, 0)), size = 80)
      setContent { RcComposePlayer(document, loader, Modifier.testTag("player")) }
      waitForIdle()
      assertEquals(bitmap, request)
      writeEvidence("before.png", onNodeWithTag("player").captureToImage())
      result.complete(redImage())
      waitForIdle()
      val rendered = onNodeWithTag("player").captureToImage()
      assertEquals(Color.Red, rendered.toPixelMap()[40, 40])
      writeEvidence("after.png", rendered)
    }

  @Test
  fun loadsAndDrawsAnExternalImageDeclaredInsideACalledMacro() =
    runSkikoComposeUiTest(size = Size(8f, 8f), density = Density(1f)) {
      val requests = mutableListOf<RcBitmapData>()
      val loader = RcImageLoader { bitmap ->
        requests += bitmap
        redImage()
      }
      val body =
        RcWireWriter()
          .apply {
            RcDocumentCodec.encodeOperation(this, externalBitmap())
            RcDocumentCodec.encodeOperation(this, RcDrawBitmapInt(42, 0, 0, 8, 8, 0, 0, 8, 8, 0))
          }
          .toByteArray()
      val document =
        document(
          listOf(
            RcMacroDefine(9, emptyList(), body),
            RcMacroCall(9, emptyList()),
            RcNoArg(RcOpcodes.CONTAINER_END),
          )
        )
      setContent { RcComposePlayer(document, loader, Modifier.testTag("player")) }
      waitForIdle()
      val rendered = onNodeWithTag("player").captureToImage()
      writeEvidence("macro.png", rendered)
      assertEquals(1, requests.size)
      assertEquals(externalBitmap().data.decodeToString(), requests.single().data.decodeToString())
      assertEquals(Color.Red, rendered.toPixelMap()[4, 4])
    }

  @Test
  fun loadsABitmapDeclaredInAnUnincludedBlockWhenTheRootDrawsIt() =
    runSkikoComposeUiTest(size = Size(8f, 8f), density = Density(1f)) {
      var calls = 0
      val loader = RcImageLoader {
        calls += 1
        redImage()
      }
      val document =
        document(
          listOf(
            RcReferencedOperations(7),
            externalBitmap(),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcDrawBitmapInt(42, 0, 0, 8, 8, 0, 0, 8, 8, 0),
          )
        )
      setContent { RcComposePlayer(document, loader, Modifier.testTag("player")) }
      waitForIdle()
      assertEquals(1, calls)
      assertEquals(Color.Red, onNodeWithTag("player").captureToImage().toPixelMap()[4, 4])
    }

  @Test
  fun replacingADocumentCancelsItsPendingImageRequest() =
    runSkikoComposeUiTest(size = Size(8f, 8f), density = Density(1f)) {
      val pending = CompletableDeferred<ImageBitmap?>()
      var cancelled = false
      val loader = RcImageLoader {
        try {
          pending.await()
        } finally {
          cancelled = true
        }
      }
      val current = mutableStateOf(document(listOf(externalBitmap())))
      setContent { RcComposePlayer(current.value, loader) }
      waitForIdle()
      runOnIdle { current.value = document(emptyList()) }
      waitForIdle()
      assertTrue(cancelled)
    }

  @Test
  fun reportRequiresTheHostImageUnlessItHasBeenSupplied() {
    val document = document(listOf(externalBitmap()))
    assertFalse(document.composeSupportReport().playable)
    assertTrue(document.composeSupportReport(availableImageIds = setOf(42)).fullyRenderable)
  }

  private fun externalBitmap() =
    RcBitmapData(
      42,
      8,
      8,
      RcBitmapData.TYPE_PNG,
      RcBitmapData.ENCODING_URL,
      "https://images.example/image.png".encodeToByteArray(),
    )

  private fun document(operations: List<RcOperation>, size: Int = 8) =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = size, legacyHeight = size, modern = false),
      operations,
    )

  private fun redImage(): ImageBitmap =
    ImageBitmap(8, 8).also {
      Canvas(it).drawRect(Rect(0f, 0f, 8f, 8f), Paint().apply { color = Color.Red })
    }

  private fun writeEvidence(name: String, image: ImageBitmap) {
    val output = System.getProperty("rc.imageLoader.out") ?: return
    val pixels = image.toPixelMap()
    val png = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until image.height) for (x in 0 until image.width) {
      png.setRGB(x, y, pixels[x, y].toArgb())
    }
    val directory = File(output).apply { mkdirs() }
    ImageIO.write(png, "png", File(directory, name))
  }
}
