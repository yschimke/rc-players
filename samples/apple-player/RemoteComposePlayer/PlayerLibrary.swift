import Foundation
import Observation

enum PlayerAppearance: String, CaseIterable, Identifiable {
  case system
  case light
  case dark

  var id: Self { self }

  var title: String { rawValue.capitalized }

}

enum PlayerRenderer: String, CaseIterable, Identifiable {
  case compose = "CMP"
  case native = "Native POC"

  var id: Self { self }
}

struct PlayerDocument: Identifiable, Hashable {
  let id: UUID
  let title: String
  let subtitle: String
  let data: Data
  let sourceURL: URL?
  let preferredRenderer: PlayerRenderer?

  init(
    id: UUID = UUID(), title: String, subtitle: String, data: Data, sourceURL: URL? = nil,
    preferredRenderer: PlayerRenderer? = nil
  ) {
    self.id = id
    self.title = title
    self.subtitle = subtitle
    self.data = data
    self.sourceURL = sourceURL
    self.preferredRenderer = preferredRenderer
  }
}

@Observable
@MainActor
final class PlayerLibrary {
  var documents: [PlayerDocument] = []
  var selection: PlayerDocument.ID? {
    didSet {
      if let preferred = selectedDocument?.preferredRenderer { renderer = preferred }
    }
  }
  var theme: PlayerAppearance = .system
  var renderer: PlayerRenderer = .compose
  var background: RemoteComposePlayerBackground = .opaque
  var zoom = 1.0
  var isImporting = false
  var errorMessage: String?
  private(set) var revision = 0

  init() {
    if ProcessInfo.processInfo.arguments.contains("--native-player") {
      renderer = .native
    }
    let fixtures: [(String, String, String, PlayerRenderer?)] = [
      ("Swift controls", "Text field + slider document bindings", "SwiftControls", .native),
      ("Swift pulse", "Document-driven PhaseAnimator", "SwiftPulse", .native),
      ("Swift chart", "Interactive Swift Charts selection", "SwiftChart", .native),
      ("Title card", "Typography and card layout", "TitleCardRemote-640x480", nil),
      ("Progress", "Animated circular progress", "IndeterminateCircularProgress-400x400", nil),
      ("Image button", "Bitmap-backed component", "ImageBackgroundRemoteButton-454x200", nil),
      ("Circular progress", "Material progress fixture", "CircularProgressRemote-384x384", nil),
      ("Arc progress", "Curved progress fixture", "ArcProgressRemote-454x400", nil),
    ]

    documents = fixtures.compactMap { title, subtitle, resource, preferredRenderer in
      guard let url = Bundle.main.url(forResource: resource, withExtension: "rc"),
        let data = try? Data(contentsOf: url)
      else { return nil }
      return PlayerDocument(
        title: title, subtitle: subtitle, data: data, preferredRenderer: preferredRenderer)
    }
    let fixtureTitle = ProcessInfo.processInfo.arguments
      .first { $0.hasPrefix("--fixture=") }?
      .dropFirst("--fixture=".count)
    selection =
      fixtureTitle.flatMap { requested in
        documents.first { $0.title.caseInsensitiveCompare(String(requested)) == .orderedSame }?.id
      } ?? documents.first?.id
    if let preferred = selectedDocument?.preferredRenderer { renderer = preferred }
  }

  var selectedDocument: PlayerDocument? {
    documents.first { $0.id == selection }
  }

  func open(_ url: URL, replacing documentID: PlayerDocument.ID? = nil) {
    guard url.pathExtension.lowercased() == "rc" else {
      errorMessage = "\(url.lastPathComponent) isn’t a Remote Compose (.rc) document."
      return
    }

    let hasAccess = url.startAccessingSecurityScopedResource()
    defer {
      if hasAccess { url.stopAccessingSecurityScopedResource() }
    }

    do {
      let data = try Data(contentsOf: url)
      let document = PlayerDocument(
        id: documentID ?? UUID(),
        title: url.deletingPathExtension().lastPathComponent,
        subtitle: ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file),
        data: data,
        sourceURL: url
      )
      if let documentID, let index = documents.firstIndex(where: { $0.id == documentID }) {
        documents[index] = document
        revision += 1
      } else {
        documents.insert(document, at: 0)
      }
      selection = document.id
      errorMessage = nil
    } catch {
      errorMessage = "Couldn’t open \(url.lastPathComponent): \(error.localizedDescription)"
    }
  }

  func reload() {
    guard let selectedDocument, let url = selectedDocument.sourceURL else {
      revision += 1
      return
    }
    open(url, replacing: selectedDocument.id)
  }
}
