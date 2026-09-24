package ee.schimke.composeai.rcplayer.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcAndroidSystemColors
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorTheme
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On Android the player reads a themed colour's `android.R.color` from the platform without the
 * host passing anything, as the embedded player does — and a host lambda still replaces it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h1000dp")
class RcAndroidSystemColorsTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val resourceName = "system_accent1_100"
  private val index = RcAndroidSystemColors.NAMES.indexOf(resourceName)

  @Test
  fun aThemedColourDrawsThePlatformPaletteByDefault() {
    val platform = composeRule.activity.getColor(android.R.color.system_accent1_100)
    assertNotEquals("the fixture's fallback must differ from the platform value", FALLBACK, platform)
    assertEquals(platform, render { RcComposePlayer(document(), modifier = Modifier.fillMaxSize()) })
  }

  @Test
  fun anExplicitHostLookupThatResolvesNothingKeepsTheFallback() {
    // Passing a lambda replaces the platform default outright, even one that resolves nothing.
    assertEquals(
      FALLBACK,
      render {
        RcComposePlayer(document(), modifier = Modifier.fillMaxSize(), systemColors = { null })
      },
    )
  }

  @Test
  fun anExplicitHostColourWinsOverThePlatform() {
    val host = Color(0xff123456)
    assertEquals(
      host.toArgb(),
      render {
        RcComposePlayer(document(), modifier = Modifier.fillMaxSize(), systemColors = { host })
      },
    )
  }

  @Test
  fun anUndefinedNameKeepsTheFallback() {
    val lookup = rcAndroidSystemColors(composeRule.activity)
    assertEquals(null, lookup("system_not_a_colour"))
    assertEquals(
      Color(composeRule.activity.getColor(android.R.color.system_accent1_100)),
      lookup(resourceName),
    )
  }

  private var started = false

  private fun render(content: @Composable () -> Unit): Int {
    check(!started) { "one render per test" }
    started = true
    composeRule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
        Box(Modifier.size(with(Density(1f)) { 8.toDp() })) { content() }
      }
    }
    composeRule.waitForIdle()
    val root = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
    root.measure(
      MeasureSpec.makeMeasureSpec(8, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(8, MeasureSpec.EXACTLY),
    )
    root.layout(0, 0, 8, 8)
    return Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
      .also { root.draw(Canvas(it)) }
      .getPixel(4, 4)
  }

  private fun document(): RcDocument {
    val groupId = 43
    val colorId = 7
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 8, legacyHeight = 8, modern = false),
      listOf(
        RcRootLayout(1),
        RcTextData(groupId, RcAndroidSystemColors.GROUP),
        RcColorTheme(
          outId = colorId,
          colorGroupId = groupId,
          lightModeIndex = index,
          darkModeIndex = index,
          lightModeFallback = FALLBACK,
          darkModeFallback = FALLBACK,
        ),
        RcBoxLayout(3, 30, 1, 4),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(8f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(8f)),
        RcBackgroundModifier(
          flags = RcBackgroundModifier.COLOR_REFERENCE_FLAG,
          colorId = colorId,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(0f),
          green = RcFloatWord.literal(0f),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(0f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(4),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
      ),
    )
  }

  private companion object {
    const val FALLBACK = 0xff00ff00.toInt()
  }
}
