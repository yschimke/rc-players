import Foundation
import Observation
import RcComposePlayer

enum PlayerAppearance: String, CaseIterable, Identifiable {
  case system
  case light
  case dark

  var id: Self { self }

  var title: String { rawValue.capitalized }

  var rcTheme: RcPlayerTheme {
    switch self {
    case .system: .system
    case .light: .light
    case .dark: .dark
    }
  }
}

struct PlayerDocument: Identifiable, Hashable {
  let id: UUID
  let title: String
  let subtitle: String
  let data: Data
  let sourceURL: URL?

  init(title: String, subtitle: String, data: Data, sourceURL: URL? = nil) {
    id = UUID()
    self.title = title
    self.subtitle = subtitle
    self.data = data
    self.sourceURL = sourceURL
  }
}

@Observable
@MainActor
final class PlayerLibrary {
  var documents: [PlayerDocument] = []
  var selection: PlayerDocument.ID?
  var theme: PlayerAppearance = .system
  var zoom = 1.0
  var isImporting = false
  var errorMessage: String?
  private(set) var revision = 0

  init() {
    let fixtures = [
      ("Title card", "Typography and card layout", "TitleCardRemote-640x480"),
      ("Progress", "Animated circular progress", "IndeterminateCircularProgress-400x400"),
      ("Image button", "Bitmap-backed component", "ImageBackgroundRemoteButton-454x200"),
      ("Circular progress", "Material progress fixture", "CircularProgressRemote-384x384"),
      ("Arc progress", "Curved progress fixture", "ArcProgressRemote-454x400"),
    ]

    documents = fixtures.compactMap { title, subtitle, resource in
      guard let url = Bundle.main.url(forResource: resource, withExtension: "rc"),
        let data = try? Data(contentsOf: url)
      else { return nil }
      return PlayerDocument(title: title, subtitle: subtitle, data: data)
    }
    selection = documents.first?.id
  }

  var selectedDocument: PlayerDocument? {
    documents.first { $0.id == selection }
  }

  func open(_ url: URL) {
    let hasAccess = url.startAccessingSecurityScopedResource()
    defer {
      if hasAccess { url.stopAccessingSecurityScopedResource() }
    }

    do {
      let data = try Data(contentsOf: url)
      let document = PlayerDocument(
        title: url.deletingPathExtension().lastPathComponent,
        subtitle: ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file),
        data: data,
        sourceURL: url
      )
      documents.insert(document, at: 0)
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
    open(url)
  }
}

extension KotlinByteArray {
  convenience init(bytes: Data) {
    self.init(size: Int32(bytes.count))
    for (offset, byte) in bytes.enumerated() {
      set(index: Int32(offset), value: Int8(bitPattern: byte))
    }
  }
}
