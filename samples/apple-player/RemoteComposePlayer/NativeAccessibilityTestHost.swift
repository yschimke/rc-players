import Foundation
import SwiftUI

struct NativeAccessibilityTestHost: View {
  var body: some View {
    if let url = Bundle.main.url(forResource: "TitleCardRemote-640x480", withExtension: "rc"),
      let data = try? Data(contentsOf: url)
    {
      RemoteComposeNativePlayerRepresentable(data: data)
        .ignoresSafeArea()
    } else {
      Text("Native accessibility fixture unavailable")
        .accessibilityIdentifier("native-accessibility-fixture-error")
    }
  }
}
