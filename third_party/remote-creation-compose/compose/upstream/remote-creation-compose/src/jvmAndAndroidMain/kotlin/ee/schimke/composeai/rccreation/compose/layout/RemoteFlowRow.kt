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

import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import ee.schimke.composeai.rccreation.compose.capture.WriterOp
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier

internal class RemoteFlowRowNode : RemoteLayoutNode() {
    var horizontalArrangement: RemoteArrangement.Horizontal = RemoteArrangement.Start
    var verticalArrangement: RemoteArrangement.Vertical = RemoteArrangement.Top
    var maxItemsInEachRow: Int = Int.MAX_VALUE
    var maxLines: Int = Int.MAX_VALUE

    override fun render(creationState: RemoteComposeCreationState, remoteCanvas: RemoteCanvas) {
        val scope = overriddenScope(remoteCanvas)
        // KMP (rc-players): written through a `WriterOp`, where upstream calls `document` here.
        val remoteModifier = resolveModifier(scope, creationState)
        (horizontalArrangement as? RemoteSpaced)?.let {
            remoteModifier.spacedBy = it.getSpacingFloatId(scope)
        }
        remoteCanvas.writeLayout(
            WriterOp.StartFlow(
                remoteModifier,
                horizontalArrangement.toRemote(layoutDirection),
                verticalArrangement.toRemote(),
                maxItemsInEachRow,
                maxLines,
            ),
        )
        renderChildren(
            creationState,
            remoteCanvas,
            reversed = shouldReverse(horizontalArrangement, layoutDirection),
        )
        // KMP (rc-players): written through a `WriterOp`, where upstream calls `document` here.
        remoteCanvas.writeLayout(WriterOp.EndFlow)
    }
}

/**
 * A layout composable that places its children in a horizontal flow.
 *
 * `RemoteFlowRow` allows you to wrap multiple children and position them using
 * [horizontalArrangement] and [verticalArrangement].
 *
 * @param modifier The modifier to be applied to this flow.
 * @param horizontalArrangement The horizontal arrangement of the children.
 * @param verticalArrangement The vertical arrangement of the children.
 * @param maxItemsInEachRow The maximum number of items in each row.
 * @param maxLines The maximum number of lines in the flow.
 * @param content The content of the flow.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@RemoteComposable
@Composable
public fun RemoteFlowRow(
    modifier: RemoteModifier = RemoteModifier,
    horizontalArrangement: RemoteArrangement.Horizontal = RemoteArrangement.Start,
    verticalArrangement: RemoteArrangement.Vertical = RemoteArrangement.Top,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    maxLines: Int = Int.MAX_VALUE,
    content: @Composable () -> Unit,
) {
    RemoteLayoutNode(
        factory = ::RemoteFlowRowNode,
        update = {
            set(modifier) { nodeModifier -> this.modifier = nodeModifier }
            set(horizontalArrangement) { nodeHorizontalArrangement ->
                this.horizontalArrangement = nodeHorizontalArrangement
            }
            set(verticalArrangement) { nodeVerticalArrangement ->
                this.verticalArrangement = nodeVerticalArrangement
            }
            set(maxItemsInEachRow) { nodeMaxItemsInEachRow ->
                this.maxItemsInEachRow = nodeMaxItemsInEachRow
            }
            set(maxLines) { nodeMaxLines -> this.maxLines = nodeMaxLines }
        },
        content = content,
    )
}
