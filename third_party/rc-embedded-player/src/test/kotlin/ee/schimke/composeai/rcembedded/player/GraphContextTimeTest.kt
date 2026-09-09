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

import androidx.compose.remote.core.RemoteClock
import androidx.compose.remote.core.RemoteContext
import androidx.compose.runtime.mutableFloatStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphContextTimeTest {
  @Test
  fun continuousSecondsTracksTheComposeFrameClock() {
    val frameTimeMillis = mutableFloatStateOf(1_750f)
    val context =
      GraphContext(
        realState = SnapshotRemoteComposeState(),
        computedOps = emptyMap(),
        timeMillis = frameTimeMillis,
        clock = RemoteClock.SYSTEM,
      )

    assertEquals(1.75f, context.getFloat(RemoteContext.ID_CONTINUOUS_SEC), 0.0001f)

    frameTimeMillis.floatValue = 2_500f

    assertEquals(2.5f, context.getFloat(RemoteContext.ID_CONTINUOUS_SEC), 0.0001f)
  }

  /**
   * The epoch second advances with the frame clock instead of staying at zero.
   *
   * `RcPlayerPreprocess` counts a reference to `ID_EPOCH_SECOND` as making a document
   * time-dependent — `RcPlayerUpstreamSyncTest` pins that, and it matches upstream — so the frame
   * loop runs forever for one. But nothing answered the id: it fell through to the store, which
   * nothing writes, so the expression sat frozen while still costing every frame. The four relative
   * ids above are milliseconds since the document started, which is what the loop publishes; this
   * one is wall clock, so it needs the base the document started at added back on.
   */
  @Test
  fun epochSecondAdvancesWithTheFrameClock() {
    val frameTimeMillis = mutableFloatStateOf(0f)
    val context = graphContext(frameTimeMillis)

    val start = context.getFloat(RemoteContext.ID_EPOCH_SECOND)
    // A real epoch second, not zero: the store answer before this was 0f, which is 1970.
    assertTrue("epoch second should be a wall-clock reading, was $start", start > 1_700_000_000f)

    // Ten minutes of frames later. Ten rather than one because of the resolution below — this
    // asserts the clock MOVES, and one second cannot show that through a Float.
    frameTimeMillis.floatValue = 600_000f

    assertEquals(start + 600f, context.getFloat(RemoteContext.ID_EPOCH_SECOND), 0f)
  }

  /**
   * The float channel quantises, and documents that need the exact second read the integer one.
   *
   * A `Float` carries 24 bits of mantissa, so around 1.79e9 — where epoch seconds are — consecutive
   * representable values are 256 apart. `RemoteContext.getFloat` is `Float`-typed, so this is a
   * property of the wire rather than of this player, and it is why the frame loop seeds
   * `loadInteger(ID_EPOCH_SECOND, …)` as well: the CMP player publishes the same variable as an
   * integer for the same reason. Pinned so the limit is a documented fact rather than something a
   * clock face discovers.
   */
  @Test
  fun theFloatEpochSecondCannotResolveASingleSecond() {
    val frameTimeMillis = mutableFloatStateOf(0f)
    val context = graphContext(frameTimeMillis)

    val start = context.getFloat(RemoteContext.ID_EPOCH_SECOND)
    frameTimeMillis.floatValue = 1_000f

    assertEquals(start, context.getFloat(RemoteContext.ID_EPOCH_SECOND), 0f)
  }

  private fun graphContext(timeMillis: androidx.compose.runtime.State<Float>) =
    GraphContext(
        realState = SnapshotRemoteComposeState(),
        computedOps = emptyMap(),
        timeMillis = timeMillis,
        clock = RemoteClock.SYSTEM,
      )
      .apply { epochBaseMillis = EPOCH_BASE_MILLIS }

  private companion object {
    /** A fixed wall clock, so an assertion is a number rather than a range. */
    const val EPOCH_BASE_MILLIS = 1_787_243_445_250L
  }
}
