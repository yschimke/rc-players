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
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The time variables answer the [RemoteClock], not the frame clock.
 *
 * `730322a70ba` reworked `ID_TIME_IN_SEC/MIN/HR` and `ID_EPOCH_SECOND` onto `GraphTimeState`: they
 * are wall-clock readings quantized per second, updated from the frame loop through `updateTime`.
 * Only `ID_ANIMATION_TIME` / `ID_CONTINUOUS_SEC` are continuous, and even the continuous second is
 * wall clock (second + fraction), not time *since the document started* — which is what these tests
 * pin, with a clock whose every reading is a deterministic function of the millis it is asked at.
 */
private fun zoned(millis: Long) =
  ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

class GraphContextTimeTest {

  /** A [RemoteClock] whose readings are pure UTC arithmetic on [baseMillis]. */
  private class FakeClock(private val baseMillis: Long) : RemoteClock {
    override fun millis(): Long = baseMillis

    override fun nanoTime(): Long = baseMillis * 1_000_000L

    override fun getZoneId(): String = "UTC"

    override fun snapshot(millis: Long?): RemoteClock.TimeSnapshot {
      val at = zoned(millis ?: baseMillis)
      return object : RemoteClock.TimeSnapshot {
        override fun getMillis(): Long = millis ?: baseMillis

        override fun getYear(): Int = at.year

        override fun getMonth(): Int = at.monthValue

        override fun getDayOfMonth(): Int = at.dayOfMonth

        override fun getDayOfYear(): Int = at.dayOfYear

        override fun getHour(): Int = at.hour

        override fun getMinute(): Int = at.minute

        override fun getSecond(): Int = at.second

        override fun getMillisOfSecond(): Int = at.nano / 1_000_000

        override fun getDayOfWeek(): Int = at.dayOfWeek.value % 7

        override fun getOffsetSeconds(): Int = 0
      }
    }
  }

  /** A fixed wall clock, so an assertion is a number rather than a range. */
  private val baseMillis = 1_787_243_445_000L

  private fun graphContext(clock: RemoteClock) =
    GraphContext(
      realState = SnapshotRemoteComposeState(),
      computedOps = emptyMap(),
      timeMillis = mutableFloatStateOf(0f),
      clock = clock,
    )

  /** The `timeInSec` reading at [millis] — the same quantity the snapshot's default derives. */
  private fun expectedTimeInSec(millis: Long): Float {
    val at = zoned(millis)
    return (at.minute * 60 + at.second).toFloat()
  }

  @Test
  fun continuousSecondsTrackTheWallClockSecond() {
    val context = graphContext(FakeClock(baseMillis))
    context.updateTime(0f)

    // Second + fraction: exactly on a boundary the fraction is zero.
    assertEquals(
      expectedTimeInSec(baseMillis),
      context.getFloat(RemoteContext.ID_CONTINUOUS_SEC),
      0.0001f,
    )

    // 1.75 s later: a new second (the base is aligned to one), so the continuous second reads the
    // new boundary's timeInSec plus the 750 ms fraction.
    context.updateTime(1_750f)
    assertEquals(
      expectedTimeInSec(baseMillis + 1_000L) + 0.75f,
      context.getFloat(RemoteContext.ID_CONTINUOUS_SEC),
      0.0001f,
    )
    assertEquals(
      zoned(baseMillis + 1_750L).toEpochSecond(),
      context.getInteger(RemoteContext.ID_EPOCH_SECOND).toLong(),
    )
  }

  @Test
  fun epochSecondIsTheWallClockSecondOnBothChannels() {
    val context = graphContext(FakeClock(baseMillis))
    context.updateTime(0f)

    val start = zoned(baseMillis).toEpochSecond()
    assertEquals(start.toInt(), context.getInteger(RemoteContext.ID_EPOCH_SECOND))
    assertEquals(start.toFloat(), context.getFloat(RemoteContext.ID_EPOCH_SECOND), 0f)

    // Ten minutes of frames later. The float channel quantizes to the whole second — it updates
    // when the second changes, so ten whole minutes land exactly on both channels.
    context.updateTime(600_000f)
    assertEquals(start + 600, context.getInteger(RemoteContext.ID_EPOCH_SECOND).toLong())
    assertEquals((start + 600).toFloat(), context.getFloat(RemoteContext.ID_EPOCH_SECOND), 0f)
  }

  @Test
  fun discreteTimeDoesNotUpdateWithinASecond() {
    val context = graphContext(FakeClock(baseMillis))
    context.updateTime(0f)
    val secAtStart = context.getFloat(RemoteContext.ID_TIME_IN_SEC)

    // Half a second of frames: no second boundary crossed, so nothing discrete moves and the
    // update reports false (the frame loop uses that to sleep instead of ticking).
    val moved = context.updateTime(500f, updateContinuous = false)

    assertFalse("a sub-second frame reported a discrete update", moved)
    assertEquals(secAtStart, context.getFloat(RemoteContext.ID_TIME_IN_SEC), 0f)

    // One second of frames, which does cross the boundary: the discrete fields move, and say so.
    val movedAcross = context.updateTime(1_000f, updateContinuous = false)
    assertTrue("crossing a second boundary reported no update", movedAcross)
    assertEquals(
      expectedTimeInSec(baseMillis + 1_000L),
      context.getFloat(RemoteContext.ID_TIME_IN_SEC),
      0.0001f,
    )
  }
}
