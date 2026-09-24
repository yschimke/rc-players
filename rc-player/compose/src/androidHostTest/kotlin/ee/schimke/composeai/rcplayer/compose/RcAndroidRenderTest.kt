package ee.schimke.composeai.rcplayer.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcIdOperation
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcPathCommands
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The CMP player drawing on Android, through `android.graphics` rather than Skiko — the only place
 * the `androidMain` actuals (raw bitmaps, conics, path measuring, fonts from bytes) really render.
 * Robolectric's native graphics mode runs the real Android canvas on the host.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A window larger than any document. Compose lays the content out at the window's size, and the
// default 320 px window would squeeze a 384 px catalog document — wrapping its text — whatever the
// harness measures the view at afterwards.
@Config(sdk = [34], qualifiers = "w1000dp-h1000dp")
class RcAndroidRenderTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private var content by mutableStateOf<Pair<RcDocument, RcTypefaceLoader>?>(null)
  private var size = 0

  private fun render(
    document: RcDocument,
    px: Int = 8,
    density: Float = 1f,
    manualClock: Boolean = false,
    typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
  ): Bitmap {
    if (size == 0) {
      size = px
      composeRule.setContent {
        val scene = Density(density, 1f)
        CompositionLocalProvider(LocalDensity provides scene) {
          Box(Modifier.size(with(scene) { size.toDp() })) {
            content?.let { (document, typefaces) ->
              key(document, typefaces) {
                RcComposePlayer(document, modifier = Modifier.fillMaxSize(), typefaces = typefaces)
              }
            }
          }
        }
      }
    }
    content = document to typefaces
    if (manualClock) {
      // A document that animates forever (an interactive page indicator, a pulsing icon button)
      // asks for a frame every frame, so with auto-advance on Compose never goes idle. Stepping a
      // fixed 100 ms instead renders the same instant every run.
      composeRule.mainClock.autoAdvance = false
      // A frame for the new document to compose, then the fixed instant. Without the first step
      // the capture below draws whatever was on screen before — the previous document.
      repeat(SETTLE_FRAMES) {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
      }
      composeRule.mainClock.advanceTimeBy(100)
    }
    composeRule.waitForIdle()
    val root = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
    root.measure(
      MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
    )
    root.layout(0, 0, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
  }

  @Test
  fun fillsARectWithThePaintColor() {
    val bitmap = render(document(RcPaintData(listOf(COLOR, RED)), rect(0f, 0f, 8f, 8f)))
    assertEquals(RED, bitmap.getPixel(4, 4))
  }

  @Test
  fun aPausedClockCaptureShowsTheDocumentJustSet() {
    // The catalog sweep drives the clock by hand. Each capture has to be the document it just set,
    // not the previous one still on screen.
    val red = document(RcPaintData(listOf(COLOR, RED)), rect(0f, 0f, 8f, 8f))
    val blue = document(RcPaintData(listOf(COLOR, BLUE)), rect(0f, 0f, 8f, 8f))
    assertEquals(RED, render(red, manualClock = true).getPixel(4, 4))
    assertEquals(BLUE, render(blue, manualClock = true).getPixel(4, 4))
    assertEquals(RED, render(red, manualClock = true).getPixel(4, 4))
  }

  @Test
  fun drawsARawBitmapUnfilteredWhenFilteringIsOff() {
    // Black beside white stretched to 8 px, as the JVM test does: with FILTER_BITMAP off the pixel
    // left of the middle is the black source pixel, not a blend.
    val bitmap =
      render(
        document(
          RcBitmapData(
            20,
            2,
            1,
            RcBitmapData.TYPE_RAW8888,
            RcBitmapData.ENCODING_INLINE,
            byteArrayOf(0, 0, 0, -1, -1, -1, -1, -1),
          ),
          RcPaintData(listOf(FILTER_BITMAP)),
          RcDrawBitmap(
            20,
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(8f),
            RcFloatWord.literal(8f),
            0,
          ),
        )
      )
    assertEquals(BLACK, bitmap.getPixel(3, 4))
    assertEquals(WHITE, bitmap.getPixel(4, 4))
  }

  @Test
  fun fillsAConicQuarterCircleThroughTheQuadSplit() {
    // A quarter disc of radius 64 from the corner: move to (64,0), conic through the corner control
    // (64,64) to (0,64) at weight √2/2, line back to the origin. Android has no conic, so this is
    // the path the Skia-style split draws.
    val nan = { command: Int -> RcFloatWord(0x7fc00000 or command) }
    val lit = RcFloatWord::literal
    val w = (sqrt(2.0) / 2).toFloat()
    val path =
      RcPathData(
        30,
        listOf(
          nan(RcPathCommands.MOVE),
          lit(64f),
          lit(0f),
          nan(RcPathCommands.CONIC),
          lit(64f),
          lit(0f),
          lit(64f),
          lit(64f),
          lit(0f),
          lit(64f),
          lit(w),
          nan(RcPathCommands.LINE),
          lit(0f),
          lit(64f),
          lit(0f),
          lit(0f),
          nan(RcPathCommands.CLOSE),
          nan(RcPathCommands.DONE),
        ),
      )
    val bitmap =
      render(
        document(path, RcPaintData(listOf(COLOR, RED)), RcIdOperation(RcOpcodes.DRAW_PATH, 30), size = 80),
        px = 80,
      )
    // Well inside the arc (distance ~42 from the corner) is filled; well outside it (~76) is not.
    assertEquals(RED, bitmap.getPixel(30, 30))
    assertNotEquals(RED, bitmap.getPixel(54, 54))
  }

  @Test
  fun rendersOneDocumentOfEveryCatalogComponent() {
    // The committed remote-m3 sticker sheet — the catalog this player is meant to serve. One
    // document per component family keeps the run short while touching every family's ops.
    val corpus = File("../../scripts/rc-catalog-corpus/corpus")
    val samples =
      corpus
        .listFiles { file -> file.name.endsWith(".rc") }
        .orEmpty()
        .sortedBy { it.name }
        .groupBy { it.name.substringBefore("__") }
        .values
        .map { it.first() }
    assertTrue("no catalog corpus under ${corpus.absolutePath}", samples.size > 50)

    // Fonts as a host would supply them: each document's `google:` families from the shared
    // cache (the wasm host's vendored files, which use its names), everything else — `default` is
    // Roboto Flex — from the same directory's manifest.
    val fonts = File("../wasm/dist-assets/fonts")
    val manifest = runBlocking {
      RcManifestTypefaceLoader { url -> File(url).readBytes() }.load(fonts.path)
    }
    val failures = mutableListOf<String>()
    var blank = 0
    for (file in samples) {
      val outcome = runCatching {
        val document = RcDocumentCodec.decode(file.readBytes())
        val families = rcDownloadableFontRequests(document).map { it.family }
        render(
          document,
          px = 384,
          density = 2f,
          manualClock = true,
          typefaces = RcGoogleFontsTypefaceLoader(families, manifest, RcSharedFontCache(fonts)),
        )
      }
      val bitmap = outcome.getOrNull()
      if (bitmap == null) {
        failures += "${file.name}: ${outcome.exceptionOrNull()}"
        continue
      }
      val pixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      if (pixels.all { it == pixels[0] }) blank++
      // `-Prc.android.out=<abs dir>` keeps the renders, for eyeballing against the other lanes.
      System.getProperty("rc.android.out")?.let { out ->
        File(out).apply { mkdirs() }.resolve(file.name.removeSuffix(".rc") + ".png").outputStream().use {
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
      }
    }
    assertTrue("documents failed to render on Android:\n${failures.joinToString("\n")}", failures.isEmpty())
    // A few components (an indicator at rest, an empty container) can legitimately draw one flat
    // colour; most must not.
    assertTrue("$blank of ${samples.size} catalog documents rendered blank", blank * 10 < samples.size)
  }

  @Test
  fun drawsAGoogleFamilyFromTheSharedFontCache() {
    // The shared Google Fonts cache the embedded player reads, pointed offline at the files the
    // wasm host vendors — they use the cache's `<slug>-<weight>.ttf` names. The branded text
    // sticker names `google:Orbitron`; with the loader it must draw differently from the default
    // face.
    val cache = RcSharedFontCache(File("../wasm/dist-assets/fonts"))
    val document =
      RcDocumentCodec.decode(catalogDocument("text-branded__ideal__default__compact.rc"))
    val families = rcDownloadableFontRequests(document).map { it.family }
    assertEquals(listOf("Orbitron"), families)
    val loader = RcGoogleFontsTypefaceLoader(families, RcTypefaceLoader.Default, cache)
    assertTrue("orbitron" in loader.families)
    assertTrue(
      document.composeSupportReport(availableFontFamilies = loader.families).fullyRenderable
    )

    val plain = render(document, px = 384, density = 2f).pixels()
    val branded = render(document, px = 384, density = 2f, typefaces = loader).pixels()
    val changed = plain.indices.count { plain[it] != branded[it] }
    assertTrue("Orbitron drew the same pixels as the default face", changed > 200)
  }

  @Test
  fun leavesFamiliesItWasNotGivenToTheFallback() {
    val loader =
      RcGoogleFontsTypefaceLoader(
        listOf("Orbitron"),
        RcTypefaceLoader.Empty,
        RcSharedFontCache(File("../wasm/dist-assets/fonts")),
      )
    assertEquals(null, loader.typeface("lobster two"))
    assertTrue(loader.typeface("orbitron") != null)
  }

  @Test
  fun readsTheSharedCacheByGoogleFontKeyNames() {
    // `GoogleFontKey.slugify`'s rules, pinned: the two players must read the same file.
    assertEquals("roboto-flex", RcSharedFontCache.slug("Roboto Flex"))
    assertEquals("jetbrains-mono", RcSharedFontCache.slug("  JetBrains   Mono! "))
    assertEquals("font", RcSharedFontCache.slug("***"))
    val cache = RcSharedFontCache(File("../wasm/dist-assets/fonts"))
    assertEquals("orbitron-700.ttf", cache.static("Orbitron", 700, italic = false)?.name)
    assertEquals(null, cache.static("Orbitron", 700, italic = true))
    assertEquals(null, cache.variable("Orbitron", italic = false))
  }

  private fun Bitmap.pixels(): IntArray =
    IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

  private fun catalogDocument(name: String): ByteArray =
    File("../../scripts/rc-catalog-corpus/corpus", name).readBytes()

  private fun document(vararg operations: RcOperation, size: Int = 8) =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = size, legacyHeight = size, modern = false),
      operations.toList(),
    )

  private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
    RcDraw4(
      RcOpcodes.DRAW_RECT,
      RcFloatWord.literal(left),
      RcFloatWord.literal(top),
      RcFloatWord.literal(right),
      RcFloatWord.literal(bottom),
    )

  private companion object {
    const val RED = 0xffff0000.toInt()
    const val BLUE = 0xff0000ff.toInt()
    /** Frames for a newly set document to compose, lay out and resolve its fonts. */
    val SETTLE_FRAMES = System.getProperty("rc.android.settleFrames")?.toInt() ?: 3
    const val BLACK = 0xff000000.toInt()
    const val WHITE = 0xffffffff.toInt()
    /** `PaintBundle.COLOR`; the ARGB word follows. */
    const val COLOR = 4
    /** `PaintBundle.FILTER_BITMAP`; its on/off value rides in the high 16 bits. */
    const val FILTER_BITMAP = 17
  }
}
