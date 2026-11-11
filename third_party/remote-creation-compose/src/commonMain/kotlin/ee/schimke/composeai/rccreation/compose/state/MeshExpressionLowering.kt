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

// LOCAL PATCH (rc-players): lowering for 2D mesh channel expressions; see `RemoteMesh2D.kt`.

package ee.schimke.composeai.rccreation.compose.state

import ee.schimke.composeai.rccreation.common.AnimatedFloatExpression
import ee.schimke.composeai.rccreation.common.Utils
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState

/**
 * How a mesh channel is lowered.
 *
 * A mesh channel reads `u` / `v` (`VAR1` / `VAR2` / `VAR3`), which only exist while the player
 * expands the mesh. The ordinary lowering is free to replace any operand by a reference to a float
 * of its own — when the inline array would grow past its safe size, or when the operand was already
 * written — and a float written to the document is evaluated by the player with no `u` / `v` at
 * all. While this is the creation state's `meshExpressionLowering`, [combineToFloatArray] keeps
 * every operand that reads them inline, while an operand that does not (the clock, a size, a phase)
 * becomes a reference: it is evaluated once a frame rather than once a vertex, and keeps the
 * channel short. Arrays are never cached meanwhile, so a shared sub-expression lowered elsewhere
 * cannot leak a reference in.
 */
@PublishedApi
internal class MeshExpressionLowering(val base: RemoteComposeCreationState) {
    /** Runs [block] with this as the creation state's mesh lowering, or with none for null. */
    private inline fun <T> RemoteComposeCreationState.lowering(
        lowering: MeshExpressionLowering?,
        block: () -> T,
    ): T {
        val previous = meshExpressionLowering
        meshExpressionLowering = lowering
        try {
            return block()
        } finally {
            meshExpressionLowering = previous
        }
    }

    fun lower(value: RemoteFloat): FloatArray {
        value.constantValueOrNull?.let {
            return floatArrayOf(it)
        }
        val array = base.lowering(this) { value.arrayForCreationState(base) }
        if (array.size == 1 || array.readsMeshVariables()) return array
        return floatArrayOf(base.lowering(null) { value.getFloatIdForCreationState(base) })
    }

    fun combine(remoteFloats: Array<RemoteFloat>, extras: FloatArray): FloatArray {
        val parts = remoteFloats.map(::lower)
        val combined = FloatArray(parts.sumOf { it.size } + extras.size)
        var index = 0
        for (part in parts) {
            part.copyInto(combined, index)
            index += part.size
        }
        extras.copyInto(combined, index)
        return combined
    }
}

private val meshVariableIds =
    setOf(
        Utils.idFromNan(AnimatedFloatExpression.VAR1),
        Utils.idFromNan(AnimatedFloatExpression.VAR2),
        Utils.idFromNan(AnimatedFloatExpression.VAR3),
    )

/** Whether this RPN reads one of the mesh's domain variables. */
internal fun FloatArray.readsMeshVariables(): Boolean = any {
    it.isNaN() && Utils.idFromNan(it) in meshVariableIds
}
