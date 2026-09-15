import Foundation

@main
enum NativeAccessibilityPolicyTests {
  static func main() {
    let roleKinds: [NativeAccessibilityElementKind] = [
      .button, .checkbox, .toggle, .radioButton, .tab, .image, .dropdownList, .picker,
      .carousel, .generic,
    ]
    precondition(
      (0...9).map { role in
        NativeAccessibilityDescriptor(
          role: NativeAccessibilityRole(rawValue: role), mode: .set,
          contentDescription: nil, text: nil, stateDescription: nil,
          isEnabled: true, isClickable: false
        ).elementKind
      } == roleKinds)

    let merged = NativeAccessibilityDescriptor(
      role: .toggle,
      mode: .merge,
      contentDescription: "Wi-Fi",
      text: "Wi-Fi",
      stateDescription: "Connected",
      isEnabled: true,
      isClickable: true)
    precondition(merged.elementKind == .toggle)
    precondition(merged.hidesDescendants)
    precondition(merged.resolvedLabel(descendantLabels: ["Network", "Network"]) == "Wi-Fi, Network")

    let cleared = NativeAccessibilityDescriptor(
      role: .image,
      mode: .clearAndSet,
      contentDescription: "Runner",
      text: nil,
      stateDescription: nil,
      isEnabled: false,
      isClickable: false)
    precondition(cleared.elementKind == .image)
    precondition(cleared.resolvedLabel(descendantLabels: ["hidden"]) == "Runner")

    let inferredButton = NativeAccessibilityDescriptor(
      role: nil,
      mode: .set,
      contentDescription: nil,
      text: nil,
      stateDescription: nil,
      isEnabled: true,
      isClickable: true)
    precondition(inferredButton.elementKind == .button)
    precondition(!inferredButton.hidesDescendants)
    precondition(inferredButton.resolvedLabel(descendantLabels: []) == nil)

    print("native UIKit accessibility policy tests: ok")
  }
}
