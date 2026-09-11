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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression coverage for expression-backed `widthIn` constraints used by RemoteEdgeButton. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class DynamicDimensionConstraintRenderTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun edgeButtonKeepsContentConstrainedByComponentWidth() {
    val bitmap = render("EdgeButtonDynamicWidth-384x112.rc")

    val darkContentPixels =
      (0 until bitmap.height).sumOf { y ->
        (0 until bitmap.width).count { x ->
          val pixel = bitmap.getPixel(x, y)
          val alpha = pixel ushr 24
          val red = (pixel ushr 16) and 0xff
          val green = (pixel ushr 8) and 0xff
          val blue = pixel and 0xff
          alpha > 128 && maxOf(red, green, blue) < 100
        }
      }

    assertTrue(
      "the EdgeButton label disappeared because its expression-backed max width resolved to zero",
      darkContentPixels > 100,
    )
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

  private companion object {
    const val WIDTH = 384
    const val HEIGHT = 112
    const val DENSITY = 2f
  }
}
