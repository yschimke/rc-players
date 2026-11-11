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

package ee.schimke.composeai.rccreation.compose.state

import androidx.compose.remote.core.RemoteComposeBuffer
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import java.io.ByteArrayInputStream

// What AndroidX's own tests of `remote-creation-expression` (under `upstream/…/src/test`) need in
// place of `RemoteComposeWriter(RcPlatformServices.None)` and its `buffer`: the fork's writer, and
// what it wrote handed to AndroidX's reader (`remote-core`, the release this repository depends
// on).

/** The fork's stand-in for AndroidX's `RemoteComposeWriter(RcPlatformServices.None)`. */
internal fun testDocumentWriter(): RemoteDocumentWriter = RemoteDocumentWriter(100, 100)

/** The document written so far, as bytes. */
internal fun RemoteComposeCreationState.documentBytes(): ByteArray =
    (document as RemoteDocumentWriter).encodeToByteArray()

/** The document written so far, in AndroidX's buffer, ready to be inflated or initialised from. */
internal fun RemoteComposeCreationState.androidxBuffer(): RemoteComposeBuffer =
    RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(documentBytes()))
