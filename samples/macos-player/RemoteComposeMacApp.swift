import AppKit
import RcComposePlayer
#if canImport(RcPlayerAppleFonts)
  import RcPlayerAppleFonts
#endif
import SwiftUI
import UniformTypeIdentifiers

private enum NativeConformanceJobError: Error { case malformed }

private struct NativeConformanceJob: Decodable {
  let document: String
  let output: String
  let frames: [NativeConformanceFrameRequest]
}

private struct NativeConformanceClock: Decodable {
  let timestampMillis: Int64?
  let continuousSeconds: Double?
  let year: Int?
  let month: Int?
  let day: Int?
  let hour: Int?
  let minute: Int?
  let second: Int?

  private enum CodingKeys: String, CodingKey {
    case year, month, day, hour, minute, second
    case timestampMillis = "timestamp_millis"
    case continuousSeconds = "continuous_seconds"
  }

  var value: NativeSwiftWallClock {
    if let timestampMillis { return NativeSwiftWallClock(epochMillis: timestampMillis) }
    if let continuousSeconds {
      return NativeSwiftWallClock(epochMillis: Int64(continuousSeconds * 1000))
    }

    // Time-only snapshots use a stable UTC date; calendar snapshots send an epoch above.
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    var components = DateComponents()
    components.calendar = calendar
    components.timeZone = calendar.timeZone
    components.year = year ?? 1970
    components.month = month ?? 1
    components.day = day ?? 1
    components.hour = hour ?? 0
    components.minute = minute ?? 0
    components.second = second ?? 0
    let epochMillis = Int64((calendar.date(from: components)?.timeIntervalSince1970 ?? 0) * 1000)
    return NativeSwiftWallClock(epochMillis: epochMillis)
  }
}

private struct NativeConformanceFrameRequest: Decodable {
  let id: String
  let time: TimeInterval
  let width: CGFloat?
  let height: CGFloat?
  let clock: NativeConformanceClock?
  let steps: [NativeMacInputStep]
  let values: NativeMacValueRequest

  private enum CodingKeys: String, CodingKey {
    case id, time, width, height, steps, values
    case clock = "wall_clock"
  }

  init(from decoder: Decoder) throws {
    let values = try decoder.container(keyedBy: CodingKeys.self)
    id = try values.decode(String.self, forKey: .id)
    time = try values.decodeIfPresent(TimeInterval.self, forKey: .time) ?? 0
    width = try values.decodeIfPresent(CGFloat.self, forKey: .width)
    height = try values.decodeIfPresent(CGFloat.self, forKey: .height)
    clock = try values.decodeIfPresent(NativeConformanceClock.self, forKey: .clock)
    steps = try values.decodeIfPresent([NativeMacInputStep].self, forKey: .steps) ?? []
    self.values =
      try values.decodeIfPresent(NativeMacValueRequest.self, forKey: .values)
      ?? NativeMacValueRequest()
  }

  var viewport: CGSize? {
    guard let width, let height else { return nil }
    return CGSize(width: width, height: height)
  }

  var wallClock: NativeSwiftWallClock { clock?.value ?? .capture }
}

enum DesktopRenderer: String, CaseIterable, Identifiable {
  case compose = "cmp"
  case native = "native"

  var id: Self { self }
  var title: String { self == .compose ? "CMP" : "Native AppKit POC" }
  var detail: String {
    self == .compose
      ? "The production Compose Multiplatform renderer."
      : "A Swift-native AppKit hierarchy backed by the pure-Swift core."
  }
}

struct DesktopPlayerEvent: Identifiable {
  let id = UUID()
  let renderer: DesktopRenderer
  let documentTitle: String
  let summary: String
  let timestamp = Date()
}

@MainActor
final class DesktopPlayerModel: ObservableObject {
  @Published var documentURL: URL?
  @Published var documentData: Data?
  @Published var errorMessage: String?
  @Published private(set) var fontStatusMessage: String?
  @Published private(set) var nativeDiagnostics: RemoteComposeNativePlayerDiagnostics?
  @Published private(set) var events: [DesktopPlayerEvent] = []
  private let googleFonts = RemoteComposeGoogleFontsResolver()
  private var renderTask: Task<Void, Never>?

