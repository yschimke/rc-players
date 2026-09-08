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

package ee.schimke.composeai.rcembedded.player.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.animation.AnimationSpec
import androidx.compose.remote.core.operations.layout.managers.StateLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import ee.schimke.composeai.rcembedded.player.LocalAnimatedVisibilityScope
import ee.schimke.composeai.rcembedded.player.LocalSharedTransitionScope
import ee.schimke.composeai.rcembedded.player.RcPlayerComponent
import ee.schimke.composeai.rcembedded.player.animationSpecReflection
import ee.schimke.composeai.rcembedded.player.indexIdReflection
import ee.schimke.composeai.rcembedded.player.mapEasing
import ee.schimke.composeai.rcembedded.player.state.rememberRemoteIntAsState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RcPlayerStateLayout(layout: StateLayout, modifier: Modifier) {
  val index by rememberRemoteIntAsState(layout.indexIdReflection)
  val children = remember(layout) { ArrayList<Component>().apply { layout.getComponents(this) } }

  if (children.isEmpty()) {
    Box(modifier = modifier)
    return
  }

  val targetIndex = index.coerceIn(0, children.size - 1)
  val layoutSpec =
    layout.componentModifiers.list.fastFirstOrNull { it is AnimationSpec } as? AnimationSpec
      ?: layout.animationSpecReflection?.takeIf { it != AnimationSpec.DEFAULT }
  val spec =
    layoutSpec
      ?: remember(children) {
        var found: AnimationSpec? = null
        fun search(component: Component) {
          val candidate =
            (component as? LayoutComponent)?.componentModifiers?.list?.fastFirstOrNull {
              it is AnimationSpec
            } as? AnimationSpec
              ?: component.animationSpecReflection?.takeIf { it != AnimationSpec.DEFAULT }
          if (
            candidate != null &&
              (found == null || candidate.motionDuration > found!!.motionDuration)
          ) {
            found = candidate
          }
          if (component is LayoutComponent) component.childrenComponents.fastForEach(::search)
        }
        children.fastForEach(::search)
        found
      }

  val duration = spec?.motionDuration?.toInt() ?: 300
  val easing = mapEasing(spec?.motionEasingType ?: 0)
  SharedTransitionLayout(modifier = modifier) {
    AnimatedContent(
      targetState = targetIndex,
      contentAlignment = Alignment.Center,
      label = "RcPlayerStateLayout",
      transitionSpec = {
        (fadeIn(animationSpec = tween(durationMillis = duration, easing = easing)) togetherWith
            fadeOut(animationSpec = tween(durationMillis = duration, easing = easing)))
          .using(
            SizeTransform(clip = false) { _, _ ->
              if (duration <= 0) snap() else tween(durationMillis = duration, easing = easing)
            }
          )
      },
    ) { currentIndex ->
      CompositionLocalProvider(
        LocalSharedTransitionScope provides this@SharedTransitionLayout,
        LocalAnimatedVisibilityScope provides this@AnimatedContent,
      ) {
        Box(contentAlignment = Alignment.Center) { RcPlayerComponent(children[currentIndex]) }
      }
    }
  }
}
