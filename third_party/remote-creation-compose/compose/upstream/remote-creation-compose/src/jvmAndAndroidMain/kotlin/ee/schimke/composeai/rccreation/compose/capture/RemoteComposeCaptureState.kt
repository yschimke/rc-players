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
@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@file:JvmName("RemoteComposeCaptureStateKt")

package ee.schimke.composeai.rccreation.compose.capture

import androidx.annotation.RestrictTo
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.ImageBitmap
import ee.schimke.composeai.rccreation.common.CoreDocument
import ee.schimke.composeai.rccreation.common.Profile
import ee.schimke.composeai.rccreation.common.RcProfiles
import ee.schimke.composeai.rccreation.common.RemoteComposeWriter
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.layout.RemoteComponentToken
import ee.schimke.composeai.rccreation.compose.layout.RemoteLayoutDirection
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope
import kotlin.jvm.JvmName

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class RemoteComposeCaptureState : RemoteComposeCreationState, RemoteStateScope {

    override val parentScope: RemoteComposeCaptureState
        get() = this

    public override fun resolveComponentToken(): RemoteComponentToken? =
        super<RemoteComposeCreationState>.resolveComponentToken()

    // KMP (rc-players): the creation state's, not `RemoteStateScope`'s parent's; see
    // [RemoteDocumentScope.lastComponentId].
    public override var lastComponentId: Int
        get() = super<RemoteComposeCreationState>.lastComponentId
        set(value) {
            super<RemoteComposeCreationState>.lastComponentId = value
        }

    public override var document: RemoteComposeWriter
        get() = super<RemoteComposeCreationState>.document
        set(value) {
            super<RemoteComposeCreationState>.document = value
        }

    public val creationDisplayInfo: RemoteCreationDisplayInfo

    public val profile: Profile

    // KMP (rc-players): the host's image encoder, where upstream's profile carries
    // `RcPlatformServices`.
    private val platformImageProvider: PlatformImageProvider

    // LOCAL PATCH (rc-players): what stood in for the profile's operations before the fork had
    // `Profile`.
    @Deprecated(
        "Read the profile's operations.",
        ReplaceWith("profile.supportedOperations"),
    )
    public val supportedOperations: Set<Int>
        get() = profile.supportedOperations

    public final override var layoutDirection: RemoteLayoutDirection

    public final override val densityBehavior: RemoteDensityBehavior

    public val time: MutableState<Long> = mutableLongStateOf(0L)

    // LOCAL PATCH (rc-players): what the canvas being flushed does when something writes a paint
    // its `PaintTracker` did not see, which a `RemoteTextMeasurer` measurement does; see
    // `RemoteCanvas.flush`.
    internal var onPaintWrittenOutsideCanvas: (() -> Unit)? = null

    // KMP (rc-players): the image encoder is a parameter of each constructor, where upstream's
    // profile carries the platform's services.
    public constructor(
        creationDisplayInfo: RemoteCreationDisplayInfo,
        profile: Profile,
        writerCallback: Any?,
        remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
        layoutDirection: RemoteLayoutDirection = RemoteLayoutDirection.Ltr,
        platformImageProvider: PlatformImageProvider = NoOpPlatformImageProvider,
    ) : super() {
        this.creationDisplayInfo = creationDisplayInfo
        this.profile = profile
        this.platformImageProvider = platformImageProvider
        document = profile.create(creationDisplayInfo.toCreationDisplayInfo(), writerCallback)
        this.remoteDensity = remoteDensity
        this.layoutDirection = layoutDirection
        this.densityBehavior = creationDisplayInfo.densityBehavior
    }

    public constructor(
        creationDisplayInfo: RemoteCreationDisplayInfo,
        contentDescription: String?,
        profile: Profile,
    ) : super() {
        this.creationDisplayInfo = creationDisplayInfo
        this.profile = profile
        this.platformImageProvider = NoOpPlatformImageProvider
        document = profile.create(creationDisplayInfo.toCreationDisplayInfo(), null)
        this.remoteDensity = RemoteDensity.from(creationDisplayInfo)
        this.layoutDirection = RemoteLayoutDirection.Ltr
        this.densityBehavior = creationDisplayInfo.densityBehavior
    }

    // KMP (rc-players): takes the density, the direction and the image encoder too, for a capture
    // into a writer its caller made (`captureCommonRemoteDocumentInto`, `RemoteDocumentSession`).
    public constructor(
        creationDisplayInfo: RemoteCreationDisplayInfo,
        profile: Profile,
        writer: RemoteComposeWriter,
        remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
        layoutDirection: RemoteLayoutDirection = RemoteLayoutDirection.Ltr,
        platformImageProvider: PlatformImageProvider = NoOpPlatformImageProvider,
    ) : super() {
        this.creationDisplayInfo = creationDisplayInfo
        this.profile = profile
        this.platformImageProvider = platformImageProvider
        this.document = writer
        this.remoteDensity = remoteDensity
        this.layoutDirection = layoutDirection
        this.densityBehavior = creationDisplayInfo.densityBehavior
    }

    // LOCAL PATCH (rc-players): the constructor of rc-creation-compose 3.0.0, from before the fork
    // had `Profile`: the operations given answer what a component asks, under AndroidX's profile.
    @Deprecated("Pass a Profile.")
    public constructor(
        creationDisplayInfo: RemoteCreationDisplayInfo,
        writer: RemoteComposeWriter,
        remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
        layoutDirection: RemoteLayoutDirection = RemoteLayoutDirection.Ltr,
        supportedOperations: Set<Int> = emptySet(),
        platformImageProvider: PlatformImageProvider = NoOpPlatformImageProvider,
    ) : this(
        creationDisplayInfo,
        legacyProfile(supportedOperations),
        writer,
        remoteDensity,
        layoutDirection,
        platformImageProvider,
    )

    /** Writes [image] to the document through the host's encoder; -1 if it has none. */
    public fun addBitmap(image: ImageBitmap): Int = platformImageProvider.addBitmap(image)

    /** [addBitmap], bound to [name] so a host can replace the picture. */
    public fun addNamedBitmap(name: String, image: ImageBitmap): Int =
        platformImageProvider.addNamedBitmap(name, image)
}

