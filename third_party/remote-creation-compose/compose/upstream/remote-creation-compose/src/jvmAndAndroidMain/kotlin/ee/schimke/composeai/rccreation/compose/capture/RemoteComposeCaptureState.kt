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

    // KMP (rc-players): no `Profile` (it is `remote-creation-core`'s, with `RcPlatformServices`).
    // The operations a host opted in to and its image encoder stand in for it, and the one
    // constructor takes the writer, which upstream's `profile.create(…)` makes.
    public val supportedOperations: Set<Int>
    private val platformImageProvider: PlatformImageProvider
    public final override var layoutDirection: RemoteLayoutDirection

    public final override val densityBehavior: RemoteDensityBehavior

    public val time: MutableState<Long> = mutableLongStateOf(0L)

    // LOCAL PATCH (rc-players): what the canvas being flushed does when something writes a paint
    // its `PaintTracker` did not see, which a `RemoteTextMeasurer` measurement does; see
    // `RemoteCanvas.flush`.
    internal var onPaintWrittenOutsideCanvas: (() -> Unit)? = null

    // KMP (rc-players): see above; upstream's three constructors each take a `Profile`.
    public constructor(
        creationDisplayInfo: RemoteCreationDisplayInfo,
        writer: RemoteComposeWriter,
        remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
        layoutDirection: RemoteLayoutDirection = RemoteLayoutDirection.Ltr,
        supportedOperations: Set<Int> = emptySet(),
        platformImageProvider: PlatformImageProvider = NoOpPlatformImageProvider,
    ) : super() {
        this.creationDisplayInfo = creationDisplayInfo
        this.supportedOperations = supportedOperations
        this.platformImageProvider = platformImageProvider
        this.document = writer
        this.remoteDensity = remoteDensity
        this.layoutDirection = layoutDirection
        this.densityBehavior = creationDisplayInfo.densityBehavior
    }

    /** Writes [image] to the document through the host's encoder; -1 if it has none. */
    public fun addBitmap(image: ImageBitmap): Int = platformImageProvider.addBitmap(image)

    /** [addBitmap], bound to [name] so a host can replace the picture. */
    public fun addNamedBitmap(name: String, image: ImageBitmap): Int =
        platformImageProvider.addNamedBitmap(name, image)
}

@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val RemoteComposeCreationState.creationDisplayInfo: RemoteCreationDisplayInfo
    get() = (this as RemoteComposeCaptureState).creationDisplayInfo

// KMP (rc-players): no `profile` / `platform` extensions and no `RemoteComposeCreationState(…)`
// factories, which all take or return a `Profile`. The image encoder is reached the same way.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteComposeCreationState.addBitmap(image: ImageBitmap): Int =
    (this as RemoteComposeCaptureState).addBitmap(image)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteComposeCreationState.addNamedBitmap(name: String, image: ImageBitmap): Int =
    (this as RemoteComposeCaptureState).addNamedBitmap(name, image)

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
        // KMP (rc-players): the Kotlin writer, where upstream builds a `Profile` for one.
        writer = RemoteDocumentWriter(1, 1),
    )

@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val LocalRemoteComposeCreationState: ProvidableCompositionLocal<RemoteComposeCaptureState> =
    compositionLocalOf {
        NoRemoteCompose()
    }
