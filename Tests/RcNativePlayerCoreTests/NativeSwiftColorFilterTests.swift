import Foundation
import Testing

@testable import RcNativePlayerCore

@Suite struct NativeSwiftColorFilterTests {
  @Test func solidSrcInTint() throws {
    #expect(try snapshot(paint: filter()).colorARGB == 0xffff_0000)
  }

  @Test(arguments: [
    (UInt32(0x8000_0000), UInt32(0xffff_0000), UInt32(0x80ff_0000)),
    (0x8000_0000, 0x80ff_0000, 0x40ff_0000),
    (0x0000_0000, 0xffff_0000, 0x00ff_0000),
  ])
  func srcInMultipliesBothAlphas(destination: UInt32, source: UInt32, expected: UInt32) throws {
    let command = try snapshot(
      paint: [UInt32(NativeSwiftPaintCommand.color), destination] + filter(color: source))
    #expect(command.colorARGB == expected)
  }

  @Test func paintAlphaRemainsSeparate() throws {
    let command = try snapshot(
      paint: [
        UInt32(NativeSwiftPaintCommand.color), 0x8000_0000,
        UInt32(NativeSwiftPaintCommand.alpha), Float(0.5).bitPattern,
      ] + filter(color: 0x80ff_0000))
    #expect(command.colorARGB == 0x40ff_0000)
    #expect(command.alpha == 0.5)
  }

  @Test func colorIDsResolveOnEverySnapshot() throws {
    var paint = ParsedPaint()
    paint.colorID = 1
    paint.colorFilterID = 2
    paint.colorFilterMode = .sourceIn
    let draw = ParsedDrawCommand(kind: NativeSwiftDrawKind.rect, words: [], paint: paint)
    func color(_ colors: [Int: UInt32]) throws -> UInt32 {
      try draw.resolve(values: [:], integers: [:], colors: colors, texts: [:], matrices: [:])
        .colorARGB
    }
    #expect(try color([1: 0x8000_0000, 2: 0xffff_0000]) == 0x80ff_0000)
    #expect(try color([1: 0x4000_0000, 2: 0x8000_ff00]) == 0x2000_ff00)
  }

  @Test(
    arguments: Array(0...28).filter { $0 != NativeSwiftPaintBlendMode.sourceIn.rawValue } + [65535])
  func unsupportedModesAreReported(mode: Int) throws {
    try assertUnsupported(document(paint: filter(mode: mode)))
  }

  @Test func filtersOnGradientsAndTexturesAreReported() throws {
    let gradient: [UInt32] = [
      UInt32(NativeSwiftPaintCommand.gradient), 2, 0xff00_0000,
      0xffff_ffff, 0, 0, 0, Float(100).bitPattern, 0, 0,
    ]
    try assertUnsupported(document(paint: filter() + gradient))
    try assertUnsupported(
      document(paint: filter() + [UInt32(NativeSwiftPaintCommand.texture), 42, 0, 0]))
  }

  @Test func filtersOnBitmapsAreReported() throws {
    try assertUnsupported(document(paint: filter(), bitmap: true))
  }

  @Test(arguments: [
    NativeSwiftDrawKind.text, NativeSwiftDrawKind.textOnPath, NativeSwiftDrawKind.textOnCircle,
  ])
  func filtersOnTextAreReported(kind: Int) throws {
    var paint = ParsedPaint()
    paint.colorFilterMode = .sourceIn
    let command = ParsedDrawCommand(kind: kind, words: [], paint: paint)
    #expect(throws: NativeSwiftCoreError.self) {
      try command.resolve(values: [:], integers: [:], colors: [:], texts: [:], matrices: [:])
    }
  }

  @Test func clearingTheFilterRestoresShaderAndBitmapDraws() throws {
    let clear = [UInt32(NativeSwiftPaintCommand.clearColorFilter)]
    _ = try snapshot(paint: filter() + clear + [UInt32(NativeSwiftPaintCommand.texture), 42, 0, 0])
    _ = try snapshot(paint: filter() + clear, bitmap: true)
  }

  @Test(arguments: [
    NativeSwiftDrawKind.matrixSave, NativeSwiftDrawKind.matrixRestore,
    NativeSwiftDrawKind.matrixTranslate, NativeSwiftDrawKind.matrixScale,
    NativeSwiftDrawKind.matrixRotate, NativeSwiftDrawKind.matrixSkew,
    NativeSwiftDrawKind.matrixFromPath, NativeSwiftDrawKind.clipRect,
    NativeSwiftDrawKind.clipPath, NativeSwiftDrawKind.drawToBitmap,
  ])
  func nonDrawingCommandsDoNotConsumeTheFilter(kind: Int) throws {
    var paint = ParsedPaint()
    paint.colorFilterMode = .sourceOver
    let command = ParsedDrawCommand(kind: kind, words: [], paint: paint)
    _ = try command.resolve(values: [:], integers: [:], colors: [:], texts: [:], matrices: [:])
  }

  private func assertUnsupported(_ bytes: Data) throws {
    let session = try NativeSwiftDocumentSession.open(data: bytes)
    do {
      _ = try session.snapshot()
      Issue.record("An unsupported filter was silently accepted")
    } catch let error as NativeSwiftCoreError {
      guard case .unsupported(let opcode, let offset, _) = error else {
        Issue.record("Expected unsupported filter diagnostic, got \(error)")
        return
      }
      #expect(opcode == NativeSwiftWireOpcode.paintValues)
      #expect(offset > 0)
    }
  }

  private func snapshot(paint: [UInt32], bitmap: Bool = false) throws
    -> NativeSwiftDrawCommandSnapshot
  {
    let session = try NativeSwiftDocumentSession.open(data: document(paint: paint, bitmap: bitmap))
    return try #require(session.snapshot().root.commands.first)
  }

  private func filter(
    mode: Int = NativeSwiftPaintBlendMode.sourceIn.rawValue, color: UInt32 = 0xffff_0000
  ) -> [UInt32] {
    [UInt32(NativeSwiftPaintCommand.colorFilter) | (UInt32(mode) << 16), color]
  }

  private func document(paint: [UInt32], bitmap: Bool = false) -> Data {
    var bytes = Data([UInt8(NativeSwiftWireOpcode.header)])
    func word(_ value: UInt32) {
      bytes.append(contentsOf: [
        UInt8(truncatingIfNeeded: value >> 24), UInt8(truncatingIfNeeded: value >> 16),
        UInt8(truncatingIfNeeded: value >> 8), UInt8(truncatingIfNeeded: value),
      ])
    }
    // Legacy header, one inline bitmap, root, paint, drawing command, container end.
    [1, 0, 0, 100, 100, 0, 0].forEach { word(UInt32($0)) }
    bytes.append(UInt8(NativeSwiftWireOpcode.dataBitmap))
    [42, 1, 1, 4, 0xff00_0000].forEach { word(UInt32($0)) }
    bytes.append(UInt8(NativeSwiftWireOpcode.layoutRoot))
    word(0)
    bytes.append(UInt8(NativeSwiftWireOpcode.paintValues))
    word(UInt32(paint.count))
    paint.forEach(word)
    bytes.append(UInt8(bitmap ? NativeSwiftWireOpcode.drawBitmap : NativeSwiftWireOpcode.drawRect))
    if bitmap { word(42) }
    [Float(0), 0, 100, 100].forEach { word($0.bitPattern) }
    if bitmap { word(0) }
    bytes.append(UInt8(NativeSwiftWireOpcode.containerEnd))
    return bytes
  }
}
