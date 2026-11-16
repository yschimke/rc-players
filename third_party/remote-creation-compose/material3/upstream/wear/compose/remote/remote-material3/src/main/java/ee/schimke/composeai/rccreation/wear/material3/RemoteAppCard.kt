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
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemotePaddingValues
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.shapes.RemoteShape
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.rb
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf

/**
 * Opinionated Wear Material 3 [RemoteCard] that offers a specific 5 slot layout to show information
 * about an application, e.g. a notification.
 *
 * Example of a [RemoteAppCard] displaying an application name, timestamp, title, and body:
 *
 * @sample ee.schimke.composeai.rccreation.wear.material3.samples.RemoteAppCardSample
 * @param onClick Will be called when the user clicks the card.
 * @param appName A slot for displaying the application name.
 * @param title A slot for displaying the title of the card.
 * @param modifier Modifier to be applied to the card
 * @param enabled Controls the enabled state of the card. When false, this component will not
 *   respond to user input.
 * @param shape Defines the card's shape.
 * @param colors [RemoteCardColors] that will be used to resolve the colors used for this card.
 * @param border A [RemoteBorderStroke] used for drawing the card's outline border, or `null` for no
 *   border.
 * @param contentPadding The spacing values to apply internally between the container and the
 *   content
 * @param appImage A slot for a small ([RemoteCardDefaults.AppImageSize]) image associated with the
 *   app, or `null` to omit the app image.
 * @param time A slot for displaying the time relevant to the contents of the card, or `null` to
 *   omit the time.
 * @param content The main slot for the content of this card
 */
@RemoteComposable
@Composable
public fun RemoteAppCard(
    onClick: RemoteAction,
    appName: @Composable @RemoteComposable () -> Unit,
    title: @Composable @RemoteComposable () -> Unit,
    modifier: RemoteModifier = RemoteModifier,
    enabled: RemoteBoolean = true.rb,
    shape: RemoteShape = RemoteCardDefaults.shape,
    colors: RemoteCardColors = RemoteCardDefaults.cardColors(),
    border: RemoteBorderStroke? = null,
    contentPadding: RemotePaddingValues = RemoteCardDefaults.ContentPadding,
    appImage: (@Composable @RemoteComposable () -> Unit)? = null,
    time: (@Composable @RemoteComposable () -> Unit)? = null,
    content: @Composable @RemoteComposable () -> Unit,
) {
    RemoteCardImpl(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        enabled = enabled,
        border = border,
        contentPadding = contentPadding,
        shape = shape,
    ) {
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            verticalAlignment = RemoteAlignment.CenterVertically,
            horizontalArrangement = RemoteArrangement.SpaceBetween,
        ) {
            RemoteRow(
                modifier = RemoteModifier.weight(1f.rf),
                verticalAlignment = RemoteAlignment.CenterVertically,
            ) {
                appImage?.let {
                    appImage()
                    RemoteBox(RemoteModifier.width(4.rdp))
                }
                CompositionLocalProvider(
                    LocalRemoteContentColor provides colors.appNameColor,
                    LocalRemoteTextStyle provides RemoteAppCardTokens.AppNameTypography,
                    content = appName,
                )
            }

            time?.let {
                RemoteBox(modifier = RemoteModifier.width(6.rdp))
                CompositionLocalProvider(
                    LocalRemoteContentColor provides colors.timeColor,
                    LocalRemoteTextStyle provides RemoteAppCardTokens.TimeTypography,
                    content = time,
                )
            }
        }
        RemoteBox(modifier = RemoteModifier.height(6.rdp))
        RemoteRow(modifier = RemoteModifier.fillMaxWidth()) {
            CompositionLocalProvider(
                LocalRemoteContentColor provides colors.titleColor,
                LocalRemoteTextStyle provides RemoteAppCardTokens.TitleTypography,
                content = title,
            )
        }
        RemoteBox(modifier = RemoteModifier.height(2.rdp))
        CompositionLocalProvider(
            LocalRemoteContentColor provides colors.contentColor,
            LocalRemoteTextStyle provides RemoteCardTokens.ContentTypography,
            content = content,
        )
    }
}

internal object RemoteAppCardTokens {
    val AppNameTypography
        @Composable @RemoteComposable get() = RemoteMaterialTheme.typography.titleSmall

    val TimeTypography
        @Composable @RemoteComposable get() = RemoteMaterialTheme.typography.bodyMedium

    val TitleTypography
        @Composable @RemoteComposable get() = RemoteMaterialTheme.typography.titleMedium
}
