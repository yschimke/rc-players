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
package ee.schimke.composeai.rccreation.foundation.image

import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteContentScale
import ee.schimke.composeai.rccreation.compose.layout.RemoteImage
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteImageBitmap
import ee.schimke.composeai.rccreation.compose.state.RemoteString
import ee.schimke.composeai.rccreation.compose.state.rf

/**
 * Lays out and draws a given [RemoteImageBitmap] in a remote layout.
 *
 * This is the remote equivalent of [androidx.compose.foundation.Image]. Provide a localized
 * [contentDescription] (for example, via `stringResource`) whenever the image conveys meaning; pass
 * `null` only for purely decorative images so accessibility services ignore them.
 *
 * Example of a [RemoteBasicImage] cropped to fill its container:
 *
 * @sample ee.schimke.composeai.rccreation.foundation.samples.RemoteBasicImageSample
 *
 * Example of a [RemoteBasicImage] scaled with [RemoteContentScale.Fit] inside its container:
 *
 * @sample ee.schimke.composeai.rccreation.foundation.samples.RemoteBasicImageFitSample
 * @param bitmap the [RemoteImageBitmap] to draw
 * @param contentDescription localized text used by accessibility services to describe what this
 *   image represents, or `null` if the image is purely decorative and should be ignored by
 *   accessibility services
 * @param modifier the [RemoteModifier] applied to this layout node
 * @param contentScale rule applied to scale the image when its layout bounds differ from the
 *   intrinsic size of [bitmap]. Defaults to [RemoteContentScale.Fit]
 * @param alpha opacity applied to [bitmap] when rendered, from `0.0f` (transparent) to `1.0f`
 *   (opaque). Defaults to `1.0f`
 */
@Composable
@RemoteComposable
public fun RemoteBasicImage(
    bitmap: RemoteImageBitmap,
    contentDescription: RemoteString?,
    modifier: RemoteModifier = RemoteModifier,
    contentScale: RemoteContentScale = RemoteContentScale.Fit,
    alpha: RemoteFloat = 1f.rf,
) {
    RemoteImage(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
    )
}
