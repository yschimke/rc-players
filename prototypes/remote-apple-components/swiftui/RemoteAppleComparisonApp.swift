import AppKit
import SwiftUI

private let canvasSize = CGSize(width: 390, height: 620)

private enum ApplePalette {
  static let accent = Color(red: 0, green: 122 / 255, blue: 1)
  static let destructive = Color(red: 1, green: 59 / 255, blue: 48 / 255)
  static let label = Color.black
  static let secondaryLabel = Color(red: 108 / 255, green: 108 / 255, blue: 112 / 255)
  static let background = Color(red: 242 / 255, green: 242 / 255, blue: 247 / 255)
  static let secondaryBackground = Color.white
  static let toggleOn = Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255)
  static let toggleOff = Color(red: 233 / 255, green: 233 / 255, blue: 234 / 255)
}

private struct ReferenceToggleStyle: ToggleStyle {
  func makeBody(configuration: Configuration) -> some View {
    HStack(spacing: 12) {
      configuration.label
        .font(.system(size: 17))
        .foregroundStyle(ApplePalette.label)
      Spacer(minLength: 8)
      ZStack(alignment: configuration.isOn ? .trailing : .leading) {
        Capsule()
          .fill(configuration.isOn ? ApplePalette.toggleOn : ApplePalette.toggleOff)
          .frame(width: 51, height: 31)
        Circle()
          .fill(ApplePalette.secondaryBackground)
          .frame(width: 27, height: 27)
          .padding(2)
          .shadow(color: .black.opacity(0.12), radius: 1, y: 1)
      }
      .contentShape(Rectangle())
    }
    .frame(height: 52)
    .contentShape(Rectangle())
    .onTapGesture { configuration.isOn.toggle() }
  }
}

private struct ReferenceButtonStyle: ButtonStyle {
  enum Treatment { case prominent, destructive }

  let treatment: Treatment

  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .font(.system(size: 17, weight: .semibold))
      .foregroundStyle(treatment == .prominent ? .white : ApplePalette.destructive)
      .frame(maxWidth: .infinity)
      .frame(height: 44)
      .background(treatment == .prominent ? ApplePalette.accent : .clear)
      .overlay {
        if treatment == .destructive {
          RoundedRectangle(cornerRadius: 10)
            .stroke(ApplePalette.destructive, lineWidth: 1)
        }
      }
      .clipShape(RoundedRectangle(cornerRadius: 10))
      .opacity(configuration.isPressed ? 0.65 : 1)
  }
}

private struct ReferenceSection<Content: View>: View {
  let title: String
  let content: Content

  init(title: String, @ViewBuilder content: () -> Content) {
    self.title = title
    self.content = content()
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 7) {
      Text(title)
        .font(.system(size: 13))
        .foregroundStyle(ApplePalette.secondaryLabel)
        .padding(.horizontal, 16)
      VStack(spacing: 0) { content }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity)
        .background(ApplePalette.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
  }
}

struct RemoteAppleSwiftUIReference: View {
  @State private var wifi = true

  var body: some View {
    VStack(alignment: .leading, spacing: 24) {
      ReferenceSection(title: "Connectivity") {
        Toggle("Wi-Fi", isOn: $wifi)
          .toggleStyle(ReferenceToggleStyle())
        HStack {
          Text("Network")
            .foregroundStyle(ApplePalette.label)
          Spacer()
          Text("Studio")
            .foregroundStyle(ApplePalette.secondaryLabel)
        }
        .font(.system(size: 17))
        .frame(height: 44)
      }

      ReferenceSection(title: "Playback") {
        VStack(alignment: .leading, spacing: 8) {
          Text("Downloading")
            .font(.system(size: 13))
            .foregroundStyle(ApplePalette.secondaryLabel)
          GeometryReader { proxy in
            ZStack(alignment: .leading) {
              Capsule().fill(ApplePalette.toggleOff)
              Capsule().fill(ApplePalette.accent).frame(width: proxy.size.width * 0.64)
            }
          }
          .frame(height: 4)
        }
        .padding(.vertical, 12)

        Button("Play") {}
          .buttonStyle(ReferenceButtonStyle(treatment: .prominent))
        Button("Remove Download") {}
          .buttonStyle(ReferenceButtonStyle(treatment: .destructive))
      }
    }
    .padding(20)
    .frame(width: canvasSize.width, height: canvasSize.height, alignment: .topLeading)
    .background(ApplePalette.background)
    .environment(\.colorScheme, .light)
  }
}

