import Foundation
import RcCreation
import RcWireOperations
import Testing

/// Upstream AndroidX's Swift bindings, generated Foundation-free from the forked schema, held to
/// the documents this repository plays: each is read and written back byte for byte.
@Suite struct RoundTripTests {
  /// The repository root, from this file's path: the corpus and fixtures are read in place rather
  /// than copied into the test bundle.
  private static let root = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()

  private static func documents(in directory: String) -> [URL] {
    let url = root.appendingPathComponent(directory)
    let names = (try? FileManager.default.contentsOfDirectory(atPath: url.path)) ?? []
    return names.filter { $0.hasSuffix(".rc") }.sorted().map { url.appendingPathComponent($0) }
  }

  /// The operations after the header, read and written back. Upstream's Swift `RemoteDocument`
  /// keeps only the header's version, size and capabilities and always writes the legacy header,
  /// so the header itself does not round-trip (tracked upstream); every operation after it must.
  private func operationsRoundTrip(_ bytes: [UInt8]) throws -> Bool {
    let saved = try RcWireOperations.RemoteDocument.load(from: bytes).save()
    return saved.dropFirst(Self.legacyHeaderLength) == bytes.dropFirst(try Self.headerLength(bytes))
  }

  /// Opcode, major, minor, patch, width and height, then a long of capabilities.
  private static let legacyHeaderLength = 1 + 5 * 4 + 8

  /// The length of [bytes]' header: legacy, or modern with its tagged properties
  /// (a short tag, a short length, then that many bytes each).
  private static func headerLength(_ bytes: [UInt8]) throws -> Int {
    func int(_ at: Int) -> Int {
      Int(Int32(bitPattern: bytes[at..<at + 4].reduce(0) { $0 << 8 | UInt32($1) }))
    }
    let major = UInt32(bitPattern: Int32(int(1)))
    guard major & 0xFFFF_0000 == 0x048C_0000 else { return legacyHeaderLength }
    var offset = 1 + 4 * 4
    for _ in 0..<int(13) {
      let length = Int(bytes[offset + 2]) << 8 | Int(bytes[offset + 3])
      offset += 4 + length
    }
    return offset
  }

  /// AndroidX-captured documents: the catalog corpus and the native player's fixtures.
  @Test func capturedDocumentsRoundTrip() throws {
    let files =
      Self.documents(in: "scripts/rc-catalog-corpus/corpus")
      + Self.documents(in: "Tests/RcNativePlayerCoreTests/Fixtures")
    try #require(files.count > 700, "expected the catalog corpus at \(Self.root.path)")
    var failures: [String] = []
    for file in files {
      let bytes = [UInt8](try Data(contentsOf: file))
      do {
        if try !operationsRoundTrip(bytes) {
          failures.append("\(file.lastPathComponent): differs")
        }
      } catch {
        failures.append("\(file.lastPathComponent): \(error)")
      }
    }
    #expect(failures.isEmpty, "\(failures.count) of \(files.count): \(failures.prefix(10))")
  }

  /// What RcCreation writes reads back through upstream's bindings unchanged.
  @Test func creationDocumentsRoundTrip() throws {
    let progress = RemoteFloat.named("progress", 0.5)
    let document = RemoteDocument_(width: 400, height: 200, density: 2) {
      RemoteColumn(modifier: .fillMaxSize().padding(8), verticalArrangement: .spacedBy(4)) {
        RemoteText("Upload " + RemoteString(progress * 100, decimals: 0) + "%", fontSize: 16)
        RemoteBox(modifier: .width(progress * 200).height(8).background(.blue))
        RemoteCanvas(modifier: .size(20)) { scope in
          scope.drawCircle(.red, radius: 10, center: RemotePoint(10, 10))
        }
        RemoteBox(modifier: .size(10).clickable(.host("tapped")))
      }
    }
    #expect(try operationsRoundTrip(document.encode()))
  }

  /// A border with a corner radius is written as the rounded-rectangle shape, the only one a
  /// player reads the radius for; without a radius it stays a rectangle. Read through upstream's
  /// bindings, so the wire fields are checked as AndroidX names them.
  @Test func roundedBorderIsTheRoundedShape() throws {
    func border(_ radius: Float) throws -> BorderModifierOperation {
      let bytes = RemoteDocument_(width: 100, height: 100, density: 2) {
        RemoteBox(modifier: .size(40).border(2, .black, cornerRadius: radius))
      }.encode()
      let operations = try RcWireOperations.RemoteDocument.load(from: bytes).operations
      return try #require(operations.compactMap { $0 as? BorderModifierOperation }.first)
    }
    let rounded = try border(6)
    #expect(rounded.shapeType == 2)
    #expect(rounded.roundedCorner == 12)
    #expect(rounded.borderWidth == 4)
    #expect(try border(0).shapeType == 0)
  }

  /// A failure is a typed error a caller can inspect, not an `NSError`.
  @Test func truncatedDocumentFailsWithATypedError() {
    #expect(throws: RemoteComposeWireError.self) {
      try RcWireOperations.RemoteDocument.load(from: [0, 0, 0])
    }
  }
}

/// RcCreation's document, named apart from the bindings' own `RemoteDocument`.
typealias RemoteDocument_ = RcCreation.RemoteDocument
