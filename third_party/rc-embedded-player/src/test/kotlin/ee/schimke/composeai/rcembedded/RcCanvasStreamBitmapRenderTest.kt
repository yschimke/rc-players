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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A `BitmapData` declared in a component's **canvas stream** must be registered like any other.
 *
 * Setup walks the document to register each bitmap's metadata without decoding it, and that walk
 * followed `Container.getList()` only. A component's draw-content operations hang off it as a
 * *field* rather than as a child, so a bitmap declared there was never registered — the same
 * structural gap that left a disabled label's colour resolving against nothing
 * ([#50](https://github.com/yschimke/rc-players/issues/50)), one function above
 * `findComponentValues`, which has always had the branch.
 *
 * `remote-m3` declares an image-background button's bitmap exactly there, and the miss cost **two**
 * things, because both read the same registered object:
 * * the texture — `resolveBitmap` gives up when `getObject(imageId)` is not a `BitmapData`; and
 * * the image's width and height — `ImageAttribute.paint` reads that object to publish them.
 *
 * The button's container is a texture with a scrim gradient over it whose geometry is derived from
 * those dimensions. With them unresolved the gradient degenerated to its first stop and painted the
 * container flat in the scrim's own colour, `(51, 46, 60)`, instead of fading across the image's
 * `(236, 236, 236)` ([#54](https://github.com/yschimke/rc-players/issues/54)).
 *
 * `ImageAttribute` needs an explicit case in the draw stream on top of the registration fix, and
 * cannot be reached any other way: `ColorAttribute` implements `VariableSupport` and
 * `VariableProvider`, so `buildComputedOpIndex` picks it up, while `ImageAttribute` implements
 * **neither** and can never be indexed however hard that walk looks.
 *
 * Asserted as an exact colour rather than as ink, because the defect drew a full container of the
 * wrong colour — every ink-based measure passes on it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RcCanvasStreamBitmapRenderTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun anImageBackgroundButtonDrawsItsTextureRatherThanTheScrimAlone() {
    val bitmap = render("ImageBackgroundRemoteButton-454x200.rc")

    // The container's centre row, clear of its rounded ends. The document's bitmap is a uniform
    // 8x8 of (236, 236, 236), and the scrim over it runs from (51, 46, 60) opaque to the same
    // colour at alpha 0 — so the centre of the container is the image, undimmed.
    val row = bitmap.height / 2
    val counts = mutableMapOf<Int, Int>()
    for (x in 0 until bitmap.width) {
      val pixel = bitmap.getPixel(x, row)
      if (pixel ushr 24 > 8) counts[pixel] = (counts[pixel] ?: 0) + 1
    }
    val dominant = counts.maxBy { it.value }.key
    val rgb = Triple((dominant shr 16) and 0xff, (dominant shr 8) and 0xff, dominant and 0xff)

    assertEquals(
      "the container drew the scrim's own colour flat instead of the image it should fade across " +
        "— the canvas-stream BitmapData was never registered, so both the texture and the " +
        "ImageAttribute dimensions the gradient derives from resolved to nothing",
      Triple(236, 236, 236),
      rgb,
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

  private companion object {
    const val WIDTH = 454
    const val HEIGHT = 200
    const val DENSITY = 2f
  }
}
