import XCTest

@MainActor
final class NativeAccessibilityUITests: XCTestCase {
  func testSwiftControlsWriteTextBackToTheDocument() throws {
    let app = XCUIApplication()
    app.launchArguments = ["--native-player", "--fixture=Swift controls"]
    app.launch()

    let name = app.textFields["swift-demo-name"]
    XCTAssertTrue(name.waitForExistence(timeout: 10), app.debugDescription)
    name.tap()
    name.typeText(" Lovelace")

    XCTAssertTrue(
      app.staticTexts["Ada Lovelace"].waitForExistence(timeout: 5),
      "The ordinary document text node did not receive the SwiftUI field edit")

    let level = app.sliders["swift-demo-level"]
    XCTAssertTrue(level.exists)
    let levelValue = app.staticTexts["swift-demo-level-value"]
    XCTAssertTrue(levelValue.exists)
    let originalValue = levelValue.label
    level.adjust(toNormalizedSliderPosition: 0.25)
    let valueChanged = expectation(
      for: NSPredicate(format: "label != %@", originalValue), evaluatedWith: levelValue)
    wait(for: [valueChanged], timeout: 5)
  }

  func testSwiftChartWritesItsSelectionBackToTheDocument() throws {
    let app = XCUIApplication()
    app.launchArguments = ["--native-player", "--fixture=Swift chart"]
    app.launch()

    let chart = app.otherElements.matching(identifier: "swift-demo-chart").firstMatch
    XCTAssertTrue(chart.waitForExistence(timeout: 10), app.debugDescription)
    chart.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.5)).press(
      forDuration: 0.1,
      thenDragTo: chart.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5)))

    let returnedSelection = app.staticTexts.matching(
      NSPredicate(format: "label CONTAINS %@ AND label ENDSWITH %@", " · ", " momentum")
    ).firstMatch
    XCTAssertTrue(
      returnedSelection.waitForExistence(timeout: 5),
      "The ordinary document text node did not receive the Swift Charts selection")
  }

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
