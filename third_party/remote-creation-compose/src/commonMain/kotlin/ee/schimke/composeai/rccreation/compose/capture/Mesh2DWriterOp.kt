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

// LOCAL PATCH (rc-players): 2D vertex meshes (`AddMesh2D` + `DrawMesh2D`). The core format has them
// (experimental, alpha23) and rc-players' players draw them; the CMP port had no way to write one.

package ee.schimke.composeai.rccreation.compose.capture

import ee.schimke.composeai.rccreation.common.Mesh2D
import ee.schimke.composeai.rccreation.common.RemoteComposeWriter
import ee.schimke.composeai.rccreation.compose.state.MeshExpressionLowering
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteImageBitmap

/**
 * Writes one parametric mesh and draws it at once: `AddMesh2D` then `DrawMesh2D`, so the mesh id
 * the writer allocates never has to escape the recording.
 *
 * Each channel is lowered by [MeshExpressionLowering]: whatever reads `u` / `v` stays inline in the
 * channel — never hoisted into a float of its own, which the player would evaluate without them —
 * and the rest becomes references to ordinary floats.
 */
internal data class DrawMesh2DWriterOp(
    val layout: Int,
    val uCount: Int,
    val vCount: Int,
    /**
     * x, y, texU, texV, alpha, red, green, blue, in `AddMesh2D`'s group order; null when absent.
     */
    val channels: List<RemoteFloat?>,
    val image: RemoteImageBitmap?,
    val antialiasWidth: RemoteFloat?,
) : WriterOp {
    override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) {
        val lowering = MeshExpressionLowering(creationState)
        val arrays = channels.map { channel -> channel?.let { meshExpression(it, lowering) } }
        val meshId =
            writer.addMesh2D(
                layout = layout,
                uCount = uCount,
                vCount = vCount,
                x = arrays[0],
                y = arrays[1],
                texU = arrays[2],
                texV = arrays[3],
                alpha = arrays[4],
                red = arrays[5],
                green = arrays[6],
                blue = arrays[7],
                antialiasWidth = antialiasWidth?.getFloatIdForCreationState(creationState),
            )
        writer.drawMesh2D(
            meshId,
            imageId = image?.getIdForCreationState(creationState) ?: 0,
            blend = Mesh2D.BLEND_MODULATE,
        )
    }

    private fun meshExpression(value: RemoteFloat, lowering: MeshExpressionLowering): FloatArray {
        val array = lowering.lower(value)
        require(array.size <= Mesh2D.MAX_EXPRESSION_SIZE) {
            "A mesh channel compiles to ${array.size} expression words; AddMesh2D allows " +
                "${Mesh2D.MAX_EXPRESSION_SIZE}. Everything that does not read u or v is already a " +
                "reference, so the part that does has to be simpler."
        }
        return array
    }
}
