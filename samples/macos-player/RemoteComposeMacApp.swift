import AppKit
import RcComposePlayer
import SwiftUI
import UniformTypeIdentifiers

enum DesktopRenderer: String, CaseIterable, Identifiable {
  case compose = "cmp"
  case native = "native"

  var id: Self { self }
  var title: String { self == .compose ? "CMP" : "Native AppKit POC" }
  var detail: String {
    self == .compose
      ? "The production Compose Multiplatform renderer."
      : "A Swift-native AppKit hierarchy backed by the shared snapshot bridge."
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
  @Published private(set) var events: [DesktopPlayerEvent] = []

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
    do {
      switch renderer {
      case .compose:
        let bytes = RcDataBridgeKt.rcByteArray(data: documentData)
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
          typefaces: RcTypefaceLoaderCompanion.shared.Default,
          onError: { [weak self] message in
            DispatchQueue.main.async { self?.errorMessage = message }
          },
          lenient: true)
      case .native:
        try NativeAppKitWindowController.shared.open(
          data: documentData, title: title,
          onEvent: { [weak self] summary in
            self?.record(renderer: .native, documentTitle: title, summary: summary)
          },
          onError: { [weak self] message in self?.errorMessage = message })
      }
    } catch {
      errorMessage = "Could not render \(title): \(error.localizedDescription)"
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
    .frame(minWidth: 560, idealWidth: 620, minHeight: 500)
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
    }
    .padding(24)
    .frame(width: 440)
  }

  private var rendererBinding: Binding<DesktopRenderer> {
    Binding(get: { model.renderer }, set: { model.renderer = $0 })
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
    mainWindow.setContentSize(NSSize(width: 620, height: 520))
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

@main
struct RemoteComposeMacApplication {
  @MainActor
  static func main() {
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
      let action = RcNativeEvent(
        kind: 0, actionId: 77, name: nil, textValue: nil, floatValue: 0, integerValue: 0,
        floatListValue: [])
      let metadata = RcNativeEvent(
        kind: 1, actionId: 78, name: nil, textValue: "details", floatValue: 0,
        integerValue: 0, floatListValue: [])
      guard nativeEventSummary(action) == "Action 77",
        nativeEventSummary(metadata) == "Action 78: details"
      else {
        FileHandle.standardError.write(Data("native event mapping failed\n".utf8))
        exit(1)
      }
      print("native event mapping action + metadata")
      return
    }
    if CommandLine.arguments.count == 3,
      ["--validate-native", "--validate-native-animation", "--validate-native-click-events"]
        .contains(CommandLine.arguments[1])
    {
      do {
        let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2]))
        let session = try RcNativeSnapshotBridge.shared.createSession(
          bytes: RcDataBridgeKt.rcByteArray(data: data))
        let snapshot = try session.snapshot(timeSeconds: 0)
        switch CommandLine.arguments[1] {
        case "--validate-native-animation":
          guard
            snapshot.needsContinuousFrames || snapshot.requestsNextFrame
              || snapshot.wakeAfterSeconds >= 0
          else { throw DesktopValidationError("document does not request scheduled frames") }
          _ = try session.snapshot(timeSeconds: 0.25)
          print(
            "native animation schedule continuous=\(snapshot.needsContinuousFrames), next=\(snapshot.requestsNextFrame), wake=\(snapshot.wakeAfterSeconds)"
          )
        case "--validate-native-click-events":
          guard let componentID = firstClickableComponent(in: snapshot.root) else {
            throw DesktopValidationError("document has no clickable component")
          }
          let update = try session.click(componentId: componentID, timeSeconds: 0)
          guard !update.events.isEmpty else {
            throw DesktopValidationError("click produced no host events")
          }
          print("native click component=\(componentID), events=\(update.events.count)")
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

  private static func firstClickableComponent(in node: RcNativeNodeSnapshot) -> Int32? {
    if node.clickable { return node.componentId }
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
