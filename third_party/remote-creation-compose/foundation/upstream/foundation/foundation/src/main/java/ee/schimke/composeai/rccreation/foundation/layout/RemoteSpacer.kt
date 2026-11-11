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

package ee.schimke.composeai.rccreation.foundation.layout

import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier

/**
 * A spacer that takes up space, as defined by the provided [modifier], but doesn't get drawn.
 *
 * This is the remote equivalent of [androidx.compose.foundation.layout.Spacer].
 *
 * Example of a fixed-height [RemoteSpacer] separating items in a column:
 *
 * @sample ee.schimke.composeai.rccreation.foundation.samples.RemoteSpacerSample
 *
 * Example of a weighted [RemoteSpacer] pushing items to opposite ends of a row:
 *
 * @sample ee.schimke.composeai.rccreation.foundation.samples.RemoteSpacerWeightSample
 * @param modifier the [RemoteModifier] applied to this spacer (for example, to set its width,
 *   height, or weight)
 */
@RemoteComposable
@Composable
public fun RemoteSpacer(modifier: RemoteModifier = RemoteModifier) {
    RemoteBox(modifier = modifier)
}
