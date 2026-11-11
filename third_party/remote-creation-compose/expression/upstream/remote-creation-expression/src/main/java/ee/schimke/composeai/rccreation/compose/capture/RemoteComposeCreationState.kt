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

package ee.schimke.composeai.rccreation.compose.capture

import androidx.annotation.RestrictTo
import androidx.collection.MutableObjectIntMap
import ee.schimke.composeai.rccreation.common.RemoteComposeWriter
import ee.schimke.composeai.rccreation.common.Utils
import ee.schimke.composeai.rccreation.compose.layout.RemoteComponentToken
import ee.schimke.composeai.rccreation.compose.state.AnimatedRemoteFloat
import ee.schimke.composeai.rccreation.compose.state.BaseRemoteStateCacheKey
import ee.schimke.composeai.rccreation.compose.state.MeshExpressionLowering
import ee.schimke.composeai.rccreation.compose.state.RemoteFloatExpression
import ee.schimke.composeai.rccreation.compose.state.RemoteIntExpression
import ee.schimke.composeai.rccreation.compose.state.RemoteOperationCacheKey
import ee.schimke.composeai.rccreation.compose.state.RemoteStateCacheKey
import ee.schimke.composeai.rccreation.compose.state.nanIdContentEquals
import ee.schimke.composeai.rccreation.compose.state.nanIdContentHashCode

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

    // KMP (rc-players): upstream reads `document.buffer.lastComponentId`, which the Kotlin writer
    // does not have; the creation state behind a scope tracks it, and a scope that is not one
    // answers with its parent's. Declared on this interface, beside `document`, so that
    // `RemoteFloatContext` needs no type of `remote-creation-compose` to reach it.
    /** The last component recorded so far; see [RemoteComposeCreationState.lastComponentId]. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public val lastComponentId: Int
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

    /**
     * Per-document emission state for a [RemoteComponentToken] during a single render pass.
     *
     * Cleared by [clearDocumentCaches] when starting a new document capture.
     */
    private class TokenComponentState {
        /**
         * Allocated variable IDs indexed by `RemoteComponentToken.PROP_*`.
         *
         * [NO_VARIABLE_ID] (`-1`) means no variable has been allocated for that property in the
         * current [document] yet; otherwise holds the integer variable ID reserved or emitted for
         * that component property.
         */
        val variableIds: IntArray = IntArray(RemoteComponentToken.PROP_COUNT) { NO_VARIABLE_ID }

        /**
         * Bitmask indexed by `RemoteComponentToken.PROP_*` (`1 shl propIndex`) tracking which
         * `ComponentValue` operations have already been written into the owning component's body in
         * [document].
         */
        var emittedMask: Int = 0

        /**
         * `true` while the [document] writer cursor is directly inside the owning component's body
         * (before any child components are emitted). Set back to `false` by
         * [disableDirectComponentEmission] before child nodes render and by [endComponentContent].
         */
        var canEmitDirectly: Boolean = false

        // KMP (rc-players): the Kotlin writer takes the component's id for each `ComponentValue`,
        // where `RemoteComposeBuffer` writes its own `lastComponentId`; so this is always the
        // owning component's id once it has begun, and never -1.
        /** The owning component's wire id, or [NO_COMPONENT_ID] before it has begun. */
        var componentId: Int = NO_COMPONENT_ID

        // LOCAL PATCH (rc-players): whether the writer is in the owning component, between a
        // [beginComponentContent] and its [endComponentContent]; see [getComponentValue].
        var inside: Boolean = false

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

    // KMP (rc-players): [componentId] is required: the Kotlin writer has no `lastComponentId` that
    // names the component being written, so the node says which one it is.
    /**
     * Marks the start of [token]'s component body in [document] and emits `ComponentValue`
     * operations for any properties already requested on [token] (either from an earlier expression
     * evaluation in this render pass or recorded on [token] during a prior pass).
     */
    public fun beginComponentContent(token: RemoteComponentToken, componentId: Int) {
        val state = tokenComponentStates.getOrPut(token) { TokenComponentState() }
        state.componentId = componentId
        state.canEmitDirectly = true
        state.inside = true
        for (propIndex in 0 until RemoteComponentToken.PROP_COUNT) {
            val bit = 1 shl propIndex
            if ((state.emittedMask and bit) != 0) continue
            val existingId = state.variableIds[propIndex]
            if (existingId != TokenComponentState.NO_VARIABLE_ID) {
                document.addComponentValue(
                    RemoteComponentToken.toComponentValueType(propIndex),
                    componentId,
                    Utils.asNan(existingId),
                )
                state.emittedMask = state.emittedMask or bit
            } else if (token.isRequested(propIndex)) {
                state.variableIds[propIndex] =
                    Utils.idFromNan(componentValue(componentId, propIndex))
                state.emittedMask = state.emittedMask or bit
            }
        }
    }

    /**
     * Disables direct `ComponentValue` emission for [token] before child components are rendered.
     *
     * A value written while a descendant is being written would be that descendant's to a player
     * (`LayoutComponent.inflate` gives a `ComponentValue` to the component it is written in), so
     * any [getComponentValue] call for [token] triggered lazily while rendering descendants must
     * reserve a float variable ID instead of emitting directly, leaving the `ComponentValue`
     * operation to be emitted in [endComponentContent] inside [token]'s own container body.
     */
    public fun disableDirectComponentEmission(token: RemoteComponentToken) {
        tokenComponentStates[token]?.canEmitDirectly = false
    }

    /**
     * Flushes any deferred `ComponentValue` operations reserved for [token] while rendering its
     * children, writing them into [token]'s component body before the container closes.
     */
    public fun endComponentContent(token: RemoteComponentToken) {
        val state = tokenComponentStates[token] ?: return
        state.canEmitDirectly = false
        state.inside = false
        for (propIndex in 0 until RemoteComponentToken.PROP_COUNT) {
            val bit = 1 shl propIndex
            if ((state.emittedMask and bit) != 0) continue
            val reservedId = state.variableIds[propIndex]
            if (reservedId != TokenComponentState.NO_VARIABLE_ID) {
                document.addComponentValue(
                    RemoteComponentToken.toComponentValueType(propIndex),
                    state.componentId,
                    Utils.asNan(reservedId),
                )
                state.emittedMask = state.emittedMask or bit
            }
        }
    }

    /**
     * Returns the NaN-encoded float variable value representing [propIndex] (`PROP_WIDTH`,
     * `PROP_HEIGHT`, etc.) for the layout component identified by [token].
     *
     * Reuses the existing variable ID if already allocated in this [document]. Otherwise, if the
     * writer is currently directly inside [token]'s body ([TokenComponentState.canEmitDirectly]),
     * emits the `ComponentValue` operation immediately; if called before [beginComponentContent] or
     * while rendering descendants, reserves a float variable via `reserveFloatVariable()` so
     * [beginComponentContent] or [endComponentContent] binds it to [token]'s component body.
     */
    internal fun getComponentValue(token: RemoteComponentToken, propIndex: Int): Float {
        token.markRequested(propIndex)
        val state = tokenComponentStates.getOrPut(token) { TokenComponentState() }
        val existingId = state.variableIds[propIndex]
        if (existingId != TokenComponentState.NO_VARIABLE_ID) {
            return Utils.asNan(existingId)
        }
        val allocatedId =
            if (state.canEmitDirectly) {
                state.emittedMask = state.emittedMask or (1 shl propIndex)
                Utils.idFromNan(componentValue(state.componentId, propIndex))
            } else if (state.componentId != TokenComponentState.NO_COMPONENT_ID && !state.inside) {
                // LOCAL PATCH (rc-players): asked for after the component has been written (a later
                // sibling sized from it). Nothing more can be written in it, and written where it
                // is asked for it would be another component's, so it is written ahead of the root,
                // where a player finds its component by the id it names. Upstream reserves a
                // variable no `ComponentValue` ever binds.
                state.emittedMask = state.emittedMask or (1 shl propIndex)
                document.beginGlobal()
                try {
                    Utils.idFromNan(componentValue(state.componentId, propIndex))
                } finally {
                    document.endGlobal()
                }
            } else {
                Utils.idFromNan(document.reserveFloatVariable())
            }
        state.variableIds[propIndex] = allocatedId
        return Utils.asNan(allocatedId)
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
    public override var lastComponentId: Int = -1

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

    // LOCAL PATCH (rc-players): for what has to know whether it is written as drawing or among a
    // layout's operations (`RemoteTextMeasurer`).
    /** Whether a canvas's drawing (`CanvasOperations` or `CanvasContent`) is being written. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val isInCanvasOperationsScope: Boolean
        get() = canvasOperationsScopes.isNotEmpty()

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
        // LOCAL PATCH (rc-players): an array that reads an id of a body that has ended is lowered
        // again; see [readsRetiredId].
        floatArrayCache[key]?.let { if (!readsRetiredId(it)) return it }
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
        // LOCAL PATCH (rc-players): as for a float array; see [readsRetiredId].
        longArrayCache[key]?.let { if (!readsRetiredId(it)) return it }
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
        // LOCAL PATCH (rc-players): not an id of a body that has ended; see [readsRetiredId].
        if (existing != -1 && !document.isRetired(existing)) {
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
        // LOCAL PATCH (rc-players): not an id of a body that has ended; see [readsRetiredId].
        if (existing != -1 && !document.isRetired(existing)) {
            return existing
        }
        val id = write()
        intExpressionIds.put(key, id)
        recordScopedIntExpressionKey(key)
        return id
    }

    /**
     * LOCAL PATCH (rc-players): whether a lowered expression reads an id the writer has retired.
     *
     * What a loop, pattern or for-each body computes from its own ids (its index, parameters or
     * item) is declared in that body, and the writer retires those ids when the body ends: a player
     * computes them in the body only, or gives them new ids each time it expands it. These caches
     * are not scoped to a body, so an entry made in one is checked before it is answered outside
     * it, and made again there. Upstream's caches have no such check, and its writer declares in
     * place, so an id a nested body declared is answered for the body around it.
     */
    @PublishedApi
    internal fun readsRetiredId(array: FloatArray): Boolean {
        for (value in array) {
            if (value.isNaN() && document.isRetired(Utils.idFromNan(value))) return true
        }
        return false
    }

    @PublishedApi
    internal fun readsRetiredId(array: LongArray): Boolean {
        for (value in array) {
            if (
                value >= INTEGER_ID_OFFSET &&
                    document.isRetired((value - INTEGER_ID_OFFSET).toInt())
            ) {
                return true
            }
        }
        return false
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
        // LOCAL PATCH (rc-players): not an id of a body that has ended; see [readsRetiredId].
        if (id == -1 || document.isRetired(id)) {
            val nextId = compute()
            remoteVariableToId.put(key, nextId)
            recordScopedVariableKey(key)
            return nextId
        }
        return id
    }
}

// LOCAL PATCH (rc-players): where an integer expression's ids start; see `readsRetiredId`.
@PublishedApi internal const val INTEGER_ID_OFFSET: Long = 0x100000000L

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
