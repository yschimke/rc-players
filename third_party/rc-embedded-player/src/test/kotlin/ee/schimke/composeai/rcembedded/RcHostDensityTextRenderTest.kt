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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcembedded.player.ExperimentalRemoteDocumentPlayer
import ee.schimke.composeai.rcembedded.player.RemoteImageSupport
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A document captured against `RemoteDensity.Host` draws its text.
 *
 * A `host` capture writes no pixel sizes: a `RemoteText`'s font size is an expression over the
 * player's `DENSITY` and `FONT_SIZE` variables, so `TextLayout.mFontSize` is a NaN-boxed id. The
 * core resolves it into `mFontSizeValue` in `updateVariables`, but its constructor seeds
 * `mFontSizeValue` with the same NaN-boxed word, and `RcPlayerText` read that field once, at
 * composition. The text was laid out at `NaN.sp` and drew nothing: every string in the
 * wear-m3-catalog `remote-m3` sheet vanished under `composePreview.rcDensity=host`.
 *
 * The two fixtures are the same two bare `RemoteText`s from that catalog
 * (`TitleCardHostProbeBareText`, 227x200dp at dpi 320), captured once with density folded in
 * (`fixed`) and once deferred to the player (`host`). Both must draw the same text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RcHostDensityTextRenderTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun textWhoseFontSizeIsDeferredToTheHostDraws() {
        val fixed = ink(render("BareTextFixedDensity-454x400.rc"))
        val host = ink(render("BareTextHostDensity-454x400.rc"))
        assertTrue("the fixed capture drew no text, so the comparison means nothing", fixed > 500)
        // The same glyphs at the same size: equal to within antialiasing, nowhere near zero.
        assertTrue(
            "the host capture drew $host ink pixels against $fixed for the same text folded in; " +
                "zero is a NaN-boxed font size read as a number",
            abs(host - fixed) <= fixed / 10,
        )
    }

    private fun ink(bitmap: Bitmap): Int =
        (0 until bitmap.width).sumOf { x ->
            (0 until bitmap.height).count { y -> (bitmap.getPixel(x, y) ushr 24) > 8 }
        }

    /** The document on screen; the rule allows one `setContent`, so both captures share it. */
    private val document = mutableStateOf<ByteArray?>(null)

    private fun render(fixture: String): Bitmap {
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$fixture")) {
                    "missing fixture /rc-fixtures/$fixture"
                }
                .use { it.readBytes() }

        if (document.value == null) {
            composeRule.setContent {
                val documentDensity = Density(DENSITY, LocalDensity.current.fontScale)
                CompositionLocalProvider(LocalDensity provides documentDensity) {
                    Box(
                        Modifier.size(
                            with(documentDensity) { WIDTH.toDp() },
                            with(documentDensity) { HEIGHT.toDp() },
                        )
                    ) {
                        RemoteImageSupport.enableEncodedImageReferences()
                        document.value?.let { current ->
                            // Keyed so nothing laid out for one document carries into the next.
                            key(current) {
                                ExperimentalRemoteDocumentPlayer(
                                    document = RemoteDocument(current),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { document.value = bytes }
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
        const val DENSITY = 2f
        /** Both fixtures are 227x200dp at density 2. */
        const val WIDTH = 454
        const val HEIGHT = 400
    }
}
