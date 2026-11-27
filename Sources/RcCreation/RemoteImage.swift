import RcWireOperations

/// How an image fills the frame it is given.
public enum RemoteContentMode: Hashable, Sendable {
  /// The whole image, scaled to fit, keeping its aspect ratio.
  case fit
  /// The frame, filled, keeping the aspect ratio and cropping what overflows.
  case fill
  /// The frame, filled, stretching the image.
  case stretch

  var wire: Int32 {
    switch self {
    case .fit: ImageLayout.scaleFit
    case .fill: ImageLayout.scaleCrop
    case .stretch: ImageLayout.scaleFillBounds
    }
  }
}

/// A bitmap, as SwiftUI's `Image`: PNG bytes carried in the document (`BitmapData`), drawn by
/// `ImageLayout`.
///
/// ```swift
/// RemoteImage(png: logoBytes, pixelWidth: 128, pixelHeight: 128)
///   .scaledToFill()
///   .frame(width: 64, height: 64)
///   .clipShape(.roundedRectangle(cornerRadius: 12))
/// ```
///
/// The bytes are declared once however many times the image is drawn. The player decodes them;
/// `pixelWidth` and `pixelHeight` are the encoded image's size, which the header of the bitmap
/// records. Without a frame the image is laid out at that size in pixels.
public struct RemoteImage: _PrimitiveRemoteView {
  let png: [UInt8]
  let pixelWidth: Int
  let pixelHeight: Int
  var contentMode: RemoteContentMode = .fit
  var opacity: Double = 1

  /// - Parameters:
  ///   - png: The encoded PNG.
  ///   - pixelWidth: Its width in pixels, 1 to 65,535.
  ///   - pixelHeight: Its height in pixels, 1 to 65,535.
  public init(png: [UInt8], pixelWidth: Int, pixelHeight: Int) {
    self.png = png
    self.pixelWidth = pixelWidth
    self.pixelHeight = pixelHeight
  }

  /// Fits the whole image in its frame (the default).
  public func scaledToFit() -> RemoteImage { with { $0.contentMode = .fit } }

  /// Fills the frame, cropping what overflows.
  public func scaledToFill() -> RemoteImage { with { $0.contentMode = .fill } }

  public func aspectRatio(contentMode: RemoteContentMode) -> RemoteImage {
    with { $0.contentMode = contentMode }
  }

  /// Draws the image at `opacity`, 0 to 1.
  public func opacity(_ opacity: Double) -> RemoteImage { with { $0.opacity = opacity } }

  private func with(_ change: (inout RemoteImage) -> Void) -> RemoteImage {
    var copy = self
    change(&copy)
    return copy
  }

  public func _write(to writer: _RemoteWriter) throws(RemoteEncodingError) {
    let sizeLimit = Int(BitmapData.BitLayout.widthAndTypeWidthMask)
    guard (1...sizeLimit).contains(pixelWidth), (1...sizeLimit).contains(pixelHeight) else {
      throw RemoteEncodingError.invalidImageSize(width: pixelWidth, height: pixelHeight)
    }
    let componentID = writer.newComponentID()
    let bitmapID = writer.bitmapID(png: png, width: pixelWidth, height: pixelHeight)
    let layout = ImageLayout(
      componentId: componentID, animationId: IdEncoding.noId, bitmapId: bitmapID,
      scaleType: contentMode.wire, alpha: Float(Swift.min(Swift.max(opacity, 0), 1)))
    try writer.writeLayout(.imageLayout(layout), kind: .leaf, content: EmptyRemoteView())
  }
}
