import Foundation

/// The committed fixtures, copied into the test bundle by the manifest's `.copy("Fixtures")`.
///
/// They used to arrive as positional arguments from a shell script, which tied every run to the
/// repository root and a hand-built argv. Reading them from the bundle works under `swift test`,
/// `xcodebuild test`, and Xcode alike.
enum NativeTestFixtures {
  struct Missing: Error, CustomStringConvertible {
    let name: String
    var description: String { "test fixture not bundled: Fixtures/\(name)" }
  }

  /// The six supported comparative documents, in the order the fuzz corpus and the benchmark
  /// report expect them. The fuzz PRNG is seeded per position, so reordering this list changes
  /// the corpus.
  static let comparativeDocuments = [
    "editable-text.rc",
    "TitleCardRemote-640x480.rc",
    "IndeterminateCircularProgress-400x400.rc",
    "CircularProgressRemote-384x384.rc",
    "ArcProgressRemote-454x400.rc",
    "ImageBackgroundRemoteButton-454x200.rc",
  ]

  static func url(_ name: String) throws -> URL {
    guard let directory = Bundle.module.url(forResource: "Fixtures", withExtension: nil) else {
      throw Missing(name: name)
    }
    let url = directory.appendingPathComponent(name)
    guard FileManager.default.fileExists(atPath: url.path) else { throw Missing(name: name) }
    return url
  }

  static func data(_ name: String) throws -> Data {
    try Data(contentsOf: url(name))
  }
}
