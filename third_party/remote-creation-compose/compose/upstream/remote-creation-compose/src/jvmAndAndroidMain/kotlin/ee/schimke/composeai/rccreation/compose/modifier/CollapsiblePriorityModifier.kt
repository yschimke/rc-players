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

import ee.schimke.composeai.rccreation.common.RemoteModifierOperation
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope

internal class CollapsiblePriorityModifier(
    val orientation: Int,
    val priority: RemoteFloat,
) : RemoteModifier.Element {

    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation =
        RemoteModifierOperation.CollapsiblePriority(orientation, priority.floatId)
}
