import RcComposePlayer
import SwiftUI
import UIKit

struct RemoteComposePlayerView: UIViewControllerRepresentable {
  let document: PlayerDocument
  let theme: PlayerAppearance
  let onError: (String) -> Void

  func makeUIViewController(context: Context) -> UIViewController {
    RcComposeViewControllerKt.RcComposeViewController(
      bytes: KotlinByteArray(bytes: document.data),
      theme: theme.rcTheme,
      onEvent: { _ in },
      typefaces: RcTypefaceLoaderCompanion.shared.Default,
      onError: onError,
      lenient: true
    )
  }

  func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
