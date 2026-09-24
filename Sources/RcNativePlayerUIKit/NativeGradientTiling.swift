/// How a linear or radial gradient continues past its own interval, as AndroidX's tile modes read
/// the wire: 0 clamp, 1 repeat, 2 mirror, 3 decal, anything else clamp.
///
/// Core Graphics can only clamp (extend the end colours) or stop at the ends, so the renderer draws
/// repeat and mirror one period at a time, each clipped to its own band. This is the arithmetic for
/// that, kept free of Core Graphics so it is testable on its own.
enum NativeGradientTiling {
  static let clamp = 0
  static let repeated = 1
  static let mirror = 2
  static let decal = 3

  /// The whole periods covering the gradient parameter range `lower...upper`, or nil when the range
  /// is not finite. There is no cap here: the renderer bounds the count against the device, with
  /// `ringBudget` for a radial gradient.
  static func periods(lower: Double, upper: Double) -> (first: Int, last: Int)? {
    guard lower.isFinite, upper.isFinite, lower <= upper,
      abs(lower) < Double(Int32.max), abs(upper) < Double(Int32.max)
    else { return nil }
    let first = lower.rounded(.down)
    let last = max(upper.rounded(.up), first + 1)
    return (Int(first), Int(last))
  }

  /// The largest factor the 2×2 part of an affine transform stretches any direction by: its
  /// largest singular value. The column lengths are not enough under shear — `a = d = 0.6`,
  /// `b = c = 0.54` has columns of about 0.81 but stretches the diagonal by 1.14.
  static func largestStretch(a: Double, b: Double, c: Double, d: Double) -> Double {
    let sumOfSquares = a * a + b * b + c * c + d * d
    let determinant = a * d - b * c
    let spread = (max(sumOfSquares * sumOfSquares - 4 * determinant * determinant, 0)).squareRoot()
    return ((sumOfSquares + spread) / 2).squareRoot()
  }

  /// The most rings a radial gradient draws one by one in a clip whose device bounding box is
  /// `deviceWidth` × `deviceHeight` pixels.
  ///
  /// Under a transform that scales every direction alike, a ring a device pixel wide or more can
  /// only repeat across the clip's diagonal as many times as that diagonal has pixels, so every
  /// such case fits and is drawn exactly. Only a strongly anisotropic or sheared transform exceeds
  /// it — a million-unit-wide clip under `scale(0.001, 1)` holds a million rings a thousandth of a
  /// pixel wide across x — and the renderer then paints the period average rather than spending a
  /// million clips on the main thread.
  static func ringBudget(deviceWidth: Double, deviceHeight: Double) -> Int {
    let diagonal = (deviceWidth * deviceWidth + deviceHeight * deviceHeight).squareRoot()
    guard diagonal.isFinite else { return 0 }
    return Int(min(diagonal.rounded(.up), Double(Int32.max))) + 2
  }

  /// The colour a repeated or mirrored gradient averages to over one period: what a period smaller
  /// than a pixel looks like. `colors` are RGBA components, one per stop; each segment between
  /// stops contributes its mean colour weighted by its length, and the stretches before the first
  /// stop and after the last hold those end colours, as they do within a period.
  static func averageColor(colors: [[Double]], stops: [Double]) -> [Double] {
    guard let head = colors.first, colors.count == stops.count else { return [0, 0, 0, 0] }
    let positions = stops.map { min(max($0, 0), 1) }
    var sum = [Double](repeating: 0, count: head.count)
    func add(_ color: [Double], weight: Double) {
      for channel in sum.indices where channel < color.count {
        sum[channel] += color[channel] * weight
      }
    }
    add(head, weight: positions[0])
    for index in 1..<max(colors.count, 1) {
      let width = max(positions[index] - positions[index - 1], 0)
      add(zip(colors[index - 1], colors[index]).map { ($0 + $1) / 2 }, weight: width)
    }
    add(colors[colors.count - 1], weight: 1 - positions[positions.count - 1])
    return sum
  }
}
