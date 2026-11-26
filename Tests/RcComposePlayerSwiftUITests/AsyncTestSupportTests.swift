import Foundation
import RcNativePlayerTestDocuments
import Testing

/// `waitUntil` fails a wait that makes no progress, but not one whose main actor was blocked by
/// other work (CI on #979: UIKit's first use blocked the main thread for 65 s). A scripted clock
/// stands in for time, so no test sleeps.
@MainActor
@Suite struct AsyncTestSupportTests {
  /// A clock that moves by the next step on each read, and by the last step once they run out, so
  /// a wait that never ends still sees time pass.
  private final class ScriptedClock {
    private var time: TimeInterval = 0
    private var steps: [TimeInterval]
    private var lastStep: TimeInterval = 0
    init(steps: [TimeInterval]) { self.steps = steps }
    func read() -> TimeInterval {
      if !steps.isEmpty { lastStep = steps.removeFirst() }
      time += lastStep
      return time
    }
  }

  /// A stall far longer than the budget, followed by the condition holding, passes.
  @Test func aStallOfTheMainActorDoesNotUseUpTheBudget() async throws {
    let clock = ScriptedClock(steps: [0, 120, 120, 0.001])
    var turns = 0
    try await waitUntil("three turns", within: 30, now: clock.read) {
      turns += 1
      return turns == 4
    }
  }

  /// Stalls on every turn still end at the hard cap: ten times the budget of real time.
  @Test func stallsOnEveryTurnEndAtTheHardCap() async {
    let clock = ScriptedClock(steps: [0, 60])
    var turns = 0
    await withKnownIssue("the wait reaches the hard cap") {
      try await waitUntil("never", within: 30, now: clock.read) {
        turns += 1
        return false
      }
    }
    // Each 60 s stall counts 1 s toward the budget, but 300 s of real time end the wait.
    #expect(turns == 5)
  }

  /// Steady turns with no progress fail once they add up to the budget.
  @Test func aWaitWithNoProgressFailsAtItsBudget() async {
    let clock = ScriptedClock(steps: [0] + Array(repeating: 0.5, count: 100))
    var turns = 0
    await withKnownIssue("the wait times out") {
      try await waitUntil("never", within: 10, now: clock.read) {
        turns += 1
        return false
      }
    }
    // 10 s of 0.5 s turns: the 20th turn reaches the budget.
    #expect(turns == 20)
  }
}
