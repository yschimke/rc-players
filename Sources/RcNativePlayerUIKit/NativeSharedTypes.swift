import Foundation

/// The host surface behind an opaque or transparent player. It has no UIKit dependency so AppKit
/// hosts and shared adapters can use the same public configuration vocabulary.
public enum RemoteComposeNativePlayerBackground: Equatable, Sendable {
  case opaque
  case transparent
}

/// A value a host sets on a document's named variable before it renders: the four kinds a `.rc`
/// document declares a `USER:` name with. A boolean travels as `.integer` 0 or 1, as on the wire.
public enum RemoteComposeNativeNamedValue: Equatable, Sendable {
  case text(String)
  case float(Float)
  case integer(Int)
  /// ARGB, as the document stores a color.
  case color(UInt32)
}

/// How a native player answered a named-variable write.
@_spi(RemoteComposeController)
public enum RemoteComposeNativeInputAdmission: Equatable, Sendable {
  /// The document applied it.
  case accepted
  /// This session's final answer: the document is failed and has no session, the document
  /// declines the name or value (nothing is reported), or applying it failed (reported through
  /// `onError`). Offering it again to the same session gets the same answer; a session the
  /// player reopens — seeded through `namedValueSeed` — judges it afresh.
  case refused
  /// The player could not take it yet — its document is still opening, its scene is in the
  /// background, its input queue is full, or the session it reached was discarded for a reopen. It
  /// may be accepted once ``RemoteComposeNativeInputAvailability`` signals.
  case deferred
}

/// Tells a caller holding a deferred write when to offer it again: the player signals each time it
/// may take input it could not before — its scene became active, a session opened or failed, or its
/// queue emptied — wakes the longest waiter when its queue gives up one input, and opens for good
/// when it goes away, so no waiter outlives it.
///
/// Thread-safe, and holds no reference to the player, so a waiter does not keep it alive.
@_spi(RemoteComposeController)
public final class RemoteComposeNativeInputAvailability: @unchecked Sendable {
  private let lock = NSLock()
  private var epoch: UInt64 = 0
  private var isClosed = false
  private var nextWaiter: UInt64 = 0
  private var waiters: [UInt64: CheckedContinuation<Void, Never>] = [:]
  /// Waiter ids in arrival order, from `orderHead` on; an id no longer in `waiters` (cancelled) is
  /// skipped when reached.
  private var order: [UInt64] = []
  private var orderHead = 0

  public init() {}

  /// The point to wait from. Read it before offering a write, so a signal that arrives while the
  /// write is being answered is not missed.
  public var mark: UInt64 { locked { epoch } }

  /// How many callers are waiting now. Tests use it to know a waiter has started waiting.
  var waiterCount: Int { locked { waiters.count } }

  /// Returns once the player has signalled since `mark`, has gone away, or the task is cancelled.
  public func wait(after mark: UInt64) async {
    let id = locked { () -> UInt64 in
      nextWaiter &+= 1
      return nextWaiter
    }
    await withTaskCancellationHandler {
      await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
        let resumeNow = locked { () -> Bool in
          if epoch != mark || isClosed || Task.isCancelled { return true }
          waiters[id] = continuation
          order.append(id)
          return false
        }
        if resumeNow { continuation.resume() }
      }
    } onCancel: {
      locked { waiters.removeValue(forKey: id) }?.resume()
    }
  }

  /// Releases every waiter: input may be accepted now that was not before.
  func signal() {
    for waiter in locked({ () -> [CheckedContinuation<Void, Never>] in
      epoch &+= 1
      defer {
        waiters.removeAll()
        order.removeAll()
        orderHead = 0
      }
      return Array(waiters.values)
    }) {
      waiter.resume()
    }
  }

  /// Releases the longest-waiting waiter: there is room for one more input. A caller that read
  /// its mark before this and has not started waiting yet returns at once, as after `signal()`.
  func signalOne() {
    locked { () -> CheckedContinuation<Void, Never>? in
      epoch &+= 1
      var next: CheckedContinuation<Void, Never>?
      while next == nil, orderHead < order.count {
        next = waiters.removeValue(forKey: order[orderHead])
        orderHead += 1
      }
      if orderHead == order.count {
        order.removeAll(keepingCapacity: true)
        orderHead = 0
      } else if orderHead > order.count / 2 {
        order.removeFirst(orderHead)
        orderHead = 0
      }
      return next
    }?.resume()
  }

  /// Releases every waiter, present and future: the player is gone.
  func close() {
    locked { isClosed = true }
    signal()
  }

  private func locked<T>(_ body: () -> T) -> T {
    lock.lock()
    defer { lock.unlock() }
    return body()
  }
}
