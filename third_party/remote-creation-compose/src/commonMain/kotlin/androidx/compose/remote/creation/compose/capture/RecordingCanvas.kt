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
import androidx.compose.remote.creation.compose.layout.RemoteLayoutDirection
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteStateScope
import androidx.compose.ui.graphics.Matrix

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface RecordingCanvas : RemoteStateScope {
    public val enableOptimizations: Boolean
    public var creationState: RemoteComposeCreationState
    public val creationDisplayInfo: RemoteCreationDisplayInfo

    override val remoteDensity: RemoteDensity
    override val layoutDirection: RemoteLayoutDirection

    public fun save(): Int

    public fun restore()

    /**
     * Preconcat the current matrix with the specified translation
     *
     * @param dx The distance to translate in X
     * @param dy The distance to translate in Y
     */
    public fun translate(dx: Float, dy: Float)

    /**
     * Preconcat the current matrix with the specified translation
     *
     * @param dx The [RemoteFloat] distance to translate in X
     * @param dy The [RemoteFloat] distance to translate in Y
     */
    public fun translate(dx: RemoteFloat, dy: RemoteFloat)

    public fun scale(sx: RemoteFloat, sy: RemoteFloat)

    public fun scale(sx: RemoteFloat, sy: RemoteFloat, px: RemoteFloat, py: RemoteFloat)

    public fun rotate(degrees: RemoteFloat)

    public fun rotate(degrees: RemoteFloat, px: RemoteFloat, py: RemoteFloat)

    public fun concat(matrix: Matrix)

    public fun drawComponentContent()

    public fun flush()

    public fun setRemoteComposeCreationState(creationState: RemoteComposeCreationState)

    public fun custom(
        config: String,
        modifier: androidx.compose.remote.creation.compose.modifier.RemoteModifier =
            androidx.compose.remote.creation.compose.modifier.RemoteModifier,
        content: (() -> Unit)? = null,
        properties:
            androidx.compose.remote.creation.compose.layout.RemoteCustomPropertiesScope.() -> Unit =
            {},
    )
}

internal interface InternalRecordingCanvas : RecordingCanvas {
    val buffer: CanvasOperationBuffer
    var forceSendingPaint: Boolean
    var currentDrawToBitmapId: Int

    fun recordRenderingOp(op: CanvasOp): CanvasOperationBuffer.SpanOp

    fun recordRenderingOp(action: () -> Unit): CanvasOperationBuffer.SpanOp

    fun recordRenderingOp(operation: WriterOp): CanvasOperationBuffer.SpanOp

    fun recordRenderingOp(
        paint: RemotePaint?,
        action: () -> Unit,
    ): CanvasOperationBuffer.SpanOp

    fun recordRenderingOp(
        paint: RemotePaint?,
        operation: WriterOp,
    ): CanvasOperationBuffer.SpanOp

    fun recordInChildSpan(action: () -> Unit): CanvasOperationBuffer.Span

    fun recordInOffscreenChildSpan(
        bitmapId: Int,
        action: () -> Unit,
    ): CanvasOperationBuffer.Span
}
