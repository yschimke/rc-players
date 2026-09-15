import SwiftUI
import UIKit

private struct NativeComparisonEntry: Decodable {
  let id: String
  let width: Int
  let height: Int
  let density: Double
}

private enum NativeComparisonError: LocalizedError {
  case invalidIdentifier(String)
  case invalidSize(String)
  case renderTimeout(String)
  case pngEncoding(String)

  var errorDescription: String? {
    switch self {
    case .invalidIdentifier(let id): "Invalid comparison fixture identifier: \(id)"
    case .invalidSize(let id): "Invalid comparison fixture size: \(id)"
    case .renderTimeout(let id): "Native UIKit player timed out rendering \(id)"
    case .pngEncoding(let id): "UIKit could not encode \(id) as PNG"
    }
  }
}

/// App-hosted renderer for the manifest contract used by the cross-player comparison scripts.
struct NativeComparisonHarnessView: View {
  @State private var status = "Rendering native UIKit comparison lane…"

  var body: some View {
    Text(status)
      .font(.headline)
      .padding(32)
      .task {
        do {
          let count = try await NativeComparisonHarness.run()
          status = "Native UIKit comparison complete: \(count) document(s)"
        } catch {
          status = "Native UIKit comparison failed: \(error.localizedDescription)"
        }
      }
  }
}

@MainActor
private enum NativeComparisonHarness {
  private static let directoryName = "native-comparison"

  static func run() async throws -> Int {
    let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    let root = documents.appendingPathComponent(directoryName, isDirectory: true)
    let input = root.appendingPathComponent("input", isDirectory: true)
    let output = root.appendingPathComponent("output", isDirectory: true)
    let done = root.appendingPathComponent("done.json")
    try? FileManager.default.removeItem(at: output)
    try? FileManager.default.removeItem(at: done)
    try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)

    let manifest = input.appendingPathComponent("manifest.json")
    let entries = try JSONDecoder().decode(
      [NativeComparisonEntry].self, from: Data(contentsOf: manifest))
    var rendered = 0
    var failed = 0
    for entry in entries {
      do {
        try validate(entry)
        let source = input.appendingPathComponent("\(entry.id).rc")
        let result = try await render(Data(contentsOf: source), entry: entry)
        guard let png = result.image.pngData() else {
          throw NativeComparisonError.pngEncoding(entry.id)
        }
        try png.write(to: output.appendingPathComponent("\(entry.id).png"), options: .atomic)
        if let diagnostics = result.diagnostics, diagnostics.isPartial {
          let report = diagnostics.issues.map {
            "\($0.operationName)[\($0.opcode)] component=\($0.componentID): \($0.reason)"
          }.joined(separator: "\n")
          try Data(report.utf8).write(
            to: output.appendingPathComponent("\(entry.id).unsupported"), options: .atomic)
        }
        rendered += 1
      } catch {
        let message = "\(type(of: error)): \(error.localizedDescription)"
        try Data(message.utf8).write(
          to: output.appendingPathComponent("\(entry.id).error"), options: .atomic)
        failed += 1
      }
    }

    let summary: [String: Int] = ["documents": entries.count, "rendered": rendered, "failed": failed]
    let summaryData = try JSONSerialization.data(
      withJSONObject: summary, options: [.prettyPrinted, .sortedKeys])
    try summaryData.write(to: done, options: .atomic)
    return entries.count
  }

  private static func validate(_ entry: NativeComparisonEntry) throws {
    guard
      !entry.id.isEmpty, entry.id == URL(fileURLWithPath: entry.id).lastPathComponent,
      !entry.id.contains("/")
    else { throw NativeComparisonError.invalidIdentifier(entry.id) }
    guard
      entry.width > 0, entry.height > 0, entry.width <= 8_192, entry.height <= 8_192,
      entry.density.isFinite, entry.density >= 0.25, entry.density <= 4
    else {
      throw NativeComparisonError.invalidSize(entry.id)
    }
  }

  private static func render(
    _ data: Data, entry: NativeComparisonEntry
  ) async throws -> (
    image: UIImage, diagnostics: RemoteComposeNativePlayerDiagnostics?
  ) {
    var diagnostics: RemoteComposeNativePlayerDiagnostics?
    let scale = CGFloat(entry.density)
    let size = CGSize(width: CGFloat(entry.width) / scale, height: CGFloat(entry.height) / scale)
    let player = RemoteComposeNativePlayerView(
      data: data,
      background: .transparent,
      compatibilityPolicy: .compatible,
      onDiagnostics: { diagnostics = $0 })
    player.overrideUserInterfaceStyle = .light
    player.frame = CGRect(origin: .zero, size: size)

    let deadline = ProcessInfo.processInfo.systemUptime + 10
    while findRenderedDocument(in: player) == nil,
      ProcessInfo.processInfo.systemUptime < deadline
    {
      await Task.yield()
      try await Task.sleep(nanoseconds: 10_000_000)
    }
    guard findRenderedDocument(in: player) != nil else {
      throw NativeComparisonError.renderTimeout(entry.id)
    }

    player.setNeedsLayout()
    player.layoutIfNeeded()
    let format = UIGraphicsImageRendererFormat()
    format.scale = scale
    format.opaque = false
    let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
      player.layer.render(in: context.cgContext)
    }
    return (image, diagnostics)
  }

  private static func findRenderedDocument(in view: UIView) -> UIView? {
    if view.accessibilityIdentifier == "rc-native-document" { return view }
    for child in view.subviews {
      if let result = findRenderedDocument(in: child) { return result }
    }
    return nil
  }
}
