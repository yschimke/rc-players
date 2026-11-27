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

  /// [bytes] read and written back, header included: `WireDocument` keeps a modern header's
  /// properties and writes them back as read.
  private func roundTrips(_ bytes: [UInt8]) throws(WireError) -> Bool {
    try WireDocument.load(from: bytes).save() == bytes
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
        if try !roundTrips(bytes) {
          failures.append("\(file.lastPathComponent): differs")
        }
      } catch {
        failures.append("\(file.lastPathComponent): \(error)")
      }
    }
    #expect(failures.isEmpty, "\(failures.count) of \(files.count): \(failures.prefix(10))")
  }

  /// What RcCreation writes reads back through the bindings unchanged.
  @Test func creationDocumentsRoundTrip() throws {
    let progress = RemoteVariable("progress", default: 0.5)
    let document = RemoteDocument(width: 200, height: 100, displayScale: 2) {
      RemoteVStack(alignment: .leading, spacing: 4) {
        RemoteText("Upload \(progress * 100, decimals: 0)%").font(.callout)
        RemoteColor.blue.frame(width: progress * 200, height: 8)
        RemoteCanvas { context, size in
          context.fill(.circle(center: size.center, radius: 10), with: .red)
        }
        .frame(width: 20, height: 20)
        RemoteColor.adaptive(light: .white, dark: .black)
          .frame(width: 10, height: 10)
          .onTapGesture(.host("tapped"))
          .accessibilityLabel("Tap")
      }
      .padding(8)
    }
    #expect(try roundTrips(document.encode()))
  }

  /// A border with a corner radius is written as the rounded-rectangle shape, the only one a
  /// player reads the radius for; without a radius it stays a rectangle. Read through the
  /// bindings, so the wire fields are checked as AndroidX names them.
  @Test func roundedBorderIsTheRoundedShape() throws {
    let displayScale = 2.0
    func border(_ radius: Double) throws -> BorderModifierOperation {
      let bytes = try RemoteDocument(width: 100, height: 100, displayScale: displayScale) {
        RemoteColor.clear
          .frame(width: 40, height: 40)
          .border(.black, width: 2, cornerRadius: radius)
      }.encode()
      let border = try WireDocument.load(from: bytes).operations.lazy.compactMap {
        if case .borderModifierOperation(let border) = $0 { border } else { nil }
      }.first
      return try #require(border)
    }
    let rounded = try border(6)
    #expect(rounded.shapeType == BorderModifierOperation.roundedRectangle)
    #expect(rounded.roundedCorner == Float(6 * displayScale))
    #expect(rounded.borderWidth == Float(2 * displayScale))
    #expect(try border(0).shapeType == BorderModifierOperation.rectangle)
  }

  /// A failure is a typed error a caller can inspect, not an `NSError`.
  @Test func truncatedDocumentFailsWithATypedError() {
    #expect(throws: WireError.expectedHeaderOpCode(actual: OpCode.drawCircle.rawValue)) {
      try WireDocument.load(from: [OpCode.drawCircle.rawValue])
    }
    #expect(throws: WireError.underflow(needed: MemoryLayout<Int32>.size, remaining: 0)) {
      try WireDocument.load(from: [OpCode.header.rawValue])
    }
  }
}
