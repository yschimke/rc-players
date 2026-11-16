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

// KMP (rc-players): `android.R.color`'s system colours that `RemoteColorScheme` names, as their
// index
// in the "android" themed-colour group (AndroidX's `AndroidSystemColorMap`), which is all a themed
// colour writes into a document. `RemoteColorRole` passes them to the fork's common
// `createThemedRemoteColor`, which takes indices where upstream's takes resource ids.

@Suppress("ClassName", "ObjectPropertyName")
internal object R {
    object color {
        const val system_background_dark: Int = 53
        const val system_error_300: Int = 66
        const val system_error_container_dark: Int = 74
        const val system_error_dark: Int = 76
        const val system_on_background_dark: Int = 104
        const val system_on_error_container_dark: Int = 106
        const val system_on_error_dark: Int = 108
        const val system_on_primary_container_dark: Int = 110
        const val system_on_primary_dark: Int = 112
        const val system_on_secondary_container_dark: Int = 116
        const val system_on_secondary_dark: Int = 118
        const val system_on_surface_dark: Int = 122
        const val system_on_surface_variant_dark: Int = 125
        const val system_on_tertiary_container_dark: Int = 127
        const val system_on_tertiary_dark: Int = 129
        const val system_outline_dark: Int = 133
        const val system_outline_variant_dark: Int = 136
        const val system_primary_container_dark: Int = 148
        const val system_primary_dark: Int = 150
        const val system_primary_fixed_dim: Int = 152
        const val system_secondary_container_dark: Int = 154
        const val system_secondary_dark: Int = 156
        const val system_secondary_fixed_dim: Int = 158
        const val system_surface_container_dark: Int = 162
        const val system_surface_container_high_dark: Int = 163
        const val system_surface_container_low_dark: Int = 168
        const val system_tertiary_container_dark: Int = 179
        const val system_tertiary_dark: Int = 181
        const val system_tertiary_fixed_dim: Int = 183
    }
}
