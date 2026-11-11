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

@file:kotlin.jvm.JvmName("HeightModifierKt")
@file:kotlin.jvm.JvmMultifileClass

package ee.schimke.composeai.rccreation.compose.modifier

import androidx.annotation.RestrictTo
import ee.schimke.composeai.rccreation.common.DimensionModifierOperation.Type
import ee.schimke.composeai.rccreation.common.RemoteModifierOperation
import ee.schimke.composeai.rccreation.compose.layout.RemoteIntrinsicSize
import ee.schimke.composeai.rccreation.compose.state.RemoteDp
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope
import ee.schimke.composeai.rccreation.compose.state.rf

internal class HeightModifier(val type: Type, val value: RemoteFloat) : RemoteModifier.Element {
    internal constructor(
        typeInt: Int,
        value: RemoteFloat,
    ) : this(Type.fromInt(typeInt), value)

    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation =
        RemoteModifierOperation.Height(type.ordinal, value.floatId)

    override fun toString(): String = "HeightModifier(type=$type, value=$value)"
}

/**
 * Sets the height of the content using [RemoteDp].
 *
 * @param height The height to apply in [RemoteDp].
 * @return a [RemoteModifier] that sets the height to [height]
 */
public fun RemoteModifier.height(height: RemoteDp): RemoteModifier =
    then(HeightModifier(Type.EXACT_DP, height.value))

/**
 * Sets the height of the content using [RemoteFloat].
 *
 * @param height The height to apply in pixels.
 * @return a [RemoteModifier] that sets the height to [height]
 */
public fun RemoteModifier.height(height: RemoteFloat): RemoteModifier =
    then(HeightModifier(Type.EXACT, height))

/**
 * Fills the maximum available height.
 *
 * @param fraction The fraction of the maximum height to use.
 * @return a [RemoteModifier] that fills [fraction] of the maximum available height
 */
public fun RemoteModifier.fillMaxHeight(fraction: RemoteFloat = 1f.rf): RemoteModifier =
    then(HeightModifier(Type.FILL, fraction))

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteModifier.fillMaxHeight(fraction: Float): RemoteModifier =
    then(HeightModifier(Type.FILL, RemoteFloat(fraction)))

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteModifier.fillParentMaxHeight(fraction: Float): RemoteModifier =
    then(HeightModifier(Type.FILL_PARENT_MAX_HEIGHT, RemoteFloat(fraction)))

/**
 * Fills the parent's maximum available height.
 *
 * @param fraction The fraction of the parent's maximum height to use.
 * @return a [RemoteModifier] that fills [fraction] of the parent's maximum height
 */
public fun RemoteModifier.fillParentMaxHeight(fraction: RemoteFloat = 1f.rf): RemoteModifier =
    then(HeightModifier(Type.FILL_PARENT_MAX_HEIGHT, fraction))

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteModifier.height(height: Int): RemoteModifier =
    then(HeightModifier(Type.EXACT, height.rf))

/**
 * Sets the height of the content to be its intrinsic size.
 *
 * @param intrinsicSize The intrinsic size to apply.
 * @return a [RemoteModifier] that sets the height to [intrinsicSize]
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteModifier.height(intrinsicSize: RemoteIntrinsicSize): RemoteModifier =
    then(HeightModifier(intrinsicSize.value, 0f.rf))
