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
import androidx.compose.remote.core.operations.layout.modifiers.GraphicsLayerModifierOperation
import androidx.compose.remote.core.operations.layout.modifiers.OffsetModifierOperation
import ee.schimke.composeai.rcembedded.player.getValuesReflection
import ee.schimke.composeai.rcembedded.player.offsetRawValues
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutValueSourceTest {
  @Test
  fun offsetKeepsVariableIdsAfterCoreResolvesIt() {
    val op = OffsetModifierOperation(Utils.asNan(41), Utils.asNan(42))
    setFloat(op, "mXValue", 18f)
    setFloat(op, "mYValue", 24f)

    val (x, y) = offsetRawValues(op)

    assertEquals(41, Utils.idFromNan(x))
    assertEquals(42, Utils.idFromNan(y))
    assertEquals(18f, op.x, 0f)
    assertEquals(24f, op.y, 0f)
  }

  @Test
  fun graphicsLayerKeepsTheVariableSourceInsteadOfItsCurrentValue() {
    val operation = GraphicsLayerModifierOperation()
    val valuesField =
      GraphicsLayerModifierOperation::class.java.getDeclaredField("mValues").apply {
        isAccessible = true
      }
    val values = valuesField.get(operation) as Array<*>
    val alpha = requireNotNull(values[GraphicsLayerModifierOperation.ALPHA])
    alpha.javaClass
      .getDeclaredMethod("setValue", Float::class.javaPrimitiveType)
      .apply { isAccessible = true }
      .invoke(alpha, Utils.asNan(43))

    val source = operation.getValuesReflection()[GraphicsLayerModifierOperation.ALPHA].source

    assertEquals(43, Utils.idFromNan(source))
  }

  private fun setFloat(target: Any, name: String, value: Float) {
    target.javaClass.getDeclaredField(name).apply { isAccessible = true }.setFloat(target, value)
  }
}
