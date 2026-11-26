import Foundation
import RcNativePlayerTestDocuments
import Testing

@testable import RcComposePlayerSwiftUI
@_spi(RemoteComposeController) @testable import RcNativePlayerUIKit

/// `deliverNamedUpdate` loops until the player answers finally, and the loop must never spin: a
/// deferred update (a write or a clear; every test runs both) is offered once per signal, plus once
/// for a signal that arrived during the offer, and the loop ends on a final answer, cancellation,
/// a gone player, a closed availability or a delivery that is no longer current. Each test counts
/// the offers exactly.
///
/// The tests assert state, not timing. A delivery is parked when its availability has one waiter:
/// it is then suspended on a continuation that only `signal()`, `signalOne()`, `close()` or
/// cancellation resumes, so while it stays parked its offer count cannot move. No test sleeps or
/// counts turns; each waits for a state under a budget. The target refuses after `cap` offers,
/// and a wait for a parked delivery fails as soon as it has offered more than expected, so a loop
/// that spins fails at once instead of hanging. `finish` fails at once when the delivery parks
/// where it should return, and cancels a delivery its test leaves behind. Every wait is bounded by
/// `waitUntil`, whose budget skips time the main actor was blocked by other suites; a wall-clock
/// `.timeLimit` is not used, because it counted such a stall (65 s of UIKit start-up on a cold
/// simulator, #979 CI) against this suite. This follows Apple's "Testing asynchronous code" (Swift
/// Testing) and Point-Free's "Reliably testing async code": wait for observable state, not for
/// time. Point-Free's `withMainSerialExecutor` would make the scheduling itself serial, but it
/// needs a new dependency (swift-concurrency-extras) in an upstream package, and the waiter count
/// already gives a deterministic state to wait on.
@MainActor
@Suite(.serialized) struct NativeDeliveryLoopTests {
  /// Every test runs for a write and for a clear (nil).
  nonisolated private static let updates: [ControllerNamedValue?] = [.integer(5), nil]

