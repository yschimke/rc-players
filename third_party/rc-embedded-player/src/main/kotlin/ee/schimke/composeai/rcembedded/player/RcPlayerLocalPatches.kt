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

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.core.operations.layout.managers.CoreText
import androidx.compose.remote.core.operations.layout.managers.TextLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.constrainWidth
import ee.schimke.composeai.rcembedded.GoogleFontFamilies
import ee.schimke.composeai.rcembedded.player.state.rememberRemoteFloatAsState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

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

/**
 * Match `AndroidPaintContext`'s `StaticLayout` result rather than Compose's stricter line cap.
 *
 * The AndroidX View player only makes `maxLines` truncate a paragraph when an ellipsis mode is
 * selected. Clip/visible paragraphs with more than one requested line keep laying out and are
 * bounded by the component's clip instead. A one-line request stays one line because `CoreText`
 * uses its unwrapped fast path for that case.
 */
internal fun javaPlayerMaxLines(overflow: Int, maxLines: Int): Int =
    if (
        maxLines > 1 &&
            (overflow == CoreText.OVERFLOW_CLIP || overflow == CoreText.OVERFLOW_VISIBLE)
    ) {
        Int.MAX_VALUE
    } else {
        maxLines
    }

/**
 * A spring-animated float, as `remote-creation-compose`'s `RemoteSpringSpec` encodes it: `[0,
 * stiffness, dampingCoefficient, stopThreshold, boundaryMode]`, with `dampingCoefficient` the
 * unit-mass `2 * dampingRatio * sqrt(stiffness)`. `FloatExpression` routes the same shape to
 * `SpringStopEngine`.
 */
internal class RemoteSpring(
    val stiffness: Float,
    val dampingCoefficient: Float,
    val stopThreshold: Float,
) {
    /** The Compose spring with the same motion, converting the coefficient back to a ratio. */
    fun toAnimationSpec(): SpringSpec<Float> =
        spring(
            dampingRatio = dampingCoefficient / (2f * sqrt(stiffness)),
            stiffness = stiffness,
            visibilityThreshold = stopThreshold,
        )
}

/** The spring [animation] encodes, or null for a tween; the same test `FloatExpression` applies. */
internal fun remoteSpringOrNull(animation: FloatArray?): RemoteSpring? =
    if (animation != null && animation.size > 4 && animation[0] == 0f) {
        RemoteSpring(
            stiffness = animation[1],
            dampingCoefficient = animation[2],
            stopThreshold = animation[3],
        )
    } else {
        null
    }

/**
 * Whether this expression animates: a tween (`mFloatAnimation`) or a spring. Upstream tests
 * `mFloatAnimation != null` alone, which the core leaves null for a spring (it builds `mSpring`
 * instead), so a spring-animated value was evaluated as a plain expression.
 */
internal val FloatExpression.isAnimatedExpression: Boolean
    get() = mFloatAnimation != null || remoteSpringOrNull(mSrcAnimation) != null

private val coreTextRawFontSizeField =
    CoreText::class.java.getDeclaredField("mFontSize").apply { isAccessible = true }
private val textLayoutRawFontSizeField =
    TextLayout::class.java.getDeclaredField("mFontSize").apply { isAccessible = true }

/** The font size as the document wrote it: a literal, or a NaN-boxed id. */
internal val CoreText.rawFontSize: Float
    get() = coreTextRawFontSizeField.getFloat(this)

/** The font size as the document wrote it: a literal, or a NaN-boxed id. */
internal val TextLayout.rawFontSize: Float
    get() = textLayoutRawFontSizeField.getFloat(this)

/**
 * The font size to draw at, resolving one the document deferred to the host by id.
 *
 * A `RemoteDensity.Host` capture writes a text's size as an expression over the player's `DENSITY`
 * and `FONT_SIZE` variables. The core resolves it into `mFontSizeValue` in `updateVariables`, but
 * its constructor seeds that field with the same NaN-boxed word, and the text composables read it
 * once, at composition — so the text was laid out at `NaN.sp` and drew nothing. [resolved] is still
 * used for a literal: it carries the core's `DENSITY_BEHAVIOR_DP` scaling, which a deferred size
 * has already applied through `DENSITY`.
 */
@Composable
internal fun rememberTextFontSize(raw: Float, resolved: Float): Float =
    if (Utils.isVariable(raw)) rememberRemoteFloatAsState(raw).value else resolved

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
