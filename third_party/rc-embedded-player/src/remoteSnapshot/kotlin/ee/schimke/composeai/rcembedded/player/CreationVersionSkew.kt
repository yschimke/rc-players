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

// VERSION SKEW (rc-players): this file has no upstream counterpart, and there are two of it, one per
// Remote Compose lane (`composeai.remoteCompose`, see `build.gradle.kts`). This one is the
// `snapshot` lane's, which builds against androidx-main, so it binds each name to what upstream's
// sources import. `src/remoteRelease` binds the same names to the pinned release's; see that file
// for why each differs. Delete both files, and restore the imports, once the pinned release ships
// all three. See PROVENANCE.md.

package ee.schimke.composeai.rcembedded.player

import androidx.compose.remote.core.RemoteComposeState

/** androidx-main's `RemoteCapturedDocument`. */
public typealias RemoteCapturedDocument =
    androidx.compose.remote.creation.compose.capture.RemoteCapturedDocument

/** androidx-main's `PendingIntentActionHandler`, in `remote-player-compose`. */
internal typealias PendingIntentActionHandler =
    androidx.compose.remote.player.compose.action.PendingIntentActionHandler

/** androidx-main's [RemoteComposeState], which declares `isFloatOverridden` itself. */
internal typealias RemoteComposeStateSkew = RemoteComposeState
