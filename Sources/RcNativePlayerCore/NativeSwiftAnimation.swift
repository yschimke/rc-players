import Foundation

/// AndroidX `FloatAnimation` easing ids, from the authoritative Easing wire implementation.
enum NativeSwiftFloatEasingType: UInt32 {
  case cubicStandard = 1
  case cubicAccelerate = 2
  case cubicDecelerate = 3
  case cubicLinear = 4
  case cubicAnticipate = 5
  case cubicOvershoot = 6
  case cubicCustom = 11
  case splineCustom = 12
  case easeOutBounce = 13
  case easeOutElastic = 14

  var acceptsParameters: Bool {
    self == .cubicCustom || self == .splineCustom
  }
}

enum NativeSwiftFloatDirectionalSnap: Int {
  case none = 0
  case snapOnDecrease = 1
  case snapOnIncrease = 2
}

/// Known spring boundary modes are the two independent edge flags and their combination.
enum NativeSwiftSpringBoundaryMode: Int {
  case none = 0
  case lower = 1
  case upper = 2
  case lowerAndUpper = 3

  var includesLower: Bool { self == .lower || self == .lowerAndUpper }
  var includesUpper: Bool { self == .upper || self == .lowerAndUpper }
}

enum NativeSwiftFloatAnimationMetadata {
  static let springMarker: Float = 0
  static let springDescriptorWordCount = 5
  static let easingTypeMask: UInt32 = 0xff
  static let wrapFlag: UInt32 = 1 << 8
  static let initialValueFlag: UInt32 = 1 << 9
  static let directionalSnapShift = 10
  static let directionalSnapMask: UInt32 = 0x3
  static let propagationFlag: UInt32 = 1 << 12
  static let parameterCountShift = 16
}

/// Stateful evaluator for AndroidX's optional animation payload on a float expression.
final class NativeSwiftFloatAnimationRuntime {
  private enum Curve {
    case cubic(Float, Float, Float, Float)
    case bounce
    case elastic
    case spline(NativeSwiftMonotonicCurve)

    func value(at x: Float) -> Float {
      switch self {
      case .cubic(let x1, let y1, let x2, let y2):
        return Self.cubic(x, x1: x1, y1: y1, x2: x2, y2: y2)
      case .bounce:
        let n: Float = 7.5625
        let d: Float = 2.75
        if x < 0 { return 0 }
        if x < 1 / d { return (n * x * x + x) / (1 + 1 / d) }
        if x < 2 / d {
          let t = x - 1.5 / d
          return n * t * t + 0.75
        }
        if x < 2.5 / d {
          let t = x - 2.25 / d
          return n * t * t + 0.9375
        }
        if x <= 1 {
          let t = x - 2.625 / d
          return n * t * t + 0.984375
        }
        return 1
      case .elastic:
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        return powf(2, -10 * x) * sinf((x * 10 - 0.75) * (2 * .pi / 3)) + 1
      case .spline(let curve):
        return x < 0 ? 0 : (x > 1 ? 1 : curve.value(at: Double(x)))
      }
    }

    private static func cubic(
      _ x: Float, x1: Float, y1: Float, x2: Float, y2: Float
    ) -> Float {
      if x <= 0 { return 0 }
      if x >= 1 { return 1 }
      func coordinate(_ t: Float, _ first: Float, _ second: Float) -> Float {
        let inverse = 1 - t
        return first * 3 * inverse * inverse * t
          + second * 3 * inverse * t * t + t * t * t
      }
      var t: Float = 0.5
      var range: Float = 0.5
      while range > 0.01 {
        let tx = coordinate(t, x1, x2)
        range *= 0.5
        if tx < x { t += range } else { t -= range }
      }
      let lowerX = coordinate(t - range, x1, x2)
      let upperX = coordinate(t + range, x1, x2)
      let lowerY = coordinate(t - range, y1, y2)
      let upperY = coordinate(t + range, y1, y2)
      guard upperX != lowerX else { return lowerY }
      return (upperY - lowerY) * (x - lowerX) / (upperX - lowerX) + lowerY
    }
  }

  private let duration: Float
  private let curve: Curve?
  private let wrap: Float?
  private let directionalSnap: NativeSwiftFloatDirectionalSnap
  private var initialValue: Float
  private var targetValue = Float.nan
  private var lastTarget = Float.nan
  private var lastChange = Float.nan

  private let springStiffness: Double?
  private let springDamping: Double
  private let springStopThreshold: Double
  private let springBoundaryMode: NativeSwiftSpringBoundaryMode
  private var springTarget = 0.0
  private var springPosition: Float = 0
  private var springVelocity: Float = 0
  private var springLastTime: Float = 0

