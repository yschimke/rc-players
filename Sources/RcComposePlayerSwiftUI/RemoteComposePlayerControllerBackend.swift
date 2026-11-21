import Foundation
#if canImport(RcComposePlayer)
  import RcComposePlayer
#endif
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif
#if canImport(RcNativePlayerUIKit)
  import RcNativePlayerUIKit
#endif

// Which player `RemoteComposePlayerController` drives, decided once per build. The overlay compiles
// in three configurations and each has a different set of modules, so code naming one player's
// types can only exist where that player is linked:
//
// - UIKit with the Kotlin framework (iOS): the Kotlin player and its controller.
// - The native Swift player without the Kotlin UIKit host: visionOS, tvOS, a native-only build,
//   and macOS — `RcNativePlayerUIKit` is an unconditional overlay dependency, so it is importable
//   there too, but no macOS view takes a controller, so nothing ever attaches a document and every
//   write is refused.
// - Neither: a build that links no native player at all, where there is nothing to drive.
#if canImport(UIKit) && canImport(RcComposePlayer)
  typealias ControllerBackend = KotlinControllerBackend
#elseif canImport(RcNativePlayerUIKit)
  typealias ControllerBackend = NativeControllerBackend
#else
  typealias ControllerBackend = DetachedControllerBackend
#endif

/// A value the controller writes to a named variable, with its type, so a colour and an integer —
/// both carried as integers — reach the setter that type-checks each. The overlay keeps its own
/// type rather than a player's because it also compiles with no native player linked at all.
enum ControllerNamedValue: Equatable {
  case float(Float)
  case text(String)
  /// ARGB, as a document stores a colour.
  case color(UInt32)
  /// The 32 bits a document's integer holds.
  case integer(Int32)
  /// An integer variable holding 1 or 0, as AndroidX stores a boolean.
  case boolean(Bool)
}

/// What every backend provides. A backend answers each write finally: `true` only for a value the
/// attached document will apply, `false` before a document is attached, for a name it does not
/// declare, or for a value its variable cannot hold.
@MainActor
protocol ControllerBackendProtocol: AnyObject {
  init()
  /// The names the attached document declares; empty before one is attached.
  var names: [String] { get }
  func set(_ value: ControllerNamedValue, for name: String) -> Bool
}

#if canImport(UIKit) && canImport(RcComposePlayer)
  /// Forwards to the Kotlin controller, which the Kotlin player attaches documents to itself.
  @MainActor
  final class KotlinControllerBackend: ControllerBackendProtocol {
    let kotlinController = RcComposePlayerController()

    init() {}

    var names: [String] { kotlinController.names }

    func set(_ value: ControllerNamedValue, for name: String) -> Bool {
      switch value {
      case .float(let value): kotlinController.setFloat(name: name, value: value)
      case .text(let value): kotlinController.setString(name: name, value: value)
      case .color(let argb): kotlinController.setColor(name: name, argb: Int32(bitPattern: argb))
      case .integer(let value): kotlinController.setInteger(name: name, value: value)
      case .boolean(let value): kotlinController.setBoolean(name: name, value: value)
      }
    }
  }
