import Foundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import RcWireOperations
import Testing

@testable import RcCreation

/// The building blocks component libraries are made from: conditions, actions that compute
/// their value, the environment, paths and system values, read back through the native player.
@Suite struct RemoteStateTests {
  private func components(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
    node.children.flatMap { child in child.kind == .content ? components(child) : [child] }
  }

  private func open(_ document: RemoteDocument) throws -> NativeSwiftDocumentSession {
    try NativeSwiftDocumentSession.open(data: document.encodedData())
  }

  private func operations(_ document: RemoteDocument) throws -> [WireOperation] {
    try WireDocument.load(from: document.encode()).operations
  }

  @Test func toggleFlipsABoolAndConditionsFollow() throws {
    let isOn = RemoteVariable("wifi", default: false)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteColor.green.frame(width: 10, height: 10).visible(if: RemoteBool(isOn))
        RemoteColor.red.frame(width: 10, height: 10).visible(if: !isOn)
        RemoteColor.blue.frame(width: 40, height: 20).onTap(.toggle(isOn))
      }
    }
    let session = try open(document)
    func visibilities() throws -> [Int?] {
      let stack = try #require(components(try session.snapshot().root).first)
      return components(stack).prefix(2).map(\.visibility)
    }
    #expect(try visibilities() == [NativeSwiftVisibility.gone, NativeSwiftVisibility.visible])
    let stack = try #require(components(try session.snapshot().root).first)
    let button = try #require(components(stack).last)
    _ = try session.click(componentID: button.componentID, timeSeconds: 0)
    #expect(try visibilities() == [NativeSwiftVisibility.visible, NativeSwiftVisibility.gone])
    _ = try session.click(componentID: button.componentID, timeSeconds: 1)
    #expect(try visibilities() == [NativeSwiftVisibility.gone, NativeSwiftVisibility.visible])
  }

  @Test func equalityPicksOneOfSeveral() throws {
    let selection = RemoteVariable("tab", default: 1)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        for index in 0..<3 {
          RemoteColor.blue.frame(width: 10, height: 10).visible(if: selection == index)
        }
      }
    }
    let session = try open(document)
    func shown() throws -> [Bool] {
      let stack = try #require(components(try session.snapshot().root).first)
      return components(stack).map { $0.visibility == NativeSwiftVisibility.visible }
    }
    #expect(try shown() == [false, true, false])
    #expect(session.setInteger(2, for: selection.name))
    #expect(try shown() == [false, false, true])
  }

  @Test func incrementStaysInItsRange() throws {
    let value = RemoteVariable("count", default: 2.0)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteText("\(value, decimals: 0)")
        RemoteColor.blue.frame(width: 20, height: 20).onTap(.increment(value, by: 1, in: 0...3))
      }
    }
    let session = try open(document)
    let stack = try #require(components(try session.snapshot().root).first)
    let button = try #require(components(stack).last)
    func text() throws -> String? {
      components(try #require(components(try session.snapshot().root).first)).first?.text?.value
    }
    #expect(try text() == "2")
    _ = try session.click(componentID: button.componentID, timeSeconds: 0)
    #expect(try text() == "3")
    _ = try session.click(componentID: button.componentID, timeSeconds: 1)
    #expect(try text() == "3")
  }

  @Test func zeroPaddedNumbersAndModulo() throws {
    let minutes = RemoteVariable("minutes", default: 427.0)
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText(
        "\(RemoteFloat(minutes) / 60 - (RemoteFloat(minutes) % 60) / 60, integerDigits: 2, zeroPadded: true):\(RemoteFloat(minutes) % 60, integerDigits: 2, zeroPadded: true)"
      )
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "07:07")
  }

  @Test func clockReadsTheSystemTime() throws {
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText("\(RemoteFloat(.hour), integerDigits: 2, zeroPadded: true)")
    }
    let expressions = try operations(document).compactMap {
      if case .textFromFloat(let text) = $0 { text } else { nil }
    }
    #expect(
      expressions.first?.value.bitPattern
        == _RemoteWriter.reference(RemoteContextIds.idTimeInHr).bitPattern)
    #expect(expressions.first?.flags == TextFromFloat.padPreZero | TextFromFloat.padAfterZero)
  }

  @Test func environmentReachesReaders() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteEnvironmentReader { environment in
        RemoteColor(storage: environment.tint.storage).frame(width: 10, height: 10)
      }
      .tint(.hex(0x123456))
    }
    #expect(
      try components(try open(document).snapshot().root).first?.backgroundARGB == 0xFF12_3456)
  }

  @Test func pathsAreDeclaredOnceAndDrawn() throws {
    let check = try #require(RemotePath(svg: "M4 12 L9 17 L20 6"))
    let document = RemoteDocument(width: 100, height: 100, displayScale: 2) {
      RemoteCanvas { context, _ in
        context.stroke(.path(check), with: .black, lineWidth: 2)
        context.fill(.path(check), with: .red)
      }
      .frame(width: 24, height: 24)
    }
    let operations = try operations(document)
    let paths = operations.compactMap { if case .pathData(let path) = $0 { path } else { nil } }
    try #require(paths.count == 1)
    #expect(
      PathCommand.decode(paths[0].pathData) == [
        .moveTo(x: 8, y: 24), .lineTo(x: 18, y: 34), .lineTo(x: 40, y: 12), .done,
      ])
    #expect(operations.filter { $0.opCode == .drawPath }.count == 2)
    _ = try open(document).snapshot()
  }

  @Test func svgPathDataParses() throws {
    let path = try #require(RemotePath(svg: "m1,2 h3 v4 c1 1 2 2 3 3 s1 1 2 2 q1 0 2 1 t1 1 z"))
    #expect(path.elements.count == 8)
    #expect(path.elements.first == .move(1, 2))
    #expect(path.elements[1] == .line(4, 2))
    #expect(path.elements[2] == .line(4, 6))
    #expect(RemotePath(svg: "M0 0 A1 1 0 0 1 2 2") == nil)
  }

  @Test func boolAndIntVariablesWithOneNameConflict() {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteColor.red.frame(width: 1, height: 1).visible(
          if: RemoteBool(RemoteVariable("x", default: true)))
        RemoteColor.red.frame(width: 1, height: 1).visible(if: RemoteVariable("x", default: 1) == 1)
      }
    }
    #expect(throws: RemoteEncodingError.conflictingVariable(name: "x")) { try document.encode() }
  }
}
