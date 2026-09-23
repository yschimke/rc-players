import Foundation
import Testing

@testable import RcNativePlayerCore
@testable import RcNativePlayerUIKit

@Suite struct NativeFrameTimingTests {
  @Test func frameTiming() {
    let staticSchedule = NativeFrameSchedule.idle
    #expect(
      staticSchedule.driverMode(isActive: true, isVisible: true, reduceMotion: false) == .idle)

    let continuous = NativeFrameSchedule(
      needsContinuousFrames: true, requestsNextFrame: false, wakeAfter: nil)
    #expect(
      continuous.driverMode(isActive: true, isVisible: true, reduceMotion: false) == .displayLink)
    #expect(
      continuous.driverMode(isActive: true, isVisible: true, reduceMotion: true) == .idle)
    #expect(
      continuous.driverMode(isActive: false, isVisible: true, reduceMotion: false) == .idle)

    let oneFrame = NativeFrameSchedule(
      needsContinuousFrames: false, requestsNextFrame: true, wakeAfter: 10)
    #expect(
      oneFrame.driverMode(isActive: true, isVisible: true, reduceMotion: true) == .displayLink)

    let delayed = NativeFrameSchedule(
      needsContinuousFrames: false, requestsNextFrame: false, wakeAfter: 0.25)
    #expect(
      delayed.driverMode(isActive: true, isVisible: true, reduceMotion: false)
        == .wake(after: 0.25))

    var wake = NativeWakeCountdown()
    wake.reset(after: 10)
    #expect(wake.start(after: 10, at: 20) == 10)
    wake.pause(at: 29)
    #expect(wake.remaining == 1)
    #expect(wake.start(after: 10, at: 100) == 1)
    wake.pause(at: 100.25)
    #expect(wake.remaining == 0.75)
    wake.complete()
    #expect(wake.remaining == nil)

    var timeline = NativeAnimationTimeline()
    timeline.reset(at: 10, active: true)
    #expect(timeline.sample(at: 10.25) == 0.25)
    timeline.pause(at: 11)
    #expect(timeline.elapsed == 1)
    timeline.resume(at: 100)
    #expect(timeline.sample(at: 101) == 2)
    #expect(timeline.sample(at: 100.5) == 2, "a backward clock must not reverse animation")
    #expect(timeline.advance(to: 10, at: 101) == 10)
    #expect(timeline.sample(at: 102) == 11)
    #expect(timeline.advance(to: 5, at: 102) == 11)
    #expect(timeline.sample(at: 103) == 12, "an older explicit frame must not rewind time")

    timeline.pause(at: 104)
    #expect(timeline.sampleFunctional(at: 104.25) == 13.25)
    #expect(timeline.sampleFunctional(at: 104.75) == 13.75)
    #expect(
      timeline.sampleFunctional(at: 104.5) == 13.75,
      "a backward clock must not reverse paused functional time")
    timeline.pause(at: 200)
    #expect(
      timeline.sampleFunctional(at: 201) == 14.75,
      "resynchronizing paused time must exclude suspension")
  }
}
