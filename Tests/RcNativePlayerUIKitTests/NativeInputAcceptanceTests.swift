#if canImport(UIKit)
import RcNativePlayerCore
import Testing
import UIKit
@testable import RcNativePlayerUIKit

@MainActor @Suite struct NativeInputAcceptanceTests {
  private func component(
    key: String = "message", value: String = "", submit: String = "send",
    queue: @escaping (@escaping (Bool) -> Void) -> Void
  ) -> RemoteComposeNativeCustomComponent {
    func string(_ id: Int, _ text: String) -> RemoteComposeNativeCustomProperty {
      .init(id: id, dataType: NativeSwiftCustomPropertyType.stringProperty,
            floatValue: 0, integerValue: 0, textValue: text)
    }
    return RemoteComposeNativeCustomComponent(
      config: NativeSwiftInput.text, componentID: 10,
      properties: [string(NativeSwiftInput.key, key), string(NativeSwiftInput.label, key),
                   string(NativeSwiftInput.value, value), string(NativeSwiftInput.submit, submit),
                   .init(id: NativeSwiftInput.valueReturn, dataType: NativeSwiftCustomPropertyType.textReturn,
                         floatValue: 0, integerValue: 100, textValue: nil)],
      returnValue: { _, value in
        if case .acknowledgedText(_, let reply) = value { queue(reply) }
      })
  }

  @Test func rejectedQueuedEditNeverReachesDraftsOrSubmit() throws {
    var replies: [(Bool) -> Void] = []
    let model = component { replies.append($0) }
    let drafts = NativeSwiftInputDrafts()
    var changes: [String] = []; var submissions = 0
    let view = NativeInputView(component: model, drafts: drafts, editable: true,
      onChange: { _, value in changes.append(value) }, onSubmit: { _, _ in submissions += 1 })
    let field = try #require(view.arrangedSubviews.compactMap { $0 as? UITextField }.first)
    let send = try #require(view.arrangedSubviews.compactMap { $0 as? UIButton }.last)
    field.text = String(repeating: "🏡", count: 1001)
    field.sendActions(for: UIControl.Event.editingChanged)
    send.sendActions(for: UIControl.Event.touchUpInside)
    #expect(drafts.values["message"] == "")
    #expect(changes.isEmpty && submissions == 0)
    let reply = try #require(replies.first)
    reply(false)
    #expect(field.text == "")
    #expect(drafts.values["message"] == "")
    #expect(changes.isEmpty && submissions == 0)
  }

  @Test func submitWaitsForAcceptedEditsInSiblingFields() throws {
    var replies: [(Bool) -> Void] = []
    let drafts = NativeSwiftInputDrafts(); let root = UIView()
    var submissions: [[String: String]] = []
    let first = NativeInputView(component: component(submit: "") { replies.append($0) },
      drafts: drafts, editable: true, onChange: { _, _ in }, onSubmit: { _, _ in })
    let last = NativeInputView(component: component(key: "room", value: "Kitchen") { replies.append($0) },
      drafts: drafts, editable: true, onChange: { _, _ in }, onSubmit: { _, values in submissions.append(values) })
    root.addSubview(first); root.addSubview(last)
    let field = try #require(first.arrangedSubviews.compactMap { $0 as? UITextField }.first)
    let send = try #require(last.arrangedSubviews.compactMap { $0 as? UIButton }.last)
    field.text = "Accepted message"; field.sendActions(for: UIControl.Event.editingChanged)
    send.sendActions(for: UIControl.Event.touchUpInside)
    #expect(submissions.isEmpty)
    let reply = try #require(replies.first)
    reply(true)
    #expect(submissions == [["message": "Accepted message", "room": "Kitchen"]])
  }
}
#endif
