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

package androidx.compose.remote.creation.compose.modifier

import androidx.compose.remote.creation.common.RemoteModifierOperation
import androidx.compose.remote.creation.compose.capture.RemoteDensityBehavior
import androidx.compose.remote.creation.compose.layout.RemoteLayoutDirection
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.layout.remoteComponent
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRectangleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.shapes.RemoteShape
import androidx.compose.remote.creation.compose.shapes.toDimension
import androidx.compose.remote.creation.compose.state.RemoteStateScope
import androidx.compose.remote.creation.compose.state.min

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
 * @sample androidx.compose.remote.creation.compose.samples.ModifierBackgroundAndClipSample
 * @param shape The [RemoteShape] to clip the content to.
 * @return a [RemoteModifier] that clips the content to [shape]
 */
public fun RemoteModifier.clip(shape: RemoteShape = RemoteRectangleShape): RemoteModifier =
    then(ClipModifier(shape))
