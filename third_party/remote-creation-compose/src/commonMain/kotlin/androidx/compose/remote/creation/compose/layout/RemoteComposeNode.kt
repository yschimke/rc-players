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

package androidx.compose.remote.creation.compose.layout

import androidx.annotation.RestrictTo
import androidx.compose.remote.creation.common.RemoteModifierData
import androidx.compose.remote.creation.compose.capture.LocalRemoteDensity
import androidx.compose.remote.creation.compose.capture.LocalRemoteLayoutDirection
import androidx.compose.remote.creation.compose.capture.RemoteComposeCreationState
import androidx.compose.remote.creation.compose.capture.RemoteDensity
import androidx.compose.remote.creation.compose.capture.WriterOp
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.drawWithContent
import androidx.compose.remote.creation.compose.modifier.hasDrawWithContent
import androidx.compose.remote.creation.compose.modifier.requestedComponentId
import androidx.compose.remote.creation.compose.modifier.toRemoteModifierData
import androidx.compose.remote.creation.compose.state.PlatformRemoteStateScope
import androidx.compose.remote.creation.compose.state.RemoteStateScope
import androidx.compose.remote.creation.compose.util.fastForEach
import androidx.compose.remote.creation.compose.util.fastForEachReversed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Updater

internal abstract class RemoteComposeNode {
    val children = mutableListOf<RemoteComposeNode>()
    var modifier: RemoteModifier = RemoteModifier
    var remoteDensity: RemoteDensity = RemoteDensity.Host
    var layoutDirection: RemoteLayoutDirection = RemoteLayoutDirection.Ltr
    private var renderedComponentId: Int = 0

    // LOCAL PATCH (rc-players): whether the writer follows this component with a content section
    // (`LayoutContent`); every layout does, an image does not.
    protected open val writesContentSection: Boolean = true

    fun overriddenScope(creationState: RemoteComposeCreationState): RemoteStateScope {
        return OverriddenScope(creationState, remoteDensity, creationState.remoteLayoutDirection)
    }

    abstract fun render(creationState: RemoteComposeCreationState, remoteCanvas: RemoteCanvas)

    fun resolveModifier(
        scope: RemoteStateScope,
        creationState: RemoteComposeCreationState,
    ): RemoteModifierData {
        // LOCAL PATCH (rc-players): the modifiers convert first, so a size they read
        // (`RemoteFloatContext.componentWidth`) is the last component recorded before this one, as
        // AndroidX's `lastComponentId` is when it converts them.
        val data = scope.toRemoteModifierData(modifier)
        // LOCAL PATCH (rc-players): honour an explicit id (`RemoteModifier.componentId`) once.
        renderedComponentId =
            modifier.requestedComponentId?.takeIf(creationState::claimComponentId)
                ?: creationState.allocateComponentId()
        // LOCAL PATCH (rc-players): the content section's id is assigned now too, so what this
        // component's descendants and later siblings read as the last component is known while
        // recording, not only when the writer replays. A component written without a content
        // section (an image) is itself the last one.
        val contentId = if (writesContentSection) creationState.allocateComponentId() else -1
        creationState.lastComponentId = if (writesContentSection) contentId else renderedComponentId
        return data.apply {
            componentId = renderedComponentId
            contentComponentId = contentId
        }
    }

    fun renderChildren(
        creationState: RemoteComposeCreationState,
        remoteCanvas: RemoteCanvas,
        reversed: Boolean = false,
    ) {
        val previousComponent = creationState.enterComponentScope(renderedComponentId)
        try {
            if (modifier.hasDrawWithContent()) {
                remoteCanvas.internalCanvas.recordRenderingOp(WriterOp.StartCanvasOperations)
                modifier.drawWithContent(overriddenScope(creationState), remoteCanvas) {
                    remoteCanvas.internalCanvas.drawComponentContent()
                }
                remoteCanvas.internalCanvas.recordRenderingOp(WriterOp.EndCanvasOperations)
            }

            if (!reversed) {
                children.fastForEach { it.render(creationState, remoteCanvas) }
            } else {
                children.fastForEachReversed { it.render(creationState, remoteCanvas) }
            }
        } finally {
            creationState.restoreComponentScope(previousComponent)
        }
    }
}

internal class RemoteRootNode : RemoteComposeNode() {
    override fun render(creationState: RemoteComposeCreationState, remoteCanvas: RemoteCanvas) {
        remoteCanvas.internalCanvas.recordRenderingOp(WriterOp.StartRoot)
        renderChildren(creationState, remoteCanvas)
        remoteCanvas.internalCanvas.recordRenderingOp(WriterOp.EndRoot)
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
internal inline fun <T : RemoteComposeNode> RemoteComposeNode(
    noinline factory: () -> T,
    update: @DisallowComposableCalls Updater<T>.() -> Unit,
) {
    val remoteDensity = LocalRemoteDensity.current
    val layoutDirection = LocalRemoteLayoutDirection.current
    ComposeNode<T, RemoteComposeApplier>(factory) {
        update()
        set(remoteDensity) { this.remoteDensity = it }
        set(layoutDirection) { this.layoutDirection = it }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
internal inline fun <T : RemoteComposeNode> RemoteComposeNode(
    noinline factory: () -> T,
    update: @DisallowComposableCalls Updater<T>.() -> Unit,
    content: @Composable @RemoteComposable () -> Unit,
) {
    val remoteDensity = LocalRemoteDensity.current
    val layoutDirection = LocalRemoteLayoutDirection.current
    ComposeNode<T, RemoteComposeApplier>(
        factory,
        {
            update()
            set(remoteDensity) { this.remoteDensity = it }
            set(layoutDirection) { this.layoutDirection = it }
        },
        content,
    )
}

internal fun shouldReverse(
    horizontalArrangement: RemoteArrangement.Horizontal,
    layoutDirection: RemoteLayoutDirection,
): Boolean =
    if (layoutDirection == RemoteLayoutDirection.Rtl) {
        when (horizontalArrangement) {
            is HorizontalArrangement -> !horizontalArrangement.isAbsolute()

            is RemoteSpacedAbsoluteHorizontalArrangement,
            is RemoteFloatSpacedAbsoluteHorizontalArrangement -> false

            else -> true
        }
    } else {
        false
    }

internal class OverriddenScope(
    override val parentScope: RemoteStateScope,
    override val remoteDensity: RemoteDensity,
    override val remoteLayoutDirection: RemoteLayoutDirection,
) : PlatformRemoteStateScope
