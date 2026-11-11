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

import ee.schimke.composeai.rccreation.common.MultiClickModifier
import ee.schimke.composeai.rccreation.common.RemoteModifierOperation
import ee.schimke.composeai.rccreation.compose.action.CombinedAction
import ee.schimke.composeai.rccreation.compose.action.RemoteAction
import ee.schimke.composeai.rccreation.compose.action.resolveAction
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope

internal class ClickableModifier(
    public val actions: List<RemoteAction>,
    public val clickType: Int = MultiClickModifier.CLICK_TYPE_SINGLE,
) : RemoteModifier.Element {
    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation =
        RemoteModifierOperation.Click(clickType, actions.flatMap { resolveAction(it) })
}

// TODO provide an onClickLabel
/**
 * Configure component to receive clicks via input or accessibility "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds.
 *
 * If you need to support double click or long click alongside the single click, consider using
 * [combinedClickable].
 *
 * Example of toggling remote state when an element is clicked:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.ClickableValueChangeSample
 * @param onClick will be called when user clicks on the element
 * @param enabled Controls the enabled state. When `false`, [onClick], and this modifier will appear
 *   disabled for accessibility services
 * @param role the type of user interface element. Accessibility services might use this to describe
 *   the element or do customizations. By default, no role is set (`null`).
 * @return a [RemoteModifier] that makes the component clickable
 */
public fun RemoteModifier.clickable(
    onClick: RemoteAction,
    enabled: Boolean = true,
    role: RemoteRole? = null,
): RemoteModifier =
    then(
            if (enabled) {
                val actions =
                    when (onClick) {
                        is CombinedAction -> onClick.actions.toList()
                        else -> listOfNotNull(onClick)
                    }
                if (actions.isNotEmpty()) ClickableModifier(actions) else RemoteModifier
            } else {
                RemoteModifier
            },
        )
        .then(
            if (role != null)
                RemoteModifier.semantics {
                    this.role = role
                    this.enabled = enabled
                }
            else RemoteModifier,
        )
