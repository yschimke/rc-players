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

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.MatrixAccess
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.core.operations.ParticlesCreate
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.RootLayoutComponent
import androidx.compose.remote.core.operations.layout.managers.FitBoxLayout
import androidx.compose.remote.core.operations.layout.managers.StateLayout
import androidx.compose.remote.core.operations.layout.modifiers.ComponentVisibilityOperation
import androidx.compose.remote.core.operations.utilities.ArrayAccess
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.util.fastFirstOrNull

/**
 * Optional inspection and tracing hook for [RcPlayer].
 *
 * When not installed (`null` by default), [RcPlayer] incurs zero allocation and zero layout/draw
 * overhead. When installed by a test harness or inspector tool, records Compose-native layout
 * coordinates, host actions, draw commands, and exposes document state probes.
 */
internal class RcPlayerInspector {

  data class TreeNodeSnapshot(
    val id: Int,
    val kind: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val depth: Int,
    val isGone: Boolean,
    val visibility: String,
  )

  data class HostActionRecord(val name: String, val value: Any?)

  private var rootCoords: LayoutCoordinates? = null
  private val outerCoords = HashMap<Int, LayoutCoordinates>()
  private val contentCoords = HashMap<Int, LayoutCoordinates>()

  val hostActions = ArrayList<HostActionRecord>()
  val drawCommands = ArrayList<String>()
  val handledSteps = LinkedHashMap<String, Boolean>()

  fun reset() {
    rootCoords = null
    outerCoords.clear()
    contentCoords.clear()
    hostActions.clear()
    drawCommands.clear()
    handledSteps.clear()
  }

  fun recordRootCoords(coords: LayoutCoordinates) {
    rootCoords = coords
  }

  fun recordOuterCoords(componentId: Int, coords: LayoutCoordinates) {
    outerCoords[componentId] = coords
  }

  fun recordContentCoords(componentId: Int, coords: LayoutCoordinates) {
    contentCoords[componentId] = coords
  }

  fun recordHostAction(name: String, value: Any?) {
    hostActions.add(HostActionRecord(name, value))
  }

  fun recordDrawCommand(command: String) {
    drawCommands.add(command)
  }

  fun recordHandledStep(stepId: String, handled: Boolean) {
    handledSteps[stepId] = handled
  }

  /**
   * Captures the live Compose layout tree for [document], computing parent-relative layout-manager
   * coordinates `(x, y)` excluding modifier-induced translations (padding, offset) in accordance
   * with `CONFORMANCE_FORMAT.md` §2.7.
   */
  fun captureTreeSnapshot(
    document: CoreDocument,
    remoteContext: RemoteContext,
  ): List<TreeNodeSnapshot> {
    val root = document.rootLayoutComponent ?: return emptyList()
    val out = ArrayList<TreeNodeSnapshot>()
    buildNodeSnapshot(
      component = root,
      parentContentCoords = null,
      depth = 0,
      ancestorGone = false,
      remoteContext = remoteContext,
      out = out,
    )
    return out
  }

  private fun buildNodeSnapshot(
    component: Component,
    parentContentCoords: LayoutCoordinates?,
    depth: Int,
    ancestorGone: Boolean,
    remoteContext: RemoteContext,
    out: MutableList<TreeNodeSnapshot>,
  ) {
    val id = component.getId()
    val typeName = component::class.java.simpleName.trimStart('_')
    val ownVisibilityCode = resolveComponentVisibilityCode(component, remoteContext)
    val isGone = ancestorGone || ownVisibilityCode == Component.Visibility.GONE
    val visibilityStr =
      when {
        isGone -> "GONE"
        ownVisibilityCode == Component.Visibility.INVISIBLE -> "INVISIBLE"
        else -> "VISIBLE"
      }

    val outer = outerCoords[id]
    val content = contentCoords[id]

    val x: Float
    val y: Float
    val width: Float
    val height: Float

    if (component is RootLayoutComponent) {
      val rc = rootCoords
      x = 0f
      y = 0f
      width = rc?.size?.width?.toFloat() ?: component.getWidth()
      height = rc?.size?.height?.toFloat() ?: component.getHeight()
    } else if (component is StateLayout) {
      val children = ArrayList<Component>().apply { component.getComponents(this) }
      val activeChild =
        children.fastFirstOrNull { it.mVisibility != Component.Visibility.GONE }
          ?: children.firstOrNull()
      val activeOuter = activeChild?.let { outerCoords[it.getId()] }
      width =
        content?.takeIf { it.isAttached }?.size?.width?.toFloat()
          ?: activeOuter?.takeIf { it.isAttached }?.size?.width?.toFloat()
          ?: outer?.takeIf { it.isAttached }?.size?.width?.toFloat()
          ?: component.getWidth()
      height =
        content?.takeIf { it.isAttached }?.size?.height?.toFloat()
          ?: activeOuter?.takeIf { it.isAttached }?.size?.height?.toFloat()
          ?: outer?.takeIf { it.isAttached }?.size?.height?.toFloat()
          ?: component.getHeight()
      if (
        parentContentCoords != null &&
          parentContentCoords.isAttached &&
          outer != null &&
          outer.isAttached
      ) {
        val rel = parentContentCoords.localPositionOf(outer, Offset.Zero)
        x = rel.x
        y = rel.y
      } else {
        x = component.getX()
        y = component.getY()
      }
    } else if (outer != null && outer.isAttached) {
      width = outer.size.width.toFloat()
      height = outer.size.height.toFloat()
      if (parentContentCoords != null && parentContentCoords.isAttached) {
        val rel = parentContentCoords.localPositionOf(outer, Offset.Zero)
        x = rel.x
        y = rel.y
      } else {
        x = component.getX()
        y = component.getY()
      }
    } else {
      x = component.getX()
      y = component.getY()
      width = component.getWidth()
      height = component.getHeight()
    }

    val effectiveContentCoords =
      when {
        component is RootLayoutComponent -> rootCoords
        content != null && content.isAttached -> content
        else -> outer
      }

    val childComponents = ArrayList<Component>()
    if (component is LayoutComponent) {
      childComponents.addAll(component.childrenComponents)
    } else {
      component.getComponents(childComponents)
    }

    for (i in childComponents.indices) {
      buildNodeSnapshot(
        component = childComponents[i],
        parentContentCoords = effectiveContentCoords,
        depth = depth + 1,
        ancestorGone = isGone,
        remoteContext = remoteContext,
        out = out,
      )
    }

    out.add(
      TreeNodeSnapshot(
        id = id,
        kind = typeName,
        x = x,
        y = y,
        width = width,
        height = height,
        depth = depth,
        isGone = isGone,
        visibility = visibilityStr,
      )
    )
  }

