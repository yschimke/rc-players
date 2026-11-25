import Foundation
#if canImport(RcComposePlayer)
  import RcComposePlayer
#endif
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif
#if canImport(RcNativePlayerUIKit)
  @_spi(RemoteComposeController) import RcNativePlayerUIKit
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

/// What every backend provides. A backend answers each write finally: `true` for a value the
/// attached document declares and can hold — handed to the player, or equal to the last value
/// written to that name, which is no change and is not handed over again (the variable keeps
/// whatever the document has made of it since) — and `false` before a document is attached, for
/// a name it does not declare, or for a value its variable cannot hold.
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
  /// session makes — and hands an accepted value to the native player, which applies it once it can:
  /// after its asynchronous session opens, its scene returns from the background, or its input
  /// queue has room. A session the player reopens starts from the named values the one it replaces
  /// held, and from the values held here (`seedValues`) that that session had not taken.
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
      // A write equal to the last one is no change, as the Kotlin player diffs the host's values
      // against the ones it last applied (`applyHostValues`): a document action that changed the
      // variable since keeps its value.
      guard values[declared] != value else { return true }
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

    /// The values written so far, by the name the player's document declares them under, which
    /// seed a session the native player reopens. The player applies each one unless the replaced
    /// session took that very write: not yet delivered, refused (judged afresh), or written again
    /// since — even back to the value the session took — is applied. Empty
    /// while no update handler is installed: the values are not yet re-keyed for the player's
    /// document.
    ///
    /// The Kotlin player builds its state once per document, seeded with the host's values, and
    /// keeps it through the background. The native player reopens its bytes for that, carries the
    /// named values the old session held, and adds these.
    var seedValues: [(name: String, value: RemoteComposeNativeNamedValue)] {
      guard updateHandler != nil else { return [] }
      return values.keys.sorted().compactMap { name in
        values[name].map { (name: name, value: $0.nativeValue) }
      }
    }
  }

  /// The native player, as the controller's values reach it. A protocol so the delivery below can
  /// be tested without a player.
  @MainActor
  protocol NativeNamedValueTarget: AnyObject {
    func submit(_ value: RemoteComposeNativeNamedValue, for name: String) async
      -> RemoteComposeNativeInputAdmission
    /// Signals when a deferred write may be offered again.
    var inputAvailability: RemoteComposeNativeInputAvailability { get }
  }

  extension ControllerNamedValue {
    /// This value as the native player takes it; a boolean is the integer 1 or 0, as the document
    /// stores it.
    var nativeValue: RemoteComposeNativeNamedValue {
      switch self {
      case .float(let value): .float(value)
      case .text(let value): .text(value)
      case .color(let argb): .color(argb)
      case .integer(let value): .integer(Int(value))
      case .boolean(let value): .integer(value ? 1 : 0)
      }
    }

    /// Writes this value into the native player. The controller has already answered `true`, so a
    /// write the player cannot take yet — its document is still opening, its scene is in the
    /// background, its input queue is full, or a reopen discarded the session that took it — is
    /// offered again each time the player signals that input may be accepted, however long that
    /// takes. Nothing polls while it waits. A refusal is the session's final answer — the document
    /// is failed, declines the value (which reports nothing), or failed applying it (reported
    /// through `onError`; a value that breaks an execution limit fails the live session, which
    /// shows the error until input repairs it) — and is not offered to that session again. The
    /// value stays with the controller, as on the Kotlin player, and each session the player
    /// reopens is seeded with it (`seedValues`) and judges it afresh: there a value that breaks an
    /// execution limit is left out of the first frame and reported through `onError` once per
    /// session, and that session stays open and takes later writes. It stops as soon as the player
    /// is gone, the task is cancelled, or `isCurrent` says a newer value for this name has
    /// started, the player was rebuilt, or a reopen took the value in its seed.
    @MainActor
    func deliver<Target: NativeNamedValueTarget>(
      for name: String, to target: () -> Target?, isCurrent: () -> Bool
    ) async {
      while true {
        let availability: RemoteComposeNativeInputAvailability
        let mark: UInt64
        // The strong reference lives only for the offer, not across the wait below.
        do {
          guard !Task.isCancelled, let target = target(), isCurrent() else { return }
          availability = target.inputAvailability
          mark = availability.mark
          switch await target.submit(nativeValue, for: name) {
          case .accepted, .refused: return
          case .deferred: break
          }
        }
        await availability.wait(after: mark)
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
