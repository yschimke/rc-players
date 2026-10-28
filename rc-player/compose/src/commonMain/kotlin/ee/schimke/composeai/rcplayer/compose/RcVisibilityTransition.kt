package ee.schimke.composeai.rcplayer.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSequence
import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.runtime.RcAnimationTimeline
import kotlin.math.roundToInt

/**
 * A component's visibility, animated by Compose's own [AnimatedVisibility].
 *
 * The document's `AnimationSpec` picks the enter and exit transitions and their duration and
 * easing, mapped onto Compose's: fades onto [fadeIn] / [fadeOut], slides onto the slide
 * transitions, and `ROTATE` onto a fade and scale plus a rotation driven by the same transition.
 * AndroidX hand-animates these. Compose's versions differ in small ways: a slide travels the
 * component's own size rather than its parent's. The player takes Compose's behaviour over an exact
 * copy. A `CUSTOM` side runs the document's own function instead (see [rcVisibilityDraw]).
 *
 * Each side's `AnimationSpec.SEQUENCE` orders it against the layout change, as remote-core alpha22
 * and the embedded player do: `AFTER` holds the visibility animation back until the motion duration
 * has passed — an entering component paints nothing until then, an exiting one paints as it was —
 * and `BEFORE` keeps the layout as it was until the visibility animation has played: an exiting
 * component keeps its space, an entering one takes none until it has entered. While it exits, or
 * enters ahead of its space, the component keeps its own size, as AndroidX paints it at its
 * original bounds whatever its parent does with the space.
 *
 * [visibility] is AndroidX's: 0 gone, 1 visible, 2 invisible (laid out, not painted). A component
 * that is gone and has finished leaving renders [gone], which still reports it to the inspection
 * seam at zero size. [modifier] is the parent's modifier, parent data included, and goes on the
 * [AnimatedVisibility] node the parent measures; [content] gets the modifier for the component
 * itself.
 */
@Composable
internal fun RcVisibilityTransition(
  visibility: Int,
  spec: RcAnimationSpec?,
  componentId: Int,
  enabled: Boolean,
  modifier: Modifier,
  gone: @Composable () -> Unit,
  content: @Composable (Modifier) -> Unit,
) {
  val visibleState = remember { MutableTransitionState(visibility != 0) }
  visibleState.targetState = visibility != 0
  // What the component was last shown as: an invisible component that turns GONE exits unpainted,
  // rather than being painted for the length of its exit.
  val shown = remember { RcShownVisibility(visibility) }
  if (visibility != 0) shown.value = visibility
  if (!visibleState.currentState && !visibleState.targetState) {
    gone()
    return
  }
  val resolved = spec ?: DefaultRcAnimationSpec
  val durationMillis = resolved.rcVisibilityDurationMillis()
  val animate = enabled && resolved.isEnabled && durationMillis > 0
  val easing = remember(resolved) { resolved.rcVisibilityEasing() }
  val enterDelay = if (animate) resolved.rcVisibilityDelayMillis(entering = true) else 0
  val exitDelay = if (animate) resolved.rcVisibilityDelayMillis(entering = false) else 0
  val layout = remember { RcVisibilityLayout() }
  AnimatedVisibility(
    visibleState = visibleState,
    modifier = if (animate) modifier.then(layout.modifier) else modifier,
    enter =
      if (animate) resolved.rcEnterTransition(durationMillis, easing, enterDelay)
      else EnterTransition.None,
    exit =
      if (animate) resolved.rcExitTransition(durationMillis, easing, exitDelay)
      else ExitTransition.None,
  ) {
    val drawn =
      if (animate) {
        layout.follow(transition, resolved)
        rcVisibilityDraw(
          resolved,
          durationMillis,
          easing,
          enterDelay,
          exitDelay,
          componentId,
          layout::position,
        )
      } else Modifier
    val rotation =
      if (animate && resolved.enterAnimation.androidXValue == RcLayoutAnimation.Rotate.wireValue) {
        rcEnterRotation(durationMillis, easing, enterDelay)
      } else Modifier
    val inner = drawn.then(rotation)
    // Outside the custom draw, so an invisible component's function paints nothing either.
    content(if (shown.value == 2) Modifier.alpha(0f).then(inner) else inner)
  }
}

/**
 * The space an animating component takes in its parent's lookahead, and the size it is measured at,
 * for [RcVisibilityTransition]'s sequencing; and where its parent placed it, for a custom
 * function's x and y.
 *
 * The parent's layout animation (`animateRcBounds`) eases the component's bounds toward the
 * lookahead's. This node sits inside it: in the lookahead pass it reports the space the sequence
 * calls for, and while the component exits, or enters ahead of its space, it measures the component
 * under the lookahead's constraints rather than the size the bounds animation hands down, so the
 * component draws at its own size while its space opens or closes around it.
 */
private class RcVisibilityLayout {
  /** Where the parent placed the component, read by a custom function's draw. */
  var position: Offset = Offset.Zero
    private set

  private var mode by mutableIntStateOf(STEADY)
  private var lookaheadConstraints: Constraints? = null
  private var shownSize = IntSize.Zero