  init(animationWords words: [UInt32], offset: Int = 0) throws {
    func malformed(_ reason: String) -> NativeSwiftCoreError {
      .malformed(offset: offset, reason: "invalid float animation: \(reason)")
    }

    guard !words.isEmpty else { throw malformed("missing animation duration") }
    let first = Float(bitPattern: words[0])
    let isSpring =
      words.count >= NativeSwiftFloatAnimationMetadata.springDescriptorWordCount
      && first == NativeSwiftFloatAnimationMetadata.springMarker
    if isSpring {
      let stiffness = Double(Float(bitPattern: words[1]))
      let damping = Double(Float(bitPattern: words[2]))
      let threshold = Double(Float(bitPattern: words[3]))
      let boundaryModeValue = Int(Int32(bitPattern: words[4]))
      guard words.count == NativeSwiftFloatAnimationMetadata.springDescriptorWordCount else {
        throw malformed("spring descriptor must have five words")
      }
      guard let boundaryMode = NativeSwiftSpringBoundaryMode(rawValue: boundaryModeValue) else {
        throw malformed("unknown spring boundary mode \(boundaryModeValue)")
      }
      guard stiffness.isFinite, stiffness > 0, damping.isFinite, damping >= 0,
        threshold.isFinite, threshold > 0
      else { throw malformed("invalid spring parameters") }
      duration = 0
      curve = nil
      wrap = nil
      directionalSnap = .none
      initialValue = .nan
      springStiffness = stiffness
      springDamping = damping
      springStopThreshold = threshold
      springBoundaryMode = boundaryMode
      return
    }

    guard first.isFinite, first > 0 else { throw malformed("duration must be finite and positive") }
    // AndroidX accepts a one-word description as the compact default animation: duration followed
    // by implicit CUBIC_STANDARD metadata. Catalog exports use this form for otherwise-static
    // values that participate in a component transition. Requiring the optional metadata word
    // rejected those documents before UIKit could install a view.
    let hasExplicitMetadata = words.count > 1
    let metadata = hasExplicitMetadata ? words[1] : NativeSwiftFloatEasingType.cubicStandard.rawValue
    let easingTypeValue = metadata & NativeSwiftFloatAnimationMetadata.easingTypeMask
    guard let easingType = NativeSwiftFloatEasingType(rawValue: easingTypeValue) else {
      throw NativeSwiftCoreError.unsupported(
        opcode: NativeSwiftWireOpcode.animatedFloat, offset: offset,
        reason: "float animation easing type \(easingTypeValue) is not supported")
    }
    let hasWrap = metadata & NativeSwiftFloatAnimationMetadata.wrapFlag != 0
    let hasInitial = metadata & NativeSwiftFloatAnimationMetadata.initialValueFlag != 0
    let directionalValue =
      Int(
        (metadata >> NativeSwiftFloatAnimationMetadata.directionalSnapShift)
          & NativeSwiftFloatAnimationMetadata.directionalSnapMask)
    guard let directional = NativeSwiftFloatDirectionalSnap(rawValue: directionalValue) else {
      throw malformed("unknown directional snap mode \(directionalValue)")
    }
    let parameterCount = Int(metadata >> NativeSwiftFloatAnimationMetadata.parameterCountShift)
    let parameterStart = hasExplicitMetadata ? 2 : 1
    let tail = parameterStart + parameterCount
    let expectedCount = tail + (hasInitial ? 1 : 0) + (hasWrap ? 1 : 0)
    guard words.count == expectedCount else {
      throw malformed("metadata requires \(expectedCount) words, found \(words.count)")
    }
    let parameters = words[parameterStart..<tail].map { Float(bitPattern: $0) }
    guard parameters.allSatisfy(\.isFinite) else { throw malformed("non-finite curve parameter") }
    let parsedCurve: Curve
    switch easingType {
    case .cubicStandard: parsedCurve = .cubic(0.4, 0, 0.2, 1)
    case .cubicAccelerate: parsedCurve = .cubic(0.4, 0.05, 0.8, 0.7)
    case .cubicDecelerate: parsedCurve = .cubic(0, 0, 0.2, 0.95)
    case .cubicLinear: parsedCurve = .cubic(1, 1, 0, 0)
    case .cubicAnticipate: parsedCurve = .cubic(0.36, 0, 0.66, -0.56)
    case .cubicOvershoot: parsedCurve = .cubic(0.34, 1.56, 0.64, 1)
    case .cubicCustom:
      guard parameters.count == 4 else {
        throw malformed("custom cubic easing needs four parameters")
      }
      parsedCurve = .cubic(parameters[0], parameters[1], parameters[2], parameters[3])
    case .splineCustom:
      guard parameters.count >= 2 else {
        throw malformed("spline easing needs at least two points")
      }
      parsedCurve = .spline(NativeSwiftMonotonicCurve(points: parameters))
    case .easeOutBounce:
      guard parameters.isEmpty else { throw malformed("bounce easing takes no parameters") }
      parsedCurve = .bounce
    case .easeOutElastic:
      guard parameters.isEmpty else { throw malformed("elastic easing takes no parameters") }
      parsedCurve = .elastic
    }
    if !easingType.acceptsParameters, !parameters.isEmpty {
      throw malformed("preset easing type \(easingType.rawValue) takes no parameters")
    }
    let initialIndex = tail
    let wrapIndex = tail + (hasInitial ? 1 : 0)
    let configuredInitial = hasInitial ? Float(bitPattern: words[initialIndex]) : .nan
    let configuredWrap = hasWrap ? Float(bitPattern: words[wrapIndex]) : .nan
    guard !hasInitial || configuredInitial.isFinite,
      !hasWrap || (configuredWrap.isFinite && configuredWrap > 0)
    else { throw malformed("invalid initial or wrap value") }

    duration = first
    curve = parsedCurve
    wrap = hasWrap ? configuredWrap : nil
    directionalSnap = directional
    initialValue = configuredInitial
    springStiffness = nil
    springDamping = 0
    springStopThreshold = 0
    springBoundaryMode = .none
  }

