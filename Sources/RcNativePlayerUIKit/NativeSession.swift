#if canImport(Foundation)
  import Foundation
  #if canImport(RcNativePlayerCore)
    import RcNativePlayerCore
  #endif

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

  /// The name the frame model and the sample harnesses know the engine's frame type by.
  typealias NativeSnapshotSessionHandle = NativePlayerEngine

  /// Serial owner of one open document: the retained pure-Swift session, the time of the frame the
  /// host last presented, and the application of host input against that time.
  ///
  /// Every method is synchronous inside the actor, so no two operations on the session interleave.
  /// Ordering between operations is the caller's: the UIKit player feeds frames and input to one
  /// engine from a single serial pump, and never has more than one call outstanding.
  actor NativePlayerEngine {
    /// One unit of host input, applied at the engine's presented frame time.
    enum Input: Sendable {
      case setFloat(Float, name: String)
      case setString(String, name: String)
      case setColor(UInt32, name: String)
      case setInteger(Int, name: String)
      case gesture(NativeSwiftGestureKind, componentID: Int, sample: NativeSwiftPointerSample?)
      case customFloat(Float, componentID: Int, propertyID: Int)
      case customText(String, componentID: Int, propertyID: Int)
    }

    struct Frame: Sendable {
      let snapshot: NativeSwiftDocumentSnapshot
      /// The host's absolute time at the instant this frame was resolved, so a refinement of the
      /// frame resolves its calendar fields against the same instant rather than a later one.
      let wallClock: NativeSwiftWallClock?

      /// A detached session for re-resolving this frame once the host knows its real geometry.
      ///
      /// Carried per frame rather than held once because its state is frozen when taken, and a
      /// document's state moves — a gesture, a set value, the clock. Nil when the document binds no
      /// component geometry, which is almost all of them, so the copy is not paid for unnecessarily.
      let refiner: NativeSwiftDocumentSession?

      init(
        session: NativeSwiftDocumentSession, snapshot: NativeSwiftDocumentSnapshot,
        wallClock: NativeSwiftWallClock?
      ) {
        self.snapshot = snapshot
        self.wallClock = wallClock
        refiner = snapshot.boundComponents.isEmpty ? nil : session.detachedCopy()
      }
    }

    struct Update: Sendable {
      let accepted: Bool
      let frame: Frame
      let events: [RemoteComposeNativePlayerEvent]
      /// The document time `frame` was resolved at.
      let timeSeconds: TimeInterval
    }

    private let session: NativeSwiftDocumentSession
    /// The host's wall clock, as of the last frame. Input updates re-resolve the document, and a
    /// geometry that reads a calendar field has to resolve against the same instant a frame did.
    private var wallClock: NativeSwiftWallClock?
    /// The time of the frame the host last presented. Input resolves against it, so the frame an
    /// input produces continues the one on screen rather than one the host resolved but dropped.
    private(set) var frameTime: TimeInterval = 0

    private init(session: NativeSwiftDocumentSession, wallClock: NativeSwiftWallClock?) {
      self.session = session
      self.wallClock = wallClock
    }

    /// - Parameters:
    ///   - hostDensity: what a deferred-density capture resolves against. Supplied at open rather
    ///     than set afterwards because the first frame is produced here, and a document that reads
    ///     `ID_DENSITY` would otherwise resolve its very first geometry against the wrong value and
    ///     only correct itself on the next frame.
    ///   - wallClock: the host's absolute time, for the first frame. See `NativeSwiftWallClock`.
    static func open(
      data: Data, maximumDocumentBytes: Int, hostDensity: Float = 1, hostFontScale: Float = 1,
      wallClock: NativeSwiftWallClock? = nil
    ) async throws -> (NativePlayerEngine, Frame) {
      guard data.count <= maximumDocumentBytes, data.count <= Int(Int32.max) else {
        throw RemoteComposeNativeLimitError.documentTooLarge(
          actual: data.count, maximum: min(maximumDocumentBytes, Int(Int32.max)))
      }
      do {
        try Task.checkCancellation()
        let session = try NativeSwiftDocumentSession.open(data: data)
        session.setHostDensity(hostDensity, fontScale: hostFontScale)
        let frame = Frame(
          session: session,
          snapshot: try session.snapshot(wallClock: wallClock), wallClock: wallClock)
        try Task.checkCancellation()
        return (
          NativePlayerEngine(session: session, wallClock: wallClock), frame
        )
      } catch is CancellationError {
        throw CancellationError()
      } catch let error as NativeSwiftCoreError {
        throw RemoteComposeNativePlayerError.decode(error.description)
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    /// Forwards the host's density to the retained document; see `setHostDensity` on the core.
    func setHostDensity(_ density: Float, fontScale: Float) {
      session.setHostDensity(density, fontScale: fontScale)
    }

    func frame(at timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock?) async throws
      -> Frame
    {
      do {
        self.wallClock = wallClock
        return Frame(
          session: session,
          snapshot: try session.snapshot(timeSeconds: timeSeconds, wallClock: wallClock),
          wallClock: wallClock)
      } catch {
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
    }

    /// Record that the host presented the frame it resolved at `timeSeconds`.
    func present(frameTime timeSeconds: TimeInterval) {
      frameTime = timeSeconds
    }

    /// Apply one host input at the presented frame time and resolve the resulting frame.
    func apply(_ input: Input) throws -> Update {
      let time = frameTime
      switch input {
      case .setFloat(let value, let name):
        return try setFloat(value, for: name, at: time)
      case .setString(let value, let name):
        return try setString(value, for: name, at: time)
      case .setColor(let argb, let name):
        return try setColor(argb, for: name, at: time)
      case .setInteger(let value, let name):
        return try setInteger(value, for: name, at: time)
      case .gesture(let kind, let componentID, let sample):
        return try gesture(kind, componentID: componentID, sample: sample, at: time)
      case .customFloat(let value, let componentID, let propertyID):
        return try returnCustomFloat(
          value, componentID: componentID, propertyID: propertyID, at: time)
      case .customText(let value, let componentID, let propertyID):
        return try returnCustomText(
          value, componentID: componentID, propertyID: propertyID, at: time)
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
        frame: Frame(
          session: session,
          snapshot: try session.snapshot(timeSeconds: timeSeconds, wallClock: wallClock),
          wallClock: wallClock),
        events: events,
        timeSeconds: timeSeconds)
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

    func setInteger(_ value: Int, for name: String, at timeSeconds: TimeInterval) throws -> Update {
      return try update(
        accepted: session.setInteger(value, for: name), timeSeconds: timeSeconds)
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
        accepted: accepted,
        frame: Frame(
          session: session,
          snapshot: try session.snapshot(timeSeconds: timeSeconds, wallClock: wallClock),
          wallClock: wallClock),
        events: [],
        timeSeconds: timeSeconds)
    }
  }

  /// A first-in, first-out queue that refuses elements beyond a fixed capacity.
  ///
  /// The UIKit player holds pending host input here instead of chaining a retained `Task` per
  /// input, so a burst of gestures or `set*` calls costs one slot each and cannot grow unbounded.
  struct NativeBoundedQueue<Element> {
    let capacity: Int
    private var storage: [Element] = []
    private var head = 0

    init(capacity: Int) {
      precondition(capacity > 0, "a bounded queue needs room for at least one element")
      self.capacity = capacity
    }

    var count: Int { storage.count - head }
    var isEmpty: Bool { count == 0 }

    /// Appends `element`, or returns false and leaves the queue unchanged when it is full.
    @discardableResult
    mutating func append(_ element: Element) -> Bool {
      guard count < capacity else { return false }
      storage.append(element)
      return true
    }

    mutating func popFirst() -> Element? {
      guard head < storage.count else { return nil }
      let element = storage[head]
      head += 1
      if head == storage.count {
        storage.removeAll(keepingCapacity: true)
        head = 0
      } else if head >= capacity {
        storage.removeFirst(head)
        head = 0
      }
      return element
    }

    /// Empties the queue, returning what it held in arrival order.
    mutating func removeAll() -> [Element] {
      let remaining = Array(storage[head...])
      storage.removeAll()
      head = 0
      return remaining
    }
  }
#endif
