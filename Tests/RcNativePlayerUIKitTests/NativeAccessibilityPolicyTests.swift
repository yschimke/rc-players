import Foundation
import Testing

@testable import RcNativePlayerUIKit

@Suite struct NativeAccessibilityPolicyTests {
  @Test func accessibilityPolicy() {
    let roleKinds: [NativeAccessibilityElementKind] = [
      .button, .checkbox, .toggle, .radioButton, .tab, .image, .dropdownList, .picker,
      .carousel, .generic,
    ]
    let resolvedKinds = (0...9).map { role in
      NativeAccessibilityDescriptor(
        role: NativeAccessibilityRole(rawValue: role), mode: .set,
        contentDescription: nil, text: nil, stateDescription: nil,
        isEnabled: true, isClickable: false
      ).elementKind
    }
    #expect(resolvedKinds == roleKinds)

    let merged = NativeAccessibilityDescriptor(
      role: .toggle,
      mode: .merge,
      contentDescription: "Wi-Fi",
      text: "Wi-Fi",
      stateDescription: "Connected",
      isEnabled: true,
      isClickable: true)
    #expect(merged.elementKind == .toggle)
    #expect(merged.hidesDescendants)
    #expect(merged.resolvedLabel(descendantLabels: ["Network", "Network"]) == "Wi-Fi, Network")
    let mergedChild = merged.mergingBehavior(
      from: NativeAccessibilityDescriptor(
        role: .button, mode: .set, contentDescription: "Open", text: nil,
        stateDescription: "Expanded", isEnabled: true, isClickable: true))
    #expect(mergedChild.role == .toggle)
    #expect(mergedChild.isClickable)
    #expect(mergedChild.stateDescription == "Connected")

    let rolelessMerge = NativeAccessibilityDescriptor(
      role: nil, mode: .merge, contentDescription: nil, text: nil, stateDescription: nil,
      isEnabled: true, isClickable: false
    ).mergingBehavior(
      from: NativeAccessibilityDescriptor(
        role: .button, mode: .set, contentDescription: "Open", text: nil,
        stateDescription: "Expanded", isEnabled: true, isClickable: true))
    #expect(rolelessMerge.elementKind == .button)
    #expect(rolelessMerge.stateDescription == "Expanded")

    let unknownRoleMerge = NativeAccessibilityDescriptor(
      role: .unknown, mode: .merge, contentDescription: nil, text: nil, stateDescription: nil,
      isEnabled: true, isClickable: false
    ).mergingBehavior(
      from: NativeAccessibilityDescriptor(
        role: .button, mode: .set, contentDescription: "Open", text: nil,
        stateDescription: nil, isEnabled: true, isClickable: true))
    #expect(unknownRoleMerge.role == .button)
    #expect(unknownRoleMerge.elementKind == .button)

    let cleared = NativeAccessibilityDescriptor(
      role: .image,
      mode: .clearAndSet,
      contentDescription: "Runner",
      text: nil,
      stateDescription: nil,
      isEnabled: false,
      isClickable: false)
    #expect(cleared.elementKind == .image)
    #expect(cleared.resolvedLabel(descendantLabels: ["hidden"]) == "Runner")

    let inferredButton = NativeAccessibilityDescriptor(
      role: nil,
      mode: .set,
      contentDescription: nil,
      text: nil,
      stateDescription: nil,
      isEnabled: true,
      isClickable: true)
    #expect(inferredButton.elementKind == .button)
    #expect(inferredButton.hidesDescendants)
    #expect(
      inferredButton.resolvedLabel(descendantLabels: ["Morning run", "5.2 km · 28 min"])
        == "Morning run, 5.2 km · 28 min")
  }
}
