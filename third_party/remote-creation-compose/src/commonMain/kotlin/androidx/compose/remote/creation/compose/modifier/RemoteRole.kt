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

package androidx.compose.remote.creation.compose.modifier

import androidx.annotation.RestrictTo
import kotlin.jvm.JvmInline

/** The type of user interface element. Accessibility services can use this to describe the node. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@JvmInline
public value class RemoteRole internal constructor(internal val value: Int) {
    /** The wire value of AndroidX's `AccessibleComponent.Role` (its ordinal; 9 is `UNKNOWN`). */
    internal fun toAccessibleRole(): Int =
        when (this) {
            Button -> 0
            Checkbox -> 1
            Switch -> 2
            RadioButton -> 3
            Tab -> 4
            Image -> 5
            DropdownList -> 6
            else -> 9
        }

    override fun toString(): String =
        when (this) {
            Button -> "Button"
            Checkbox -> "Checkbox"
            Switch -> "Switch"
            RadioButton -> "RadioButton"
            Tab -> "Tab"
            Image -> "Image"
            DropdownList -> "DropdownList"
            else -> "Unknown"
        }

    public companion object {
        public val Button: RemoteRole = RemoteRole(0)
        public val Checkbox: RemoteRole = RemoteRole(1)
        public val Switch: RemoteRole = RemoteRole(2)
        public val RadioButton: RemoteRole = RemoteRole(3)
        public val Tab: RemoteRole = RemoteRole(4)
        public val Image: RemoteRole = RemoteRole(5)
        public val DropdownList: RemoteRole = RemoteRole(6)
        internal val Unknown: RemoteRole = RemoteRole(7)
    }
}
