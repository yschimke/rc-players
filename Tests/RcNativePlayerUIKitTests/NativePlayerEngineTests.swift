import Foundation
import Testing

@testable import RcNativePlayerCore
@testable import RcNativePlayerUIKit

@Suite struct NativePlayerEngineTests {
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

    let initial = try await engine.apply(.setFloat(1, name: unknown))
    #expect(!initial.accepted)
    #expect(initial.events.isEmpty)
    #expect(initial.timeSeconds == 0)

    // Resolving a frame the host never presented does not move input time.
    _ = try await engine.frame(at: 3, wallClock: nil)
    let unpresented = try await engine.apply(.setInteger(1, name: unknown))
    #expect(unpresented.timeSeconds == 0)

    await engine.present(frameTime: 2.5)
    let presented = try await engine.apply(.setString("x", name: unknown))
    #expect(presented.timeSeconds == 2.5)
    let frameTime = await engine.frameTime
    #expect(frameTime == 2.5)
  }
}
