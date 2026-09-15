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

@MainActor
final class DesktopPlayerModel: ObservableObject {
  @Published var documentURL: URL?
  @Published var documentData: Data?
  @Published var errorMessage: String?

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
          onEvent: { _ in },
          typefaces: RcTypefaceLoaderCompanion.shared.Default,
          onError: { [weak self] message in
            DispatchQueue.main.async { self?.errorMessage = message }
          },
          lenient: true)
      case .native:
        try NativeAppKitWindowController.shared.open(data: documentData, title: title)
      }
    } catch {
      errorMessage = "Could not render \(title): \(error.localizedDescription)"
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
    .frame(minWidth: 560, idealWidth: 620, minHeight: 420)
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
    mainWindow.setContentSize(NSSize(width: 620, height: 440))
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
    if CommandLine.arguments.count == 3, CommandLine.arguments[1] == "--validate-native" {
      do {
        let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2]))
        let session = try RcNativeSnapshotBridge.shared.createSession(
          bytes: RcDataBridgeKt.rcByteArray(data: data))
        let snapshot = try session.snapshot(timeSeconds: 0)
        print("native snapshot \(snapshot.width)x\(snapshot.height), root=\(snapshot.root.kind)")
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
}
