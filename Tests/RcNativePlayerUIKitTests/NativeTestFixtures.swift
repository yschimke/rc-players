import Foundation

/// The platform tests' fixtures, copied into this test bundle by the manifest's `.copy("Fixtures")`.
///
/// Only the fonts live here. The `.rc` documents belong to `RcNativePlayerCoreTests`, which has
/// its own copy of this helper reading its own bundle, so the core suite can run without the
/// platform targets.
enum NativeTestFixtures {
  struct Missing: Error, CustomStringConvertible {
    let name: String
    var description: String { "test fixture not bundled: Fixtures/\(name)" }
  }

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
