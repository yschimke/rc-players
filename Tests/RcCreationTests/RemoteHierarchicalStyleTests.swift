import Foundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import RcWireOperations
import Testing

@testable import RcCreation

/// `.secondary` and the other hierarchical styles are the foreground they are drawn under, dimmed,
/// as SwiftUI's are, read back through the native player.
@Suite struct RemoteHierarchicalStyleTests {
  private func open(_ document: RemoteDocument) throws -> NativeSwiftDocumentSession {
    try NativeSwiftDocumentSession.open(data: document.encodedData())
  }

  private func flatten(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
    [node] + node.children.flatMap(flatten)
  }

  /// The colour of each text, in order.
  private func textColors(_ session: NativeSwiftDocumentSession) throws -> [UInt32] {
    try flatten(session.snapshot().root).compactMap(\.text?.colorARGB)
  }

  @Test func secondaryIsTheForegroundDimmed() throws {
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteVStack {
        RemoteText("primary")
        RemoteText("secondary").foregroundStyle(.secondary)
        RemoteVStack {
          RemoteText("red")
          RemoteText("red secondary").foregroundStyle(.secondary)
          RemoteText("red tertiary").foregroundStyle(.tertiary)
        }
        .foregroundStyle(.hex(0xFF0000))
        // Nested: the secondary of the secondary.
        RemoteText("nested").foregroundStyle(.secondary).foregroundStyle(.secondary)
      }
    }
    // 60% of 255 is 153 (0x99), 30% is 77 (0x4D), 36% is 92 (0x5C). The default foreground is
    // black in the light appearance the session opens in.
    #expect(
      try textColors(open(document)) == [
        0xFF00_0000, 0x9900_0000, 0xFFFF_0000, 0x99FF_0000, 0x4DFF_0000, 0x5C00_0000,
      ])
  }

  /// However the foreground is set, a hierarchical style resolves against the one it replaces.
  @Test func theGenericEnvironmentSetterResolvesToo() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteText("text").environment(\.foregroundStyle, .secondary).foregroundStyle(.hex(0xFF0000))
    }
    #expect(try textColors(open(document)) == [0x99FF_0000])
  }

  @Test func aBackgroundInTheSecondaryStyleTakesTheForeground() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteColor.secondary.frame(width: 10, height: 10).foregroundStyle(.hex(0x0000FF))
    }
    #expect(
      try flatten(open(document).snapshot().root).contains { $0.backgroundARGB == 0x9900_00FF })
  }

  /// A foreground the host writes is dimmed on the player, so `.secondary` follows it.
  @Test func secondaryFollowsAVariableForeground() throws {
    let accent = RemoteVariable("accent", default: RemoteColorValue.hex(0x00FF00))
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteText("accent")
        RemoteText("dimmed").foregroundStyle(.secondary)
      }
      .foregroundStyle(.variable(accent))
    }
    let operations = try WireDocument.load(from: document.encode()).operations
    #expect(operations.contains { $0.opCode == .colorExpression })
    let session = try open(document)
    #expect(try textColors(session) == [0xFF00_FF00, 0x9900_FF00])
    #expect(session.setColor(0xFF12_3456, for: accent.name))
    #expect(try textColors(session) == [0xFF12_3456, 0x9912_3456])
  }
}
