package ee.schimke.composeai.rcplayer.compose

import android.graphics.BitmapFactory
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import ee.schimke.composeai.rcplayer.runtime.RcStyledText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Android rasterizer draws a text as the other platforms' does, so a device can draw its own
 * documents' bitmap fonts: a PNG of the laid-out box, taller where a glyph's ink stands above the
 * line, and nothing for a family it hasn't got.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RcAndroidTextRasterizerTest {
  private val robotoFlex =
    FontFamily(Font(File("../wasm/dist-assets/fonts/RobotoFlex.ttf")))

  private val typefaces =
    object : RcTypefaceLoader {
      override val families: Set<String>
        get() = setOf("roboto flex")

      override fun typeface(family: String, variations: RcFontVariations?): FontFamily? =
        robotoFlex.takeIf { family == "roboto flex" }
    }

  private val rasterizer = RcComposeTextRasterizer(RuntimeEnvironment.getApplication(), typefaces)

  private fun text(text: String, family: String = "Roboto Flex") =
    RcStyledText(text, 2f, 56f, 0xff000000.toInt(), 400, false, family, null)

  @Test
  fun aTextIsDrawnAsAPngOfItsBox() {
    val drawn = assertNotNull(rasterizer.rasterize(text("A")))
    val bitmap = assertNotNull(BitmapFactory.decodeByteArray(drawn.png, 0, drawn.png.size))
    assertEquals(drawn.width, bitmap.width)
    assertEquals(drawn.height, bitmap.height)
    assertTrue("a baseline inside the box: ${drawn.baseline}", drawn.baseline in 1f..drawn.height.toFloat())
  }

  @Test
  fun aGlyphAboveItsLineIsDrawnWholeOnALowerBaseline() {
    val plain = assertNotNull(rasterizer.rasterize(text("A")))
    val tall = assertNotNull(rasterizer.rasterize(text("Ǻ")))
    val above = tall.baseline - plain.baseline
    assertTrue("the acute and ring above the line: $above", above > 4f)
    assertEquals(plain.height + above, tall.height.toFloat(), 1f)
  }

  @Test
  fun aFamilyItHasntGotIsDeclined() {
    assertNull(rasterizer.rasterize(text("A", family = "Orbitron")))
  }

}
