import Foundation
import Testing

@testable import RcComposePlayerSwiftUI
@testable import RcNativePlayerCore
@_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

/// The controller's native fallback must answer every write the way the Kotlin controller does:
/// false before a document is attached, for a name the document does not declare, and for a value
/// of another type — never a `true` that the native player later drops.
@MainActor
@Suite struct RemoteComposePlayerControllerTests {
  private typealias Value = ControllerNamedValue

  /// Declarations of the given names and wire types. The one place the test builds the core's
  /// internal representation; the ids are irrelevant to the controller, so they are assigned here.
  private static func declaring(_ types: [String: Int]) -> NativeSwiftNamedVariableDeclarations {
    var variables: [String: ParsedNamedVariable] = [:]
    for (index, entry) in types.sorted(by: { $0.key < $1.key }).enumerated() {
      variables[entry.key] = ParsedNamedVariable(id: index + 1, type: entry.value)
    }
    return NativeSwiftNamedVariableDeclarations(variables: variables)
  }

  private static let declarations = declaring([
    "USER:progress": NativeSwiftNamedVariableType.float,
    "USER:title": NativeSwiftNamedVariableType.string,
    "USER:accent": NativeSwiftNamedVariableType.color,
    "USER:count": NativeSwiftNamedVariableType.int,
    "USER:enabled": NativeSwiftNamedVariableType.int,
    "USER:epoch": NativeSwiftNamedVariableType.long,
  ])

  @Test func refusesEveryWriteBeforeADocumentIsAttached() {
    let controller = RemoteComposePlayerController()
    #expect(controller.names.isEmpty)
    #expect(!controller.setFloat(1, for: "progress"))
    #expect(!controller.setString("Ready", for: "title"))
    #expect(!controller.setColor(0xFF33_6699, for: "accent"))
    #expect(!controller.setInteger(1, for: "count"))
    #expect(!controller.setBoolean(true, for: "enabled"))
  }

  @Test func checksEachWriteAgainstTheAttachedDeclarations() {
    let controller = RemoteComposePlayerController()
    controller.backend.attach(Self.declarations)

    #expect(
      controller.names == [
        "USER:accent", "USER:count", "USER:enabled", "USER:epoch", "USER:progress", "USER:title",
      ])

    #expect(controller.setFloat(0.75, for: "progress"))
    #expect(controller.setString("Ready", for: "USER:title"))
    #expect(controller.setColor(0xFF33_6699, for: "accent"))
    #expect(controller.setInteger(7, for: "count"))
    #expect(controller.setBoolean(true, for: "enabled"))

    // Another declared type, an undeclared name, or a value the variable cannot hold.
    #expect(!controller.setInteger(1, for: "progress"))
    #expect(!controller.setBoolean(true, for: "progress"))
    #expect(!controller.setFloat(1, for: "count"))
    #expect(!controller.setColor(1, for: "count"))
    #expect(!controller.setInteger(1, for: "epoch"))
    #expect(!controller.setInteger(1, for: "missing"))
    #expect(!controller.setBoolean(true, for: "missing"))
    #expect(!controller.setFloat(.nan, for: "progress"))
    #expect(!controller.setFloat(.infinity, for: "progress"))
    #expect(!controller.setInteger(Int(Int32.max) + 1, for: "count"))
    #expect(!controller.setInteger(Int(Int32.min) - 1, for: "count"))
    let tooLong = String(repeating: "x", count: NativeSwiftDocumentDecoder.maximumStringBytes + 1)
    #expect(!controller.setString(tooLong, for: "title"))

