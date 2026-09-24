package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a document's `google:` families on Android by the same rule the vendored embedded
 * player uses, so the two Android players draw a `google:` family from the same place:
 * 1. **The shared font cache**, when `composeai.fonts.cacheDir` is set — the directory
 *    `data-fonts-google`'s `GoogleFontCache` fills and the embedded player's `GoogleFontFamilies`,
 *    its JVM cut, the Robolectric downloadable-font shadow and the figma-svg embed path all read.
 *    Same directory, same `<slug>-<weight>.ttf` / `<slug>-variable.ttf` names (see
 *    [RcSharedFontCache], which only reads it). That is a render-side property: previews,
 *    screenshot tests, CI.
 * 2. **Otherwise, GMS downloadable fonts** — Compose's `GoogleFont` against the
 *    `com.google.android.gms.fonts` provider with the embedded player's certificates. The face
 *    loads asynchronously and draws the default face until it arrives. This is the on-device path,
 *    and in the preview daemon's sandbox the shadowed provider serves and fills the same cache.
 *
 * The variable file is preferred from the cache: one file serves every weight, and it is the only
 * way a document's font-variation axes (`wght`, `wdth`, …) can be honoured. The GMS path serves
 * static instances, so there axes are dropped — as they are in the embedded player's provider
 * path. Anything this loader does not serve (`default`, generic families, a family the document
 * did not declare) is asked of [fallback].
 *
 * Build one per document with [rcGoogleFontsTypefaceLoader]; [families] should be the document's
 * [rcDownloadableFontRequests], so `composeSupportReport` sees them as resolvable.
 */
public class RcGoogleFontsTypefaceLoader
internal constructor(
  families: Collection<String>,
  private val fallback: RcTypefaceLoader,
  private val cache: RcSharedFontCache?,
) : RcTypefaceLoader {

  public constructor(
    families: Collection<String>,
    fallback: RcTypefaceLoader = RcTypefaceLoader.Default,
  ) : this(families, fallback, RcSharedFontCache.fromSystemProperty)

  /** Declared name by lowercased key — the player hands [typeface] lowercased names. */
  private val names: Map<String, String> =
    families.map(String::trim).filter(String::isNotEmpty).associateBy(String::lowercase)

  override val families: Set<String> = names.keys + fallback.families

  private val resolved = ConcurrentHashMap<Pair<String, List<Pair<String, Float>>>, FontFamily>()

  override fun typeface(family: String, variations: RcFontVariations?): FontFamily? {
    val name = names[family.lowercase()] ?: return fallback.typeface(family, variations)
    val axes = variations?.axes.orEmpty().map { it.tag to it.value }
    val key = name.lowercase() to axes
    resolved[key]?.let {
      return it
    }
    val built =
      runCatching { cacheFamily(name, axes) }.getOrNull()
        ?: runCatching { downloadableFamily(name) }.getOrNull()
        ?: return fallback.typeface(family, variations)
    resolved[key] = built
    return built
  }

  /** The family from the shared cache: its variable file at every weight, else static faces. */
  private fun cacheFamily(name: String, axes: List<Pair<String, Float>>): FontFamily? {
    val source = cache ?: return null
    val fonts =
      listOf(false, true).flatMap { italic ->
        val style = if (italic) FontStyle.Italic else FontStyle.Normal
        val variable = source.variable(name, italic)
        if (variable != null) {
          WEIGHTS.map { weight ->
            Font(variable, weight, style, variationSettings(weight, style, axes))
          }
        } else {
          WEIGHTS.mapNotNull { weight ->
            source.static(name, weight.weight, italic)?.let { Font(it, weight, style) }
          }
        }
      }
    return fonts.takeIf { it.isNotEmpty() }?.let(::FontFamily)
  }

  /** The family through the GMS provider — every weight and style, each fetched on first use. */
  private fun downloadableFamily(name: String): FontFamily {
    val font = GoogleFont(name, bestEffort = true)
    return FontFamily(
      listOf(FontStyle.Normal, FontStyle.Italic).flatMap { style ->
        WEIGHTS.map { weight ->
          androidx.compose.ui.text.googlefonts.Font(font, gmsFontProvider, weight, style)
        }
      }
    )
  }

  private companion object {
    val WEIGHTS: List<FontWeight> = (100..900 step 100).map(::FontWeight)

    val gmsFontProvider: GoogleFont.Provider by lazy {
      GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = GmsFontProviderCertificates,
      )
    }

    fun variationSettings(
      weight: FontWeight,
      style: FontStyle,
      axes: List<Pair<String, Float>>,
    ): FontVariation.Settings {
      // The face's own weight and slant first, then the document's axes — a document `wght`
      // replaces the face's, which is what an explicit axis means.
      val own = FontVariation.Settings(weight, style).settings.filter { setting ->
        axes.none { it.first == setting.axisName }
      }
      return FontVariation.Settings(
        *(own + axes.map { (tag, value) -> FontVariation.Setting(tag, value) }).toTypedArray()
      )
    }
  }
}

/**
 * An [RcGoogleFontsTypefaceLoader] for the `google:` families [document] declares, asking
 * [fallback] for everything else.
 */
public fun rcGoogleFontsTypefaceLoader(
  document: RcDocument,
  fallback: RcTypefaceLoader = RcTypefaceLoader.Default,
): RcTypefaceLoader =
  RcGoogleFontsTypefaceLoader(rcDownloadableFontRequests(document).map { it.family }, fallback)

/** As [rcGoogleFontsTypefaceLoader], from a document's encoded [bytes]. */
public fun rcGoogleFontsTypefaceLoader(
  bytes: ByteArray,
  fallback: RcTypefaceLoader = RcTypefaceLoader.Default,
): RcTypefaceLoader =
  RcGoogleFontsTypefaceLoader(rcDownloadableFontRequests(bytes).map { it.family }, fallback)
