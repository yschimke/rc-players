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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rasterizes captured Remote Compose documents through **this** player on **Android**, so the
 * `rc-compare` wall can carry a `cmp-android` column beside the two AndroidX lanes and `cmp-jvm`.
 *
 * The third host of one implementation: [RcCmpRenderHarness] runs the same player on the desktop
 * JVM through Skiko and `rc-player/wasm` runs it in the browser, so a divergence between this
 * column and `cmp-jvm` is the *host* — Android's text stack, its canvas, its shaders — rather than
 * the interpreter. That is the comparison this lane exists to make: `cmp-android` is the lane a
 * Wear widget actually ships through, and until now it was the only player on the wall with no
 * published column, so every `?rcPlayer=cmp-android` browse paid for a live daemon render.
 *
 * Deliberately the same contract as its three siblings ([RcCmpRenderHarness],
 * `RcEmbeddedRenderHarness`, `RcViewPlayerRenderHarness`): an input directory of `<id>.rc` plus a
 * `manifest.json` of `{id, width, height, density}`, an output directory of `<id>.png`, both named
 * by system property. The driver stages once and every lane reads the same bytes.
 *
 * A harness, not an assertion test. With nothing staged it yields one skipped placeholder case, so
 * `check` stays green without a catalog; a document this player cannot render writes `<id>.error`
 * beside the PNGs rather than failing the run, because "unrendered" is a result the wall reports.
 *
 * **One document per case**, like the embedded harness, and for its second reason: swapping
 * documents inside one composition produced captures holding the *previous* document's pixels,
 * which on a compare page is indistinguishable from a renderer bug.
 *
 * **Fonts come from the vendored manifest**, not from the Google-fonts cache this player uses in
 * the daemon. The wall attributes a difference to the interpreter or the host, so every lane has to
 * shape text with the same faces; `cmp-jvm` loads this same directory. A run that cannot find it
 * fails rather than quietly producing a lane nobody can trust.
 *
 * Density is the document's own ([LocalDensity] per entry) while the Robolectric device stays xhdpi
 * for platform resources — the split [RcEmbeddedRenderHarness] documents, so dp-denominated
 * modifiers rasterize at the geometry the baked reference was captured at.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RcCmpAndroidRenderHarness(private val entry: Entry) {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  /** One document to rasterize, at the baked PNG's pixel size. */
  data class Entry(val id: String, val width: Int, val height: Int, val density: Float = 2f) {
    /** Drives the JUnit case name. */
    override fun toString(): String = id
  }

  @Test
  fun render() {
    val inputDir = inputDir()
    assumeTrue("no $INPUT_PROPERTY configured — nothing to rasterize", inputDir != null)
    val outputDir =
      File(
          requireNotNull(System.getProperty(OUTPUT_PROPERTY)) {
            "$OUTPUT_PROPERTY must be set alongside $INPUT_PROPERTY"
          }
        )
        .apply { mkdirs() }

    val png = File(outputDir, "${entry.id}.png")
    val err = File(outputDir, "${entry.id}.error")
    // Both cleared first: output directories are reused across runs, and a document that rendered
    // last time and fails now would otherwise leave last run's pixels to be diffed as this run's.
    png.delete()
    err.delete()

    runCatching { renderToBitmap(File(inputDir, "${entry.id}.rc").readBytes()) }
      .onSuccess { bitmap ->
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      }
      .onFailure { err.writeText("${it::class.java.simpleName}: ${it.message?.take(500)}") }
  }

  private fun renderToBitmap(bytes: ByteArray): Bitmap {
    val typefaces = typefaces()
    val document = RcDocumentCodec.decode(bytes)
    composeRule.setContent {
      val documentDensity = Density(entry.density, LocalDensity.current.fontScale)
      CompositionLocalProvider(LocalDensity provides documentDensity) {
        Box(
          // Sized in px routed through the document density, so the capture is exactly the baked
          // PNG's pixel size — the wall's diff needs both sides equal, and dp rounding drifts.
          Modifier.size(
            with(documentDensity) { entry.width.toDp() },
            with(documentDensity) { entry.height.toDp() },
          )
        ) {
          val decoded = remember { document }
          RcComposePlayer(decoded, modifier = Modifier.fillMaxSize(), typefaces = typefaces)
        }
      }
    }
    composeRule.waitForIdle()

    val root = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
    // Measure and lay out at the document's exact size before drawing, so the draw cannot land at
    // the window's bounds instead of the document's.
    root.measure(
      MeasureSpec.makeMeasureSpec(entry.width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(entry.height, MeasureSpec.EXACTLY),
    )
    root.layout(0, 0, entry.width, entry.height)

    val bitmap = Bitmap.createBitmap(entry.width, entry.height, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bitmap))
    // Compose reports some draw failures through the test dispatcher after `View.draw` returns.
    // Drain it while still inside `runCatching`, so a failed capture becomes a lane result rather
    // than a delayed JUnit failure paired with a blank PNG.
    composeRule.waitForIdle()
    return bitmap
  }

  private companion object {
    const val INPUT_PROPERTY = "rc.cmpandroid.input"
    const val OUTPUT_PROPERTY = "rc.cmpandroid.output"
    const val FONTS_PROPERTY = "rc.cmpandroid.fonts"

    /** Relative to this module's directory, which is a Gradle `Test` task's working directory. */
    const val DEFAULT_FONTS_DIR = "../wasm/dist-assets/fonts"

    fun inputDir(): File? =
      System.getProperty(INPUT_PROPERTY)?.let(::File)?.takeIf { it.isDirectory }

    /**
     * The vendored `fonts.json` the Wasm distribution serves, the same directory
     * [RcCmpRenderHarness] loads, so the two CMP lanes shape text identically and a difference
     * between them is the host.
     */
    fun typefaces(): RcTypefaceLoader {
      val dir = File(System.getProperty(FONTS_PROPERTY) ?: DEFAULT_FONTS_DIR)
      check(dir.isDirectory) { "no font manifest directory at ${dir.absolutePath}" }
      return runBlocking {
        RcManifestTypefaceLoader { url -> File(url).readBytes() }.load(dir.path)
      }
    }

    /**
     * One case per staged document. With nothing staged this yields a single placeholder so the
     * runner still has a case to skip — an empty parameter list is an error in `Parameterized`,
     * which would read as a harness failure rather than a no-op.
     */
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    fun documents(): List<Array<Any>> {
      val dir = inputDir() ?: return listOf(arrayOf(Entry("<none staged>", 1, 1)))
      val manifest = File(dir, "manifest.json")
      if (!manifest.isFile) return listOf(arrayOf(Entry("<no manifest.json>", 1, 1)))
      return entries(manifest).filter { File(dir, "${it.id}.rc").isFile }.map { arrayOf(it) }
    }

    /**
     * The staged manifest, read through the player's own JSON reader rather than by adding a
     * serialization runtime to this source set for one fixed-shape file — the same trade
     * [RcCmpRenderHarness] makes.
     */
    fun entries(manifest: File): List<Entry> {
      val root = rcParseJson(manifest.readText())
      val items = (root as? RcJsonValue.Arr)?.items ?: error("manifest.json is not a JSON array")
      return items.map { item ->
        val fields = (item as? RcJsonValue.Obj)?.entries ?: error("manifest entry is not an object")
        fun number(name: String): Double =
          (fields[name] as? RcJsonValue.Num)?.value ?: error("manifest entry has no $name")
        Entry(
          id = (fields["id"] as? RcJsonValue.Str)?.value ?: error("manifest entry has no id"),
          width = number("width").toInt(),
          height = number("height").toInt(),
          density = (fields["density"] as? RcJsonValue.Num)?.value?.toFloat() ?: 2f,
        )
      }
    }
  }
}
