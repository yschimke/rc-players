package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import ee.schimke.composeai.rcplayer.runtime.RcRasterizedText
import ee.schimke.composeai.rcplayer.runtime.RcStyledText
import ee.schimke.composeai.rcplayer.runtime.RcTextRasterizer
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * An [RcTextRasterizer] drawing text as this player draws a `CoreText`: the same Compose text
 * stack, the same style mapping, and the same family rules over [typefaces] — so the image an
 * editor embeds for an older player looks like the text a newer one would have laid out.
 *
 * Declines (returns null) a text naming a family [typefaces] cannot supply and the document does
 * not embed, rather than drawing it in a fallback face. Android has its own, which takes a
 * `Context` for its font resolver.
 */
public class RcComposeTextRasterizer(typefaces: RcTypefaceLoader = RcTypefaceLoader.Default) :
  RcTextRasterizer {
  private val drawing =
    RcComposeTextDrawing(typefaces, createFontFamilyResolver()) { bitmap ->
      Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
    }

  override fun rasterize(text: RcStyledText): RcRasterizedText? = drawing.rasterize(text)
}
