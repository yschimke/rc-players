import Testing
@testable import RcNativePlayerCore

@Suite struct NativeSwiftInputTests {
  @Test func portableFixtureReturnsTextThroughTheDeclaredChannel() throws {
    let session = try NativeSwiftDocumentSession.open(data: NativeTestFixtures.data("message-inputs.rc"))
    func field(_ node: NativeSwiftNodeSnapshot) -> NativeSwiftCustomSnapshot? {
      if node.componentID == 13 { return node.custom }
      return node.children.compactMap(field).first
    }
    let initial = field(try session.snapshot().root)
    #expect(initial?.config == NativeSwiftInput.text)
    #expect(initial?.properties.first { $0.id == NativeSwiftInput.value }?.textValue == "Dinner is ready")
    #expect(session.returnCustomText("Hello 🏡\nRoom", componentID: 13, propertyID: NativeSwiftInput.valueReturn))
    let updated = field(try session.snapshot().root)
    #expect(updated?.properties.first { $0.id == NativeSwiftInput.value }?.textValue == "Hello 🏡\nRoom")
    #expect(!session.returnCustomText("Ignored", componentID: 13, propertyID: NativeSwiftInput.value))
  }

  @MainActor @Test func draftsSurviveSnapshotsAndExplicitResetRestoresServerValue() {
    let drafts = NativeSwiftInputDrafts()
    #expect(drafts.value(for: "message", documentValue: "initial") == "initial")
    drafts.update("Hello 🏡\nRoom", for: "message")
    #expect(drafts.value(for: "message", documentValue: "stale") == "Hello 🏡\nRoom")
    #expect(drafts.value(for: "channel", documentValue: "matrix") == "matrix")
    let submitted = drafts.values
    drafts.update("later", for: "message")
    #expect(submitted["message"] == "Hello 🏡\nRoom")
    #expect(submitted["channel"] == "matrix")
    drafts.reset("message")
    #expect(drafts.value(for: "message", documentValue: "server") == "server")
    #expect(NativeSwiftInput.configs == ["rc:input/text:v1", "rc:input/choice:v1"])
  }
}
