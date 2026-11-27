import Foundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import RcWireOperations
import Testing

@testable import RcCreation

/// A ``RemoteInt`` interpolated into text is written as its digits, read back through the native
/// player, rather than Swift's description of the struct.
@Suite struct RemoteIntTextTests {
  private func flatten(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
    [node] + node.children.flatMap(flatten)
  }

  private func texts(_ session: NativeSwiftDocumentSession) throws -> [String] {
    try flatten(session.snapshot().root).compactMap(\.text?.value)
  }

  @Test func anIntegerIsWrittenAsItsDigits() throws {
    let count = RemoteVariable("count", default: 3)
    let level = RemoteVariable("level", default: 2.7)
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteVStack {
        RemoteText("\(RemoteInt(count) + 1) of 4")
        RemoteText("\(count) items")
        RemoteText("level \(RemoteInt(truncating: RemoteFloat(level)))")
        RemoteText("\(RemoteInt(7))")
        RemoteText("\(RemoteInt(-12) * RemoteInt(count))")
      }
    }
    let session = try NativeSwiftDocumentSession.open(data: document.encodedData())
    #expect(try texts(session) == ["4 of 4", "3 items", "level 2", "7", "-36"])
    #expect(session.setInteger(9, for: "count"))
    #expect(session.setFloat(-5.9, for: "level"))
    #expect(try texts(session) == ["10 of 4", "9 items", "level -5", "7", "-108"])
  }

  @Test func anIntegerIsAFloatForArithmetic() throws {
    let count = RemoteVariable("count", default: 3)
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteText("\(RemoteFloat(RemoteInt(count)) / 2, decimals: 1)")
    }
    let session = try NativeSwiftDocumentSession.open(data: document.encodedData())
    #expect(try texts(session) == ["1.5"])
  }

  /// An integer view of a canvas's size is that canvas's, as the float it truncates is.
  @Test func anIntegerOverTheCanvasSizeIsEachCanvass() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteCanvas { context, size in
          let radius = RemoteFloat(RemoteInt(truncating: size.width / 4))
          context.fill(.circle(center: size.center, radius: radius), with: .red)
        }
        .frame(width: 42, height: 20)
        RemoteCanvas { context, size in
          let radius = RemoteFloat(RemoteInt(truncating: size.width / 4))
          context.fill(.circle(center: size.center, radius: radius), with: .red)
        }
        .frame(width: 62, height: 30)
      }
    }
    let operations = try WireDocument.load(from: document.encode()).operations
    // Two integer expressions, one per canvas, each truncating its own canvas's width.
    let integers = operations.filter {
      if case .integerExpression = $0 { true } else { false }
    }
    #expect(integers.count == 2)
  }

  /// Every Int32 keeps its digits: a ten-digit integer is not cut to nine.
  @Test func aTenDigitIntegerKeepsItsLeadingDigit() throws {
    let count = RemoteVariable("count", default: 1_000_000_000)
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteText("\(count)")
    }
    let session = try NativeSwiftDocumentSession.open(data: document.encodedData())
    #expect(try texts(session) == ["1000000000"])
  }
}
