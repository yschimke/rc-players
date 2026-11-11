/*
 * Copyright (C) 2024 The Android Open Source Project
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

import ee.schimke.composeai.rccreation.common.RemoteModifierOperation
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensityBehavior
import ee.schimke.composeai.rccreation.compose.layout.RemoteLayoutDirection
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.layout.remoteComponent
import ee.schimke.composeai.rccreation.compose.shapes.RemoteCircleShape
import ee.schimke.composeai.rccreation.compose.shapes.RemoteRectangleShape
import ee.schimke.composeai.rccreation.compose.shapes.RemoteRoundedCornerShape
import ee.schimke.composeai.rccreation.compose.shapes.RemoteShape
import ee.schimke.composeai.rccreation.compose.shapes.toDimension
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope
import ee.schimke.composeai.rccreation.compose.state.min

internal class ClipModifier(val shape: RemoteShape = RemoteRectangleShape) :
    RemoteModifier.Element {
    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation {
        if (shape == RemoteRectangleShape) return RemoteModifierOperation.ClipRect
        if (shape == RemoteCircleShape) {
            val component = remoteComponent()
            val remoteSize = RemoteSize(component.width, component.height)
            val radius =
                if (densityBehavior == RemoteDensityBehavior.Dp) {
                    min(remoteSize.width, remoteSize.height) / remoteDensity.density / 2f
                } else {
                    min(remoteSize.width, remoteSize.height) / 2f
                }
            return RemoteModifierOperation.RoundedClipRect(
                radius.floatId,
                radius.floatId,
                radius.floatId,
                radius.floatId,
            )
        }
        if (shape is RemoteRoundedCornerShape) {
            val component = remoteComponent()
            val remoteSize = RemoteSize(component.width, component.height)
            val isRtl = layoutDirection == RemoteLayoutDirection.Rtl
            return RemoteModifierOperation.RoundedClipRect(
                (if (isRtl) shape.topEnd else shape.topStart)
                    .toDimension(remoteSize, remoteDensity, densityBehavior)
                    .floatId,
                (if (isRtl) shape.topStart else shape.topEnd)
                    .toDimension(remoteSize, remoteDensity, densityBehavior)
                    .floatId,
                (if (isRtl) shape.bottomEnd else shape.bottomStart)
                    .toDimension(remoteSize, remoteDensity, densityBehavior)
                    .floatId,
                (if (isRtl) shape.bottomStart else shape.bottomEnd)
                    .toDimension(remoteSize, remoteDensity, densityBehavior)
                    .floatId,
            )
        }
        return RemoteModifierOperation.ClipRect
    }
}

/**
 * Clips the content to [shape].
 *
 * Example of clipping a background to rounded corners:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.ModifierBackgroundAndClipSample
 * @param shape The [RemoteShape] to clip the content to.
 * @return a [RemoteModifier] that clips the content to [shape]
 */
public fun RemoteModifier.clip(shape: RemoteShape = RemoteRectangleShape): RemoteModifier =
    then(ClipModifier(shape))