  var renderer: DesktopRenderer {
    get {
      DesktopRenderer(rawValue: UserDefaults.standard.string(forKey: "renderer") ?? "cmp")
        ?? .compose
    }
    set {
      UserDefaults.standard.set(newValue.rawValue, forKey: "renderer")
      objectWillChange.send()
    }
  }

  var nativeCompatibility: NativeMacCompatibility {
    get {
      NativeMacCompatibility(
        rawValue: UserDefaults.standard.string(forKey: "nativeCompatibility") ?? "compatible")
        ?? .compatible
    }
    set {
      UserDefaults.standard.set(newValue.rawValue, forKey: "nativeCompatibility")
      objectWillChange.send()
    }
  }

  var downloadsGoogleFonts: Bool {
    get {
      guard UserDefaults.standard.object(forKey: "downloadsGoogleFonts") != nil else { return true }
      return UserDefaults.standard.bool(forKey: "downloadsGoogleFonts")
    }
    set {
      UserDefaults.standard.set(newValue, forKey: "downloadsGoogleFonts")
      objectWillChange.send()
    }
  }

  func chooseDocument() {
    let panel = NSOpenPanel()
    panel.allowedContentTypes = [UTType(filenameExtension: "rc") ?? .data]
    panel.allowsMultipleSelection = false
    panel.message = "Choose a Remote Compose document"
    guard panel.runModal() == .OK, let url = panel.url else { return }
    open(url)
  }

  func open(_ url: URL, renderImmediately: Bool = false) {
    guard url.pathExtension.lowercased() == "rc" else {
      errorMessage = "\(url.lastPathComponent) is not a Remote Compose (.rc) document."
      return
    }
    do {
      documentData = try Data(contentsOf: url)
      documentURL = url
      errorMessage = nil
      NSDocumentController.shared.noteNewRecentDocumentURL(url)
      if renderImmediately { render() }
    } catch {
      errorMessage = "Could not open \(url.lastPathComponent): \(error.localizedDescription)"
    }
  }

  func render() {
    guard let documentData else {
      chooseDocument()
      return
    }
    let title = documentURL?.deletingPathExtension().lastPathComponent ?? "Remote Compose"
    let renderer = renderer
    let compatibility = nativeCompatibility
    let resolver: (any RemoteComposeDownloadableFontResolving)? =
      downloadsGoogleFonts ? googleFonts : nil
    renderTask?.cancel()
    errorMessage = nil
    fontStatusMessage = downloadsGoogleFonts
      ? "Google Fonts loading is enabled." : "Google Fonts loading is disabled; using system fonts."
    renderTask = Task { [weak self] in
      guard let self else { return }
      do {
        switch renderer {
        case .compose:
          nativeDiagnostics = nil
          let bytes = RcDataBridgeKt.rcByteArray(data: documentData)
          let typefaces = await cmpTypefaces(bytes: bytes, resolver: resolver)
          try Task.checkCancellation()
          RcComposeWindowKt.RcComposeWindow(
            bytes: bytes,
            title: title,
            width: 800,
            height: 600,
            theme: RcPlayerTheme.system,
            onEvent: { [weak self] event in
              DispatchQueue.main.async {
                self?.record(
                  renderer: .compose, documentTitle: title,
                  summary: Self.describe(event))
              }
            },
            typefaces: typefaces,
            onError: { [weak self] message in
              DispatchQueue.main.async { self?.errorMessage = message }
            },
            lenient: true)
        case .native:
          try await NativeAppKitWindowController.shared.open(
            data: documentData, title: title, compatibility: compatibility,
            downloadableFontResolver: resolver,
            onFontFallback: { [weak self] message in self?.fontStatusMessage = message },
            onEvent: { [weak self] summary in
              self?.record(renderer: .native, documentTitle: title, summary: summary)
            },
            onDiagnostics: { [weak self] diagnostics in
              self?.nativeDiagnostics = diagnostics
            },
            onError: { [weak self] message in self?.errorMessage = message })
        }
      } catch is CancellationError {
      } catch RemoteComposeNativePlayerError.incompatible(let diagnostics) {
        nativeDiagnostics = diagnostics
        errorMessage =
          "Strict native policy refused \(title): \(diagnostics.issues.count) known difference(s)."
      } catch {
        errorMessage = "Could not render \(title): \(error.localizedDescription)"
      }
    }
  }