#elseif canImport(RcNativePlayerUIKit)
  /// Checks each write against the attached document's declarations — the same check the native
  /// session makes — and hands an accepted value to the native player, which applies it once its
  /// asynchronous session opens.
  @MainActor
  final class NativeControllerBackend: ControllerBackendProtocol {
    /// The attached document's declarations; none until a native player has loaded a document.
    private var declarations = NativeSwiftNamedVariableDeclarations.none
    /// The values written so far, by the name the attached document declares them under.
    private var values: [String: ControllerNamedValue] = [:]
    private var updateHandler: ((String, ControllerNamedValue) -> Void)?

    init() {}

    var names: [String] { declarations.names }

    func set(_ value: ControllerNamedValue, for name: String) -> Bool {
      guard let declared = declarations.declaredName(for: name),
        declarations.accepts(value, for: declared)
      else { return false }
      values[declared] = value
      updateHandler?(declared, value)
      return true
    }

    /// Attaches the declarations of the document a native player is showing, as the Kotlin
    /// controller attaches when its player is built. Each stored value is re-keyed under the name
    /// the new document resolves its key to, and dropped when that variable cannot take it; the
    /// rest are re-applied when the player installs its update handler. Re-keying keeps the two
    /// backends in step: checking the old key let a bare name reach a different variable.
    func attach(_ declarations: NativeSwiftNamedVariableDeclarations) {
      self.declarations = declarations
      var kept: [String: ControllerNamedValue] = [:]
      for name in values.keys.sorted() {
        guard let value = values[name], let declared = declarations.declaredName(for: name),
          declarations.accepts(value, for: declared)
        else { continue }
        kept[declared] = value
      }
      values = kept
    }

    /// Forgets the attached declarations without a new document to judge the stored values by —
    /// the player is not showing one (a refused theme) or its document could not be read. Writes
    /// are refused until the next `attach`, and the values written so far are kept for it.
    func detach() {
      declarations = .none
    }

    /// Installs the native player's update handler, replaying every value written so far, or
    /// removes it with `nil`.
    func installUpdateHandler(_ handler: ((String, ControllerNamedValue) -> Void)?) {
      updateHandler = handler
      guard let handler else { return }
      for name in values.keys.sorted() {
        guard let value = values[name] else { continue }
        handler(name, value)
      }
    }
  }

  /// The native player's named setters, as the controller's values reach them. A protocol so the
  /// routing below can be tested without a player.
  @MainActor
  protocol NativeNamedValueTarget: AnyObject {
    func setFloat(_ value: Float, for name: String) async -> Bool
    func setString(_ value: String, for name: String) async -> Bool
    func setColor(_ argb: UInt32, for name: String) async -> Bool
    func setInteger(_ value: Int, for name: String) async -> Bool
    /// Whether the player is still opening its document, so a refused write may yet be accepted.
    var isOpeningDocument: Bool { get }
  }

  extension ControllerNamedValue {
    /// Writes this value through the native setter for its type; a boolean is the integer 1 or 0,
    /// as the document stores it.
    @MainActor
    func apply(to target: some NativeNamedValueTarget, for name: String) async -> Bool {
      switch self {
      case .float(let value): await target.setFloat(value, for: name)
      case .text(let value): await target.setString(value, for: name)
      case .color(let argb): await target.setColor(argb, for: name)
      case .integer(let value): await target.setInteger(Int(value), for: name)
      case .boolean(let value): await target.setInteger(value ? 1 : 0, for: name)
      }
    }

    /// Writes this value into the native player. The controller has already checked it against
    /// the document's declarations, so a refusal means the asynchronous session has not opened
    /// yet, or its input queue is full. It is retried for as long as the player is still opening
    /// its document — however long its resources take — and then `openRetries` times for a full
    /// queue. It stops as soon as the player is gone, or `isCurrent` says a newer value for this
    /// name has started or the player was rebuilt.
    @MainActor
    func deliver<Target: NativeNamedValueTarget>(
      for name: String, to target: () -> Target?,
      retryInterval: UInt64 = 50_000_000, openRetries: Int = 50,
      isCurrent: () -> Bool
    ) async {
      var retriesAfterOpen = openRetries
      while true {
        // The strong reference lives only for the call, not across the sleep below.
        do {
          guard let target = target(), isCurrent() else { return }
          if await apply(to: target, for: name) { return }
          if !target.isOpeningDocument {
            retriesAfterOpen -= 1
            if retriesAfterOpen <= 0 { return }
          }
        }
        try? await Task.sleep(nanoseconds: retryInterval)
      }
    }
  }

  extension NativeSwiftNamedVariableDeclarations {
    func accepts(_ value: ControllerNamedValue, for name: String) -> Bool {
      switch value {
      case .float(let value): acceptsFloat(value, for: name)
      case .text(let value): acceptsString(value, for: name)
      case .color: acceptsColor(for: name)
      case .integer, .boolean: acceptsInteger(for: name)
      }
    }
  }
#else
  /// No player this controller could drive is linked in, so every write is refused.
  @MainActor
  final class DetachedControllerBackend: ControllerBackendProtocol {
    init() {}

    var names: [String] { [] }

    func set(_ value: ControllerNamedValue, for name: String) -> Bool { false }
  }
#endif
