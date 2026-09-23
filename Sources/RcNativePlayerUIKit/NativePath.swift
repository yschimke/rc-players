import CoreGraphics
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

struct NativePathElement: Equatable {
  let kind: Int
  let values: [CGFloat]
}

enum NativePathBuilder {
  static func make(_ elements: [NativePathElement]) -> CGPath {
    let path = CGMutablePath()
    elements.forEach { segment in
      let v = segment.values
      switch segment.kind {
      case NativeSwiftPathVerb.move: path.move(to: CGPoint(x: v[0], y: v[1]))
      case NativeSwiftPathVerb.line: path.addLine(to: CGPoint(x: v[0], y: v[1]))
      case NativeSwiftPathVerb.quadratic, NativeSwiftPathVerb.conic:
        path.addQuadCurve(
          to: CGPoint(x: v[2], y: v[3]),
          control: CGPoint(x: v[0], y: v[1]))
      case NativeSwiftPathVerb.cubic:
        path.addCurve(
          to: CGPoint(x: v[4], y: v[5]),
          control1: CGPoint(x: v[0], y: v[1]),
          control2: CGPoint(x: v[2], y: v[3]))
      case NativeSwiftPathVerb.close: path.closeSubpath()
      default: break
      }
    }
    return path
  }
}