  private func cmpTypefaces(
    bytes: KotlinByteArray,
    resolver: (any RemoteComposeDownloadableFontResolving)?
  ) async -> RcTypefaceLoader {
    let requests = RcDownloadableFontsKt.rcDownloadableFontRequests(bytes: bytes)
    guard !requests.isEmpty else { return RcTypefaceLoaderCompanion.shared.Default }
    guard let resolver else {
      return RcDownloadableFontsKt.rcDownloadableFontFallback(
        families: requests.map(\.family))
    }
    do {
      var fonts: [RcDownloadedFont] = []
      for request in requests {
        let font = try await resolver.resolve(
          RemoteComposeDownloadableFontRequest(family: request.family))
        guard font.family.caseInsensitiveCompare(request.family) == .orderedSame else {
          throw RemoteComposeDownloadableFontError.familyMismatch(
            expected: request.family, actual: font.family)
        }
        try Task.checkCancellation()
        fonts.append(
          RcDownloadedFont(
            family: font.family, identity: font.identity,
            data: RcDataBridgeKt.rcByteArray(data: font.data)))
      }
      fontStatusMessage = "Downloaded \(fonts.count) Google font family(s)."
      return RcDownloadableFontsKt.rcDownloadedTypefaceLoader(fonts: fonts)
    } catch is CancellationError {
      return RcDownloadableFontsKt.rcDownloadableFontFallback(
        families: requests.map(\.family))
    } catch {
      fontStatusMessage =
        "Google Fonts unavailable; using the default font: \(error.localizedDescription)"
      return RcDownloadableFontsKt.rcDownloadableFontFallback(
        families: requests.map(\.family))
    }
  }

  func clearEvents() { events.removeAll() }

  private func record(renderer: DesktopRenderer, documentTitle: String, summary: String) {
    events.insert(
      DesktopPlayerEvent(renderer: renderer, documentTitle: documentTitle, summary: summary), at: 0)
    if events.count > 50 { events.removeLast(events.count - 50) }
  }

  private static func describe(_ event: RcPlayerEvent) -> String {
    if let action = event as? RcPlayerEventHostAction {
      return "Action \(action.actionId)"
    }
    if let action = event as? RcPlayerEventHostActionMetadata {
      return "Action \(action.actionId): \(action.metadata)"
    }
    if let action = event as? RcPlayerEventHostNamedAction {
      return "Named \(action.name): \(describe(action.value))"
    }
    if let debug = event as? RcPlayerEventDebugMessage {
      return "Debug: \(debug.message) (value \(debug.value), flags \(debug.flags))"
    }
    return String(describing: event)
  }

  private static func describe(_ value: RcHostActionValue) -> String {
    switch value {
    case is RcHostActionValueNone: return "none"
    case let value as RcHostActionValueFloatValue: return String(value.value)
    case let value as RcHostActionValueIntegerValue: return String(value.value)
    case let value as RcHostActionValueTextValue: return value.value
    case let value as RcHostActionValueFloatListValue:
      return value.value.map { String($0.floatValue) }.joined(separator: ", ")
    default: return String(describing: value)
    }
  }
}

struct DesktopPlayerView: View {
  @ObservedObject var model: DesktopPlayerModel

