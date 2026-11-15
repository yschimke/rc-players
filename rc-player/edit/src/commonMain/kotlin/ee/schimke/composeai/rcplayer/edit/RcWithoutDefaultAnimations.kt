package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcOperation

/**
 * This document with animation turned off on every layout component that does not ask for one: each
 * component without an `AnimationSpec` gets a disabled one (`animationId` 0, what
 * `RemoteModifier.animationSpec(enabled = false)` writes). A component that has a spec keeps it, so
 * what the document's author chose to animate still animates.
 *
 * AndroidX animates every layout component to its new bounds, and fades it across a visibility
 * change, over its spec or a 300 ms default, so a card whose data a host updates eases every change
 * whether its author wanted that or not, and a player pays for the lookahead pass those animations
 * need. A disabled spec is AndroidX's own way to say a component lands at once, so every player
 * plays the result the same way; rc-players' Compose player builds no lookahead pass for a document
 * in which nothing can animate.
 *
 * Returns this document itself when there is nothing to add, or when its containers do not balance
 * (an edit never guesses where a component ends).
 */
public fun RcDocument.withoutDefaultAnimations(): RcDocument {
  val tree = operationTree(operations) ?: return this
  val after = mutableListOf<Int>()
  tree.forEach { root ->
    root.walk { node ->
      if (
        node.isContainer &&
          node.operation.isLayoutComponent() &&
          node.operation !is RcLayoutContent &&
          node.operation !is RcCanvasContent &&
          node.children.none { it.operation is RcAnimationSpec }
      ) {
        after += node.start
      }
    }
  }
  if (after.isEmpty()) return this
  val starts = after.toHashSet()
  val rewritten = ArrayList<RcOperation>(operations.size + after.size)
  operations.forEachIndexed { index, operation ->
    rewritten += operation
    if (index in starts) rewritten += DisabledAnimationSpec
  }
  return copy(operations = rewritten)
}

/**
 * AndroidX's `AnimationSpec` defaults with animation off: only [RcAnimationSpec.animationId] reads.
 */
private val DisabledAnimationSpec =
  RcAnimationSpec(
    animationId = 0,
    motionDurationMillis = RcFloatWord.literal(300f),
    motionEasingType = 1,
    visibilityDurationMillis = RcFloatWord.literal(300f),
    visibilityEasingType = 1,
    enterAnimation = RcLayoutAnimation.FadeIn,
    exitAnimation = RcLayoutAnimation.FadeOut,
  )
