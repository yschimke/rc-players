import Foundation

@main
enum NativeResourcePolicyTests {
  static func main() throws {
    var total = 0
    let limits = RemoteComposeNativeResourceLimits(
      maximumResourceBytes: 16,
      maximumTotalBytes: 24,
      maximumImageDimension: 8,
      maximumDecodedPixels: 32,
      maximumDecodedImageBytes: 64,
      maximumResourceCount: 2)
    try NativeResourcePolicy.validate(
      id: 1, byteCount: 12, width: 4, height: 4, runningTotal: &total, limits: limits)
    precondition(total == 12)
    let reference = try NativeResourcePolicy.reference(from: Data("image.png".utf8), id: 1)
    precondition(reference == "image.png")
    try NativeResourcePolicy.validateUniqueIDs([1, 2])

    expect(.resourceTooLarge(id: 2, actual: 17, maximum: 16)) { runningTotal in
      try NativeResourcePolicy.validate(
        id: 2, byteCount: 17, width: 1, height: 1, runningTotal: &runningTotal,
        limits: limits)
    }
    expect(.decodedImageTooLarge(id: 3, pixels: 40, maximum: 32)) { runningTotal in
      try NativeResourcePolicy.validate(
        id: 3, byteCount: 1, width: 5, height: 8, runningTotal: &runningTotal,
        limits: limits)
    }
    expect(.totalTooLarge(actual: 25, maximum: 24), initialTotal: 12) { runningTotal in
      try NativeResourcePolicy.validate(
        id: 4, byteCount: 13, width: 1, height: 1, runningTotal: &runningTotal,
        limits: limits)
    }
    expectError(.invalidDimensions(id: 5, width: 0, height: 1)) {
      var runningTotal = 0
      try NativeResourcePolicy.validate(
        id: 5, byteCount: 1, width: 0, height: 1, runningTotal: &runningTotal,
        limits: limits)
    }
    expectError(.invalidReference(id: 6)) {
      _ = try NativeResourcePolicy.reference(from: Data([0xff]), id: 6)
    }
    expectError(.duplicateResource(id: 7)) {
      try NativeResourcePolicy.validateUniqueIDs([7, 8, 7])
    }
    expectError(.invalidLimits) {
      try NativeResourcePolicy.validate(
        limits: RemoteComposeNativeResourceLimits(maximumDecodedImageBytes: 0))
    }

    let fontURL = URL(fileURLWithPath: #filePath)
      .deletingLastPathComponent()
      .appendingPathComponent("Fixtures/fonts/Roboto-Regular.ttf")
    let originalFont = try Data(contentsOf: fontURL)
    // A trailing padding byte preserves the valid font and PostScript name while making the
    // registration data differ, exercising replacement after the previous registry is gone.
    let replacementFont = originalFont + Data([0])
    func registerAndRelease(_ data: Data) throws {
      let registry = NativeFontRegistry(countLimit: 1)
      let name = try registry.register(data: data, id: 1)
      precondition(name == "Roboto-Regular")
    }
    try registerAndRelease(originalFont)
    let replacementRegistry = NativeFontRegistry(countLimit: 1)
    let replacementName = try replacementRegistry.register(data: replacementFont, id: 2)
    precondition(replacementName == "Roboto-Regular")
    replacementRegistry.reset()

    let fit = NativeImageGeometry.destination(
      source: CGRect(x: 0, y: 0, width: 200, height: 100),
      destination: CGRect(x: 10, y: 20, width: 100, height: 100),
      scaleType: 4,
      scaleFactor: 1)
    precondition(fit == CGRect(x: 10, y: 45, width: 100, height: 50))
    let crop = NativeImageGeometry.destination(
      source: CGRect(x: 0, y: 0, width: 200, height: 100),
      destination: CGRect(x: 10, y: 20, width: 100, height: 100),
      scaleType: 5,
      scaleFactor: 1)
    precondition(crop == CGRect(x: -40, y: 20, width: 200, height: 100))
    let huge = NativeImageGeometry.destination(
      source: CGRect(x: 0, y: 0, width: 2, height: 1),
      destination: CGRect(x: 0, y: 0, width: 1e100, height: 1e100),
      scaleType: 4,
      scaleFactor: 1)
    precondition(huge.width.isFinite && huge.height.isFinite)
    precondition(
      NativeImageGeometry.destination(
        source: CGRect(x: 0, y: 0, width: 2, height: 1),
        destination: CGRect(x: 0, y: 0, width: CGFloat.infinity, height: 1),
        scaleType: 6,
        scaleFactor: 1) == .zero)
    print("native UIKit resource policy tests: ok")
  }

  private static func expect(
    _ expected: RemoteComposeNativeResourceError,
    initialTotal: Int = 0,
    operation: (inout Int) throws -> Void
  ) {
    var total = initialTotal
    do {
      try operation(&total)
      preconditionFailure("expected \(expected)")
    } catch let error as RemoteComposeNativeResourceError {
      precondition(error == expected)
    } catch {
      preconditionFailure("unexpected error: \(error)")
    }
  }

  private static func expectError(
    _ expected: RemoteComposeNativeResourceError,
    operation: () throws -> Void
  ) {
    do {
      try operation()
      preconditionFailure("expected \(expected)")
    } catch let error as RemoteComposeNativeResourceError {
      precondition(error == expected)
    } catch {
      preconditionFailure("unexpected error: \(error)")
    }
  }
}
