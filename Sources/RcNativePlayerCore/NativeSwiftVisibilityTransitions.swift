import Foundation

/// How one component's change of visibility plays, as remote-core 1.0.0-alpha22's
/// `AnimateMeasure` plays it: which phase it is in, how far its visibility animation has got, and
/// whether the component still holds the layout space it is leaving.
///
/// A transition is an enter (gone or invisible to visible) or an exit (visible to gone or
/// invisible). Its visibility animation runs over the spec's visibility duration on its visibility
/// curve, 0 to 1 both ways; the side's `NativeSwiftAnimationSequence` orders it against the layout
/// change, which takes the motion duration:
///
/// - `concurrent`: the visibility animation starts at once, and the transition ends when both the
///   visibility animation and the motion are over.
/// - `before`: the visibility animation starts at once and the transition ends with it. An exit
///   keeps its layout space until then (`Component.isHoldingLayoutForBeforeAnimation`); an enter
///   takes its space at once, as the reference measures it (`getMeasureVisibility` holds only an
///   exit).
/// - `after`: the visibility animation waits out the motion duration — an entering component
///   paints nothing meanwhile, an exiting one paints as it is — and the transition ends once it
///   has then played.
///
/// This player has no layout motion: a component's space opens or closes at once, and the motion
/// duration is only the time an `after` side waits.
struct NativeSwiftVisibilityTimeline: Equatable {
  let spec: NativeSwiftAnimationSpec
  let from: Int
  let to: Int

  /// Whether a change from `from` to `to` is a transition at all: one side visible and the other
  /// not. A change between gone and invisible paints nothing either side, and plays nothing.
  static func isTransition(from: Int, to: Int) -> Bool {
    from != to && (from == NativeSwiftVisibility.visible || to == NativeSwiftVisibility.visible)
  }

  var isEntering: Bool { to == NativeSwiftVisibility.visible }

  /// The side's `NativeSwiftAnimationSequence`.
  var sequence: Int { isEntering ? spec.enterSequence : spec.exitSequence }

  struct State: Equatable {
    let phase: NativeSwiftVisibilityTransitionSnapshot.Phase
    /// The eased visibility progress, 0 to 1.
    let progress: Float
    /// The visibility the component lays out with: `from` while an exit holds its space.
    let layoutVisibility: Int
    let isDone: Bool
  }

  /// The transition `elapsedMillis` after it began. Time before the start reads as the start.
  func state(elapsedMillis rawElapsed: Float) -> State {
    let elapsed = rawElapsed.isFinite ? max(rawElapsed, 0) : 0
    let motion = spec.motionDuration
    let visibility = spec.visibilityDuration
    func eased(_ time: Float) -> Float {
      // A zero duration has nothing to animate: the reference's curve reads past its end.
      guard visibility > 0 else { return 1 }
      let fraction = min(max(time / visibility, 0), 1)
      return NativeSwiftFloatAnimationRuntime.easedFraction(
        fraction, easingType: spec.visibilityEasingType)
    }
    let phase: NativeSwiftVisibilityTransitionSnapshot.Phase
    let progress: Float
    let isDone: Bool
    switch sequence {
    case NativeSwiftAnimationSequence.after:
      if elapsed < motion {
        phase = isEntering ? .waiting : .holding
        progress = 0
      } else {
        phase = .playing
        progress = eased(elapsed - motion)
      }
      isDone = elapsed >= motion + visibility
    case NativeSwiftAnimationSequence.before:
      phase = .playing
      progress = eased(elapsed)
      isDone = elapsed >= visibility
    default:
      phase = .playing
      progress = eased(elapsed)
      isDone = elapsed >= max(motion, visibility)
    }
    let holdsSpace =
      !isEntering && sequence == NativeSwiftAnimationSequence.before && !isDone
    return State(
      phase: phase, progress: progress, layoutVisibility: holdsSpace ? from : to, isDone: isDone)
  }
}

