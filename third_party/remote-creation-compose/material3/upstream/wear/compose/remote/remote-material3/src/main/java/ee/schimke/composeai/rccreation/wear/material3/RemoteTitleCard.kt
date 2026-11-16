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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import ee.schimke.composeai.rccreation.compose.action.RemoteAction
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemotePaddingValues
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.painter.RemotePainter
import ee.schimke.composeai.rccreation.compose.shapes.RemoteShape
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.rb
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf

/**
 * Opinionated Wear Material 3 [RemoteCard] that offers a specific layout to show interactive
 * information about an application, e.g. a message.
 *
 * Example of a [RemoteTitleCard] with a title, timestamp, and body content:
 *
 * @sample ee.schimke.composeai.rccreation.wear.material3.samples.RemoteTitleCardSample
 * @param onClick Will be called when the user clicks the card
 * @param title A slot for displaying the title of the card
 * @param modifier Modifier to be applied to the card
 * @param time An optional slot for displaying the time relevant to the contents of the card, or
 *   `null` to omit the time.
 * @param subtitle An optional slot for displaying the subtitle of the card, or `null` to omit the
 *   subtitle.
 * @param enabled Controls the enabled state of the card. When false, this component will not
 *   respond to user input
 * @param shape Defines the card's shape.
 * @param colors [RemoteCardColors] that will be used to resolve the colors used for this card.
 * @param border A [RemoteBorderStroke] used for drawing the card's outline border, or `null` for no
 *   border.
 * @param contentPadding The spacing values to apply internally between the container and the
 *   content
 * @param content The optional body content of the card, or `null` to omit body content.
 */
@RemoteComposable
@Composable
public fun RemoteTitleCard(
    onClick: RemoteAction,
    title: @Composable @RemoteComposable () -> Unit,
    modifier: RemoteModifier = RemoteModifier,
    time: (@Composable @RemoteComposable () -> Unit)? = null,
    subtitle: (@Composable @RemoteComposable () -> Unit)? = null,
    enabled: RemoteBoolean = true.rb,
    shape: RemoteShape = RemoteCardDefaults.shape,
    colors: RemoteCardColors = RemoteCardDefaults.cardColors(),
    border: RemoteBorderStroke? = null,
    contentPadding: RemotePaddingValues = RemoteCardDefaults.ContentPadding,
    content: (@Composable @RemoteComposable () -> Unit)? = null,
) {
    val timeWithTextStyle: @Composable @RemoteComposable () -> Unit = {
        time?.let {
            CompositionLocalProvider(
                LocalRemoteContentColor provides colors.timeColor,
                LocalRemoteTextStyle provides RemoteTitleCardTokens.TimeTypography,
                content = time,
            )
        }
    }

    RemoteCardImpl(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        enabled = enabled,
        border = border,
        contentPadding = contentPadding,
        shape = shape,
    ) {
        if (content == null && time != null) {
            timeWithTextStyle()
            RemoteBox(modifier = RemoteModifier.height(4.rdp))
        }
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            verticalAlignment = RemoteAlignment.Top,
        ) {
            RemoteRow(modifier = RemoteModifier.weight(1f.rf)) {
                CompositionLocalProvider(
                    LocalRemoteContentColor provides colors.titleColor,
                    LocalRemoteTextStyle provides RemoteTitleCardTokens.TitleTypography,
                    content = title,
                )
            }
            if (content != null && time != null) {
                RemoteBox(modifier = RemoteModifier.width(4.rdp))
                timeWithTextStyle()
            }
        }
        content?.let {
            RemoteBox(modifier = RemoteModifier.height(2.rdp))
            CompositionLocalProvider(
                LocalRemoteContentColor provides colors.contentColor,
                LocalRemoteTextStyle provides RemoteCardTokens.ContentTypography,
                content = content,
            )
        }
        subtitle?.let {
            RemoteBox(
                modifier =
                    RemoteModifier.height(if (time == null && content == null) 2.rdp else 6.rdp),
            )
            CompositionLocalProvider(
                LocalRemoteContentColor provides colors.subtitleColor,
                LocalRemoteTextStyle provides RemoteTitleCardTokens.SubtitleTypography,
                content = subtitle,
            )
        }
    }
}

