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
@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)

package ee.schimke.composeai.rccreation.compose.modifier

import androidx.annotation.RestrictTo
import ee.schimke.composeai.rccreation.common.RemoteModifierOperation
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope

/**
 * A modifier that animates the text content to scroll across the screen like a marquee.
 *
 * @param iterations The number of times to repeat the animation. Int.MAX_VALUE will repeat forever,
 *   and 0 will disable animation.
 * @param animationMode Whether the marquee should start animating Immediately or only WhileFocused.
 *   In WhileFocused mode, the modified node or the content must be made focusable. Note that the
 *   initialDelayMillis is part of the animation, so this parameter determines when that initial
 *   delay starts counting down, not when the content starts to actually scroll.
 * @param repeatDelayMillis The duration to wait before starting each subsequent iteration, in
 *   millis.
 * @param initialDelayMillis The duration to wait before starting the first iteration of the
 *   animation, in millis. By default, there will be no initial delay if animationMode is
 *   WhileFocused, otherwise the initial delay will be repeatDelayMillis.
 * @param spacing Specifies how much space to leave at the end of the content before showing the
 *   beginning again.
 * @param velocity The speed of the animation in dps / second.
 */
internal class MarqueeModifier(
    val iterations: Int,
    val animationMode: Int,
    val repeatDelayMillis: Float,
    val initialDelayMillis: Float,
    val spacing: Float,
    val velocity: Float,
) : RemoteModifier.Element {

    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation {
        return RemoteModifierOperation.Marquee(
            iterations,
            animationMode,
            repeatDelayMillis,
            initialDelayMillis,
            spacing,
            velocity,
        )
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteModifier.basicMarquee(
    iterations: Int = Int.MAX_VALUE,
    animationMode: Int = 0,
    repeatDelayMillis: Float = 0f,
    initialDelayMillis: Float = 0f,
    spacing: Float = 0f,
    velocity: Float = 20f,
): RemoteModifier =
    then(
        MarqueeModifier(
            iterations,
            animationMode,
            repeatDelayMillis,
            initialDelayMillis,
            spacing,
            velocity,
        ),
    )
