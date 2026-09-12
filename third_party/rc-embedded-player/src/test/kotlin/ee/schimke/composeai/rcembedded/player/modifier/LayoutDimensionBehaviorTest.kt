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

package ee.schimke.composeai.rcembedded.player.modifier

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.remote.core.operations.layout.modifiers.DimensionConstraintsModifierOperation
import androidx.compose.remote.core.operations.layout.modifiers.DimensionModifierOperation
import androidx.compose.remote.core.operations.layout.modifiers.HeightModifierOperation
import androidx.compose.remote.core.operations.layout.modifiers.WidthModifierOperation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
class LayoutDimensionBehaviorTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun requiredMinimumCanOverrideTheIncomingParentConstraint() {
    val operation =
      DimensionConstraintsModifierOperation(
        DimensionConstraintsModifierOperation.REQUIRED_HORIZONTAL_CONSTRAINTS,
        80f,
        -1f,
      )

    assertMeasuredWidth(80) { Modifier.dimensionConstraints(operation).size(10.dp) }
  }

  @Test
  fun requiredVerticalMinimumCanOverrideTheIncomingParentConstraint() {
    val operation =
      DimensionConstraintsModifierOperation(
        DimensionConstraintsModifierOperation.REQUIRED_VERTICAL_CONSTRAINTS,
        80f,
        -1f,
      )

    assertMeasuredHeight(80) { Modifier.dimensionConstraints(operation).size(10.dp) }
  }

  @Test
  fun fillParentMaxWidthUsesTheAvailableWidth() {
    val operation =
      WidthModifierOperation(DimensionModifierOperation.Type.FILL_PARENT_MAX_WIDTH, 1f)

    assertMeasuredWidth(100) { Modifier.width(operation).size(10.dp) }
  }

  @Test
  fun fillMaxWidthPreservesItsFraction() {
    val operation = WidthModifierOperation(DimensionModifierOperation.Type.FILL, 0.5f)

    assertMeasuredWidth(50) { Modifier.width(operation).size(10.dp) }
  }

  @Test
  fun fillParentMaxHeightUsesTheAvailableHeight() {
    val operation =
      HeightModifierOperation(DimensionModifierOperation.Type.FILL_PARENT_MAX_HEIGHT, 1f)

    assertMeasuredHeight(100) { Modifier.height(operation).size(10.dp) }
  }

  @Test
  fun fillMaxHeightPreservesItsFraction() {
    val operation = HeightModifierOperation(DimensionModifierOperation.Type.FILL, 0.5f)

    assertMeasuredHeight(50) { Modifier.height(operation).size(10.dp) }
  }

  private fun assertMeasuredWidth(expected: Int, childModifier: @Composable () -> Modifier) {
    val measuredWidth = AtomicInteger()
    composeRule.setContent {
      Box(Modifier.size(100.dp, 50.dp)) {
        Box(childModifier().onSizeChanged { measuredWidth.set(it.width) })
      }
    }
    composeRule.waitForIdle()
    assertEquals(expected, measuredWidth.get())
  }

  private fun assertMeasuredHeight(expected: Int, childModifier: @Composable () -> Modifier) {
    val measuredHeight = AtomicInteger()
    composeRule.setContent {
      Box(Modifier.size(50.dp, 100.dp)) {
        Box(childModifier().onSizeChanged { measuredHeight.set(it.height) })
      }
    }
    composeRule.waitForIdle()
    assertEquals(expected, measuredHeight.get())
  }
}
