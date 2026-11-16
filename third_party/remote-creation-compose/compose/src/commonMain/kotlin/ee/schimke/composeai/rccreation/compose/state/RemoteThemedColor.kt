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

package ee.schimke.composeai.rccreation.compose.state

import androidx.annotation.RestrictTo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// KMP (rc-players): upstream's `createThemedRemoteColor` (`RemoteColor.android.kt`) takes
// `android.R.color` resource ids and maps each to its index in the "android" colour group with
// `AndroidSystemColorMap`, which reads `android.R`. The document it writes holds only the group
// and the two indices, so this common one takes the indices and writes the same operation from
// every target. The cache key is upstream's `RemoteStateThemeColorKey`, unchanged.

/** The colour group a themed colour's indices are in: Android's system colours. */
public const val ANDROID_SYSTEM_COLOR_GROUP: String = "android"

/**
 * Creates a [RemoteColor] that follows the player's Android system (dynamic) colour at
 * [lightColorIndex] in light mode and [darkColorIndex] in dark mode, each an index in the
 * [ANDROID_SYSTEM_COLOR_GROUP] group (AndroidX's `AndroidSystemColorMap`), falling back to
 * [lightFallback] and [darkFallback] where the player has no themed colours.
 */
public fun RemoteColor.Companion.createThemedRemoteColor(
    lightColorIndex: Short,
    darkColorIndex: Short,
    lightFallback: Color,
    darkFallback: Color,
): RemoteColor {
    val light = lightFallback.toArgb()
    val dark = darkFallback.toArgb()
    val cacheKey =
        RemoteStateThemeColorKey(
            group = ANDROID_SYSTEM_COLOR_GROUP,
            lightModeIndex = lightColorIndex,
            darkModeIndex = darkColorIndex,
            lightFallback = light,
            darkFallback = dark,
        )
    return RemoteColor(
        cacheKey = cacheKey,
        idProvider = { creationState ->
            creationState.getOrPutVariableId(cacheKey) {
                creationState.document
                    .addThemedColor(
                        ANDROID_SYSTEM_COLOR_GROUP,
                        lightColorIndex,
                        darkColorIndex,
                        light,
                        dark,
                    )
                    .toInt()
            }
        },
    )
}

/**
 * A cache key for themed colors, identified by their [group], [lightModeIndex], [darkModeIndex],
 * [lightFallback], and [darkFallback].
 */
internal class RemoteStateThemeColorKey(
    internal val group: String,
    internal val lightModeIndex: Short,
    internal val darkModeIndex: Short,
    internal val lightFallback: Int,
    internal val darkFallback: Int,
) : BaseRemoteStateCacheKey() {
    private var state: BaseRemoteState<*>? = null

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun getState(): BaseRemoteState<*>? = state

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun setState(state: BaseRemoteState<*>?) {
        this.state = state
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteStateThemeColorKey) return false
        if (group != other.group) return false
        if (lightModeIndex != other.lightModeIndex) return false
        if (darkModeIndex != other.darkModeIndex) return false
        if (lightFallback != other.lightFallback) return false
        if (darkFallback != other.darkFallback) return false
        return true
    }

    override fun hashCodeImpl(): Int {
        var result = group.hashCode()
        result = 31 * result + lightModeIndex.hashCode()
        result = 31 * result + darkModeIndex.hashCode()
        result = 31 * result + lightFallback.hashCode()
        result = 31 * result + darkFallback.hashCode()
        return result
    }

    override fun toString(): String =
        "RemoteStateThemeColorKey(group=$group, lightModeIndex=$lightModeIndex, darkModeIndex=$darkModeIndex, lightFallback=$lightFallback, darkFallback=$darkFallback)"

    override fun toDebugString(): String =
        "themeColor($group, light=$lightModeIndex, dark=$darkModeIndex)"
}
