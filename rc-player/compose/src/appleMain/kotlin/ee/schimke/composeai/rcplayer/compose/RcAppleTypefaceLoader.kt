package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

/**
 * Apple system-font names understood by the iOS and macOS hosts.
 *
 * These are platform requests, not font files. Nothing is downloaded or redistributed: Compose asks
 * CoreText for the system sans, serif, or monospaced design already present on the device. Keeping
 * this loader in `appleMain` is deliberate — another target must reject an `apple:` family unless
 * its host explicitly provides one, rather than silently substituting a non-Apple face. Installed
 * names are resolved locally; an absent Apple name uses the Apple system default.
 */
internal object RcAppleTypefaceLoader : RcTypefaceLoader {
  private val installedFamilies: Map<String, String> = buildMap {
    val fonts = FontMgr.default
    repeat(fonts.familiesCount) { index ->
      val name = fonts.getFamilyName(index)
      if (name.lowercase() !in this) put(name.lowercase(), name)
    }
  }

  private val installedTypefaces = mutableMapOf<String, FontFamily>()

  override val families: Set<String> =
    setOf(
      "apple:system",
      "apple:sf pro",
      "apple:sf pro text",
      "apple:sf pro display",
      "default",
      "sans-serif",
      "apple:serif",
      "apple:new york",
      "serif",
      "apple:monospaced",
      "apple:sf mono",
      "apple:*",
      "monospace",
    ) + installedFamilies.keys.mapTo(mutableSetOf()) { "apple:$it" }

  override fun typeface(family: String, variations: RcFontVariations?): FontFamily? =
    when (family) {
      "apple:system",
      "apple:sf pro",
      "apple:sf pro text",
      "apple:sf pro display",
      "default",
      "sans-serif" -> FontFamily.Default
      "apple:serif",
      "apple:new york",
      "serif" -> FontFamily.Serif
      "apple:monospaced",
      "apple:sf mono",
      "monospace" -> FontFamily.Monospace
      else -> installedFamily(family)
    }

  private fun installedFamily(family: String): FontFamily? {
    val requested =
      family.removePrefix("apple:").takeIf { family.startsWith("apple:") } ?: return null
    val installed = installedFamilies[requested] ?: return FontFamily.Default
    installedTypefaces[installed]?.let {
      return it
    }
    val typeface = FontMgr.default.matchFamilyStyle(installed, FontStyle.NORMAL) ?: return null
    return FontFamily(Typeface(typeface, alias = installed)).also {
      installedTypefaces[installed] = it
    }
  }
}

/**
 * Adds the Apple system families to an existing [additional] loader.
 *
 * Swift hosts use this when they also resolve `google:` families: the supplied loader retains
 * priority, so it can override the default/sans/serif/monospace roles, while Apple system families
 * remain available without copying any Apple font bytes into the application.
 */
public fun rcAppleTypefaceLoader(additional: RcTypefaceLoader): RcTypefaceLoader =
  object : RcTypefaceLoader {
    override val families: Set<String> = additional.families + RcAppleTypefaceLoader.families

    override fun typeface(family: String, variations: RcFontVariations?): FontFamily? =
      additional.typeface(family, variations) ?: RcAppleTypefaceLoader.typeface(family, variations)
  }
