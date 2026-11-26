import Foundation
#if canImport(RcComposePlayer)
  import RcComposePlayer
#endif

public enum RemoteComposePlayerTheme: Equatable, Sendable {
  case system
  case light
  case dark

  #if canImport(RcComposePlayer)
    var kotlinValue: RcPlayerTheme {
    switch self {
    case .system: .system
    case .light: .light
    case .dark: .dark
    }
    }
  #endif
}

public enum RemoteComposePlayerCompatibility: Equatable, Sendable {
  case strict
  case compatible

  var isLenient: Bool { self == .compatible }
}

public enum RemoteComposePlayerBackground: Equatable, Sendable {
  case opaque
  case transparent

  var isOpaque: Bool { self == .opaque }
}

public struct RemoteComposePlayerConfiguration: Equatable, Sendable {
  public var theme: RemoteComposePlayerTheme
  public var compatibility: RemoteComposePlayerCompatibility
  public var background: RemoteComposePlayerBackground

  public init(
    theme: RemoteComposePlayerTheme = .system,
    compatibility: RemoteComposePlayerCompatibility = .strict,
    background: RemoteComposePlayerBackground = .opaque
  ) {
    self.theme = theme
    self.compatibility = compatibility
    self.background = background
  }
}

public enum RemoteComposePlayerActionValue: Equatable, Sendable {
  case none
  case float(Float)
  case integer(Int)
  case text(String)
  case floatList([Float])
  case unsupported(String)
}

public enum RemoteComposePlayerEvent: Equatable, Sendable {
  case action(id: Int)
  case actionWithMetadata(id: Int, metadata: String)
  case namedAction(name: String, value: RemoteComposePlayerActionValue)
  case haptic(type: Int)
  case playSound(id: Int)
  case debug(message: String, value: Float, flags: Int)
  case unsupported(String)

  /// A URL carried by a metadata action or a text-valued named action.
  public var url: URL? {
    switch self {
    case .actionWithMetadata(_, let metadata): remoteComposeActionURL(metadata)
    case .namedAction(_, .text(let value)): remoteComposeActionURL(value)
    default: nil
    }
  }
}

private func remoteComposeActionURL(_ value: String) -> URL? {
  guard let url = URL(string: value), url.scheme != nil else { return nil }
  return url
}

/// Discovers and updates the host-driven named variables of the document a player shows. The same
/// calls drive whichever player the build links; see `ControllerBackend`.
@MainActor
public final class RemoteComposePlayerController {
  let backend = ControllerBackend()

  public init() {}

  #if canImport(UIKit) && canImport(RcComposePlayer)
  /// Enable only for an authenticated host. Defaults preserve read-only behavior.
  public func configureInputs(editable: Bool,
    onChange: @escaping (String, String) -> Void = { _, _ in },
    onSubmit: @escaping (String, [String: String]) -> Void = { _, _ in }) {
      backend.kotlinController.configureInputs(
        editable: editable, onChange: onChange, onSubmit: onSubmit)
  }
  #endif

  /// The names the document currently attached to the player declares; empty before one is.
  public var names: [String] { backend.names }

  @discardableResult
  public func setFloat(_ value: Float, for name: String) -> Bool {
    backend.set(.float(value), for: name)
  }

  @discardableResult
  public func setString(_ value: String, for name: String) -> Bool {
    backend.set(.text(value), for: name)
  }

  @discardableResult
  public func setColor(_ argb: UInt32, for name: String) -> Bool {
    backend.set(.color(argb), for: name)
  }

  /// Sets an integer variable. Values outside the 32-bit range a Remote Compose integer holds are
  /// refused.
  @discardableResult
  public func setInteger(_ value: Int, for name: String) -> Bool {
    guard let value = Int32(exactly: value) else { return false }
    return backend.set(.integer(value), for: name)
  }

  /// Sets a boolean variable: an integer variable holding 1 or 0, as AndroidX stores a boolean.
  @discardableResult
  public func setBoolean(_ value: Bool, for name: String) -> Bool {
    backend.set(.boolean(value), for: name)
  }

  /// Sets a long variable: an instant in epoch milliseconds, as a document's time attributes read
  /// one.
  @discardableResult
  public func setLong(_ value: Int64, for name: String) -> Bool {
    backend.set(.long(value), for: name)
  }

  /// Replaces the list a float-array variable holds.
  /// Every value must be finite, and there may be no more of them than a float list holds
  /// (AndroidX `DataListFloat.MAX_FLOAT_ARRAY`, 2000); other arrays are refused.
  @discardableResult
  public func setFloatArray(_ values: [Float], for name: String) -> Bool {
    backend.set(.floatArray(values), for: name)
  }

  /// Stops overriding `name`: the value set for it is removed and the variable shows the value
  /// the document recorded again (a named system variable, the player's clock). Returns `false`
  /// before a document is attached or when it does not declare `name`. Clearing a name with no
  /// value set — or one whose write was cleared in the same main-actor turn, before the player
  /// saw it — changes nothing, so a value the document changed itself stands. A later write of the
  /// same value as before the clear is applied.
  @discardableResult
  public func clear(_ name: String) -> Bool {
    backend.clear(name)
  }
}

public enum RemoteComposePlayerError: Error, Equatable, Sendable {
  case missingHighRefreshRatePlistEntry
  case documentTooLarge(byteCount: Int)
  case playback(String)
}

extension RemoteComposePlayerError: LocalizedError {
  public var errorDescription: String? {
    switch self {
    case .missingHighRefreshRatePlistEntry:
      "Add CADisableMinimumFrameDurationOnPhone = YES to the application Info.plist before creating the player."
    case .documentTooLarge(let byteCount):
      "The Remote Compose document is too large to bridge to the player (\(byteCount) bytes)."
    case .playback(let message):
      message
    }
  }
}

#if canImport(RcComposePlayer)
  func kotlinBytes(from data: Data) throws -> KotlinByteArray {
  guard data.count <= Int(Int32.max) else {
    throw RemoteComposePlayerError.documentTooLarge(byteCount: data.count)
  }

  return RcDataBridgeKt.rcByteArray(data: data)
}

func swiftEvent(from event: RcPlayerEvent) -> RemoteComposePlayerEvent {
  if let action = event as? RcPlayerEventHostAction {
    return .action(id: Int(action.actionId))
  }
  if let action = event as? RcPlayerEventHostActionMetadata {
    return .actionWithMetadata(id: Int(action.actionId), metadata: action.metadata)
  }
  if let action = event as? RcPlayerEventHostNamedAction {
    return .namedAction(name: action.name, value: swiftActionValue(from: action.value))
  }
  if let debug = event as? RcPlayerEventDebugMessage {
    return .debug(message: debug.message, value: debug.value, flags: Int(debug.flags))
  }
  return .unsupported(String(describing: event))
}

  private func swiftActionValue(from value: RcHostActionValue) -> RemoteComposePlayerActionValue {
  switch value {
  case is RcHostActionValueNone:
    return .none
  case let value as RcHostActionValueFloatValue:
    return .float(value.value)
  case let value as RcHostActionValueIntegerValue:
    return .integer(Int(value.value))
  case let value as RcHostActionValueTextValue:
    return .text(value.value)
  case let value as RcHostActionValueFloatListValue:
    return .floatList(value.value.map { $0.floatValue })
  default:
    return .unsupported(String(describing: value))
  }
  }
#endif
