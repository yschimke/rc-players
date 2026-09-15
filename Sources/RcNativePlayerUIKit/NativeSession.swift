#if canImport(UIKit)
  import Foundation
  import RcComposePlayer

  public enum RemoteComposeNativePlayerActionValue: Equatable, Sendable {
    case none
    case float(Float)
    case integer(Int)
    case text(String)
    case floatList([Float])
  }

  public enum RemoteComposeNativePlayerEvent: Equatable, Sendable {
    case action(id: Int)
    case actionWithMetadata(id: Int, metadata: String)
    case namedAction(name: String, value: RemoteComposeNativePlayerActionValue)
    case debug(message: String, value: Float, flags: Int)
  }

  /// Kotlin/Native objects are confined to this serial handle. Immutable snapshots cross back to
  /// UIKit inside a wrapper whose fields are never mutated after construction.
  actor NativeSnapshotSessionHandle {
    private final class SessionBox: @unchecked Sendable {
      let value: RcNativeSnapshotSession

      init(_ value: RcNativeSnapshotSession) {
        self.value = value
      }
    }

    struct Frame: @unchecked Sendable {
      let snapshot: RcNativeDocumentSnapshot
    }

    private actor Decoder {
      func open(data: Data) throws -> (SessionBox, Frame) {
        try Task.checkCancellation()
        let bytes = RcDataBridgeKt.rcByteArray(data: data)
        do {
          let session = SessionBox(
            try RcNativeSnapshotBridge.shared.createSession(bytes: bytes))
          let frame = try session.value.snapshot(timeSeconds: 0)
          try Task.checkCancellation()
          return (session, Frame(snapshot: frame))
        } catch is CancellationError {
          throw CancellationError()
        } catch {
          throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
        }
      }
    }

    private static let decoder = Decoder()

    struct Update: Sendable {
      let accepted: Bool
      let frame: Frame
      let events: [RemoteComposeNativePlayerEvent]
    }
    private let session: SessionBox

    private init(session: SessionBox) {
      self.session = session
    }

    static func open(
      data: Data, maximumDocumentBytes: Int
    ) async throws -> (NativeSnapshotSessionHandle, Frame) {
      guard data.count <= maximumDocumentBytes, data.count <= Int(Int32.max) else {
        throw RemoteComposeNativeLimitError.documentTooLarge(
          actual: data.count, maximum: min(maximumDocumentBytes, Int(Int32.max)))
      }
      let opened = try await decoder.open(data: data)
      try Task.checkCancellation()
      return (NativeSnapshotSessionHandle(session: opened.0), opened.1)
    }

    func frame(at timeSeconds: TimeInterval) async throws -> Frame {
      do {
        return Frame(snapshot: try session.value.snapshot(timeSeconds: Float(timeSeconds)))
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    func click(componentID: Int, at timeSeconds: TimeInterval) throws -> Update {
      try update {
        try session.value.click(
          componentId: Int32(componentID), timeSeconds: Float(timeSeconds))
      }
    }

    func setFloat(_ value: Float, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      try update {
        try session.value.setFloat(name: name, value: value, timeSeconds: Float(timeSeconds))
      }
    }

    func setString(_ value: String, for name: String, at timeSeconds: TimeInterval) throws -> Update
    {
      try update {
        try session.value.setString(name: name, value: value, timeSeconds: Float(timeSeconds))
      }
    }

    func setColor(_ argb: UInt32, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      try update {
        try session.value.setColor(
          name: name, argb: Int32(bitPattern: argb), timeSeconds: Float(timeSeconds))
      }
    }

    func returnCustomFloat(
      _ value: Float, componentID: Int, propertyID: Int, at timeSeconds: TimeInterval
    ) throws -> Update {
      try update {
        try session.value.returnCustomFloat(
          componentId: Int32(componentID), propertyType: Int32(propertyID), value: value,
          timeSeconds: Float(timeSeconds))
      }
    }

    func returnCustomText(
      _ value: String, componentID: Int, propertyID: Int, at timeSeconds: TimeInterval
    ) throws -> Update {
      try update {
        try session.value.returnCustomText(
          componentId: Int32(componentID), propertyType: Int32(propertyID), value: value,
          timeSeconds: Float(timeSeconds))
      }
    }

    private func update(_ operation: () throws -> RcNativeSessionUpdate) throws -> Update {
      do {
        let result = try operation()
        return Update(
          accepted: result.accepted,
          frame: Frame(snapshot: result.snapshot),
          events: result.events.compactMap(Self.event))
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    private static func event(_ event: RcNativeEvent) -> RemoteComposeNativePlayerEvent? {
      switch Int(event.kind) {
      case 0: return .action(id: Int(event.actionId))
      case 1:
        return .actionWithMetadata(
          id: Int(event.actionId), metadata: event.textValue ?? "")
      case 2: return .namedAction(name: event.name ?? "", value: .none)
      case 3:
        return .namedAction(name: event.name ?? "", value: .float(event.floatValue))
      case 4:
        return .namedAction(
          name: event.name ?? "", value: .integer(Int(event.integerValue)))
      case 5:
        return .namedAction(
          name: event.name ?? "", value: .text(event.textValue ?? ""))
      case 6:
        return .namedAction(
          name: event.name ?? "",
          value: .floatList(event.floatListValue.map { $0.floatValue }))
      case 7:
        return .debug(
          message: event.textValue ?? "", value: event.floatValue,
          flags: Int(event.actionId))
      default: return nil
      }
    }
  }
#endif
