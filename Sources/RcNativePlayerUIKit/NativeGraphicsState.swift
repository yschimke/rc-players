import CoreGraphics
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

struct NativeGradient: Equatable {
  let kind: Int
  let colors: [CGColor]
  let stops: [CGFloat]
  let values: [CGFloat]
  let tileMode: Int
}

enum NativeGraphicsState {
  static func apply(
    to context: CGContext,
    strokeWidth: CGFloat,
    strokeCap: Int,
    strokeJoin: Int,
    blendMode: Int
  ) {
    context.setLineWidth(max(strokeWidth, 0.5))
    context.setLineCap(lineCap(strokeCap))
    context.setLineJoin(lineJoin(strokeJoin))
    context.setBlendMode(self.blendMode(blendMode))
  }

  static func lineCap(_ value: Int) -> CGLineCap {
    switch value {
    case 1: .round
    case 2: .square
    default: .butt
    }
  }

  static func lineJoin(_ value: Int) -> CGLineJoin {
    switch value {
    case 1: .round
    case 2: .bevel
    default: .miter
    }
  }

  static func blendMode(_ value: Int) -> CGBlendMode {
    switch value {
    case NativeSwiftPaintBlendMode.clear: .clear
    case NativeSwiftPaintBlendMode.source: .copy
    case NativeSwiftPaintBlendMode.destination: .normal
    case NativeSwiftPaintBlendMode.sourceOver: .normal
    case NativeSwiftPaintBlendMode.destinationOver: .destinationOver
    case NativeSwiftPaintBlendMode.sourceIn: .sourceIn
    case NativeSwiftPaintBlendMode.destinationIn: .destinationIn
    case NativeSwiftPaintBlendMode.sourceOut: .sourceOut
    case NativeSwiftPaintBlendMode.destinationOut: .destinationOut
    case NativeSwiftPaintBlendMode.sourceAtop: .sourceAtop
    case NativeSwiftPaintBlendMode.destinationAtop: .destinationAtop
    case NativeSwiftPaintBlendMode.xor: .xor
    case NativeSwiftPaintBlendMode.plus: .plusLighter
    case NativeSwiftPaintBlendMode.modulate, NativeSwiftPaintBlendMode.multiply: .multiply
    case NativeSwiftPaintBlendMode.screen: .screen
    case NativeSwiftPaintBlendMode.overlay: .overlay
    case NativeSwiftPaintBlendMode.darken: .darken
    case NativeSwiftPaintBlendMode.lighten: .lighten
    case NativeSwiftPaintBlendMode.colorDodge: .colorDodge
    case NativeSwiftPaintBlendMode.colorBurn: .colorBurn
    case NativeSwiftPaintBlendMode.hardLight: .hardLight
    case NativeSwiftPaintBlendMode.softLight: .softLight
    case NativeSwiftPaintBlendMode.difference: .difference
    case NativeSwiftPaintBlendMode.exclusion: .exclusion
    case NativeSwiftPaintBlendMode.hue: .hue
    case NativeSwiftPaintBlendMode.saturation: .saturation
    case NativeSwiftPaintBlendMode.color: .color
    case NativeSwiftPaintBlendMode.luminosity: .luminosity
    default: .normal
    }
  }

  /// Corner radii Core Graphics accepts for `rect`. `CGPath(roundedRect:cornerWidth:cornerHeight:)`
  /// asserts unless each radius is non-negative and at most half the matching side, and document
  /// radii are unvalidated; a non-finite radius becomes 0.
  static func clampedCornerRadii(
    in rect: CGRect, cornerWidth: CGFloat, cornerHeight: CGFloat
  ) -> (width: CGFloat, height: CGFloat) {
    func clamp(_ radius: CGFloat, _ side: CGFloat) -> CGFloat {
      guard radius.isFinite, side.isFinite else { return 0 }
      return min(max(radius, 0), side / 2)
    }
    return (clamp(cornerWidth, rect.width), clamp(cornerHeight, rect.height))
  }

  /// A rounded-rectangle path whose radii are clamped with `clampedCornerRadii`.
  static func roundedRectPath(
    _ rect: CGRect, cornerWidth: CGFloat, cornerHeight: CGFloat
  ) -> CGPath {
    let radii = clampedCornerRadii(in: rect, cornerWidth: cornerWidth, cornerHeight: cornerHeight)
    return CGPath(
      roundedRect: rect, cornerWidth: radii.width, cornerHeight: radii.height, transform: nil)
  }
}

