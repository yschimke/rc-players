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
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.RemoteScrollState
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.horizontalScroll
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A scroll container starts at the position the document gives it (upstream `db2c2360362`, taken
 * with the alpha24 refresh). The player used to start every scroll at 0 and then publish that 0
 * back into the document's scroll-position variable, so a `RemoteScrollState(position = 100f)`
 * opened on its first page.
 *
 * This is upstream's `RcPlayerPixelTest.scroll_rendersAtInitialPositionFromState`, written against
 * this copy: a 100dp window on a row of red, blue and green 100dp boxes, scrolled 100 px, shows
 * blue. With `-Prc.embedded.scrollOutput=<dir>` it also writes the frame it read, which is the
 * evidence the refresh's pull request shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "mdpi")
class RcInitialScrollPositionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun scrollStartsAtTheDocumentsPosition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val captured =
            captureSingleRemoteDocument(
                context = context,
                content = {
                    val scrollState = remember { RemoteScrollState(position = 100f, notches = 2) }
                    RemoteRow(
                        modifier = RemoteModifier.size(100.rdp).horizontalScroll(scrollState),
                    ) {
                        RemoteBox(modifier = RemoteModifier.size(100.rdp).background(Color.Red.rc))
                        RemoteBox(modifier = RemoteModifier.size(100.rdp).background(Color.Blue.rc))
                        RemoteBox(
                            modifier = RemoteModifier.size(100.rdp).background(Color.Green.rc),
                        )
                    }
                },
            )

        rule.setContent {
            Box(modifier = Modifier.size(100.dp).testTag("player")) {
                RcPlayer(capturedDocument = captured)
            }
        }
        rule.waitForIdle()

        val bitmap = rule.onNodeWithTag("player").captureToImage().asAndroidBitmap()
        System.getProperty("rc.embedded.scrollOutput")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "initial-scroll.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val center = Color(bitmap.getPixel(50, 50))
        assertTrue("expected the second (blue) page, got $center", center.blue > 0.8f)
        assertTrue("expected the second (blue) page, got $center", center.red < 0.2f)
    }
}