  private fun resolveComponentVisibilityCode(
    component: Component,
    remoteContext: RemoteContext,
  ): Int {
    if (component.mVisibility == Component.Visibility.GONE) {
      return Component.Visibility.GONE
    }
    if (component.parent is FitBoxLayout || component.parent is StateLayout) {
      return component.mVisibility
    }
    if (component is LayoutComponent) {
      val visibilityOp =
        component.componentModifiers.list.fastFirstOrNull { it is ComponentVisibilityOperation }
          as? ComponentVisibilityOperation
      if (visibilityOp != null) {
        val visId = visibilityOp.getVisibilityIdReflection()
        return remoteContext.getInteger(visId)
      }
    }
    return component.mVisibility
  }

  fun resolveVariableId(
    target: Any,
    document: CoreDocument,
    remoteContext: RemoteContext,
  ): Int? {
    if (target is Number) return target.toInt()
    val name = target.toString()
    val asInt = name.toIntOrNull()
    if (asInt != null) return asInt

    if (remoteContext is StoreBackedRemoteContext) {
      try {
        return remoteContext.getVariableId(name)
      } catch (_: NoSuchElementException) {}
    }

    val allOps = collectAllOperations(document)
    for (i in allOps.indices) {
      val op = allOps[i]
      if (op is NamedVariable && op.mVarName == name) {
        return op.mVarId
      }
    }
    return null
  }

  fun resolveFloat(
    target: Any,
    document: CoreDocument,
    remoteContext: RemoteContext,
    graphContext: GraphContext?,
  ): Float? {
    val id = resolveVariableId(target, document, remoteContext) ?: return null
    if (graphContext != null) {
      return graphContext.getFloat(id)
    }
    return remoteContext.getFloat(id)
  }

  fun resolveInt(
    target: Any,
    document: CoreDocument,
    remoteContext: RemoteContext,
    graphContext: GraphContext?,
  ): Int? {
    val id = resolveVariableId(target, document, remoteContext) ?: return null
    if (graphContext != null) {
      return graphContext.getInteger(id)
    }
    return remoteContext.getInteger(id)
  }

  fun resolveColor(
    target: Any,
    document: CoreDocument,
    remoteContext: RemoteContext,
    graphContext: GraphContext?,
  ): Long? {
    val id = resolveVariableId(target, document, remoteContext) ?: return null
    val argb =
      if (graphContext != null) {
        graphContext.getColor(id)
      } else {
        remoteContext.getColor(id)
      }
    return argb.toLong() and 0xFFFFFFFFL
  }

  fun resolveText(
    target: Any,
    document: CoreDocument,
    remoteContext: RemoteContext,
    graphContext: GraphContext?,
  ): String? {
    val id = resolveVariableId(target, document, remoteContext) ?: return null
    if (graphContext != null) {
      return graphContext.getText(id)
    }
    return remoteContext.getText(id)
  }

  fun resolveMatrix(target: Int, remoteContext: RemoteContext): FloatArray? {
    val obj = remoteContext.getObject(target)
    if (obj is MatrixAccess) {
      return obj.get()
    }
    return null
  }

  fun resolveFloatArray(target: Int, remoteContext: RemoteContext): FloatArray? {
    val fromCollections = remoteContext.getCollectionsAccess()?.getFloats(target)
    if (fromCollections != null) return fromCollections
    val fromState = remoteContext.mRemoteComposeState.getFromId(target)
    when (fromState) {
      is ArrayAccess -> return fromState.getFloats()
      is FloatArray -> return fromState
    }
    val obj = remoteContext.getObject(target)
    when (obj) {
      is ArrayAccess -> return obj.getFloats()
      is FloatArray -> return obj
    }
    return null
  }

  fun resolveParticles(document: CoreDocument): List<FloatArray> {
    val result = ArrayList<FloatArray>()
    val ops = collectAllOperations(document)
    for (i in ops.indices) {
      val op = ops[i]
      if (op is ParticlesCreate) {
        val particles = op.particles
        if (particles != null) {
          for (p in particles) {
            result.add(p)
          }
        }
      }
    }
    return result
  }

  fun collectAllOperations(document: CoreDocument): List<Operation> {
    val result = ArrayList<Operation>()
    fun walk(ops: Collection<Operation>) {
      for (op in ops) {
        result.add(op)
        if (op is Container) {
          walk(op.getList())
        }
        if (op is LayoutComponent) {
          val canvasOps = op.getCanvasOperations()
          if (canvasOps != null) {
            walk(listOf(canvasOps))
          }
        }
      }
    }
    walk(document.getOperationsReflection())
    return result
  }
}
