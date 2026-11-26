import RcWireOperations

#if canImport(Foundation)
  import Foundation
#endif

/// Why a document could not be encoded.
public enum RemoteEncodingError: Error, Sendable, Hashable {
  /// Two variables share `name` but not their type or default, so a host write by that name
  /// could reach only one of them.
  case conflictingVariable(name: String)
  /// An expression has more terms than a `FloatExpression` holds.
  case expressionTooLong(terms: Int, limit: Int)
  /// The display scale is not a positive, finite number.
  case invalidDisplayScale(Double)
  /// The document's size is not positive and finite, or does not fit the header.
  case invalidDocumentSize(width: Double, height: Double)
  /// A font weight outside 1...1000.
  case invalidFontWeight(Double)
  /// A flex weight that is not positive and finite.
  case invalidFlexWeight(Double)
  /// A number format whose digit counts are outside 0...16.
  case invalidNumberFormat(integerDigits: Int, decimals: Int)
  /// A canvas's size was used outside that canvas's own drawing.
  case canvasSizeOutsideCanvas
  /// The bindings refused an operation.
  case wire(WireError)
}

/// A Remote Compose document, composed in Swift and encoded as the `.rc` bytes any player reads.
///
/// ```swift
/// let document = RemoteDocument(width: 360, height: 200, displayScale: 2) {
///   RemoteVStack(alignment: .leading, spacing: 8) {
///     RemoteText("Hello").font(.title)
///     RemoteText(RemoteVariable("subtitle", default: "from Swift"))
///       .foregroundStyle(.secondary)
///   }
///   .padding(16)
/// }
/// let bytes = try document.encode()
/// ```
///
/// Every length is in points, as in SwiftUI. The document is written in pixels at
/// `displayScale` pixels per point, which the header records as the density it was generated at.
public struct RemoteDocument: Sendable {
  /// The width, in points.
  public let width: Double
  /// The height, in points.
  public let height: Double
  /// Pixels per point.
  public let displayScale: Double
  /// What assistive technologies read for the document as a whole.
  public let accessibilityLabel: String?
  let content: any RemoteView

  public init(
    width: Double,
    height: Double,
    displayScale: Double = 1,
    accessibilityLabel: String? = nil,
    @RemoteViewBuilder content: () -> some RemoteView
  ) {
    self.width = width
    self.height = height
    self.displayScale = displayScale
    self.accessibilityLabel = accessibilityLabel
    self.content = content()
  }

  /// The document's `.rc` bytes.
  public func encode() throws(RemoteEncodingError) -> [UInt8] {
    guard displayScale > 0, displayScale.isFinite else {
      throw RemoteEncodingError.invalidDisplayScale(displayScale)
    }
    let pixelWidth = (width * displayScale).rounded()
    let pixelHeight = (height * displayScale).rounded()
    guard pixelWidth >= 1, pixelHeight >= 1, pixelWidth <= Double(Int32.max),
      pixelHeight <= Double(Int32.max)
    else {
      throw RemoteEncodingError.invalidDocumentSize(width: width, height: height)
    }

    let writer = _RemoteWriter(displayScale: Float(displayScale))
    try writer.writeRoot(content)

    var properties = [
      HeaderProperty.int(HeaderProperty.docWidth, Int32(pixelWidth)),
      HeaderProperty.int(HeaderProperty.docHeight, Int32(pixelHeight)),
      HeaderProperty.float(HeaderProperty.docDensityAtGeneration, Float(displayScale)),
    ]
    if let accessibilityLabel {
      properties.append(.string(HeaderProperty.docContentDescription, accessibilityLabel))
    }
    let document = WireDocument(
      properties: properties, width: Int32(pixelWidth), height: Int32(pixelHeight),
      operations: writer.declarations + writer.body)
    do {
      return try document.save()
    } catch {
      throw RemoteEncodingError.wire(error)
    }
  }

  #if canImport(Foundation)
    /// The document's `.rc` bytes as `Data`, for a player or a file.
    public func encodedData() throws(RemoteEncodingError) -> Data { Data(try encode()) }
  #endif
}

extension HeaderProperty {
  private static func tag(_ key: Int32, _ type: Int32) -> Int16 {
    Int16(key | type << Int32(HeaderProperty.BitLayout.tagDataTypeShift))
  }

  static func int(_ key: Int32, _ value: Int32) -> HeaderProperty {
    HeaderProperty(
      tag: tag(key, HeaderConstants.dataTypeInt), itemLen: Int16(MemoryLayout<Int32>.size),
      intValue: value)
  }

  static func float(_ key: Int32, _ value: Float) -> HeaderProperty {
    HeaderProperty(
      tag: tag(key, HeaderConstants.dataTypeFloat), itemLen: Int16(MemoryLayout<Float>.size),
      floatValue: value)
  }

  /// A string property: its length word, then its UTF-8 bytes.
  static func string(_ key: Int32, _ value: String) -> HeaderProperty {
    HeaderProperty(
      tag: tag(key, HeaderConstants.dataTypeString),
      itemLen: Int16(clamping: MemoryLayout<Int32>.size + value.utf8.count), stringValue: value)
  }
}
