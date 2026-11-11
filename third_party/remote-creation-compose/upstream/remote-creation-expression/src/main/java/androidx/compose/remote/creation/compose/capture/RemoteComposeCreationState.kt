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

package androidx.compose.remote.creation.compose.capture

import androidx.annotation.RestrictTo
import androidx.collection.MutableObjectIntMap
import androidx.compose.remote.creation.common.RemoteComposeWriter
import androidx.compose.remote.creation.common.Utils
import androidx.compose.remote.creation.compose.layout.RemoteComponentToken
import androidx.compose.remote.creation.compose.state.AnimatedRemoteFloat
import androidx.compose.remote.creation.compose.state.BaseRemoteStateCacheKey
import androidx.compose.remote.creation.compose.state.MeshExpressionLowering
import androidx.compose.remote.creation.compose.state.RemoteFloatExpression
import androidx.compose.remote.creation.compose.state.RemoteIntExpression
import androidx.compose.remote.creation.compose.state.RemoteOperationCacheKey
import androidx.compose.remote.creation.compose.state.RemoteStateCacheKey
import androidx.compose.remote.creation.compose.state.nanIdContentEquals
import androidx.compose.remote.creation.compose.state.nanIdContentHashCode

/** Provider for resolving a layout component's [RemoteComponentToken] during rendering. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun interface RemoteComponentTokenProvider {
    /** Returns the [RemoteComponentToken] for the active layout component, or `null` if none. */
    public fun getOrCreateComponentToken(): RemoteComponentToken?
}

