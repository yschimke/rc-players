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
    drafts.update("old", for: "obsolete")
    _ = drafts.value(for: "obsolete", documentValue: "old")
    #expect(drafts.values(for: ["message", "channel"]) == ["message": "Hello 🏡\nRoom", "channel": "matrix"])
    let submitted = drafts.values
    drafts.update("later", for: "message")
    #expect(submitted["message"] == "Hello 🏡\nRoom")
    #expect(submitted["channel"] == "matrix")
    drafts.reset("message")
    #expect(drafts.value(for: "message", documentValue: "server") == "server")
    #expect(NativeSwiftInput.configs.count == 5)
  }
  @Test func sharedContractValidationVectors() throws {
    let data = try NativeTestFixtures.data("input-controls-v1.tsv")
    let source = String(decoding: data, as: UTF8.self)
    for line in source.split(separator: "\n") where !line.hasPrefix("#") {
      let fields = line.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
      let result = NativeSwiftInput.isValid(config: "rc:input/\(fields[0]):v1", value: fields[1] == "~" ? "" : fields[1], options: fields[2].split(separator: ";").map(String.init), minimum: Double(fields[3]), maximum: Double(fields[4]), step: Double(fields[5]))
      #expect(result == (fields[6] == "true"), "\(line)")
    }
  }

  @Test func expandedFixtureReturnsBooleanNumericAndRangeStrings() throws {
    let session = try NativeSwiftDocumentSession.open(data: NativeTestFixtures.data("input-controls.rc"))
    func controls(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftCustomSnapshot] {
      (node.custom.map { [$0] } ?? []) + node.children.flatMap(controls)
    }
    #expect(Set(controls(try session.snapshot().root).map { $0.config }) == NativeSwiftInput.configs)
    for (componentID, value) in [(12, "true"), (13, "22.5"), (14, "51")] {
      #expect(session.returnCustomText(value, componentID: componentID, propertyID: NativeSwiftInput.valueReturn))
      let updated = controls(try session.snapshot().root).first { $0.properties.contains { $0.id == NativeSwiftInput.value && $0.textValue == value } }
      #expect(updated != nil)
    }
  }

}
