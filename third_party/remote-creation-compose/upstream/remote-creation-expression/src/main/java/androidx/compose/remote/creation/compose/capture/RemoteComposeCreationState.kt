/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)

package androidx.compose.remote.creation.compose.capture

import androidx.annotation.RestrictTo
import androidx.collection.MutableObjectIntMap
import androidx.compose.remote.creation.common.RemoteWriter
import androidx.compose.remote.creation.common.Utils
import androidx.compose.remote.creation.compose.layout.RemoteComponentToken
import androidx.compose.remote.creation.compose.layout.RemoteComposeNode
import androidx.compose.remote.creation.compose.layout.RemoteLayoutDirection
import androidx.compose.remote.creation.compose.state.BaseRemoteState
import androidx.compose.remote.creation.compose.state.RemoteStateCacheKey
import androidx.compose.remote.creation.compose.state.nanIdContentEquals
import androidx.compose.remote.creation.compose.state.nanIdContentHashCode
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.ImageBitmap

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class RemoteComposeCreationState(
    public val creationDisplayInfo: RemoteCreationDisplayInfo,
    writer: RemoteWriter,
    public override val remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    public override val layoutDirection: RemoteLayoutDirection = RemoteLayoutDirection.Ltr,
    public val supportedOperations: Set<Int> = emptySet(),
    private val platformImageProvider: PlatformImageProvider = NoOpPlatformImageProvider,
    internal var platformState: Any? = null,
) : RemoteComposeCreationContext {
    // LOCAL PATCH (rc-players): settable so a live session can render each frame into a
    // fresh writer while the composition, and every node in it, stays alive. Upstream's Android
    // capture loop does the same by replacing `legacyDocument` before each render.
    public final override var writer: RemoteWriter = writer
        internal set

    override val parentScope: RemoteComposeCreationState
        get() = this

    public final override val densityBehavior: RemoteDensityBehavior =
        creationDisplayInfo.densityBehavior

    /**
     * Ids of float expressions already written to [writer], keyed by their lowered RPN array and
     * optional animation. Used to write each distinct expression only once.
     */
    internal val floatExpressionIds: MutableObjectIntMap<LoweredFloatExpressionKey> =
        MutableObjectIntMap()

    /**
     * Ids of integer expressions already written to [writer], keyed by their lowered RPN array.
     * Used to write each distinct expression only once.
     */
    internal val intExpressionIds: MutableObjectIntMap<LoweredIntExpressionKey> =
        MutableObjectIntMap()
    internal val remoteVariableToId: MutableObjectIntMap<RemoteStateCacheKey> =
        MutableObjectIntMap()
    internal val floatArrayCache: HashMap<RemoteStateCacheKey, FloatArray> = HashMap()
    internal val longArrayCache: HashMap<RemoteStateCacheKey, LongArray> = HashMap()
    private val globalDeclarations = LinkedHashMap<RemoteStateCacheKey, BaseRemoteState<*>>()
    private var nextComponentId = -1_000_000
    internal var componentCacheKey: Int = 0
        private set

    /**
     * The last component (a layout's content section) recorded so far, in document order: the
     * component a size expression bound to no component token refers to, as AndroidX's
     * `RemoteComposeBuffer.lastComponentId` is.
     */
    internal var lastComponentId: Int = -1

    /**
     * Per-render state of a [RemoteComponentToken]: the component id its node was given in this
     * render, and the float variables its `ComponentValue`s were written to.
     *
     * Port: upstream writes `ComponentValue`s inside the owning component's body, where the player
     * binds them to that component. The port's writer writes them as declarations with an explicit
     * component id, so a value requested before its component is recorded reserves a variable that
     * [beginComponentContent] binds once the id is known.
     */
    private class TokenComponentState {
        val variableIds: IntArray = IntArray(RemoteComponentToken.PROP_COUNT) { NO_VARIABLE_ID }
        var componentId: Int = NO_COMPONENT_ID

        companion object {
            const val NO_VARIABLE_ID: Int = -1
            const val NO_COMPONENT_ID: Int = 0
        }
    }

    private val tokenComponentStates = HashMap<RemoteComponentToken, TokenComponentState>()

    /**
     * The [RemoteComposeNode] currently rendering its content or canvas commands, used to resolve
     * component tokens from [RemoteStateScope]s that are not tied to a specific modifier node.
     */
    internal var currentRenderingNode: RemoteComposeNode? = null

    /**
     * Marks [token]'s component as recorded with [componentId] in this render, and binds any value
     * of it requested before that to the component.
     */
    internal fun beginComponentContent(token: RemoteComponentToken, componentId: Int) {
        val state = tokenComponentStates.getOrPut(token) { TokenComponentState() }
        state.componentId = componentId
        for (propIndex in 0 until RemoteComponentToken.PROP_COUNT) {
            val reservedId = state.variableIds[propIndex]
            if (reservedId != TokenComponentState.NO_VARIABLE_ID) {
                writer.addComponentValue(
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
        val reserved = writer.reserveFloatVariable()
        state.variableIds[propIndex] = Utils.idFromNan(reserved)
        return reserved
    }

    /** Writes (once) the `ComponentValue` [propIndex] of [componentId] and returns its variable. */
    internal fun componentValue(componentId: Int, propIndex: Int): Float =
        when (propIndex) {
            RemoteComponentToken.PROP_WIDTH -> writer.addComponentWidthValue(componentId)
            RemoteComponentToken.PROP_HEIGHT -> writer.addComponentHeightValue(componentId)
            RemoteComponentToken.PROP_X -> writer.addComponentXValue(componentId)
            RemoteComponentToken.PROP_Y -> writer.addComponentYValue(componentId)
            RemoteComponentToken.PROP_ROOT_X -> writer.addComponentRootXValue(componentId)
            RemoteComponentToken.PROP_ROOT_Y -> writer.addComponentRootYValue(componentId)
            else -> throw IllegalArgumentException("Unknown property index: $propIndex")
        }

    internal fun allocateComponentId(): Int = nextComponentId--

    /**
     * LOCAL PATCH (rc-players): restarts component ids, so re-rendering the same retained tree
     * gives the same ids. Without it every render of a live composition shifts all of them, and
     * nothing keyed on a component id (editor bounds, player state) survives an edit.
     */
    internal fun resetComponentIds() {
        nextComponentId = -1_000_000
        lastComponentId = -1
        claimedComponentIds.clear()
        tokenComponentStates.clear()
        currentRenderingNode = null
    }

    // LOCAL PATCH (rc-players): explicit ids already used in this render; see ComponentIdModifier.
    private val claimedComponentIds = HashSet<Int>()

    /** Claims [id] for one component of this render; false if another already has it. */
    internal fun claimComponentId(id: Int): Boolean = claimedComponentIds.add(id)

    internal fun enterComponentScope(componentId: Int): Int {
        val previous = componentCacheKey
        componentCacheKey = componentId
        return previous
    }

    internal fun restoreComponentScope(previous: Int) {
        componentCacheKey = previous
    }

    public override fun enqueueGlobalDeclaration(state: BaseRemoteState<*>) {
        globalDeclarations[state.cacheKey] = state
    }

    internal fun drainGlobalDeclarations(record: (() -> Unit) -> Unit) {
        globalDeclarations.values.forEach { state -> record { state.getIdForCreationState(this) } }
        globalDeclarations.clear()
    }

    public override fun getOrPutFloatExpressionId(
        array: FloatArray,
        animation: FloatArray?,
        write: () -> Int,
    ): Int = floatExpressionIds.getOrPut(LoweredFloatExpressionKey(array, animation)) { write() }

    public override fun getOrPutIntExpressionId(array: LongArray, write: () -> Int): Int =
        intExpressionIds.getOrPut(LoweredIntExpressionKey(array)) { write() }

    /** Clears all caches tied to the current document. */
    internal fun clearDocumentCaches() {
        floatExpressionIds.clear()
        intExpressionIds.clear()
        remoteVariableToId.clear()
        floatArrayCache.clear()
        longArrayCache.clear()
        tokenComponentStates.clear()
        currentRenderingNode = null
    }

    public override fun getOrPutFloatArray(key: Any, compute: () -> FloatArray): FloatArray =
        floatArrayCache.getOrPut(key as RemoteStateCacheKey) { compute() }

    public override fun getOrPutLongArray(key: Any, compute: () -> LongArray): LongArray =
        longArrayCache.getOrPut(key as RemoteStateCacheKey) { compute() }

    public override fun getOrPutVariableId(key: Any, compute: () -> Int): Int {
        key as RemoteStateCacheKey
        val id = remoteVariableToId.getOrDefault(key, -1)
        if (id != -1) return id
        return compute().also { remoteVariableToId.put(key, it) }
    }

    public override fun hasVariableId(key: Any): Boolean =
        remoteVariableToId.contains(key as RemoteStateCacheKey)

    public val time: MutableState<Long> = mutableLongStateOf(0L)

    public override fun addBitmap(image: ImageBitmap): Int = platformImageProvider.addBitmap(image)

    public override fun addNamedBitmap(name: String, image: ImageBitmap): Int =
        platformImageProvider.addNamedBitmap(name, image)
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

internal object NoOpPlatformImageProvider : PlatformImageProvider {
    override fun addBitmap(image: ImageBitmap): Int = -1

    override fun addNamedBitmap(name: String, image: ImageBitmap): Int = -1
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface PlatformImageProvider {
    public fun addBitmap(image: ImageBitmap): Int

    public fun addNamedBitmap(name: String, image: ImageBitmap): Int
}