enum NativeGradientRenderer {
  static func draw(_ value: NativeGradient, in context: CGContext) {
    let normalizedColors = value.colors.count == 1 ? [value.colors[0], value.colors[0]] : value.colors
    guard normalizedColors.count >= 2 else { return }
    let normalizedStops: [CGFloat]?
    if value.stops.count == normalizedColors.count {
      normalizedStops = value.stops
    } else {
      normalizedStops = (0..<normalizedColors.count).map {
        CGFloat($0) / CGFloat(normalizedColors.count - 1)
      }
    }
    // Converted into the gradient's own space first. CGGradient returns nil when a colour is not
    // already in the space it is handed, and `UIColor.cgColor` is sRGB rather than device RGB — so
    // building against CGColorSpaceCreateDeviceRGB() failed for every real colour and painted
    // nothing at all. sRGB is also what the reference players interpolate in.
    let space = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
    let spaceColors = normalizedColors.map {
      $0.converted(to: space, intent: .defaultIntent, options: nil) ?? $0
    }
    guard
      let gradient = CGGradient(
        colorsSpace: space,
        colors: spaceColors as CFArray,
        locations: normalizedStops)
    else { return }

    // Clamp extends the end colours; decal stops at them. Repeat and mirror are laid out over the
    // periods the clip reaches and drawn as one gradient, falling back to clamp past the limit.
    let extend: CGGradientDrawingOptions =
      value.tileMode == NativeGradientTiling.decal
      ? [] : [.drawsBeforeStartLocation, .drawsAfterEndLocation]
    let tiles =
      value.tileMode == NativeGradientTiling.repeated
      || value.tileMode == NativeGradientTiling.mirror
    switch value.kind {
    case 0:
      let start = CGPoint(x: value.values[0], y: value.values[1])
      let end = CGPoint(x: value.values[2], y: value.values[3])
      if tiles,
        let tiled = tiledLinear(
          start: start, end: end, colors: spaceColors, stops: normalizedStops ?? [],
          mirror: value.tileMode == NativeGradientTiling.mirror, space: space,
          clip: context.boundingBoxOfClipPath)
      {
        context.drawLinearGradient(
          tiled.gradient, start: tiled.start, end: tiled.end,
          options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        return
      }
      context.drawLinearGradient(gradient, start: start, end: end, options: extend)
    case 1:
      let center = CGPoint(x: value.values[0], y: value.values[1])
      let radius = value.values[2]
      if tiles,
        let tiled = tiledRadial(
          center: center, radius: radius, colors: spaceColors, stops: normalizedStops ?? [],
          mirror: value.tileMode == NativeGradientTiling.mirror, space: space,
          clip: context.boundingBoxOfClipPath)
      {
        context.drawRadialGradient(
          tiled.gradient, startCenter: center, startRadius: 0, endCenter: center,
          endRadius: tiled.radius, options: [.drawsAfterEndLocation])
        return
      }
      context.drawRadialGradient(
        gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius,
        options: extend)
    case 2:
      drawSweep(
        colors: normalizedColors,
        stops: normalizedStops ?? [],
        center: CGPoint(x: value.values[0], y: value.values[1]),
        in: context)
    default: break
    }
  }

  /// A linear gradient repeated or mirrored over every period `clip` reaches, and the start and
  /// end points of that span; nil when it cannot be laid out.
  private static func tiledLinear(
    start: CGPoint, end: CGPoint, colors: [CGColor], stops: [CGFloat], mirror: Bool,
    space: CGColorSpace, clip: CGRect
  ) -> (gradient: CGGradient, start: CGPoint, end: CGPoint)? {
    let axis = CGPoint(x: end.x - start.x, y: end.y - start.y)
    let length = axis.x * axis.x + axis.y * axis.y
    guard length > 0, !clip.isNull, !clip.isInfinite else { return nil }
    // The gradient parameter at each corner of the clip: its projection onto the axis.
    let parameters = corners(of: clip).map {
      Double((($0.x - start.x) * axis.x + ($0.y - start.y) * axis.y) / length)
    }
    guard let lower = parameters.min(), let upper = parameters.max(),
      let span = NativeGradientTiling.periods(lower: lower, upper: upper),
      let gradient = tiledGradient(
        colors: colors, stops: stops, mirror: mirror, span: span, space: space)
    else { return nil }
    func point(_ t: Int) -> CGPoint {
      CGPoint(x: start.x + axis.x * CGFloat(t), y: start.y + axis.y * CGFloat(t))
    }
    return (gradient, point(span.first), point(span.last))
  }

  /// A radial gradient repeated or mirrored out to the farthest corner of `clip`, and the radius
  /// that span ends at; nil when it cannot be laid out.
  private static func tiledRadial(
    center: CGPoint, radius: CGFloat, colors: [CGColor], stops: [CGFloat], mirror: Bool,
    space: CGColorSpace, clip: CGRect
  ) -> (gradient: CGGradient, radius: CGFloat)? {
    guard radius > 0, !clip.isNull, !clip.isInfinite else { return nil }
    let farthest = corners(of: clip).map { hypot($0.x - center.x, $0.y - center.y) }.max() ?? 0
    guard let span = NativeGradientTiling.periods(lower: 0, upper: Double(farthest / radius)),
      let gradient = tiledGradient(
        colors: colors, stops: stops, mirror: mirror, span: span, space: space)
    else { return nil }
    return (gradient, radius * CGFloat(span.last))
  }

  private static func tiledGradient(
    colors: [CGColor], stops: [CGFloat], mirror: Bool, span: (first: Int, last: Int),
    space: CGColorSpace
  ) -> CGGradient? {
    guard stops.count == colors.count else { return nil }
    let layout = NativeGradientTiling.layout(
      stops: stops.map(Double.init), mirror: mirror, first: span.first, last: span.last)
    guard !layout.isEmpty else { return nil }
    return CGGradient(
      colorsSpace: space, colors: layout.map { colors[$0.colorIndex] } as CFArray,
      locations: layout.map { CGFloat($0.location) })
  }

  private static func corners(of rect: CGRect) -> [CGPoint] {
    [
      CGPoint(x: rect.minX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY),
      CGPoint(x: rect.maxX, y: rect.maxY), CGPoint(x: rect.minX, y: rect.maxY),
    ]
  }

  private static func drawSweep(
    colors: [CGColor], stops: [CGFloat], center: CGPoint, in context: CGContext
  ) {
    let clip = context.boundingBoxOfClipPath
    let radius = [
      CGPoint(x: clip.minX, y: clip.minY), CGPoint(x: clip.maxX, y: clip.minY),
      CGPoint(x: clip.maxX, y: clip.maxY), CGPoint(x: clip.minX, y: clip.maxY),
    ].map { hypot($0.x - center.x, $0.y - center.y) }.max() ?? 0
    guard radius > 0 else { return }
    // The wedges are drawn aliased. Each is about a pixel wide at the rim, and two antialiased
    // neighbours only cover a shared edge pixel a·(1−a) of the way between them, so the backdrop
    // showed through almost every seam. The clip set before this call keeps the shape's own edge
    // antialiased.
    context.saveGState()
    defer { context.restoreGState() }
    context.setShouldAntialias(false)
    let steps = 360
    for index in 0..<steps {
      let start = CGFloat(index) / CGFloat(steps)
      let end = CGFloat(index + 1) / CGFloat(steps)
      context.setFillColor(interpolatedColor(at: (start + end) / 2, colors: colors, stops: stops))
      context.move(to: center)
      context.addArc(
        center: center,
        radius: radius,
        startAngle: start * 2 * .pi,
        endAngle: end * 2 * .pi,
        clockwise: false)
      context.closePath()
      context.fillPath()
    }
  }

  private static func interpolatedColor(
    at location: CGFloat, colors: [CGColor], stops: [CGFloat]
  ) -> CGColor {
    let upper = stops.firstIndex(where: { $0 >= location }) ?? stops.index(before: stops.endIndex)
    guard upper > stops.startIndex else { return colors[0] }
    let lower = stops.index(before: upper)
    let distance = stops[upper] - stops[lower]
    let fraction = distance > 0 ? (location - stops[lower]) / distance : 0
    let left = rgbaComponents(colors[lower])
    let right = rgbaComponents(colors[upper])
    let components = (0..<4).map { index in
      left[index] + (right[index] - left[index]) * fraction
    }
    return CGColor(colorSpace: rgbColorSpace, components: components) ?? colors[lower]
  }

  private static let rgbColorSpace =
    CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()

  /// Exactly four sRGB components (red, green, blue, alpha) for any colour. `CGColor.components`
  /// has as many entries as the colour's space has channels plus alpha, so a grayscale colour has
  /// two and indexing it as RGBA read out of bounds.
  static func rgbaComponents(_ color: CGColor) -> [CGFloat] {
    let converted = color.converted(to: rgbColorSpace, intent: .defaultIntent, options: nil)
    let components = (converted ?? color).components ?? []
    switch components.count {
    case 4...: return Array(components[0..<4])
    case 3: return [components[0], components[1], components[2], 1]
    case 2: return [components[0], components[0], components[0], components[1]]
    case 1: return [components[0], components[0], components[0], 1]
    default: return [0, 0, 0, 1]
    }
  }
}
