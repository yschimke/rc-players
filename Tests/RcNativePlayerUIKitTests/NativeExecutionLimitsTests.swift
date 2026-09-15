import Foundation

@main
enum NativeExecutionLimitsTests {
  static func main() throws {
    try NativeFrameBudget.validate(.default)
    expect(.invalidLimits) {
      try NativeFrameBudget.validate(
        RemoteComposeNativeExecutionLimits(maximumNodeCount: 0))
    }

    var nodes = NativeFrameBudget()
    let oneNode = RemoteComposeNativeExecutionLimits(maximumNodeCount: 1)
    try nodes.recordNode(depth: 1, limits: oneNode)
    expect(.tooManyNodes(actual: 2, maximum: 1)) {
      try nodes.recordNode(depth: 1, limits: oneNode)
    }

    var nesting = NativeFrameBudget()
    expect(.nestingTooDeep(actual: 3, maximum: 2)) {
      try nesting.recordNode(
        depth: 3, limits: RemoteComposeNativeExecutionLimits(maximumNestingDepth: 2))
    }

    var commands = NativeFrameBudget()
    let commandLimit = RemoteComposeNativeExecutionLimits(
      maximumDrawCommandCount: 1, maximumPathElementCount: 1)
    try commands.recordCommand(pathElementCount: 1, strings: ["ok"], limits: commandLimit)
    expect(.tooManyDrawCommands(actual: 2, maximum: 1)) {
      try commands.recordCommand(pathElementCount: 0, limits: commandLimit)
    }

    var paths = NativeFrameBudget()
    expect(.tooManyPathElements(actual: 2, maximum: 1)) {
      try paths.recordCommand(
        pathElementCount: 2,
        limits: RemoteComposeNativeExecutionLimits(maximumPathElementCount: 1))
    }

    var text = NativeFrameBudget()
    expect(.textTooLong(actual: 4, maximum: 3)) {
      try text.recordCommand(
        pathElementCount: 0, strings: ["éé"],
        limits: RemoteComposeNativeExecutionLimits(maximumTextBytes: 3))
    }
    var repeatedText = NativeFrameBudget()
    try repeatedText.recordStrings(
      ["abc"], limits: RemoteComposeNativeExecutionLimits(maximumTextBytes: 5))
    expect(.textTooLong(actual: 6, maximum: 5)) {
      try repeatedText.recordStrings(
        ["abc"], limits: RemoteComposeNativeExecutionLimits(maximumTextBytes: 5))
    }
    var aggregateText = NativeFrameBudget()
    expect(.frameWorkExceeded(actual: 5, maximum: 4)) {
      try aggregateText.recordCommand(
        pathElementCount: 0, strings: ["text"],
        limits: RemoteComposeNativeExecutionLimits(maximumFrameWork: 4))
    }

    let numbers = NativeFrameBudget()
    expect(.nonFiniteValue(componentID: 42, field: "path")) {
      try numbers.validateNumbers(
        [.nan], componentID: 42, field: "path", limits: .default)
    }
    expect(
      .coordinateTooLarge(
        componentID: 7, field: "draw", actual: 11, maximum: 10)
    ) {
      try numbers.validateNumbers(
        [11], componentID: 7, field: "draw",
        limits: RemoteComposeNativeExecutionLimits(maximumCoordinateMagnitude: 10))
    }
    try numbers.validateFinite(
      [400], componentID: 7, field: "paint")
    expect(.canvasTooLarge(actual: 101, maximum: 100)) {
      try numbers.validateCanvasDimensions(
        [101], limits: RemoteComposeNativeExecutionLimits(maximumCanvasDimension: 100))
    }
    expect(.invalidCanvasDimension(actual: 0)) {
      try numbers.validateDocumentDimensions([0, 100], limits: .default)
    }

    var work = NativeFrameBudget()
    expect(.frameWorkExceeded(actual: 3, maximum: 2)) {
      try work.recordCommand(
        pathElementCount: 2,
        limits: RemoteComposeNativeExecutionLimits(maximumFrameWork: 2))
    }
    var gradients = NativeFrameBudget()
    expect(.frameWorkExceeded(actual: 6, maximum: 5)) {
      try gradients.recordCommand(
        pathElementCount: 0, additionalWork: 5,
        limits: RemoteComposeNativeExecutionLimits(maximumFrameWork: 5))
    }

    print("native UIKit execution limit tests: ok")
  }

  private static func expect(
    _ expected: RemoteComposeNativeLimitError,
    operation: () throws -> Void
  ) {
    do {
      try operation()
      preconditionFailure("expected \(expected)")
    } catch let error as RemoteComposeNativeLimitError {
      precondition(error == expected, "expected \(expected), got \(error)")
    } catch {
      preconditionFailure("unexpected error: \(error)")
    }
  }
}
