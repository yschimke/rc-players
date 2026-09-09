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
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.managers.FitBoxLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import ee.schimke.composeai.rcembedded.player.layout.RcPlayerFitBoxLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A `FitBox` whose alternatives are themselves subcomposed lays out instead of throwing.
 *
 * `RcPlayerFitBoxLayout` picks its alternative by measuring probes in a `SubcomposeLayout`. Asking
 * those probes for `maxIntrinsicWidth` / `maxIntrinsicHeight` is what broke: Compose refuses
 * intrinsic queries against anything built on `SubcomposeLayout` — "Asking for intrinsic
 * measurements of SubcomposeLayout layouts is not supported. This includes components that are
 * built upon SubcomposeLayout, such as lazy lists, BoxWithConstraints, TabRow, etc." — and a
 * `FitBox` alternative that is itself a `FitBox` is exactly that. Such a document threw during
 * measurement rather than drawing, and nothing in the suite nested one.
 *
 * Measuring the probes under unconstrained constraints answers the same question of every child
 * whatever it is built from, which is what this layout did before the intrinsics rewrite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RcPlayerFitBoxNestingTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun aFitBoxNestedInsideAFitBoxLaysOut() {
    // Each alternative is a FitBox WITH children of its own: an empty one short-circuits to a
    // plain Box, which supports intrinsics, so it would not exercise the bug at all.
    val outer = fitBox(componentId = 1)
    outer.mList.add(nestedFitBox(componentId = 2, parent = outer, leafId = 20))
    outer.mList.add(nestedFitBox(componentId = 3, parent = outer, leafId = 30))

    val state = SnapshotRemoteComposeState()
    val document = CoreDocument().also { it.setRemoteComposeState(state) }
    rule.setContent {
      CompositionLocalProvider(
        LocalCoreDocument provides document,
        LocalRemoteContext provides object : StoreBackedRemoteContext(RemoteClock.SYSTEM) {},
      ) {
        RcPlayerFitBoxLayout(outer, Modifier)
      }
    }

    // Reaching idle is the assertion: an intrinsic query against the nested alternatives throws
    // out of the measure pass, which surfaces here rather than as a failed expectation.
    rule.waitForIdle()
  }

  private fun fitBox(componentId: Int, parent: FitBoxLayout? = null): FitBoxLayout =
    FitBoxLayout(parent, componentId, -1, FitBoxLayout.CENTER, FitBoxLayout.CENTER)

  /** A FitBox that really subcomposes, because it has an alternative to choose between. */
  private fun nestedFitBox(componentId: Int, parent: FitBoxLayout, leafId: Int): FitBoxLayout =
    fitBox(componentId, parent).also { nested ->
      nested.mList.add(Component(nested, leafId, 0, 0f, 0f, 8f, 8f))
    }
}
