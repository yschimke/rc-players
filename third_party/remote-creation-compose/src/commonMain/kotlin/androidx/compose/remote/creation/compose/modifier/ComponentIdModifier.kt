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

// LOCAL PATCH (rc-players): an explicit component id for the component this modifier decorates.
// Upstream allocates every component id from a counter, so ids are positional: an insertion
// renumbers every later component, and nothing outside the document can name one. A host that
// keys anything on a component (an editor's per-node bounds, carried player state) gives it a
// stable id of its own. It writes no operation; `RemoteComposeNode.resolveModifier` reads it.
internal class ComponentIdModifier(val id: Int) : RemoteModifier.Element

/**
 * Gives the component this modifier decorates the component id [id], instead of the next one the
 * document allocates.
 *
 * [id] must be negative, as every component id is, and must not be one the document allocates
 * itself: those count down from `-1_000_000`. An id is honoured once per document; a second
 * component asking for the same one gets an allocated id instead, so a modifier a component library
 * applies to two of its nodes can never give the document a duplicate.
 */
public fun RemoteModifier.componentId(id: Int): RemoteModifier {
    require(id < 0) { "Remote Compose component ids are negative, was $id" }
    return then(ComponentIdModifier(id))
}

internal val RemoteModifier.requestedComponentId: Int?
    get() =
        foldIn<Int?>(null) { requested, element ->
            requested ?: (element as? ComponentIdModifier)?.id
        }
