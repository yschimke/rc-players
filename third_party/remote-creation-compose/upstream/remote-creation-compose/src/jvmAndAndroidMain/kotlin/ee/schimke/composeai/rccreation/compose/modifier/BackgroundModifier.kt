/*
 * Copyright 2025 The Android Open Source Project
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

package ee.schimke.composeai.rccreation.compose.modifier

import androidx.annotation.RestrictTo
import ee.schimke.composeai.rccreation.common.RemoteModifierOperation
import ee.schimke.composeai.rccreation.compose.painter.RemotePainter
import ee.schimke.composeai.rccreation.compose.shaders.RemoteBrush
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope

internal data class BackgroundModifier(val color: RemoteColor) : RemoteModifier.Element {
    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation =
        if (color.hasConstantValue) {
            RemoteModifierOperation.Background(
                flags = 0,
                colorId = 0,
                red = color.red.floatId,
                green = color.green.floatId,
                blue = color.blue.floatId,
                alpha = color.alpha.floatId,
                shape = 0,
            )
        } else {
            RemoteModifierOperation.Background(
                flags = 2,
                colorId = color.id,
                red = 0f,
                green = 0f,
                blue = 0f,
                alpha = 0f,
                shape = 0,
            )
        }
}

/**
 * Draws a solid [color] background behind the content.
 *
 * Example of applying a clipped solid background color:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.ModifierBackgroundAndClipSample
 * @param color The [RemoteColor] to use for the background.
 * @return a [RemoteModifier] that draws a [color] background behind the content
 */
public fun RemoteModifier.background(color: RemoteColor): RemoteModifier =
    this.then(BackgroundModifier(color))

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteModifier.background(brush: RemoteBrush): RemoteModifier =
    this.drawWithContent {
        drawRect(paint = RemotePaint { with(brush) { applyTo(this@RemotePaint, size) } })
        drawContent()
    }

/**
 * Draws a [painter] behind the content.
 *
 * @param painter The [RemotePainter] to use for the background.
 * @return a [RemoteModifier] that draws [painter] behind the content
 */
public fun RemoteModifier.background(painter: RemotePainter): RemoteModifier =
    this.drawWithContent {
        with(painter) { onDraw() }
        drawContent()
    }
