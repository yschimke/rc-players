import CoreGraphics
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

/// `==` compares `values` NaN-stably (`NativeRedrawEquality`) so an unchanged path never redraws.
struct NativePathElement: Equatable {
  let kind: Int
  let values: [CGFloat]

  static func == (lhs: Self, rhs: Self) -> Bool {
    lhs.kind == rhs.kind && NativeRedrawEquality.same(lhs.values, rhs.values)
  }
}

enum NativePathBuilder {
  static func make(_ elements: [NativePathElement]) -> CGPath {
    let path = CGMutablePath()
    elements.forEach { segment in
      let v = segment.values
      switch segment.kind {
      case NativeSwiftPathCommand.move: path.move(to: CGPoint(x: v[0], y: v[1]))
      case NativeSwiftPathCommand.line: path.addLine(to: CGPoint(x: v[0], y: v[1]))
      case NativeSwiftPathCommand.quadratic, NativeSwiftPathCommand.conic:
        path.addQuadCurve(
          to: CGPoint(x: v[2], y: v[3]),
          control: CGPoint(x: v[0], y: v[1]))
      case NativeSwiftPathCommand.cubic:
        path.addCurve(
          to: CGPoint(x: v[4], y: v[5]),
          control1: CGPoint(x: v[0], y: v[1]),
          control2: CGPoint(x: v[2], y: v[3]))
      case NativeSwiftPathCommand.close: path.closeSubpath()
      default: break
      }
    }
    return path
  }
}
