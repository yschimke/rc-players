import SwiftUI

@MainActor
enum NativeCustomComponentSamples {
  static let registry =
    RemoteComposeNativeCustomComponentRegistry()
    .registerSwiftUI("demo:EditableText") { component in
      NativeEditableText(component: component)
    }
}

private struct NativeEditableText: View {
  @ObservedObject var component: RemoteComposeNativeCustomComponent
  @State private var editing: String

  private static let text = RemoteComposeNativeTextProperty(1)
  private static let textReturn = RemoteComposeNativeTextReturnProperty(2)
  private static let textColor = RemoteComposeNativeColorProperty(3, default: 0xff20_2124)

  init(component: RemoteComposeNativeCustomComponent) {
    self.component = component
    _editing = State(initialValue: component.text(Self.text))
  }

  var body: some View {
    TextField("Document text", text: $editing)
      .textFieldStyle(.roundedBorder)
      .foregroundStyle(Color(argb: component.color(Self.textColor)))
      .padding(.horizontal, 8)
      .padding(.vertical, 4)
      .onChange(of: editing) { _, value in
        _ = component.send(value, to: Self.textReturn)
      }
      .onChange(of: component.text(Self.text)) { _, value in
        if editing != value { editing = value }
      }
  }
}

extension Color {
  fileprivate init(argb: UInt32) {
    self.init(
      .sRGB,
      red: Double((argb >> 16) & 0xff) / 255,
      green: Double((argb >> 8) & 0xff) / 255,
      blue: Double(argb & 0xff) / 255,
      opacity: Double((argb >> 24) & 0xff) / 255)
  }
}
