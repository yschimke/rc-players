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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteClock
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.creation.compose.action.valueChange
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.state.animateRemoteFloat
import androidx.compose.remote.creation.compose.state.animateRemoteFloatAsState
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberMutableRemoteFloat
import androidx.compose.remote.creation.compose.state.remoteSpring
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RcAnimatedInteractionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun valueChangeAnimatesRemoteFloatToItsNewTarget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val documentBytes =
            captureSingleRemoteDocument(
                    context = context,
                    content = {
                        val progress = rememberMutableRemoteFloat { 0.25f.rf }
                        val animatedProgress = animateRemoteFloat(progress, 0.25f)
                        val advanceProgress =
                            valueChange(progress, ((progress + 0.25f) % 1f).createReference())

                        RemoteColumn(modifier = RemoteModifier.size(160.rdp)) {
                            RemoteBox(
                                modifier =
                                    RemoteModifier.size(160.rdp, 40.rdp).clickable(advanceProgress)
                            )
                            RemoteBox(
                                modifier =
                                    RemoteModifier.semantics {
                                            contentDescription = "animated-progress".rs
                                        }
                                        .width(animatedProgress * 200f)
                                        .height(20.rdp)
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

        rule.mainClock.advanceTimeBy(300)
        val progressNode = rule.onNodeWithContentDescription("animated-progress")
        fun progressWidth() =
            progressNode.getUnclippedBoundsInRoot().let { it.right.value - it.left.value }

        val initialWidth = progressWidth()
        rule.onNode(hasClickAction()).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
        val animatingWidth = progressWidth()
        rule.mainClock.advanceTimeBy(200)
        val settledWidth = progressWidth()

        assert(animatingWidth > initialWidth) {
            "Expected the click animation to grow past $initialWidth, but was $animatingWidth"
        }
        assert(settledWidth > animatingWidth) {
            "Expected the animation to settle past $animatingWidth, but was $settledWidth"
        }
    }

    /**
     * A spring starts settled at its target, as the View player's `FloatExpression` starts one. The
     * embedded player used to decode the spring array as a tween, seed the animation at a value
     * taken from its other fields, and so draw a still that ignored the target entirely (#551).
     */
    @Test
    fun springAnimatedRemoteFloatStartsAtItsTargetAndSpringsToTheNext() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val documentBytes =
            captureSingleRemoteDocument(
                    context = context,
                    content = {
                        val progress = rememberMutableRemoteFloat { 0.75f.rf }
                        val animatedProgress =
                            animateRemoteFloatAsState(
                                targetValue = progress,
                                animationSpec = remoteSpring(stiffness = 200f),
                            )
                        val shrink = valueChange(progress, 0.25f.rf)

                        RemoteColumn(modifier = RemoteModifier.size(160.rdp)) {
                            RemoteBox(
                                modifier = RemoteModifier.size(160.rdp, 40.rdp).clickable(shrink)
                            )
                            RemoteBox(
                                modifier =
                                    RemoteModifier.semantics {
                                            contentDescription = "spring-progress".rs
                                        }
                                        .width(animatedProgress * 200f)
                                        .height(20.rdp)
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

        val progressNode = rule.onNodeWithContentDescription("spring-progress")
        fun progressWidth() =
            progressNode.getUnclippedBoundsInRoot().let { it.right.value - it.left.value }

        // The first frame, before any animation clock has run: already at 0.75 of 200dp.
        val firstFrameWidth = progressWidth()
        assert(abs(firstFrameWidth - 150f) < 1f) {
            "Expected the spring to start at its target width 150, but was $firstFrameWidth"
        }

        rule.onNode(hasClickAction()).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(50)
        val springingWidth = progressWidth()
        rule.mainClock.advanceTimeBy(2_000)
        val settledWidth = progressWidth()

        assert(springingWidth < firstFrameWidth && springingWidth > settledWidth) {
            "Expected the spring to be between $firstFrameWidth and $settledWidth, was $springingWidth"
        }
        assert(abs(settledWidth - 50f) < 1f) {
            "Expected the spring to settle at 50, but was $settledWidth"
        }
    }
}
