package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.text.font.FontFamily

/**
 * Apple system-font names understood by the iOS and macOS hosts.
 *
 * These are platform requests, not font files. Nothing is downloaded or redistributed: Compose asks
 * CoreText for the system sans, serif, or monospaced design already present on the device. Keeping
 * this loader in `appleMain` is deliberate — another target must reject an `apple:` family unless
 * its host explicitly provides one, rather than silently substituting a non-Apple face.
 */
internal object RcAppleTypefaceLoader : RcTypefaceLoader {
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
      "monospace",
    )

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
      else -> null
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