/// A component's visibility transition in one frame: the custom visibility animation a host plays
/// in place of the component's own drawing.
///
/// Only a side whose animation is `NativeSwiftLayoutAnimation.custom` is reported. The built-in
/// animations — fades, slides, rotate, particles — are not run by this player, so a component with
/// one changes visibility at once, as it always has. While a transition is reported the component
/// lays out as `NativeSwiftNodeSnapshot.visibility` says (its old visibility while an exit holds
/// its space) and the host draws it according to `phase`:
///
/// - `waiting`: nothing at all;
/// - `holding`: the component as it is, at its old bounds;
/// - `playing`: `function`'s drawing at `progress`; without a function — the side names none, or
///   one the document does not define — the component as it is, as the reference paints it.
///
/// An exiting component keeps the bounds it last had while its space closes, as the reference
/// paints it at its original bounds.
public struct NativeSwiftVisibilityTransitionSnapshot: Sendable {
  public enum Phase: Sendable, Equatable {
    /// An `after` enter waiting for the motion duration: the component paints nothing.
    case waiting
    /// An `after` exit waiting for the motion duration: the component paints as it is.
    case holding
    /// The visibility animation is playing.
    case playing
  }

  /// The visibility the component is changing from and to.
  public let from: Int
  public let to: Int
  public let phase: Phase
  /// The eased visibility progress, 0 to 1 on the way in and on the way out alike.
  public let progress: Float
  /// The side's function id as the spec names it, or `NativeSwiftFunctionDefine.unsetID`.
  public let functionID: Int
  /// The function to draw the component with while `playing`, bound to this frame's values, or nil
  /// when the document defines no function under `functionID`.
  public let function: NativeSwiftVisibilityFunction?

  public var isEntering: Bool { to == NativeSwiftVisibility.visible }
}

/// A custom visibility animation's `FloatFunctionDefine`, bound to one frame's values, ready to be
/// called for one component.
///
/// The host calls it with the component's bounds in its parent once it has laid the component out,
/// and replays the commands it returns in the parent's space, from a reset paint, instead of the
/// component's own drawing. Its `NativeSwiftDrawKind.drawContent` commands draw the component,
/// clipped to its bounds in a layer at the command's alpha, or — inside a component offscreen
/// target — the target's component at the target's origin.
public struct NativeSwiftVisibilityFunction: @unchecked Sendable {
  // `@unchecked` because the parsed body holds the decoder's command values, which are immutable
  // once decoded but are not declared `Sendable`. Nothing here is mutated after construction.
  let function: ParsedFunction
  let values: [Int: Float]
  let integers: [Int: Int]
  let colors: [Int: UInt32]
  let texts: [Int: String]
  let matrices: [Int: ParsedMatrixExpression]
  /// The progress and component the call passes, which the frame decides.
  public let progress: Float
  public let componentID: Int

  public var id: Int { function.id }

  /// The arguments a call passes for a component at `x`, `y` and `width` by `height` in its parent,
  /// in `NativeSwiftVisibilityFunctionArgument` order, as `AnimateMeasure.executeCustomAnimation`
  /// computes them: the origin floored to a whole pixel and the size widened to cover the
  /// component from there.
  public func arguments(x: Float, y: Float, width: Float, height: Float) -> [Float] {
    let left = x.rounded(.down)
    let top = y.rounded(.down)
    var arguments = [Float](repeating: 0, count: NativeSwiftVisibilityFunctionArgument.count)
    arguments[NativeSwiftVisibilityFunctionArgument.progress] = progress
    arguments[NativeSwiftVisibilityFunctionArgument.width] = (x - left + width).rounded(.up)
    arguments[NativeSwiftVisibilityFunctionArgument.height] = (y - top + height).rounded(.up)
    arguments[NativeSwiftVisibilityFunctionArgument.x] = left
    arguments[NativeSwiftVisibilityFunctionArgument.y] = top
    arguments[NativeSwiftVisibilityFunctionArgument.componentID] = Float(componentID)
    return arguments
  }