  private init(copying other: NativeSwiftFloatAnimationRuntime) {
    duration = other.duration
    curve = other.curve
    wrap = other.wrap
    directionalSnap = other.directionalSnap
    initialValue = other.initialValue
    targetValue = other.targetValue
    lastTarget = other.lastTarget
    lastChange = other.lastChange
    springStiffness = other.springStiffness
    springDamping = other.springDamping
    springStopThreshold = other.springStopThreshold
    springBoundaryMode = other.springBoundaryMode
    springTarget = other.springTarget
    springPosition = other.springPosition
    springVelocity = other.springVelocity
    springLastTime = other.springLastTime
  }

  func detachedCopy() -> NativeSwiftFloatAnimationRuntime {
    NativeSwiftFloatAnimationRuntime(copying: self)
  }

  func evaluate(target: Float, at time: Float) -> Float {
    if let springStiffness {
      if target != lastTarget {
        springTarget = Double(target)
        lastTarget = target
        lastChange = time
      }
      if lastChange.isNaN { lastChange = time }
      integrateSpring(to: time, stiffness: springStiffness)
      if springIsStopped(stiffness: springStiffness) { springPosition = Float(springTarget) }
      return springPosition
    }

    if target != lastTarget {
      if lastTarget.isNaN {
        setTarget(target)
        if initialValue.isNaN { setInitial(target) }
      } else {
        setInitial(targetValue)
        setTarget(target)
      }
      lastTarget = target
      lastChange = time
    }
    if lastChange.isNaN { lastChange = time }
    let progress = (time - lastChange) / duration
    if directionalSnap == .snapOnDecrease, targetValue < initialValue {
      initialValue = targetValue
      return targetValue
    }
    if directionalSnap == .snapOnIncrease, targetValue > initialValue {
      initialValue = targetValue
      return targetValue
    }
    return curve!.value(at: progress) * (targetValue - initialValue) + initialValue
  }

  func isAnimating(at time: Float) -> Bool {
    if let springStiffness { return !springIsStopped(stiffness: springStiffness) }
    return !initialValue.isNaN && !targetValue.isNaN && initialValue != targetValue
      && time - lastChange < duration
  }

  private func setInitial(_ value: Float) {
    initialValue = wrap.map { value.truncatingRemainder(dividingBy: $0) } ?? value
  }

  private func setTarget(_ value: Float) {
    targetValue = value
    guard let wrap else { return }
    initialValue = positiveWrap(initialValue, by: wrap)
    targetValue = positiveWrap(targetValue, by: wrap)
    if initialValue.isNaN { initialValue = targetValue }
    let distance = wrapDistance(from: initialValue, to: targetValue, wrap: wrap)
    if distance > 0, targetValue < initialValue {
      targetValue += wrap
    } else if distance < 0, directionalSnap != .none {
      if directionalSnap == .snapOnDecrease, targetValue > initialValue {
        initialValue = targetValue
      }
      if directionalSnap == .snapOnIncrease, targetValue < initialValue {
        initialValue = targetValue
      }
      targetValue -= wrap
    }
  }

  private func positiveWrap(_ value: Float, by wrap: Float) -> Float {
    let remainder = value.truncatingRemainder(dividingBy: wrap)
    return remainder < 0 ? remainder + wrap : remainder
  }

  private func wrapDistance(from: Float, to: Float, wrap: Float) -> Float {
    var delta = (to - from).truncatingRemainder(dividingBy: wrap)
    if delta < -wrap / 2 { delta += wrap } else if delta > wrap / 2 { delta -= wrap }
    return delta
  }

