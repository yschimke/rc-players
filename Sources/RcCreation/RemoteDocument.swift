#if canImport(Foundation)
  import Foundation
#endif

/// A Remote Compose document, composed in Swift and encoded as the `.rc` bytes any player reads.
///
/// ```swift
/// let document = RemoteDocument(width: 400, height: 200, density: 2) {
///   RemoteColumn(
///     modifier: .fillMaxSize().background(.white).padding(16),
///     verticalArrangement: .spacedBy(8)
///   ) {
///     RemoteText("Hello", fontSize: 24, fontWeight: .bold)
///     RemoteText(.named("subtitle", "from Swift"), color: .gray)
///   }
/// }
/// let bytes = document.encode()
/// ```
///
/// [width] and [height] are in pixels, as the header records them. Every dimension inside is in
/// dp (sp for text) and is written in pixels at [density], which the header records as the
/// density the document was generated at, as AndroidX's capture does.
public struct RemoteDocument {
  public let width: Int
  public let height: Int
  public let density: Float
  public let contentDescription: String?
  let content: RemoteContent

  public init(
    width: Int,
    height: Int,
    density: Float = 1,
    contentDescription: String? = nil,
    @RemoteContentBuilder content: () -> RemoteContent
  ) {
    precondition(width > 0 && height > 0, "a document needs a positive size")
    self.width = width
    self.height = height
    self.density = density
    self.contentDescription = contentDescription
    self.content = content()
  }

  /// The document's `.rc` bytes.
  public func encode() -> [UInt8] {
    let context = RemoteCreationContext(density: density)
    context.op(RemoteOpcode.layoutRoot)
    context.body.int(context.newComponentID())
    context.withParent(.root) { context.write(content) }
    context.containerEnd()

    var output = RemoteByteBuffer()
    writeHeader(to: &output)
    output.append(context.declarations)
    output.append(context.body)
    return output.bytes
  }

  #if canImport(Foundation)
    /// The document's `.rc` bytes as `Data`, for a player or a file.
    public func encodedData() -> Data { Data(encode()) }
  #endif

  /// A modern header: the version with `Header.MAGIC_NUMBER`, then tagged properties.
  private func writeHeader(to buffer: inout RemoteByteBuffer) {
    var properties: [(tag: Int, type: Int, value: RemoteByteBuffer)] = []
    func int(_ tag: Int, _ value: Int) {
      var encoded = RemoteByteBuffer()
      encoded.int(value)
      properties.append((tag, RemoteHeaderValueType.int, encoded))
    }
    int(RemoteHeaderKey.documentWidth, width)
    int(RemoteHeaderKey.documentHeight, height)
    var densityValue = RemoteByteBuffer()
    densityValue.float(density)
    properties.append(
      (RemoteHeaderKey.densityAtGeneration, RemoteHeaderValueType.float, densityValue))
    if let contentDescription {
      var text = RemoteByteBuffer()
      text.utf8(contentDescription)
      properties.append((RemoteHeaderKey.contentDescription, RemoteHeaderValueType.string, text))
    }

    buffer.byte(RemoteOpcode.header)
    buffer.int(RemoteHeaderVersion.magic | RemoteHeaderVersion.major)
    buffer.int(RemoteHeaderVersion.minor)
    buffer.int(RemoteHeaderVersion.patch)
    buffer.int(properties.count)
    for property in properties {
      buffer.short(property.tag | property.type << 10)
      buffer.short(property.value.bytes.count)
      buffer.append(property.value)
    }
  }
}
