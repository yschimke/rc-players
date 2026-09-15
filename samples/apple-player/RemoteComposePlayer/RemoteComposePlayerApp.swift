import SwiftUI

@main
struct RemoteComposePlayerApp: App {
  @State private var library = PlayerLibrary()

  var body: some Scene {
    WindowGroup {
      if ProcessInfo.processInfo.arguments.contains("--native-comparison") {
        NativeComparisonHarnessView()
      } else if ProcessInfo.processInfo.arguments.contains("--native-accessibility-ui-test") {
        NativeAccessibilityTestHost()
      } else if ProcessInfo.processInfo.arguments.contains("--native-evidence") {
        NativePlayerEvidenceView()
      } else {
        PlayerRootView(library: library)
      }
    }
    .commands {
      CommandGroup(replacing: .newItem) {
        Button("Open Remote Compose Document…") {
          library.isImporting = true
        }
        .keyboardShortcut("o")
      }

      CommandMenu("Playback") {
        Button("Reload Document") {
          library.reload()
        }
        .keyboardShortcut("r")

        Picker("Appearance", selection: $library.theme) {
          ForEach(PlayerAppearance.allCases) { appearance in
            Text(appearance.title).tag(appearance)
          }
        }
      }
    }
  }
}
