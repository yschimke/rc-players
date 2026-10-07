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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcembedded.player.ExperimentalRemoteDocumentPlayer
import ee.schimke.composeai.rcembedded.player.RemoteImageSupport
import kotlin.math.abs
import org.junit.Assert.assertEquals
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
/**
 * Two operations horologist's `remotecompose:fontvariation` writes, which animates variable-font
 * axes as path data, and which AndroidX's released embedded player drew nothing for up to
 * 1.0.0-alpha19 (alpha21 draws both, as this copy of `androidx-main` does):
 * - `BitmapTextMeasure`, with which its `RemoteString` text reads each character's index from a
 *   hidden bitmap font: with the measure unevaluated every index read 0 and no glyph drew;
 * - `PathTween` whose result is drawn by id, and tweened again: a tween of a tween.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RcPathAndBitmapMeasureRenderTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun textReadThroughBitmapTextMeasureDrawsAsTheStringDoes() {
        // "Hamburg" in Roboto Flex at 22 dp, weight 700, its characters read with
        // BitmapTextMeasure, against the same text written as a String: the same glyphs at the same
        // places. With the measure unevaluated the RemoteString text drew nothing.
        val (text, reference) =
            render(
                listOf(
                    "BitmapTextMeasureRemoteString-400x64.rc",
                    "BitmapTextMeasureReferenceString-400x64.rc",
                ),
                width = 400,
                height = 64,
            )
        // The String text is drawn as tweened outlines and the RemoteString text as point
        // expressions, which round differently: a few edge pixels may differ by a level or two.
        val worst =
            (0 until 400).maxOf { x ->
                (0 until 64).maxOf { y ->
                    abs((text.getPixel(x, y) ushr 24) - (reference.getPixel(x, y) ushr 24))
                }
            }
        assertTrue("the String text drew ${ink(reference)} ink pixels", ink(reference) > 2000)
        assertTrue("a pixel's alpha differs from the String text's by $worst", worst <= 16)
    }

    @Test
    fun aTweenOfATweenDrawsBetweenItsPaths() {
        // A 10-wide box tweened halfway to a 50-wide one (30 wide), then halfway towards a
        // 130-wide one: an 80 by 40 box.
        val ink = ink(render("PathTweenNested-160x64.rc", width = 160, height = 64))
        assertEquals(80 * 40.0, ink.toDouble(), 80.0 * 2 + 40 * 2)
    }

    private fun ink(bitmap: Bitmap): Int =
        (0 until bitmap.width).sumOf { x ->
            (0 until bitmap.height).count { y -> (bitmap.getPixel(x, y) ushr 24) > 8 }
        }

    private fun render(fixture: String, width: Int, height: Int): Bitmap =
        render(listOf(fixture), width, height).single()

    /** Each of [fixtures] at [width] by [height], side by side in one composition. */
    private fun render(fixtures: List<String>, width: Int, height: Int): List<Bitmap> {
        val documents = fixtures.map { fixture ->
            checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$fixture")) {
                    "missing fixture /rc-fixtures/$fixture"
                }
                .use { it.readBytes() }
        }

        composeRule.setContent {
            val documentDensity = Density(DENSITY, LocalDensity.current.fontScale)
            CompositionLocalProvider(LocalDensity provides documentDensity) {
                Row {
                    for (bytes in documents) {
                        Box(
                            Modifier.size(
                                with(documentDensity) { width.toDp() },
                                with(documentDensity) { height.toDp() },
                            )
                        ) {
                            RemoteImageSupport.enableEncodedImageReferences()
                            ExperimentalRemoteDocumentPlayer(
                                document = RemoteDocument(bytes),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val total = width * documents.size
        val root = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
        root.measure(
            MeasureSpec.makeMeasureSpec(total, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, total, height)
        val whole = Bitmap.createBitmap(total, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(whole))
        return documents.indices.map { Bitmap.createBitmap(whole, it * width, 0, width, height) }
    }

    private companion object {
        const val DENSITY = 2f
    }
}
