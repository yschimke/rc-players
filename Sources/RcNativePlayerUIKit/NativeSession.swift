#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore

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
      do {
        return Frame(snapshot: try session.snapshot(timeSeconds: timeSeconds))
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    func click(componentID: Int, at timeSeconds: TimeInterval) throws -> Update {
      guard
        let nativeEvents = try session.click(
          componentID: componentID, timeSeconds: timeSeconds)
      else { return try unchangedUpdate(timeSeconds: timeSeconds) }
      return try eventUpdate(nativeEvents, timeSeconds: timeSeconds)
    }

    func gesture(
      _ kind: NativeSwiftGestureKind, componentID: Int,
      sample: NativeSwiftPointerSample? = nil, at timeSeconds: TimeInterval
    ) throws -> Update {
      guard
        let nativeEvents = try session.gesture(
          kind, componentID: componentID, sample: sample, timeSeconds: timeSeconds)
      else { return try unchangedUpdate(timeSeconds: timeSeconds) }
      return try eventUpdate(nativeEvents, timeSeconds: timeSeconds)
    }

    private func eventUpdate(
      _ nativeEvents: [NativeSwiftEvent], timeSeconds: TimeInterval
    ) throws -> Update {
      let events = nativeEvents.map { event -> RemoteComposeNativePlayerEvent in
        switch event {
        case .namedAction(let name, let value):
          let publicValue: RemoteComposeNativePlayerActionValue
          switch value {
          case .none: publicValue = .none
          case .float(let value): publicValue = .float(value)
          case .integer(let value): publicValue = .integer(value)
          case .text(let value): publicValue = .text(value)
          }
          return .namedAction(name: name, value: publicValue)
        }
      }
      return Update(
        accepted: true,
        frame: Frame(snapshot: try session.snapshot(timeSeconds: timeSeconds)), events: events)
    }

    func setFloat(_ value: Float, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      return try update(
        accepted: session.setFloat(value, for: name), timeSeconds: timeSeconds)
    }

    func setString(_ value: String, for name: String, at timeSeconds: TimeInterval) throws -> Update
    {
      return try update(
        accepted: session.setString(value, for: name), timeSeconds: timeSeconds)
    }

    func setColor(_ argb: UInt32, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      return try update(
        accepted: session.setColor(argb, for: name), timeSeconds: timeSeconds)
    }

    func returnCustomFloat(
      _ value: Float, componentID: Int, propertyID: Int, at timeSeconds: TimeInterval
    ) throws -> Update {
      let accepted = try session.returnCustomFloat(
        value, componentID: componentID, propertyID: propertyID)
      return try update(accepted: accepted, timeSeconds: timeSeconds)
    }

    func returnCustomText(
      _ value: String, componentID: Int, propertyID: Int, at timeSeconds: TimeInterval
    ) throws -> Update {
      let accepted = try session.returnCustomText(
        value, componentID: componentID, propertyID: propertyID)
      return try update(accepted: accepted, timeSeconds: timeSeconds)
    }

    private func unchangedUpdate(timeSeconds: TimeInterval = 0) throws -> Update {
      try update(accepted: false, timeSeconds: timeSeconds)
    }

    private func update(accepted: Bool, timeSeconds: TimeInterval) throws -> Update {
      Update(
        accepted: accepted, frame: Frame(snapshot: try session.snapshot(timeSeconds: timeSeconds)),
        events: [])
    }
  }
#endif
