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
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import ee.schimke.composeai.rccreation.compose.capture.WriterOp
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.drawWithContent

internal class RemoteCanvasNode : RemoteComposeNode() {
    var onDraw: (RemoteDrawScope.() -> Unit) = {}

    override fun render(creationState: RemoteComposeCreationState, remoteCanvas: RemoteCanvas) {
        val scope = overriddenScope(remoteCanvas)
        // KMP (rc-players): written through a `WriterOp`, where upstream calls `document` here.
        val remoteModifier = resolveModifier(scope, creationState)
        remoteCanvas.writeLayout(WriterOp.StartCanvas(remoteModifier))
        // KMP (rc-players): the node's own id, where upstream reads the writer's `lastComponentId`.
        val canvasComponentId = componentIdForToken

        val previousNode = creationState.currentRenderingNode
        creationState.currentRenderingNode = this
        componentToken?.let { creationState.beginComponentContent(it, canvasComponentId) }
        try {
            creationState.withCanvasOperationsScope {
                if (!modifier.drawWithContent(scope, remoteCanvas, onDraw)) {
                    val drawScope = RemoteDrawScope(remoteCanvas)
                    drawScope.onDraw()
                }

                componentToken?.let {
                    creationState.beginComponentContent(it, canvasComponentId)
                    creationState.disableDirectComponentEmission(it)
                }
                remoteCanvas.flush()
            }
        } finally {
            componentToken?.let { creationState.endComponentContent(it) }
            creationState.currentRenderingNode = previousNode
        }

        // KMP (rc-players): written through a `WriterOp`, where upstream calls `document` here.
        remoteCanvas.writeLayout(WriterOp.EndCanvas)
    }
}

/**
 * A Composable that provides a [RemoteDrawScope] for drawing operations in RemoteCompose.
 *
 * Basic example of drawing shapes on a [RemoteCanvas]:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.RemoteCanvasSample
 *
 * Example of animating canvas drawing with continuous remote time variables:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.RemoteCanvasAnimationSample
 * @param modifier The [RemoteModifier] to apply to this layout.
 * @param onDraw The drawing commands to be executed on the remote canvas via [RemoteDrawScope].
 */
@RemoteComposable
@Composable
public fun RemoteCanvas(
    modifier: RemoteModifier = RemoteModifier,
    onDraw: RemoteDrawScope.() -> Unit,
) {
    RemoteComposeNode(
        factory = ::RemoteCanvasNode,
        update = {
            set(modifier) { this.modifier = it }
            set(onDraw) { this.onDraw = it }
        },
    )
}
