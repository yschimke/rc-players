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

package androidx.compose.remote.creation.compose.layout

import androidx.annotation.RestrictTo

/**
 * Identity token representing a specific layout component node in a RemoteCompose composition tree.
 *
 * Expressions scoped to this component read its `ComponentValue`s (width, height, position) by
 * token, so they refer to this component wherever they are consumed.
 */
// Port: upstream also records which properties were requested (`requestedMask`) so it can write
// their `ComponentValue`s inside the component's body. The port writes them with the component's
// explicit id instead (see `RemoteComposeCreationState.getComponentValue`), so it needs no mask.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteComponentToken public constructor() {
    internal companion object {
        internal const val PROP_WIDTH: Int = 0
        internal const val PROP_HEIGHT: Int = 1
        internal const val PROP_X: Int = 2
        internal const val PROP_Y: Int = 3
        internal const val PROP_ROOT_X: Int = 4
        internal const val PROP_ROOT_Y: Int = 5
        internal const val PROP_COUNT: Int = 6

        /** The wire `ComponentValue` type of [propIndex]: AndroidX's `ComponentValue.WIDTH`, ... */
        internal fun toComponentValueType(propIndex: Int): Int =
            when (propIndex) {
                PROP_WIDTH -> 0
                PROP_HEIGHT -> 1
                PROP_X -> 2
                PROP_Y -> 3
                PROP_ROOT_X -> 4
                PROP_ROOT_Y -> 5
                else -> throw IllegalArgumentException("Unknown property index: $propIndex")
            }
    }
}