/**
 * Opinionated Wear Material 3 [RemoteCard] that offers a specific layout to show interactive
 * information about an application, e.g. a message, with a background image.
 *
 * An Image background is a means to reinforce the meaning of information in a Card, e.g. to help to
 * contextualize the information. Cards should have a content color that contrasts with the
 * background image and scrim. This [RemoteTitleCard] takes [containerPainter] for the container
 * image background to be drawn (the [RemoteCardColors] containerColor property is ignored). It is
 * recommended to use [RemoteCardDefaults.containerPainter] to create the painter so that a scrim is
 * drawn on top of the container image, ensuring that any content above the background is legible.
 *
 * Example of a [RemoteTitleCard] with a background image painter:
 *
 * @sample ee.schimke.composeai.rccreation.wear.material3.samples.RemoteTitleCardWithImageSample
 * @param onClick Will be called when the user clicks the card
 * @param containerPainter The [RemotePainter] to use to draw the container image of the
 *   [RemoteTitleCard], such as returned by [RemoteCardDefaults.containerPainter].
 * @param title A slot for displaying the title of the card
 * @param modifier Modifier to be applied to the card
 * @param time An optional slot for displaying the time relevant to the contents of the card, or
 *   `null` to omit the time.
 * @param subtitle An optional slot for displaying the subtitle of the card, or `null` to omit the
 *   subtitle.
 * @param enabled Controls the enabled state of the card. When false, this component will not
 *   respond to user input
 * @param shape Defines the card's shape.
 * @param colors [RemoteCardColors] that will be used to resolve the colors used for this card.
 * @param border A [RemoteBorderStroke] used for drawing the card's outline border, or `null` for no
 *   border.
 * @param contentPadding The spacing values to apply internally between the container and the
 *   content
 * @param content The optional body content of the card, or `null` to omit body content.
 */
@RemoteComposable
@Composable
public fun RemoteTitleCard(
    onClick: RemoteAction,
    containerPainter: RemotePainter,
    title: @Composable @RemoteComposable () -> Unit,
    modifier: RemoteModifier = RemoteModifier,
    time: (@Composable @RemoteComposable () -> Unit)? = null,
    subtitle: (@Composable @RemoteComposable () -> Unit)? = null,
    enabled: RemoteBoolean = true.rb,
    shape: RemoteShape = RemoteCardDefaults.shape,
    colors: RemoteCardColors = RemoteCardDefaults.cardWithContainerPainterColors(),
    border: RemoteBorderStroke? = null,
    contentPadding: RemotePaddingValues = RemoteCardDefaults.CardWithContainerPainterContentPadding,
    content: (@Composable @RemoteComposable () -> Unit)? = null,
) {
    val timeWithTextStyle: @Composable @RemoteComposable () -> Unit = {
        time?.let {
            CompositionLocalProvider(
                LocalRemoteContentColor provides colors.timeColor,
                LocalRemoteTextStyle provides RemoteTitleCardTokens.TimeTypography,
                content = time,
            )
        }
    }

    RemoteCardImpl(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        enabled = enabled,
        containerPainter = containerPainter,
        border = border,
        contentPadding = contentPadding,
        shape = shape,
    ) {
        if (content == null && time != null) {
            timeWithTextStyle()
            RemoteBox(modifier = RemoteModifier.height(4.rdp))
        }
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            verticalAlignment = RemoteAlignment.Top,
        ) {
            RemoteRow(modifier = RemoteModifier.weight(1f.rf)) {
                CompositionLocalProvider(
                    LocalRemoteContentColor provides colors.titleColor,
                    LocalRemoteTextStyle provides RemoteTitleCardTokens.TitleTypography,
                    content = title,
                )
            }
            if (content != null && time != null) {
                RemoteBox(modifier = RemoteModifier.width(4.rdp))
                timeWithTextStyle()
            }
        }
        content?.let {
            RemoteBox(modifier = RemoteModifier.height(2.rdp))
            CompositionLocalProvider(
                LocalRemoteContentColor provides colors.contentColor,
                LocalRemoteTextStyle provides RemoteCardTokens.ContentTypography,
                content = content,
            )
        }
        subtitle?.let {
            RemoteBox(
                modifier =
                    RemoteModifier.height(if (time == null && content == null) 2.rdp else 6.rdp),
            )
            CompositionLocalProvider(
                LocalRemoteContentColor provides colors.subtitleColor,
                LocalRemoteTextStyle provides RemoteTitleCardTokens.SubtitleTypography,
                content = subtitle,
            )
        }
    }
}

internal object RemoteTitleCardTokens {
    val TimeTypography
        @Composable @RemoteComposable get() = RemoteMaterialTheme.typography.bodyMedium

    val TitleTypography
        @Composable @RemoteComposable get() = RemoteMaterialTheme.typography.titleMedium

    val SubtitleTypography
        @Composable @RemoteComposable get() = RemoteMaterialTheme.typography.labelMedium
}
