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
  /// An instant in epoch milliseconds, as a document's `LongConstant` holds one.
  case long(Int64)
  /// A float array, which replaces the list the document keeps at that name.
  case floatArray([Float])

  /// Equal as the Kotlin controller's values are: `RcNamedValue.FloatValue` and
  /// `FloatArrayValue` are data classes (RcPlayerState.kt:2489, 2501), so a float compares as
  /// `Float.equals` does — -0.0 differs from 0.0 and every NaN equals every NaN — where Swift's
  /// `==` says the opposite of both. The backend's skip of an equal write then matches Kotlin's.
  static func == (lhs: Self, rhs: Self) -> Bool {
    switch (lhs, rhs) {
    case (.float(let lhs), .float(let rhs)): sameKotlinFloat(lhs, rhs)
    case (.floatArray(let lhs), .floatArray(let rhs)):
      lhs.count == rhs.count && zip(lhs, rhs).allSatisfy(sameKotlinFloat)
    case (.text(let lhs), .text(let rhs)): lhs == rhs
    case (.color(let lhs), .color(let rhs)): lhs == rhs
    case (.integer(let lhs), .integer(let rhs)): lhs == rhs
    case (.boolean(let lhs), .boolean(let rhs)): lhs == rhs
    case (.long(let lhs), .long(let rhs)): lhs == rhs
    case (.float, _), (.floatArray, _), (.text, _), (.color, _), (.integer, _), (.boolean, _),
      (.long, _):
      false
    }
  }

  /// Kotlin's `Float.equals`: the same bits, with every NaN one value (`floatToIntBits`).
  private static func sameKotlinFloat(_ lhs: Float, _ rhs: Float) -> Bool {
    lhs.bitPattern == rhs.bitPattern || (lhs.isNaN && rhs.isNaN)
  }
}