private struct ComparisonPane<Content: View>: View {
  let title: String
  let subtitle: String
  let content: Content

  init(title: String, subtitle: String, @ViewBuilder content: () -> Content) {
    self.title = title
    self.subtitle = subtitle
    self.content = content()
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 10) {
      VStack(alignment: .leading, spacing: 2) {
        Text(title).font(.headline)
        Text(subtitle).font(.caption).foregroundStyle(.secondary)
      }
      content
        .frame(width: canvasSize.width, height: canvasSize.height)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay { RoundedRectangle(cornerRadius: 12).stroke(.quaternary) }
    }
  }
}

private struct ComparisonView: View {
  let remoteImage: NSImage

  var body: some View {
    VStack(alignment: .leading, spacing: 18) {
      VStack(alignment: .leading, spacing: 4) {
        Text("Remote Apple component comparison")
          .font(.largeTitle.bold())
        Text("One specification expressed in SwiftUI and remote-creation-compose")
          .foregroundStyle(.secondary)
      }
      HStack(alignment: .top, spacing: 24) {
        ComparisonPane(title: "SwiftUI reference", subtitle: "Live reference implementation") {
          RemoteAppleSwiftUIReference()
        }
        ComparisonPane(
          title: "Remote Compose", subtitle: "Captured document rendered by the CMP player"
        ) {
          Image(nsImage: remoteImage)
            .resizable()
            .interpolation(.none)
        }
      }
    }
    .padding(24)
    .frame(minWidth: 876, minHeight: 724)
  }
}

private enum ComparisonError: Error, CustomStringConvertible {
  case usage
  case missingResource(String)
  case cannotRender
  case imageMismatch

  var description: String {
    switch self {
    case .usage:
      "usage: RemoteAppleComparison [--render-reference output.png | --compare reference.png candidate.png diff.png report.json]"
    case .missingResource(let path): "could not load image: \(path)"
    case .cannotRender: "could not render SwiftUI reference"
    case .imageMismatch: "reference and candidate dimensions differ"
    }
  }
}

@MainActor
private func renderReference(to url: URL) throws {
  _ = NSApplication.shared
  let view = NSHostingView(rootView: RemoteAppleSwiftUIReference())
  view.frame = NSRect(origin: .zero, size: canvasSize)
  view.layoutSubtreeIfNeeded()
  guard let bitmap = view.bitmapImageRepForCachingDisplay(in: view.bounds) else {
    throw ComparisonError.cannotRender
  }
  view.cacheDisplay(in: view.bounds, to: bitmap)
  guard let png = bitmap.representation(using: .png, properties: [:]) else {
    throw ComparisonError.cannotRender
  }
  try png.write(to: url)
}

