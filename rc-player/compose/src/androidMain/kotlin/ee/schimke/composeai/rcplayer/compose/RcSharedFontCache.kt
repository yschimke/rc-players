package ee.schimke.composeai.rcplayer.compose

import java.io.File

/**
 * A read-only view of the shared Google Fonts cache directory — the one
 * `ee.schimke.composeai:data-fonts-google`'s `GoogleFontCache` fills and the vendored embedded
 * player, its JVM cut, the Robolectric downloadable-font shadow and the figma-svg embed path read.
 *
 * Only the reading half, and on purpose: that module fetches with okhttp 5, whose Android variant
 * requires every consumer to compile against API 37, and this player is a library its hosts should
 * not have to raise their compileSdk for. Nothing is lost by not fetching here — a miss falls to the
 * GMS downloadable-font path, which in the preview daemon's Robolectric sandbox is shadowed by the
 * same cache and fills it.
 *
 * The file names must match `GoogleFontKey.fileName()` and `variableFileName()` exactly, or the two
 * players would read different files for one family: see [slug].
 */
internal class RcSharedFontCache(private val directory: File) {

  /** The static face for one `(family, weight, italic)`, or null when the cache has none. */
  fun static(family: String, weight: Int, italic: Boolean): File? =
    existing("${slug(family)}-$weight${italicSuffix(italic)}.ttf")

  /** The family's variable file, or null when the cache has none. */
  fun variable(family: String, italic: Boolean): File? =
    existing("${slug(family)}-variable${italicSuffix(italic)}.ttf")

  private fun existing(name: String): File? =
    File(directory, name).takeIf { it.isFile && it.length() > 0L }

  internal companion object {
    /**
     * `GoogleFontKey.slugify`: lowercase, keep `[a-z0-9]`, collapse every other run to one `-`,
     * trim dashes, `"font"` when nothing is left.
     */
    fun slug(name: String): String {
      val out = StringBuilder()
      var dash = true
      for (char in name) {
        val lower = char.lowercaseChar()
        if (lower in 'a'..'z' || lower in '0'..'9') {
          out.append(lower)
          dash = false
        } else if (!dash) {
          out.append('-')
          dash = true
        }
      }
      return out.toString().trim('-').ifEmpty { "font" }
    }

    private fun italicSuffix(italic: Boolean): String = if (italic) "-italic" else ""

    /**
     * The cache the embedded player reads, from the same system property, or null when unset.
     * `composeai.fonts.offline` has nothing to switch here: this view never fetches.
     */
    val fromSystemProperty: RcSharedFontCache? by lazy {
      System.getProperty("composeai.fonts.cacheDir")
        ?.takeIf(String::isNotBlank)
        ?.let { RcSharedFontCache(File(it)) }
    }
  }
}
