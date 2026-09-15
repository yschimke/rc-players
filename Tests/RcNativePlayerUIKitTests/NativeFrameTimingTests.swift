import Foundation

@main
enum NativeFrameTimingTests {
  static func main() {
    let staticSchedule = NativeFrameSchedule.idle
    precondition(
      staticSchedule.driverMode(isActive: true, isVisible: true, reduceMotion: false) == .idle)

    let continuous = NativeFrameSchedule(
      needsContinuousFrames: true, requestsNextFrame: false, wakeAfter: nil)
    precondition(
      continuous.driverMode(isActive: true, isVisible: true, reduceMotion: false) == .displayLink)
    precondition(
      continuous.driverMode(isActive: true, isVisible: true, reduceMotion: true) == .idle)
    precondition(
      continuous.driverMode(isActive: false, isVisible: true, reduceMotion: false) == .idle)

    let oneFrame = NativeFrameSchedule(
      needsContinuousFrames: false, requestsNextFrame: true, wakeAfter: 10)
    precondition(
      oneFrame.driverMode(isActive: true, isVisible: true, reduceMotion: true) == .displayLink)

    let delayed = NativeFrameSchedule(
      needsContinuousFrames: false, requestsNextFrame: false, wakeAfter: 0.25)
    precondition(
      delayed.driverMode(isActive: true, isVisible: true, reduceMotion: false)
        == .wake(after: 0.25))

    var wake = NativeWakeCountdown()
    wake.reset(after: 10)
    precondition(wake.start(after: 10, at: 20) == 10)
    wake.pause(at: 29)
    precondition(wake.remaining == 1)
    precondition(wake.start(after: 10, at: 100) == 1)
    wake.pause(at: 100.25)
    precondition(wake.remaining == 0.75)
    wake.complete()
    precondition(wake.remaining == nil)

    var timeline = NativeAnimationTimeline()
    timeline.reset(at: 10, active: true)
    precondition(timeline.sample(at: 10.25) == 0.25)
    timeline.pause(at: 11)
    precondition(timeline.elapsed == 1)
    timeline.resume(at: 100)
    precondition(timeline.sample(at: 101) == 2)
    precondition(timeline.sample(at: 100.5) == 2, "a backward clock must not reverse animation")
    precondition(timeline.advance(to: 10, at: 101) == 10)
    precondition(timeline.sample(at: 102) == 11)
    precondition(timeline.advance(to: 5, at: 102) == 11)
    precondition(timeline.sample(at: 103) == 12, "an older explicit frame must not rewind time")

    timeline.pause(at: 104)
    precondition(timeline.sampleFunctional(at: 104.25) == 13.25)
    precondition(timeline.sampleFunctional(at: 104.75) == 13.75)
    precondition(
      timeline.sampleFunctional(at: 104.5) == 13.75,
      "a backward clock must not reverse paused functional time")
    timeline.pause(at: 200)
    precondition(
      timeline.sampleFunctional(at: 201) == 14.75,
      "resynchronizing paused time must exclude suspension")

    print("native UIKit frame timing tests: ok")
  }
}
