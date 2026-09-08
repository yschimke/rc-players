import SwiftUI
import UniformTypeIdentifiers

extension UTType {
  static let remoteCompose = UTType(
    importedAs: "ee.schimke.remote-compose-document", conformingTo: .data)
}

struct PlayerRootView: View {
  @Bindable var library: PlayerLibrary
  @State private var inspectorPresented = true

  var body: some View {
    NavigationSplitView {
      List(selection: $library.selection) {
        Section("Library") {
          ForEach(library.documents) { document in
            Label {
              VStack(alignment: .leading, spacing: 2) {
                Text(document.title)
                  .fontWeight(.medium)
                Text(document.subtitle)
                  .font(.caption)
                  .foregroundStyle(.secondary)
              }
            } icon: {
              Image(systemName: "rectangle.on.rectangle.angled")
                .foregroundStyle(.indigo)
            }
            .tag(document.id)
          }
        }
      }
      .navigationTitle("Remote Compose")
    } detail: {
      ZStack {
        AuroraBackdrop()

        if let document = library.selectedDocument {
          PlayerCanvas(document: document, library: library)
            .id("\(document.id)-\(library.theme.rawValue)-\(library.revision)")
        } else {
          ContentUnavailableView(
            "No Document Selected",
            systemImage: "rectangle.dashed",
            description: Text("Choose a document from the library or open a .rc file.")
          )
        }
      }
      .navigationTitle(library.selectedDocument?.title ?? "Player")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItemGroup(placement: .primaryAction) {
          Button("Open", systemImage: "folder") {
            library.isImporting = true
          }
          .keyboardShortcut("o")

          Button("Reload", systemImage: "arrow.clockwise") {
            library.reload()
          }
          .keyboardShortcut("r")
        }
      }
    }
    .navigationSplitViewStyle(.balanced)
    .fileImporter(
      isPresented: $library.isImporting,
      allowedContentTypes: [.remoteCompose, .data],
      allowsMultipleSelection: false
    ) { result in
      if case .success(let urls) = result, let url = urls.first {
        library.open(url)
      } else if case .failure(let error) = result {
        library.errorMessage = error.localizedDescription
      }
    }
    .alert(
      "Remote Compose couldn’t play this document",
      isPresented: Binding(
        get: { library.errorMessage != nil },
        set: { if !$0 { library.errorMessage = nil } }
      )
    ) {
      Button("OK", role: .cancel) {}
    } message: {
      Text(library.errorMessage ?? "Unknown error")
    }
    .onDrop(of: [.fileURL], isTargeted: nil) { providers in
      guard let provider = providers.first else { return false }
      provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { item, _ in
        let url: URL?
        if let data = item as? Data {
          url = URL(dataRepresentation: data, relativeTo: nil)
        } else {
          url = item as? URL
        }
        if let url {
          Task { @MainActor in library.open(url) }
        }
      }
      return true
    }
  }
}

private struct PlayerCanvas: View {
  let document: PlayerDocument
  @Bindable var library: PlayerLibrary

  var body: some View {
    GeometryReader { proxy in
      ZStack(alignment: .bottom) {
        ScrollView([.horizontal, .vertical]) {
          RemoteComposePlayerView(
            document: document,
            theme: library.theme,
            onError: { message in
              Task { @MainActor in library.errorMessage = message }
            }
          )
          .frame(
            width: max(320, min(720, proxy.size.width - 96)),
            height: max(320, min(720, proxy.size.height - 120))
          )
          .scaleEffect(library.zoom)
          .padding(80)
        }
        .scrollIndicators(.hidden)

        PlaybackChrome(library: library)
          .padding(.bottom, 22)
      }
    }
  }
}

private struct PlaybackChrome: View {
  @Bindable var library: PlayerLibrary

  var body: some View {
    GlassEffectContainer(spacing: 14) {
      HStack(spacing: 12) {
        Picker("Appearance", selection: $library.theme) {
          ForEach(PlayerAppearance.allCases) { appearance in
            Image(systemName: appearance.symbol)
              .tag(appearance)
          }
        }
        .pickerStyle(.segmented)
        .frame(width: 152)

        Divider()
          .frame(height: 22)

        Image(systemName: "minus.magnifyingglass")
          .foregroundStyle(.secondary)
        Slider(value: $library.zoom, in: 0.5...1.5, step: 0.05)
          .frame(width: 150)
        Image(systemName: "plus.magnifyingglass")
          .foregroundStyle(.secondary)

        Button("Actual Size", systemImage: "1.magnifyingglass") {
          withAnimation(.snappy) { library.zoom = 1 }
        }
        .labelStyle(.iconOnly)
        .buttonStyle(.glass)
      }
      .padding(.leading, 18)
      .padding(.trailing, 8)
      .padding(.vertical, 8)
      .glassEffect(.regular.interactive(), in: Capsule())
    }
  }
}

extension PlayerAppearance {
  fileprivate var symbol: String {
    switch self {
    case .system: "circle.lefthalf.filled"
    case .light: "sun.max"
    case .dark: "moon.stars"
    }
  }
}

private struct AuroraBackdrop: View {
  var body: some View {
    TimelineView(.animation(minimumInterval: 1 / 24)) { timeline in
      let phase = timeline.date.timeIntervalSinceReferenceDate.remainder(dividingBy: 18) / 18
      Canvas { context, size in
        context.fill(
          Path(CGRect(origin: .zero, size: size)),
          with: .linearGradient(
            Gradient(colors: [
              Color(red: 0.04, green: 0.06, blue: 0.12), Color(red: 0.10, green: 0.08, blue: 0.20),
            ]),
            startPoint: .zero,
            endPoint: CGPoint(x: size.width, y: size.height)
          ))

        context.addFilter(.blur(radius: 70))
        let blobs: [(Color, CGFloat, CGFloat)] = [
          (.indigo.opacity(0.72), 0.23, 0.28),
          (.cyan.opacity(0.48), 0.76, 0.32),
          (.purple.opacity(0.55), 0.58, 0.78),
        ]
        for (index, blob) in blobs.enumerated() {
          let angle = phase * .pi * 2 + Double(index) * 2.1
          let center = CGPoint(
            x: size.width * blob.1 + cos(angle) * size.width * 0.09,
            y: size.height * blob.2 + sin(angle) * size.height * 0.10
          )
          let radius = min(size.width, size.height) * 0.34
          context.fill(
            Path(
              ellipseIn: CGRect(
                x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
            with: .color(blob.0)
          )
        }
      }
    }
    .ignoresSafeArea()
  }
}
