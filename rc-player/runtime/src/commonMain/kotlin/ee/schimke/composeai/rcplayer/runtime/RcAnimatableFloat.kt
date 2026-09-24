package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerAttribute
import ee.schimke.composeai.rcplayer.protocol.RcGraphicsLayerModifier

/**
 * AndroidX `AnimatableValue`: the implicit tween a layout modifier's float attribute gets when the
 * variable behind it moves.
 *
 * `GraphicsLayerModifierOperation` wraps every one of its float attributes in an `AnimatableValue`,
 * so in AndroidX a graphics layer bound to a variable does not jump when the variable changes — it
 * eases to the new value. Nothing in the wire format says so: the behaviour lives in the player, so
 * a player that resolves the attribute straight through is not "simpler", it draws a different
 * animation from the AndroidX lanes.
 *
 * Two rules come with the curve, and they matter more than the curve does:
 *
 * * **No animation on first appearance.** The first value a variable takes is the document's
 *   opening pose. Only changes after it ease.
 * * **A source that moves faster than the tween is followed, not chased.** A clock-driven or
 *   dragged attribute changes every frame; easing towards each new target in turn would draw it a
 *   third of a second behind itself, so a target that moves again inside one duration snaps.
 *
 * ### Which AndroidX this follows, and why not the other one
 *
 * `remote-core`'s own `AnimatableValue` is the normative-looking implementation and is not the one
 * to copy. Two things are wrong with it as written:
 *
 * * It applies the cadence test to the gap between the *rendered value* and the target rather than
 *   between successive targets. A tween's whole middle is a frame where the two differ, so the
 *   frame after a tween starts measures a frame-length interval, concludes "continuous source", and
 *   snaps. The tween it just started never runs.
 * * It intends 300ms (`mAnimateDuration = 300f`) but evaluates `FloatAnimation.get(elapsed / 300f)`
 *   against a *0.3-second* curve, which would complete in 90ms if it ran at all.
 *
 * `remote-player-compose`'s embedded player reimplemented the behaviour on `animateFloatAsState` in
 * androidx-main `4969cdd96c6`, at the full 300ms, and that is the behaviour this follows: a tween
 * that survives its own frames. The continuous-source bypass is measured here — the interval
 * between successive targets — where upstream infers it statically from the expression graph,
 * because a player with a frame clock can observe the cadence it would otherwise have to predict.
 *
 * Kept outside Compose so the Wasm and Apple hosts run the same curve and a conformance trace can
 * step it frame by frame.
 */
