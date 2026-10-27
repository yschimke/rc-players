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

// VERSION SKEW (rc-players): this file has no upstream counterpart. It stands in for a
// `remote-core` API that androidx-main has and the pinned release (1.0.0-alpha22) does not.
// Delete it once the pinned release ships the API. See PROVENANCE.md.

package ee.schimke.composeai.rcembedded.player.draw

import androidx.compose.remote.core.operations.ImageAttribute

private val imageAttributeImageIdField =
    ImageAttribute::class.java.getDeclaredField("mImageId").apply { isAccessible = true }

/** `ImageAttribute.getImageId()` from androidx-main: the (possibly NaN-boxed) image id. */
internal val ImageAttribute.imageId: Int
    get() = imageAttributeImageIdField.getInt(this)
