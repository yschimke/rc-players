import Foundation
import RcNativePlayerTestDocuments
import Testing

@_spi(RemoteComposeController) @testable import RcNativePlayerCore
@_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

@Suite struct NativePlayerEngineTests {
  /// What a reopen carries: each named value a write set, also one written back to the
  /// document's initial value (so a value an earlier reopen left out is known to be replaced),
  /// and nothing no write reached.
  @Test func writtenNamedValuesAreWhatAWriteSet() async throws {
    let (label, _) = try await NativePlayerEngine.open(
      data: NativeTestDocuments.label(), maximumDocumentBytes: 1 << 20)
    #expect(await label.writtenNamedValues().isEmpty)
    _ = try await label.apply(.setNamed("label", value: .text("Ready")))
    _ = try await label.apply(.setNamed("title", value: .text("Title")))
    let written = await label.writtenNamedValues()
    #expect(written.map(\.name) == ["label", "title"])
    #expect(written.map(\.value) == [.text("Ready"), .text("Title")])

    let (shown, _) = try await NativePlayerEngine.open(
      data: NativeTestDocuments.shownBox(), maximumDocumentBytes: 1 << 20)
    #expect(await shown.writtenNamedValues().isEmpty)
    _ = try await shown.apply(.setNamed("shown", value: .integer(NativeSwiftVisibility.visible)))
    _ = try await shown.apply(.setNamed("shown", value: .integer(NativeSwiftVisibility.gone)))
    #expect(
      await shown.writtenNamedValues().map(\.value) == [.integer(NativeSwiftVisibility.gone)])
  }

  /// `signalOne` releases the longest waiter only, skipping one that was cancelled; a later
  /// `signal` releases the rest.
  @MainActor @Test func inputAvailabilityWakesOneWaiterInArrivalOrder() async throws {
    let availability = RemoteComposeNativeInputAvailability()
    let released = NativeTestLog()
    func waiter(_ name: String) -> Task<Void, Never> {
      let mark = availability.mark
      return Task {
        await availability.wait(after: mark)
        released.append(name)
      }
    }
    /// Returns once `count` callers are waiting, so each waiter starts after the one before; fails
    /// the test rather than hanging (`waitUntil`).
    func waitUntilWaiting(_ count: Int) async throws {
      try await waitUntil("\(count) waiters") { availability.waiterCount >= count }
      #expect(availability.waiterCount == count)
    }
    let first = waiter("first")
    try await waitUntilWaiting(1)
    let cancelled = waiter("cancelled")
    try await waitUntilWaiting(2)
    let third = waiter("third")
    try await waitUntilWaiting(3)
    let fourth = waiter("fourth")
    try await waitUntilWaiting(4)
    #expect(released.entries.isEmpty)

    cancelled.cancel()
    await cancelled.value
    #expect(released.entries == ["cancelled"])
    #expect(availability.waiterCount == 3)

    availability.signalOne()
    await first.value
    #expect(released.entries == ["cancelled", "first"])
    #expect(availability.waiterCount == 2)

    availability.signalOne()
    await third.value
    #expect(released.entries == ["cancelled", "first", "third"])
    #expect(availability.waiterCount == 1)

    availability.signal()
    await fourth.value
    #expect(released.entries == ["cancelled", "first", "third", "fourth"])
  }

  @Test func boundedQueueIsFirstInFirstOutAndRefusesOverflow() {
    var queue = NativeBoundedQueue<Int>(capacity: 3)
    #expect(queue.isEmpty)
    let first = queue.append(1)
    let second = queue.append(2)
    let third = queue.append(3)
    #expect(first && second && third)
    let overflow = queue.append(4)
    #expect(!overflow)
    #expect(queue.count == 3)

    let head = queue.popFirst()
    #expect(head == 1)
    let refilled = queue.append(4)
    #expect(refilled)
    let next = queue.popFirst()
    #expect(next == 2)

    let remaining = queue.removeAll()
    #expect(remaining == [3, 4])
    #expect(queue.isEmpty)
    let empty = queue.popFirst()
    #expect(empty == nil)
  }

  @Test func boundedQueueKeepsArrivalOrderWhileCompacting() {
    var queue = NativeBoundedQueue<Int>(capacity: 4)
    var produced = 0
    var consumed = 0
    for _ in 0..<50 {
      while queue.append(produced) { produced += 1 }
      #expect(queue.count == 4)
      for _ in 0..<3 {
        let element = queue.popFirst()
        #expect(element == consumed)
        consumed += 1
      }
    }
    let drained = queue.removeAll()
    #expect(drained == Array(consumed..<produced))
  }

  @Test func inputResolvesAtThePresentedFrameTime() async throws {
    let data = try NativeTestFixtures.data("TitleCardRemote-640x480.rc")
    let (engine, _) = try await NativePlayerEngine.open(
      data: data, maximumDocumentBytes: data.count)
    let unknown = "rc.native.engine-test.no-such-variable"

    let initial = try await engine.apply(.setNamed(unknown, value: .float(1)))
    #expect(!initial.accepted)
    #expect(initial.events.isEmpty)
    #expect(initial.timeSeconds == 0)

    // Resolving a frame the host never presented does not move input time.
    _ = try await engine.frame(at: 3, wallClock: nil)
    let unpresented = try await engine.apply(.setNamed(unknown, value: .integer(1)))
    #expect(unpresented.timeSeconds == 0)

    await engine.present(frameTime: 2.5)
    let presented = try await engine.apply(.setNamed(unknown, value: .text("x")))
    #expect(presented.timeSeconds == 2.5)
    let frameTime = await engine.frameTime
    #expect(frameTime == 2.5)
  }
}

/// A thread-safe record of what concurrent tasks did, in order.
private final class NativeTestLog: @unchecked Sendable {
  private let lock = NSLock()
  private var storage: [String] = []

  func append(_ entry: String) {
    lock.lock()
    storage.append(entry)
    lock.unlock()
  }

  var entries: [String] {
    lock.lock()
    defer { lock.unlock() }
    return storage
  }
}
