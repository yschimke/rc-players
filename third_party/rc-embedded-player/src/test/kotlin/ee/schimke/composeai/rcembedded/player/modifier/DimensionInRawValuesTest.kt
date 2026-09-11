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

import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.core.operations.layout.modifiers.DimensionInModifierOperation
import androidx.compose.remote.core.operations.layout.modifiers.WidthInModifierOperation
import ee.schimke.composeai.rcembedded.player.dimensionInRawValues
import org.junit.Assert.assertEquals
import org.junit.Test

class DimensionInRawValuesTest {
  @Test
  fun keepsVariableIdsAfterCoreResolvesTheConstraint() {
    val maxSource = Utils.asNan(42)
    val op = WidthInModifierOperation(12f, maxSource)
    setResolved(op, 24f, 0f)

    val (min, max) = dimensionInRawValues(op)

    assertEquals(12f, min, 0f)
    assertEquals(42, Utils.idFromNan(max))
    assertEquals(24f, op.min, 0f)
    assertEquals(0f, op.max, 0f)
  }

  private fun setResolved(op: WidthInModifierOperation, min: Float, max: Float) {
    for ((name, value) in listOf("mV1" to min, "mV2" to max)) {
      DimensionInModifierOperation::class
        .java
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .setFloat(op, value)
    }
  }
}
