/*
 * Copyright 2026 Yuri Schimke
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

import androidx.compose.remote.creation.compose.layout.RemoteCustomPropertiesScope
import androidx.compose.remote.creation.compose.layout.RemoteLayoutDirection
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteStateScope
import androidx.compose.remote.creation.compose.state.StandardRemotePaint
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.graphics.Matrix

/** JVM recorder for the platform-neutral RemoteCanvas write path. */
internal class CommonRecordingCanvas(override val enableOptimizations: Boolean = false) :
    InternalRecordingCanvas {
    override val buffer = CanvasOperationBuffer(enableOptimizations)
    override lateinit var creationState: RemoteComposeCaptureState
    override val parentScope: RemoteStateScope
        get() = creationState

    override val remoteDensity: RemoteDensity
        get() = creationState.remoteDensity

    override val layoutDirection: RemoteLayoutDirection
        get() = creationState.layoutDirection

    override val creationDisplayInfo: RemoteCreationDisplayInfo
        get() = creationState.creationDisplayInfo

    override var forceSendingPaint: Boolean = false
    override var currentDrawToBitmapId: Int = 0
    private val paintTracker = PaintTracker()

    override fun recordRenderingOp(op: CanvasOp): CanvasOperationBuffer.SpanOp =
        buffer.recordRenderingOp(op)

    override fun recordRenderingOp(action: () -> Unit): CanvasOperationBuffer.SpanOp =
        recordRenderingOp(CanvasOp.Draw { action() })

    override fun recordRenderingOp(operation: WriterOp): CanvasOperationBuffer.SpanOp =
        recordRenderingOp(CanvasOp.Draw(operation))

    override fun recordRenderingOp(
        paint: RemotePaint?,
        action: () -> Unit,
    ): CanvasOperationBuffer.SpanOp = recordRenderingOp(action)

    override fun recordRenderingOp(
        paint: RemotePaint?,
        operation: WriterOp,
    ): CanvasOperationBuffer.SpanOp {
        val snapshot = paint?.let(::StandardRemotePaint)
        val force = forceSendingPaint
        forceSendingPaint = false
        return recordRenderingOp(WriterOp.Painted(snapshot, operation, paintTracker, force))
    }

    override fun recordInChildSpan(action: () -> Unit): CanvasOperationBuffer.Span {
        val child = buffer.insertPoint.createChildSpan()
        val previous = buffer.insertPoint
        buffer.insertPoint = child
        try {
            action()
        } finally {
            buffer.insertPoint = previous
        }
        return child
    }

    override fun recordInOffscreenChildSpan(
        bitmapId: Int,
        action: () -> Unit,
    ): CanvasOperationBuffer.Span {
        val previous = currentDrawToBitmapId
        return recordInChildSpan {
            currentDrawToBitmapId = bitmapId
            try {
                action()
            } finally {
                currentDrawToBitmapId = previous
            }
        }
    }

    override fun save(): Int {
        recordRenderingOp(WriterOp.Save)
        return 1
    }

    override fun restore() {
        recordRenderingOp(WriterOp.Restore)
    }

    override fun translate(dx: Float, dy: Float) {
        recordRenderingOp(WriterOp.Translate(dx.rf, dy.rf))
    }

    override fun translate(dx: RemoteFloat, dy: RemoteFloat) {
        val op = recordRenderingOp(WriterOp.Translate(dx, dy))
        buffer.addRoots(op, dx, dy)
    }

    override fun scale(sx: RemoteFloat, sy: RemoteFloat) {
        val op = recordRenderingOp(WriterOp.Scale(sx, sy, null, null))
        buffer.addRoots(op, sx, sy)
    }

    override fun scale(
        sx: RemoteFloat,
        sy: RemoteFloat,
        px: RemoteFloat,
        py: RemoteFloat,
    ) {
        translate(px, py)
        scale(sx, sy)
        translate(-px, -py)
    }

    override fun rotate(degrees: RemoteFloat) {
        val op = recordRenderingOp(WriterOp.Rotate(degrees, null, null))
        buffer.addRoots(op, degrees)
    }

    override fun rotate(degrees: RemoteFloat, px: RemoteFloat, py: RemoteFloat) {
        translate(px, py)
        rotate(degrees)
        translate(-px, -py)
    }

    override fun concat(matrix: Matrix) {
        // Remote Compose has no matrix primitive. Preserve the affine operations used by Compose.
        val values = matrix.values
        translate(values[12], values[13])
    }

    override fun drawComponentContent() {
        recordRenderingOp(WriterOp.DrawComponentContent)
    }

    override fun flush() {
        buffer.flush(creationState)
    }

    override fun setRemoteComposeCreationState(creationState: RemoteComposeCaptureState) {
        this.creationState = creationState
    }

    override fun custom(
        config: String,
        modifier: RemoteModifier,
        content: (() -> Unit)?,
        properties: RemoteCustomPropertiesScope.() -> Unit,
    ) {
        val scope = RemoteCustomPropertiesScope().apply(properties)
        val child = content?.let(::recordInChildSpan)
        val op = recordRenderingOp(CanvasOp.CustomComponent(config, modifier, scope.entries, child))
        scope.entries.mapNotNull { it.state }.forEach { buffer.addRoots(op, it) }
    }
}
