package ee.schimke.composeai.rcplayer.compose

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import ee.schimke.composeai.rcplayer.runtime.RcRasterizedText
import ee.schimke.composeai.rcplayer.runtime.RcStyledText
import ee.schimke.composeai.rcplayer.runtime.RcTextRasterizer
import java.io.ByteArrayOutputStream

/**
 * An [RcTextRasterizer] drawing text as this player draws a `CoreText` on Android: the same
 * Compose text stack, style mapping and family rules over [typefaces] as the other platforms', so
 * a device can draw its own documents' bitmap fonts (`coreTextAsBitmapFonts`) rather than ask a
 * server to. [context] gives Compose its font resolver.
 *
 * Declines (returns null) a text naming a family [typefaces] cannot supply and the document does
 * not embed, rather than drawing it in a fallback face.
 */
public class RcComposeTextRasterizer(
  context: Context,
  typefaces: RcTypefaceLoader = RcTypefaceLoader.Default,
) : RcTextRasterizer {
  private val drawing =
    RcComposeTextDrawing(typefaces, createFontFamilyResolver(context)) { bitmap ->
      ByteArrayOutputStream()
        .also { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
    }

  override fun rasterize(text: RcStyledText): RcRasterizedText? = drawing.rasterize(text)
}
