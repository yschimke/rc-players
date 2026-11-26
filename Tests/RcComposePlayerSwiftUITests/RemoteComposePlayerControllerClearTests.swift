import Foundation
import RcNativePlayerTestDocuments
import Testing

@testable import RcComposePlayerSwiftUI
@testable import RcNativePlayerCore
@_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

/// The controller's `clear(_:)`, `setLong(_:for:)` and `setFloatArray(_:for:)` on the native
/// fallback backend, against the Kotlin controller (`RcComposePlayerController.clear`, whose
/// removal `RcComposePlayer` hands to `RcPlayerState.clearNamedValue`).
@MainActor
@Suite struct RemoteComposePlayerControllerClearTests {
  private typealias Value = ControllerNamedValue

  private static let declarations: NativeSwiftNamedVariableDeclarations = {
    let types: [String: Int] = [
      "USER:count": NativeSwiftNamedVariableType.int,
      "USER:title": NativeSwiftNamedVariableType.string,
      "USER:epoch": NativeSwiftNamedVariableType.long,
      "USER:series": NativeSwiftNamedVariableType.floatArray,
      "USER:progress": NativeSwiftNamedVariableType.float,
    ]
    var variables: [String: ParsedNamedVariable] = [:]
    // Sorted so each name gets the same id on every run.
    for (index, entry) in types.sorted(by: { $0.key < $1.key }).enumerated() {  // lint-ok: fixture
      variables[entry.key] = ParsedNamedVariable(id: index + 1, type: entry.value)
    }
    return NativeSwiftNamedVariableDeclarations(variables: variables)
  }()

  /// Records what the handler is handed: a value, or nil for a clear.
  private final class Updates {
    var all: [(name: String, value: Value?)] = []
  }

  @Test func aClearIsRefusedBeforeADocumentIsAttachedAndForAnUndeclaredName() {
    let controller = RemoteComposePlayerController()
    #expect(!controller.clear("count"))
    controller.backend.attach(Self.declarations)
    #expect(!controller.clear("missing"))
    #expect(controller.clear("count"))
    controller.backend.detach()
    #expect(!controller.clear("count"))
  }

  /// The stored value is removed and the clear handed to the player under the declared name; a
  /// name with no stored value has nothing of the host's to clear and hands nothing over.
  @Test func aClearRemovesTheStoredValueAndHandsTheClearToThePlayer() {
    let controller = RemoteComposePlayerController()
    controller.backend.attach(Self.declarations)
    let updates = Updates()
    controller.backend.installUpdateHandler { updates.all.append(($0, $1)) }
    #expect(controller.setInteger(4, for: "count"))
    updates.all = []

    #expect(controller.clear("count"))
    #expect(updates.all.map(\.name) == ["USER:count"])
    #expect(updates.all.map(\.value) == [nil])

    #expect(controller.clear("count"), "clearing twice")
    #expect(controller.clear("title"), "a declared name with no value")
    #expect(updates.all.count == 1, "a clear with nothing to clear was handed over")

    // Not replayed to the next handler: that player opened from the document's own values.
    updates.all = []
    controller.backend.installUpdateHandler { updates.all.append(($0, $1)) }
    #expect(updates.all.isEmpty)
  }

  /// The equal-write guard forgets a cleared value: writing it again is handed over.
  @Test func theSameValueWrittenAfterAClearIsHandedOverAgain() {
    let controller = RemoteComposePlayerController()
    controller.backend.attach(Self.declarations)
    let updates = Updates()
    controller.backend.installUpdateHandler { updates.all.append(($0, $1)) }
    #expect(controller.setInteger(4, for: "count"))
    #expect(controller.setInteger(4, for: "count"))
    #expect(updates.all.count == 1, "an equal write is no change")

    #expect(controller.clear("count"))
    #expect(controller.setInteger(4, for: "count"))
    #expect(updates.all.map(\.value) == [.integer(4), nil, .integer(4)])
  }

