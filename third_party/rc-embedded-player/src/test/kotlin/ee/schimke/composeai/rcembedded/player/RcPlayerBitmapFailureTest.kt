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

@file:Suppress("RestrictedApiAndroidX")

package ee.schimke.composeai.rcembedded.player

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteClock
import androidx.compose.remote.core.operations.BitmapData
import androidx.compose.remote.core.operations.layout.managers.BoxLayout
import androidx.compose.remote.player.core.platform.AndroidRemoteContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RcPlayerBitmapFailureTest {

    @Test
    fun relativeUriLeavesBitmapSlotEmpty() {
        val context = AndroidRemoteContext()
        val state = SnapshotRemoteComposeState()
        context.mRemoteComposeState = state
        context.putObject(
            IMAGE_ID,
            BitmapData(
                IMAGE_ID,
                BitmapData.TYPE_PNG,
                64,
                BitmapData.ENCODING_URL,
                64,
                "camera/current".toByteArray(),
            ),
        )

        assertNull(resolveBitmap(context, IMAGE_ID))
        // The failed decode is memoized, so another frame remains empty without retrying or
        // throwing.
        assertNull(resolveBitmap(context, IMAGE_ID))
    }

    /** Upstream's `RcPlayerBitmapFailureTest`, over the same relative URI as above. */
    @Test
    fun nestedRelativeUriIsSkippedDuringSetupTraversal() {
        val document = CoreDocument(RemoteClock.SYSTEM)
        val context = AndroidRemoteContext(RemoteClock.SYSTEM)
        val bitmap = relativeUriBitmap()
        val boxLayout = BoxLayout(null, 1, 0, 0f, 0f, 100f, 100f, BoxLayout.START, BoxLayout.TOP)
        boxLayout.getList().add(bitmap)
        document.getOperationsReflection().add(boxLayout)

        document.initializeContext(context, null)
        document.applyDataOperationsWithoutBitmaps(context)

        assertFalse(
            "setup must not decode the nested bitmap",
            context.mRemoteComposeState.containsId(IMAGE_ID),
        )
        assertEquals(bitmap, context.getObject(IMAGE_ID))
    }

    private fun relativeUriBitmap(): BitmapData =
        BitmapData(
            IMAGE_ID,
            BitmapData.TYPE_PNG,
            64,
            BitmapData.ENCODING_URL,
            64,
            "camera/current".toByteArray(),
        )

    private companion object {
        const val IMAGE_ID = 42
    }
}
