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
import androidx.compose.remote.creation.common.RemoteComposeWriter
import androidx.compose.remote.creation.compose.capture.RemoteComposeCreationState
import androidx.compose.remote.creation.compose.capture.RemoteDocumentScope
import androidx.compose.remote.creation.compose.state.RemoteComponentCacheKey
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteFloatExpression

/** Exposes component-relative [RemoteFloat] bounds for remote float expressions. */
internal class RemoteFloatContext
private constructor(
    internal val scope: RemoteDocumentScope?,
    internal val token: RemoteComponentToken?,
) {
    /**
     * Creates a [RemoteFloatContext] backed by [scope].
     *
     * @param scope scope providing the target [RemoteComposeWriter]
     */
    internal constructor(scope: RemoteDocumentScope) : this(scope = scope, token = null)

    /**
     * Creates a [RemoteFloatContext] bound to [token].
     *
     * @param token identity token of the target layout component
     */
    internal constructor(token: RemoteComponentToken?) : this(scope = null, token = token)

    internal val document: RemoteComposeWriter
        get() = requireDocument("unknown")

    private fun activeToken(): RemoteComponentToken? = token ?: scope?.resolveComponentToken()

    private fun requireDocument(type: String): RemoteComposeWriter =
        checkNotNull(scope) {
                "Component value '$type' requires an enclosing RemoteCompose layout component " +
                    "or RemoteStateScope."
            }
            .document

    // KMP (rc-players): the Kotlin writer has no `buffer.lastComponentId` while a render is being
    // recorded and takes a component's id from its caller, so without a token a value is written
    // for the last component the creation state recorded, when the expression is lowered.
    // TODO(rc-players): `RemoteStateScope` is `remote-creation-compose`'s; the module split has to
    // reach the creation state another way.
    private fun lastComponentId(type: String): Int {
        val scope =
            checkNotNull(scope) {
                "Component value '$type' requires an enclosing RemoteCompose layout component " +
                    "or RemoteStateScope."
            }
        val creationState =
            scope as? RemoteComposeCreationState
                ?: (scope as androidx.compose.remote.creation.compose.state.RemoteStateScope)
                    .parentScope as RemoteComposeCreationState
        return creationState.lastComponentId
    }

    private fun tokenComponentFloat(
        capturedToken: RemoteComponentToken,
        propIndex: Int,
        type: String,
    ): RemoteFloat {
        capturedToken.markRequested(propIndex)
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(capturedToken, type),
            arrayProvider = { creationState ->
                floatArrayOf(creationState.getComponentValue(capturedToken, propIndex))
            },
        )
    }

    /** Returns the width of the current component in pixels as a [RemoteFloat]. */
    internal fun componentWidth(): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            return tokenComponentFloat(capturedToken, RemoteComponentToken.PROP_WIDTH, "width")
        }
        // KMP (rc-players): see [lastComponentId].
        val componentId = lastComponentId("width")
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, "width"),
            arrayProvider = { creationState ->
                floatArrayOf(
                    creationState.componentValue(componentId, RemoteComponentToken.PROP_WIDTH),
                )
            },
        )
    }

    /** Returns the height of the current component in pixels as a [RemoteFloat]. */
    internal fun componentHeight(): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            return tokenComponentFloat(capturedToken, RemoteComponentToken.PROP_HEIGHT, "height")
        }
        // KMP (rc-players): see [lastComponentId].
        val componentId = lastComponentId("height")
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, "height"),
            arrayProvider = { creationState ->
                floatArrayOf(
                    creationState.componentValue(componentId, RemoteComponentToken.PROP_HEIGHT),
                )
            },
        )
    }

    /**
     * Returns the horizontal center (`width / 2`) of the current component in pixels.
     *
     * @return horizontal center offset as a [RemoteFloat]
     */
    internal fun componentCenterX(): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            capturedToken.markRequested(RemoteComponentToken.PROP_WIDTH)
            return RemoteFloatExpression(
                constantValueOrNull = null,
                cacheKey = RemoteComponentCacheKey(capturedToken, "centerX"),
                arrayProvider = { creationState ->
                    val componentWidthValue =
                        creationState.getComponentValue(
                            capturedToken,
                            RemoteComponentToken.PROP_WIDTH,
                        )
                    // LOCAL PATCH (rc-players): the port leaves the half inline in the expression
                    // that uses it, where upstream writes it as a float of its own. Kept so that
                    // the documents written do not change.
                    floatArrayOf(componentWidthValue, 2f, AnimatedFloatExpression.DIV)
                },
            )
        }
        // KMP (rc-players): see [lastComponentId]. The half is left inline, as above.
        val componentId = lastComponentId("centerX")
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, "centerX"),
            arrayProvider = { creationState ->
                floatArrayOf(
                    creationState.componentValue(componentId, RemoteComponentToken.PROP_WIDTH),
                    2f,
                    AnimatedFloatExpression.DIV,
                )
            },
        )
    }

    /**
     * Returns the vertical center (`height / 2`) of the current component in pixels.
     *
     * @return vertical center offset as a [RemoteFloat]
     */
    internal fun componentCenterY(): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            capturedToken.markRequested(RemoteComponentToken.PROP_HEIGHT)
            return RemoteFloatExpression(
                constantValueOrNull = null,
                cacheKey = RemoteComponentCacheKey(capturedToken, "centerY"),
                arrayProvider = { creationState ->
                    val componentHeightValue =
                        creationState.getComponentValue(
                            capturedToken,
                            RemoteComponentToken.PROP_HEIGHT,
                        )
                    // LOCAL PATCH (rc-players): the port leaves the half inline in the expression
                    // that uses it, where upstream writes it as a float of its own. Kept so that
                    // the documents written do not change.
                    floatArrayOf(componentHeightValue, 2f, AnimatedFloatExpression.DIV)
                },
            )
        }
        // KMP (rc-players): see [lastComponentId]. The half is left inline, as above.
        val componentId = lastComponentId("centerY")
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, "centerY"),
            arrayProvider = { creationState ->
                floatArrayOf(
                    creationState.componentValue(componentId, RemoteComponentToken.PROP_HEIGHT),
                    2f,
                    AnimatedFloatExpression.DIV,
                )
            },
        )
    }

    /** Returns the parent-relative X coordinate of the current component in pixels. */
    internal fun componentX(): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            return tokenComponentFloat(capturedToken, RemoteComponentToken.PROP_X, "x")
        }
        // KMP (rc-players): see [lastComponentId].
        val componentId = lastComponentId("x")
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, "x"),
            arrayProvider = { creationState ->
                floatArrayOf(creationState.componentValue(componentId, RemoteComponentToken.PROP_X))
            },
        )
    }

    /** Returns the parent-relative Y coordinate of the current component in pixels. */
    internal fun componentY(): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            return tokenComponentFloat(capturedToken, RemoteComponentToken.PROP_Y, "y")
        }
        // KMP (rc-players): see [lastComponentId].
        val componentId = lastComponentId("y")
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, "y"),
            arrayProvider = { creationState ->
                floatArrayOf(creationState.componentValue(componentId, RemoteComponentToken.PROP_Y))
            },
        )
    }

    /** Returns the root-relative X coordinate of the current component in pixels. */
    internal fun componentRootX(): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            return tokenComponentFloat(capturedToken, RemoteComponentToken.PROP_ROOT_X, "rootX")
        }
        // KMP (rc-players): see [lastComponentId].
        val componentId = lastComponentId("rootX")
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, "rootX"),
            arrayProvider = { creationState ->
                floatArrayOf(
                    creationState.componentValue(componentId, RemoteComponentToken.PROP_ROOT_X),
                )
            },
        )
    }

    /** Returns the root-relative Y coordinate of the current component in pixels. */
    internal fun componentRootY(): RemoteFloat {
        val capturedToken = activeToken()
        if (capturedToken != null) {
            return tokenComponentFloat(capturedToken, RemoteComponentToken.PROP_ROOT_Y, "rootY")
        }
        // KMP (rc-players): see [lastComponentId].
        val componentId = lastComponentId("rootY")
        return RemoteFloatExpression(
            constantValueOrNull = null,
            cacheKey = RemoteComponentCacheKey(componentId, "rootY"),
            arrayProvider = { creationState ->
                floatArrayOf(
                    creationState.componentValue(componentId, RemoteComponentToken.PROP_ROOT_Y),
                )
            },
        )
    }

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
