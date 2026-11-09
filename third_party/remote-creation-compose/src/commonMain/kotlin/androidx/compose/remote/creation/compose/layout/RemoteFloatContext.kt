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

import androidx.compose.remote.creation.common.AnimatedFloatExpression
import androidx.compose.remote.creation.compose.capture.RemoteComposeCreationContext
import androidx.compose.remote.creation.compose.capture.RemoteComposeCreationState
import androidx.compose.remote.creation.compose.state.RemoteComponentCacheKey
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteFloatExpression
import androidx.compose.remote.creation.compose.state.RemoteStateScope
import androidx.compose.remote.creation.compose.state.creationState

/**
 * Exposes component-relative [RemoteFloat] bounds for remote float expressions.
 *
 * A context bound to a [RemoteComponentToken] (or whose [scope] resolves one) refers to that
 * component, wherever its values are consumed. Without one it falls back, as upstream does, to the
 * last component recorded (`RemoteComposeCreationState.lastComponentId`, the port's record-time
 * counterpart of `RemoteComposeBuffer.lastComponentId`).
 */
internal class RemoteFloatContext
private constructor(
    internal val scope: RemoteStateScope?,
    internal val token: RemoteComponentToken?,
) {
    /**
     * Creates a [RemoteFloatContext] backed by [scope].
     *
     * @param scope scope providing the active component or the target document
     */
    internal constructor(scope: RemoteStateScope) : this(scope = scope, token = null)

    /**
     * Creates a [RemoteFloatContext] bound to [token].
     *
     * @param token identity token of the target layout component
     */
    internal constructor(token: RemoteComponentToken?) : this(scope = null, token = token)

    private fun activeToken(): RemoteComponentToken? = token ?: scope?.resolveComponentToken()

    private fun lastComponentId(type: String): Int =
        checkNotNull(scope) {
                "Component value '$type' requires an enclosing RemoteCompose layout component " +
                    "or RemoteStateScope."
            }
            .creationState
            .composeCreationState()
            .lastComponentId

    private fun componentFloat(propIndex: Int, type: String): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            return RemoteFloatExpression(
                constantValueOrNull = null,
                cacheKey = RemoteComponentCacheKey(capturedToken, type),
                arrayProvider = { creationState ->
                    floatArrayOf(creationState.getComponentValue(capturedToken, propIndex))
                },
            )
        }
        val componentId = lastComponentId(type)
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, type),
            arrayProvider = { creationState ->
                floatArrayOf(creationState.componentValue(componentId, propIndex))
            },
        )
    }

    private fun componentHalf(propIndex: Int, type: String): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            return RemoteFloatExpression(
                constantValueOrNull = null,
                cacheKey = RemoteComponentCacheKey(capturedToken, type),
                arrayProvider = { creationState ->
                    floatArrayOf(
                        creationState.getComponentValue(capturedToken, propIndex),
                        2f,
                        AnimatedFloatExpression.DIV,
                    )
                },
            )
        }
        val componentId = lastComponentId(type)
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, type),
            arrayProvider = { creationState ->
                floatArrayOf(
                    creationState.componentValue(componentId, propIndex),
                    2f,
                    AnimatedFloatExpression.DIV,
                )
            },
        )
    }

    /** Returns the width of the current component in pixels as a [RemoteFloat]. */
    internal fun componentWidth(): RemoteFloat =
        componentFloat(RemoteComponentToken.PROP_WIDTH, "width")

    /** Returns the height of the current component in pixels as a [RemoteFloat]. */
    internal fun componentHeight(): RemoteFloat =
        componentFloat(RemoteComponentToken.PROP_HEIGHT, "height")

    /**
     * Returns the horizontal center (`width / 2`) of the current component in pixels.
     *
     * @return horizontal center offset as a [RemoteFloat]
     */
    internal fun componentCenterX(): RemoteFloat =
        componentHalf(RemoteComponentToken.PROP_WIDTH, "centerX")

    /**
     * Returns the vertical center (`height / 2`) of the current component in pixels.
     *
     * @return vertical center offset as a [RemoteFloat]
     */
    internal fun componentCenterY(): RemoteFloat =
        componentHalf(RemoteComponentToken.PROP_HEIGHT, "centerY")

    /** Returns the parent-relative X coordinate of the current component in pixels. */
    internal fun componentX(): RemoteFloat = componentFloat(RemoteComponentToken.PROP_X, "x")

    /** Returns the parent-relative Y coordinate of the current component in pixels. */
    internal fun componentY(): RemoteFloat = componentFloat(RemoteComponentToken.PROP_Y, "y")

    /** Returns the root-relative X coordinate of the current component in pixels. */
    internal fun componentRootX(): RemoteFloat =
        componentFloat(RemoteComponentToken.PROP_ROOT_X, "rootX")

    /** Returns the root-relative Y coordinate of the current component in pixels. */
    internal fun componentRootY(): RemoteFloat =
        componentFloat(RemoteComponentToken.PROP_ROOT_Y, "rootY")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteFloatContext) return false
        return scope == other.scope && token == other.token
    }

    override fun hashCode(): Int {
        var result = scope?.hashCode() ?: 0
        result = 31 * result + (token?.hashCode() ?: 0)
        return result
    }
}

/**
 * The capture's [RemoteComposeCreationState], also behind a context that wraps it (a mesh channel's
 * lowering context, whose parent scope is the capture's).
 */
private fun RemoteStateScope.composeCreationState(): RemoteComposeCreationState =
    this as? RemoteComposeCreationState ?: parentScope as RemoteComposeCreationState

private fun RemoteComposeCreationContext.getComponentValue(
    token: RemoteComponentToken,
    propIndex: Int,
): Float = composeCreationState().getComponentValue(token, propIndex)

private fun RemoteComposeCreationContext.componentValue(componentId: Int, propIndex: Int): Float =
    composeCreationState().componentValue(componentId, propIndex)
