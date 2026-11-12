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

package ee.schimke.composeai.rccreation.compose.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Updater
import androidx.compose.runtime.remember
import ee.schimke.composeai.rccreation.common.RemoteModifierData
import ee.schimke.composeai.rccreation.compose.capture.LocalRemoteDensity
import ee.schimke.composeai.rccreation.compose.capture.LocalRemoteLayoutDirection
import ee.schimke.composeai.rccreation.compose.capture.RemoteComponentTokenProvider
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensity
import ee.schimke.composeai.rccreation.compose.capture.WriterOp
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.drawWithContent
import ee.schimke.composeai.rccreation.compose.modifier.hasDrawWithContent
import ee.schimke.composeai.rccreation.compose.modifier.requestedComponentId
import ee.schimke.composeai.rccreation.compose.modifier.toRemoteModifierData
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope
import ee.schimke.composeai.rccreation.compose.util.fastForEach
import ee.schimke.composeai.rccreation.compose.util.fastForEachReversed

internal abstract class RemoteComposeNode : RemoteComponentTokenProvider {
    val children = mutableListOf<RemoteComposeNode>()
    var modifier: RemoteModifier = RemoteModifier
    var remoteDensity: RemoteDensity = RemoteDensity.Host
    var componentToken: RemoteComponentToken? = null
    private var renderedComponentId: Int = 0

    // LOCAL PATCH (rc-players): whether the writer follows this component with a content section
    // (`LayoutContent`); every layout does, an image does not.
    protected open val writesContentSection: Boolean = true

    internal open val supportsComponentToken: Boolean
        get() = true

    override fun getOrCreateComponentToken(): RemoteComponentToken? =
        if (supportsComponentToken) {
            componentToken ?: RemoteComponentToken().also { componentToken = it }
        } else {
            null
        }

    // KMP (rc-players): as upstream's `attachComponentTokenFlush`, on the Kotlin writer's modifier
    // data: once the component's modifiers are written, the `ComponentValue`s already asked of it
    // are written, in the component.
    private fun attachComponentTokenFlush(
        creationState: RemoteComposeCreationState,
        data: RemoteModifierData,
    ): RemoteModifierData {
        if (!supportsComponentToken || (modifier === RemoteModifier && componentToken == null)) {
            return data
        }
        val componentId = renderedComponentId
        data.afterOperations = {
            componentToken?.let { token ->
                creationState.beginComponentContent(token, componentId)
                creationState.endComponentContent(token)
            }
        }
        return data
    }

    fun overriddenScope(scope: RemoteStateScope): RemoteStateScope {
        return OverriddenScope(
            parentScope = scope,
            remoteDensity = remoteDensity,
            layoutDirection = scope.layoutDirection,
            node = this,
        )
    }

    abstract fun render(creationState: RemoteComposeCreationState, remoteCanvas: RemoteCanvas)

    fun resolveModifier(
        scope: RemoteStateScope,
        creationState: RemoteComposeCreationState,
    ): RemoteModifierData {
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
        val data = scope.toRemoteModifierData(modifier)
        data.componentId = renderedComponentId
        data.contentComponentId = contentId
        return attachComponentTokenFlush(creationState, data)
    }

    // KMP (rc-players): the id this node's component has in this render, for
    // `beginComponentContent`, where upstream's reads the writer's `lastComponentId`.
    internal val componentIdForToken: Int
        get() = renderedComponentId

    fun renderChildren(
        creationState: RemoteComposeCreationState,
        remoteCanvas: RemoteCanvas,
        reversed: Boolean = false,
    ) {
        val previousNode = creationState.currentRenderingNode
        if (supportsComponentToken) {
            creationState.currentRenderingNode = this
        }
        componentToken?.let { creationState.beginComponentContent(it, renderedComponentId) }
        try {
            if (modifier.hasDrawWithContent()) {
                creationState.withCanvasOperationsScope {
                    // KMP (rc-players): written through a `WriterOp`, where upstream calls
                    // `document` here.
                    remoteCanvas.writeLayout(WriterOp.StartCanvasOperations)
                    modifier.drawWithContent(overriddenScope(remoteCanvas), remoteCanvas) {
                        remoteCanvas.drawComponentContent()
                    }
                    componentToken?.let {
                        creationState.beginComponentContent(it, renderedComponentId)
                        creationState.disableDirectComponentEmission(it)
                    }
                    remoteCanvas.flush()
                    remoteCanvas.writeLayout(WriterOp.EndCanvasOperations)
                }
            }

            componentToken?.let { creationState.disableDirectComponentEmission(it) }

            if (!reversed) {
                children.fastForEach { it.render(creationState, remoteCanvas) }
            } else {
                children.fastForEachReversed { it.render(creationState, remoteCanvas) }
            }
        } finally {
            componentToken?.let { creationState.endComponentContent(it) }
            creationState.currentRenderingNode = previousNode
        }
    }
}

