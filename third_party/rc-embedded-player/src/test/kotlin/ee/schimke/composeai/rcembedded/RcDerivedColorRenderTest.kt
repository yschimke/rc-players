/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.schimke.composeai.rcembedded

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcembedded.player.ExperimentalRemoteDocumentPlayer
import ee.schimke.composeai.rcembedded.player.enableEncodedImageReferences
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A colour the document **derives** must reach the pixels, not the value it derived to before its
 * inputs existed.
 *
 * Two defects, one shape, both reported against `remote-material3` and neither reproducible on the
 * View player, the JS player or the CMP player
 * ([yschimke/rc-players#46](https://github.com/yschimke/rc-players/issues/46)):
 *
 * * a `ColorExpression` over three `ColorAttribute` channels drew `(15, 12, 23)` — 2.07:1 against
 *   its own container, where every other player draws `(212, 202, 227)` at 5.95:1
 *   ([wear-m3-catalog#326](https://github.com/yschimke/wear-m3-catalog/issues/326)); and
 * * a disabled `RemoteTextButton` drew a **fully transparent** capture — no container and no label
 *   ([wear-m3-catalog#130](https://github.com/yschimke/wear-m3-catalog/issues/130)).
 *
 * Asserted on pixels rather than on a resolved id, because both failure modes were invisible at
 * every level above the raster: the expression evaluated correctly every time and the write was
 * dropped, and the text op held a value it had resolved once and never revisited.
 *
 * The two fixtures are the published `remote-m3` documents, captured verbatim, so the test fails on
 * the same bytes the catalog ships rather than on a reconstruction of them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RcDerivedColorRenderTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun aSecondaryLabelBuiltFromColorAttributesKeepsItsContrast() {
    val bitmap = render("FilledVariantRemoteButton-454x200.rc")

    // The band the secondary label occupies, inside the container and clear of the icon.
    val samples =
      (104 until 124).flatMap { y -> (120 until 300).map { x -> bitmap.getPixel(it@ x, y) } }
    val container = samples.groupingBy { it }.eachCount().maxBy { it.value }.key
    val label =
      samples
        .filter { it != container && it ushr 24 != 0 }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    assertTrue("the secondary label drew nothing over its container", label != null)

    val ratio = contrast(container, label!!)
    // 2.07:1 was the defect; the View, JS and CMP players all resolve 5.95:1. Asserting the WCAG AA
    // floor rather than the exact number keeps this about the defect and not about the theme.
    assertTrue(
      "secondary label resolved to ${hex(label)} on ${hex(container)} — ${"%.2f".format(ratio)}:1, " +
        "which is the derived colour collapsing to black rather than the colour the document names",
      ratio >= 4.5,
    )
  }

  @Test
  fun aDisabledTextButtonDrawsItsLabel() {
    val bitmap = render("DisabledRemoteTextButton-454x200.rc")
    val ink =
      (0 until bitmap.width).sumOf { x ->
        (0 until bitmap.height).count { y -> bitmap.getPixel(x, y) ushr 24 != 0 }
      }
    // The defect was a byte-for-byte transparent capture, so any ink at all is the regression line;
    // the View player draws ~915 pixels of it.
    assertTrue("the disabled text button drew a fully transparent capture", ink > 100)
  }

  private fun render(fixture: String): Bitmap {
    val bytes =
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$fixture")) {
          "missing fixture /rc-fixtures/$fixture"
        }
        .use { it.readBytes() }

    composeRule.setContent {
      val documentDensity = Density(DENSITY, LocalDensity.current.fontScale)
      CompositionLocalProvider(LocalDensity provides documentDensity) {
        Box(
          Modifier.size(
            with(documentDensity) { WIDTH.toDp() },
            with(documentDensity) { HEIGHT.toDp() },
          )
        ) {
          enableEncodedImageReferences()
          ExperimentalRemoteDocumentPlayer(
            document = RemoteDocument(bytes),
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
    composeRule.waitForIdle()

    val root = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
    root.measure(
      MeasureSpec.makeMeasureSpec(WIDTH, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(HEIGHT, MeasureSpec.EXACTLY),
    )
    root.layout(0, 0, WIDTH, HEIGHT)
    return Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also {
      root.draw(Canvas(it))
    }
  }

  private fun contrast(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
  }

  private fun relativeLuminance(argb: Int): Double {
    fun channel(shift: Int): Double {
      val v = ((argb shr shift) and 0xff) / 255.0
      return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
  }

  private fun hex(argb: Int): String = "#%08x".format(argb)

  private companion object {
    const val WIDTH = 454
    const val HEIGHT = 200
    const val DENSITY = 2f
  }
}
