/// How a linear or radial gradient continues past its own interval, as AndroidX's tile modes read
/// the wire: 0 clamp, 1 repeat, 2 mirror, 3 decal, anything else clamp.
///
/// Core Graphics can only clamp (extend the end colours) or stop at the ends, so repeat and mirror
/// are laid out as one gradient over every period the shape can reach: the stops of each period in
/// turn, reversed in the odd periods of a mirror. Kept free of Core Graphics so the layout is
/// testable on its own.
enum NativeGradientTiling {
  static let clamp = 0
  static let repeated = 1
  static let mirror = 2
  static let decal = 3

  /// The most periods one tiled gradient lays out. Past this the gradient is drawn clamped: a
  /// period that small is below a pixel on any real canvas.
  static let maximumPeriods = 256

  /// The whole periods covering the gradient parameter range `lower...upper`, or nil when there
  /// are too many to lay out or the range is not finite.
  static func periods(lower: Double, upper: Double) -> (first: Int, last: Int)? {
    guard lower.isFinite, upper.isFinite, lower <= upper else { return nil }
    let first = lower.rounded(.down)
    let last = max(upper.rounded(.up), first + 1)
    guard last - first <= Double(maximumPeriods) else { return nil }
    return (Int(first), Int(last))
  }

  /// The colour stops of the gradient repeated over periods `first..<last`, as indices into the
  /// original colours and locations normalised to the whole span.
  ///
  /// `stops` are the original locations, one per colour. Each period is padded with the first and
  /// last colours at 0 and 1, so a gradient whose stops do not span the whole interval still holds
  /// its end colours up to the period boundary, as a clamped one does.
  static func layout(
    stops: [Double], mirror: Bool, first: Int, last: Int
  ) -> [(colorIndex: Int, location: Double)] {
    guard !stops.isEmpty, last > first else { return [] }
    var period: [(colorIndex: Int, location: Double)] = stops.enumerated().map {
      (colorIndex: $0.offset, location: min(max($0.element, 0), 1))
    }
    if let head = period.first, head.location > 0 {
      period.insert((colorIndex: head.colorIndex, location: 0), at: 0)
    }
    if let tail = period.last, tail.location < 1 {
      period.append((colorIndex: tail.colorIndex, location: 1))
    }
    let reversed = period.reversed().map { (colorIndex: $0.colorIndex, location: 1 - $0.location) }
    let span = Double(last - first)
    var result: [(colorIndex: Int, location: Double)] = []
    for index in first..<last {
      let flipped = mirror && (index % 2 != 0)
      for stop in flipped ? reversed : period {
        result.append(
          (colorIndex: stop.colorIndex, location: (Double(index - first) + stop.location) / span))
      }
    }
    return result
  }
}