/** Scope providing access to a [RemoteComposeWriter] document. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface RemoteDocumentScope {
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public val document: RemoteComposeWriter

    /** Resolves the active [RemoteComponentToken] for this scope, or `null` if none is bound. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun resolveComponentToken(): RemoteComponentToken? = null
}

/** Base state for remote creation, holding expression caches and variable ID mappings. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class RemoteComposeCreationState : RemoteDocumentScope {
    public override lateinit var document: RemoteComposeWriter
    public open lateinit var remoteDensity: RemoteDensity

    /**
     * Ids of float expressions already written to [document], keyed by their lowered RPN array and
     * optional animation. Used to write each distinct expression only once.
     */
    internal val floatExpressionIds: MutableObjectIntMap<LoweredFloatExpressionKey> =
        MutableObjectIntMap()

    /**
     * Ids of integer expressions already written to [document], keyed by their lowered RPN array.
     * Used to write each distinct expression only once.
     */
    internal val intExpressionIds: MutableObjectIntMap<LoweredIntExpressionKey> =
        MutableObjectIntMap()
    public var ready: Boolean = true
    public val remoteVariableToId: MutableObjectIntMap<RemoteStateCacheKey> = MutableObjectIntMap()
    public val floatArrayCache: HashMap<RemoteStateCacheKey, FloatArray> = HashMap()
    public val longArrayCache: HashMap<RemoteStateCacheKey, LongArray> = HashMap()

    // KMP (rc-players): the Kotlin writer writes a `ComponentValue` as a declaration that names its
    // component's id, where `RemoteComposeWriter` writes it inside the component's body. So this
    // state holds the id a token's component was given in this render, and no emitted mask.
    /**
     * Per-render state of a [RemoteComponentToken]: the component id its node was given in this
     * render, and the float variables reserved for values requested before that id was known.
     */
    private class TokenComponentState {
        val variableIds: IntArray = IntArray(RemoteComponentToken.PROP_COUNT) { NO_VARIABLE_ID }
        var componentId: Int = NO_COMPONENT_ID

        companion object {
            const val NO_VARIABLE_ID: Int = -1
            const val NO_COMPONENT_ID: Int = 0
        }
    }

    private val tokenComponentStates: HashMap<RemoteComponentToken, TokenComponentState> = HashMap()

    /**
     * The [RemoteComponentTokenProvider] currently rendering its content or canvas commands, used
     * to resolve component tokens from scopes that are not tied to a specific modifier node.
     */
    public var currentRenderingNode: RemoteComponentTokenProvider? = null

    public override fun resolveComponentToken(): RemoteComponentToken? =
        currentRenderingNode?.getOrCreateComponentToken()

    // KMP (rc-players): as above, a token's values are written for its component's explicit id.
    // A value requested before the component is recorded reserves a variable that
    // [beginComponentContent] binds; nothing is deferred to the end of the component, so there is
    // no `disableDirectComponentEmission` or `endComponentContent`.
    /**
     * Marks [token]'s component as recorded with [componentId] in this render, and binds any value
     * of it requested before that to the component.
     */
    public fun beginComponentContent(token: RemoteComponentToken, componentId: Int) {
        val state = tokenComponentStates.getOrPut(token) { TokenComponentState() }
        state.componentId = componentId
        for (propIndex in 0 until RemoteComponentToken.PROP_COUNT) {
            val reservedId = state.variableIds[propIndex]
            if (reservedId != TokenComponentState.NO_VARIABLE_ID) {
                document.addComponentValue(
                    RemoteComponentToken.toComponentValueType(propIndex),
                    componentId,
                    Utils.asNan(reservedId),
                )
            }
        }
    }

    /**
     * Returns the NaN-encoded float variable holding [propIndex] (`RemoteComponentToken.PROP_*`) of
     * the component identified by [token]: written for its component id once it is recorded in this
     * render, otherwise reserved for [beginComponentContent] to bind.
     */
    internal fun getComponentValue(token: RemoteComponentToken, propIndex: Int): Float {
        val state = tokenComponentStates.getOrPut(token) { TokenComponentState() }
        if (state.componentId != TokenComponentState.NO_COMPONENT_ID) {
            return componentValue(state.componentId, propIndex)
        }
        val existingId = state.variableIds[propIndex]
        if (existingId != TokenComponentState.NO_VARIABLE_ID) {
            return Utils.asNan(existingId)
        }
        val reserved = document.reserveFloatVariable()
        state.variableIds[propIndex] = Utils.idFromNan(reserved)
        return reserved
    }

    /** Writes (once) the `ComponentValue` [propIndex] of [componentId] and returns its variable. */
    internal fun componentValue(componentId: Int, propIndex: Int): Float =
        when (propIndex) {
            RemoteComponentToken.PROP_WIDTH -> document.addComponentWidthValue(componentId)
            RemoteComponentToken.PROP_HEIGHT -> document.addComponentHeightValue(componentId)
            RemoteComponentToken.PROP_X -> document.addComponentXValue(componentId)
            RemoteComponentToken.PROP_Y -> document.addComponentYValue(componentId)
            RemoteComponentToken.PROP_ROOT_X -> document.addComponentRootXValue(componentId)
            RemoteComponentToken.PROP_ROOT_Y -> document.addComponentRootYValue(componentId)
            else -> throw IllegalArgumentException("Unknown property index: $propIndex")
        }

    // KMP (rc-players): the Kotlin writer takes a component's id from its caller and has no
    // `buffer.lastComponentId` to read while a render is being recorded, so the ids are allocated
    // and the last one recorded is tracked here.
    private var nextComponentId = -1_000_000

    /**
     * The last component (a layout's content section) recorded so far, in document order: the
     * component a size expression bound to no component token refers to, as
     * `RemoteComposeBuffer.lastComponentId` is upstream.
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @set:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public var lastComponentId: Int = -1

    /** Allocates the id of the next component recorded in this render. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun allocateComponentId(): Int = nextComponentId--

    // LOCAL PATCH (rc-players): explicit ids already used in this render; see ComponentIdModifier.
    private val claimedComponentIds = HashSet<Int>()

    /** Claims [id] for one component of this render; false if another already has it. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun claimComponentId(id: Int): Boolean = claimedComponentIds.add(id)

    /**
     * LOCAL PATCH (rc-players): restarts component ids, so re-rendering the same retained tree
     * gives the same ids. Without it every render of a live composition shifts all of them, and
     * nothing keyed on a component id (editor bounds, player state) survives an edit.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun resetComponentIds() {
        nextComponentId = -1_000_000
        lastComponentId = -1
        claimedComponentIds.clear()
        tokenComponentStates.clear()
        currentRenderingNode = null
    }

    // LOCAL PATCH (rc-players): set while a 2D mesh channel is lowered, which keeps what reads
    // `u`/`v` inline and bypasses the array cache; see `MeshExpressionLowering.kt`.
    @PublishedApi internal var meshExpressionLowering: MeshExpressionLowering? = null

    private class CanvasOperationsScopeFrame {
        val floatExpressionKeys: ArrayList<LoweredFloatExpressionKey> = ArrayList()
        val intExpressionKeys: ArrayList<LoweredIntExpressionKey> = ArrayList()
        val variableKeys: ArrayList<RemoteStateCacheKey> = ArrayList()
        val floatArrayKeys: ArrayList<RemoteStateCacheKey> = ArrayList()
        val longArrayKeys: ArrayList<RemoteStateCacheKey> = ArrayList()
    }

    private val canvasOperationsScopes: ArrayList<CanvasOperationsScopeFrame> = ArrayList()

    /**
     * Enters a paint-scoped canvas block (`CanvasOperations` or `CanvasContent`).
     *
     * Expressions emitted into [document] while a canvas block is active live inside that canvas
     * container's operation list and are only re-evaluated during `paint()`, not during
     * `measure()`. Tracking and evicting newly cached computed expressions when the block exits
     * preserves common subexpression elimination within the canvas block while preventing
     * subsequent sibling or child layout/modifier expressions from referencing paint-only
     * expression IDs.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun enterCanvasOperationsScope() {
        canvasOperationsScopes.add(CanvasOperationsScopeFrame())
    }

    /** Exits the current paint-scoped canvas block and evicts computed expressions added in it. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun exitCanvasOperationsScope() {
        val frame = canvasOperationsScopes.removeAt(canvasOperationsScopes.lastIndex)
        for (i in 0 until frame.floatExpressionKeys.size) {
            floatExpressionIds.remove(frame.floatExpressionKeys[i])
        }
        for (i in 0 until frame.intExpressionKeys.size) {
            intExpressionIds.remove(frame.intExpressionKeys[i])
        }
        for (i in 0 until frame.variableKeys.size) {
            remoteVariableToId.remove(frame.variableKeys[i])
        }
        for (i in 0 until frame.floatArrayKeys.size) {
            floatArrayCache.remove(frame.floatArrayKeys[i])
        }
        for (i in 0 until frame.longArrayKeys.size) {
            longArrayCache.remove(frame.longArrayKeys[i])
        }
    }

    /**
     * Runs [block] inside a paint-scoped canvas expression cache frame.
     *
     * @see enterCanvasOperationsScope
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public inline fun <T> withCanvasOperationsScope(block: () -> T): T {
        enterCanvasOperationsScope()
        try {
            return block()
        } finally {
            exitCanvasOperationsScope()
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun recordScopedFloatArrayKey(key: RemoteStateCacheKey) {
        if (canvasOperationsScopes.isNotEmpty()) {
            canvasOperationsScopes[canvasOperationsScopes.lastIndex].floatArrayKeys.add(key)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun recordScopedLongArrayKey(key: RemoteStateCacheKey) {
        if (canvasOperationsScopes.isNotEmpty()) {
            canvasOperationsScopes[canvasOperationsScopes.lastIndex].longArrayKeys.add(key)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun recordScopedVariableKey(key: RemoteStateCacheKey) {
        if (canvasOperationsScopes.isNotEmpty() && key.isCanvasScopedExpressionKey()) {
            canvasOperationsScopes[canvasOperationsScopes.lastIndex].variableKeys.add(key)
        }
    }

    internal fun recordScopedFloatExpressionKey(key: LoweredFloatExpressionKey) {
        if (canvasOperationsScopes.isNotEmpty()) {
            canvasOperationsScopes[canvasOperationsScopes.lastIndex].floatExpressionKeys.add(key)
        }
    }

    internal fun recordScopedIntExpressionKey(key: LoweredIntExpressionKey) {
        if (canvasOperationsScopes.isNotEmpty()) {
            canvasOperationsScopes[canvasOperationsScopes.lastIndex].intExpressionKeys.add(key)
        }
    }

    /**
     * Returns the cached [FloatArray] for [key], or computes and caches it using [compute].
     *
     * @param key cache key identifying the float expression
     * @param compute provider invoked on cache miss
     */
    public inline fun getOrPutFloatArray(
        key: RemoteStateCacheKey,
        crossinline compute: () -> FloatArray,
    ): FloatArray {
        // LOCAL PATCH (rc-players): a 2D mesh channel's arrays are never cached.
        if (meshExpressionLowering != null) return compute()
        floatArrayCache[key]?.let {
            return it
        }
        val computed = compute()
        floatArrayCache[key] = computed
        recordScopedFloatArrayKey(key)
        return computed
    }

    /**
     * Returns the cached [LongArray] for [key], or computes and caches it using [compute].
     *
     * @param key cache key identifying the integer expression
     * @param compute provider invoked on cache miss
     */
    public inline fun getOrPutLongArray(
        key: RemoteStateCacheKey,
        crossinline compute: () -> LongArray,
    ): LongArray {
        longArrayCache[key]?.let {
            return it
        }
        val computed = compute()
        longArrayCache[key] = computed
        recordScopedLongArrayKey(key)
        return computed
    }

    /**
     * Returns the id of a float expression with the same lowered [array] and [animation] that was
     * already written to [document], or calls [write] to write it and caches the resulting id.
     */
    internal inline fun getOrPutFloatExpressionId(
        array: FloatArray,
        animation: FloatArray?,
        crossinline write: () -> Int,
    ): Int {
        val key = LoweredFloatExpressionKey(array, animation)
        val existing = floatExpressionIds.getOrDefault(key, -1)
        if (existing != -1) {
            return existing
        }
        val id = write()
        floatExpressionIds.put(key, id)
        recordScopedFloatExpressionKey(key)
        return id
    }

    /**
     * Returns the id of an integer expression with the same lowered [array] that was already
     * written to [document], or calls [write] to write it and caches the resulting id.
     */
    internal inline fun getOrPutIntExpressionId(
        array: LongArray,
        crossinline write: () -> Int,
    ): Int {
        val key = LoweredIntExpressionKey(array)
        val existing = intExpressionIds.getOrDefault(key, -1)
        if (existing != -1) {
            return existing
        }
        val id = write()
        intExpressionIds.put(key, id)
        recordScopedIntExpressionKey(key)
        return id
    }

    /** Clears all caches tied to the current [document]. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun clearDocumentCaches() {
        floatExpressionIds.clear()
        intExpressionIds.clear()
        remoteVariableToId.clear()
        floatArrayCache.clear()
        longArrayCache.clear()
        tokenComponentStates.clear()
        canvasOperationsScopes.clear()
        currentRenderingNode = null
    }

    /**
     * Returns the variable ID cached for [key], or allocates and caches one using [compute].
     *
     * @param key cache key identifying the remote state
     * @param compute allocator invoked on cache miss
     */
    public inline fun getOrPutVariableId(
        key: RemoteStateCacheKey,
        crossinline compute: () -> Int,
    ): Int {
        val id = remoteVariableToId.getOrDefault(key, -1)
        if (id == -1) {
            val nextId = compute()
            remoteVariableToId.put(key, nextId)
            recordScopedVariableKey(key)
            return nextId
        }
        return id
    }
}

