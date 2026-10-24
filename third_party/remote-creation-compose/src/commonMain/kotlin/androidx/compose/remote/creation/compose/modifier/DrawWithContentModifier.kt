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

package androidx.compose.remote.creation.compose.modifier

import androidx.compose.remote.creation.common.RemoteModifierOperation
import androidx.compose.remote.creation.compose.layout.DrawInset
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteContentDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.state.RemoteStateScope

/**
 * Creates a [RemoteModifier] that allows drawing with the component's content.
 *
 * @param onDraw The drawing block that provides access to [RemoteContentDrawScope].
 */
public fun RemoteModifier.drawWithContent(
    onDraw: RemoteContentDrawScope.() -> Unit
): RemoteModifier = then(DrawWithContentModifier(onDraw))

internal class DrawWithContentModifier(val onDraw: RemoteContentDrawScope.() -> Unit) :
    RemoteModifier.Element {
    override fun RemoteStateScope.toRemoteModifierOperation(): RemoteModifierOperation =
        RemoteModifierOperation.DrawContent
}

internal fun RemoteModifier.hasDrawWithContent(): Boolean = any { it is DrawWithContentModifier }

/**
 * Runs every draw-with-content modifier in order, nesting each modifier's content lambda.
 *
 * LOCAL PATCH (rc-players): a draw modifier after padding draws inside it, as AndroidX writes it:
 * translated past the padding before it, its scope's size reduced by it, and its content drawn back
 * at the component's own origin.
 */
internal fun RemoteModifier.drawWithContent(
    remoteCanvas: RemoteCanvas,
    content: RemoteDrawScope.() -> Unit,
): Boolean {
    val modifiers = mutableListOf<Pair<DrawWithContentModifier, DrawInset?>>()
    var inset: DrawInset? = null
    foldIn(Unit) { _, element ->
        when (element) {
            is PaddingModifier ->
                inset =
                    inset?.let {
                        DrawInset(
                            it.left + element.start,
                            it.top + element.top,
                            it.horizontal + element.start + element.end,
                            it.vertical + element.top + element.bottom,
                        )
                    }
                        ?: DrawInset(
                            element.start,
                            element.top,
                            element.start + element.end,
                            element.top + element.bottom,
                        )
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
            )
        )
        remoteCanvas.restore()
    }
    drawAt(0)
    return true
}
