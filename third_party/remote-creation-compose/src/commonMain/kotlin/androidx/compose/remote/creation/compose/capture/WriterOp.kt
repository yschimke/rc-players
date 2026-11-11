package androidx.compose.remote.creation.compose.capture

import androidx.compose.remote.creation.common.DrawTextOnCircle
import androidx.compose.remote.creation.common.PaintBundle
import androidx.compose.remote.creation.common.RemoteComposeWriter
import androidx.compose.remote.creation.common.RemoteImageData
import androidx.compose.remote.creation.common.RemoteModifierData
import androidx.compose.remote.creation.common.RemoteTextData
import androidx.compose.remote.creation.compose.path.RemotePath
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteImageBitmap
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteString

/** Typed write operations retained in the document program until serialization. */
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

    data class Painted(
        val paint: RemotePaint?,
        val operation: WriterOp,
        val tracker: PaintTracker,
        val force: Boolean = false,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) {
            if (paint != null) {
                val bundle = PaintBundle()
                tracker.reset(force || writer.checkAndClearForceSendingNewPaint())
                tracker.updateWithPaint(paint, bundle, creationState)
                if (tracker.isChanged) writer.applyPaint(bundle)
            }
            operation.write(writer, creationState)
        }
    }

    data object Save : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.save()
    }

    data object Restore : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.restore()
    }

    data class Translate(val dx: RemoteFloat, val dy: RemoteFloat) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.translate(dx.valueIn(creationState), dy.valueIn(creationState))
    }

    data class Scale(
        val scaleX: RemoteFloat,
        val scaleY: RemoteFloat,
        val centerX: RemoteFloat?,
        val centerY: RemoteFloat?,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.scale(
                scaleX.valueIn(creationState),
                scaleY.valueIn(creationState),
                centerX?.valueIn(creationState) ?: Float.NaN,
                centerY?.valueIn(creationState) ?: Float.NaN,
            )
    }

    data class Rotate(
        val angle: RemoteFloat,
        val centerX: RemoteFloat?,
        val centerY: RemoteFloat?,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.rotate(
                angle.valueIn(creationState),
                centerX?.valueIn(creationState) ?: Float.NaN,
                centerY?.valueIn(creationState) ?: Float.NaN,
            )
    }

    data class Skew(val skewX: RemoteFloat, val skewY: RemoteFloat) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.skew(skewX.valueIn(creationState), skewY.valueIn(creationState))
    }

    data class DrawRect(
        val left: RemoteFloat,
        val top: RemoteFloat,
        val right: RemoteFloat,
        val bottom: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawRect(
                left.valueIn(creationState),
                top.valueIn(creationState),
                right.valueIn(creationState),
                bottom.valueIn(creationState),
            )
    }

    data class DrawRoundRect(
        val left: RemoteFloat,
        val top: RemoteFloat,
        val right: RemoteFloat,
        val bottom: RemoteFloat,
        val radiusX: RemoteFloat,
        val radiusY: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawRoundRect(
                left.valueIn(creationState),
                top.valueIn(creationState),
                right.valueIn(creationState),
                bottom.valueIn(creationState),
                radiusX.valueIn(creationState),
                radiusY.valueIn(creationState),
            )
    }

    data class DrawCircle(
        val centerX: RemoteFloat,
        val centerY: RemoteFloat,
        val radius: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawCircle(
                centerX.valueIn(creationState),
                centerY.valueIn(creationState),
                radius.valueIn(creationState),
            )
    }

    data class DrawOval(
        val left: RemoteFloat,
        val top: RemoteFloat,
        val right: RemoteFloat,
        val bottom: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawOval(
                left.valueIn(creationState),
                top.valueIn(creationState),
                right.valueIn(creationState),
                bottom.valueIn(creationState),
            )
    }

    data class DrawArc(
        val left: RemoteFloat,
        val top: RemoteFloat,
        val right: RemoteFloat,
        val bottom: RemoteFloat,
        val startAngle: RemoteFloat,
        val sweepAngle: RemoteFloat,
        val useCenter: Boolean,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) {
            val values =
                floatArrayOf(
                    left.valueIn(creationState),
                    top.valueIn(creationState),
                    right.valueIn(creationState),
                    bottom.valueIn(creationState),
                    startAngle.valueIn(creationState),
                    sweepAngle.valueIn(creationState),
                )
            if (useCenter) {
                writer.drawSector(values[0], values[1], values[2], values[3], values[4], values[5])
            } else {
                writer.drawArc(values[0], values[1], values[2], values[3], values[4], values[5])
            }
        }
    }

    data class DrawLine(
        val x1: RemoteFloat,
        val y1: RemoteFloat,
        val x2: RemoteFloat,
        val y2: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawLine(
                x1.valueIn(creationState),
                y1.valueIn(creationState),
                x2.valueIn(creationState),
                y2.valueIn(creationState),
            )
    }

    data class DrawPath(val pathData: FloatArray, val winding: Int = 0) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawPath(writer.addPathData(pathData, winding))
    }

    data class DrawBitmap(
        val bitmap: RemoteImageBitmap,
        val left: RemoteFloat,
        val top: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawBitmap(
                bitmap.idIn(creationState),
                left.valueIn(creationState),
                top.valueIn(creationState),
                bitmap.width.valueIn(creationState),
                bitmap.height.valueIn(creationState),
                0,
            )
    }

    data class DrawBitmapRect(
        val bitmap: RemoteImageBitmap,
        val left: RemoteFloat,
        val top: RemoteFloat,
        val right: RemoteFloat,
        val bottom: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawBitmap(
                bitmap.idIn(creationState),
                left.valueIn(creationState),
                top.valueIn(creationState),
                right.valueIn(creationState),
                bottom.valueIn(creationState),
                0,
            )
    }

    data object DrawComponentContent : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawComponentContent()
    }

    data class ClipPath(val pathData: FloatArray) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.addClipPath(writer.addPathData(pathData))
    }

    data class DrawTweenPath(
        val path1Data: FloatArray,
        val path2Data: FloatArray,
        val tween: RemoteFloat,
        val start: RemoteFloat,
        val stop: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawTweenPath(
                writer.addPathData(path1Data),
                writer.addPathData(path2Data),
                tween.valueIn(creationState),
                start.valueIn(creationState),
                stop.valueIn(creationState),
            )
    }

    data class DrawTextOnPath(
        val text: RemoteString,
        val pathData: FloatArray,
        val horizontalOffset: RemoteFloat,
        val verticalOffset: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawTextOnPath(
                text.idIn(creationState),
                writer.addPathData(pathData),
                horizontalOffset.valueIn(creationState),
                verticalOffset.valueIn(creationState),
            )
    }

    data class ClipRect(
        val left: RemoteFloat,
        val top: RemoteFloat,
        val right: RemoteFloat,
        val bottom: RemoteFloat,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.clipRect(
                left.valueIn(creationState),
                top.valueIn(creationState),
                right.valueIn(creationState),
                bottom.valueIn(creationState),
            )
    }

    data class DrawTextRun(
        val text: RemoteString,
        val start: Int,
        val end: Int,
        val contextStart: Int,
        val contextEnd: Int,
        val x: RemoteFloat,
        val y: RemoteFloat,
        val isRtl: Boolean,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawTextRun(
                text.idIn(creationState),
                start,
                end,
                contextStart,
                contextEnd,
                x.valueIn(creationState),
                y.valueIn(creationState),
                isRtl,
            )
    }

    data class DrawTextAnchored(
        val text: RemoteString,
        val anchorX: RemoteFloat,
        val anchorY: RemoteFloat,
        val panX: RemoteFloat,
        val panY: RemoteFloat,
        val flags: Int,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawTextAnchored(
                text.idIn(creationState),
                anchorX.valueIn(creationState),
                anchorY.valueIn(creationState),
                panX.valueIn(creationState),
                panY.valueIn(creationState),
                flags,
            )
    }

    data class DrawTextOnCircle(
        val text: RemoteString,
        val centerX: RemoteFloat,
        val centerY: RemoteFloat,
        val radius: RemoteFloat,
        val startAngle: RemoteFloat,
        val warpRadiusOffset: RemoteFloat,
        val alignment: androidx.compose.remote.creation.common.DrawTextOnCircle.Alignment,
        val placement: androidx.compose.remote.creation.common.DrawTextOnCircle.Placement,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawTextOnCircle(
                text.idIn(creationState),
                centerX.valueIn(creationState),
                centerY.valueIn(creationState),
                radius.valueIn(creationState),
                startAngle.valueIn(creationState),
                warpRadiusOffset.valueIn(creationState),
                alignment,
                placement,
            )
    }

    data class DrawScaledBitmap(
        val bitmap: RemoteImageBitmap,
        val srcLeft: RemoteFloat,
        val srcTop: RemoteFloat,
        val srcRight: RemoteFloat,
        val srcBottom: RemoteFloat,
        val dstLeft: RemoteFloat,
        val dstTop: RemoteFloat,
        val dstRight: RemoteFloat,
        val dstBottom: RemoteFloat,
        val scaleType: Int,
        val scaleFactor: RemoteFloat,
        val contentDescription: String?,
    ) : WriterOp {
        override fun write(writer: RemoteComposeWriter, creationState: RemoteComposeCaptureState) =
            writer.drawScaledBitmap(
                bitmap.idIn(creationState),
                srcLeft.valueIn(creationState),
                srcTop.valueIn(creationState),
                srcRight.valueIn(creationState),
                srcBottom.valueIn(creationState),
                dstLeft.valueIn(creationState),
                dstTop.valueIn(creationState),
                dstRight.valueIn(creationState),
                dstBottom.valueIn(creationState),
                scaleType,
                scaleFactor.valueIn(creationState),
                contentDescription,
            )
    }
}

private fun RemoteFloat.valueIn(creationState: RemoteComposeCreationState): Float =
    getFloatIdForCreationState(creationState)

private fun RemoteString.idIn(creationState: RemoteComposeCreationState): Int =
    getIdForCreationState(creationState)

private fun RemoteImageBitmap.idIn(creationState: RemoteComposeCreationState): Int =
    getIdForCreationState(creationState)

internal fun RemotePath.snapshotData(): FloatArray = pathArray.copyOf(size)