    // What was accepted reaches the native player under the declared name, with its type intact:
    // a colour, an integer and a boolean stay apart.
    var applied: [String: Value] = [:]
    controller.backend.installUpdateHandler { applied[$0] = $1 }
    #expect(
      applied == [
        "USER:progress": .float(0.75),
        "USER:title": .text("Ready"),
        "USER:accent": .color(0xFF33_6699),
        "USER:count": .integer(7),
        "USER:enabled": .boolean(true),
      ])

    // A later write goes straight to the installed handler.
    #expect(controller.setBoolean(false, for: "enabled"))
    #expect(applied["USER:enabled"] == .boolean(false))
    #expect(controller.setInteger(Int(Int32.min), for: "count"))
    #expect(applied["USER:count"] == .integer(Int32.min))
  }

  @Test func aNewDocumentDropsTheValuesItCannotTake() {
    let controller = RemoteComposePlayerController()
    controller.backend.attach(Self.declarations)
    #expect(controller.setInteger(7, for: "count"))
    #expect(controller.setBoolean(true, for: "enabled"))
    #expect(controller.setFloat(0.5, for: "progress"))

    controller.backend.attach(
      Self.declaring([
        "USER:count": NativeSwiftNamedVariableType.float,
        "USER:enabled": NativeSwiftNamedVariableType.int,
      ]))

    var applied: [String: Value] = [:]
    controller.backend.installUpdateHandler { applied[$0] = $1 }
    #expect(applied == ["USER:enabled": .boolean(true)])
    #expect(controller.names == ["USER:count", "USER:enabled"])

    controller.backend.attach(.none)
    #expect(controller.names.isEmpty)
    #expect(!controller.setBoolean(true, for: "enabled"))
  }

  /// Stored values are re-keyed under the name the new document resolves them to, as the Kotlin
  /// controller does, so a bare key cannot be replayed onto a variable it no longer names.
  @Test func aNewDocumentReResolvesTheStoredNames() {
    let controller = RemoteComposePlayerController()
    controller.backend.attach(Self.declaring(["both": NativeSwiftNamedVariableType.int]))
    #expect(controller.setInteger(1, for: "both"))

    controller.backend.attach(
      Self.declaring([
        "both": NativeSwiftNamedVariableType.int,
        "USER:both": NativeSwiftNamedVariableType.int,
      ]))
    var applied: [String: Value] = [:]
    controller.backend.installUpdateHandler { applied[$0] = $1 }
    // Replayed under the name it is stored by, which is where the session will resolve it.
    #expect(applied == ["USER:both": .integer(1)])

    // A resolved name of another type drops the value instead.
    controller.backend.attach(
      Self.declaring([
        "both": NativeSwiftNamedVariableType.int,
        "USER:both": NativeSwiftNamedVariableType.float,
      ]))
    applied = [:]
    controller.backend.installUpdateHandler { applied[$0] = $1 }
    #expect(applied.isEmpty)
  }

  /// Without a document to judge them by — a refused theme, an unreadable document, or while the
  /// next one's declarations are read — writes are refused but the stored values are kept.
  @Test func detachingKeepsTheStoredValuesForTheNextDocument() {
    let controller = RemoteComposePlayerController()
    controller.backend.attach(Self.declarations)
    #expect(controller.setInteger(3, for: "count"))

    controller.backend.detach()
    #expect(controller.names.isEmpty)
    #expect(!controller.setInteger(4, for: "count"))

    controller.backend.attach(Self.declarations)
    var applied: [String: Value] = [:]
    controller.backend.installUpdateHandler { applied[$0] = $1 }
    #expect(applied == ["USER:count": .integer(3)])
  }

  /// A native player that opens its document anew is handed every value written so far as its
  /// seed — it applies those the old session had not taken — by the name the document declares
  /// and as the native player takes it (a boolean is the integer 1 or 0).
  @Test func seedValuesAreEveryStoredValueOnceAHandlerIsInstalled() {
    let controller = RemoteComposePlayerController()
    #expect(controller.backend.seedValues.isEmpty)  // Nothing attached or installed.
    controller.backend.attach(Self.declarations)
    #expect(controller.setInteger(5, for: "count"))
    #expect(controller.setBoolean(true, for: "enabled"))
    #expect(controller.backend.seedValues.isEmpty, "not yet handed to a player")

    controller.backend.installUpdateHandler { _, _ in }
    let seeded = controller.backend.seedValues
    #expect(seeded.map(\.name) == ["USER:count", "USER:enabled"])
    #expect(seeded.map(\.value) == [.integer(5), .integer(1)])

    // Without a handler — the player is being rebuilt — the values wait for the next one.
    controller.backend.installUpdateHandler(nil)
    #expect(controller.backend.seedValues.isEmpty)
  }

  /// A deferred value — the document is still opening, the scene is in the background, or the
  /// queue is full — waits for the player to say input may be accepted again, however long that
  /// takes, and is then offered again. It does not poll while it waits.
  @Test func aDeferredValueWaitsForThePlayerToTakeInputAgain() async throws {
    let target = RecordingTarget()
    target.answers = [.deferred, .deferred]
    let delivery = Task { @MainActor in
      await Value.integer(5).deliver(for: "USER:count", to: { target }, isCurrent: { true })
    }
    try await Self.until("the delivery waits") { target.inputAvailability.waiterCount == 1 }
    #expect(target.calls == [.integer(5)], "offered again without a signal")

    target.inputAvailability.signal()
    try await Self.until("the delivery waits again") {
      target.calls.count == 2 && target.inputAvailability.waiterCount == 1
    }
    #expect(target.calls == [.integer(5), .integer(5)])

    target.inputAvailability.signal()
    await delivery.value
    #expect(target.calls == [.integer(5), .integer(5), .integer(5)])
  }

  /// A signal that arrives while the value is being offered is not missed.
  @Test func aSignalDuringTheOfferIsNotMissed() async {
    let target = RecordingTarget()
    target.answers = [.deferred]
    target.onCall = { target.inputAvailability.signal() }
    await Value.integer(5).deliver(for: "USER:count", to: { target }, isCurrent: { true })
    #expect(target.calls == [.integer(5), .integer(5)])
  }

  /// A refusal is the document's final answer — a validation or execution-limit failure the
  /// player has already reported — so it is not offered again, even while other input is queued.
  @Test func aRefusedValueIsNotOfferedAgain() async {
    let target = RecordingTarget()
    target.answers = [.refused]
    await Value.integer(5).deliver(for: "USER:count", to: { target }, isCurrent: { true })
    #expect(target.calls == [.integer(5)])
  }

  /// A waiting delivery ends when the player goes away, when its task is cancelled — a newer value
  /// for the name, or a rebuild — or when a newer value has started.
  @Test func aWaitingDeliveryEndsWithThePlayerOrItsTask() async throws {
    var target: RecordingTarget? = RecordingTarget()
    let availability = target!.inputAvailability
    target!.answers = [.deferred]
    let gone = Task { @MainActor in
      await Value.integer(5).deliver(for: "USER:count", to: { target }, isCurrent: { true })
    }
    try await Self.until("the delivery waits") { availability.waiterCount == 1 }
    target = nil
    availability.close()
    await gone.value

    let cancelled = RecordingTarget()
    cancelled.answers = [.deferred]
    let delivery = Task { @MainActor in
      await Value.integer(5).deliver(for: "USER:count", to: { cancelled }, isCurrent: { true })
    }
    try await Self.until("the delivery waits") { cancelled.inputAvailability.waiterCount == 1 }
    delivery.cancel()
    await delivery.value
    #expect(cancelled.calls == [.integer(5)])

    let superseded = RecordingTarget()
    superseded.answers = [.deferred]
    superseded.onCall = { superseded.inputAvailability.signal() }
    await Value.integer(5).deliver(
      for: "USER:count", to: { superseded }, isCurrent: { superseded.calls.isEmpty })
    #expect(superseded.calls == [.integer(5)])
  }

  /// Each value reaches the native player as the value of its type. A colour and an integer are
  /// both carried as integers, and a colour used to be the only integer the native player was
  /// sent, so an integer went to `setColor` and was refused there.
  @Test func eachValueReachesTheNativePlayerAsItsType() async {
    let cases: [(Value, RemoteComposeNativeNamedValue)] = [
      (.float(0.75), .float(0.75)),
      (.text("Ready"), .text("Ready")),
      (.color(0xFF33_6699), .color(0xFF33_6699)),
      (.integer(7), .integer(7)),
      (.integer(Int32.min), .integer(Int(Int32.min))),
      (.boolean(true), .integer(1)),
      (.boolean(false), .integer(0)),
    ]
    for (value, expected) in cases {
      let target = RecordingTarget()
      await value.deliver(for: "USER:name", to: { target }, isCurrent: { true })
      #expect(target.calls == [expected], "\(value)")
    }
  }

  /// Returns once `condition` holds, yielding to the tasks it waits on; fails the test after
  /// `seconds` rather than hanging.
  private static func until(
    _ what: String, within seconds: TimeInterval = 30, _ condition: () -> Bool
  ) async throws {
    let deadline = Date().addingTimeInterval(seconds)
    while !condition() {
      try #require(Date() < deadline, "timed out waiting until \(what)")
      await Task.yield()
    }
  }
}

/// A native player that records each value it is offered and answers from `answers`, then
/// `.accepted`.
@MainActor
private final class RecordingTarget: NativeNamedValueTarget {
  var calls: [RemoteComposeNativeNamedValue] = []
  var answers: [RemoteComposeNativeInputAdmission] = []
  var onCall: () -> Void = {}
  let inputAvailability = RemoteComposeNativeInputAvailability()

  func submit(_ value: RemoteComposeNativeNamedValue, for name: String) async
    -> RemoteComposeNativeInputAdmission
  {
    calls.append(value)
    onCall()
    return answers.isEmpty ? .accepted : answers.removeFirst()
  }
}
