/*
 * Copyright 2025 The Android Open Source Project
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

@file:kotlin.jvm.JvmName("RemoteCreationDisplayInfoKt")
@file:kotlin.jvm.JvmMultifileClass

package androidx.compose.remote.creation.compose.capture

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The width of the display in [Dp] units. */
public val RemoteCreationDisplayInfo.widthDp: Dp
    get() = (width / density).dp

/** The height of the display in [Dp] units. */
public val RemoteCreationDisplayInfo.heightDp: Dp
    get() = (height / density).dp

// KMP (rc-players): `rememberRemoteCreationDisplayInfo` is not carried: it reads Android
// resources and the configuration.