  val modifier: Modifier =
    Modifier.onPlaced { position = it.positionInParent() }
      .layout { measurable, constraints ->
        val mode = mode
        if (isLookingAhead) {
          lookaheadConstraints = constraints
          val placeable = measurable.measure(constraints)
          val size = IntSize(placeable.width, placeable.height)
          if (mode == STEADY || mode == HOLD_GONE) shownSize = size
          val reported =
            when (mode) {
              HOLD_SHOWN -> shownSize
              HOLD_GONE -> IntSize.Zero
              else -> size
            }
          layout(
            constraints.constrainWidth(reported.width),
            constraints.constrainHeight(reported.height),
          ) {
            placeable.place(0, 0)
          }
        } else {
          val placeable =
            measurable.measure(
              if (mode == STEADY) constraints else lookaheadConstraints ?: constraints
            )
          layout(
            constraints.constrainWidth(placeable.width),
            constraints.constrainHeight(placeable.height),
          ) {
            placeable.place(0, 0)
          }
        }
      }

  /** Reads which phase the component's own enter/exit transition is in. */
  @Composable
  fun follow(transition: Transition<EnterExitState>, spec: RcAnimationSpec) {
    val next =
      when {
        transition.targetState == EnterExitState.PostExit ->
          if (spec.exitAnimation.sequence == RcAnimationSequence.Before) HOLD_SHOWN else EXITING
        transition.currentState != EnterExitState.Visible &&
          spec.enterAnimation.sequence == RcAnimationSequence.Before -> HOLD_GONE
        else -> STEADY
      }
    SideEffect { mode = next }
  }

  private companion object {
    /** Shown, or entering with its space: measured and reported as it is. */
    const val STEADY = 0
    /** Exiting: its space goes as the lookahead says, the component keeps its size. */
    const val EXITING = 1
    /** Exiting `BEFORE`: it keeps its space, and its size, until it has played. */
    const val HOLD_SHOWN = 2
    /** Entering `BEFORE`: no space until it has played, at its own size meanwhile. */
    const val HOLD_GONE = 3
  }
}

/**
 * Draws a component's visibility animation phases that no Compose transition expresses: the wait of
 * an `AFTER` side, and a `CUSTOM` side's function.
 *
 * An `AFTER` enter paints nothing until its delay is over (AndroidX `AnimateMeasure.paint` returns
 * early); an `AFTER` exit paints the component as it is until then. A `CUSTOM` side runs the spec's
 * function with its progress, 0 to 1 on the way in and on the way out, over the visibility duration
 * and curve. The function's progress is animated on [AnimatedVisibilityScope.transition], which
 * keeps an exiting component composed until it has finished, though its Compose exit is `None`.
 * Without a function — an unset id, or one the document does not define — the component paints as
 * it is for the whole animation, as core's does.
 */
@Composable
internal fun AnimatedVisibilityScope.rcVisibilityDraw(
  spec: RcAnimationSpec,
  durationMillis: Int,
  easing: Easing,
  enterDelay: Int,
  exitDelay: Int,
  componentId: Int,
  position: () -> Offset,
): Modifier {
  val enterCustom = spec.enterAnimation.androidXValue == RcLayoutAnimation.Custom.wireValue
  val exitCustom = spec.exitAnimation.androidXValue == RcLayoutAnimation.Custom.wireValue
  if (enterDelay <= 0 && exitDelay <= 0 && !enterCustom && !exitCustom) return Modifier
  val functions = LocalRcVisibilityFunctions.current
  val transition = transition
  // 1 while shown, 0 once gone, flipping when the side's delay is over: an entering component's
  // animation has begun once it reads 1, an exiting one's once it reads 0.
  val started =
    transition.animateFloat(
      transitionSpec = {
        snap(if (targetState == EnterExitState.Visible) enterDelay else exitDelay)
      },
      label = "rcVisibilityDelay",
    ) {
      if (it == EnterExitState.Visible) 1f else 0f
    }
  val shown =
    if (enterCustom || exitCustom) {
      transition.animateFloat(
        transitionSpec = {
          val delay = if (targetState == EnterExitState.Visible) enterDelay else exitDelay
          tween(durationMillis, delayMillis = delay, easing = easing)
        },
        label = "rcVisibilityProgress",
      ) {
        if (it == EnterExitState.Visible) 1f else 0f
      }
    } else null
  return Modifier.drawWithContent {
    val entering = transition.targetState == EnterExitState.Visible
    val animating = transition.currentState != transition.targetState
    if (!animating) {
      drawContent()
      return@drawWithContent
    }
    if (entering && enterDelay > 0 && started.value < 1f) return@drawWithContent
    val functionId =
      when {
        entering && enterCustom -> spec.enterFunctionId
        !entering && exitCustom && (exitDelay <= 0 || started.value <= 0f) -> spec.exitFunctionId
        else -> null
      }
    if (functionId != null && shown != null && functions != null) {
      val progress = if (entering) shown.value else 1f - shown.value
      if (functions.draw(this, functionId, progress.coerceIn(0f, 1f), position(), componentId)) {
        return@drawWithContent
      }
    }
    drawContent()
  }
}

