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

package ee.schimke.composeai.rccreation.compose.state

import androidx.annotation.RestrictTo
import ee.schimke.composeai.rccreation.common.RemoteComposeWriter
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensity
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensityBehavior
import ee.schimke.composeai.rccreation.compose.capture.RemoteDocumentScope
import ee.schimke.composeai.rccreation.compose.capture.toRemotePath
import ee.schimke.composeai.rccreation.compose.layout.RemoteComponentToken
import ee.schimke.composeai.rccreation.compose.layout.RemoteLayoutDirection
import ee.schimke.composeai.rccreation.compose.path.RemotePath
import ee.schimke.composeai.rccreation.compose.vector.RemotePathScope

/** Scope for accessing remote state IDs. */
@Suppress("HiddenSuperclass")
public interface RemoteStateScope : RemoteDocumentScope {
    /** The parent [RemoteStateScope] associated with the document being drawn into. */
    @get:Suppress("HiddenAbstractMethodInInterface")
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val parentScope: RemoteStateScope

    /** The [RemoteDensity] associated with the document being drawn into. */
    public val remoteDensity: RemoteDensity

    /** The [RemoteLayoutDirection] associated with the document being drawn into. */
    public val layoutDirection: RemoteLayoutDirection

    /** The [RemoteComposeWriter] associated with the document being drawn into. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override val document: RemoteComposeWriter
        get() = parentScope.document

    /**
     * Resolves the active [RemoteComponentToken] for this scope, or `null` if none is bound.
     *
     * @return the active [RemoteComponentToken], or `null` if none is bound
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun resolveComponentToken(): RemoteComponentToken? =
        parentScope.resolveComponentToken()

    /** The density behavior associated with the document being drawn into. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val densityBehavior: RemoteDensityBehavior
        get() = parentScope.densityBehavior

    /** Returns the ID for this state within the scope. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val RemoteState<*>.id: Int
        get() = (this as BaseRemoteState<*>).getIdForCreationState(creationState)

    /** Returns the float ID for this state within the scope. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val RemoteState<*>.floatId: Float
        get() = (this as BaseRemoteState<*>).getFloatIdForCreationState(creationState)

    /** Returns the long ID for this state within the scope. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val RemoteState<*>.longId: Long
        get() = (this as BaseRemoteState<*>).getLongIdForCreationState(creationState)
}

/**
 * Build the [RemotePath], encoding using this [RemoteStateScope].
 *
 * @return the constructed [RemotePath]
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteStateScope.remotePath(fn: RemotePathScope.() -> Unit): RemotePath {
    return RemotePathScope().apply(fn).nodes.toRemotePath(creationState = this)
}

/** The [RemoteComposeCreationState] associated with the document being drawn into. */
@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val RemoteStateScope.creationState: RemoteComposeCreationState
    get() = this as? RemoteComposeCreationState ?: parentScope.creationState

// KMP (rc-players): `withGlobalScope` is not carried: `RemoteWriter` has no `beginGlobal` /
// `endGlobal`, as it hoists every declaration ahead of the layout itself.