  private func springIsStopped(stiffness: Double) -> Bool {
    let displacement = Double(springPosition) - springTarget
    let velocity = Double(springVelocity)
    let energy = velocity * velocity + stiffness * displacement * displacement
    return sqrt(energy / stiffness) <= springStopThreshold
  }

  private func integrateSpring(to time: Float, stiffness: Double) {
    let delta = Double(time - springLastTime)
    springLastTime = time
    guard delta > 0 else { return }
    let requestedSteps = 1 + 9 / (sqrt(stiffness) * delta * 4)
    let steps =
      requestedSteps >= 1_000 || !requestedSteps.isFinite
      ? 1_000 : max(Int(requestedSteps), 1)
    let dt = delta / Double(steps)
    for _ in 0..<steps {
      let position = Double(springPosition)
      let velocity = Double(springVelocity)
      let displacement = position - springTarget
      let acceleration = -stiffness * displacement - springDamping * velocity
      let averageVelocity = velocity + acceleration * dt / 2
      let averageDisplacement = position + dt * averageVelocity / 2 - springTarget
      let adjustedAcceleration = -stiffness * averageDisplacement - springDamping * averageVelocity
      let velocityDelta = adjustedAcceleration * dt
      let adjustedAverageVelocity = velocity + velocityDelta / 2
      springVelocity += Float(velocityDelta)
      springPosition += Float(adjustedAverageVelocity * dt)
      if springBoundaryMode.includesLower, springPosition < 0 {
        springPosition = -springPosition
        springVelocity = -springVelocity
      }
      if springBoundaryMode.includesUpper, springPosition > 1 {
        springPosition = 2 - springPosition
        springVelocity = -springVelocity
      }
    }
  }
}

/// AndroidX's monotonic spline fit used by custom spline easing descriptors.
struct NativeSwiftMonotonicCurve {
  private let times: [Double]
  private let values: [Double]
  private let tangents: [Double]

  init(points: [Float]) {
    let sourceCount = points.count
    let offset = sourceCount - 1
    let pointCount = sourceCount * 3 - 2
    let gap = 1.0 / Double(offset)
    var times = Array(repeating: 0.0, count: pointCount)
    var values = Array(repeating: 0.0, count: pointCount)
    for (index, point) in points.enumerated() {
      let value = Double(point)
      values[index + offset] = value
      times[index + offset] = Double(index) * gap
      if index > 0 {
        values[index + offset * 2] = value + 1
        times[index + offset * 2] = Double(index) * gap + 1
        values[index - 1] = value - 1 - gap
        times[index - 1] = Double(index) * gap - 1 - gap
      }
    }
    var slopes = Array(repeating: 0.0, count: pointCount - 1)
    var tangents = Array(repeating: 0.0, count: pointCount)
    for index in slopes.indices {
      slopes[index] = (values[index + 1] - values[index]) / (times[index + 1] - times[index])
      tangents[index] = index == 0 ? slopes[index] : (slopes[index - 1] + slopes[index]) * 0.5
    }
    tangents[tangents.count - 1] = slopes[slopes.count - 1]
    for index in slopes.indices {
      if slopes[index] == 0 {
        tangents[index] = 0
        tangents[index + 1] = 0
      } else {
        let a = tangents[index] / slopes[index]
        let b = tangents[index + 1] / slopes[index]
        let magnitude = hypot(a, b)
        if magnitude > 9 {
          let scale = 3 / magnitude
          tangents[index] = scale * a * slopes[index]
          tangents[index + 1] = scale * b * slopes[index]
        }
      }
    }
    self.times = times
    self.values = values
    self.tangents = tangents
  }

  func value(at position: Double) -> Float {
    // A NaN progress fails every comparison below, so the segment search found nothing and the
    // force unwrap trapped. AndroidX's `getPos` falls through its loop and answers 0.
    guard !position.isNaN else { return 0 }
    if position <= times[0] { return Float(values[0] + (position - times[0]) * tangents[0]) }
    if position >= times[times.count - 1] {
      return Float(
        values[values.count - 1] + (position - times[times.count - 1])
          * tangents[tangents.count - 1])
    }
    guard let index = times.indices.dropLast().first(where: { position < times[$0 + 1] }) else {
      return 0
    }
    let h = times[index + 1] - times[index]
    let x = (position - times[index]) / h
    let x2 = x * x
    let x3 = x2 * x
    let value =
      -2 * x3 * values[index + 1] + 3 * x2 * values[index + 1]
      + 2 * x3 * values[index] - 3 * x2 * values[index] + values[index]
      + h * tangents[index + 1] * x3 + h * tangents[index] * x3
      - h * tangents[index + 1] * x2 - 2 * h * tangents[index] * x2
      + h * tangents[index] * x
    return Float(value)
  }
}
