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

package ee.schimke.composeai.rccreation.compose.modifier

import androidx.annotation.RestrictTo
import ee.schimke.composeai.rccreation.common.RemoteModifierOperation
import ee.schimke.composeai.rccreation.compose.state.RemoteInt
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope

internal class IncludeMacroModifier(val id: Int, val args: Array<out RemoteInt>? = null) :
    RemoteModifier.Element {

    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation =
        RemoteModifierOperation.MacroCall(id, args?.map { it.id }?.toIntArray() ?: intArrayOf())
}

/**
 * Includes a macro as a modifier.
 *
 * @param id The id of the macro to include.
 * @return a [RemoteModifier] that includes macro [id]
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteModifier.includeMacro(id: Int): RemoteModifier = then(IncludeMacroModifier(id))

/**
 * Includes a macro as a modifier with arguments.
 *
 * @param id The id of the macro to include.
 * @param args The arguments for the macro.
 * @return a [RemoteModifier] that includes macro [id] with [args]
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteModifier.includeMacro(id: Int, vararg args: RemoteInt): RemoteModifier =
    then(IncludeMacroModifier(id, args))
