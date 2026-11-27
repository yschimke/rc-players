import Foundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import RcWireOperations
import Testing

@testable import RcCreation

/// `RemoteScrollView` writes AndroidX's scroll modifier, read back through the native player.
@Suite struct RemoteScrollViewTests {
  private func flatten(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
    [node] + node.children.flatMap(flatten)
  }

  private func scrolled(_ document: RemoteDocument) throws -> NativeSwiftNodeSnapshot {
    let session = try NativeSwiftDocumentSession.open(data: document.encodedData())
    return try #require(flatten(session.snapshot().root).first { $0.scrollDirection != nil })
  }

  private func rows() -> some RemoteView {
    RemoteForEach(1...20) { index in RemoteText("Row \(index)").padding(12) }
  }

  @Test func aVerticalScrollViewIsAScrolledColumn() throws {
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteScrollView { rows() }
    }
    let node = try scrolled(document)
    #expect(node.scrollDirection == .vertical)
    #expect(node.scrollOffset == 0)
    // A column, whose content holds the rows.
    #expect(node.kind == .column)
    #expect(node.children.first?.children.count == 20)
  }

  @Test func aHorizontalScrollViewStartsAtItsOffsetInPixels() throws {
    let document = RemoteDocument(width: 200, height: 100, displayScale: 2) {
      RemoteScrollView(.horizontal, initialOffset: 50) { rows() }
    }
    let node = try scrolled(document)
    #expect(node.scrollDirection == .horizontal)
    #expect(node.scrollOffset == 100)
  }

  /// The modifier's shape, as AndroidX's writer has it: a clip, the scroll, and the position as a
  /// touch expression that is the scroll container's only child, decelerating after a fling.
  @Test func theScrollModifierIsAndroidXs() throws {
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteScrollView { rows() }
    }
    let operations = try WireDocument.load(from: document.encode()).operations
    let scroll = try #require(
      operations.firstIndex {
        if case .scrollModifierOperation = $0 { true } else { false }
      })
    guard case .clipRectModifierOperation = operations[scroll - 1],
      case .scrollModifierOperation(let modifier) = operations[scroll],
      case .touchExpression(let touch) = operations[scroll + 1],
      case .containerEnd = operations[scroll + 2]
    else {
      Issue.record(
        "unexpected operations around the scroll: \(operations[scroll - 1...scroll + 2])")
      return
    }
    #expect(modifier.direction == 0)
    #expect(RemoteWriter.reference(touch.id).bitPattern == modifier.position.bitPattern)
    #expect(touch.max.bitPattern == modifier.max.bitPattern)
    #expect(
      touch.expression.map(\.bitPattern) == [
        RemoteWriter.reference(RemoteContextIds.idTouchPosY).bitPattern, Float(-1).bitPattern,
        RemoteWriter.reference(FloatExpressionOperator.offset + FloatExpressionOperator.mul)
          .bitPattern,
      ])
    #expect(touch.stopMode == 0)
  }

  @Test func aNegativeOffsetIsRefused() {
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteScrollView(initialOffset: -1) { rows() }
    }
    #expect(throws: RemoteEncodingError.invalidScrollOffset(-1)) { try document.encode() }
  }
}
