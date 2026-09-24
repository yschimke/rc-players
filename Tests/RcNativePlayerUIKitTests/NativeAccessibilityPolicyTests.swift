import Foundation
import RcNativePlayerCore
import Testing

#if canImport(AppKit) && !targetEnvironment(macCatalyst)
  import AppKit
#endif

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

/// A node for exercising the shared semantic resolution without a decoded document.
private struct TestSemanticNode: NativeAccessibilityNode {
  var semanticComponentID: Int
  var semanticDescriptor: NativeAccessibilityDescriptor?
  var semanticLocalLabels: [String] = []
  var semanticClickActionTypes: [NativeSwiftGestureKind] = []
  var isSemanticallyVisible = true
  var semanticChildren: [TestSemanticNode] = []
}

private func descriptor(
  _ role: NativeAccessibilityRole?,
  mode: NativeAccessibilityMode = .set,
  label: String? = nil,
  state: String? = nil,
  enabled: Bool = true,
  clickable: Bool = false
) -> NativeAccessibilityDescriptor {
  NativeAccessibilityDescriptor(
    role: role, mode: mode, contentDescription: label, text: nil, stateDescription: state,
    isEnabled: enabled, isClickable: clickable)
}

@Suite struct NativeAccessibilityNodeTests {
  @Test func mergingNodeTakesItsDescendantsRoleLabelAndAction() {
    let row = TestSemanticNode(
      semanticComponentID: 1,
      semanticDescriptor: descriptor(nil, mode: .merge),
      semanticChildren: [
        TestSemanticNode(
          semanticComponentID: 2,
          semanticDescriptor: descriptor(.button, label: "Open", clickable: true),
          semanticClickActionTypes: [.tap]),
        TestSemanticNode(semanticComponentID: 3, semanticLocalLabels: ["Settings"]),
        TestSemanticNode(
          semanticComponentID: 4, semanticLocalLabels: ["Hidden"], isSemanticallyVisible: false),
      ])
    let behavior = row.semanticBehavior
    #expect(behavior?.descriptor.elementKind == .button)
    #expect(behavior?.descriptor.hidesDescendants == true)
    #expect(behavior?.componentID == 2)
    #expect(behavior?.clickActionTypes == [NativeSwiftGestureKind.tap])
    #expect(behavior?.acceptsPointerAction == false)
    #expect(behavior?.activatesTap == true)
    #expect(row.resolvedSemanticLabel == "Open, Settings")
  }

  @Test func clearAndSetHidesDescendantLabels() {
    let image = TestSemanticNode(
      semanticComponentID: 1,
      semanticDescriptor: descriptor(.image, mode: .clearAndSet, label: "Runner"),
      semanticChildren: [TestSemanticNode(semanticComponentID: 2, semanticLocalLabels: ["x"])])
    #expect(image.resolvedSemanticLabel == "Runner")
    #expect(image.effectiveSemanticLabels == ["Runner"])
    #expect(image.semanticBehavior?.activatesTap == false)
  }

  @Test func disabledNodeDoesNotActivate() {
    let button = TestSemanticNode(
      semanticComponentID: 1,
      semanticDescriptor: descriptor(.button, label: "Pay", enabled: false, clickable: true),
      semanticClickActionTypes: [.tap])
    #expect(button.semanticBehavior?.activatesTap == false)
    #expect(button.resolvedSemanticLabel == "Pay")
  }

  @Test func nodeWithoutSemanticsHasNoElement() {
    let text = TestSemanticNode(semanticComponentID: 1, semanticLocalLabels: ["Title"])
    #expect(text.semanticBehavior == nil)
    #expect(text.resolvedSemanticLabel == nil)
    #expect(text.effectiveSemanticLabels == ["Title"])
  }

  @Test func decodedDocumentResolvesItsButton() throws {
    let data = try NativeTestFixtures.data("TitleCardRemote-640x480.rc")
    let root = try NativeSwiftDocumentSession.open(data: data).snapshot().root
    var behaviors: [NativeAccessibilityBehavior] = []
    func walk(_ node: NativeSwiftNodeSnapshot) {
      if let behavior = node.semanticBehavior { behaviors.append(behavior) }
      node.children.forEach(walk)
    }
    walk(root)
    // The fixture's call to action, which the UIKit evidence also counts as its one button.
    #expect(
      behaviors.contains { $0.descriptor.isClickable || $0.descriptor.elementKind == .button })
  }
}

#if canImport(AppKit) && !targetEnvironment(macCatalyst)
  @Suite struct NativeAppKitAccessibilityTests {
    @MainActor @Test func rolesFollowTheSharedElementKind() {
      typealias Mapping = NativeAppKitAccessibility
      #expect(Mapping.role(for: descriptor(.button)) == .button)
      #expect(Mapping.role(for: descriptor(.checkbox)) == .checkBox)
      #expect(Mapping.role(for: descriptor(.toggle)) == .checkBox)
      #expect(Mapping.subrole(for: .toggle)?.rawValue == "AXSwitch")
      #expect(Mapping.role(for: descriptor(.tab)) == .radioButton)
      #expect(Mapping.subrole(for: .tab)?.rawValue == "AXTabButton")
      #expect(Mapping.subrole(for: .button) == nil)
      #expect(Mapping.role(for: descriptor(.dropdownList)) == .popUpButton)
      #expect(Mapping.role(for: descriptor(.image)) == .image)
      #expect(Mapping.role(for: descriptor(.image, clickable: true)) == .button)
      #expect(Mapping.role(for: descriptor(nil)) == .staticText)
      #expect(Mapping.role(for: descriptor(nil, clickable: true)) == .button)
    }

    @MainActor @Test func identifiersMatchTheUIKitHost() {
      #expect(
        NativeAppKitAccessibility.identifier(for: descriptor(.button), componentID: 12)
          == "rc-native-button-12")
      #expect(
        NativeAppKitAccessibility.identifier(for: descriptor(.radioButton), componentID: 3)
          == "rc-native-radioButton-3")
    }

    @MainActor @Test func silentContainersAreNotPublished() {
      typealias Mapping = NativeAppKitAccessibility
      #expect(!Mapping.publishes(descriptor(nil), label: nil, activates: false))
      #expect(!Mapping.publishes(descriptor(.carousel), label: nil, activates: false))
      #expect(Mapping.publishes(descriptor(nil), label: "Title", activates: false))
      #expect(Mapping.publishes(descriptor(.picker, enabled: false), label: nil, activates: false))
      #expect(Mapping.publishes(descriptor(.button), label: nil, activates: false))
    }
  }
#endif