  /// Waits until the delivery is parked after exactly `offers` offers; fails at once when it has
  /// offered more or has already returned, so a broken loop fails instead of timing out.
  private static func parked(
    _ slot: TargetSlot, after offers: Int, sourceLocation: SourceLocation = #_sourceLocation
  ) async throws {
    let target = try #require(slot.target, sourceLocation: sourceLocation)
    try await waitUntil(
      "parked after \(offers) offers, at \(target.offers)", sourceLocation: sourceLocation
    ) {
      target.availability.waiterCount == 1 || target.offers > offers || slot.isFinished
    }
    try #require(
      target.offers <= offers, "offered \(target.offers) times, expected \(offers)",
      sourceLocation: sourceLocation)
    try #require(
      !slot.isFinished, "the delivery returned instead of parking",
      sourceLocation: sourceLocation)
    try #require(target.availability.waiterCount == 1, sourceLocation: sourceLocation)
    try #require(target.offers == offers, sourceLocation: sourceLocation)
  }

  /// The delivery's answer, once it has returned. Fails at once when it parks instead (or when the
  /// test is cancelled), and then cancels the delivery so it is not left behind.
  private static func finish(
    _ delivery: Task<RemoteComposeNativeInputAdmission?, Never>, of slot: TargetSlot,
    sourceLocation: SourceLocation = #_sourceLocation
  ) async throws -> RemoteComposeNativeInputAdmission? {
    defer { if !slot.isFinished { delivery.cancel() } }
    try await waitUntil("the delivery returns", sourceLocation: sourceLocation) {
      slot.isFinished || slot.availability.waiterCount == 1
    }
    try #require(
      slot.isFinished, "the delivery parked instead of returning", sourceLocation: sourceLocation)
    return await delivery.value
  }

  /// Starts a delivery of `value` to `target` and returns its answer, as `finish` does.
  private static func finish(
    _ value: ControllerNamedValue?, to target: ScriptedTarget,
    sourceLocation: SourceLocation = #_sourceLocation
  ) async throws -> RemoteComposeNativeInputAdmission? {
    let slot = TargetSlot(target)
    return try await finish(deliver(value, to: slot), of: slot, sourceLocation: sourceLocation)
  }

  private static func deliver(
    _ value: ControllerNamedValue?, to slot: TargetSlot,
    isCurrent: @escaping () -> Bool = { true }
  ) -> Task<RemoteComposeNativeInputAdmission?, Never> {
    slot.target?.expected = value?.nativeValue
    return Task { @MainActor in
      defer { slot.isFinished = true }
      return await deliverNamedUpdate(
        value, for: "USER:count", to: { slot.target }, isCurrent: isCurrent)
    }
  }

  /// 1. Without a signal a deferred update is offered exactly once and the delivery parks; waking
  /// it with `signalOne()` (room for one more input) gives exactly one more offer and it parks
  /// again.
  @Test(arguments: updates)
  func aDeferredUpdateIsOfferedOnceWithoutASignal(_ value: ControllerNamedValue?) async throws {
    let target = ScriptedTarget(answers: { _ in .deferred })
    let slot = TargetSlot(target)
    let delivery = Self.deliver(value, to: slot)
    defer { delivery.cancel() }
    try await Self.parked(slot, after: 1)

    target.availability.signalOne()
    try await Self.parked(slot, after: 2)

    delivery.cancel()
    #expect(try await Self.finish(delivery, of: slot) == nil)
    #expect(target.offers == 2)
    #expect(target.availability.waiterCount == 0)
  }

  /// 2. N signals give exactly N + 1 offers; the loop returns on `.accepted`.
  @Test(arguments: updates)
  func eachSignalGivesExactlyOneMoreOffer(_ value: ControllerNamedValue?) async throws {
    let signals = 5
    let target = ScriptedTarget(answers: { $0 <= signals ? .deferred : .accepted })
    let slot = TargetSlot(target)
    let delivery = Self.deliver(value, to: slot)
    defer { delivery.cancel() }
    for offered in 1...signals {
      try await Self.parked(slot, after: offered)
      target.availability.signal()
    }
    #expect(try await Self.finish(delivery, of: slot) == .accepted)
    #expect(target.offers == signals + 1)
    #expect(target.availability.waiterCount == 0)
  }

  /// 3. A signal sent while the offer is in flight — after the loop read its mark, before it waits
  /// — is not lost: the update is offered again at once, and then the loop waits.
  @Test(arguments: updates)
  func aSignalDuringTheOfferGivesOneImmediateReOffer(_ value: ControllerNamedValue?) async throws {
    let target = ScriptedTarget(answers: { _ in .deferred })
    let availability = target.availability
    target.onOffer = { if $0 == 1 { availability.signal() } }
    let slot = TargetSlot(target)
    let delivery = Self.deliver(value, to: slot)
    defer { delivery.cancel() }
    // One re-offer for the in-flight signal, then parked: no more.
    try await Self.parked(slot, after: 2)
    delivery.cancel()
    #expect(try await Self.finish(delivery, of: slot) == nil)
    #expect(target.offers == 2)
  }

  /// 4. A cancelled delivery returns nil with no further offer: before it starts, while it waits,
  /// and while its offer is in flight.
  @Test(arguments: updates)
  func aCancelledDeliveryReturnsNilWithoutAnotherOffer(_ value: ControllerNamedValue?) async throws
  {
    let early = ScriptedTarget(answers: { _ in .deferred })
    // The task is cancelled before it runs its first job.
    let earlySlot = TargetSlot(early)
    let beforeStart = Self.deliver(value, to: earlySlot)
    beforeStart.cancel()
    #expect(try await Self.finish(beforeStart, of: earlySlot) == nil)
    #expect(early.offers == 0)

    let waiting = ScriptedTarget(answers: { _ in .deferred })
    let slot = TargetSlot(waiting)
    let delivery = Self.deliver(value, to: slot)
    defer { delivery.cancel() }
    try await Self.parked(slot, after: 1)
    delivery.cancel()
    #expect(try await Self.finish(delivery, of: slot) == nil)
    #expect(waiting.offers == 1)
    #expect(waiting.availability.waiterCount == 0)

    let inFlight = ScriptedTarget(answers: { _ in .deferred })
    // The offer runs in the delivery's task, so cancelling the current task cancels the delivery.
    inFlight.onOffer = { _ in withUnsafeCurrentTask { $0?.cancel() } }
    #expect(try await Self.finish(value, to: inFlight) == nil)
    #expect(inFlight.offers == 1)
    #expect(inFlight.availability.waiterCount == 0)
  }

  /// 5a. Once the player is gone (`target()` is nil), the waiting delivery wakes on the next signal
  /// and returns nil after its one offer. It is woken by a signal, not `close()`, so the gone
  /// target is what ends the loop; 5b covers the closed availability.
  @Test(arguments: updates)
  func aDeliveryEndsOnceThePlayerIsGone(_ value: ControllerNamedValue?) async throws {
    let target = ScriptedTarget(answers: { _ in .deferred })
    let availability = target.availability
    let slot = TargetSlot(target)
    let delivery = Self.deliver(value, to: slot)
    defer { delivery.cancel() }
    try await Self.parked(slot, after: 1)
    slot.target = nil
    availability.signal()
    #expect(try await Self.finish(delivery, of: slot) == nil)
    #expect(target.offers == 1)
  }

  /// 5b. A closed availability means its player is gone. Should a target still hand it out and
  /// defer, the delivery returns rather than offering again in a hot loop: a closed availability
  /// returns from every wait at once. Closed while the delivery waits, it ends after its one offer;
  /// closed before it starts, it never offers.
  @Test(arguments: updates)
  func aClosedAvailabilityEndsTheDeliveryEvenWhileTheTargetDefers(_ value: ControllerNamedValue?)
    async throws
  {
    let target = ScriptedTarget(answers: { _ in .deferred })
    let slot = TargetSlot(target)
    let delivery = Self.deliver(value, to: slot)
    defer { delivery.cancel() }
    try await Self.parked(slot, after: 1)
    target.availability.close()
    #expect(try await Self.finish(delivery, of: slot) == nil)
    #expect(target.offers == 1, "no offer after close")

    let closed = ScriptedTarget(answers: { _ in .deferred })
    closed.availability.close()
    #expect(try await Self.finish(value, to: closed) == nil)
    #expect(closed.offers == 0)

    // Closed while the offer is in flight. The real player cannot do this (the delivery holds the
    // target, which owns the availability), so this case is defensive: no wait is left behind.
    let inFlight = ScriptedTarget(answers: { _ in .deferred })
    let inFlightAvailability = inFlight.availability
    inFlight.onOffer = { _ in inFlightAvailability.close() }
    #expect(try await Self.finish(value, to: inFlight) == nil)
    #expect(inFlight.offers == 1)
    #expect(inFlightAvailability.waiterCount == 0)
  }

  /// 5c. A delivery that is no longer current (a newer value for the name started, or the player
  /// was rebuilt) returns nil on its next wake without offering again.
  @Test(arguments: updates)
  func aDeliveryThatIsNoLongerCurrentStopsOffering(_ value: ControllerNamedValue?) async throws {
    let target = ScriptedTarget(answers: { _ in .deferred })
    let slot = TargetSlot(target)
    let delivery = Self.deliver(value, to: slot, isCurrent: { target.offers == 0 })
    defer { delivery.cancel() }
    try await Self.parked(slot, after: 1)
    target.availability.signal()
    #expect(try await Self.finish(delivery, of: slot) == nil)
    #expect(target.offers == 1)
  }

  /// 6. A refusal returns at once: one offer, and no wait.
  @Test(arguments: updates)
  func aRefusalReturnsAtOnce(_ value: ControllerNamedValue?) async throws {
    let target = ScriptedTarget(answers: { _ in .refused })
    #expect(try await Self.finish(value, to: target) == .refused)
    #expect(target.offers == 1)
    #expect(target.availability.waiterCount == 0)
  }
}

