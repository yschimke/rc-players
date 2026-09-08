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

package ee.schimke.composeai.rcembedded.player

import androidx.collection.mutableIntObjectMapOf
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.VariableProvider
import androidx.compose.remote.core.VariableSupport
import androidx.compose.remote.core.operations.BitmapData
import androidx.compose.remote.core.operations.ColorConstant
import androidx.compose.remote.core.operations.ColorTheme
import androidx.compose.remote.core.operations.ComponentValue
import androidx.compose.remote.core.operations.FloatConstant
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.core.operations.ParticlesCompare
import androidx.compose.remote.core.operations.ParticlesLoop
import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.core.operations.WakeIn
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.LayoutComponentContent
import androidx.compose.remote.core.operations.utilities.AnimatedFloatExpression
import androidx.compose.remote.core.operations.utilities.NanMap

internal class DocumentPreprocessResult(
  val globalOps: ArrayList<Operation>,
  val constantOps: ArrayList<Operation>,
  val bitmaps: ArrayList<BitmapData>,
  val computedOpIndex: Map<Int, Operation>,
  val componentValueMap: Map<Int, List<ComponentValue>>,
  val hasParticles: Boolean,
  val hasWakeIn: Boolean,
  val hasAnimations: Boolean,
  val isTimeDependent: Boolean,
)

/** Collects every setup index and frame-loop flag in one traversal of the operation tree. */
internal fun preprocessDocument(document: CoreDocument): DocumentPreprocessResult {
  val rootComponent = document.rootLayoutComponent
  val operations = document.getOperationsReflection()
  val globalOps = ArrayList<Operation>()
  if (rootComponent != null) {
    for (operation in operations) {
      if (operation === rootComponent) break
      globalOps.add(operation)
    }
  } else {
    globalOps.addAll(operations)
  }

  val constantOps = ArrayList<Operation>()
  val bitmaps = ArrayList<BitmapData>()
  val computedOpIndex = HashMap<Int, Operation>()
  val rawComponentValues = ArrayList<ComponentValue>()
  val componentsById = mutableIntObjectMapOf<Component>()
  var hasParticles = false
  var hasWakeIn = false

  fun visit(operation: Operation) {
    if (
      operation is ColorConstant ||
        operation is FloatConstant ||
        operation is ColorTheme ||
        operation is NamedVariable ||
        operation.javaClass.simpleName.endsWith("Constant")
    ) {
      constantOps.add(operation)
    }
    if (operation is BitmapData) bitmaps.add(operation)
    if (operation is ParticlesLoop || operation is ParticlesCompare) hasParticles = true
    if (operation is WakeIn) hasWakeIn = true

    if (operation is VariableSupport && operation is VariableProvider) {
      val animated = operation is FloatExpression && operation.mFloatAnimation != null
      val id = operation.id
      if (!animated && id > 0 && !computedOpIndex.containsKey(id)) computedOpIndex[id] = operation
    }
    if (operation is ComponentValue) rawComponentValues.add(operation)
    if (operation is Component && operation.componentId !in componentsById) {
      componentsById[operation.componentId] = operation
    }
    if (operation is LayoutComponent) {
      val content = operation.getContentReflection()
      if (content != null && content.componentId !in componentsById) {
        componentsById[content.componentId] = content
      }
      operation.getCanvasOperations()?.let(::visit)
    }
    if (operation is Container) operation.getList().forEach(::visit)
  }
  operations.forEach(::visit)

  val componentValueMap = HashMap<Int, MutableList<ComponentValue>>()
  rawComponentValues.forEach { operation ->
    var targetId = operation.componentId
    val target = componentsById[targetId]
    if (target is LayoutComponentContent) target.parent?.let { targetId = it.id }
    componentValueMap.getOrPut(targetId) { ArrayList() }.add(operation)
  }

  var hasAnimations = false
  var isTimeDependent = false
  document.getFloatExpressionsReflection().values.forEach { expression ->
    if (expression.mFloatAnimation != null) hasAnimations = true
    if (!isTimeDependent && isExpressionTimeDependent(expression)) isTimeDependent = true
  }
  return DocumentPreprocessResult(
    globalOps,
    constantOps,
    bitmaps,
    computedOpIndex,
    componentValueMap,
    hasParticles,
    hasWakeIn,
    hasAnimations,
    isTimeDependent,
  )
}

internal fun isExpressionTimeDependent(expression: FloatExpression): Boolean =
  expression.mSrcValue.any { value ->
    if (
      !value.isNaN() ||
        AnimatedFloatExpression.isMathOperator(value) ||
        NanMap.isDataVariable(value)
    ) {
      false
    } else {
      when (Utils.idFromNan(value)) {
        RemoteContext.ID_CONTINUOUS_SEC,
        RemoteContext.ID_TIME_IN_SEC,
        RemoteContext.ID_TIME_IN_MIN,
        RemoteContext.ID_TIME_IN_HR,
        RemoteContext.ID_EPOCH_SECOND -> true
        else -> false
      }
    }
  }