  /// A reopen's seed names a cleared name with no value until a write replaces it, or a document
  /// that does not declare the name is attached.
  @Test func theSeedCarriesAClearUntilAWriteOrAnUndeclaringDocument() {
    let controller = RemoteComposePlayerController()
    controller.backend.attach(Self.declarations)
    controller.backend.installUpdateHandler { _, _ in }
    #expect(controller.setInteger(4, for: "count"))
    #expect(controller.setString("Ready", for: "title"))

    #expect(controller.clear("count"))
    var seeded = controller.backend.seedValues
    #expect(seeded.map(\.name) == ["USER:title", "USER:count"])
    #expect(seeded.map(\.value) == [.text("Ready"), nil])

    #expect(controller.setInteger(5, for: "count"))
    seeded = controller.backend.seedValues
    #expect(seeded.map(\.name) == ["USER:count", "USER:title"])
    #expect(seeded.map(\.value) == [.integer(5), .text("Ready")])

    // Another attach — a second player sharing the controller — keeps a pending clear, re-keyed
    // as the values are; one whose document does not declare the name drops it.
    #expect(controller.clear("count"))
    controller.backend.attach(Self.declarations)
    #expect(controller.backend.seedValues.map(\.name) == ["USER:title", "USER:count"])
    #expect(controller.backend.seedValues.map(\.value) == [.text("Ready"), nil])
    var withoutCount: [String: ParsedNamedVariable] = [:]
    for name in ["USER:title"] {
      withoutCount[name] = ParsedNamedVariable(id: 1, type: NativeSwiftNamedVariableType.string)
    }
    controller.backend.attach(NativeSwiftNamedVariableDeclarations(variables: withoutCount))
    #expect(controller.backend.seedValues.map(\.name) == ["USER:title"])
  }

  @Test func longAndFloatArrayWritesAreTypedAndReachThePlayer() {
    let controller = RemoteComposePlayerController()
    #expect(!controller.setLong(1, for: "epoch"))
    #expect(!controller.setFloatArray([1], for: "series"))
    controller.backend.attach(Self.declarations)

    #expect(controller.setLong(1_700_000_000_000, for: "epoch"))
    #expect(controller.setFloatArray([1, 2.5], for: "series"))
    #expect(!controller.setLong(1, for: "count"))
    #expect(!controller.setLong(1, for: "series"))
    #expect(!controller.setFloatArray([1], for: "epoch"))
    #expect(!controller.setFloatArray([1], for: "progress"))
    #expect(!controller.setFloatArray([.nan], for: "series"))
    // Every element is checked, not only the first, as on the Kotlin controller.
    let limit = NativeSwiftDocumentDecoder.maximumFloatListValues
    let full = (0..<limit).map(Float.init)
    #expect(controller.setFloatArray(full, for: "series"), "a full-length finite array is taken")
    for index in [0, 1, limit / 2, limit - 1] {
      for bad in [Float.nan, .infinity, -.infinity] {
        var values = full
        values[index] = bad
        #expect(!controller.setFloatArray(values, for: "series"), "\(bad) at index \(index)")
      }
    }
    #expect(controller.setFloatArray([1, 2.5], for: "series"))
    #expect(!controller.setInteger(1, for: "epoch"))
    #expect(!controller.setFloat(1, for: "series"))
    let tooMany = Array(
      repeating: Float(1), count: NativeSwiftDocumentDecoder.maximumFloatListValues + 1)
    #expect(!controller.setFloatArray(tooMany, for: "series"))

    let updates = Updates()
    controller.backend.installUpdateHandler { updates.all.append(($0, $1)) }
    #expect(updates.all.map(\.name) == ["USER:epoch", "USER:series"])
    #expect(updates.all.map(\.value) == [.long(1_700_000_000_000), .floatArray([1, 2.5])])
    #expect(
      controller.backend.seedValues.map(\.value) == [
        .long(1_700_000_000_000), .floatArray([1, 2.5]),
      ])
  }

  /// A clear the player cannot take yet is offered again when it signals, as a write is.
  @Test func aDeferredClearIsOfferedAgainWhenThePlayerSignals() async throws {
    let target = ClearTarget()
    target.answers = [.deferred]
    let delivery = Task { @MainActor in
      await deliverNamedUpdate(nil, for: "USER:count", to: { target })
    }
    try await waitUntil("the delivery waits") { target.inputAvailability.waiterCount == 1 }
    #expect(target.clears == ["USER:count"] && target.writes == 0)

    target.inputAvailability.signal()
    await delivery.value
    #expect(target.clears == ["USER:count", "USER:count"] && target.writes == 0)
  }

  @MainActor
  private final class ClearTarget: NativeNamedValueTarget {
    var clears: [String] = []
    var writes = 0
    var answers: [RemoteComposeNativeInputAdmission] = []
    let inputAvailability = RemoteComposeNativeInputAvailability()

    func submit(_ value: RemoteComposeNativeNamedValue, for name: String) async
      -> RemoteComposeNativeInputAdmission
    {
      writes += 1
      return .accepted
    }

    func submitClear(for name: String) async -> RemoteComposeNativeInputAdmission {
      clears.append(name)
      return answers.isEmpty ? .accepted : answers.removeFirst()
    }
  }
}
