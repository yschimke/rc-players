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

package ee.schimke.composeai.rccreation.compose.modifier

import ee.schimke.composeai.rccreation.common.RemoteModifierOperation
import ee.schimke.composeai.rccreation.compose.layout.DrawInset
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteContentDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.state.RemoteStateScope

/**
 * Creates a [RemoteModifier] that allows drawing with the component's content.
 *
 * @param onDraw The drawing block that provides access to [RemoteContentDrawScope].
 * @return a [RemoteModifier] that draws using [onDraw]
 */
public fun RemoteModifier.drawWithContent(
    onDraw: RemoteContentDrawScope.() -> Unit,
): RemoteModifier = then(DrawWithContentModifier(onDraw))

internal class DrawWithContentModifier(val onDraw: RemoteContentDrawScope.() -> Unit) :
    RemoteModifier.Element {
    // KMP (rc-players): `RemoteWriter` takes a `RemoteModifierOperation`, where upstream builds
    // `remote-creation-core`'s `RecordingModifier.Element`.
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation =
        RemoteModifierOperation.DrawContent
}

internal fun RemoteModifier.hasDrawWithContent(): Boolean = any { it is DrawWithContentModifier }

/**
 * Runs every draw-with-content modifier in order, nesting each modifier's content lambda.
 *
 * LOCAL PATCH (rc-players): a draw modifier after padding draws inside it, as AndroidX writes it:
 * translated past the padding before it (resolved for [scope]'s layout direction and density
 * behavior), its scope's size reduced by it, and its content drawn back at the component's own
 * origin.
 */
internal fun RemoteModifier.drawWithContent(
    scope: RemoteStateScope,
    remoteCanvas: RemoteCanvas,
    content: RemoteDrawScope.() -> Unit,
): Boolean {
    val modifiers = mutableListOf<Pair<DrawWithContentModifier, DrawInset?>>()
    var inset: DrawInset? = null
    foldIn(Unit) { _, element ->
        when (element) {
            is PaddingModifier -> {
                val left = element.resolveLeft(scope)
                val top = element.resolveTop(scope)
                val right = element.resolveRight(scope)
                val bottom = element.resolveBottom(scope)
                inset =
                    inset?.let {
                        DrawInset(
                            it.left + left,
                            it.top + top,
                            it.horizontal + left + right,
                            it.vertical + top + bottom,
                        )
                    } ?: DrawInset(left, top, left + right, top + bottom)
            }
            is DrawWithContentModifier -> modifiers += element to inset
            else -> {}
        }
    }
    if (modifiers.isEmpty()) return false

    fun drawAt(index: Int) {
        if (index == modifiers.size) {
            RemoteDrawScope(remoteCanvas).content()
            return
        }
        val (modifier, padding) = modifiers[index]
        if (padding == null) {
            modifier.onDraw(RemoteContentDrawScope(remoteCanvas, { drawAt(index + 1) }))
            return
        }
        remoteCanvas.save()
        remoteCanvas.translate(padding.left, padding.top)
        modifier.onDraw(
            RemoteContentDrawScope(
                remoteCanvas,
                {
                    remoteCanvas.translate(-padding.left, -padding.top)
                    drawAt(index + 1)
                    remoteCanvas.translate(padding.left, padding.top)
                },
                padding,
            ),
        )
        remoteCanvas.restore()
    }
    drawAt(0)
    return true
}