  /// The function's drawing for a component at `x`, `y` and `width` by `height` in its parent.
  ///
  /// The arguments are loaded into the function's argument ids — as many as it declares — the
  /// component id as an integer too, then the body's expressions run in wire order and its
  /// commands resolve against the result. Throws when an expression does not evaluate to a finite
  /// number or a command cannot resolve; a host then paints the component as it is.
  public func commands(x: Float, y: Float, width: Float, height: Float) throws
    -> [NativeSwiftDrawCommandSnapshot]
  {
    var values = values
    var integers = integers
    let arguments = arguments(x: x, y: y, width: width, height: height)
    for (index, id) in function.argumentIDs.prefix(arguments.count).enumerated() {
      values[id] = arguments[index]
      if index == NativeSwiftVisibilityFunctionArgument.componentID {
        integers[id] = componentID
      }
    }
    for expression in function.expressions {
      values[expression.id] = try NativeSwiftFloatExpression.evaluate(
        expression.words, values: values, opcode: expression.opcode, offset: expression.offset)
    }
    return try function.commands.map {
      try $0.resolve(
        values: values, integers: integers, colors: colors, texts: texts, matrices: matrices)
    }
  }
}

/// The visibility transitions a session is playing, by component id; see
/// `NativeSwiftVisibilityTimeline`.
struct NativeSwiftVisibilityTracker {
  struct Track {
    /// The visibility the document last asked for.
    var target: Int
    var timeline: NativeSwiftVisibilityTimeline?
    var startSeconds: TimeInterval = 0
  }

  private(set) var tracks: [Int: Track] = [:]
  /// Whether visibility changes animate at all: the reference's `isAnimationEnabled`. A host turns
  /// it off under Reduce Motion, where its frames stop, and every change then lands at once.
  var animationsEnabled = true {
    didSet {
      guard !animationsEnabled else { return }
      for id in tracks.keys { tracks[id]?.timeline = nil }
    }
  }

  var hasActiveTransition: Bool { tracks.values.contains { $0.timeline != nil } }

  /// Records that component `id` asks for `visibility` at `timeSeconds` and returns the transition
  /// it is in, if any, with its state then.
  ///
  /// The first observation of a component never animates, as the reference's first layout does
  /// not. A change while a transition plays retargets it from the visibility it was heading to,
  /// starting again, as `AnimateMeasure.updateTarget` does. Only a change whose side is `CUSTOM`
  /// starts one (see `NativeSwiftVisibilityTransitionSnapshot`), and a spec whose id is 0 — the
  /// reference's `AnimationSpec.DISABLED` — never does.
  mutating func observe(
    id: Int, visibility: Int, spec: NativeSwiftAnimationSpec?, specID: Int?,
    timeSeconds: TimeInterval
  ) -> (timeline: NativeSwiftVisibilityTimeline, state: NativeSwiftVisibilityTimeline.State)? {
    guard var track = tracks[id] else {
      tracks[id] = Track(target: visibility)
      return nil
    }
    if visibility != track.target {
      let from = track.target
      track.target = visibility
      track.timeline = nil
      if animationsEnabled, specID != 0, let spec,
        NativeSwiftVisibilityTimeline.isTransition(from: from, to: visibility)
      {
        let timeline = NativeSwiftVisibilityTimeline(spec: spec, from: from, to: visibility)
        let kind = timeline.isEntering ? spec.enterKind : spec.exitKind
        if kind == NativeSwiftLayoutAnimation.custom {
          track.timeline = timeline
          track.startSeconds = timeSeconds
        }
      }
    }
    defer { tracks[id] = track }
    guard let timeline = track.timeline else { return nil }
    let state = timeline.state(elapsedMillis: Float((timeSeconds - track.startSeconds) * 1_000))
    guard !state.isDone else {
      track.timeline = nil
      return nil
    }
    return (timeline, state)
  }
}
