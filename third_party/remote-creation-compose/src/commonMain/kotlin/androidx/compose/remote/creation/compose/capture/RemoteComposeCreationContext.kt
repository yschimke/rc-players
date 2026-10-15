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
import androidx.compose.remote.creation.common.RemoteWriter
import androidx.compose.remote.creation.compose.state.BaseRemoteState
import androidx.compose.remote.creation.compose.state.RemoteStateScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.LayoutDirection

/** Common state-allocation surface independent of the platform capture and player stack. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface RemoteComposeCreationContext : RemoteStateScope {
    public val writer: RemoteWriter

    /**
     * Returns the id of a float expression with the same lowered [array] and [animation] that was
     * already written to [writer], or calls [write] to write it and caches the resulting id.
     */
    public fun getOrPutFloatExpressionId(
        array: FloatArray,
        animation: FloatArray?,
        write: () -> Int,
    ): Int

    /**
     * Returns the id of an integer expression with the same lowered [array] that was already
     * written to [writer], or calls [write] to write it and caches the resulting id.
     */
    public fun getOrPutIntExpressionId(array: LongArray, write: () -> Int): Int

    public override val remoteDensity: RemoteDensity

    public override val layoutDirection: LayoutDirection

    public override val densityBehavior: RemoteDensityBehavior

    public fun getOrPutFloatArray(key: Any, compute: () -> FloatArray): FloatArray

    public fun getOrPutLongArray(key: Any, compute: () -> LongArray): LongArray

    public fun getOrPutVariableId(key: Any, compute: () -> Int): Int

    public fun hasVariableId(key: Any): Boolean

    public fun enqueueGlobalDeclaration(state: BaseRemoteState<*>)

    public fun addBitmap(image: ImageBitmap): Int

    public fun addNamedBitmap(name: String, image: ImageBitmap): Int
}