/** The custom visibility functions of the document being played; null outside a player. */
internal val LocalRcVisibilityFunctions = staticCompositionLocalOf<RcVisibilityFunctions?> { null }

/** The spec's visibility duration in whole milliseconds, or 0 when unset or not a finite time. */
internal fun RcAnimationSpec.rcVisibilityDurationMillis(): Int =
  visibilityDurationMillis.value.takeIf { it.isFinite() && it > 0f }?.roundToInt() ?: 0

/**
 * How long a side's visibility animation waits: the motion duration for an `AFTER` side, which
 * plays once the layout has settled, as the embedded player delays it.
 */
internal fun RcAnimationSpec.rcVisibilityDelayMillis(entering: Boolean): Int {
  val sequence = if (entering) enterAnimation.sequence else exitAnimation.sequence
  return if (sequence == RcAnimationSequence.After) rcMotionDurationMillis() else 0
}

/** Plain holder, not state: it is written and read in the same composition. */
private class RcShownVisibility(var value: Int)

/**
 * The spec's visibility curve as a Compose [Easing], sampled from the same [RcAnimationTimeline]
 * the player evaluates every other animated value with.
 */
internal fun RcAnimationSpec.rcVisibilityEasing(): Easing {
  val duration =
    visibilityDurationMillis.value.takeIf { it.isFinite() && it > 0f } ?: return Easing { it }
  val timeline = RcAnimationTimeline(this)
  return Easing { fraction -> timeline.progress(fraction * duration).visibility }
}

/** Holds the content unpainted until the transition ends: AndroidX's non-painting branches. */
private val Hidden = Easing { 0f }

/** Hides the content for the whole exit: AndroidX's `ParticleAnimation` branch. */
private val HiddenAtOnce = Easing { 1f }

/** The enter as a Compose transition, starting [delayMillis] in; `CUSTOM` draws its own. */
internal fun RcAnimationSpec.rcEnterTransition(
  durationMillis: Int,
  easing: Easing,
  delayMillis: Int = 0,
): EnterTransition {
  fun <T> spec(easing: Easing) = tween<T>(durationMillis, delayMillis, easing)
  return when (enterAnimation.androidXValue) {
    RcLayoutAnimation.FadeIn.wireValue -> fadeIn(spec(easing))
    RcLayoutAnimation.SlideLeft.wireValue -> slideInHorizontally(spec(easing)) { it }
    RcLayoutAnimation.SlideRight.wireValue -> slideInHorizontally(spec(easing)) { -it }
    RcLayoutAnimation.SlideTop.wireValue -> slideInVertically(spec(easing)) { it }
    RcLayoutAnimation.SlideBottom.wireValue -> slideInVertically(spec(easing)) { -it }
    RcLayoutAnimation.Rotate.wireValue ->
      fadeIn(spec(easing)) + scaleIn(spec(easing), initialScale = 0f)
    RcLayoutAnimation.Custom.wireValue -> EnterTransition.None
    // FADE_OUT and PARTICLE as an enter paint nothing until they land.
    else -> fadeIn(spec(Hidden))
  }
}

/** The exit as a Compose transition, starting [delayMillis] in; `CUSTOM` draws its own. */
internal fun RcAnimationSpec.rcExitTransition(
  durationMillis: Int,
  easing: Easing,
  delayMillis: Int = 0,
): ExitTransition {
  fun <T> spec(easing: Easing) = tween<T>(durationMillis, delayMillis, easing)
  return when (exitAnimation.androidXValue) {
    RcLayoutAnimation.FadeOut.wireValue -> fadeOut(spec(easing))
    RcLayoutAnimation.SlideLeft.wireValue -> slideOutHorizontally(spec(easing)) { -it }
    RcLayoutAnimation.SlideRight.wireValue -> slideOutHorizontally(spec(easing)) { it }
    RcLayoutAnimation.SlideTop.wireValue -> slideOutVertically(spec(easing)) { -it }
    RcLayoutAnimation.SlideBottom.wireValue -> slideOutVertically(spec(easing)) { it }
    RcLayoutAnimation.Custom.wireValue -> ExitTransition.None
    // AndroidX routes PARTICLE, ROTATE, FADE_IN and unknowns to ParticleAnimation, which stops
    // painting the component at once and keeps its place until the exit ends.
    else -> fadeOut(spec(HiddenAtOnce))
  }
}

/**
 * `ROTATE`'s full turn as it enters, on the enter transition's own clock. Only the enter turns: the
 * exit keeps the finished angle, so whatever exit the spec names runs without a reverse turn.
 */
@Composable
internal fun AnimatedVisibilityScope.rcEnterRotation(
  durationMillis: Int,
  easing: Easing,
  delayMillis: Int = 0,
): Modifier {
  val degrees by
    transition.animateFloat(transitionSpec = { tween(durationMillis, delayMillis, easing) }) {
      if (it == EnterExitState.PreEnter) 0f else 360f
    }
  return Modifier.graphicsLayer { rotationZ = degrees }
}
