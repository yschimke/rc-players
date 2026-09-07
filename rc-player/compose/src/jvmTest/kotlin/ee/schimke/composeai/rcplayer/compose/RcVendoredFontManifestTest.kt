package ee.schimke.composeai.rcplayer.compose

import java.io.File
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The vendored `fonts.json` must describe the files beside it, exactly.
 *
 * This lane is **manifest-only** — it never fetches — so a family the manifest does not declare is
 * a family the CMP and Wasm players cannot draw, and the document renders in a fallback face rather
 * than failing. That makes the gap invisible to a raster comparison, and it is how `google:Inter`
 * went missing while seven other branded families were present: four `remote-m3` typeface themes
 * name it, and only `composeSupportReport` said so — `CoreText[21]: custom font family google:Inter
 * (47) has no DataFont`. Adding it took those four documents from 3.01/3.00/2.64/2.02% against the
 * reference bake to 1.79/1.79/1.45/1.39%.
 *
 * Two invariants, and the second is the one with teeth.
 *
 * **Every declared file exists.** A manifest naming a file that was never committed fails the same
 * way as a missing family, one step later.
 *
 * **Every declared weight matches the file's own `OS/2.usWeightClass`.** The Google Fonts CSS2
 * endpoint returns its `@font-face` blocks in **descending** weight, so the first URL for
 * `wght@400;700` is the *700*. Naming the downloads in the order they appear silently swaps the
 * pair — which is what happened while adding Inter here, and a 400/700 swap is not something a
 * reviewer catches by eye in a render. Reading the weight out of the file is the check that does.
 */
class RcVendoredFontManifestTest {

  private val fontsDir = File(FONTS_DIR)

  private fun families(): List<Pair<String, List<Pair<String, Int>>>> {
    val manifest = File(fontsDir, "fonts.json")
    assertTrue(manifest.isFile, "no manifest at ${manifest.absolutePath}")
    // Deliberately parsed with the player's own reader rather than a test-only JSON dependency,
    // matching RcCmpRenderHarness.
    val root = rcParseJson(manifest.readText()) as RcJsonValue.Obj
    val list = (root.entries.getValue("families") as RcJsonValue.Arr).items
    return list.map { entry ->
      val obj = (entry as RcJsonValue.Obj).entries
      val name = (obj.getValue("name") as RcJsonValue.Str).value
      val fonts =
        (obj.getValue("fonts") as RcJsonValue.Arr).items.map { font ->
          val f = (font as RcJsonValue.Obj).entries
          (f.getValue("file") as RcJsonValue.Str).value to
            (f.getValue("weight") as RcJsonValue.Num).value.toInt()
        }
      name to fonts
    }
  }

  @Test
  fun `every declared face is committed beside the manifest`() {
    val missing =
      families().flatMap { (name, fonts) ->
        fonts.filterNot { (file, _) -> File(fontsDir, file).isFile }.map { "$name -> ${it.first}" }
      }
    assertTrue(
      missing.isEmpty(),
      "the manifest declares faces that are not committed: $missing — this lane never fetches, so " +
        "a missing file is a family that silently draws a fallback",
    )
  }

  @Test
  fun `every declared weight matches the face's own usWeightClass`() {
    for ((name, fonts) in families()) {
      for ((file, declared) in fonts) {
        val actual = usWeightClass(File(fontsDir, file)) ?: continue
        assertEquals(
          declared,
          actual,
          "$name: $file is declared weight $declared but its OS/2 table says $actual. The CSS2 " +
            "endpoint returns @font-face blocks in DESCENDING weight, so naming downloads in the " +
            "order they arrive swaps the pair",
        )
      }
    }
  }

  @Test
  fun `the branded families the remote-m3 typeface themes name are all present`() {
    val names = families().map { it.first }.toSet()
    // `google:Inter` is the one that was missing; the rest guard against a regeneration dropping a
    // family that is still referenced. See the class doc.
    val required =
      listOf("Inter", "Orbitron", "Space Grotesk", "JetBrains Mono", "Google Sans Flex")
    val absent = required.filterNot { it in names }
    assertTrue(absent.isEmpty(), "the vendored manifest no longer declares $absent — got $names")
  }

  /** `OS/2.usWeightClass`, or null when the file has no `OS/2` table. */
  private fun usWeightClass(file: File): Int? {
    RandomAccessFile(file, "r").use { raf ->
      raf.seek(4)
      val tables = raf.readUnsignedShort()
      for (i in 0 until tables) {
        raf.seek(12L + 16L * i)
        val tag = ByteArray(4).also { raf.readFully(it) }.decodeToString()
        raf.skipBytes(4)
        val offset = raf.readInt().toLong() and 0xffffffffL
        if (tag == "OS/2") {
          raf.seek(offset + 4)
          return raf.readUnsignedShort()
        }
      }
    }
    return null
  }

  private companion object {
    const val FONTS_DIR = "../../rc-player/wasm/dist-assets/fonts"
  }
}
