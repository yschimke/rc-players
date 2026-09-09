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

@file:Suppress("RestrictedApiAndroidX")

package ee.schimke.composeai.rcembedded.player.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.managers.FitBoxLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import ee.schimke.composeai.rcembedded.player.LocalAnimatedVisibilityScope
import ee.schimke.composeai.rcembedded.player.LocalSharedTransitionScope
import ee.schimke.composeai.rcembedded.player.RcPlayerComponent
import ee.schimke.composeai.rcembedded.player.animationSpecReflection
import ee.schimke.composeai.rcembedded.player.horizontalPositioningReflection
import ee.schimke.composeai.rcembedded.player.mapEasing
import ee.schimke.composeai.rcembedded.player.verticalPositioningReflection

/** Renders a [FitBoxLayout], transitioning between alternatives as available space changes. */
@Suppress("ComposableLambdaInMeasurePolicy")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RcPlayerFitBoxLayout(layout: FitBoxLayout, modifier: Modifier) {
  val children = remember(layout) { ArrayList<Component>().apply { layout.getComponents(this) } }
  if (children.isEmpty()) {
    Box(modifier = modifier)
    return
  }

  val duration = layout.animationSpecReflection?.motionDuration?.toInt() ?: 300
  val easing = mapEasing(layout.animationSpecReflection?.motionEasingType ?: 0)
  val alignment =
    mapFitBoxAlignment(
      layout.horizontalPositioningReflection,
      layout.verticalPositioningReflection,
    )

  SubcomposeLayout(modifier = modifier) { constraints ->
    val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
    val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
    val probeMeasurables =
      subcompose(FitBoxSlot.Probe) {
        children.fastForEach { component ->
          Box(modifier = Modifier.clearAndSetSemantics {}) { RcPlayerComponent(component) }
        }
      }

    // Measured, not asked. Compose refuses intrinsic queries against anything built on
    // `SubcomposeLayout` — "Asking for intrinsic measurements of SubcomposeLayout layouts is not
    // supported" — and that is not an exotic child here: a `FitBox` alternative may itself be a
    // `FitBox`, and any plugin backed by a lazy layout is in the same class. Such a document threw
    // during measurement instead of drawing at all. Measuring each alternative under unconstrained
    // constraints asks the same question — how big does this one want to be? — of every child
    // whatever it is built from, which is what this layout did before the intrinsics rewrite.
    //
    // The probe slot is never placed, so these placeables are measurements and nothing else.
    val probePlaceables = probeMeasurables.fastMap { it.measure(Constraints()) }

    var chosen = -1
    for (i in probePlaceables.indices) {
      val placeable = probePlaceables[i]
      if (placeable.width <= maxWidth && placeable.height <= maxHeight) {
        chosen = i
        break
      }
    }
    if (chosen < 0) {
      chosen = probePlaceables.indices.minByOrNull { probePlaceables[it].width } ?: 0
    }

    val contentMeasurables =
      subcompose(FitBoxSlot.Content) {
        SharedTransitionLayout {
          AnimatedContent(
            targetState = chosen,
            contentAlignment = alignment,
            label = "RcPlayerFitBoxLayout",
            transitionSpec = {
              fadeIn(animationSpec = tween(durationMillis = duration, easing = easing)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = duration, easing = easing))
            },
          ) { currentIndex ->
            CompositionLocalProvider(
              LocalSharedTransitionScope provides this@SharedTransitionLayout,
              LocalAnimatedVisibilityScope provides this@AnimatedContent,
            ) {
              Box(contentAlignment = alignment) { RcPlayerComponent(children[currentIndex]) }
            }
          }
        }
      }

    val contentPlaceables = contentMeasurables.fastMap { it.measure(constraints) }
    val width = constraints.constrainWidth(contentPlaceables.fastMaxOfOrNull { it.width } ?: 0)
    val height = constraints.constrainHeight(contentPlaceables.fastMaxOfOrNull { it.height } ?: 0)
    layout(width, height) { contentPlaceables.fastForEach { it.placeRelative(0, 0) } }
  }
}

private enum class FitBoxSlot {
  Probe,
  Content,
}

private fun mapFitBoxAlignment(horizontal: Int, vertical: Int): Alignment =
  when {
    horizontal == FitBoxLayout.START && vertical == FitBoxLayout.TOP -> Alignment.TopStart
    horizontal == FitBoxLayout.START && vertical == FitBoxLayout.BOTTOM -> Alignment.BottomStart
    horizontal == FitBoxLayout.START -> Alignment.CenterStart
    horizontal == FitBoxLayout.END && vertical == FitBoxLayout.TOP -> Alignment.TopEnd
    horizontal == FitBoxLayout.END && vertical == FitBoxLayout.BOTTOM -> Alignment.BottomEnd
    horizontal == FitBoxLayout.END -> Alignment.CenterEnd
    vertical == FitBoxLayout.TOP -> Alignment.TopCenter
    vertical == FitBoxLayout.BOTTOM -> Alignment.BottomCenter
    else -> Alignment.Center
  }
