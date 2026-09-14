#if canImport(UIKit)
  import Foundation
  import RcComposePlayer

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

    private let session: SessionBox

    private init(session: SessionBox) {
      self.session = session
    }

    static func open(data: Data) async throws -> (NativeSnapshotSessionHandle, Frame) {
      guard data.count <= Int(Int32.max) else {
        throw RemoteComposeNativePlayerError.documentTooLarge(data.count)
      }
      let opened = try await Task.detached(priority: .userInitiated) {
        let bytes = RcDataBridgeKt.rcByteArray(data: data)
        do {
          let session = SessionBox(
            try RcNativeSnapshotBridge.shared.createSession(bytes: bytes))
          let frame = try session.value.snapshot(timeSeconds: 0)
          return (session, Frame(snapshot: frame))
        } catch {
          throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
        }
      }.value
      return (NativeSnapshotSessionHandle(session: opened.0), opened.1)
    }

    func frame(at timeSeconds: TimeInterval) async throws -> Frame {
      do {
        return Frame(snapshot: try session.value.snapshot(timeSeconds: Float(timeSeconds)))
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }
  }
#endif
