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

package ee.schimke.composeai.rccreation.compose.path

import androidx.annotation.RestrictTo
import ee.schimke.composeai.rccreation.common.Utils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Platform-neutral encoded path used by Creation Compose. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemotePath(initialCapacity: Int = DefaultCapacity) {
    private var values = FloatArray(initialCapacity)

    public var size: Int = 0
        private set

    public val pathArray: FloatArray
        get() = values

    private var currentX = 0f
    private var currentY = 0f

    public fun rewind() {
        size = 0
        currentX = 0f
        currentY = 0f
    }

    public fun isEmpty(): Boolean = size == 0

    public fun moveTo(x: Float, y: Float) {
        appendMove(Move, x, y)
        currentX = x
        currentY = y
    }

    public fun rMoveTo(dx: Float, dy: Float) {
        currentX += dx
        currentY += dy
        appendMove(Move, currentX, currentY)
    }

    public fun lineTo(x: Float, y: Float) {
        append(Line, x, y)
        currentX = x
        currentY = y
    }

    public fun rLineTo(dx: Float, dy: Float) {
        currentX += dx
        currentY += dy
        append(Line, currentX, currentY)
    }

    public fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        append(Quadratic, x1, y1, x2, y2)
        currentX = x2
        currentY = y2
    }

    public fun rQuadTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float) {
        append(Quadratic, currentX + dx1, currentY + dy1, currentX + dx2, currentY + dy2)
        currentX += dx2
        currentY += dy2
    }

    public fun conicTo(x1: Float, y1: Float, x2: Float, y2: Float, weight: Float) {
        append(Conic, x1, y1, x2, y2, weight)
        currentX = x2
        currentY = y2
    }

    public fun rConicTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float, weight: Float) {
        append(Conic, currentX + dx1, currentY + dy1, currentX + dx2, currentY + dy2, weight)
        currentX += dx2
        currentY += dy2
    }

    public fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        append(Cubic, x1, y1, x2, y2, x3, y3)
        currentX = x3
        currentY = y3
    }

    public fun rCubicTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float, dx3: Float, dy3: Float) {
        append(
            Cubic,
            currentX + dx1,
            currentY + dy1,
            currentX + dx2,
            currentY + dy2,
            currentX + dx3,
            currentY + dy3,
        )
        currentX += dx3
        currentY += dy3
    }

    public fun close() {
        ensureCapacity(1)
        values[size++] = Utils.asNan(Close)
    }

    /**
     * The path of an SVG path string, as `RemotePath(pathData: String)` reads one on the JVM
     * (`remote-core`'s `RemotePathBase.parsePathData`): the absolute commands `M`, `L`, `H`, `C`,
     * `S`, `Q` and `Z`, and the arcs `A` and `a`, their numbers separated by commas or spaces. Any
     * other command is an [IllegalArgumentException], as it is there.
     */
    public constructor(pathData: String) : this() {
        parsePathData(pathData)
    }

    /**
     * An elliptical arc from the current point to ([x], [y]) as cubics, as `RemotePathBase.arcTo`:
     * radii [rx] and [ry], the ellipse rotated by [angle] degrees.
     */
    public fun arcTo(
        rx: Float,
        ry: Float,
        angle: Float,
        largeArc: Boolean,
        sweep: Boolean,
        x: Float,
        y: Float,
    ) {
        val x0 = currentX
        val y0 = currentY
        if (rx == 0f || ry == 0f) {
            lineTo(x, y)
            return
        }
        val alpha = angle.toDouble() * PI / 180.0
        val cosAlpha = cos(alpha)
        val sinAlpha = sin(alpha)

        val dx = (x0 - x) / 2.0
        val dy = (y0 - y) / 2.0
        val x1p = cosAlpha * dx + sinAlpha * dy
        val y1p = -sinAlpha * dx + cosAlpha * dy

        var rxp = abs(rx).toDouble()
        var ryp = abs(ry).toDouble()
        val check = (x1p * x1p) / (rxp * rxp) + (y1p * y1p) / (ryp * ryp)
        if (check > 1.0) {
            val s = sqrt(check)
            rxp *= s
            ryp *= s
        }

        val sign = if (largeArc == sweep) -1.0 else 1.0
        val numerator = (rxp * rxp * ryp * ryp) - (rxp * rxp * y1p * y1p) - (ryp * ryp * x1p * x1p)
        val denominator = (rxp * rxp * y1p * y1p) + (ryp * ryp * x1p * x1p)
        val root = sqrt(max(0.0, numerator / denominator))
        val cxp = sign * root * rxp * y1p / ryp
        val cyp = -sign * root * ryp * x1p / rxp

        val cx = cosAlpha * cxp - sinAlpha * cyp + (x0 + x) / 2.0
        val cy = sinAlpha * cxp + cosAlpha * cyp + (y0 + y) / 2.0

        val theta1 = atan2((y1p - cyp) / ryp, (x1p - cxp) / rxp)
        var dTheta = atan2((-y1p - cyp) / ryp, (-x1p - cxp) / rxp) - theta1

        if (sweep && dTheta < 0) {
            dTheta += 2 * PI
        } else if (!sweep && dTheta > 0) {
            dTheta -= 2 * PI
        }

        var segments = ceil(abs(dTheta) / (PI / 2.0)).toInt()
        if (segments == 0) {
            segments = 1
        }

        for (i in 0 until segments) {
            val s1 = theta1 + i * dTheta / segments
            val s2 = theta1 + (i + 1) * dTheta / segments

            val t = 4.0 / 3.0 * tan((s2 - s1) / 4.0)

            val xstart = cosAlpha * rxp * cos(s1) - sinAlpha * ryp * sin(s1) + cx
            val ystart = sinAlpha * rxp * cos(s1) + cosAlpha * ryp * sin(s1) + cy

            val xend = cosAlpha * rxp * cos(s2) - sinAlpha * ryp * sin(s2) + cx
            val yend = sinAlpha * rxp * cos(s2) + cosAlpha * ryp * sin(s2) + cy

            val cp1x = xstart + t * (-cosAlpha * rxp * sin(s1) - sinAlpha * ryp * cos(s1))
            val cp1y = ystart + t * (-sinAlpha * rxp * sin(s1) + cosAlpha * ryp * cos(s1))

            val cp2x = xend - t * (-cosAlpha * rxp * sin(s2) - sinAlpha * ryp * cos(s2))
            val cp2y = yend - t * (-sinAlpha * rxp * sin(s2) + cosAlpha * ryp * cos(s2))

            cubicTo(
                cp1x.toFloat(),
                cp1y.toFloat(),
                cp2x.toFloat(),
                cp2y.toFloat(),
                xend.toFloat(),
                yend.toFloat(),
            )
        }
    }

    /** `RemotePathBase.parsePathData`, command for command. */
    private fun parsePathData(pathData: String) {
        val cords = FloatArray(6)
        for (command in splitCommands(pathData)) {
            val cmd = command[0]
            val rest = command.substring(1).trim()
            // As `String.split` in Java: no trailing empty strings, and the input itself when
            // nothing separates it.
            val values =
                if (rest.isEmpty()) listOf("")
                else rest.split(ValueSeparator).dropLastWhile { it.isEmpty() }
            when (cmd) {
                'M' -> moveTo(values[0].toFloat(), values[1].toFloat())
                'L' ->
                    for (i in values.indices step 2) {
                        lineTo(values[i].toFloat(), values[i + 1].toFloat())
                    }
                'H' ->
                    for (value in values) {
                        lineTo(value.toFloat(), cords[1])
                    }
                'C' ->
                    for (i in values.indices step 6) {
                        cubicTo(
                            values[i].toFloat(),
                            values[i + 1].toFloat(),
                            values[i + 2].toFloat(),
                            values[i + 3].toFloat(),
                            values[i + 4].toFloat(),
                            values[i + 5].toFloat(),
                        )
                    }
                'S' ->
                    for (i in values.indices step 4) {
                        cubicTo(
                            2 * cords[0] - cords[2],
                            2 * cords[1] - cords[3],
                            values[i].toFloat(),
                            values[i + 1].toFloat(),
                            values[i + 2].toFloat(),
                            values[i + 3].toFloat(),
                        )
                    }
                'Q' ->
                    for (i in values.indices step 4) {
                        quadTo(
                            values[i].toFloat(),
                            values[i + 1].toFloat(),
                            values[i + 2].toFloat(),
                            values[i + 3].toFloat(),
                        )
                    }
                'A',
                'a' ->
                    for (i in values.indices step 7) {
                        var x = values[i + 5].toFloat()
                        var y = values[i + 6].toFloat()
                        if (cmd == 'a') {
                            x += currentX
                            y += currentY
                        }
                        arcTo(
                            values[i].toFloat(),
                            values[i + 1].toFloat(),
                            values[i + 2].toFloat(),
                            values[i + 3].toFloat() != 0f,
                            values[i + 4].toFloat() != 0f,
                            x,
                            y,
                        )
                    }
                'Z' -> close()
                else -> throw IllegalArgumentException("Unsupported command: $cmd")
            }
            if (cmd != 'Z' && cmd != 'H') {
                cords[0] = values[values.size - 2].toFloat()
                cords[1] = values[values.size - 1].toFloat()
                if (cmd == 'C' || cmd == 'S') {
                    cords[2] = values[values.size - 4].toFloat()
                    cords[3] = values[values.size - 3].toFloat()
                }
            }
        }
    }

    /** `pathData.split("(?=[MmZzLlHhVvCcSsQqTtAa])")`: a new piece before each command letter. */
    private fun splitCommands(pathData: String): List<String> {
        val commands = ArrayList<String>()
        var start = 0
        for (i in 1 until pathData.length) {
            if (pathData[i] in CommandLetters) {
                commands.add(pathData.substring(start, i))
                start = i
            }
        }
        if (start < pathData.length) commands.add(pathData.substring(start))
        return commands
    }

    public fun toFloatArray(): FloatArray = values.copyOf(size)

    /** A copy of the encoded path, under the name AndroidX's `RemotePath` has for it. */
    public fun createFloatArray(): FloatArray = toFloatArray()

    private fun appendMove(type: Int, first: Float, second: Float) {
        ensureCapacity(3)
        values[size++] = Utils.asNan(type)
        values[size++] = first
        values[size++] = second
    }

    private fun append(type: Int, vararg coordinates: Float) {
        // The two reserved zero slots are part of the current wire format.
        ensureCapacity(3 + coordinates.size)
        values[size++] = Utils.asNan(type)
        values[size++] = 0f
        values[size++] = 0f
        coordinates.copyInto(values, destinationOffset = size)
        size += coordinates.size
    }

    private fun ensureCapacity(additional: Int) {
        val required = size + additional
        if (required > values.size) values = values.copyOf(maxOf(values.size * 2, required))
    }

    private companion object {
        const val DefaultCapacity = 64
        const val Move = 10
        const val Line = 11
        const val Quadratic = 12
        const val Conic = 13
        const val Cubic = 14
        const val Close = 15
        const val CommandLetters = "MmZzLlHhVvCcSsQqTtAa"
        val ValueSeparator = Regex("[,\\s]+")
    }
}
