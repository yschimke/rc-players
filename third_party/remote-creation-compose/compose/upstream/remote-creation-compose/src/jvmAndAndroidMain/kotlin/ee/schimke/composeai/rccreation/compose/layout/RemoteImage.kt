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
@file:kotlin.jvm.JvmName("RemoteImageKt")

package ee.schimke.composeai.rccreation.compose.layout

import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.common.RemoteImageData
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import ee.schimke.composeai.rccreation.compose.capture.WriterOp
import ee.schimke.composeai.rccreation.compose.capture.addBitmap
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteImageBitmap
import ee.schimke.composeai.rccreation.compose.state.RemoteString
import ee.schimke.composeai.rccreation.compose.state.rf

internal class RemoteImageNode : RemoteComposeNode() {
    // LOCAL PATCH (rc-players): the writer's image is a component with no content section.
    override val writesContentSection: Boolean = false

    var image: Any? = null
    var remoteBitmap: RemoteImageBitmap? = null
    var contentScale: RemoteContentScale = RemoteContentScale.Fit
    var alpha: RemoteFloat = RemoteFloat(1f)
    var contentDescription: RemoteString? = null

    override fun render(creationState: RemoteComposeCreationState, remoteCanvas: RemoteCanvas) {
        val bitmapId =
            remoteBitmap?.getIdForCreationState(creationState)
                ?: (image as? androidx.compose.ui.graphics.ImageBitmap)?.let {
                    creationState.addBitmap(it)
                }
                ?: 0
        val scope = overriddenScope(remoteCanvas)
        // KMP (rc-players): written through a `WriterOp`, where upstream calls `document` here.
        remoteCanvas.writeLayout(
            WriterOp.Image(
                RemoteImageData(
                    resolveModifier(scope, creationState),
                    bitmapId,
                    contentScale.value,
                    alpha.getFloatIdForCreationState(creationState),
                ),
            ),
        )
    }
}

/**
 * Creates an image component that lays out and draws a given [RemoteImageBitmap].
 *
 * Example of displaying a cropped [RemoteImageBitmap]:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.RemoteImageSample
 *
 * Example of fitting a [RemoteImageBitmap] inside fixed bounds:
 *
 * @sample ee.schimke.composeai.rccreation.compose.samples.RemoteImageFitSample
 * @param bitmap [RemoteImageBitmap] to be drawn
 * @param contentDescription text used by accessibility services to describe what this image
 *   represents
 * @param modifier [RemoteModifier] to be applied to this layout node
 * @param contentScale rule to apply to scale the image when its size does not match layout size
 * @param alpha opacity to be applied to [bitmap] when rendered, defaulting to `1.0f`
 */
@Composable
@RemoteComposable
public fun RemoteImage(
    bitmap: RemoteImageBitmap,
    contentDescription: RemoteString?,
    modifier: RemoteModifier = RemoteModifier,
    contentScale: RemoteContentScale = RemoteContentScale.Fit,
    alpha: RemoteFloat = 1f.rf,
) {
    RemoteComposeNode(
        factory = ::RemoteImageNode,
        update = {
            set(modifier) { nodeModifier -> this.modifier = nodeModifier }
            set(bitmap) { this.remoteBitmap = it }
            set(contentScale) { this.contentScale = it }
            set(alpha) { this.alpha = it }
            set(contentDescription) { this.contentDescription = it }
        },
    )
}
