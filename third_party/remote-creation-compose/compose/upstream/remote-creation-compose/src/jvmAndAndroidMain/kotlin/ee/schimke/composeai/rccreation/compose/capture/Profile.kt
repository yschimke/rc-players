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

@file:kotlin.jvm.JvmName("ProfileKt")

package ee.schimke.composeai.rccreation.compose.capture

import androidx.annotation.RestrictTo
import androidx.collection.IntSet
import ee.schimke.composeai.rccreation.common.CoreDocument
import ee.schimke.composeai.rccreation.common.HTag
import ee.schimke.composeai.rccreation.common.Header
import ee.schimke.composeai.rccreation.common.Profile
import ee.schimke.composeai.rccreation.common.Profile.SupportedOperationsProvider
import ee.schimke.composeai.rccreation.common.RcProfiles
import ee.schimke.composeai.rccreation.common.RemoteComposeWriterFactory
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter

// KMP (rc-players): upstream's `Profile.android.kt`, which is in `androidMain` for its
// `AndroidxRcPlatformServices` and `RemoteComposeWriterAndroid`. Here it is common: the writer is
// `RemoteDocumentWriter` on every target.
/**
 * Creates a [Profile] for the given [docApiLevel], [profileFlags], and optional
 * [supportedOperations].
 *
 * @param docApiLevel The document API level.
 * @param profileFlags Bitmask of supported operation profiles.
 * @param supportedOperations Optional set of explicitly supported operation IDs. If null, the
 *   default operations for [docApiLevel] and [profileFlags] are used.
 * @param headerTags LOCAL PATCH (rc-players): header properties written after the width, height,
 *   content description and profiles (and the density behavior, when it is not the legacy one): a
 *   feature tag such as `Header.FEATURE_PAINT_MEASURE`. A consumer of AndroidX's API passes them to
 *   a writer factory of its own (`RemoteComposeWriterAndroid(profile, hTag(…), …)`); given any, the
 *   header is the one such a factory writes, and not given any, the one
 *   `RemoteComposeWriter(CreationDisplayInfo, String, Profile)` writes.
 */
public fun Profile(
    docApiLevel: Int = CoreDocument.DOCUMENT_API_LEVEL,
    profileFlags: Int = RcProfiles.PROFILE_ANDROIDX,
    supportedOperations: IntSet? = null,
    headerTags: List<HTag> = emptyList(),
): Profile {
    val factory = RemoteComposeWriterFactory { creationDisplayInfo, profile, _ ->
        if (headerTags.isEmpty()) {
            RemoteDocumentWriter(creationDisplayInfo, null, profile)
        } else {
            RemoteDocumentWriter(
                profile,
                HTag(Header.DOC_WIDTH, creationDisplayInfo.width),
                HTag(Header.DOC_HEIGHT, creationDisplayInfo.height),
                HTag(Header.DOC_CONTENT_DESCRIPTION, ""),
                HTag(Header.DOC_PROFILES, profile.operationsProfiles),
                *(if (creationDisplayInfo.densityBehavior != CoreDocument.DENSITY_BEHAVIOR_LEGACY) {
                        listOf(
                            HTag(Header.DOC_DENSITY_BEHAVIOR, creationDisplayInfo.densityBehavior),
                        )
                    } else {
                        emptyList()
                    } + headerTags)
                    .toTypedArray(),
            )
        }
    }
    return if (supportedOperations != null) {
        @Suppress("PrimitiveInCollection")
        val operationsSet =
            HashSet<Int>(supportedOperations.size).apply { supportedOperations.forEach { add(it) } }
        Profile(docApiLevel, profileFlags, SupportedOperationsProvider { operationsSet }, factory)
    } else {
        Profile(docApiLevel, profileFlags, factory)
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun createProfile(
    docApiLevel: Int = CoreDocument.DOCUMENT_API_LEVEL,
    profileFlags: Int = RcProfiles.PROFILE_ANDROIDX,
    supportedOperations: IntSet? = null,
): Profile = Profile(docApiLevel, profileFlags, supportedOperations)
