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

package ee.schimke.composeai.rccreation.wear.material3

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import ee.schimke.composeai.rccreation.compose.text.RemoteTextStyle
import ee.schimke.composeai.rccreation.compose.text.fromTextStyle
import ee.schimke.composeai.rccreation.compose.text.toRemoteFontVariationSettings
import ee.schimke.composeai.rccreation.wear.material3.wearcompose.TypeScaleTokens
import ee.schimke.composeai.rccreation.wear.material3.wearcompose.TypographyVariableFontsTokens

internal object RemoteTypographyTokens {
    // KMP (rc-players): upstream converts Wear Compose's default `Typography()`, which is Android's
    // alone and whose fonts (`DeviceFontFamilyName` with variation settings) only Android reads.
    // Each style here is what that conversion gives on Android, built from the same Wear Compose
    // tokens: the token's weight, size, line height and tracking, Wear's default `"pnum"`, its
    // variable-font axes, and no font family (a device font converts to none).
    private fun tokenStyle(
        weight: Float,
        size: TextUnit,
        lineHeight: TextUnit,
        tracking: TextUnit,
        axes: FontVariation.Settings,
    ): RemoteTextStyle =
        RemoteTextStyle.fromTextStyle(
                TextStyle(
                    fontWeight = FontWeight(weight.toInt()),
                    fontSize = size,
                    lineHeight = lineHeight,
                    letterSpacing = tracking,
                    fontFeatureSettings = "pnum",
                ),
            )
            .copy(fontVariationSettings = axes.toRemoteFontVariationSettings())

    val DisplayLarge =
        tokenStyle(
            TypeScaleTokens.DisplayLargeWeight,
            TypeScaleTokens.DisplayLargeSize,
            TypeScaleTokens.DisplayLargeLineHeight,
            TypeScaleTokens.DisplayLargeTracking,
            TypographyVariableFontsTokens.DisplayLargeVariationSettings,
        )
    val DisplayMedium =
        tokenStyle(
            TypeScaleTokens.DisplayMediumWeight,
            TypeScaleTokens.DisplayMediumSize,
            TypeScaleTokens.DisplayMediumLineHeight,
            TypeScaleTokens.DisplayMediumTracking,
            TypographyVariableFontsTokens.DisplayMediumVariationSettings,
        )
    val DisplaySmall =
        tokenStyle(
            TypeScaleTokens.DisplaySmallWeight,
            TypeScaleTokens.DisplaySmallSize,
            TypeScaleTokens.DisplaySmallLineHeight,
            TypeScaleTokens.DisplaySmallTracking,
            TypographyVariableFontsTokens.DisplaySmallVariationSettings,
        )

    val TitleLarge =
        tokenStyle(
            TypeScaleTokens.TitleLargeWeight,
            TypeScaleTokens.TitleLargeSize,
            TypeScaleTokens.TitleLargeLineHeight,
            TypeScaleTokens.TitleLargeTracking,
            TypographyVariableFontsTokens.TitleLargeVariationSettings,
        )
    val TitleMedium =
        tokenStyle(
            TypeScaleTokens.TitleMediumWeight,
            TypeScaleTokens.TitleMediumSize,
            TypeScaleTokens.TitleMediumLineHeight,
            TypeScaleTokens.TitleMediumTracking,
            TypographyVariableFontsTokens.TitleMediumVariationSettings,
        )
    val TitleSmall =
        tokenStyle(
            TypeScaleTokens.TitleSmallWeight,
            TypeScaleTokens.TitleSmallSize,
            TypeScaleTokens.TitleSmallLineHeight,
            TypeScaleTokens.TitleSmallTracking,
            TypographyVariableFontsTokens.TitleSmallVariationSettings,
        )

