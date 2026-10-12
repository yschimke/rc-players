// `rc-swift-render`: the native Swift player's AppKit renderer as a command-line tool, so anything
// that renders `.rc` documents to PNG — the preview server's `swift-appkit` lane, a player
// comparison, a script on a Mac — can ask the Apple player for its pixels the same way it asks the
// CMP JVM player for its own.
//
// Two modes, mirroring compose-ai-tools' `rc-render-jvm` exactly, because the server drives both
// renderers through one client:
//
//   rc-swift-render --input <doc.rc> --output <out.png> --width <px> --height <px>
//     [--density <f>] [--fontScale <f>] [--format png] [--theme light|dark] [--seeds <file>]
//     [--time <seconds>]
//
//   rc-swift-render --worker
//     Binary frames over stdin/stdout, big-endian; see `RcSwiftRenderWorker`. Closing stdin ends
//     the worker.
//
// macOS only: the renderer is AppKit, CoreGraphics and CoreText. On every other platform this
// target compiles to a stub that says so, so building the whole package for visionOS or iOS still
// succeeds.

#if os(macOS)
  import AppKit
  import Foundation
  import RcNativePlayerCore
  import RcNativePlayerUIKit

  /// One render, the shape both modes reduce a request to.
  struct RcSwiftRenderRequest {
    var width: Int
    var height: Int
    var density: CGFloat = 1
    var fontScale: Float = 1
    var dark = false
    var seeds: [String: RemoteComposeNativeNamedValue] = [:]
    var timeSeconds: TimeInterval = 0
  }

  @MainActor
  func rcSwiftRender(_ document: Data, _ request: RcSwiftRenderRequest) throws -> Data {
    try autoreleasepool {
      try NativeAppKitWindowController.renderPNG(
        data: document, pixelWidth: request.width, pixelHeight: request.height,
        density: request.density,
        theme: request.dark ? NativeSwiftTheme.dark : NativeSwiftTheme.light,
        fontScale: request.fontScale, namedValues: request.seeds,
        timeSeconds: request.timeSeconds)
    }
  }

  /// The knob-seed wire format `rc-render-jvm` defines (`RcSeedFormat.kt`), parsed the same way:
  /// one seed per line, `<kind> <base64Name> <value>`, kind `str` / `float` / `int` / `color`, with
  /// `str` values base64 and the numeric kinds plain decimal (`color` a signed decimal ARGB int).
  /// Unknown kinds and malformed lines are skipped, never fatal.
  func parseSeedText(_ text: String) -> [String: RemoteComposeNativeNamedValue] {
    func decode(_ value: Substring) -> String? {
      Data(base64Encoded: String(value)).flatMap { String(data: $0, encoding: .utf8) }
    }
    var seeds: [String: RemoteComposeNativeNamedValue] = [:]
    for line in text.split(whereSeparator: \.isNewline) {
      let parts = line.split(separator: " ", omittingEmptySubsequences: false)
      guard parts.count >= 3, let name = decode(parts[1]) else { continue }
      let raw = parts[2]
      switch parts[0] {
      case "str": if let value = decode(raw) { seeds[name] = .text(value) }
      case "float": if let value = Float(raw), value.isFinite { seeds[name] = .float(value) }
      case "int": if let value = Int32(raw) { seeds[name] = .integer(Int(value)) }
      case "color": if let value = Int32(raw) { seeds[name] = .color(UInt32(bitPattern: value)) }
      default: continue
      }
    }
    return seeds
  }

  func fail(_ message: String, code: Int32) -> Never {
    FileHandle.standardError.write(Data("\(message)\n".utf8))
    exit(code)
  }

  @MainActor
  func runOneShot(_ arguments: [String]) -> Never {
    var options: [String: String] = [:]
    var index = 0
    while index < arguments.count {
      let key = arguments[index]
      guard key.hasPrefix("--"), index + 1 < arguments.count else {
        fail("unexpected argument \(key)", code: 2)
      }
      options[String(key.dropFirst(2))] = arguments[index + 1]
      index += 2
    }
    guard let input = options["input"], let output = options["output"],
      let width = options["width"].flatMap(Int.init),
      let height = options["height"].flatMap(Int.init)
    else {
      fail(
        "usage: rc-swift-render --input <doc.rc> --output <out.png> --width <px> --height <px> "
          + "[--density <f>] [--fontScale <f>] [--format png] [--theme light|dark] "
          + "[--seeds <file>] [--time <seconds>]   |   rc-swift-render --worker",
        code: 2)
    }
    guard width > 0, height > 0 else {
      fail("width and height must be positive (got \(width)x\(height))", code: 2)
    }
    if let format = options["format"]?.lowercased(), format != "png" {
      fail("format must be png; the AppKit renderer has no vector export (got \(format))", code: 2)
    }
    var request = RcSwiftRenderRequest(width: width, height: height)
    // Unparseable or non-positive display axes fall back to the defaults, as `rc-render-jvm` does.
    if let density = options["density"].flatMap(Double.init), density > 0 {
      request.density = CGFloat(density)
    }
    if let fontScale = options["fontScale"].flatMap(Float.init), fontScale > 0 {
      request.fontScale = fontScale
    }
    if let time = options["time"].flatMap(Double.init), time >= 0 {
      request.timeSeconds = time
    }
    request.dark = options["theme"]?.lowercased() == "dark"
    if let seedsPath = options["seeds"] {
      if let text = try? String(contentsOfFile: seedsPath, encoding: .utf8) {
        request.seeds = parseSeedText(text)
      } else {
        FileHandle.standardError.write(Data("ignoring unreadable seed file \(seedsPath)\n".utf8))
      }
    }
    let document: Data
    do {
      document = try Data(contentsOf: URL(fileURLWithPath: input))
    } catch {
      fail("could not read input document \(input): \(error)", code: 3)
    }
    do {
      let png = try rcSwiftRender(document, request)
      try png.write(to: URL(fileURLWithPath: output), options: .atomic)
    } catch {
      fail("\(error)", code: 1)
    }
    exit(0)
  }

  _ = NSApplication.shared
  // No Dock icon and no menu bar, whichever mode: this is an offscreen renderer.
  NSApp.setActivationPolicy(.prohibited)

  let arguments = Array(CommandLine.arguments.dropFirst())
  if arguments == ["--worker"] {
    RcSwiftRenderWorker.run()
  } else {
    runOneShot(arguments)
  }
#else
  import Foundation

  FileHandle.standardError.write(
    Data("rc-swift-render needs macOS: the native renderer is AppKit.\n".utf8))
  exit(2)
#endif