@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val RemoteComposeCreationState.creationDisplayInfo: RemoteCreationDisplayInfo
    get() = (this as RemoteComposeCaptureState).creationDisplayInfo

@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val RemoteComposeCreationState.profile: Profile
    get() = (this as RemoteComposeCaptureState).profile

// KMP (rc-players): no `platform` extension (`RcPlatformServices`); the image encoder is reached
// the same way.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteComposeCreationState.addBitmap(image: ImageBitmap): Int =
    (this as RemoteComposeCaptureState).addBitmap(image)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteComposeCreationState.addNamedBitmap(name: String, image: ImageBitmap): Int =
    (this as RemoteComposeCaptureState).addNamedBitmap(name, image)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteComposeCreationState(
    creationDisplayInfo: RemoteCreationDisplayInfo,
    profile: Profile,
    writerCallback: Any?,
    remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    layoutDirection: RemoteLayoutDirection = RemoteLayoutDirection.Ltr,
): RemoteComposeCaptureState =
    RemoteComposeCaptureState(
        creationDisplayInfo,
        profile,
        writerCallback,
        remoteDensity,
        layoutDirection,
    )

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteComposeCreationState(
    creationDisplayInfo: RemoteCreationDisplayInfo,
    contentDescription: String?,
    profile: Profile,
): RemoteComposeCaptureState =
    RemoteComposeCaptureState(
        creationDisplayInfo,
        contentDescription,
        profile,
    )

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteComposeCreationState(
    creationDisplayInfo: RemoteCreationDisplayInfo,
    profile: Profile,
    writer: RemoteComposeWriter,
): RemoteComposeCaptureState = RemoteComposeCaptureState(creationDisplayInfo, profile, writer)

/**
 * LOCAL PATCH (rc-players): the profile of a capture that names the operations its reader takes and
 * no profile, as rc-creation-compose 3.0.0's captures did: AndroidX's level and bits, and
 * [supportedOperations] for what a component asks (`currentProfileSupports…`). Its writer is not
 * made by it; the caller's own writes what it is asked to.
 */
internal fun legacyProfile(supportedOperations: Set<Int>): Profile =
    Profile(
        CoreDocument.DOCUMENT_API_LEVEL,
        RcProfiles.PROFILE_ANDROIDX,
        { supportedOperations },
        { creationDisplayInfo, _, _ ->
            RemoteDocumentWriter(
                creationDisplayInfo.width,
                creationDisplayInfo.height,
                creationDisplayInfo.densityBehavior,
            )
        },
    )

internal object NoOpPlatformImageProvider : PlatformImageProvider {
    override fun addBitmap(image: ImageBitmap): Int = -1

    override fun addNamedBitmap(name: String, image: ImageBitmap): Int = -1
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface PlatformImageProvider {
    public fun addBitmap(image: ImageBitmap): Int

    public fun addNamedBitmap(name: String, image: ImageBitmap): Int
}

// Density and Size should be taken from Compose in this mode
internal class NoRemoteCompose :
    RemoteComposeCaptureState(
        creationDisplayInfo = RemoteCreationDisplayInfo(1, 1, 160, 1.0f),
        contentDescription = null,
        profile =
            Profile(
                CoreDocument.DOCUMENT_API_LEVEL,
                0,
                { creationDisplayInfo, profile, _ ->
                    RemoteDocumentWriter(creationDisplayInfo, null, profile)
                },
            ),
    )

@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val LocalRemoteComposeCreationState: ProvidableCompositionLocal<RemoteComposeCaptureState> =
    compositionLocalOf {
        NoRemoteCompose()
    }
