#if canImport(UIKit)
  import Foundation

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

  /// Serial ownership boundary for the pure-Swift decoder and retained document state.
  actor NativeSnapshotSessionHandle {
    struct Frame: Sendable {
      let snapshot: NativeSwiftDocumentSnapshot
    }

    struct Update: Sendable {
      let accepted: Bool
      let frame: Frame
      let events: [RemoteComposeNativePlayerEvent]
    }

    private let session: NativeSwiftDocumentSession

    private init(session: NativeSwiftDocumentSession) {
      self.session = session
    }

    static func open(
      data: Data, maximumDocumentBytes: Int
    ) async throws -> (NativeSnapshotSessionHandle, Frame) {
      guard data.count <= maximumDocumentBytes, data.count <= Int(Int32.max) else {
        throw RemoteComposeNativeLimitError.documentTooLarge(
          actual: data.count, maximum: min(maximumDocumentBytes, Int(Int32.max)))
      }
      do {
        try Task.checkCancellation()
        let session = try NativeSwiftDocumentSession.open(data: data)
        let frame = Frame(snapshot: try session.snapshot())
        try Task.checkCancellation()
        return (NativeSnapshotSessionHandle(session: session), frame)
      } catch is CancellationError {
        throw CancellationError()
      } catch let error as NativeSwiftCoreError {
        throw RemoteComposeNativePlayerError.decode(error.description)
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    func frame(at timeSeconds: TimeInterval) async throws -> Frame {
      _ = timeSeconds
      do {
        return Frame(snapshot: try session.snapshot())
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    func click(componentID: Int, at timeSeconds: TimeInterval) throws -> Update {
      _ = componentID
      _ = timeSeconds
      return try unchangedUpdate()
    }

    func setFloat(_ value: Float, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      _ = value
      _ = name
      _ = timeSeconds
      return try unchangedUpdate()
    }

    func setString(_ value: String, for name: String, at timeSeconds: TimeInterval) throws -> Update
    {
      _ = value
      _ = name
      _ = timeSeconds
      return try unchangedUpdate()
    }

    func setColor(_ argb: UInt32, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      _ = argb
      _ = name
      _ = timeSeconds
      return try unchangedUpdate()
    }

    func returnCustomFloat(
      _ value: Float, componentID: Int, propertyID: Int, at timeSeconds: TimeInterval
    ) throws -> Update {
      _ = timeSeconds
      let accepted = try session.returnCustomFloat(
        value, componentID: componentID, propertyID: propertyID)
      return try update(accepted: accepted)
    }

    func returnCustomText(
      _ value: String, componentID: Int, propertyID: Int, at timeSeconds: TimeInterval
    ) throws -> Update {
      _ = timeSeconds
      let accepted = try session.returnCustomText(
        value, componentID: componentID, propertyID: propertyID)
      return try update(accepted: accepted)
    }

    private func unchangedUpdate() throws -> Update {
      try update(accepted: false)
    }

    private func update(accepted: Bool) throws -> Update {
      Update(accepted: accepted, frame: Frame(snapshot: try session.snapshot()), events: [])
    }
  }
#endif
