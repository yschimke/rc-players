#if canImport(UIKit)
  import Foundation
  import RcNativePlayerCore
  import Testing
  import UIKit

  @testable import RcNativePlayerUIKit

  /// The UIKit host resolves accessibility through the shared `NativeAccessibilityNode` policy:
  /// its document nodes exactly as the document core does, and its component views from what they
  /// display.
  @MainActor @Suite struct NativeUIKitAccessibilityTests {
    private func titleCard() throws -> NativeSwiftNodeSnapshot {
      let data = try NativeTestFixtures.data("TitleCardRemote-640x480.rc")
      return try NativeSwiftDocumentSession.open(data: data).snapshot().root
    }

    private func makeView(_ snapshot: NativeSwiftNodeSnapshot) -> NativeComponentView {
      NativeComponentView(
        node: NativeNode(swiftSnapshot: snapshot, densityBehavior: 0), images: [:],
        fontNames: [:], customComponents: RemoteComposeNativeCustomComponentRegistry(),
        onGesture: { _, _, _ in }, onCustomReturn: { _, _, _ in })
    }

    private func componentChildren(of view: UIView) -> [NativeComponentView] {
      view.subviews.compactMap { $0 as? NativeComponentView }
    }

    @Test func nodesResolveAsTheDocumentCoreDoes() throws {
      let snapshot = try titleCard()
      func compare(_ node: NativeNode, _ snapshot: NativeSwiftNodeSnapshot) {
        #expect(node.semanticBehavior == snapshot.semanticBehavior)
        #expect(node.resolvedSemanticLabel == snapshot.resolvedSemanticLabel)
        #expect(node.effectiveSemanticLabels == snapshot.effectiveSemanticLabels)
        #expect(node.children.count == snapshot.children.count)
        for (child, childSnapshot) in zip(node.children, snapshot.children) {
          compare(child, childSnapshot)
        }
      }
      compare(NativeNode(swiftSnapshot: snapshot, densityBehavior: 0), snapshot)
    }

    /// Before any container hides a child, the views display what the document says, so they
    /// resolve exactly as the document's nodes do.
    @Test func unlaidOutViewsResolveAsTheirNodes() throws {
      let snapshot = try titleCard()
      let node = NativeNode(swiftSnapshot: snapshot, densityBehavior: 0)
      func compare(_ view: NativeComponentView, _ node: NativeNode) {
        #expect(view.semanticBehavior == node.semanticBehavior)
        #expect(view.resolvedSemanticLabel == node.resolvedSemanticLabel)
        let children = componentChildren(of: view)
        #expect(children.count == node.children.count)
        for (child, childNode) in zip(children, node.children) { compare(child, childNode) }
      }
      compare(makeView(snapshot), node)
    }

    /// A component a container hides contributes nothing to its ancestors, whatever the document's
    /// own visibility field says: the policy reads the view, not the node.
    @Test func aHiddenComponentLeavesItsAncestorsLabels() throws {
      let root = makeView(try titleCard())
      #expect(!root.descendantSemanticLabels.isEmpty)
      for child in componentChildren(of: root) { child.isHidden = true }
      root.refreshSemanticElements()
      #expect(root.descendantSemanticLabels.isEmpty)
    }
  }
#endif
