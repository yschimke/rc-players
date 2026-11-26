import Foundation
import Testing

/// The one wait every test suite of the package uses (the UIKit and the SwiftUI overlay suites both
/// depend on this target).
///
/// Returns once `condition` holds, yielding the main actor to the tasks it waits on; fails the test
/// once it has waited `seconds` without the condition holding, and stops when the test is
/// cancelled. Tests wait on observable state this way (a waiter count, a recorded call), never on a
/// number of turns or a sleep. A timeout is reported at the caller's `sourceLocation`.
///
/// The budget counts only time this wait could run. Main-actor tests share one thread, and another
/// suite can block it: on a freshly booted simulator, UIKit's first `UIScreen` use waited 65 s on
/// SpringBoard (yschimke/rc-players#979 CI). A gap between two turns longer than `maxCountedGap`
/// is that kind of stall, not this wait's, so it counts as `maxCountedGap`. A hard cap of
/// `hardCapFactor` times `seconds` of real time still ends any wait, so a regression that blocks
/// every turn fails instead of stretching the budget without end. The clock is the monotonic
/// uptime, so a wall-clock change cannot end it early either; `now` is injectable so tests can pin
/// the budget without sleeping.
@MainActor
public func waitUntil(
  _ what: @autoclosure () -> String, within seconds: TimeInterval = 30,
  now: () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
  sourceLocation: SourceLocation = #_sourceLocation, _ condition: () -> Bool
) async throws {
  var waited: TimeInterval = 0
  var last = now()
  let hardDeadline = last + seconds * hardCapFactor
  while !condition() {
    try Task.checkCancellation()
    let current = now()
    waited += min(current - last, maxCountedGap)
    last = current
    try #require(
      waited < seconds, "timed out waiting until \(what())", sourceLocation: sourceLocation)
    try #require(
      current < hardDeadline, "still waiting until \(what()) after the hard cap",
      sourceLocation: sourceLocation)
    await Task.yield()
  }
}

/// The most one gap between two turns of `waitUntil` counts toward its budget. A longer gap means
/// the main actor was blocked by other work; far above a turn on a loaded runner (milliseconds).
public let maxCountedGap: TimeInterval = 1

/// How many times its budget of real time any `waitUntil` may take, stalls included.
public let hardCapFactor: TimeInterval = 10
