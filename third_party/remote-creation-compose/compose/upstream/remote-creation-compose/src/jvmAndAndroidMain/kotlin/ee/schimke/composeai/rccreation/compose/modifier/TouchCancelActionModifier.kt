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
import ee.schimke.composeai.rccreation.compose.action.RemoteAction
import ee.schimke.composeai.rccreation.compose.action.resolveAction
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope

internal class TouchCancelActionModifier(public val action: RemoteAction) : RemoteModifier.Element {
    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation =
        RemoteModifierOperation.Touch(2, resolveAction(action))
}

/**
 * Performs [action] when a touch gesture is cancelled on the element.
 *
 * @param action The [RemoteAction] to perform when the touch is cancelled.
 * @return a [RemoteModifier] that runs [action] when a touch is cancelled
 */
public fun RemoteModifier.onTouchCancel(action: RemoteAction): RemoteModifier =
    then(TouchCancelActionModifier(action))
