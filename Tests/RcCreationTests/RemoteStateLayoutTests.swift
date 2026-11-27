import Foundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import RcWireOperations
import Testing

@testable import RcCreation

/// State layouts written as Swift `switch` and `if`, read back through the native player, which
/// shows the branch the index selects.
@Suite struct RemoteStateLayoutTests {
  enum Tab: RemoteStateValue { case day, week, month }

  private func open(_ document: RemoteDocument) throws -> NativeSwiftDocumentSession {
    try NativeSwiftDocumentSession.open(data: document.encodedData())
  }

  /// The state layout's snapshot: the first node that has a state index.
  private func stateNode(_ session: NativeSwiftDocumentSession) throws -> NativeSwiftNodeSnapshot {
    func find(_ node: NativeSwiftNodeSnapshot) -> NativeSwiftNodeSnapshot? {
      if node.stateIndex != nil { return node }
      return node.children.lazy.compactMap(find).first
    }
    return try #require(find(try session.snapshot().root))
  }

  /// The texts the player shows: those not under a gone node.
  private func shownTexts(_ session: NativeSwiftDocumentSession) throws -> [String] {
    func walk(_ node: NativeSwiftNodeSnapshot) -> [String] {
      if node.visibility == NativeSwiftVisibility.gone { return [] }
      return (node.text.map { [$0.value] } ?? []) + node.children.flatMap(walk)
    }
    return walk(try session.snapshot().root)
  }

  @Test func switchOverAnEnumShowsTheSelectedCase() throws {
    let tab = RemoteVariable("tab", default: Tab.week)
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteVStack {
        RemoteStateLayout(tab) { tab in
          switch tab {
          case .day: RemoteText("Day")
          case .week: RemoteText("Week")
          case .month: RemoteText("Month")
          }
        }
        RemoteColor.blue.frame(width: 20, height: 20).onTap(.set(tab, to: .month))
      }
    }
    let session = try open(document)
    #expect(try stateNode(session).stateIndex == Tab.week.ordinal)
    #expect(try shownTexts(session) == ["Week"])
    #expect(session.setInteger(Tab.day.ordinal, for: tab.name))
    #expect(try shownTexts(session) == ["Day"])
    func find(_ node: NativeSwiftNodeSnapshot) -> NativeSwiftNodeSnapshot? {
      if node.isClickable { return node }
      return node.children.lazy.compactMap(find).first
    }
    let button = try #require(find(try session.snapshot().root))
    _ = try session.click(componentID: button.componentID, timeSeconds: 0)
    #expect(try shownTexts(session) == ["Month"])
  }

  @Test func ifElseOverABool() throws {
    let isOn = RemoteVariable("isOn", default: false)
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteStateLayout(isOn) { isOn in
        if isOn { RemoteText("On") } else { RemoteText("Off") }
      }
    }
    let session = try open(document)
    #expect(try shownTexts(session) == ["Off"])
    #expect(session.setInteger(1, for: isOn.name))
    #expect(try shownTexts(session) == ["On"])
  }

  @Test func remoteIfOverACondition() throws {
    let count = RemoteVariable("count", default: 0)
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteIf(count == 0) {
        RemoteText("Empty")
      } else: {
        RemoteVStack {
          RemoteText("Items")
          RemoteText("Clear")
        }
      }
    }
    let session = try open(document)
    #expect(try shownTexts(session) == ["Empty"])
    #expect(session.setInteger(3, for: count.name))
    #expect(try shownTexts(session) == ["Items", "Clear"])
  }

  /// One `StateLayout` with one child per state, each its own box, as AndroidX writes it.
  @Test func writesAStateLayoutWithOneChildPerState() throws {
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteStateLayout(RemoteVariable("tab", default: Tab.day)) { tab in
        RemoteText("\(String(describing: tab))")
        RemoteText("second view in the same state")
      }
    }
    let operations = try WireDocument.load(from: document.encode()).operations
    #expect(operations.filter { $0.opCode == .stateLayout }.count == 1)
    // Its branches, under the content node every layout puts around its children.
    let state = try stateNode(try open(document))
    let branches =
      state.children.count == 1 && state.children[0].kind == .content
      ? state.children[0].children : state.children
    #expect(branches.count == Tab.allCases.count)
    #expect(branches.allSatisfy { $0.componentKind == "BoxLayout" })
  }

  @Test func viewModifiersApplyTheirBody() throws {
    struct Card: RemoteViewModifier {
      func body(content: Content) -> some RemoteView {
        content.padding(12).background(.white)
      }
    }
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteText("Hello").modifier(Card())
    }
    let session = try open(document)
    func find(_ node: NativeSwiftNodeSnapshot) -> NativeSwiftNodeSnapshot? {
      if node.text != nil { return node }
      return node.children.lazy.compactMap(find).first
    }
    let text = try #require(find(try session.snapshot().root))
    #expect(text.padding.left == 12)
    #expect(text.backgroundARGB == 0xFFFF_FFFF)
  }
}
