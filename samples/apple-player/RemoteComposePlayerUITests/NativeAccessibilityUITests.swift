import XCTest

@MainActor
final class NativeAccessibilityUITests: XCTestCase {
  func testTitleCardExposesNativeAssistiveTechnologyTargets() throws {
    let app = XCUIApplication()
    app.launchArguments = ["--native-accessibility-ui-test"]
    app.launch()

    let nativeButton = app.buttons.matching(
      NSPredicate(format: "identifier BEGINSWITH %@", "rc-native-button-")
    ).firstMatch
    XCTAssertTrue(nativeButton.waitForExistence(timeout: 10), app.debugDescription)
    XCTAssertEqual(nativeButton.label, "Morning run, 5.2 km · 28 min")
    XCTAssertTrue(nativeButton.isEnabled)
    XCTAssertTrue(nativeButton.isHittable)

    let labels = app.staticTexts.matching(
      NSPredicate(format: "identifier BEGINSWITH %@", "rc-native-text-")
    )
    XCTAssertEqual(labels.count, 0, "The conceptual button must own its descendant text labels")

    try app.performAccessibilityAudit(
      for: [.elementDetection, .hitRegion, .sufficientElementDescription, .trait])
  }
}
