import Foundation

/// Injectable monotonic time source used by the native player's animation timeline.
public protocol RemoteComposeNativePlayerClock: Sendable {
  func now() -> TimeInterval
}

public struct RemoteComposeNativeSystemClock: RemoteComposeNativePlayerClock, Sendable {
  public init() {}

  public func now() -> TimeInterval { ProcessInfo.processInfo.systemUptime }
}

enum NativeFrameDriverMode: Equatable, Sendable {
  case idle
  case displayLink
  case wake(after: TimeInterval)
}

struct NativeFrameSchedule: Equatable, Sendable {
  var needsContinuousFrames: Bool
  var requestsNextFrame: Bool
  var wakeAfter: TimeInterval?

  static let idle = NativeFrameSchedule(
    needsContinuousFrames: false, requestsNextFrame: false, wakeAfter: nil)

  func driverMode(isActive: Bool, isVisible: Bool, reduceMotion: Bool) -> NativeFrameDriverMode {
    guard isActive, isVisible else { return .idle }
    if requestsNextFrame { return .displayLink }
    if needsContinuousFrames, !reduceMotion { return .displayLink }
    if let wakeAfter { return wakeAfter <= 0 ? .displayLink : .wake(after: wakeAfter) }
    return .idle
  }
}

/// Accumulates only active monotonic time, so suspension never causes a resumed animation to jump.
struct NativeAnimationTimeline: Equatable, Sendable {
  private(set) var elapsed: TimeInterval = 0
  private var lastActiveTime: TimeInterval?

  mutating func reset(at now: TimeInterval, active: Bool) {
    elapsed = 0
    lastActiveTime = active ? now : nil
  }

  mutating func sample(at now: TimeInterval) -> TimeInterval {
    if let lastActiveTime {
      elapsed += max(now - lastActiveTime, 0)
      self.lastActiveTime = max(now, lastActiveTime)
    } else {
      self.lastActiveTime = now
    }
    return elapsed
  }

  mutating func pause(at now: TimeInterval) {
    guard lastActiveTime != nil else { return }
    _ = sample(at: now)
    lastActiveTime = nil
  }

  mutating func resume(at now: TimeInterval) {
    guard lastActiveTime == nil else { return }
    lastActiveTime = now
  }
}
