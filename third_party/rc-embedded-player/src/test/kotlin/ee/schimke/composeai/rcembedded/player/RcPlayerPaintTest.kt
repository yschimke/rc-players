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

package ee.schimke.composeai.rcembedded.player

import androidx.compose.remote.core.PaintOperation
import androidx.compose.remote.core.RemoteClock
import androidx.compose.remote.core.operations.ClipPath
import androidx.compose.remote.core.operations.DrawCircle
import androidx.compose.remote.core.operations.DrawOval
import androidx.compose.remote.core.operations.DrawPath
import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.core.operations.paint.PaintBundle
import androidx.compose.remote.player.core.platform.AndroidRemoteContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class RcPlayerPaintTest {

  @Test
  fun defaultColorMatchesFrameworkPaint() {
    val paint = ComposeLocalPaint()

    assertEquals(Color.Black.toArgb(), paint.color)
    assertEquals(Color.Black, paint.effectiveColor())
    assertFalse(
      "default color must not masquerade as an explicit COLOR operation",
      paint.isColorSet,
    )
  }

  @Test
  fun nanBoxedGradientStopsResolve() {
    val context = AndroidRemoteContext(RemoteClock.SYSTEM)
    context.loadFloat(51, 0.2f)
    context.loadFloat(52, 0.8f)
    val bundle =
      PaintBundle().apply {
        setLinearGradient(
          intArrayOf(Color.Black.toArgb(), Color.White.toArgb()),
          0,
          floatArrayOf(Utils.asNan(51), Utils.asNan(52)),
          0f,
          0f,
          100f,
          100f,
          0,
        )
      }
    val paint = ComposeLocalPaint()

    updatePaintFromBundle(bundle, paint, context)

    assertNotNull(paint.brush)
  }

  @Test
  fun circleAndOvalCoordinatesResolve() {
    val context = AndroidRemoteContext(RemoteClock.SYSTEM)
    (101..107).forEach { context.loadFloat(it, it.toFloat()) }
    val circle =
      DrawCircle(Utils.asNan(101), Utils.asNan(102), Utils.asNan(103)).readDataReflection()
    val oval =
      DrawOval(Utils.asNan(104), Utils.asNan(105), Utils.asNan(106), Utils.asNan(107))
        .readDataReflection()

    assertEquals(101f, resolveFloat(circle.value1, circle.v1, context))
    assertEquals(102f, resolveFloat(circle.value2, circle.v2, context))
    assertEquals(103f, resolveFloat(circle.value3, circle.v3, context))
    assertEquals(104f, resolveFloat(oval.x1Value, oval.x1, context))
    assertEquals(105f, resolveFloat(oval.y1Value, oval.y1, context))
    assertEquals(106f, resolveFloat(oval.x2Value, oval.x2, context))
    assertEquals(107f, resolveFloat(oval.y2Value, oval.y2, context))
  }

  @Test
  fun pathIdsDereferencePointers() {
    val context = AndroidRemoteContext(RemoteClock.SYSTEM)
    context.mRemoteComposeState.updateInteger(201, 301)
    val encoded = (201 and PaintOperation.VALUE_MASK) or PaintOperation.PTR_DEREFERENCE
    val drawPath = DrawPath(encoded).readDataReflection()
    val clipPath = ClipPath(encoded, ClipPath.PATH_CLIP_INTERSECT).readData()

    assertEquals(301, derefId(drawPath.id, context))
    assertEquals(301, derefId(clipPath.id, context))
  }
}
