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

// LOCAL PATCH (rc-players): authoring for 2D vertex meshes, after AndroidX's `RcMesh` DSL
// (remote-creation-core `dsl/RcMesh.kt`), in this port's remote types.

package androidx.compose.remote.creation.compose.layout

import androidx.annotation.RestrictTo
import androidx.compose.remote.creation.common.AnimatedFloatExpression
import androidx.compose.remote.creation.common.Mesh2D
import androidx.compose.remote.creation.compose.capture.DrawMesh2DWriterOp
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteImageBitmap
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.ui.graphics.Color

/**
 * The domain a mesh's `(u, v)` parameters live in. It decides how `(u, v)` is sampled, how the
 * vertices are wired into triangles, and the default geometry when no position is given: the unit
 * square for [Grid] and [Strip], the unit circle at the origin for the radial ones.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class RemoteMeshLayout(internal val value: Int) {
    /** u across, v down. Gradient fields, warps, cloth. */
    Grid(Mesh2D.LAYOUT_GRID),
    /** u is an angle over a full turn, v the radius 0..1. Radial gradients, sunbursts. */
    Polar(Mesh2D.LAYOUT_POLAR),
    /** u is an angle, v crosses the band from radius 0.5 to 1. Gauges, donuts. */
    Ring(Mesh2D.LAYOUT_RING),
    /** u along, v across. Ribbons. */
    Strip(Mesh2D.LAYOUT_STRIP),
    /** u is an angle about a shared centre vertex. Pie wedges, cones of light. */
    Fan(Mesh2D.LAYOUT_FAN),
}

/**
 * The channels of a parametric 2D mesh, each a [RemoteFloat] expression over the domain parameters
 * [u] and [v] (0..1) and anything else the document has — `RemoteTimeVariables.animationTime`, a
 * component's size, a named float. The player evaluates them per vertex, every frame, so a mesh
 * animates without recomposition.
 *
 * Every channel is optional, and absent is not zero: an absent position takes the layout's default
 * geometry, absent texture coordinates are `(u, v)`, and with no colour channel at all the mesh is
 * drawn in the paint's colour. Once any colour channel is set, the unset ones default to 1.
 *
 * A channel compiles to at most 32 expression words. Work that does not depend on [u] or [v] — a
 * phase from the animation clock, say — is cheapest as its own `RemoteFloat`, which a channel then
 * reads by id.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteMesh2DScope internal constructor() {
    /** The first domain parameter, 0..1. Its meaning comes from the layout. */
    public val u: RemoteFloat = RemoteFloat(AnimatedFloatExpression.VAR1)

    /** The second domain parameter, 0..1. Its meaning comes from the layout. */
    public val v: RemoteFloat = RemoteFloat(AnimatedFloatExpression.VAR2)

    /** Vertex x, or null for the layout's default geometry. */
    public var x: RemoteFloat? = null

    /** Vertex y, or null for the layout's default geometry. */
    public var y: RemoteFloat? = null

    /** Texture u, 0..1 across the image, or null for [u]. */
    public var texU: RemoteFloat? = null

    /** Texture v, 0..1 down the image, or null for [v]. */
    public var texV: RemoteFloat? = null

    /** Vertex alpha, 0..1. */
    public var alpha: RemoteFloat? = null

    /** Vertex red, 0..1. */
    public var red: RemoteFloat? = null

    /** Vertex green, 0..1. */
    public var green: RemoteFloat? = null

    /** Vertex blue, 0..1. */
    public var blue: RemoteFloat? = null

    /** Sets [x] and [y]. */
    public fun xy(x: RemoteFloat, y: RemoteFloat) {
        this.x = x
        this.y = y
    }

    /** Sets [texU] and [texV]. */
    public fun texUv(u: RemoteFloat, v: RemoteFloat) {
        texU = u
        texV = v
    }

    /** Sets all four colour channels. */
    public fun rgba(red: RemoteFloat, green: RemoteFloat, blue: RemoteFloat, alpha: RemoteFloat) {
        this.red = red
        this.green = green
        this.blue = blue
        this.alpha = alpha
    }

    /** Sets the colour channels to opaque [red], [green] and [blue]. */
    public fun rgb(red: RemoteFloat, green: RemoteFloat, blue: RemoteFloat) {
        rgba(red, green, blue, 1f.rf)
    }

    /** Sets every vertex to [color], which may itself be an expression. */
    public fun color(color: RemoteColor) {
        rgba(color.red, color.green, color.blue, color.alpha)
    }

    /** Sets every vertex to the constant [color]. */
    public fun color(color: Color) {
        rgba(color.red.rf, color.green.rf, color.blue.rf, color.alpha.rf)
    }

    internal fun channels(): List<RemoteFloat?> = listOf(x, y, texU, texV, alpha, red, green, blue)
}

/**
 * Draws a 2D vertex mesh: a [uCount] × [vCount] lattice over the [layout]'s domain, each vertex
 * placed and coloured by the [channels] expressions, and the triangles between them filled with the
 * colours interpolated across them — a mesh gradient, when the colours vary.
 *
 * ```
 * remoteCanvas.drawMesh2D(uCount = 3, vCount = 3) {
 *     xy(u * width, v * height)
 *     rgb(red = u, green = 0.3f.rf, blue = 1f.rf - u)
 * }
 * ```
 *
 * With an [image] the mesh is textured, its texture coordinates sampling the image's 0..1 extent
 * and the vertex colour, when there is one, multiplying the texel. [paint] supplies the colour of a
 * mesh without colour channels, and the alpha, blend mode and colour filter of any mesh.
 * [antialiasWidth] grows a skirt that wide that fades the mesh's edges out (players that implement
 * it; it needs a colour channel).
 *
 * Meshes are an experimental Remote Compose operation (`AddMesh2D` / `DrawMesh2D`): the document is
 * written with the experimental profile, which players without them refuse.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteCanvas.drawMesh2D(
    uCount: Int,
    vCount: Int,
    layout: RemoteMeshLayout = RemoteMeshLayout.Grid,
    image: RemoteImageBitmap? = null,
    paint: RemotePaint? = null,
    antialiasWidth: RemoteFloat? = null,
    channels: RemoteMesh2DScope.() -> Unit,
) {
    require(uCount >= 1 && vCount >= 1) { "A mesh needs at least one sample along u and v" }
    require(uCount.toLong() * vCount.toLong() <= Mesh2D.MAX_GRID) {
        "A ${uCount}x$vCount mesh exceeds AddMesh2D's ${Mesh2D.MAX_GRID} vertices"
    }
    val scope = RemoteMesh2DScope().apply(channels)
    val op =
        internalCanvas.recordRenderingOp(
            paint,
            DrawMesh2DWriterOp(
                layout.value,
                uCount,
                vCount,
                scope.channels(),
                image,
                antialiasWidth,
            ),
        )
    // The channels are deliberately not roots: a root can be hoisted into a variable of its own,
    // and one that reads u or v means nothing outside the mesh.
    internalCanvas.buffer.addRoots(op, image, antialiasWidth)
}

/** [RemoteCanvas.drawMesh2D] in a draw scope, where `width` and `height` size the mesh. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RemoteDrawScope.drawMesh2D(
    uCount: Int,
    vCount: Int,
    layout: RemoteMeshLayout = RemoteMeshLayout.Grid,
    image: RemoteImageBitmap? = null,
    paint: RemotePaint? = null,
    antialiasWidth: RemoteFloat? = null,
    channels: RemoteMesh2DScope.() -> Unit,
) {
    remoteCanvas.drawMesh2D(uCount, vCount, layout, image, paint, antialiasWidth, channels)
}
