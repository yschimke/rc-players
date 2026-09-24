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

    switch value.kind {
    case 0:
      context.drawLinearGradient(
        gradient,
        start: CGPoint(x: value.values[0], y: value.values[1]),
        end: CGPoint(x: value.values[2], y: value.values[3]),
        options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    case 1:
      context.drawRadialGradient(
        gradient,
        startCenter: CGPoint(x: value.values[0], y: value.values[1]),
        startRadius: 0,
        endCenter: CGPoint(x: value.values[0], y: value.values[1]),
        endRadius: value.values[2],
        options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    case 2:
      drawSweep(
        colors: normalizedColors,
        stops: normalizedStops ?? [],
        center: CGPoint(x: value.values[0], y: value.values[1]),
        in: context)
    default: break
    }
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
