package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import ee.schimke.composeai.rcplayer.runtime.RcRasterizedText
import ee.schimke.composeai.rcplayer.runtime.RcStyledText
import ee.schimke.composeai.rcplayer.runtime.RcTextRasterizer
import kotlin.math.ceil
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * An [RcTextRasterizer] drawing text as this player draws a `CoreText`: the same Compose text
 * stack, the same style mapping, and the same family rules over [typefaces] — so the image an
 * editor embeds for an older player looks like the text a newer one would have laid out.
 *
 * Declines (returns null) a text naming a family [typefaces] cannot supply and the document does
 * not embed, rather than drawing it in a fallback face.
 */
public class RcComposeTextRasterizer(
  private val typefaces: RcTypefaceLoader = RcTypefaceLoader.Default
) : RcTextRasterizer {
  private val fontFamilyResolver = createFontFamilyResolver()

  override fun rasterize(text: RcStyledText): RcRasterizedText? {
    val settings =
      fontVariationSettings(text.fontAxes.map { it.first }, text.fontAxes.map { it.second })
    val variations =
      withWeightAxis(withItalicAxis(settings.withoutFeatures(), text.italic), text.fontWeight)
    val family = family(text, variations) ?: return null
    // Pixels in, so a font scale of 1: the size is already what the document asked for.
    val density = Density(text.density)
    val lineHeight =
      if (text.lineHeightAdd == 0f && text.lineHeightMultiplier == 1f) TextUnit.Unspecified
      else with(density) { (text.fontSize * text.lineHeightMultiplier + text.lineHeightAdd).toSp() }
    val style =
      TextStyle(
        color = Color(text.color),
        fontSize = with(density) { text.fontSize.toSp() },
        letterSpacing = text.letterSpacing.em,
        lineHeight = lineHeight,
        lineHeightStyle =
          LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        fontWeight = FontWeight(text.fontWeight),
        fontStyle = if (text.italic) FontStyle.Italic else FontStyle.Normal,
        fontFeatureSettings = fontFeatureSettings(settings),
        fontFamily = family,
        textAlign =
          if (text.justification == 1) TextAlign.Justify
          else runCatching { androidXTextAlign(text.textAlign) }.getOrNull() ?: return null,
        lineBreak =
          when (text.lineBreak) {
            1 -> LineBreak.Paragraph
            2 -> LineBreak.Heading
            else -> LineBreak.Simple
          },
        hyphens = if (text.hyphenation > 0) Hyphens.Auto else Hyphens.None,
        textDecoration =
          TextDecoration.combine(
            listOfNotNull(
              TextDecoration.Underline.takeIf { text.underline },
              TextDecoration.LineThrough.takeIf { text.strikethrough },
            )
          ),
      )
    val overflow = runCatching { androidXTextOverflow(text.overflow) }.getOrNull() ?: return null
    val layout =
      TextMeasurer(fontFamilyResolver, density, LayoutDirection.Ltr, cacheSize = 0)
        .measure(
          text.text,
          style,
          overflow = overflow,
          maxLines = androidXMaxLines(text.overflow, text.maxLines),
          constraints =
            text.maxWidth?.let { Constraints(maxWidth = ceil(it).toInt().coerceAtLeast(1)) }
              ?: Constraints(),
        )
    val width = layout.size.width
    val height = layout.size.height
    if (width <= 0 || height <= 0) return null
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(
      density,
      LayoutDirection.Ltr,
      Canvas(bitmap),
      Size(width.toFloat(), height.toFloat()),
    ) {
      drawText(layout)
    }
    val png =
      Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        ?: return null
    return RcRasterizedText(png, width, height)
  }

  /** The family the player would draw [text] in, or null where it would fall back. */
  private fun family(text: RcStyledText, variations: RcFontVariations?): FontFamily? {
    // A fresh set per text: the identity names the payload's bytes, so two images embedding
    // different fonts can never share one cached typeface.
    val embedded = text.fontData?.let { RcDocumentFonts(mapOf(EMBEDDED to it)) }
    val name = text.fontFamily?.lowercase()
    val generic = name == null || name in RC_GENERIC_FAMILIES
    if (
      !generic &&
        embedded == null &&
        typefaces.typeface(name!!.removePrefix("google:"), variations) == null
    ) {
      return null
    }
    return rcResolveTypeface(
      text.fontFamily,
      EMBEDDED,
      embedded ?: RcDocumentFonts.Empty,
      typefaces,
      variations,
      text.fontWeight,
      text.italic,
    )
  }

  private companion object {
    const val EMBEDDED = 0
  }
}
