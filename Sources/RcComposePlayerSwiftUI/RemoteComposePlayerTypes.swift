import Foundation
import RcComposePlayer

public enum RemoteComposePlayerTheme: Equatable, Sendable {
  case system
  case light
  case dark

  var kotlinValue: RcPlayerTheme {
    switch self {
    case .system: .system
    case .light: .light
    case .dark: .dark
    }
  }
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

#if canImport(UIKit)
  @MainActor
  public final class RemoteComposePlayerController {
    let kotlinController = RcComposePlayerController()

    public init() {}

    public var names: [String] { kotlinController.names }

    @discardableResult
    public func setFloat(_ value: Float, for name: String) -> Bool {
      kotlinController.setFloat(name: name, value: value)
    }

    @discardableResult
    public func setString(_ value: String, for name: String) -> Bool {
      kotlinController.setString(name: name, value: value)
    }

    @discardableResult
    public func setColor(_ argb: UInt32, for name: String) -> Bool {
      kotlinController.setColor(name: name, argb: Int32(bitPattern: argb))
    }
  }
#endif

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