  var body: some View {
    VStack(alignment: .leading, spacing: 22) {
      VStack(alignment: .leading, spacing: 6) {
        Text("Remote Compose Player")
          .font(.largeTitle.bold())
        Text("Open a local .rc document and compare the macOS renderers.")
          .foregroundStyle(.secondary)
      }

      GroupBox("Document") {
        HStack(spacing: 12) {
          Image(systemName: "doc.richtext")
            .font(.title2)
            .foregroundStyle(.tint)
          VStack(alignment: .leading, spacing: 2) {
            Text(model.documentURL?.lastPathComponent ?? "No document selected")
              .font(.headline)
            if let data = model.documentData {
              Text(ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file))
                .font(.caption)
                .foregroundStyle(.secondary)
            }
          }
          Spacer()
          Button("Open…") { model.chooseDocument() }
            .keyboardShortcut("o")
        }
        .padding(8)
      }

      GroupBox("Renderer") {
        VStack(alignment: .leading, spacing: 8) {
          Picker("Renderer", selection: rendererBinding) {
            ForEach(DesktopRenderer.allCases) { renderer in
              Text(renderer.title).tag(renderer)
            }
          }
          .pickerStyle(.segmented)
          Text(model.renderer.detail)
            .font(.caption)
            .foregroundStyle(.secondary)
          Divider()
          Picker("Native policy", selection: nativeCompatibilityBinding) {
            ForEach(NativeMacCompatibility.allCases) { policy in
              Text(policy.title).tag(policy)
            }
          }
          .pickerStyle(.segmented)
          Text(model.nativeCompatibility.detail)
            .font(.caption)
            .foregroundStyle(.secondary)
          Divider()
          Label(
            model.downloadsGoogleFonts ? "Google Fonts enabled" : "Google Fonts disabled",
            systemImage: model.downloadsGoogleFonts ? "textformat" : "textformat.slash"
          )
          .font(.caption)
          .foregroundStyle(.secondary)
        }
        .padding(8)
      }

      GroupBox("Native compatibility & safety") {
        VStack(alignment: .leading, spacing: 6) {
          if let diagnostics = model.nativeDiagnostics {
            Label(
              diagnostics.isPartial
                ? "\(diagnostics.issues.count) known rendering difference(s)"
                : "Frame is inside the current AppKit compatibility profile",
              systemImage: diagnostics.isPartial
                ? "exclamationmark.triangle.fill" : "checkmark.shield.fill"
            )
            .foregroundStyle(diagnostics.isPartial ? .orange : .green)
            ForEach(Array(diagnostics.issues.prefix(4).enumerated()), id: \.offset) { _, issue in
              Text("Component \(issue.componentID): \(issue.reason)")
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
            }
          } else {
            Label(
              "Native frames are checked before AppKit creates or updates views",
              systemImage: "shield.lefthalf.filled"
            )
            .foregroundStyle(.secondary)
          }
          Text("16 MiB document · 20,000 nodes · depth 256 · 200,000 frame work units")
            .font(.caption2.monospaced())
            .foregroundStyle(.tertiary)
        }
        .padding(8)
      }

      GroupBox("Events") {
        VStack(alignment: .leading, spacing: 6) {
          HStack {
            Text(model.events.isEmpty ? "Event feed" : "Latest \(min(model.events.count, 4))")
              .font(.caption)
              .foregroundStyle(.secondary)
            Spacer()
            if !model.events.isEmpty {
              Button("Clear") { model.clearEvents() }
                .controlSize(.small)
            }
          }
          if model.events.isEmpty {
            Text("Actions and debug events will appear here.")
              .foregroundStyle(.secondary)
          } else {
            ForEach(Array(model.events.prefix(4))) { event in
              HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(event.timestamp, style: .time)
                  .font(.caption.monospacedDigit())
                  .foregroundStyle(.secondary)
                Text(event.summary)
                  .font(.callout.monospaced())
                  .lineLimit(1)
                Spacer()
                Text("\(event.renderer.title) · \(event.documentTitle)")
                  .font(.caption)
                  .foregroundStyle(.secondary)
                  .lineLimit(1)
              }
            }
          }
        }
        .padding(8)
      }

      if let error = model.errorMessage {
        Label(error, systemImage: "exclamationmark.triangle.fill")
          .foregroundStyle(.red)
          .textSelection(.enabled)
      }

      if let fontStatus = model.fontStatusMessage {
        Label(fontStatus, systemImage: "textformat")
          .foregroundStyle(.secondary)
          .textSelection(.enabled)
      }

