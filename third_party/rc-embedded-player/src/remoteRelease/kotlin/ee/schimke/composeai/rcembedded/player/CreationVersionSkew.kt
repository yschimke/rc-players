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
// `release` lane's. It stands in for three names androidx-main has and the pinned release
// (1.0.0-alpha23) does not:
//
// - `remote-creation-compose`'s `CapturedDocument` became `RemoteCapturedDocument` in 7177ff18cdd,
//   after the alpha23 cut. `RcPlayer.kt` and `RcPlayerState.kt` drop their import of the new name
//   and resolve it here.
// - `PendingIntentActionHandler` moved from `remote-creation-compose`
//   (`androidx.compose.remote.creation.compose.action`) to `remote-player-compose`
//   (`androidx.compose.remote.player.compose.action`) in 3be30799c59, unchanged. `RcPlayer.kt`
//   drops its import of the new location and resolves alpha23's class here.
// - `RemoteComposeState.isFloatOverridden(int)` is new in `remote-core` after alpha23, and
//   `doc/SnapshotRemoteComposeState.kt` overrides it. Here it extends a class that declares it.
//
// `src/remoteSnapshot` has the same names bound to androidx-main's. Delete both files, and restore
// the imports, once the pinned release ships all three. See PROVENANCE.md.

package ee.schimke.composeai.rcembedded.player

import androidx.compose.remote.core.RemoteComposeState
import androidx.compose.remote.creation.compose.capture.CapturedDocument

/** `RemoteCapturedDocument` from androidx-main: alpha23's [CapturedDocument], renamed. */
public typealias RemoteCapturedDocument = CapturedDocument

/**
 * `androidx.compose.remote.player.compose.action.PendingIntentActionHandler` from androidx-main:
 * alpha23's class of the same name in `remote-creation-compose`, moved.
 */
internal typealias PendingIntentActionHandler =
    androidx.compose.remote.creation.compose.action.PendingIntentActionHandler

/**
 * alpha23's [RemoteComposeState] with androidx-main's `isFloatOverridden`, so that
 * `SnapshotRemoteComposeState` can override it on either lane. Nothing in alpha23's core calls it.
 */
internal open class RemoteComposeStateSkew : RemoteComposeState() {
    /** Whether a host/action override should take precedence over the id's authored expression. */
    public open fun isFloatOverridden(id: Int): Boolean = false
}