public class RcAnimatableFloat(
  private val durationSeconds: Float = DEFAULT_DURATION_SECONDS,
  private val easingType: Int = CUBIC_STANDARD,
) {
  private val easing =
    RcFloatAnimation(listOf(RcFloatWord.literal(durationSeconds), RcFloatWord(easingType))).also {
      it.setInitial(0f)
      it.setTarget(1f)
    }

  private var value = 0f
  private var startValue = 0f
  private var targetValue = 0f
  private var animating = false
  /**
   * NaN until the clock moves past [changeSeconds], and then the tween's real zero.
   *
   * Not the clock reading the change arrives on. A player only runs its clock for frames it was
   * asked for, so a host writing to an idle document sees whatever the clock read when the document
   * went idle — arbitrarily long ago. Stamping there would count the whole idle stretch as the
   * tween's first step and finish it on the frame that was supposed to start it. Waiting for a
   * clock that has moved also survives the several recompositions one write can cause, which all
   * share a frame and must not each restake the start.
   */
  private var animationStartSeconds = Float.NaN
  /** The (possibly stale) clock the retarget was noticed on. */
  private var changeSeconds = Float.NaN
  /** NaN is the "nothing has landed yet" marker, which is what suppresses the first tween. */
  private var lastTarget = Float.NaN

  /** Whether this value still needs another frame to reach its target. */
  public val isAnimating: Boolean
    get() = animating

  /**
   * The attribute's value at [nowSeconds], easing towards [target] when [target] is a variable the
   * document moved.
   *
   * [animatable] is false for an attribute that must not ease: a literal, which cannot change
   * without the document itself being replaced, and a variable the document keeps moving on its own
   * (see [RcPlayerState.isContinuouslyDriven]), which has to be followed rather than chased.
   */
  public fun evaluate(target: Float, animatable: Boolean, nowSeconds: Float): Float {
    if (!animatable) {
      animating = false
      lastTarget = target
      value = target
      return value
    }
    if (lastTarget.isNaN()) {
      // First landing: the document's opening pose, not a change to ease into.
      lastTarget = target
      value = target
      return value
    }
    if (target != lastTarget) {
      // Retargeting mid-flight eases on from wherever the value is, rather than restarting.
      lastTarget = target
      startValue = value
      targetValue = target
      animationStartSeconds = Float.NaN
      changeSeconds = nowSeconds
      animating = true
    }
    if (animating) {
      if (animationStartSeconds.isNaN()) {
        if (nowSeconds <= changeSeconds) {
          value = startValue
          return value
        }
        animationStartSeconds = nowSeconds
        value = startValue
        return value
      }
      val progress = easing.value(nowSeconds - animationStartSeconds).coerceIn(0f, 1f)
      value = (1f - progress) * startValue + progress * targetValue
      if (progress >= 1f) {
        value = targetValue
        animating = false
      }
    }
    return value
  }

  public companion object {
    /** `AnimatableValue.mAnimateDuration`, in the seconds this player's clock counts. */
    public const val DEFAULT_DURATION_SECONDS: Float = 0.3f
    /** `GeneralEasing.CUBIC_STANDARD`, which is `RcFloatAnimation`'s `TYPE_STANDARD`. */
    private const val CUBIC_STANDARD: Int = 1
  }
}

/**
 * The graphics-layer attributes AndroidX animates, resolved for one frame.
 *
 * Defaults are `GraphicsLayerModifierOperation`'s own except the origin, which is **centred** — and
 * that is a reading of AndroidX's behaviour, not a deviation from it. `fillInAttributes` only
 * records an attribute whose value differs from its declared default, and `setGraphicsLayer`
 * applies only the keys it is handed, so an absent origin never reaches the layer at all and the
 * layer keeps its own pivot, which is its centre. Reading the declared `0f` back as a *value*
 * instead would pivot at the top-left, which no `RenderNode`-backed AndroidX lane does.
 *
 * Only an absent attribute is affected. Since androidx-main `4969cdd96c6` the creation side writes
 * a centre origin explicitly (it omits `0f` rather than `0.5f`), so a document that means top-left
 * says so, arrives here as a present attribute, and pivots at the top-left.
 *
 * Tracked as #155 and #153; `RcNativePlayerUIKit` reaches the same default from the same evidence.
 */
public data class RcGraphicsLayerValues(
  val scaleX: Float = 1f,
  val scaleY: Float = 1f,
  val rotationX: Float = 0f,
  val rotationY: Float = 0f,
  val rotationZ: Float = 0f,
  val transformOriginX: Float = 0.5f,
  val transformOriginY: Float = 0.5f,
  val translationX: Float = 0f,
  val translationY: Float = 0f,
  val shadowElevation: Float = 0f,
  val alpha: Float = 1f,
  val cameraDistance: Float = 8f,
  /** Whether any attribute is mid-tween, and the player therefore owes this layer another frame. */
  val isAnimating: Boolean = false,
  /**
   * The layer's outline, one of `RcGraphicsLayerModifier.SHAPE_*`, or -1 when the document set
   * none. It shapes the shadow; the layer does not clip to it.
   */
  val shape: Int = -1,
  /** The corner radius of a `SHAPE_ROUND_RECT` outline, in pixels. */
  val shapeRadius: Float = 0f,
  /** Compose's `CompositingStrategy` value: 0 auto, 1 offscreen, 2 modulate alpha. */
  val compositingStrategy: Int = 0,
  /** A blur applied to the layer's content, when either radius is positive. */
  val blurRadiusX: Float = 0f,
  val blurRadiusY: Float = 0f,
  /** One of `RcGraphicsLayerModifier.TILE_MODE_*`, for the blur's edges. */
  val blurTileMode: Int = 0,
  /** Shadow colours as ARGB, or null for Compose's default black. */
  val ambientShadowColor: Int? = null,
  val spotShadowColor: Int? = null,
)

