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

package ee.schimke.composeai.rcembedded.player.modifier

import androidx.compose.remote.core.CoreDocument
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipModifierTest {
    @Test
    fun dpCornerScalesWithPlaybackDensity() {
        assertEquals(
            52f,
            mutableStateOf(26f)
                .resolveRadius(
                    fallback = 42f,
                    density = 2f,
                    densityBehavior = CoreDocument.DENSITY_BEHAVIOR_DP,
                ),
        )
    }

    @Test
    fun nonFiniteCornerFallsBack() {
        assertEquals(
            42f,
            mutableStateOf(Float.NaN)
                .resolveRadius(
                    fallback = 42f,
                    density = 1f,
                    densityBehavior = CoreDocument.DENSITY_BEHAVIOR_LEGACY,
                ),
        )
    }

    /**
     * The regression that motivated the fix, in the shape that actually renders.
     *
     * A 26dp corner on a 195 × 121dp card is `52` px at density 2. It is genuinely smaller than
     * half the box, so [roundedRectRadiusScale] does not clamp it — which is exactly why the old
     * double scaling survived here and was invisible on every stadium-shaped button beside it.
     * Clipping a card to a 104px radius cut the corners off the border its content drew
     * (wear-m3-catalog#89).
     */
    @Test
    fun cardSizedDpCornerScalesOnceAtDensityTwo() {
        val corner = mutableStateOf(26f)
        val shape =
            RemoteRoundedClipShape(
                topStart = corner,
                topEnd = corner,
                bottomEnd = corner,
                bottomStart = corner,
                densityBehavior = CoreDocument.DENSITY_BEHAVIOR_DP,
            )

        val outline =
            shape.createOutline(
                size = Size(390f, 242f),
                layoutDirection = LayoutDirection.Ltr,
                density = Density(2f),
            )

        assertTrue(outline is Outline.Rounded)
        assertEquals(52f, (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x)
    }

    /**
     * The clamp that hid the bug, kept explicit. An oversized corner still normalizes to the box,
     * so stadium and circle shapes are unaffected by the fix above.
     */
    @Test
    fun oversizedCornerStillNormalizesToTheBox() {
        val corner = mutableStateOf(64f)
        val shape =
            RemoteRoundedClipShape(
                topStart = corner,
                topEnd = corner,
                bottomEnd = corner,
                bottomStart = corner,
                densityBehavior = CoreDocument.DENSITY_BEHAVIOR_PIXELS,
            )

        val outline =
            shape.createOutline(
                size = Size(268f, 84f),
                layoutDirection = LayoutDirection.Ltr,
                density = Density(2f),
            )

        assertTrue(outline is Outline.Rounded)
        assertEquals(42f, (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x)
    }
}