internal class RemoteRootNode : RemoteComposeNode() {
    override val supportsComponentToken: Boolean
        get() = false

    override fun render(creationState: RemoteComposeCreationState, remoteCanvas: RemoteCanvas) {
        // LOCAL PATCH (rc-players): what a render requests of a component (`markRequested`) is
        // forgotten when it ends, so that a retained composition's next render writes what its
        // first did: upstream's tokens keep it, and a later render writes those values earlier in
        // their component. What composition requested is kept.
        val requested = HashMap<RemoteComponentToken, Int>()
        forEachNode { node -> node.componentToken?.let { requested[it] = it.saveRequested() } }
        try {
            // KMP (rc-players): written through a `WriterOp`, where upstream calls `document`
            // here.
            remoteCanvas.writeLayout(WriterOp.StartRoot)
            renderChildren(creationState, remoteCanvas)
            remoteCanvas.writeLayout(WriterOp.EndRoot)
            // LOCAL PATCH (rc-players): after the root, the sizes asked of a component after it
            // was written; see `RemoteComposeCreationState.getComponentValue`.
            creationState.writeLateComponentValues()
        } finally {
            forEachNode { node ->
                node.componentToken?.let { it.restoreRequested(requested[it] ?: 0) }
            }
        }
    }

    private fun forEachNode(action: (RemoteComposeNode) -> Unit) {
        val pending = ArrayDeque<RemoteComposeNode>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            action(node)
            pending.addAll(node.children)
        }
    }
}

internal abstract class RemoteLayoutNode : RemoteComposeNode() {
    var layoutDirection: RemoteLayoutDirection = RemoteLayoutDirection.Ltr
}

@Composable
internal inline fun <T : RemoteComposeNode> RemoteComposeNode(
    noinline factory: () -> T,
    update: @DisallowComposableCalls Updater<T>.() -> Unit,
) {
    val remoteDensity = LocalRemoteDensity.current
    ComposeNode<T, RemoteComposeApplier>(factory) {
        update()
        set(remoteDensity) { this.remoteDensity = it }
    }
}

@Composable
internal inline fun <T : RemoteComposeNode> RemoteComposeNode(
    noinline factory: () -> T,
    update: @DisallowComposableCalls Updater<T>.() -> Unit,
    noinline content: @Composable @RemoteComposable () -> Unit,
) {
    val remoteDensity = LocalRemoteDensity.current
    val componentToken = remember { RemoteComponentToken() }
    ComposeNode<T, RemoteComposeApplier>(
        factory,
        {
            update()
            set(remoteDensity) { this.remoteDensity = it }
            set(componentToken) { this.componentToken = it }
        },
    ) {
        CompositionLocalProvider(
            LocalRemoteComponentToken provides componentToken,
            content = content,
        )
    }
}

@Composable
internal inline fun <T : RemoteLayoutNode> RemoteLayoutNode(
    noinline factory: () -> T,
    update: @DisallowComposableCalls Updater<T>.() -> Unit,
    noinline content: @Composable @RemoteComposable () -> Unit,
) {
    val layoutDirection = LocalRemoteLayoutDirection.current
    RemoteComposeNode(
        factory = factory,
        update = {
            update()
            set(layoutDirection) { this.layoutDirection = it }
        },
        content = content,
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
    override val layoutDirection: RemoteLayoutDirection,
    val node: RemoteComposeNode,
) : RemoteStateScope {
    override fun resolveComponentToken(): RemoteComponentToken? = node.getOrCreateComponentToken()
}