/**
 * Per-component holder for a graphics layer's [RcAnimatableFloat]s.
 *
 * One per component instance rather than per operation: two components can share an identical
 * `RcGraphicsLayerModifier` and still be mid-tween at different points.
 */
public class RcGraphicsLayerAnimator {
  private val animatables = mutableMapOf<Int, RcAnimatableFloat>()

  public fun evaluate(
    modifier: RcGraphicsLayerModifier,
    state: RcPlayerState,
    nowSeconds: Float = state.animationTimeSeconds,
  ): RcGraphicsLayerValues {
    val attributes = modifier.attributes.associateBy { it.index }
    var animating = false

    fun attribute(index: Int, default: Float): Float {
      val word =
        (attributes[index] as? RcGraphicsLayerAttribute.FloatValue)?.value ?: return default
      val referencedId = word.referencedId
      val animatable = animatables.getOrPut(index) { RcAnimatableFloat() }
      val resolved =
        animatable.evaluate(
          target = state.resolve(word),
          animatable = referencedId != null && !state.isContinuouslyDriven(referencedId),
          nowSeconds = nowSeconds,
        )
      if (animatable.isAnimating) animating = true
      return resolved
    }

    fun int(index: Int): Int? = (attributes[index] as? RcGraphicsLayerAttribute.IntValue)?.value

    val values =
      RcGraphicsLayerValues(
        scaleX = attribute(RcGraphicsLayerModifier.SCALE_X, 1f),
        scaleY = attribute(RcGraphicsLayerModifier.SCALE_Y, 1f),
        rotationX = attribute(RcGraphicsLayerModifier.ROTATION_X, 0f),
        rotationY = attribute(RcGraphicsLayerModifier.ROTATION_Y, 0f),
        rotationZ = attribute(RcGraphicsLayerModifier.ROTATION_Z, 0f),
        transformOriginX = attribute(RcGraphicsLayerModifier.TRANSFORM_ORIGIN_X, 0.5f),
        transformOriginY = attribute(RcGraphicsLayerModifier.TRANSFORM_ORIGIN_Y, 0.5f),
        translationX = attribute(RcGraphicsLayerModifier.TRANSLATION_X, 0f),
        translationY = attribute(RcGraphicsLayerModifier.TRANSLATION_Y, 0f),
        shadowElevation = attribute(RcGraphicsLayerModifier.SHADOW_ELEVATION, 0f),
        alpha = attribute(RcGraphicsLayerModifier.ALPHA, 1f),
        cameraDistance = attribute(RcGraphicsLayerModifier.CAMERA_DISTANCE, 8f),
        shape = int(RcGraphicsLayerModifier.SHAPE) ?: -1,
        shapeRadius = attribute(RcGraphicsLayerModifier.SHAPE_RADIUS, 0f),
        compositingStrategy = int(RcGraphicsLayerModifier.COMPOSITING_STRATEGY) ?: 0,
        blurRadiusX = attribute(RcGraphicsLayerModifier.BLUR_RADIUS_X, 0f),
        blurRadiusY = attribute(RcGraphicsLayerModifier.BLUR_RADIUS_Y, 0f),
        blurTileMode = int(RcGraphicsLayerModifier.BLUR_TILE_MODE) ?: 0,
        ambientShadowColor = int(RcGraphicsLayerModifier.AMBIENT_SHADOW_COLOR),
        spotShadowColor = int(RcGraphicsLayerModifier.SPOT_SHADOW_COLOR),
      )
    return values.copy(isAnimating = animating)
  }
}
