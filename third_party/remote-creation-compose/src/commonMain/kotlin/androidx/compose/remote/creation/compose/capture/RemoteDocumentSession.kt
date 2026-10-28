package androidx.compose.remote.creation.compose.capture

import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.common.RemoteWriter
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteComposeApplier
import androidx.compose.remote.creation.compose.layout.RemoteRootNode
import androidx.compose.remote.creation.compose.layout.toRemoteLayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * LOCAL PATCH (rc-players): a remote composition that stays alive across edits.
 *
 * [captureCommonRemoteDocument] builds a Recomposer, composes from nothing, renders and throws
 * everything away, on every call. A session instead composes [setContent] once as a child of
 * [parent] — the host's own recomposer or composition — so an edit recomposes only what changed, on
 * the host's frame, and [render] re-records the retained node tree into whatever [RemoteWriter] it
 * is handed. Writing never happens during composition (as upstream enforces), so each render starts
 * from a fresh writer and cleared id caches and gives a complete, self-consistent document.
 */
@OptIn(ExperimentalAtomicApi::class)
public class RemoteDocumentSession(
    private val creationDisplayInfo: RemoteCreationDisplayInfo,
    parent: CompositionContext,
    private val remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    private val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    supportedOperations: Set<Int> = emptySet(),
    encodePng: ((ImageBitmap) -> ByteArray?)? = null,
) {
    private val rootNode = RemoteRootNode()
    private val applier = RemoteComposeApplier(rootNode)
    private val composition = Composition(applier, parent)
    // Holds the writer between renders. Composition must not write the document, as upstream's
    // capture loop checks; anything written here would carry ids the next render reuses.
    private val compositionWriter = RemoteDocumentWriter(1, 1)
    private val compositionWriterSize = compositionWriter.encodeToByteArray().size
    private val creationState =
        RemoteComposeCreationState(
            creationDisplayInfo = creationDisplayInfo,
            writer = compositionWriter,
            remoteDensity = remoteDensity,
            layoutDirection = layoutDirection,
            supportedOperations = supportedOperations,
            platformImageProvider =
                encodePng?.let { SessionPngImageProvider(it) } ?: NoOpPlatformImageProvider,
        )

    /**
     * Called after every batch of changes is applied to the retained tree — the point at which a
     * live host re-renders. It runs in the recomposer's apply phase, outside composition.
     */
    public var onChangesApplied: (() -> Unit)?
        get() = applier.onChangesApplied
        set(value) {
            applier.onChangesApplied = value
        }

    /**
     * Called when a state the last [render] read has changed, on whichever thread applied it. A
     * `RemoteCanvas` draw lambda runs at render time, so a state read only there changes no node
     * and applies nothing, yet the document it drew is stale. A live host re-renders on this as it
     * does on [onChangesApplied]. Changes made by a render itself are not reported.
     */
    public var onRenderInputsChanged: (() -> Unit)? = null

    /**
     * Whether a state the last [render] read has changed since. The next [render] clears it, so a
     * host that re-renders for some other reason first has nothing left to render.
     */
    @Volatile
    public var renderInputsChanged: Boolean = false
        private set

    // What the last render read, and while its own apply is notified, what it wrote. Replaced
    // whole, so the apply observer, on any thread, sees one render's sets or the next's.
    @Volatile private var renderInputs = RenderInputs(emptySet(), emptySet())

    // Every change applied while a render records, held until it knows what it read: such a change
    // may be to a state the render is reading for the first time, which the previous render's reads
    // cannot show. Null outside a render. Only a change to a state the render read makes it stale;
    // the host applying its own, unrelated state meanwhile does not.
    private val changedWhileRendering = AtomicReference<List<Set<Any>>?>(null)

    private val renderInputsObserver = Snapshot.registerApplyObserver { changed, _ ->
        while (true) {
            val held = changedWhileRendering.load() ?: break
            if (changedWhileRendering.compareAndSet(held, held + listOf(changed)))
                return@registerApplyObserver
        }
        val inputs = renderInputs
        if (changed.any { it in inputs.reads && it !in inputs.writes }) markRenderInputsChanged()
    }

    private fun markRenderInputsChanged() {
        renderInputsChanged = true
        onRenderInputsChanged?.invoke()
    }

    /** Applier change count, so a host can skip [render] when nothing was applied. */
    public val changeCount: Long
        get() = applier.changeCount

    public fun setContent(content: @Composable @RemoteComposable () -> Unit) {
        composition.setContent {
            CompositionLocalProvider(
                LocalRemoteComposeCreationState provides creationState,
                LocalRemoteDensity provides remoteDensity,
                LocalDensity provides creationDisplayInfo.density,
                LocalInspectionMode provides creationDisplayInfo.isInspectionMode,
                LocalLayoutDirection provides layoutDirection,
                // As upstream's Android capture: follows a nested LocalLayoutDirection.
                LocalRemoteLayoutDirection providesComputed
                    {
                        LocalLayoutDirection.currentValue.toRemoteLayoutDirection()
                    },
                content = content,
            )
        }
    }

    /** Records the current node tree into [writer] and returns it. */
    public fun <W : RemoteWriter> render(writer: W): W {
        check(compositionWriter.encodeToByteArray().size == compositionWriterSize) {
            "The document was written to during composition"
        }
        val state = creationState
        state.writer = writer
        state.clearDocumentCaches()
        state.resetComponentIds()
        // Changes applied from here on are seen by this render or held for it.
        changedWhileRendering.store(emptyList())
        renderInputsChanged = false
        val reads = HashSet<Any>()
        val writes = HashSet<Any>()
        val snapshot =
            Snapshot.takeMutableSnapshot(
                readObserver = { reads += it },
                writeObserver = { writes += it },
            )
        try {
            snapshot.enter {
                val recordingCanvas =
                    CommonRecordingCanvas().apply { setRemoteComposeCreationState(state) }
                rootNode.render(state, RemoteCanvas(recordingCanvas))
                recordingCanvas.flush()
            }
            // The render's own writes are notified by its apply, below, and are no reason to
            // render again; a later change to one of them is.
            renderInputs = RenderInputs(reads, writes)
            val heldChanges = changedWhileRendering.exchange(null).orEmpty()
            if (heldChanges.any { changed -> changed.any { it in reads && it !in writes } }) {
                markRenderInputsChanged()
            }
            // Only a render that wrote state applies. An apply also claims pending global writes,
            // and notifies them on this thread ahead of the recomposer that has to see them first.
            if (snapshot.hasPendingChanges()) snapshot.apply().check()
            renderInputs = RenderInputs(reads, emptySet())
        } finally {
            changedWhileRendering.store(null)
            snapshot.dispose()
            creationState.writer = compositionWriter
        }
        return writer
    }

    public fun dispose() {
        renderInputsObserver.dispose()
        onRenderInputsChanged = null
        composition.dispose()
    }

    private class RenderInputs(val reads: Set<Any>, val writes: Set<Any>)

    /**
     * Bitmaps written inline as PNG into whichever writer the current render uses, as
     * [captureCommonRemoteDocument] does. Bitmap ids are resolved during render, never during
     * composition. The encoding is kept per image across renders, because a live session re-renders
     * on every edit and a PNG encode costs far more than the rest of a render.
     */
    private inner class SessionPngImageProvider(
        private val encodePng: (ImageBitmap) -> ByteArray?
    ) : PlatformImageProvider {
        private val encoded = HashMap<ImageBitmap, ByteArray?>()

        private fun png(image: ImageBitmap): ByteArray? =
            encoded.getOrPut(image) { encodePng(image) }

        private val writer: RemoteDocumentWriter?
            get() = creationState.writer as? RemoteDocumentWriter

        override fun addBitmap(image: ImageBitmap): Int =
            png(image)?.let { writer?.addBitmapPng(it, image.width, image.height) } ?: -1

        override fun addNamedBitmap(name: String, image: ImageBitmap): Int =
            png(image)?.let { writer?.addNamedBitmapPng(name, it, image.width, image.height) } ?: -1
    }
}
