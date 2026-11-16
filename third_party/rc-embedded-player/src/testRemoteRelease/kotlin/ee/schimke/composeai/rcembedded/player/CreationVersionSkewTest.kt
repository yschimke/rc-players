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

// VERSION SKEW (rc-players): the tests' binding of a name the pinned release (1.0.0-alpha23) spells
// differently from androidx-main, for the default `release` lane. `src/testRemoteSnapshot` binds it
// for `-Pcomposeai.remoteCompose=snapshot`; `build.gradle.kts` compiles one or the other. Delete
// both once the pinned release has androidx-main's name, and import it instead.

package ee.schimke.composeai.rcembedded.player

import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.action.valueChange
import androidx.compose.remote.creation.compose.state.MutableRemoteState
import androidx.compose.remote.creation.compose.state.RemoteState

/** androidx-main's `valueChangeAction`: alpha23's `valueChange`, renamed. */
internal fun <T> valueChangeAction(
    remoteState: MutableRemoteState<T>,
    updatedValue: RemoteState<T>,
): RemoteAction = valueChange(remoteState, updatedValue)
