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

package ee.schimke.composeai.rcembedded.player

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.core.operations.utilities.AnimatedFloatExpression
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcPlayerUpstreamSyncTest {
  @Test
  fun offsetDensityBehaviorMatchesTheWireUnits() {
    assertEquals(16.dp, rawDimensionDp(16f, CoreDocument.DENSITY_BEHAVIOR_DP, 2.5f))
    assertEquals(10.dp, rawDimensionDp(25f, CoreDocument.DENSITY_BEHAVIOR_PIXELS, 2.5f))
  }

  @Test
  fun preprocessingRecognizesEveryContinuouslyChangingTimeId() {
    val timeIds =
      intArrayOf(
        RemoteContext.ID_CONTINUOUS_SEC,
        RemoteContext.ID_EPOCH_SECOND,
        RemoteContext.ID_TIME_IN_SEC,
        RemoteContext.ID_TIME_IN_MIN,
        RemoteContext.ID_TIME_IN_HR,
      )
    timeIds.forEach { id ->
      assertTrue(
        isExpressionTimeDependent(FloatExpression(id, floatArrayOf(Utils.asNan(id)), null))
      )
    }
    assertFalse(
      isExpressionTimeDependent(
        FloatExpression(
          99,
          floatArrayOf(1f, 2f, AnimatedFloatExpression.ADD),
          null,
        )
      )
    )
  }
}
