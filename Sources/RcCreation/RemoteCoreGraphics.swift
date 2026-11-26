#if canImport(CoreGraphics)
  import CoreGraphics

  extension RemoteFloat {
    public init(_ value: CGFloat) { self.init(Double(value)) }
  }

  extension RemotePoint {
    public init(_ point: CGPoint) { self.init(x: RemoteFloat(point.x), y: RemoteFloat(point.y)) }
  }

  extension RemoteSize {
    public init(_ size: CGSize) {
      self.init(width: RemoteFloat(size.width), height: RemoteFloat(size.height))
    }
  }

  extension RemoteRect {
    public init(_ rect: CGRect) {
      self.init(origin: RemotePoint(rect.origin), size: RemoteSize(rect.size))
    }
  }

  extension RemoteColorValue {
    /// `color` in sRGB, or `nil` if it cannot be converted (a pattern colour).
    public init?(_ color: CGColor) {
      guard
        let space = CGColorSpace(name: CGColorSpace.sRGB),
        let converted = color.converted(to: space, intent: .defaultIntent, options: nil),
        let components = converted.components, components.count >= 3
      else { return nil }
      self.init(
        red: Double(components[0]), green: Double(components[1]), blue: Double(components[2]),
        opacity: Double(converted.alpha))
    }
  }
#endif
