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

package ee.schimke.composeai.rcembedded

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcembedded.player.RcPlayer
import ee.schimke.composeai.rcembedded.player.RcPlayerInspector
import ee.schimke.composeai.rcembedded.player.RcPlayerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The inspection seam, exercised through a **composed** player.
 *
 * The seam is upstream's [RcPlayerInspector]: with [isDebugInspectorInfoEnabled] on, the player
 * attaches inspectable modifier elements, and [RcPlayerInspector.captureTreeSnapshot] reads the
 * laid-out component tree back out of the Compose layout tree. With it off the player attaches
 * nothing. A host that wants the player's actions passes its own `onAction` / `onNamedAction`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RcPlayerInspectorTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun disableInspectorInfo() {
        isDebugInspectorInfoEnabled = false
    }

    private fun render(fixture: String, inspect: Boolean): RcPlayerState {
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$fixture")) {
                    "missing fixture /rc-fixtures/$fixture"
                }
                .use { it.readBytes() }
        isDebugInspectorInfoEnabled = inspect
        val state = RcPlayerState(RemoteDocument(bytes).document)
        composeRule.setContent {
            val documentDensity = Density(DENSITY, LocalDensity.current.fontScale)
            CompositionLocalProvider(LocalDensity provides documentDensity) {
                Box(
                    Modifier.size(
                        with(documentDensity) { WIDTH.toDp() },
                        with(documentDensity) { HEIGHT.toDp() },
                    )
                ) {
                    RcPlayer(state = state, modifier = Modifier.fillMaxSize())
                }
            }
        }
        composeRule.waitForIdle()
        return state
    }

    @Test
    fun theInspectorCapturesTheLaidOutTreeFromAComposedPlayer() {
        val state = render("ImageBackgroundRemoteButton-454x200.rc", inspect = true)

        val tree =
            RcPlayerInspector.captureTreeSnapshot(
                composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode(),
                state,
            )

        assertTrue("the inspector captured no tree at all", tree.isNotEmpty())
        val root = tree.first()
        assertTrue("root geometry is not laid out: $root", root.width > 0f && root.height > 0f)
        assertEquals("root node should not be reported gone", false, root.isGone)
        // Every node carries a real id, kind and visibility, which is what the corpus's tree
        // assertion
        // reads.
        tree.forEach { node ->
            assertTrue("node has no kind: $node", node.kind.isNotEmpty())
            assertNotNull(node.visibility)
        }
    }

    private companion object {
        const val WIDTH = 454
        const val HEIGHT = 200
        const val DENSITY = 1f
    }
}
