package androidx.compose.remote.creation.compose.shapes

import androidx.annotation.RestrictTo
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteLayoutDirection
import androidx.compose.remote.creation.compose.path.RemotePath
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.remotePath
import androidx.compose.remote.creation.compose.state.rf

/**
 * Draws the [RemoteOutline] on a [RemoteDrawScope].
 *
 * @param outline the outline to draw.
 * @param paint the paint used for the drawing.
 */
public fun RemoteDrawScope.drawOutline(outline: RemoteOutline, paint: RemotePaint) {
    when (outline) {
        is RemoteOutline.Rectangle -> {
            drawRect(paint, outline.topLeft, outline.size)
        }
        is RemoteOutline.Rounded -> {
            val corners = resolveRoundedCorners(outline)
            if (corners.isUniform) {
                remoteCanvas.drawRoundRect(
                    left = corners.left,
                    top = corners.top,
                    right = corners.right,
                    bottom = corners.bottom,
                    rx = corners.topLeft,
                    ry = corners.topLeft,
                    paint = paint,
                )
                return
            }
            drawPath(roundedOutlinePath(corners), paint)
        }
        is RemoteOutline.Generic -> {
            val p = genericOutlinePath(outline)
            if (p != null) {
                drawPath(p, paint)
            }
        }
        else -> {}
    }
}

/**
 * Clips the drawing area to the [RemoteOutline] and executes [block] within it.
 *
 * This emits a path clip, so callers must check that the current profile supports it, for example
 * with `currentProfileSupportsPathClip()`. If the outline can't be expressed as a path, [block] is
 * executed without clipping.
 *
 * @param outline the outline to clip to.
 * @param block the drawing operations to execute within the clipped area.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteDrawScope.clipOutline(outline: RemoteOutline, block: RemoteDrawScope.() -> Unit) {
    val path =
        when (outline) {
            is RemoteOutline.Rectangle -> {
                val left = outline.topLeft.x
                val top = outline.topLeft.y
                val right = left + outline.size.width
                val bottom = top + outline.size.height
                remotePath {
                    moveTo(left, top)
                    lineTo(right, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
            }
            is RemoteOutline.Rounded -> roundedOutlinePath(resolveRoundedCorners(outline))
            is RemoteOutline.Generic -> genericOutlinePath(outline)
            else -> null
        }
    if (path != null) {
        clipPath(path, block = block)
    } else {
        block()
    }
}

/** The resolved bounds and layout direction aware corner radii of a [RemoteOutline.Rounded]. */
private class RoundedCorners(
    val left: RemoteFloat,
    val top: RemoteFloat,
    val right: RemoteFloat,
    val bottom: RemoteFloat,
    val topLeft: RemoteFloat,
    val topRight: RemoteFloat,
    val bottomRight: RemoteFloat,
    val bottomLeft: RemoteFloat,
) {
    val isUniform: Boolean
        get() =
            areEqual(topLeft, topRight) &&
                areEqual(topRight, bottomRight) &&
                areEqual(bottomRight, bottomLeft)
}

private fun RemoteDrawScope.resolveRoundedCorners(outline: RemoteOutline.Rounded): RoundedCorners {
    // Compute the bounding rectangle [left, top, right, bottom] from origin `offset`
    // and dimensions `size`. When drawing a centered stroke of width S, `offset` is
    // (S/2, S/2) and `size` is (W - S, H - S), yielding bounds [S/2, S/2, W - S/2, H -
    // S/2].
    val left = outline.offset.x
    val top = outline.offset.y
    val right = left + (outline.size?.width ?: width)
    val bottom = top + (outline.size?.height ?: height)

    // Remap corner radii based on layout direction
    return when (remoteCanvas.remoteLayoutDirection) {
        RemoteLayoutDirection.Ltr ->
            RoundedCorners(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeft = outline.topStart,
                topRight = outline.topEnd,
                bottomRight = outline.bottomEnd,
                bottomLeft = outline.bottomStart,
            )
        RemoteLayoutDirection.Rtl ->
            RoundedCorners(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeft = outline.topEnd,
                topRight = outline.topStart,
                bottomRight = outline.bottomStart,
                bottomLeft = outline.bottomEnd,
            )
    }
}

private fun RemoteDrawScope.roundedOutlinePath(corners: RoundedCorners): RemotePath {
    val left = corners.left
    val top = corners.top
    val right = corners.right
    val bottom = corners.bottom
    val rTopLeft = corners.topLeft
    val rTopRight = corners.topRight
    val rBottomRight = corners.bottomRight
    val rBottomLeft = corners.bottomLeft

    val kappa = 0.55228475f.rf
    val cTopLeft = rTopLeft * kappa
    val cTopRight = rTopRight * kappa
    val cBottomRight = rBottomRight * kappa
    val cBottomLeft = rBottomLeft * kappa

    return remotePath {
        // 1. Move to top edge
        moveTo(left + rTopLeft, top)

        // 2. Top Line & Top-Right Corner
        lineTo(right - rTopRight, top)
        curveTo(
            x1 = right - rTopRight + cTopRight,
            y1 = top,
            x2 = right,
            y2 = top + rTopRight - cTopRight,
            x3 = right,
            y3 = top + rTopRight,
        )

        // 3. Right Line & Bottom-Right Corner
        lineTo(right, bottom - rBottomRight)
        curveTo(
            x1 = right,
            y1 = bottom - rBottomRight + cBottomRight,
            x2 = right - rBottomRight + cBottomRight,
            y2 = bottom,
            x3 = right - rBottomRight,
            y3 = bottom,
        )

        // 4. Bottom Line & Bottom-Left Corner
        lineTo(left + rBottomLeft, bottom)
        curveTo(
            x1 = left + rBottomLeft - cBottomLeft,
            y1 = bottom,
            x2 = left,
            y2 = bottom - rBottomLeft + cBottomLeft,
            x3 = left,
            y3 = bottom - rBottomLeft,
        )

        // 5. Start Line & Top-Left Corner
        lineTo(left, top + rTopLeft)
        curveTo(
            x1 = left,
            y1 = top + rTopLeft - cTopLeft,
            x2 = left + rTopLeft - cTopLeft,
            y2 = top,
            x3 = left + rTopLeft,
            y3 = top,
        )

        // 6. Close the path
        close()
    }
}

private fun RemoteDrawScope.genericOutlinePath(outline: RemoteOutline.Generic): RemotePath? =
    outline.path ?: outline.block?.let { remotePath(it) }

private fun areEqual(a: RemoteFloat, b: RemoteFloat): Boolean =
    a === b ||
        a.cacheKey == b.cacheKey ||
        (a.hasConstantValue && b.hasConstantValue && a.constantValue == b.constantValue)
