import Foundation
import Testing

@testable import RcComposePlayerSwiftUI
@testable import RcNativePlayerCore

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

  /// Each value reaches the native setter for its type. A colour and an integer are both carried
  /// as integers, and a colour used to be the only integer the native player was sent, so an
  /// integer went to `setColor` and was refused there.
  @Test func eachValueReachesTheNativeSetterForItsType() async {
    let target = RecordingTarget()
    let cases: [(Value, RecordingTarget.Call)] = [
      (.float(0.75), .float(0.75)),
      (.text("Ready"), .string("Ready")),
      (.color(0xFF33_6699), .color(0xFF33_6699)),
      (.integer(7), .integer(7)),
      (.integer(Int32.min), .integer(Int(Int32.min))),
      (.boolean(true), .integer(1)),
      (.boolean(false), .integer(0)),
    ]
    for (value, expected) in cases {
      #expect(await value.apply(to: target, for: "USER:name"))
      #expect(target.calls.last == expected, "\(value)")
    }
    #expect(target.calls.count == cases.count)

    // The player's answer is passed back, so a refusal is retried rather than taken as applied.
    target.accepts = false
    #expect(!(await Value.integer(1).apply(to: target, for: "USER:name")))
  }
}

/// A native player that records which setter each value reached.
@MainActor
private final class RecordingTarget: NativeNamedValueTarget {
  enum Call: Equatable {
    case float(Float)
    case string(String)
    case color(UInt32)
    case integer(Int)
  }

  var calls: [Call] = []
  var accepts = true

  func setFloat(_ value: Float, for name: String) async -> Bool { record(.float(value)) }
  func setString(_ value: String, for name: String) async -> Bool { record(.string(value)) }
  func setColor(_ argb: UInt32, for name: String) async -> Bool { record(.color(argb)) }
  func setInteger(_ value: Int, for name: String) async -> Bool { record(.integer(value)) }

  private func record(_ call: Call) -> Bool {
    calls.append(call)
    return accepts
  }
}
