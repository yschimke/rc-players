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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies that the resolved Compose/Robolectric stack captures real pixels without a manual draw.
 *
 * Older Compose versions timed out in `WindowCapture.forceRedraw` even for a bare `Box`. With
 * Compose 1.12.1 the capture succeeds; assert its dimensions and pixels so an empty image cannot
 * masquerade as support. [RcIdleProbeTest] separately checks that the player reaches idle.
 *
 * The render harnesses retain `View.draw(Canvas(bitmap))` to preserve their reference pixels.
 * Migrating those harnesses to `captureToImage()` requires a separate rendered comparison.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RobolectricCaptureToImageProbeTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureToImageDrawsRedBoxUnderRobolectric() {
        composeRule.setContent { Box(Modifier.testTag(TAG).size(10.dp).background(Color.Red)) }
        composeRule.waitForIdle()

        val image = composeRule.onNodeWithTag(TAG).captureToImage()
        // xhdpi is 2 pixels per dp: this also rejects a full-window or empty capture.
        assertEquals(20, image.width)
        assertEquals(20, image.height)
        val pixels = image.toPixelMap()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                assertEquals("pixel ($x, $y)", Color.Red, pixels[x, y])
            }
        }
    }

    private companion object {
        const val TAG = "probe"
    }
}