    val LabelLarge =
        tokenStyle(
            TypeScaleTokens.LabelLargeWeight,
            TypeScaleTokens.LabelLargeSize,
            TypeScaleTokens.LabelLargeLineHeight,
            TypeScaleTokens.LabelLargeTracking,
            TypographyVariableFontsTokens.LabelLargeVariationSettings,
        )
    val LabelMedium =
        tokenStyle(
            TypeScaleTokens.LabelMediumWeight,
            TypeScaleTokens.LabelMediumSize,
            TypeScaleTokens.LabelMediumLineHeight,
            TypeScaleTokens.LabelMediumTracking,
            TypographyVariableFontsTokens.LabelMediumVariationSettings,
        )
    val LabelSmall =
        tokenStyle(
            TypeScaleTokens.LabelSmallWeight,
            TypeScaleTokens.LabelSmallSize,
            TypeScaleTokens.LabelSmallLineHeight,
            TypeScaleTokens.LabelSmallTracking,
            TypographyVariableFontsTokens.LabelSmallVariationSettings,
        )

    val BodyLarge =
        tokenStyle(
            TypeScaleTokens.BodyLargeWeight,
            TypeScaleTokens.BodyLargeSize,
            TypeScaleTokens.BodyLargeLineHeight,
            TypeScaleTokens.BodyLargeTracking,
            TypographyVariableFontsTokens.BodyLargeVariationSettings,
        )
    val BodyMedium =
        tokenStyle(
            TypeScaleTokens.BodyMediumWeight,
            TypeScaleTokens.BodyMediumSize,
            TypeScaleTokens.BodyMediumLineHeight,
            TypeScaleTokens.BodyMediumTracking,
            TypographyVariableFontsTokens.BodyMediumVariationSettings,
        )
    val BodySmall =
        tokenStyle(
            TypeScaleTokens.BodySmallWeight,
            TypeScaleTokens.BodySmallSize,
            TypeScaleTokens.BodySmallLineHeight,
            TypeScaleTokens.BodySmallTracking,
            TypographyVariableFontsTokens.BodySmallVariationSettings,
        )
    val BodyExtraSmall =
        tokenStyle(
            TypeScaleTokens.BodyExtraSmallWeight,
            TypeScaleTokens.BodyExtraSmallSize,
            TypeScaleTokens.BodyExtraSmallLineHeight,
            TypeScaleTokens.BodyExtraSmallTracking,
            TypographyVariableFontsTokens.BodyExtraSmallVariationSettings,
        )

    val NumeralExtraLarge =
        tokenStyle(
            TypeScaleTokens.NumeralExtraLargeWeight,
            TypeScaleTokens.NumeralExtraLargeSize,
            TypeScaleTokens.NumeralExtraLargeLineHeight,
            TypeScaleTokens.NumeralExtraLargeTracking,
            TypographyVariableFontsTokens.NumeralExtraLargeVariationSettings,
        )
    val NumeralLarge =
        tokenStyle(
            TypeScaleTokens.NumeralLargeWeight,
            TypeScaleTokens.NumeralLargeSize,
            TypeScaleTokens.NumeralLargeLineHeight,
            TypeScaleTokens.NumeralLargeTracking,
            TypographyVariableFontsTokens.NumeralLargeVariationSettings,
        )
    val NumeralMedium =
        tokenStyle(
            TypeScaleTokens.NumeralMediumWeight,
            TypeScaleTokens.NumeralMediumSize,
            TypeScaleTokens.NumeralMediumLineHeight,
            TypeScaleTokens.NumeralMediumTracking,
            TypographyVariableFontsTokens.NumeralMediumVariationSettings,
        )
    val NumeralSmall =
        tokenStyle(
            TypeScaleTokens.NumeralSmallWeight,
            TypeScaleTokens.NumeralSmallSize,
            TypeScaleTokens.NumeralSmallLineHeight,
            TypeScaleTokens.NumeralSmallTracking,
            TypographyVariableFontsTokens.NumeralSmallVariationSettings,
        )
    val NumeralExtraSmall =
        tokenStyle(
            TypeScaleTokens.NumeralExtraSmallWeight,
            TypeScaleTokens.NumeralExtraSmallSize,
            TypeScaleTokens.NumeralExtraSmallLineHeight,
            TypeScaleTokens.NumeralExtraSmallTracking,
            TypographyVariableFontsTokens.NumeralExtraSmallVariationSettings,
        )
}
