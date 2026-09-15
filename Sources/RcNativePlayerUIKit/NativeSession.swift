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

  /// Runtime objects are confined to this serial handle. New operation families execute in the
  /// Swift session; the Kotlin session remains a migration fallback for documents containing an
  /// operation family the Swift decoder does not understand yet.
  actor NativeSnapshotSessionHandle {
    private final class SessionBox: @unchecked Sendable {
      let value: RcNativeSnapshotSession

      init(_ value: RcNativeSnapshotSession) {
        self.value = value
      }
    }

    struct Frame: @unchecked Sendable {
      enum Payload {
        case swift(NativeSwiftDocumentSnapshot)
        case kotlin(RcNativeDocumentSnapshot)
      }

      let payload: Payload
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
          return (session, Frame(payload: .kotlin(frame)))
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
    private enum Backend {
      case swift(NativeSwiftDocumentSession)
      case kotlin(SessionBox)
    }

    private let backend: Backend

    private init(backend: Backend) {
      self.backend = backend
    }

    static func open(
      data: Data, maximumDocumentBytes: Int
    ) async throws -> (NativeSnapshotSessionHandle, Frame) {
      guard data.count <= maximumDocumentBytes, data.count <= Int(Int32.max) else {
        throw RemoteComposeNativeLimitError.documentTooLarge(
          actual: data.count, maximum: min(maximumDocumentBytes, Int(Int32.max)))
      }
      do {
        let session = try NativeSwiftDocumentSession.open(data: data)
        let frame = Frame(payload: .swift(try session.snapshot()))
        try Task.checkCancellation()
        return (
          NativeSnapshotSessionHandle(backend: .swift(session)),
          frame
        )
      } catch let error as NativeSwiftCoreError where error.isUnsupported {
        let opened = try await decoder.open(data: data)
        try Task.checkCancellation()
        return (NativeSnapshotSessionHandle(backend: .kotlin(opened.0)), opened.1)
      } catch let error as NativeSwiftCoreError {
        throw RemoteComposeNativePlayerError.decode(error.description)
      }
    }

    func frame(at timeSeconds: TimeInterval) async throws -> Frame {
      do {
        switch backend {
        case .swift(let session): return Frame(payload: .swift(try session.snapshot()))
        case .kotlin(let session):
          return Frame(
            payload: .kotlin(try session.value.snapshot(timeSeconds: Float(timeSeconds))))
        }
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    func click(componentID: Int, at timeSeconds: TimeInterval) throws -> Update {
      switch backend {
      case .swift(let session): return try unchangedSwiftUpdate(session: session)
      case .kotlin(let session):
        return try update {
          try session.value.click(
            componentId: Int32(componentID), timeSeconds: Float(timeSeconds))
        }
      }
    }

    func setFloat(_ value: Float, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      switch backend {
      case .swift(let session): return try unchangedSwiftUpdate(session: session)
      case .kotlin(let session):
        return try update {
          try session.value.setFloat(name: name, value: value, timeSeconds: Float(timeSeconds))
        }
      }
    }

    func setString(_ value: String, for name: String, at timeSeconds: TimeInterval) throws -> Update
    {
      switch backend {
      case .swift(let session): return try unchangedSwiftUpdate(session: session)
      case .kotlin(let session):
        return try update {
          try session.value.setString(name: name, value: value, timeSeconds: Float(timeSeconds))
        }
      }
    }

    func setColor(_ argb: UInt32, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      switch backend {
      case .swift(let session): return try unchangedSwiftUpdate(session: session)
      case .kotlin(let session):
        return try update {
          try session.value.setColor(
            name: name, argb: Int32(bitPattern: argb), timeSeconds: Float(timeSeconds))
        }
      }
    }

    func returnCustomFloat(
      _ value: Float, componentID: Int, propertyID: Int, at timeSeconds: TimeInterval
    ) throws -> Update {
      switch backend {
      case .swift(let session):
        let accepted = try session.returnCustomFloat(
          value, componentID: componentID, propertyID: propertyID)
        return Update(
          accepted: accepted, frame: Frame(payload: .swift(try session.snapshot())), events: [])
      case .kotlin(let session):
        return try update {
          try session.value.returnCustomFloat(
            componentId: Int32(componentID), propertyType: Int32(propertyID), value: value,
            timeSeconds: Float(timeSeconds))
        }
      }
    }

    func returnCustomText(
      _ value: String, componentID: Int, propertyID: Int, at timeSeconds: TimeInterval
    ) throws -> Update {
      switch backend {
      case .swift(let session):
        let accepted = try session.returnCustomText(
          value, componentID: componentID, propertyID: propertyID)
        return Update(
          accepted: accepted, frame: Frame(payload: .swift(try session.snapshot())), events: [])
      case .kotlin(let session):
        return try update {
          try session.value.returnCustomText(
            componentId: Int32(componentID), propertyType: Int32(propertyID), value: value,
            timeSeconds: Float(timeSeconds))
        }
      }
    }

    private func update(_ operation: () throws -> RcNativeSessionUpdate) throws -> Update {
      do {
        let result = try operation()
        return Update(
          accepted: result.accepted,
          frame: Frame(payload: .kotlin(result.snapshot)),
          events: result.events.compactMap(Self.event))
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    private func unchangedSwiftUpdate(session: NativeSwiftDocumentSession) throws -> Update {
      Update(
        accepted: false, frame: Frame(payload: .swift(try session.snapshot())), events: [])
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
