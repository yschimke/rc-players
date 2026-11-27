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
    // The touch expression starts there too, for a player that applies it before a touch.
    let operations = try WireDocument.load(from: document.encode()).operations
    let touch = operations.compactMap { operation -> TouchExpression? in
      if case .touchExpression(let touch) = operation { touch } else { nil }
    }
    #expect(touch.map(\.value) == [100])
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
    #expect(touch.stopMode == RemoteWriter.TouchStopMode.gently)
  }

  @Test func aNegativeOffsetIsRefused() {
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteScrollView(initialOffset: -1) { rows() }
    }
    #expect(throws: RemoteEncodingError.invalidScrollOffset(-1)) { try document.encode() }
  }

  /// A position reads the scroll's offset, in points, as the player moves it.
  @Test func aPositionReadsTheOffsetInPoints() throws {
    let position = RemoteScrollPosition()
    let document = RemoteDocument(width: 200, height: 200, displayScale: 2) {
      RemoteVStack {
        RemoteText("\(position.offset, decimals: 0)")
        RemoteScrollView(position: position) { rows() }
      }
    }
    let session = try NativeSwiftDocumentSession.open(data: document.encodedData())
    func shown() throws -> [String] {
      try flatten(session.snapshot().root).compactMap(\.text?.value)
    }
    #expect(try shown().first == "0")
    let node = try #require(flatten(session.snapshot().root).first { $0.scrollDirection != nil })
    let id = try #require(node.scrollPositionID)
    // The player writes pixels; the document reads points.
    #expect(session.setFloat(240, forID: id))
    #expect(try shown().first == "120")
  }

  /// Paging snaps to even notches over the content's length, one per page, as AndroidX's
  /// `RemoteScrollState(notches:)` writes them.
  @Test func pagingSnapsToEvenNotches() throws {
    let position = RemoteScrollPosition()
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteScrollView(.horizontal, position: position, pageCount: 4) { rows() }
      RemoteText("\(position.contentLength, decimals: 0)")
    }
    let operations = try WireDocument.load(from: document.encode()).operations
    let modifier = try #require(
      operations.compactMap { operation -> ScrollModifierOperation? in
        if case .scrollModifierOperation(let modifier) = operation { modifier } else { nil }
      }.first)
    let touch = try #require(
      operations.compactMap { operation -> TouchExpression? in
        if case .touchExpression(let touch) = operation { touch } else { nil }
      }.first)
    #expect(touch.stopMode == RemoteWriter.TouchStopMode.notchesEven)
    #expect(
      touch.stopSpec.map(\.bitPattern) == [Float(4).bitPattern, modifier.notchMax.bitPattern])
    #expect(modifier.direction == 1)
  }

  @Test func aPositionBelongsToOneScrollView() {
    let position = RemoteScrollPosition()
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteScrollView(position: position) { rows() }
      RemoteScrollView(position: position) { rows() }
    }
    #expect(throws: RemoteEncodingError.scrollPositionReused) { try document.encode() }
  }

  @Test func aPageCountBelowOneIsRefused() {
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteScrollView(pageCount: 0) { rows() }
    }
    #expect(throws: RemoteEncodingError.invalidPageCount(0)) { try document.encode() }
  }
}
