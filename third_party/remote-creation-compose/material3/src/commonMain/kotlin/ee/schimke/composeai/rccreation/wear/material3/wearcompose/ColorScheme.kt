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

package ee.schimke.composeai.rccreation.wear.material3.wearcompose

import androidx.compose.ui.graphics.Color

// Wear Compose Material 3's `ColorScheme`: its colour roles and their default (dark) values, which
// `RemoteColorScheme` reads as each role's fallback.

/**
 * A Wear Material 3 colour scheme: the colour of each role, by default Wear Material 3's baseline.
 */
public class ColorScheme(
    public val primary: Color = ColorTokens.Primary,
    public val primaryDim: Color = ColorTokens.PrimaryDim,
    public val primaryContainer: Color = ColorTokens.PrimaryContainer,
    public val onPrimary: Color = ColorTokens.OnPrimary,
    public val onPrimaryContainer: Color = ColorTokens.OnPrimaryContainer,
    public val secondary: Color = ColorTokens.Secondary,
    public val secondaryDim: Color = ColorTokens.SecondaryDim,
    public val secondaryContainer: Color = ColorTokens.SecondaryContainer,
    public val onSecondary: Color = ColorTokens.OnSecondary,
    public val onSecondaryContainer: Color = ColorTokens.OnSecondaryContainer,
    public val tertiary: Color = ColorTokens.Tertiary,
    public val tertiaryDim: Color = ColorTokens.TertiaryDim,
    public val tertiaryContainer: Color = ColorTokens.TertiaryContainer,
    public val onTertiary: Color = ColorTokens.OnTertiary,
    public val onTertiaryContainer: Color = ColorTokens.OnTertiaryContainer,
    public val surfaceContainerLow: Color = ColorTokens.SurfaceContainerLow,
    public val surfaceContainer: Color = ColorTokens.SurfaceContainer,
    public val surfaceContainerHigh: Color = ColorTokens.SurfaceContainerHigh,
    public val onSurface: Color = ColorTokens.OnSurface,
    public val onSurfaceVariant: Color = ColorTokens.OnSurfaceVariant,
    public val outline: Color = ColorTokens.Outline,
    public val outlineVariant: Color = ColorTokens.OutlineVariant,
    public val background: Color = ColorTokens.Background,
    public val onBackground: Color = ColorTokens.OnBackground,
    public val error: Color = ColorTokens.Error,
    public val errorDim: Color = ColorTokens.ErrorDim,
    public val errorContainer: Color = ColorTokens.ErrorContainer,
    public val onError: Color = ColorTokens.OnError,
    public val onErrorContainer: Color = ColorTokens.OnErrorContainer,
)
