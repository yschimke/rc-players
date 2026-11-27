import Foundation
import RcCreation
import RcCreationHost
@_spi(RemoteComposeController) import RcNativePlayerCore
import Testing

/// A host writes the variables its document reads through the same `RemoteVariable`s, checked by
/// type, and the native player shows the result.
@Suite struct RemoteVariableHostingTests {
  enum Tab: RemoteStateValue { case day, week, month }

  @Test func everyKindOfVariableIsWrittenByItsTypedHandle() throws {
    let tab = RemoteVariable("tab", default: Tab.week)
    let isOn = RemoteVariable("isOn", default: false)
    let count = RemoteVariable("count", default: 1)
    let level = RemoteVariable("level", default: 0.25)
    let title = RemoteVariable("title", default: "Before")
    let tint = RemoteVariable("tint", default: RemoteColorValue.hex(0xFF0000))
    let shown = RemoteVariable("shown", default: RemoteVisibility.visible)
    let document = RemoteDocument(width: 200, height: 200) {
      RemoteVStack {
        RemoteSwitch(tab) { tab in RemoteText("\(String(describing: tab))") }
        RemoteText("on").visible(if: RemoteBool(isOn))
        RemoteText("many").visible(if: count > 2)
        RemoteText("\(level, decimals: 2)")
        RemoteText(title)
        RemoteColor.variable(tint).frame(width: 10, height: 10)
        RemoteText("visible").visibility(shown)
      }
    }
    let session = try NativeSwiftDocumentSession.open(data: document.encodedData())
    func texts() throws -> [String] {
      func walk(_ node: NativeSwiftNodeSnapshot) -> [String] {
        if node.visibility == NativeSwiftVisibility.gone { return [] }
        return (node.text.map { [$0.value] } ?? []) + node.children.flatMap(walk)
      }
      return walk(try session.snapshot().root)
    }
    #expect(try texts() == ["week", "0.25", "Before", "visible"])

    #expect(session.set(tab, to: .month))
    #expect(session.set(isOn, to: true))
    #expect(session.set(count, to: 3))
    #expect(session.set(level, to: 0.75))
    #expect(session.set(title, to: "After"))
    #expect(session.set(tint, to: .hex(0x00FF00)))
    #expect(session.set(shown, to: .gone))
    #expect(try texts() == ["month", "on", "many", "0.75", "After"])

    func flatten(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
      [node] + node.children.flatMap(flatten)
    }
    #expect(try flatten(session.snapshot().root).contains { $0.backgroundARGB == 0xFF00_FF00 })
  }

  @Test func aVariableTheDocumentDoesNotHaveIsRefused() throws {
    let document = RemoteDocument(width: 100, height: 100) { RemoteText("x") }
    let session = try NativeSwiftDocumentSession.open(data: document.encodedData())
    #expect(!session.set(RemoteVariable("missing", default: 1.0), to: 2))
  }
}