private func rgba(_ image: NSImage) throws -> (width: Int, height: Int, pixels: [UInt8]) {
  var proposed = NSRect(origin: .zero, size: image.size)
  guard let source = image.cgImage(forProposedRect: &proposed, context: nil, hints: nil) else {
    throw ComparisonError.cannotRender
  }
  let width = source.width
  let height = source.height
  var pixels = [UInt8](repeating: 0, count: width * height * 4)
  guard
    let context = CGContext(
      data: &pixels, width: width, height: height, bitsPerComponent: 8,
      bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
  else { throw ComparisonError.cannotRender }
  context.draw(source, in: CGRect(x: 0, y: 0, width: width, height: height))
  return (width, height, pixels)
}

private func compare(reference: URL, candidate: URL, diff: URL, report: URL) throws {
  guard let referenceImage = NSImage(contentsOf: reference) else {
    throw ComparisonError.missingResource(reference.path)
  }
  guard let candidateImage = NSImage(contentsOf: candidate) else {
    throw ComparisonError.missingResource(candidate.path)
  }
  let lhs = try rgba(referenceImage)
  let rhs = try rgba(candidateImage)
  guard lhs.width == rhs.width, lhs.height == rhs.height else { throw ComparisonError.imageMismatch }

  var output = [UInt8](repeating: 255, count: lhs.pixels.count)
  var absoluteDifference = 0
  var changed = 0
  let pixelCount = lhs.width * lhs.height
  for offset in stride(from: 0, to: lhs.pixels.count, by: 4) {
    let red = abs(Int(lhs.pixels[offset]) - Int(rhs.pixels[offset]))
    let green = abs(Int(lhs.pixels[offset + 1]) - Int(rhs.pixels[offset + 1]))
    let blue = abs(Int(lhs.pixels[offset + 2]) - Int(rhs.pixels[offset + 2]))
    let maximum = max(red, green, blue)
    absoluteDifference += red + green + blue
    if maximum > 16 { changed += 1 }
    output[offset] = UInt8(maximum)
    output[offset + 1] = 0
    output[offset + 2] = UInt8(maximum)
  }
  guard
    let context = CGContext(
      data: &output, width: lhs.width, height: lhs.height, bitsPerComponent: 8,
      bytesPerRow: lhs.width * 4, space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue),
    let image = context.makeImage()
  else { throw ComparisonError.cannotRender }
  let bitmap = NSBitmapImageRep(cgImage: image)
  guard let png = bitmap.representation(using: .png, properties: [:]) else {
    throw ComparisonError.cannotRender
  }
  try png.write(to: diff)

  let payload: [String: Any] = [
    "width": lhs.width,
    "height": lhs.height,
    "mean_absolute_channel_error": Double(absoluteDifference) / Double(pixelCount * 3),
    "changed_pixel_ratio_at_16": Double(changed) / Double(pixelCount),
    "reference": reference.lastPathComponent,
    "candidate": candidate.lastPathComponent,
  ]
  try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys])
    .write(to: report)
}

@main
private struct RemoteAppleComparisonMain {
  @MainActor
  static func main() throws {
    let arguments = CommandLine.arguments
    if arguments.count == 3, arguments[1] == "--render-reference" {
      try renderReference(to: URL(fileURLWithPath: arguments[2]))
      return
    }
    if arguments.count == 6, arguments[1] == "--compare" {
      try compare(
        reference: URL(fileURLWithPath: arguments[2]),
        candidate: URL(fileURLWithPath: arguments[3]),
        diff: URL(fileURLWithPath: arguments[4]),
        report: URL(fileURLWithPath: arguments[5]))
      return
    }
    if arguments.count != 1 { throw ComparisonError.usage }

    guard
      let resource = Bundle.main.url(
        forResource: "after-component-set", withExtension: "png"),
      let remoteImage = NSImage(contentsOf: resource)
    else { throw ComparisonError.missingResource("after-component-set.png") }
    let application = NSApplication.shared
    application.setActivationPolicy(.regular)
    let window = NSWindow(
      contentRect: NSRect(x: 0, y: 0, width: 876, height: 724),
      styleMask: [.titled, .closable, .miniaturizable, .resizable],
      backing: .buffered,
      defer: false)
    window.title = "Remote Apple Comparison"
    window.contentViewController = NSHostingController(rootView: ComparisonView(remoteImage: remoteImage))
    window.center()
    window.makeKeyAndOrderFront(nil)
    application.activate(ignoringOtherApps: true)
    application.run()
  }
}
