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

@file:kotlin.jvm.JvmName("RemoteColorComposeKt")

package androidx.compose.remote.creation.compose.state

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Creates a [RemoteColor] from a literal [Color] value.
 *
 * @param color The [Color] value.
 * @return A [RemoteColor] representing the constant color.
 */
public fun RemoteColor(color: Color): RemoteColor = color.asRemoteColor()

/** Extension property to convert a [Color] to a [RemoteColor]. */
public val Color.rc: RemoteColor
    get() = asRemoteColor()

/**
 * Extension function to convert a [Color] to a [RemoteColor].
 *
 * @return a [RemoteColor] representing this [Color]
 */
public fun Color.asRemoteColor(): RemoteColor = RemoteColor(this.toArgb())

/**
 * Converts this [RemoteColor] to a [Color] if it has a constant value.
 *
 * @return the constant [Color] value of this [RemoteColor]
 */
public fun RemoteColor.asConstantColor(): Color = Color(constantValue)

/**
 * Converts this [RemoteColor] to a [Color] if it has a constant value, or null otherwise.
 *
 * @return the constant [Color] value of this [RemoteColor], or `null` if it is dynamic
 */
public fun RemoteColor.asConstantColorOrNull(): Color? = constantValueOrNull?.let { Color(it) }
