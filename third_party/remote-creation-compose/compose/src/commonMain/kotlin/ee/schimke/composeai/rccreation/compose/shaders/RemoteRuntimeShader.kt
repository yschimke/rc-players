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

// LOCAL PATCH (rc-players): AGSL runtime shaders as a RemoteBrush. The core format has
// `ShaderData` and the players run it; the CMP port had no way to write one.

package ee.schimke.composeai.rccreation.compose.shaders

import androidx.annotation.RestrictTo
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import ee.schimke.composeai.rccreation.common.PaintBundle
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope

/**
 * A brush that paints with an AGSL runtime shader, `half4 main(float2 position)`.
 *
 * Each of [floatUniforms] is a `float` to `float4x4` uniform of 1 to 16 values, and each value can
 * be any [RemoteFloat]: a constant, an expression, `RemoteTimeVariables.animationTime`, a
 * component's size. The player re-evaluates them every frame, so the shader animates without
 * recomposition. When [sizeUniform] is set, the shader also receives the size of the area it
 * paints, in pixels, as a `float2` of that name.
 *
 * Shaders need a player that runs AGSL: Android 13 (API 33) and later, or a Skia-backed player
 * (rc-players' Compose Multiplatform player). Elsewhere the draw falls back to the paint's colour,
 * so a design that needs a fallback should draw one underneath.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Stable
public fun RemoteBrush.Companion.runtimeShader(
    source: String,
    floatUniforms: Map<String, List<RemoteFloat>> = emptyMap(),
    intUniforms: Map<String, List<Int>> = emptyMap(),
    sizeUniform: String? = "size",
): RemoteShaderBrush = RemoteRuntimeShaderBrush(source, floatUniforms, intUniforms, sizeUniform)

/** A [RemoteBrush.Companion.runtimeShader] brush. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Immutable
public data class RemoteRuntimeShaderBrush(
    private val source: String,
    private val floatUniforms: Map<String, List<RemoteFloat>>,
    private val intUniforms: Map<String, List<Int>>,
    private val sizeUniform: String?,
) : RemoteShaderBrush() {
    override fun RemoteStateScope.createShader(size: RemoteSize): RemoteShader =
        RemoteRuntimeShader(
            source = source,
            floatUniforms =
                if (sizeUniform == null) floatUniforms
                else floatUniforms + (sizeUniform to listOf(size.width, size.height)),
            intUniforms = intUniforms,
        )
}

/** Writes `ShaderData` for [source] and its uniforms, and sets it as the paint's shader. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteRuntimeShader(
    public val source: String,
    public val floatUniforms: Map<String, List<RemoteFloat>>,
    public val intUniforms: Map<String, List<Int>>,
) : RemoteShader() {
    override fun apply(creationState: RemoteComposeCreationState, paintBundle: PaintBundle) {
        val writer = creationState.document
        val shaderId =
            writer.addShader(
                sourceId = writer.addText(source),
                floatUniforms =
                    floatUniforms.mapValues { (_, values) ->
                        FloatArray(values.size) {
                            values[it].getFloatIdForCreationState(creationState)
                        }
                    },
                intUniforms = intUniforms.mapValues { (_, values) -> values.toIntArray() },
            )
        paintBundle.setShader(shaderId)
    }
}
