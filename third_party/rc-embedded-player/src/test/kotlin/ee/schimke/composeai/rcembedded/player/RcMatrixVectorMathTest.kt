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

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteClock
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.utilities.MatrixOperations
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A `MatrixVectorMath` product follows its matrix as the matrix's inputs change, so geometry
 * projected through it (a document-defined 3D shape) moves.
 *
 * The fixture rotates the point (1, 0, 0) about Y by `ANIMATION_TIME * 90°` and draws a green
 * marker at `x' = cos(angle)`, beside a red control marker driven by the same angle directly. The
 * control proves time advanced; the green marker is what the matrix pipeline delivers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RcMatrixVectorMathTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun matrixVectorMathOutputFollowsAnimatedMatrix() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val documentBytes =
            captureSingleRemoteDocument(
                    context = context,
                    content = {
                        RemoteCanvas(RemoteModifier.size(200.rdp)) {
                            val angle = RemoteFloat(RemoteContext.FLOAT_ANIMATION_TIME) * 90f
                            val matrix =
                                document.matrixExpression(angle.floatId, MatrixOperations.ROT_Y)
                            val rotated = FloatArray(3)
                            document.addMatrixMultiply(matrix, floatArrayOf(1f, 0f, 0f), rotated)
                            val x = RemoteFloat(rotated[0])
                            drawRect(
                                RemotePaint { color = RemoteColor(Color.Red) },
                                RemoteOffset(angle, 10f.rf),
                                RemoteSize(10f.rf, 10f.rf),
                            )
                            drawRect(
                                RemotePaint { color = RemoteColor(Color.Green) },
                                RemoteOffset(x * 100f + 50f, 50f.rf),
                                RemoteSize(10f.rf, 10f.rf),
                            )
                        }
                    },
                )
                .bytes
        val document =
            CoreDocument(RemoteClock.SYSTEM).apply {
                ByteArrayInputStream(documentBytes).use {
                    initFromBuffer(RemoteComposeBuffer.fromInputStream(it))
                }
            }

        rule.mainClock.autoAdvance = false
        rule.setContent { Box(modifier = Modifier.size(200.dp)) { RcPlayer(document = document) } }
        rule.mainClock.advanceTimeBy(32)
        val start = capture()
        // One second: the angle reaches 90°, so cos goes from 1 to 0.
        rule.mainClock.advanceTimeBy(1000)
        val later = capture()

        val redStart = requireMarker(markerLeft(start, Color.Red))
        val redLater = requireMarker(markerLeft(later, Color.Red))
        assertTrue("control marker did not move: $redStart → $redLater", redLater > redStart + 40)

        val greenStart = requireMarker(markerLeft(start, Color.Green))
        val greenLater = requireMarker(markerLeft(later, Color.Green))
        // x' = cos(angle) * 100 + 50: from ~150 at t≈0 to ~50 at 90°.
        assertEquals("projected marker at t≈0", 150f, greenStart.toFloat(), 4f)
        assertEquals("projected marker at 90°", 50f, greenLater.toFloat(), 4f)
    }

    private fun capture(): Bitmap = rule.onRoot().captureToImage().asAndroidBitmap()

    /** Leftmost column holding an exact [color] pixel, in px, or null. */
    private fun markerLeft(bitmap: Bitmap, color: Color): Int? {
        val argb =
            android.graphics.Color.argb(
                255,
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            )
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) == argb) return x
            }
        }
        return null
    }

    private fun requireMarker(left: Int?): Int = checkNotNull(left) { "marker not drawn" }
}
