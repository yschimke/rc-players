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
package ee.schimke.composeai.rccreation.compose.layout

/**
 * A remote-compatible drawing scope for RemoteCompose that provides access to the content of the
 * component being drawn.
 */
public class RemoteContentDrawScope
internal constructor(
    remoteCanvas: RemoteCanvas,
    private val content: RemoteDrawScope.() -> Unit = { remoteCanvas.drawComponentContent() },
    // LOCAL PATCH (rc-players): the padding a `drawWithContent` sits inside is the fork's
    // `DrawInset`, which `RemoteDrawScope` applies; upstream passes the content size and the
    // offset to this class (`1d18c52eb`). See `DrawWithContentModifier.kt`.
    inset: DrawInset? = null,
) : RemoteDrawScope(remoteCanvas, inset) {

    /** Draws the content of the component. */
    public fun drawContent() {
        // LOCAL PATCH (rc-players): the content is drawn back at the component's origin by the
        // lambda `DrawWithContentModifier` passes, not by translating here.
        content()
    }
}
