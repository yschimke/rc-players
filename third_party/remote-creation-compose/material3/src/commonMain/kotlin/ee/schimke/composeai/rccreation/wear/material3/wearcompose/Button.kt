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

package ee.schimke.composeai.rccreation.wear.material3.wearcompose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Wear Compose Material 3's `ButtonDefaults`: the scrim colours `RemoteButton` draws over an image.
// Wear Compose reads them from the theme's `surfaceContainer` (`ImageButtonTokens`); a remote
// capture has no Wear Compose theme, so they are the default scheme's.

/** Contains the default values used by buttons. */
public object ButtonDefaults {
    /**
     * The default gradient start color when drawing a scrim over an image to ensure that the text
     * drawn over the image is legible.
     */
    public val scrimGradientStartColor: Color
        @Composable get() = ColorTokens.SurfaceContainer.copy(alpha = 1.0f)

    /**
     * The default gradient end color when drawing a scrim over an image to ensure that the text
     * drawn over the image is legible.
     */
    public val scrimGradientEndColor: Color
        @Composable get() = ColorTokens.SurfaceContainer.copy(alpha = 0.0f)
}