      HStack {
        Spacer()
        Button(model.documentData == nil ? "Choose Document" : "Open Player Window") {
          model.render()
        }
        .keyboardShortcut(.defaultAction)
        .controlSize(.large)
      }
    }
    .padding(28)
    .frame(minWidth: 560, idealWidth: 620, minHeight: 650)
    .onDrop(of: ["public.file-url"], isTargeted: nil) { providers in
      guard let provider = providers.first else { return false }
      provider.loadDataRepresentation(forTypeIdentifier: "public.file-url") { data, _ in
        guard let data, let url = URL(dataRepresentation: data, relativeTo: nil) else { return }
        DispatchQueue.main.async { model.open(url) }
      }
      return true
    }
  }

  private var rendererBinding: Binding<DesktopRenderer> {
    Binding(get: { model.renderer }, set: { model.renderer = $0 })
  }

  private var nativeCompatibilityBinding: Binding<NativeMacCompatibility> {
    Binding(get: { model.nativeCompatibility }, set: { model.nativeCompatibility = $0 })
  }
}

struct DesktopPlayerSettingsView: View {
  @ObservedObject var model: DesktopPlayerModel

  var body: some View {
    Form {
      Picker("Preferred renderer", selection: rendererBinding) {
        ForEach(DesktopRenderer.allCases) { Text($0.title).tag($0) }
      }
      Text(model.renderer.detail)
        .font(.caption)
        .foregroundStyle(.secondary)
      Picker("Native compatibility", selection: nativeCompatibilityBinding) {
        ForEach(NativeMacCompatibility.allCases) { Text($0.title).tag($0) }
      }
      Text(model.nativeCompatibility.detail)
        .font(.caption)
        .foregroundStyle(.secondary)
      Toggle("Download Google Fonts", isOn: downloadableFontsBinding)
      Text("Enabled by default for both CMP and Native AppKit; unavailable fonts use the default face.")
        .font(.caption)
        .foregroundStyle(.secondary)
    }
    .padding(24)
    .frame(width: 440)
  }

  private var rendererBinding: Binding<DesktopRenderer> {
    Binding(get: { model.renderer }, set: { model.renderer = $0 })
  }

  private var nativeCompatibilityBinding: Binding<NativeMacCompatibility> {
    Binding(get: { model.nativeCompatibility }, set: { model.nativeCompatibility = $0 })
  }

  private var downloadableFontsBinding: Binding<Bool> {
    Binding(get: { model.downloadsGoogleFonts }, set: { model.downloadsGoogleFonts = $0 })
  }
}

@MainActor
final class RemoteComposeMacAppDelegate: NSObject, NSApplicationDelegate {
  let model = DesktopPlayerModel()
  private var mainWindow: NSWindow!
  private var settingsWindow: NSWindow?

  func applicationDidFinishLaunching(_ notification: Notification) {
    let controller = NSHostingController(rootView: DesktopPlayerView(model: model))
    mainWindow = NSWindow(contentViewController: controller)
    mainWindow.title = "Remote Compose Player"
    mainWindow.setContentSize(NSSize(width: 620, height: 680))
    mainWindow.center()
    mainWindow.makeKeyAndOrderFront(nil)
    configureMenu()

    if let argument = CommandLine.arguments.dropFirst().first, !argument.hasPrefix("-") {
      model.open(URL(fileURLWithPath: argument), renderImmediately: true)
    }
  }

  func application(_ sender: NSApplication, openFiles filenames: [String]) {
    guard let filename = filenames.first else { return }
    model.open(URL(fileURLWithPath: filename), renderImmediately: true)
    sender.reply(toOpenOrPrint: .success)
  }

  func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

  @objc private func openDocument(_ sender: Any?) { model.chooseDocument() }
  @objc private func showSettings(_ sender: Any?) {
    if settingsWindow == nil {
      let controller = NSHostingController(rootView: DesktopPlayerSettingsView(model: model))
      let window = NSWindow(contentViewController: controller)
      window.title = "Remote Compose Player Settings"
      window.styleMask = [.titled, .closable]
      settingsWindow = window
    }
    settingsWindow?.center()
    settingsWindow?.makeKeyAndOrderFront(nil)
  }

