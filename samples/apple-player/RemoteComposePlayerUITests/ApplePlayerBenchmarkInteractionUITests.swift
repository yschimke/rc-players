import XCTest

@MainActor
final class ApplePlayerBenchmarkInteractionUITests: XCTestCase {
  func testBothPlayersAcceptAUserScrollGesture() throws {
    for renderer in ["cmp", "nativeUIKit"] {
      let app = XCUIApplication()
      app.launchArguments = ["--apple-player-benchmark-interaction=\(renderer)"]
      app.launch()

      let scroll = app.scrollViews["apple-benchmark-scroll-\(renderer)"]
      XCTAssertTrue(scroll.waitForExistence(timeout: 10), app.debugDescription)
      let offset = app.staticTexts["apple-benchmark-scroll-offset-\(renderer)"]
      XCTAssertEqual(offset.label, "0")
      scroll.swipeUp()
      let changed = expectation(for: NSPredicate(format: "label != %@", "0"), evaluatedWith: offset)
      wait(for: [changed], timeout: 5)
      app.terminate()
    }
  }
}
