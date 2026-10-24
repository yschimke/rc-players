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
@file:kotlin.jvm.JvmName("RemoteComposeCreationStateKt")
@file:kotlin.jvm.JvmMultifileClass

package androidx.compose.remote.creation.compose.capture

import androidx.annotation.RestrictTo
import androidx.collection.MutableObjectIntMap
import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.common.RemoteWriter
import androidx.compose.remote.creation.compose.state.BaseRemoteState
import androidx.compose.remote.creation.compose.state.RemoteStateCacheKey
import androidx.compose.remote.creation.compose.state.nanIdContentEquals
import androidx.compose.remote.creation.compose.state.nanIdContentHashCode
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.LayoutDirection

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class RemoteComposeCreationState(
    public val creationDisplayInfo: RemoteCreationDisplayInfo,
    writer: RemoteWriter,
    public override var remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    public override var layoutDirection: LayoutDirection = LayoutDirection.Ltr,
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
    public var ready: Boolean = true
    internal val remoteVariableToId: MutableObjectIntMap<RemoteStateCacheKey> =
        MutableObjectIntMap()
    internal val floatArrayCache: HashMap<RemoteStateCacheKey, FloatArray> = HashMap()
    internal val longArrayCache: HashMap<RemoteStateCacheKey, LongArray> = HashMap()
    private val globalDeclarations = LinkedHashMap<RemoteStateCacheKey, BaseRemoteState<*>>()
    private var nextComponentId = -1_000_000
    internal var componentCacheKey: Int = 0
        private set

    /**
     * LOCAL PATCH (rc-players): the last component (a layout's content section) recorded so far, in
     * document order: the component a size expression refers to, as AndroidX's
     * `RemoteComposeBuffer.lastComponentId` is.
     */
    internal var lastComponentId: Int = -1

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
public class NoRemoteCompose :
    RemoteComposeCreationState(
        creationDisplayInfo = RemoteCreationDisplayInfo(1, 1, 160, 1.0f),
        writer = RemoteDocumentWriter(1, 1),
    )

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface PlatformImageProvider {
    public fun addBitmap(image: ImageBitmap): Int

    public fun addNamedBitmap(name: String, image: ImageBitmap): Int
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val LocalRemoteComposeCreationState: ProvidableCompositionLocal<RemoteComposeCreationState> =
    compositionLocalOf {
        NoRemoteCompose()
    }
