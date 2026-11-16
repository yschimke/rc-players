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

// VERSION SKEW (rc-players): the `-Pcomposeai.remoteCompose=snapshot` lane's binding of the name
// `src/testRemoteRelease` declares for the pinned release. See that file.

package ee.schimke.composeai.rcembedded.player

import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.state.MutableRemoteState
import androidx.compose.remote.creation.compose.state.RemoteState

/** androidx-main's `valueChangeAction`. */
internal fun <T> valueChangeAction(
    remoteState: MutableRemoteState<T>,
    updatedValue: RemoteState<T>,
): RemoteAction =
    androidx.compose.remote.creation.compose.action.valueChangeAction(remoteState, updatedValue)
