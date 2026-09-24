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

@file:Suppress("RestrictedApiAndroidX", "PrimitiveInCollection")

package ee.schimke.composeai.rcembedded.player.modifier

import androidx.collection.MutableIntSet
import androidx.collection.mutableIntSetOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.core.operations.layout.modifiers.GraphicsLayerModifierOperation
import androidx.compose.remote.core.operations.utilities.AnimatedFloatExpression
import androidx.compose.remote.core.operations.utilities.NanMap
import androidx.compose.remote.core.operations.utilities.easing.GeneralEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import ee.schimke.composeai.rcembedded.player.GraphicsLayerAttributeValueData
import ee.schimke.composeai.rcembedded.player.LocalComponentValueStateMap
import ee.schimke.composeai.rcembedded.player.LocalCoreDocument
import ee.schimke.composeai.rcembedded.player.getFloatExpressionsReflection
import ee.schimke.composeai.rcembedded.player.getValuesReflection
import ee.schimke.composeai.rcembedded.player.mapEasing
import ee.schimke.composeai.rcembedded.player.state.expressionDependsOnAnimation
import ee.schimke.composeai.rcembedded.player.state.rememberRemoteFloatAsState

@Composable
internal fun Modifier.graphicsLayer(op: GraphicsLayerModifierOperation): Modifier {
  val values = op.getValuesReflection()
  val scaleX =
    rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.SCALE_X].source)
  val scaleY =
    rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.SCALE_Y].source)
  val alpha = rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.ALPHA].source)
  val translationX =
    rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.TRANSLATION_X].source)
  val translationY =
    rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.TRANSLATION_Y].source)
  val shadowElevation =
    rememberGraphicsLayerFloatAsState(
      values[GraphicsLayerModifierOperation.SHADOW_ELEVATION].source
    )
  val rotationX =
    rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.ROTATION_X].source)
  val rotationY =
    rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.ROTATION_Y].source)
  val rotationZ =
    rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.ROTATION_Z].source)
  val cameraDistance =
    rememberGraphicsLayerFloatAsState(values[GraphicsLayerModifierOperation.CAMERA_DISTANCE].source)
  val transformOriginX =
    rememberGraphicsLayerFloatAsState(
      values[GraphicsLayerModifierOperation.TRANSFORM_ORIGIN_X].originOrCenter()
    )
  val transformOriginY =
    rememberGraphicsLayerFloatAsState(
      values[GraphicsLayerModifierOperation.TRANSFORM_ORIGIN_Y].originOrCenter()
    )

  return this.graphicsLayer {
    this.scaleX = scaleX.value
    this.scaleY = scaleY.value
    this.alpha = alpha.value
    this.translationX = translationX.value
    this.translationY = translationY.value
    this.shadowElevation = shadowElevation.value
    this.rotationX = rotationX.value
    this.rotationY = rotationY.value
    this.rotationZ = rotationZ.value
    this.cameraDistance = cameraDistance.value
    this.transformOrigin = TransformOrigin(transformOriginX.value, transformOriginY.value)
  }
}

/**
 * A transform origin, or the layer's centre when the document did not write one.
 *
 * `remote-core` declares `TRANSFORM_ORIGIN_X/Y` with a default of 0, the top-left corner, while
 * `remote-creation-compose` omits the attribute when it is the centre. Reading the table's default
 * would turn every unauthored scale, mirror or rotation into one about the corner, which can move
 * the content out of its own bounds (#153). An absent origin means Compose's
 * [TransformOrigin.Center], as it does for the writer and for the CMP player.
 */
private fun GraphicsLayerAttributeValueData.originOrCenter(): Float = if (isSet) source else 0.5f

private val GraphicsLayerImplicitAnimationSpec =
  tween<Float>(durationMillis = 300, easing = mapEasing(GeneralEasing.CUBIC_STANDARD))

/**
 * Resolves a graphics layer property from a wire float/NaN variable source and applies implicit
 * value-change animation when appropriate.
 *
 * In `remote-core`, [GraphicsLayerModifierOperation] wraps its properties in `AnimatableValue`,
 * which automatically animates discrete variable changes over 300ms (`CUBIC_STANDARD` easing)
 * without animating on initial appearance. Using Compose's [animateFloatAsState] provides the same
 * behavior (snap on first composition, smooth tween on target changes, and mid-flight velocity
 * preservation), while deferring `.value` reads into the `graphicsLayer` lambda avoids
 * recomposition during animation frames.
 *
 * Continuous variables (e.g., time, component dimensions, or expressions already driven by a
 * `FloatAnimation`) bypass this implicit animation to avoid lagging behind their source.
 */
@Composable
private fun rememberGraphicsLayerFloatAsState(source: Float): State<Float> {
  val state = rememberRemoteFloatAsState(source)
  if (!Utils.isVariable(source)) {
    return state
  }

  val id = Utils.idFromNan(source)
  val document = LocalCoreDocument.current
  val componentValueStates = LocalComponentValueStateMap.current
  val shouldAnimate =
    remember(document, id, componentValueStates) {
      val expressions = document.getFloatExpressionsReflection()
      !isTimeVariable(id) &&
        !componentValueStates.containsKey(id) &&
        !expressionDependsOnAnimation(expressions, id) &&
        !expressionDependsOnTime(expressions, id)
    }
  return if (shouldAnimate) {
    animateFloatAsState(
      targetValue = state.value,
      animationSpec = GraphicsLayerImplicitAnimationSpec,
    )
  } else {
    state
  }
}

private fun isTimeVariable(id: Int): Boolean =
  id == RemoteContext.ID_CONTINUOUS_SEC ||
    id == RemoteContext.ID_TIME_IN_SEC ||
    id == RemoteContext.ID_TIME_IN_MIN ||
    id == RemoteContext.ID_TIME_IN_HR ||
    id == RemoteContext.ID_ANIMATION_TIME ||
    id == RemoteContext.ID_EPOCH_SECOND

private fun expressionDependsOnTime(
  expressions: Map<Int, FloatExpression>,
  id: Int,
  visited: MutableIntSet = mutableIntSetOf(),
): Boolean {
  if (isTimeVariable(id)) return true
  if (!visited.add(id)) return false
  val expr = expressions[id] ?: return false
  // Upstream writes `expr.mSrcValue ?: return false`: `FloatExpression.mSrcValue` is unannotated
  // there, so Kotlin sees a platform type. The `remote-core` this module compiles against declares
  // it non-null, and the elvis is a warning rather than a seam worth keeping.
  val src = expr.mSrcValue
  for (v in src) {
    if (v.isNaN() && !AnimatedFloatExpression.isMathOperator(v) && !NanMap.isDataVariable(v)) {
      val varId = Utils.idFromNan(v)
      if (expressionDependsOnTime(expressions, varId, visited)) return true
    }
  }
  return false
}
