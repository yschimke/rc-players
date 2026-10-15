package androidx.compose.remote.creation.compose.capture

import androidx.compose.remote.creation.common.RemoteDocumentWriter
import androidx.compose.remote.creation.common.RemoteWriter
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteComposeApplier
import androidx.compose.remote.creation.compose.layout.RemoteRootNode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * PROTOTYPE (embedded documents): a remote composition that stays alive across edits.
 *
 * [captureCommonRemoteDocument] builds a Recomposer, composes from nothing, renders and throws
 * everything away, on every call. A session instead composes [setContent] once as a child of
 * [parent] — the host's own recomposer or composition — so an edit recomposes only what changed, on
 * the host's frame, and [render] re-records the retained node tree into whatever [RemoteWriter] it
 * is handed. Writing never happens during composition (as upstream enforces), so each render starts
 * from a fresh writer and cleared id caches and gives a complete, self-consistent document.
 */
public class RemoteDocumentSession(
    private val creationDisplayInfo: RemoteCreationDisplayInfo,
    parent: CompositionContext,
    private val remoteDensity: RemoteDensity = RemoteDensity.from(creationDisplayInfo),
    private val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    supportedOperations: Set<Int> = emptySet(),
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
        try {
            Snapshot.withMutableSnapshot {
                val recordingCanvas =
                    CommonRecordingCanvas().apply { setRemoteComposeCreationState(state) }
                rootNode.render(state, RemoteCanvas(recordingCanvas))
                recordingCanvas.flush()
            }
        } finally {
            creationState.writer = compositionWriter
        }
        return writer
    }

    public fun dispose() {
        composition.dispose()
    }
}
