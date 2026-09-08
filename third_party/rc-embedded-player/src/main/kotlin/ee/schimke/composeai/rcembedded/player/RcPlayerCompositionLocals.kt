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

@file:Suppress("RestrictedApiAndroidX", "PrimitiveInCollection", "AutoboxingStateCreation")

package ee.schimke.composeai.rcembedded.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.VariableProvider
import androidx.compose.remote.core.VariableSupport
import androidx.compose.remote.core.operations.ComponentValue
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf

internal val LocalCoreDocument: ProvidableCompositionLocal<CoreDocument> = compositionLocalOf {
  throw IllegalStateException("No document")
}

/**
 * The pure-Compose [GraphContext] that resolves *computed* operations (color/text/float/int
 * expressions, attributes, lookups) via `derivedStateOf`. Resolvers route a computed id through it;
 * leaf variables read the snapshot store directly. Null when no graph is installed (shouldn't
 * happen inside [RcPlayer]).
 */
internal val LocalGraphContext: ProvidableCompositionLocal<GraphContext?> = compositionLocalOf {
  null
}

/**
 * Index of computed-value operations by the id they produce — `VariableSupport`+`VariableProvider`
 * ops that compute from other variables. Animation/spring-bearing `FloatExpression`s are excluded
 * (those are displayed via `rememberAnimatedRemoteFloat`); everything else, including plain
 * `FloatExpression`/`IntegerExpression`, is included so the graph can resolve them when a derived
 * op reads them as an input (chains).
 */
internal fun buildComputedOpIndex(operations: Collection<Operation>): Map<Int, Operation> {
  val map = HashMap<Int, Operation>()
  fun walk(ops: Collection<Operation>) {
    for (op in ops) {
      if (op is VariableSupport && op is VariableProvider) {
        val animated = op is FloatExpression && op.mFloatAnimation != null
        val id = op.id
        if (!animated && id > 0 && !map.containsKey(id)) map[id] = op
      }
      if (op is Container) walk(op.getList())
      // A component's draw-content operations hang off it as a *field* rather than as a child, so
      // a walk that only follows `Container.getList()` never reaches them — and the values they
      // produce are not private to the draw pass. `remote-m3` builds a disabled label's colour in
      // the layout tree from `ColorAttribute`s declared in the component's canvas stream, so
      // leaving them out of the index made the expression resolve its channels against a store
      // nothing had written: `rgb(alpha, 0, 0, 0)`, a fully transparent label, and a control that
      // drew its container and its box and no text at all.
      if (op is LayoutComponent) op.getCanvasOperations()?.let { walk(listOf(it)) }
    }
  }
  walk(operations)
  return map
}

internal val LocalRemoteContext: ProvidableCompositionLocal<RemoteContext> = compositionLocalOf {
  throw IllegalStateException("No remote context")
}

internal val LocalComponentValueMap: ProvidableCompositionLocal<Map<Int, List<ComponentValue>>> =
  compositionLocalOf {
    emptyMap()
  }

internal val LocalComponentValueStateMap:
  ProvidableCompositionLocal<Map<Int, MutableState<Float>>> =
  compositionLocalOf {
    emptyMap()
  }

internal val LocalCurrentTimeMillis: ProvidableCompositionLocal<State<Float>> = compositionLocalOf {
  androidx.compose.runtime.mutableFloatStateOf(0f)
}

/** Host-action callback (id, value) for `HostAction`/`RunAction` clicks. Default no-op. */
internal val LocalRemoteActionHandler: ProvidableCompositionLocal<(Int, String?) -> Unit> =
  compositionLocalOf {
    { _, _ -> }
  }

/**
 * Host named-action callback (name, resolved value) for `HostNamedAction` clicks. Default no-op.
 */
internal val LocalRemoteNamedActionHandler: ProvidableCompositionLocal<(String, Any?) -> Unit> =
  compositionLocalOf {
    { _, _ -> }
  }

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
  compositionLocalOf {
    null
  }

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
  compositionLocalOf {
    null
  }