  private func configureMenu() {
    let main = NSMenu()
    let appItem = NSMenuItem(title: "Remote Compose Player", action: nil, keyEquivalent: "")
    let fileItem = NSMenuItem(title: "File", action: nil, keyEquivalent: "")
    main.addItem(appItem)
    main.addItem(fileItem)

    let appMenu = NSMenu()
    appMenu.addItem(
      withTitle: "About Remote Compose Player",
      action: #selector(NSApplication.orderFrontStandardAboutPanel(_:)), keyEquivalent: "")
    appMenu.addItem(.separator())
    let settingsItem = appMenu.addItem(
      withTitle: "Settings…", action: #selector(showSettings(_:)), keyEquivalent: ",")
    settingsItem.target = self
    appMenu.addItem(.separator())
    appMenu.addItem(
      withTitle: "Quit Remote Compose Player", action: #selector(NSApplication.terminate(_:)),
      keyEquivalent: "q")
    appItem.submenu = appMenu

    let fileMenu = NSMenu(title: "File")
    let openItem = fileMenu.addItem(
      withTitle: "Open…", action: #selector(openDocument(_:)), keyEquivalent: "o")
    openItem.target = self
    fileItem.submenu = fileMenu
    NSApplication.shared.mainMenu = main
  }
}

private final class BlockingResult<Value>: @unchecked Sendable {
  private let semaphore = DispatchSemaphore(value: 0)
  private var result: Result<Value, Error>?

  func complete(_ result: Result<Value, Error>) {
    self.result = result
    semaphore.signal()
  }

  func wait() -> Result<Value, Error> {
    semaphore.wait()
    return result!
  }
}

@main
struct RemoteComposeMacApplication {
  @MainActor
  static func main() {
    // Conformance capture: one process per gold rather than per frame.
    //
    // The conformance runner is a JVM process and this player is Swift, so the lane has to be
    // out-of-process. Launching the app per capture would pay AppKit startup for every frame in a
    // 252-gold corpus; a gold's timeline is short, so batching by gold turns thousands of launches
    // into hundreds. The job names its own frames so the runner can bind each PNG back to the step
    // that asked for it.
    if CommandLine.arguments.count == 3, CommandLine.arguments[1] == "--conformance-batch" {
      do {
        _ = NSApplication.shared
        let job = try JSONDecoder().decode(
          NativeConformanceJob.self,
          from: try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2])))
        guard let document = Data(base64Encoded: job.document) else {
          throw NativeConformanceJobError.malformed
        }
        var results: [[String: Any]] = []
        for request in job.frames {
          // The values this frame's checks assert. A gold asks for the handful it names rather than
          // for a whole dump, and the player resolves them at the frame's own instant.
          // The input the lane drove before this capture: the whole sequence, because the player
          // opens the document fresh and replays it.
          // A document this player refuses is a result, not a crash: the runner needs to report it
          // as a failing check for that frame rather than lose the whole gold.
          do {
            let frame = try NativeAppKitWindowController.renderFrame(
              data: document, timeSeconds: request.time, wallClock: request.wallClock,
              viewport: request.viewport, values: request.values, steps: request.steps)
            let path = URL(fileURLWithPath: job.output).appendingPathComponent(
              "\(request.id).png")
            try frame.png.write(to: path, options: .atomic)
            // The laid-out tree travels with the frame: the corpus's `tree` probe reads it, and
            // taking it from the same view the pixels came from keeps the two channels consistent.
            // The document's own values travel with it too, resolved at the same instant.
            var result: [String: Any] = [
              "id": request.id, "png": path.path, "tree": frame.tree,
            ]
             if let values = frame.values { result["values"] = values }
             if !frame.records.isEmpty { result["records"] = frame.records }
             if let inputHandled = frame.inputHandled { result["input_handled"] = inputHandled }
            results.append(result)
          } catch {
            results.append(["id": request.id, "error": "\(error)"])
          }
        }
        let encoded = try JSONSerialization.data(
          withJSONObject: ["frames": results], options: [.sortedKeys])
        FileHandle.standardOutput.write(encoded)
      } catch {
        FileHandle.standardError.write(Data("conformance batch failed: \(error)\n".utf8))
        exit(1)
      }
      return
    }
    if CommandLine.arguments.count == 4,
      ["--render-native-png", "--render-native-google-font-png"].contains(
        CommandLine.arguments[1])
    {
      do {
        _ = NSApplication.shared
        let input = URL(fileURLWithPath: CommandLine.arguments[2])
        let output = URL(fileURLWithPath: CommandLine.arguments[3])
        let data = try Data(contentsOf: input)
        var fonts: [String: RemoteComposeDownloadedFont] = [:]
        if CommandLine.arguments[1] == "--render-native-google-font-png" {
          let families = try NativeAppKitWindowController.downloadableFontFamilies(data: data)
          let blocking = BlockingResult<[String: RemoteComposeDownloadedFont]>()
          Task.detached {
            do {
              let resolver = RemoteComposeGoogleFontsResolver()
              var resolved: [String: RemoteComposeDownloadedFont] = [:]
              for family in families {
                resolved[family.lowercased()] = try await resolver.resolve(
                  RemoteComposeDownloadableFontRequest(family: family))
              }
              blocking.complete(.success(resolved))
            } catch {
              blocking.complete(.failure(error))
            }
          }
          fonts = try blocking.wait().get()
        }
        try NativeAppKitWindowController.renderPNG(data: data, downloadedFonts: fonts).write(
          to: output, options: .atomic)
        print(output.path)
      } catch {
        FileHandle.standardError.write(Data("native capture failed: \(error)\n".utf8))
        exit(1)
      }
      return
    }
    if CommandLine.arguments.count == 4,
      CommandLine.arguments[1] == "--measure-native-evidence"
    {
      do {
        _ = NSApplication.shared
        let input = URL(fileURLWithPath: CommandLine.arguments[2])
        let output = URL(fileURLWithPath: CommandLine.arguments[3])
        let report = try NativeAppKitWindowController.measureEvidence(
          data: try Data(contentsOf: input),
          fixture: input.deletingPathExtension().lastPathComponent)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(report).write(to: output, options: .atomic)
        guard report.passed else {
          FileHandle.standardError.write(
            Data("native AppKit evidence over budget: \(report.overBudget)\n".utf8))
          exit(1)
        }
        print(output.path)
      } catch {
        FileHandle.standardError.write(Data("native AppKit evidence failed: \(error)\n".utf8))
        exit(1)
      }
      return
    }
    if CommandLine.arguments.count == 2,
      CommandLine.arguments[1] == "--validate-native-scheduling-policy"
    {
      let continuous = NativeMacFrameDriverMode.resolve(
        needsContinuousFrames: true, requestsNextFrame: false, wakeAfter: nil,
        isActive: true, isVisible: true, reduceMotion: false)
      let reduced = NativeMacFrameDriverMode.resolve(
        needsContinuousFrames: true, requestsNextFrame: false, wakeAfter: nil,
        isActive: true, isVisible: true, reduceMotion: true)
      let functional = NativeMacFrameDriverMode.resolve(
        needsContinuousFrames: true, requestsNextFrame: true, wakeAfter: nil,
        isActive: true, isVisible: true, reduceMotion: true)
      let wake = NativeMacFrameDriverMode.resolve(
        needsContinuousFrames: false, requestsNextFrame: false, wakeAfter: 0.25,
        isActive: true, isVisible: true, reduceMotion: false)
      guard continuous == .displayLink, reduced == .idle, functional == .displayLink,
        wake == .wake(after: 0.25)
      else {
        FileHandle.standardError.write(Data("native scheduling policy failed\n".utf8))
        exit(1)
      }
      print("native scheduling policy display-link + reduce-motion + delayed-wake")
      return
    }
    if CommandLine.arguments.count == 2, CommandLine.arguments[1] == "--validate-native-events" {
      let action = NativeSwiftEvent.namedAction(name: "catalogAction", value: .none)
      let metadata = NativeSwiftEvent.namedAction(name: "catalogAction", value: .text("details"))
      guard nativeEventSummary(action) == "Named catalogAction: none",
        nativeEventSummary(metadata) == "Named catalogAction: details"
      else {
        FileHandle.standardError.write(Data("native event mapping failed\n".utf8))
        exit(1)
      }
      print("native event mapping action + metadata")
      return
    }
    if CommandLine.arguments.count == 2,
      CommandLine.arguments[1] == "--validate-native-safety-policy"
    {
      do {
        try NativeMacPolicy.validateDocument(Data())
        let oversized = Data(count: NativeMacPolicy.executionLimits.maximumDocumentBytes + 1)
        do {
          try NativeMacPolicy.validateDocument(oversized)
          throw DesktopValidationError("oversized document was accepted")
        } catch is RemoteComposeNativeLimitError {
          // Expected hard failure in compatible and strict modes.
        }
        let issue = RemoteComposeNativePlayerDiagnostic(
          severity: .unsupported, opcode: 19, operationName: "image", componentID: 1,
          reason: "test")
        let diagnostics = RemoteComposeNativePlayerDiagnostics(
          issues: [issue], unsupportedOpcodes: [19], notes: [])
        guard
          RemoteComposeNativeCompatibilityDecision.shouldRender(
            policy: .compatible, diagnostics: diagnostics),
          !RemoteComposeNativeCompatibilityDecision.shouldRender(
            policy: .strict, diagnostics: diagnostics)
        else { throw DesktopValidationError("compatibility decision was incorrect") }
        print("native safety policy document-limit + compatible + strict")
        return
      } catch {
        FileHandle.standardError.write(Data("native safety policy failed: \(error)\n".utf8))
        exit(1)
      }
    }
    if CommandLine.arguments.count == 3,
      [
        "--validate-native", "--validate-native-animation", "--validate-native-click-events",
        "--validate-native-policy",
      ]
      .contains(CommandLine.arguments[1])
    {
      do {
        let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2]))
        let session = try NativeSwiftDocumentSession.open(data: data)
        let snapshot = try session.snapshot(timeSeconds: 0, wallClock: .capture)
        switch CommandLine.arguments[1] {
        case "--validate-native-policy":
          let compatible = try NativeMacPolicy.evaluate(snapshot, compatibility: .compatible)
          do {
            _ = try NativeMacPolicy.evaluate(snapshot, compatibility: .strict)
            guard !compatible.diagnostics.isPartial else {
              throw DesktopValidationError("strict accepted a partial snapshot")
            }
          } catch RemoteComposeNativePlayerError.incompatible {
            guard compatible.diagnostics.isPartial else {
              throw DesktopValidationError("strict rejected a compatible snapshot")
            }
          }
          print(
            "native policy issues=\(compatible.diagnostics.issues.count), unsupported=\(compatible.diagnostics.unsupportedOpcodes.count)"
          )
        case "--validate-native-animation":
          guard
            snapshot.needsContinuousFrames
          else { throw DesktopValidationError("document does not request scheduled frames") }
          _ = try session.snapshot(timeSeconds: 0.25, wallClock: .capture)
          print(
            "native animation schedule continuous=\(snapshot.needsContinuousFrames)"
          )
        case "--validate-native-click-events":
          guard let componentID = firstClickableComponent(in: snapshot.root) else {
            throw DesktopValidationError("document has no clickable component")
          }
          guard let events = try session.click(componentID: componentID, timeSeconds: 0),
            !events.isEmpty
          else {
            throw DesktopValidationError("click produced no host events")
          }
          print("native click component=\(componentID), events=\(events.count)")
        default:
          print("native snapshot \(snapshot.width)x\(snapshot.height), root=\(snapshot.root.kind)")
        }
        return
      } catch {
        FileHandle.standardError.write(Data("native validation failed: \(error)\n".utf8))
        exit(1)
      }
    }
    let application = NSApplication.shared
    let delegate = RemoteComposeMacAppDelegate()
    application.delegate = delegate
    application.setActivationPolicy(.regular)
    application.activate(ignoringOtherApps: true)
    application.run()
  }

  private static func firstClickableComponent(in node: NativeSwiftNodeSnapshot) -> Int? {
    if node.isClickable || node.accessibility?.isClickable == true { return node.componentID }
    for child in node.children {
      if let result = firstClickableComponent(in: child) { return result }
    }
    return nil
  }
}

private struct DesktopValidationError: LocalizedError {
  let message: String
  init(_ message: String) { self.message = message }
  var errorDescription: String? { message }
}
