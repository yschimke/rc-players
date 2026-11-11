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

package androidx.compose.remote.creation.compose.text

import androidx.compose.remote.creation.common.TextFromFloat.PAD_PRE_NONE
import androidx.compose.remote.creation.common.TextFromFloat.PAD_PRE_ZERO
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.RemoteTimeVariables
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.runtime.Composable

/** Default time values backed by the player's clock variables. */
public object RemoteTimeDefaults {
    /**
     * Returns a [RemoteBoolean] indicating whether the time should be displayed in 24-hour format.
     * Currently captured at recording time.
     *
     * @return a [RemoteBoolean] that is `true` when 24-hour format is enabled
     */
    @RemoteComposable
    @Composable
    public fun is24HourFormat(): RemoteBoolean = RemoteBoolean(platformUses24HourTime())

    /**
     * Creates a [RemoteString] representing the current time in either 12-hour or 24-hour format.
     *
     * The 24-hour format is represented as "HH:mm", while the 12-hour format is represented as
     * "h:mm".
     *
     * @param is24HourFormat A [RemoteBoolean] indicating whether to use 24-hour format. Defaults to
     *   the system's current setting at the time of recording.
     * @return A [RemoteString] that evaluates to the formatted time string.
     */
    @RemoteComposable
    @Composable
    public fun defaultTimeString(is24HourFormat: RemoteBoolean = is24HourFormat()): RemoteString {
        val mins =
            (RemoteTimeVariables.minuteOfDay.toRemoteFloat() % 60f).toRemoteStringOptions(
                2,
                0,
                PAD_PRE_ZERO,
            )
        val currentHour = RemoteTimeVariables.hourOfDay.toRemoteFloat()
        val hours24 = currentHour.toRemoteStringOptions(2, 0, PAD_PRE_ZERO)
        val hour12 =
            ((currentHour % 12f).isEqualTo(0.rf)).select(RemoteFloat(12f), currentHour % 12f)
        // Upstream dropped the AM/PM suffix and the leading zero: 12-hour time reads "h:mm".
        val hours12 = hour12.toRemoteStringOptions(2, 0, PAD_PRE_NONE)
        return is24HourFormat.select(hours24 + ":" + mins, hours12 + ":" + mins)
    }
}

@Composable internal expect fun platformUses24HourTime(): Boolean