private fun RemoteStateCacheKey.isCanvasScopedExpressionKey(): Boolean {
    if (this is RemoteOperationCacheKey) return true
    val state = (this as? BaseRemoteStateCacheKey)?.getState() ?: return false
    return state is RemoteFloatExpression ||
        state is AnimatedRemoteFloat ||
        state is RemoteIntExpression
}

/**
 * Key for a lowered float expression: its RPN [array] plus optional [animation].
 *
 * The arrays contain NaN-encoded variable ids and operators, so equality uses [nanIdContentEquals];
 * [FloatArray.contentEquals] would treat e.g. `[a, b, ADD]` and `[c, d, MUL]` as equal.
 */
internal class LoweredFloatExpressionKey(
    private val array: FloatArray,
    private val animation: FloatArray?,
) {
    private val hashCode = 31 * array.nanIdContentHashCode() + animation.nanIdContentHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoweredFloatExpressionKey) return false
        return hashCode == other.hashCode &&
            array.nanIdContentEquals(other.array) &&
            animation.nanIdContentEquals(other.animation)
    }

    override fun hashCode(): Int = hashCode
}

/** Key for a lowered integer expression, identified by its RPN [array]. */
internal class LoweredIntExpressionKey(private val array: LongArray) {
    private val hashCode = array.contentHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoweredIntExpressionKey) return false
        return hashCode == other.hashCode && array.contentEquals(other.array)
    }

    override fun hashCode(): Int = hashCode
}
