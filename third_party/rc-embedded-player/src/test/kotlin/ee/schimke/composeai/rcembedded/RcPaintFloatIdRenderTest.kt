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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A paint-bundle float that is a **variable reference** must be resolved, not read as a number.
 *
 * Four paint commands encode their float as a literal *or* a NaN-boxed id into the float store, and
 * the reference player names the four together in both `PaintBundle.registerListening` and
 * `PaintBundle.resolveIds` — `TEXT_SIZE`, `STROKE_WIDTH`, `ALPHA` and `STROKE_MITER`. Three of them
 * are applied here and all three read the word with `Float.fromBits` alone, which does not yield a
 * wrong number: it yields **NaN**, and NaN then loses every comparison downstream in silence.
 *
 * `RemoteCurvedProgressIndicator` is where that surfaced. It encodes `strokeWidth` as a computed
 * expression rather than a constant, so `Stroke(width = NaN)` reached the canvas and the platform
 * drew its minimum — a hairline whatever width the document asked for
 * ([wear-m3-catalog#289](https://github.com/yschimke/wear-m3-catalog/issues/289), the last of the
 * three in [rc-players#46](https://github.com/yschimke/rc-players/issues/46)). The View player, the
 * JS player and the CMP player all draw it correctly from the same bytes.
 *
 * Two fixtures rather than one, because the failure and the fix are on **different branches of the
 * same word**. `ArcProgressRemote` boxes an id and is the regression; `CircularProgressRemote`
 * writes a literal and is the guard — a "fix" that resolved unconditionally would break it, and
 * nothing else in this suite draws a stroked arc from a constant.
 *
 * Asserted on pixels, like [RcDerivedColorRenderTest], because NaN is invisible at every level
 * above the raster: the bundle decodes, the op applies, the draw runs, and only the ink is wrong.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RcPaintFloatIdRenderTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun anArcWhoseStrokeWidthIsAVariableDrawsAtThatWidth() {
    val ink = ink(render("ArcProgressRemote-454x400.rc", width = 454, height = 400))
    // 239 was the defect — a one-pixel arc, which is what a NaN width degenerates to. The View, JS
    // and CMP players all draw ~2734 from these bytes. The floor is set an order of magnitude above
    // the hairline and well below the reference so it fails on the defect and not on antialiasing.
    assertTrue(
      "the arc drew $ink ink pixels — a hairline, which is what Stroke(width = NaN) rasterises " +
        "to when the strokeWidth word is a NaN-boxed id read as a literal float",
      ink > 1000,
    )
  }

  @Test
  fun anArcWhoseStrokeWidthIsALiteralStillDrawsAtThatWidth() {
    val ink = ink(render("CircularProgressRemote-384x384.rc", width = 384, height = 384))
    // The other branch of the same word: this document writes its stroke width as a constant, and
    // it drew correctly throughout the defect. Resolving unconditionally — treating every word as
    // an id — would take this to zero, so it is the half of the pair that can only fail from
    // overreach.
    assertTrue("the constant-width arc drew $ink ink pixels", ink > 1000)
  }

  private fun ink(bitmap: Bitmap): Int =
    (0 until bitmap.width).sumOf { x ->
      (0 until bitmap.height).count { y -> (bitmap.getPixel(x, y) ushr 24) > 8 }
    }

  private fun render(fixture: String, width: Int, height: Int): Bitmap {
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
            with(documentDensity) { width.toDp() },
            with(documentDensity) { height.toDp() },
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
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
    )
    root.layout(0, 0, width, height)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
      root.draw(Canvas(it))
    }
  }

  private companion object {
    const val DENSITY = 2f
  }
}