/// What the delivery's `target()` returns: the player, or nil once it is gone.
@MainActor
private final class TargetSlot {
  /// The player the delivery offers to; nil once it is gone.
  var target: ScriptedTarget?
  /// The availability of the player the slot started with; kept once `target` is nil.
  let availability: RemoteComposeNativeInputAvailability
  /// Whether the delivery task has returned.
  var isFinished = false
  init(_ target: ScriptedTarget) {
    self.target = target
    availability = target.availability
  }
}

/// A native player that counts every offer (write or clear) and answers each by its 1-based
/// number. After `cap` offers it refuses, so a spinning loop ends with a count the tests reject.
/// An offer of the wrong kind (a write for a clear, another value, or the reverse) is an issue.
@MainActor
private final class ScriptedTarget: NativeNamedValueTarget {
  /// Offers after which the target refuses, ending a loop that spins.
  static let cap = 50
  /// Offers so far, writes and clears together.
  private(set) var offers = 0
  /// The offer each call should carry: the write's value, or nil for a clear.
  var expected: RemoteComposeNativeNamedValue?
  /// Runs inside each offer with its 1-based number, before it is answered.
  var onOffer: (Int) -> Void = { _ in }
  /// The availability the delivery waits on.
  let availability = RemoteComposeNativeInputAvailability()
  /// The answer to each offer, by its 1-based number.
  private let answers: (Int) -> RemoteComposeNativeInputAdmission

  init(answers: @escaping (Int) -> RemoteComposeNativeInputAdmission) {
    self.answers = answers
  }

  var inputAvailability: RemoteComposeNativeInputAvailability { availability }

  func submit(_ value: RemoteComposeNativeNamedValue, for name: String) async
    -> RemoteComposeNativeInputAdmission
  {
    offer(value)
  }

  func submitClear(for name: String) async -> RemoteComposeNativeInputAdmission {
    offer(nil)
  }

  private func offer(_ value: RemoteComposeNativeNamedValue?) -> RemoteComposeNativeInputAdmission {
    if value != expected {
      Issue.record("offered \(String(describing: value)), not \(String(describing: expected))")
    }
    offers += 1
    onOffer(offers)
    return offers > Self.cap ? .refused : answers(offers)
  }
}
