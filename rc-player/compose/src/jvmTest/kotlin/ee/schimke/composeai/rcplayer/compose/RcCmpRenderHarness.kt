package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * Rasterizes captured Remote Compose documents through **this** player, so a CMP render can be
 * diffed against the AndroidX lanes on the same bytes.
 *
 * The counterpart of `third_party/rc-embedded-player`'s `RcEmbeddedRenderHarness` and
 * `RcViewPlayerRenderHarness`, and deliberately the same contract: an input directory of `<id>.rc`
 * plus a `manifest.json` of `{id, width, height, density}`, an output directory of `<id>.png`, both
 * named by system property. That is what makes a cross-player comparison possible at all — the same
 * staged documents reach the AOSP view player, the AndroidX embedded player and this one, so a
 * divergence is attributable to the interpreter rather than to the document, the density or the
 * capture path. The sweep it was written for, and what it found, is
 * `renders/rc-cmp-lane-ab/README.md`.
 *
 * A harness, not an assertion test. With nothing staged it renders nothing and passes, so `check`
 * stays green without a catalog; a document this player cannot render writes `<id>.error` instead
 * of failing the run, because "unrendered" is a *result* the comparison wants to report rather than
 * a reason to lose every other row in the sweep.
 *
 * Density comes from the manifest rather than the scene, for the same reason it does on the Android
 * lanes: the document's dp-denominated modifiers have to rasterize at the geometry the reference
 * PNG was baked at, and `ImageComposeScene` is sized in pixels.
 *
 * **The font manifest is not optional here.** A `CoreText` that names no family asks for the host's
 * `default` face, and with no manifest loaded it silently gets Compose's built-in one instead — the
 * failure [RcManifestTypefaceRenderTest] exists to catch. That draws perfectly good text at a
 * different width, which in a cross-player comparison reads as a layout divergence in every
 * document containing a glyph. So this lane loads the repo's vendored faces by default, and a run
 * that cannot find them fails rather than quietly producing a lane nobody can trust.
 */
class RcCmpRenderHarness {

  private data class Entry(val id: String, val width: Int, val height: Int, val density: Float)

  @Test
  fun render() {
    val inputDir = System.getProperty(INPUT_PROPERTY)?.let(::File)?.takeIf { it.isDirectory }
    val outputProperty = System.getProperty(OUTPUT_PROPERTY)
    if (inputDir == null || outputProperty == null) return
    val outputDir = File(outputProperty).apply { mkdirs() }
    val typefaces = typefaces()

    for (entry in entries(inputDir)) {
      val source = File(inputDir, "${entry.id}.rc")
      if (!source.isFile) continue
      val png = File(outputDir, "${entry.id}.png")
      val err = File(outputDir, "${entry.id}.error")
      val unsupported = File(outputDir, "${entry.id}.unsupported")
      // All three cleared before the render: an output directory is reused across runs, so a
      // document that rendered last time and fails now would otherwise leave last run's pixels in
      // place and be diffed as if they were this run's.
      png.delete()
      err.delete()
      unsupported.delete()
      runCatching { renderToPng(source.readBytes(), entry, typefaces, unsupported) }
        .onSuccess { png.writeBytes(it) }
        .onFailure { err.writeText("${it::class.simpleName}: ${it.message?.take(500)}") }
    }
  }

  /**
   * The vendored `fonts.json` the Wasm distribution serves, so this lane shapes text with the same
   * faces the browser lane does. Overridable with `rc.cmp.fonts` for a host that stages its own.
   */
  private fun typefaces(): RcTypefaceLoader {
    val dir = File(System.getProperty(FONTS_PROPERTY) ?: DEFAULT_FONTS_DIR)
    check(dir.isDirectory) { "no font manifest directory at ${dir.absolutePath}" }
    return runBlocking { RcManifestTypefaceLoader { url -> File(url).readBytes() }.load(dir.path) }
  }

  private fun renderToPng(
    bytes: ByteArray,
    entry: Entry,
    typefaces: RcTypefaceLoader,
    unsupported: File,
  ): ByteArray {
    val document = RcDocumentCodec.decode(bytes)
    // What the player says about the document before it draws it. A capability gap that the render
    // happens to survive — an operation skipped rather than refused — is invisible in the pixels
    // and is exactly the kind of divergence a raster comparison cannot report, so it is written out
    // alongside the PNG rather than inferred from it.
    //
    // `availableFontFamilies` is not optional: it defaults to the empty set, and a document naming
    // a family the *host* supplies then reports "has no DataFont" for a face that resolves
    // perfectly well at draw time. Passing what this lane actually loaded is the difference between
    // a report of real gaps and a report of 216 documents that render correctly.
    document
      .composeSupportReport(availableFontFamilies = typefaces.families)
      .issues
      .takeIf { it.isNotEmpty() }
      ?.let { issues ->
        unsupported.writeText(
          issues.joinToString("\n") { "${it.operation}[${it.operationIndex}]: ${it.detail}" }
        )
      }
    val scene =
      ImageComposeScene(
        width = entry.width,
        height = entry.height,
        density = Density(entry.density),
      ) {
        RcComposePlayer(document, typefaces = typefaces)
      }
    return try {
      checkNotNull(scene.render(0L).encodeToData()) { "Skia declined to encode the render" }.bytes
    } finally {
      scene.close()
    }
  }

  /**
   * The staged manifest, read through the player's own JSON reader rather than by adding a
   * serialization runtime to this module for one fixed-shape file — the same trade [rcParseJson]
   * was written for.
   */
  private fun entries(inputDir: File): List<Entry> {
    val manifest = File(inputDir, "manifest.json")
    if (!manifest.isFile) return emptyList()
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

  private companion object {
    const val INPUT_PROPERTY = "rc.cmp.input"
    const val OUTPUT_PROPERTY = "rc.cmp.output"
    const val FONTS_PROPERTY = "rc.cmp.fonts"

    /** Relative to this module's directory, which is a Gradle `Test` task's working directory. */
    const val DEFAULT_FONTS_DIR = "../../rc-player/wasm/dist-assets/fonts"
  }
}
