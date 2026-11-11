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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import ee.schimke.composeai.rccreation.common.DimensionModifierOperation.Type
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import ee.schimke.composeai.rccreation.compose.capture.WriterOp
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.WidthModifier
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat

/** Receiver scope used by [RemoteRow] for its content. */
@RemoteLayoutScopeMarker
@Immutable
public class RemoteRowScope {
    /**
     * Sets the horizontal weight of this element relative to its siblings in the [RemoteRow].
     *
     * Example of distributing width proportionally across children using [weight]:
     *
     * @sample ee.schimke.composeai.rccreation.compose.samples.RemoteRowWeightSample
     * @param weight The proportional width to allocate to this element.
     * @return a [RemoteModifier] that allocates width proportional to [weight]
     */
    public fun RemoteModifier.weight(weight: RemoteFloat): RemoteModifier =
        then(WidthModifier(Type.WEIGHT, weight))
}

internal class RemoteRowNode : RemoteLayoutNode() {
    var horizontalArrangement: RemoteArrangement.Horizontal = RemoteArrangement.Start
    var verticalAlignment: RemoteAlignment.Vertical = RemoteAlignment.Top

    override fun render(creationState: RemoteComposeCreationState, remoteCanvas: RemoteCanvas) {
        val scope = overriddenScope(remoteCanvas)
        // KMP (rc-players): recorded as a `WriterOp` and written when the canvas flushes, where
        // upstream writes to `document` here.
        val remoteModifier = resolveModifier(scope, creationState)
        (horizontalArrangement as? RemoteSpaced)?.let {
            remoteModifier.spacedBy = it.getSpacingFloatId(scope)
        }
        remoteCanvas.recordRenderingOp(
            WriterOp.StartRow(
                remoteModifier,
                horizontalArrangement.toRemote(layoutDirection),
                verticalAlignment.toRemote(),
            ),
        )
        renderChildren(
            creationState,
            remoteCanvas,
            reversed = shouldReverse(horizontalArrangement, layoutDirection),
        )
        // KMP (rc-players): recorded as a `WriterOp` and written when the canvas flushes, where
        // upstream writes to `document` here.
        remoteCanvas.recordRenderingOp(WriterOp.EndRow)
    }
}

/**
 * A layout composable that positions its children in a horizontal sequence.
 *
 * `RemoteRow` allows you to arrange children horizontally and control their [horizontalArrangement]
 * (spacing) and [verticalAlignment].
 *
 * Example of arranging children horizontally with spacing:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.RemoteRowSample
 *
 * Example of distributing horizontal space using [RemoteRowScope.weight]:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.RemoteRowWeightSample
 * @param modifier The modifier to be applied to this row.
 * @param horizontalArrangement The horizontal arrangement of the children.
 * @param verticalAlignment The vertical alignment of the children.
 * @param content The content of the row, which has access to [RemoteRowScope].
 */
@RemoteComposable
@Composable
public fun RemoteRow(
    modifier: RemoteModifier = RemoteModifier,
    horizontalArrangement: RemoteArrangement.Horizontal = RemoteArrangement.Start,
    verticalAlignment: RemoteAlignment.Vertical = RemoteAlignment.Top,
    content: @RemoteComposable @Composable RemoteRowScope.() -> Unit,
) {
    val scope = remember { RemoteRowScope() }
    RemoteLayoutNode(
        factory = ::RemoteRowNode,
        update = {
            set(modifier) { nodeModifier -> this.modifier = nodeModifier }
            set(horizontalArrangement) { nodeHorizontalArrangement ->
                this.horizontalArrangement = nodeHorizontalArrangement
            }
            set(verticalAlignment) { nodeVerticalAlignment ->
                this.verticalAlignment = nodeVerticalAlignment
            }
        },
        content = { scope.content() },
    )
}