/// What every backend provides. A backend answers each write finally: `true` for a value the
/// attached document declares and can hold — handed to the player, or equal to the last value
/// written to that name, which is no change and is not handed over again (the variable keeps
/// whatever the document has made of it since) — and `false` before a document is attached, for
/// a name it does not declare, or for a value its variable cannot hold.
///
/// A clear answers the same way: `true` for a name the attached document declares, its stored
/// value (if any) removed and the variable handed back to the document's own value, `false`
/// before a document is attached or for a name it does not declare.
@MainActor
protocol ControllerBackendProtocol: AnyObject {
  init()
  /// The names the attached document declares; empty before one is attached.
  var names: [String] { get }
  func set(_ value: ControllerNamedValue, for name: String) -> Bool
  func clear(_ name: String) -> Bool
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
      case .long(let value): kotlinController.setLong(name: name, value: value)
      case .floatArray(let values):
        // Counted against the Kotlin controller's own limit before the element-by-element copy
        // below; the Kotlin controller then applies the whole rule (`RcFloatList.canHold`), so
        // the limit has one owner per language and this backend does not depend on the Swift
        // core (the sample app checked by `check-swift-sample.sh` builds without it).
        values.count <= Int(kotlinController.maxFloatArrayValues)
          && kotlinController.setFloatArray(name: name, values: kotlinFloats(values))
      }
    }

    func clear(_ name: String) -> Bool { kotlinController.clear(name: name) }

    /// The Kotlin/Native export gives `KotlinFloatArray` only `init(size:)`, `init(size:init:)`
    /// (which boxes every index and value) and `set(index:value:)` — no `[Float]` bridge — so the
    /// array is filled in place.
    private func kotlinFloats(_ values: [Float]) -> KotlinFloatArray {
      let array = KotlinFloatArray(size: Int32(values.count))
      for (index, value) in values.enumerated() { array.set(index: Int32(index), value: value) }
      return array
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
    /// The declarations of the document a native player is showing, which every write and clear
    /// is checked against; `.none` (every write refused) until `attach` and after `detach`.
    private var attachedDeclarations = NativeSwiftNamedVariableDeclarations.none
    /// The latest value the host wrote to each name, keyed by the name the attached document
    /// declares it under; a clear removes the name. What a new or reopened player is seeded with.
    private var valuesByName: [String: ControllerNamedValue] = [:]
    /// Names the host cleared and has not written since. A session a player reopens while such a
    /// clear is still on its way is seeded with "no value" for each (`seedValues(for:)`), so it
    /// starts the name from the document's value. Re-keyed by `attach` as `valuesByName` is. A
    /// player's host leaves out the names it never held a value of (a new document, or a new
    /// player).
    private var pendingClearedNames: Set<String> = []
    /// One handler per attached player view controller, so a controller shared by multiple views
    /// reaches every one of them rather than only the last attached. Each receives every accepted
    /// write, or nil for a clear.
    private var updateHandlers: [ObjectIdentifier: (String, ControllerNamedValue?) -> Void] = [:]
    /// Default key used by single-handler callers (unit tests).
    private static let defaultHandlerKey = ObjectIdentifier(NativeControllerBackend.self)

    init() {}

    var names: [String] { attachedDeclarations.names }

    func set(_ value: ControllerNamedValue, for name: String) -> Bool {
      guard let declared = attachedDeclarations.declaredName(for: name),
        attachedDeclarations.accepts(value, for: declared)
      else { return false }
      // A write equal to the last one is no change, as the Kotlin player diffs the host's values
      // against the ones it last applied (`applyHostValues`): a document action that changed the
      // variable since keeps its value.
      guard valuesByName[declared] != value else { return true }
      valuesByName[declared] = value
      pendingClearedNames.remove(declared)
      for handler in updateHandlers.values { handler(declared, value) }
      return true
    }

    /// Removes the stored value and hands the player the clear, as removing a key from the Kotlin
    /// player's named values clears it (`clearNamedValue`). A name with no stored value has
    /// nothing of the host's to clear, as on the Kotlin player, and nothing is handed over. The
    /// player's host drops a clear whose write it never delivered (a write and its clear in one
    /// main-actor turn), as the Kotlin player's once-per-pass diff never sees either.
    func clear(_ name: String) -> Bool {
      guard let declared = attachedDeclarations.declaredName(for: name) else { return false }
      guard valuesByName.removeValue(forKey: declared) != nil else { return true }
      pendingClearedNames.insert(declared)
      for handler in updateHandlers.values { handler(declared, nil) }
      return true
    }

    /// Attaches the declarations of the document a native player is showing, as the Kotlin
    /// controller attaches when its player is built. Each stored value is re-keyed under the name
    /// the new document resolves its key to, and dropped when that variable cannot take it; the
    /// rest are re-applied when the player installs its update handler. Re-keying keeps the two
    /// backends in step: checking the old key let a bare name reach a different variable.
    func attach(_ declarations: NativeSwiftNamedVariableDeclarations) {
      attachedDeclarations = declarations
      var kept: [String: ControllerNamedValue] = [:]
      // Sorted so that when two stored names resolve to one declared name, the same one wins on
      // every run (Swift randomises Dictionary order per process).
      for name in valuesByName.keys.sorted() {  // lint-ok: O(n log n) once per attach
        guard let value = valuesByName[name], let declared = declarations.declaredName(for: name),
          declarations.accepts(value, for: declared)
        else { continue }
        kept[declared] = value
      }
      valuesByName = kept
      // Pending clears are re-keyed as the values are, not dropped: another player sharing the
      // controller may attach while a player's clear is still on its way to it. A player that
      // never held the name leaves the clear out of its seed (the host's `nativeHeldNames`).
      pendingClearedNames = Set(
        pendingClearedNames.lazy.compactMap { declarations.declaredName(for: $0) }
          .filter { kept[$0] == nil })  // lint-ok: O(1) dictionary lookup per pending clear
    }

    /// Forgets the attached declarations without a new document to judge the stored values by —
    /// the player is not showing one (a refused theme) or its document could not be read. Writes
    /// are refused until the next `attach`, and the values written so far are kept for it.
    func detach() {
      attachedDeclarations = .none
    }

    /// Detaches `owner`'s update handler, clearing declarations only when no other view remains
    /// attached to this controller.
    func detach(for owner: ObjectIdentifier) {
      updateHandlers[owner] = nil
      if updateHandlers.isEmpty { attachedDeclarations = .none }
    }

    /// Installs a single update handler (used by tests), replaying every value written so far, or
    /// removes it with `nil`. A clear is not replayed: a player the handler is installed for
    /// opened from the document's own values and the controller's.
    func installUpdateHandler(_ handler: ((String, ControllerNamedValue?) -> Void)?) {
      installUpdateHandler(handler, for: Self.defaultHandlerKey)
    }

    /// Installs `owner`'s update handler, replaying every value written so far except those in
    /// `skippingInitial` that resolved to the same declared name and value (already applied on the
    /// player's first open), and a clear of each name in `skippingInitial` no longer stored, or
    /// removes it with `nil`.
    ///
    /// The replay runs in name order. Names are distinct, so the order changes no result; it only
    /// makes the replay the same on every run, which Dictionary order (random per process) is not.
    func installUpdateHandler(
      _ handler: ((String, ControllerNamedValue?) -> Void)?,
      for owner: ObjectIdentifier,
      skippingInitial initial: [String: ControllerNamedValue] = [:]
    ) {
      updateHandlers[owner] = handler
      guard let handler else { return }
      var seeded: [String: ControllerNamedValue] = [:]
      for name in initial.keys.sorted() {  // lint-ok: O(n log n) once per install
        guard let value = initial[name],
          let declared = attachedDeclarations.declaredName(for: name),
          attachedDeclarations.accepts(value, for: declared)
        else { continue }
        seeded[declared] = value
      }
      for name in valuesByName.keys.sorted() {  // lint-ok: O(n log n) once per install
        guard let value = valuesByName[name] else { continue }
        if seeded[name] == value { continue }
        handler(name, value)
      }
      // A value the first open was seeded with that has been cleared since: the player holds it,
      // so it is handed the clear.
      for name in seeded.keys.sorted() where valuesByName[name] == nil {  // lint-ok: once per install
        handler(name, nil)
      }
    }

    /// A snapshot of the values written so far, keyed as currently stored, for comparing against
    /// post-attach values once a new player's declarations finish loading.
    var storedValues: [String: ControllerNamedValue] { valuesByName }

    /// Every value stored so far, sorted by key, for seeding a brand-new player's first open
    /// before its declarations finish loading on the main actor.
    var initialSeedValues: [(name: String, value: RemoteComposeNativeNamedValue)] {
      valuesByName.sorted { $0.key < $1.key }  // lint-ok: O(n log n) once per player open
        .map { (name: $0.key, value: $0.value.nativeValue) }
    }

    /// The values written so far, by the name the player's document declares them under, which
    /// seed a session the native player reopens. The player applies each one unless the replaced
    /// session took that very write: not yet delivered, refused (judged afresh), or written again
    /// since — even back to the value the session took — is applied. A name cleared since is
    /// seeded with no value, so the reopened session starts it from the document's value unless
    /// the replaced session took that clear. Empty while no update handler is installed: the
    /// values are not yet re-keyed for the player's document.
    ///
    /// The Kotlin player builds its state once per document, seeded with the host's values, and
    /// keeps it through the background. The native player reopens its bytes for that, carries the
    /// named values the old session held, and adds these.
    var seedValues: [(name: String, value: RemoteComposeNativeNamedValue?)] {
      guard !updateHandlers.isEmpty else { return [] }
      return reopenSeed
    }

    /// `seedValues` scoped to `owner`, empty while `owner` has no installed update handler.
    func seedValues(for owner: ObjectIdentifier) -> [(
      name: String, value: RemoteComposeNativeNamedValue?
    )] {
      guard updateHandlers[owner] != nil else { return [] }
      return reopenSeed
    }

    /// Every stored value, then every pending cleared name with no value, each part in name order
    /// (for a reproducible seed; the names are distinct, so the order changes no result).
    private var reopenSeed: [(name: String, value: RemoteComposeNativeNamedValue?)] {
      var seed: [(name: String, value: RemoteComposeNativeNamedValue?)] = []
      seed.reserveCapacity(valuesByName.count + pendingClearedNames.count)
      for (name, value) in valuesByName.sorted(by: { $0.key < $1.key }) {  // lint-ok: per reopen
        seed.append((name: name, value: value.nativeValue))
      }
      for name in pendingClearedNames.sorted() {  // lint-ok: O(c log c) once per reopen
        seed.append((name: name, value: nil))
      }
      return seed
    }
  }

  /// The native player, as the controller's values reach it. A protocol so the delivery below can
  /// be tested without a player.
  @MainActor
  protocol NativeNamedValueTarget: AnyObject {
    func submit(_ value: RemoteComposeNativeNamedValue, for name: String) async
      -> RemoteComposeNativeInputAdmission
    func submitClear(for name: String) async -> RemoteComposeNativeInputAdmission
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
      case .long(let value): .long(value)
      case .floatArray(let values): .floatArray(values)
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
    /// is gone or has closed its input availability, the task is cancelled, or `isCurrent` says a
    /// newer value for this name has started, the player was rebuilt, or a reopen took the value in
    /// its seed.
    @MainActor
    func deliver<Target: NativeNamedValueTarget>(
      for name: String, to target: () -> Target?, isCurrent: () -> Bool
    ) async {
      await deliverNamedUpdate(self, for: name, to: target, isCurrent: isCurrent)
    }
  }

  /// Delivers a write — or, for nil, a clear — to the native player, as
  /// ``ControllerNamedValue/deliver(for:to:isCurrent:)`` describes: offered again each time the
  /// player signals until the session it reaches answers finally. Returns that answer, or nil when
  /// it stopped first.
  @MainActor
  @discardableResult
  func deliverNamedUpdate<Target: NativeNamedValueTarget>(
    _ value: ControllerNamedValue?, for name: String, to target: () -> Target?,
    isCurrent: () -> Bool
  ) async -> RemoteComposeNativeInputAdmission? {
    while true {
      let availability: RemoteComposeNativeInputAvailability
      let mark: UInt64
      // The strong reference lives only for the offer, not across the wait below.
      do {
        guard !Task.isCancelled, let target = target(), isCurrent() else { return nil }
        availability = target.inputAvailability
        // A closed availability returns from every wait at once: its player is gone, and offering
        // again would spin.
        guard !availability.isClosed else { return nil }
        mark = availability.mark
        let admission =
          if let value {
            await target.submit(value.nativeValue, for: name)
          } else {
            await target.submitClear(for: name)
          }
        switch admission {
        case .accepted, .refused: return admission
        case .deferred: break
        }
      }
      await availability.wait(after: mark)
    }
  }

  extension NativeSwiftNamedVariableDeclarations {
    func accepts(_ value: ControllerNamedValue, for name: String) -> Bool {
      switch value {
      case .float(let value): acceptsFloat(value, for: name)
      case .text(let value): acceptsString(value, for: name)
      case .color: acceptsColor(for: name)
      case .integer, .boolean: acceptsInteger(for: name)
      case .long: acceptsLong(for: name)
      case .floatArray(let values): acceptsFloatArray(values, for: name)
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

    func clear(_ name: String) -> Bool { false }
  }
#endif
