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

// LOCAL PATCH (rc-players): everything in this file is local to this copy — helpers for the
// patches marked `LOCAL PATCH` in the upstream files. It has no upstream counterpart; see
// PROVENANCE.md for each patch and its upstream report.

package ee.schimke.composeai.rcembedded.player

import androidx.compose.remote.core.RemoteContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.constrainWidth
import ee.schimke.composeai.rcembedded.GoogleFontFamilies
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The document's font-variation axes as `(tag, value)` pairs, empty when it declares none.
 *
 * Tags and values are positional, so an axis counts only when both halves are present. Kept as
 * pairs rather than a `FontVariation.Settings` because [GoogleFontFamilies] caches on them.
 */
internal fun fontVariationAxes(
    axisTagIds: IntArray?,
    axisValues: FloatArray?,
    context: RemoteContext,
): List<Pair<String, Float>> {
    if (axisTagIds == null || axisValues == null) return emptyList()
    return axisTagIds.asList().mapIndexedNotNull { index, tag ->
        val value = axisValues.getOrNull(index) ?: return@mapIndexedNotNull null
        axisName(tag, context)?.let { it to value }
    }
}

/**
 * The axis name a [tag] int stands for, in either encoding the format uses: a text id (a `CoreText`
 * style interns its axis names) or the raw OpenType tag packed into four bytes (the paint bundle's
 * `setTextAxis`, `0x77676874` = `wght`). Anything that is neither is dropped.
 */
private fun axisName(tag: Int, context: RemoteContext): String? =
    context.getText(tag)?.takeIf { it.isNotBlank() }
        ?: CharArray(4) { index -> ((tag shr (24 - index * 8)) and 0xff).toChar() }
            .concatToString()
            .takeIf { name -> name.all { it in '!'..'~' } }

/** The latest layout of one `CoreText`'s paragraph, written by `BasicText` during its measure. */
internal class CoreTextLines {
    var result: TextLayoutResult? = null
}

/**
 * Sizes a `CoreText` node the way the core sizes the component: to the span its lines cover,
 * `ceil(max lineRight) - floor(min lineLeft)` (`AndroidPaintContext.getTightBoundingBox`, which
 * `CoreText.computeWrapSize` takes its width from).
 *
 * `BasicText` reports `ceil(maxIntrinsicWidth)` for a line that fits, and the whole available width
 * for one that wraps. With letter spacing the intrinsic width runs up to a pixel past the line's
 * right edge, so a 198.43 px label measured 200 where the core measures 199, and everything laid
 * out against it — a wrap-width button, its centring — moved with it (#572).
 *
 * A truncated or overflowing run keeps the width it was laid out at: it was cut because it did not
 * fit. The node never goes below the incoming minimum, and never grows.
 */
internal fun Modifier.tightCoreTextWidth(lines: CoreTextLines): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val result = lines.result
        if (result == null || result.lineCount == 0 || result.hasVisualOverflow) {
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        var left = Float.MAX_VALUE
        var right = 0f
        for (line in 0 until result.lineCount) {
            left = minOf(left, result.getLineLeft(line))
            right = maxOf(right, result.getLineRight(line))
        }
        val start = floor(left).toInt()
        val width =
            constraints.constrainWidth(ceil(right).toInt() - start).coerceAtMost(placeable.width)
        if (width == placeable.width) {
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            // The core draws the paragraph at `-bounds.left`, so centred and end-aligned lines
            // keep their place within the narrowed span.
            layout(width, placeable.height) { placeable.place(-start, 0) }
        }
    }
