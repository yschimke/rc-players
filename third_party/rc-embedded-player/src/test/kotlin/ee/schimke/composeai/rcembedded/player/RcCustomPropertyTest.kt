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
import androidx.compose.remote.core.operations.layout.managers.Custom
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RcCustomPropertyTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun integerAndColorIdsAreObservedReactively() {
    val state = SnapshotRemoteComposeState()
    val document = CoreDocument().also { it.setRemoteComposeState(state) }
    state.updateInteger(INT_VALUE_ID, 7)
    state.updateColor(COLOR_VALUE_ID, Color.Red.toArgb())
    val component =
      RcCustomComponent(
        config = "test",
        componentId = 1,
        rawProperties =
          listOf(
            Custom.CustomProperty(1, Custom.CustomProperty.INT_ID_PROP, INT_VALUE_ID),
            Custom.CustomProperty(2, Custom.CustomProperty.COLOR_ID_PROP, COLOR_VALUE_ID),
          ),
        remoteContext = object : StoreBackedRemoteContext(RemoteClock.SYSTEM) {},
      )
    var observedInt = 0
    var observedColor = Color.Unspecified

    rule.setContent {
      CompositionLocalProvider(LocalCoreDocument provides document) {
        observedInt = component.intState(IntProperty(1)).value
        observedColor = component.colorState(ColorProperty(2)).value
      }
    }
    rule.waitForIdle()
    assertEquals(7, observedInt)
    assertEquals(Color.Red, observedColor)

    rule.runOnIdle {
      state.updateInteger(INT_VALUE_ID, 9)
      state.updateColor(COLOR_VALUE_ID, Color.Blue.toArgb())
    }
    rule.waitForIdle()
    assertEquals(9, observedInt)
    assertEquals(Color.Blue, observedColor)
  }

  private companion object {
    const val INT_VALUE_ID = 100
    const val COLOR_VALUE_ID = 101
  }
}
