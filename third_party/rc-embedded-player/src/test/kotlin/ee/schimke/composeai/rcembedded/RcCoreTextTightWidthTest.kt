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

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcembedded.player.ExperimentalRemoteDocumentPlayer
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A `CoreText` is as wide as its lines, as the core measures it, not as wide as Compose's intrinsic
 * width (#572).
 *
 * `CoreText.computeWrapSize` takes a letter-spaced text's width from
 * `AndroidPaintContext.getTightBoundingBox`: `ceil(max lineRight) - floor(min lineLeft)`.
 * `BasicText` reports `ceil(maxIntrinsicWidth)`, which with letter spacing runs past the line's
 * right edge. The fixture is wear-m3-catalog's `TonalRemoteButton` `icon` variant (227x100dp at dpi
 * 320): a wrap-width button of icon + 6dp spacer + a column of two labels, centred in its frame.
 * Its widest label, "Secondary label", ends at 198.43 px, so the core sizes it 199 and the button
 * 319, centred at 67.5. The unpatched player measured it 200, the button 320, at 67: the whole
 * content one pixel left of the CMP player, which follows the core.
 *
 * The plain button (one label in a fixed-width button) must not move: its label centres to the same
 * pixel either way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "xhdpi")
class RcCoreTextTightWidthTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun letterSpacedLabelIsSizedToItsLines() {
        play("TonalRemoteButtonIcon-454x200.rc")

        val label = textNode("Secondary label")
        val tight = tightWidth(label.second)
        assertEquals("the label's line ends at 198.43 px, so the core measures 199", 199, tight)

        // `BasicText`'s own semantics bounds stay the paragraph's; the node the row measures is
        // the narrowed one, so read it through the button that wraps it.
        val button = buttonBounds()
        assertEquals(
            "the button should wrap 28 + 52 + 12 + the label as CoreText measures it + 28",
            (28 + 52 + 12 + tight + 28).toFloat(),
            button.width,
        )
        // Centred at 67.5, which Compose rounds up: where the CMP player puts it.
        assertEquals(68f, button.left)
        assertEquals("the label starts after the padding, icon and spacer", 160f, label.first.left)
    }

    @Test
    fun plainButtonLabelDoesNotMove() {
        play("TonalRemoteButton-454x200.rc")

        val label = textNode("Primary label")
        assertEquals(188, tightWidth(label.second))
        // Centred in the fixed-width button: (288 - 188) / 2 = 50 now, round(49.5) = 50 before
        // for the 189 px the unpatched player measured. The same pixel.
        assertEquals(133f, label.first.left)
        assertEquals(Rect(55f, 48f, 399f, 152f), buttonBounds())
    }

    private fun tightWidth(result: TextLayoutResult): Int {
        var left = Float.MAX_VALUE
        var right = 0f
        for (line in 0 until result.lineCount) {
            left = minOf(left, result.getLineLeft(line))
            right = maxOf(right, result.getLineRight(line))
        }
        return ceil(right).toInt() - floor(left).toInt()
    }

    private fun textNode(text: String): Pair<Rect, TextLayoutResult> {
        val node = composeRule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)) {
                "\"$text\" exposes no text layout"
            }
            .action
            ?.invoke(results)
        return node.boundsInRoot to results.single()
    }

    /** The button's click target, which carries its background and padding. */
    private fun buttonBounds(): Rect =
        composeRule
            .onNode(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

    private val document = mutableStateOf<ByteArray?>(null)

    private fun play(fixture: String) {
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
                    document.value?.let { current ->
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
        composeRule.runOnIdle { document.value = bytes }
        composeRule.waitForIdle()
    }

    private companion object {
        const val DENSITY = 2f
        /** Both fixtures are 227x100dp at density 2. */
        const val WIDTH = 454
        const val HEIGHT = 200
    }
}
