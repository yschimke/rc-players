package androidx.compose.remote.creation.compose.capture

import androidx.compose.remote.creation.common.RemoteComposeWriter
import androidx.compose.remote.creation.common.RemoteImageData
import androidx.compose.remote.creation.common.RemoteModifierData
import androidx.compose.remote.creation.common.RemoteTextData

/**
 * The layout nodes' writer calls, retained in the canvas's operation buffer until it flushes. The
 * drawing operations are recorded as upstream records them, in `RemoteCanvas`.
 */
internal sealed interface WriterOp {
    fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState)

    data object StartRoot : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startRoot()
    }

    data object EndRoot : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endRoot()
    }

    data class StartBox(val modifier: RemoteModifierData, val horizontal: Int, val vertical: Int) :
        WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startBox(modifier, horizontal, vertical)
    }

    data object EndBox : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endBox()
    }

    data class StartRow(val modifier: RemoteModifierData, val horizontal: Int, val vertical: Int) :
        WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startRow(modifier, horizontal, vertical)
    }

    data object EndRow : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endRow()
    }

    data class StartColumn(
        val modifier: RemoteModifierData,
        val horizontal: Int,
        val vertical: Int,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startColumn(modifier, horizontal, vertical)
    }

    data object EndColumn : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endColumn()
    }

    data class StartCollapsibleRow(
        val modifier: RemoteModifierData,
        val horizontal: Int,
        val vertical: Int,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startCollapsibleRow(modifier, horizontal, vertical)
    }

    data object EndCollapsibleRow : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endCollapsibleRow()
    }

    data class StartCollapsibleColumn(
        val modifier: RemoteModifierData,
        val horizontal: Int,
        val vertical: Int,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startCollapsibleColumn(modifier, horizontal, vertical)
    }

    data object EndCollapsibleColumn : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endCollapsibleColumn()
    }

    data class StartCanvas(val modifier: RemoteModifierData) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startCanvas(modifier)
    }

    data object EndCanvas : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endCanvas()
    }

    data object StartCanvasOperations : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startCanvasOperations()
    }

    data object EndCanvasOperations : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endCanvasOperations()
    }

    data class StartFitBox(
        val modifier: RemoteModifierData,
        val horizontal: Int,
        val vertical: Int,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startFitBox(modifier, horizontal, vertical)
    }

    data object EndFitBox : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endFitBox()
    }

    data class StartFlow(
        val modifier: RemoteModifierData,
        val horizontal: Int,
        val vertical: Int,
        val maxItemsInEachRow: Int,
        val maxLines: Int,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startFlow(modifier, horizontal, vertical, maxItemsInEachRow, maxLines)
    }

    data object EndFlow : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endFlow()
    }

    data class StartStateLayout(val modifier: RemoteModifierData, val indexId: Int) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startStateLayout(modifier, indexId)
    }

    data object EndStateLayout : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endStateLayout()
    }

    data class StartText(val data: RemoteTextData) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.startTextComponent(data)
    }

    data object EndText : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.endTextComponent()
    }

    data class Image(val data: RemoteImageData) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.image(data)
    }
}
