import CoreGraphics
import Foundation
import Testing

@testable import RcNativePlayerCore
@testable import RcNativePlayerUIKit

/// The shared layout engine, driven through plain tree nodes and a fake text measurer: every
/// character is 10 points wide and every line 20 points tall, wrapping at the offered width.
///
/// The expectations are the geometry the UIKit renderer produced before the engine was extracted
/// (#399), worked through by hand from its rules.
@Suite struct NativeLayoutEngineTests {
  // MARK: - Linear layouts

  @Test func columnSplitsTheLeftoverBetweenWeights() {
    let root = item(
      .column, id: 1,
      children: [
        fixed(2, width: 40, height: 50),
        item(.box, id: 3) {
          $0.fillWidth()
          $0.heightType = NativeSwiftDimensionType.weight
          $0.heightValue = 1
        },
        item(.box, id: 4) {
          $0.fillWidth()
          $0.heightType = NativeSwiftDimensionType.weight
          $0.heightValue = 1
        },
      ])
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 100, height: 200))
    expectFrame(tree, 2, CGRect(x: 0, y: 0, width: 40, height: 50))
    // 200 less the fixed 50 leaves 150, split 1:1.
    expectFrame(tree, 3, CGRect(x: 0, y: 50, width: 100, height: 75))
    expectFrame(tree, 4, CGRect(x: 0, y: 125, width: 100, height: 75))
  }

  @Test func rowWeightsIgnoreSpacingAndCentreVertically() {
    let root = item(.row, id: 1) {
      $0.spacing = 10
      $0.verticalPositioning = NativeSwiftPositioning.center
    }
    root.layoutChildren = [
      fixed(2, width: 50, height: 20),
      item(.box, id: 3) {
        $0.widthType = NativeSwiftDimensionType.weight
        $0.widthValue = 1
        $0.heightType = NativeSwiftDimensionType.exact
        $0.heightValue = 40
      },
    ]
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 200, height: 100))
    // `spacedBy` is additive: the weight takes all 150 points the fixed child leaves, and the gap
    // is added during placement.
    expectFrame(tree, 2, CGRect(x: 0, y: 40, width: 50, height: 20))
    expectFrame(tree, 3, CGRect(x: 60, y: 30, width: 150, height: 40))
  }

  @Test func rowPlacesRightToLeft() {
    let root = item(
      .row, id: 1, children: [fixed(2, width: 20, height: 10), fixed(3, width: 30, height: 10)])
    let engine = NativeLayoutEngine(context: NativeLayoutContext(layoutDirection: .rightToLeft))
    let tree = engine.frameTree(root: root, size: CGSize(width: 100, height: 10))
    expectFrame(tree, 2, CGRect(x: 80, y: 0, width: 20, height: 10))
    expectFrame(tree, 3, CGRect(x: 50, y: 0, width: 30, height: 10))
  }

  @Test func structuralWrappersAreFlattenedIntoTheirParent() {
    let content = item(
      .content, id: 2, children: [fixed(3, width: 50, height: 20), fixed(4, width: 50, height: 20)])
    let root = item(.row, id: 1, children: [content])
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 200, height: 50))
    #expect(NativeLayoutEngine.isStructural(content.layoutNode))
    #expect(tree.frames.map(\.componentID) == [1, 2, 3, 4])
    expectFrame(tree, 2, CGRect(x: 0, y: 0, width: 200, height: 50))
    expectFrame(tree, 3, CGRect(x: 0, y: 0, width: 50, height: 20))
    expectFrame(tree, 4, CGRect(x: 50, y: 0, width: 50, height: 20))
  }

  @Test func marqueeRowLaysItsContentOutAtItsNaturalWidth() {
    func row(marquee: Bool) -> Item {
      item(.row, id: 1, children: [item(.box, id: 2, children: [fixed(3, width: 150, height: 20)])])
      { $0.marquee = marquee }
    }
    let size = CGSize(width: 100, height: 40)
    // Unbounded along x, as a horizontal scroll is: the wrap-content box keeps its 150 points past
    // the 100-point row, which the renderer clips and slides it under.
    let marquee = NativeLayoutEngine().frameTree(root: row(marquee: true), size: size)
    expectFrame(marquee, 2, CGRect(x: 0, y: 0, width: 150, height: 20))
    // Without the marquee the box is held to the row's 100.
    let plain = NativeLayoutEngine().frameTree(root: row(marquee: false), size: size)
    expectFrame(plain, 2, CGRect(x: 0, y: 0, width: 100, height: 20))
  }

  // MARK: - Text, fill and padding

  @Test func textWrapsWithinItsPaddedContentBox() {
    let text = item(.text, id: 2, text: String(repeating: "a", count: 25)) {
      $0.padding = NativeLayoutInsets(top: 2, left: 5, bottom: 2, right: 5)
    }
    let root = item(.column, id: 1, children: [text])
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 100, height: 300))
    // 90 points of content hold 9 characters a line, so 25 characters take 3 lines.
    expectFrame(tree, 2, CGRect(x: 0, y: 0, width: 100, height: 64))
    #expect(tree.frame(ofComponent: 2)?.contentFrame == CGRect(x: 5, y: 2, width: 90, height: 60))
  }

  @Test func wrapSizeIsTheTextsMeasurement() {
    let text = item(.text, id: 1, text: String(repeating: "a", count: 25))
    let engine = NativeLayoutEngine()
    #expect(
      engine.preferredSize(of: text, in: CGSize(width: 100, height: 300))
        == CGSize(width: 100, height: 60))
    #expect(
      engine.preferredSize(of: text, in: CGSize(width: 1000, height: 300))
        == CGSize(width: 250, height: 20))
  }

  @Test func fillChildrenCoverThePaddedBoxAndFractionsAlign() {
    let root = item(.box, id: 1) {
      $0.padding = NativeLayoutInsets(top: 10, left: 10, bottom: 10, right: 10)
      $0.horizontalPositioning = NativeSwiftPositioning.center
      $0.verticalPositioning = NativeSwiftPositioning.bottom
    }
    root.layoutChildren = [
      item(.box, id: 2) {
        $0.fillWidth()
        $0.fillHeight()
      },
      item(.box, id: 3) {
        $0.fillWidth(fraction: 0.5)
        $0.heightType = NativeSwiftDimensionType.exact
        $0.heightValue = 20
      },
    ]
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 200, height: 100))
    expectFrame(tree, 2, CGRect(x: 10, y: 10, width: 180, height: 80))
    expectFrame(tree, 3, CGRect(x: 55, y: 70, width: 90, height: 20))
  }

  @Test func paddingAndSpacingFollowTheLayoutUnitScale() {
    let root = item(
      .column, id: 1,
      children: [fixed(2, width: 10, height: 10), fixed(3, width: 10, height: 10)]
    ) {
      $0.padding = NativeLayoutInsets(top: 5, left: 5, bottom: 0, right: 0)
      $0.spacing = 4
    }
    let engine = NativeLayoutEngine(context: NativeLayoutContext(layoutUnitScale: 2))
    let tree = engine.frameTree(root: root, size: CGSize(width: 100, height: 100))
    expectFrame(tree, 2, CGRect(x: 10, y: 10, width: 10, height: 10))
    expectFrame(tree, 3, CGRect(x: 10, y: 28, width: 10, height: 10))
  }

  // MARK: - Collapsible

  @Test func collapsibleColumnHidesTheChildrenThatDoNotFit() {
    let root = item(
      .column, id: 1,
      children: [fixed(2, width: 100, height: 30), fixed(3, width: 100, height: 30)]
    ) { $0.isCollapsible = true }
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 100, height: 50))
    expectFrame(tree, 2, CGRect(x: 0, y: 0, width: 100, height: 30))
    #expect(tree.frame(ofComponent: 1)?.isHidden == false)
    #expect(tree.frame(ofComponent: 2)?.isHidden == false)
    #expect(tree.frame(ofComponent: 3)?.isHidden == true)
  }

  @Test func collapsibleColumnThatKeepsNothingIsGone() {
    let root = item(
      .column, id: 1,
      children: [fixed(2, width: 100, height: 30), fixed(3, width: 100, height: 30)]
    ) { $0.isCollapsible = true }
    let engine = NativeLayoutEngine()
    let tree = engine.frameTree(root: root, size: CGSize(width: 100, height: 20))
    #expect(tree.frames.allSatisfy { $0.isHidden })
    #expect(engine.preferredSize(of: root, in: CGSize(width: 100, height: 20)) == .zero)
  }

  @Test func descendantsOfAGoneContainerAreHidden() {
    let gone = item(
      .column, id: 2,
      children: [item(.row, id: 3, children: [fixed(4, width: 10, height: 10)])]
    ) { $0.visibility = NativeSwiftVisibility.gone }
    let root = item(.column, id: 1, children: [gone, fixed(5, width: 10, height: 10)])
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 100, height: 100))
    #expect(tree.frame(ofComponent: 1)?.isHidden == false)
    #expect(tree.frame(ofComponent: 2)?.isHidden == true)
    #expect(tree.frame(ofComponent: 3)?.isHidden == true)
    #expect(tree.frame(ofComponent: 4)?.isHidden == true)
    #expect(tree.frame(ofComponent: 5)?.isHidden == false)
  }

  // MARK: - Flow

  @Test func flowWrapsOntoFurtherLines() {
    let root = item(
      .row, id: 1,
      children: (2...4).map { fixed($0, width: 40, height: 20) }
    ) { $0.flowMaximumItems = 0 }
    let engine = NativeLayoutEngine()
    let tree = engine.frameTree(root: root, size: CGSize(width: 100, height: 100))
    expectFrame(tree, 2, CGRect(x: 0, y: 0, width: 40, height: 20))
    expectFrame(tree, 3, CGRect(x: 40, y: 0, width: 40, height: 20))
    expectFrame(tree, 4, CGRect(x: 0, y: 20, width: 40, height: 20))
    #expect(
      engine.preferredSize(of: root, in: CGSize(width: 100, height: 100))
        == CGSize(width: 80, height: 40))
  }

  @Test func flowDiscardsWhatItsLineCapCannotHold() {
    let root = item(
      .row, id: 1,
      children: (2...4).map { fixed($0, width: 40, height: 20) }
    ) {
      $0.flowMaximumItems = 0
      $0.flowMaximumLines = 1
    }
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 100, height: 100))
    #expect(tree.frame(ofComponent: 2)?.isHidden == false)
    #expect(tree.frame(ofComponent: 3)?.isHidden == false)
    #expect(tree.frame(ofComponent: 4)?.isHidden == true)
  }

  // MARK: - FitBox

  @Test func fitBoxShowsTheFirstAlternativeThatFits() {
    let root = item(
      .box, id: 1,
      children: [
        fixed(2, width: 150, height: 20),
        // An alternative's own visibility modifier is the document's switch, not the box's.
        fixed(3, width: 80, height: 20) { $0.visibility = NativeSwiftVisibility.gone },
      ]
    ) { $0.componentKind = "FitBoxLayout" }
    let engine = NativeLayoutEngine()
    let tree = engine.frameTree(root: root, size: CGSize(width: 100, height: 50))
    #expect(tree.frame(ofComponent: 1)?.isHidden == false)
    #expect(tree.frame(ofComponent: 2)?.isHidden == true)
    #expect(tree.frame(ofComponent: 3)?.isHidden == false)
    expectFrame(tree, 3, CGRect(x: 0, y: 0, width: 80, height: 20))
    #expect(
      engine.preferredSize(of: root, in: CGSize(width: 100, height: 50))
        == CGSize(width: 80, height: 20))
    let arrangement = engine.arrange(root, in: CGRect(x: 0, y: 0, width: 100, height: 50))
    #expect(arrangement.visibilityChanges.allSatisfy { $0.isFitBoxAlternative })
  }

  @Test func fitBoxWithNothingThatFitsIsGone() {
    let root = item(.box, id: 1, children: [fixed(2, width: 150, height: 20)]) {
      $0.componentKind = "FitBoxLayout"
    }
    let engine = NativeLayoutEngine()
    let tree = engine.frameTree(root: root, size: CGSize(width: 100, height: 50))
    #expect(tree.frame(ofComponent: 1)?.isHidden == true)
    #expect(engine.preferredSize(of: root, in: CGSize(width: 100, height: 50)) == .zero)
  }

  // MARK: - Measurement cache

  @Test func measurementsAreCachedPerConstraint() {
    let text = item(.text, id: 1, text: "abc")
    let engine = NativeLayoutEngine()
    _ = engine.preferredSize(of: text, in: CGSize(width: 100, height: 100))
    _ = engine.preferredSize(of: text, in: CGSize(width: 100, height: 100))
    #expect(text.measurements == 1)
    _ = engine.preferredSize(of: text, in: CGSize(width: 50, height: 100))
    #expect(text.measurements == 2)
    text.layoutCache.removeAll()
    _ = engine.preferredSize(of: text, in: CGSize(width: 100, height: 100))
    #expect(text.measurements == 3)
  }

  // MARK: - Layout computations

  @Test func boxRunsItsChildsLayoutComputations() {
    let child = fixed(2, width: 20, height: 10) {
      $0.layoutComputes = [Self.halfParentWidth(), Self.endAlignedWithInset(10)]
    }
    let root = item(.box, id: 1, children: [child]) {
      $0.componentKind = "BoxLayout"
      $0.horizontalPositioning = NativeSwiftPositioning.center
      $0.verticalPositioning = NativeSwiftPositioning.center
    }
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 200, height: 100))
    // Measured 20 wide, the measure computation makes it half the box's 200; centring puts it at
    // y 45, and the position computation moves x to 200 - 100 - 10.
    expectFrame(tree, 2, CGRect(x: 90, y: 45, width: 100, height: 10))
  }

  @Test func wrappingBoxSizesToTheComputedChild() {
    let child = fixed(2, width: 20, height: 10) { $0.layoutComputes = [Self.halfParentWidth()] }
    let box = item(.box, id: 1, children: [child]) { $0.componentKind = "BoxLayout" }
    let engine = NativeLayoutEngine()
    #expect(
      engine.preferredSize(of: box, in: CGSize(width: 160, height: 100))
        == CGSize(width: 80, height: 10))
  }

  @Test func layoutComputationsAreInertOutsideABox() {
    // The reference's row never calls `applyComputedLayout`.
    let child = fixed(2, width: 20, height: 10) {
      $0.layoutComputes = [Self.halfParentWidth(), Self.endAlignedWithInset(10)]
    }
    let root = item(.row, id: 1, children: [child]) { $0.componentKind = "RowLayout" }
    let tree = NativeLayoutEngine().frameTree(root: root, size: CGSize(width: 200, height: 100))
    expectFrame(tree, 2, CGRect(x: 0, y: 0, width: 20, height: 10))
  }

  @Test func canvasWithCanvasContentFillsAndSkipsComputations() {
    // `CanvasLayout` with a `CanvasContent` sizes every child to its content and never calls
    // `applyComputedLayout`; without one it lays its children out as `BoxLayout` does.
    func canvas(hasCanvasContent: Bool) -> Item {
      let child = fixed(2, width: 20, height: 10) {
        $0.layoutComputes = [Self.halfParentWidth(), Self.endAlignedWithInset(10)]
      }
      return item(.canvas, id: 1, children: [child]) {
        $0.componentKind = "CanvasLayout"
        $0.drawsContent = true
        $0.hasCanvasContent = hasCanvasContent
      }
    }
    let engine = NativeLayoutEngine()
    let size = CGSize(width: 200, height: 100)
    let filled = engine.frameTree(root: canvas(hasCanvasContent: true), size: size)
    expectFrame(filled, 2, CGRect(x: 0, y: 0, width: 200, height: 100))
    #expect(engine.preferredSize(of: canvas(hasCanvasContent: true), in: size).width == 20)
    let boxed = engine.frameTree(root: canvas(hasCanvasContent: false), size: size)
    expectFrame(boxed, 2, CGRect(x: 90, y: 0, width: 100, height: 10))
    #expect(engine.preferredSize(of: canvas(hasCanvasContent: false), in: size).width == 100)
  }

  @Test func canvasContentIsReadFromTheSnapshot() throws {
    typealias Op = NativeSwiftWireOpcode
    // root { canvas 2 { [content 5 {] canvas content 3 { rect } [}] } }, and a canvas holding a
    // plain content 4 instead.
    func document(nestInContent: Bool, canvasContent: Bool) -> Data {
      var words: [Int] = [0, 1, 0, 0, 100, 100, 0, 0]
      var bytes: [UInt8] = []
      func u8(_ value: Int) { bytes.append(UInt8(truncatingIfNeeded: value)) }
      func int(_ value: Int) {
        let raw = UInt32(bitPattern: Int32(value))
        for shift in [24, 16, 8, 0] { u8(Int(raw >> UInt32(shift))) }
      }
      u8(words.removeFirst())
      for word in words { int(word) }
      u8(Op.layoutRoot)
      int(1)
      u8(Op.layoutCanvas)
      int(2)
      int(-1)
      if nestInContent {
        u8(Op.layoutContent)
        int(5)
      }
      u8(canvasContent ? Op.layoutCanvasContent : Op.layoutContent)
      int(canvasContent ? 3 : 4)
      u8(Op.drawRect)
      for value: Float in [0, 0, 5, 5] { int(Int(Int32(bitPattern: value.bitPattern))) }
      u8(Op.containerEnd)
      if nestInContent { u8(Op.containerEnd) }
      u8(Op.containerEnd)
      u8(Op.containerEnd)
      return Data(bytes)
    }
    func canvasNode(_ data: Data) throws -> NativeLayoutNode {
      let root = try NativeSwiftDocumentSession.open(data: data).snapshot().root
      let canvas = try #require(root.children.first { $0.componentID == 2 })
      return NativeLayoutNode(snapshot: canvas)
    }
    #expect(try canvasNode(document(nestInContent: false, canvasContent: true)).hasCanvasContent)
    #expect(try canvasNode(document(nestInContent: true, canvasContent: true)).hasCanvasContent)
    #expect(
      try !canvasNode(document(nestInContent: false, canvasContent: false)).hasCanvasContent)
  }

  private static let boundsID = NativeSwiftIDRegion.array + 42

  private static func word(_ value: Float) -> UInt32 { value.bitPattern }

  private static func reference(_ id: Int) -> UInt32 { 0xff80_0000 | UInt32(id) }

  private static func bound(_ index: Float) -> [UInt32] {
    [
      reference(boundsID), word(index),
      NativeSwiftFloatExpression.operatorWord(NativeSwiftFloatOperator.arrayDeref),
    ]
  }

  /// Measure: width = parent width / 2.
  private static func halfParentWidth() -> NativeSwiftLayoutComputeSnapshot {
    let divide = NativeSwiftFloatExpression.operatorWord(NativeSwiftFloatOperator.div)
    return NativeSwiftLayoutComputeSnapshot(
      type: NativeSwiftLayoutComputeType.measure, boundsID: boundsID, animateChanges: false,
      steps: [
        .float(id: 50, words: bound(4) + [word(2), divide], offset: 0),
        .update(listID: boundsID, index: word(2), value: reference(50)),
      ],
      dynamicListIDs: [boundsID], lists: [:], values: [:])
  }

  /// Position: x = parent width - width - inset.
  private static func endAlignedWithInset(_ inset: Float) -> NativeSwiftLayoutComputeSnapshot {
    let subtract = NativeSwiftFloatExpression.operatorWord(NativeSwiftFloatOperator.sub)
    return NativeSwiftLayoutComputeSnapshot(
      type: NativeSwiftLayoutComputeType.position, boundsID: boundsID, animateChanges: false,
      steps: [
        .float(
          id: 51, words: bound(4) + bound(2) + [subtract, word(inset), subtract], offset: 0),
        .update(listID: boundsID, index: word(0), value: reference(51)),
      ],
      dynamicListIDs: [boundsID], lists: [:], values: [:])
  }

  // MARK: - Fixtures

  private final class Item: NativeLayoutItem {
    var layoutNode: NativeLayoutNode
    var layoutChildren: [Item]
    let layoutCache = NativeLayoutSizeCache()
    let text: String?
    private(set) var measurements = 0

    init(_ node: NativeLayoutNode, children: [Item], text: String?) {
      layoutNode = node
      layoutChildren = children
      self.text = text
    }

    /// The fake text measurer: 10 points a character, 20 a line.
    func layoutContentSize(fitting available: CGSize) -> CGSize {
      guard let text else { return .zero }
      measurements += 1
      let perLine = max(Int(available.width / 10), 1)
      let lines = (text.count + perLine - 1) / perLine
      return CGSize(width: CGFloat(min(text.count, perLine) * 10), height: CGFloat(lines * 20))
    }
  }

  private func item(
    _ kind: NativeLayoutNode.Kind, id: Int, text: String? = nil, children: [Item] = [],
    _ configure: (inout NativeLayoutNode) -> Void = { _ in }
  ) -> Item {
    var node = NativeLayoutNode(kind: kind)
    node.componentID = id
    node.drawsContent = text != nil
    configure(&node)
    return Item(node, children: children, text: text)
  }

  private func fixed(
    _ id: Int, width: CGFloat, height: CGFloat,
    _ configure: (inout NativeLayoutNode) -> Void = { _ in }
  ) -> Item {
    item(.box, id: id) {
      $0.widthType = NativeSwiftDimensionType.exact
      $0.widthValue = width
      $0.heightType = NativeSwiftDimensionType.exact
      $0.heightValue = height
      configure(&$0)
    }
  }

  private func expectFrame(
    _ tree: NativeLayoutFrameTree, _ componentID: Int, _ expected: CGRect,
    sourceLocation: SourceLocation = #_sourceLocation
  ) {
    guard let frame = tree.frame(ofComponent: componentID)?.frame else {
      Issue.record("component \(componentID) was not laid out", sourceLocation: sourceLocation)
      return
    }
    let close =
      abs(frame.minX - expected.minX) < 0.001 && abs(frame.minY - expected.minY) < 0.001
      && abs(frame.width - expected.width) < 0.001 && abs(frame.height - expected.height) < 0.001
    #expect(
      close, "component \(componentID): expected \(expected), got \(frame)",
      sourceLocation: sourceLocation)
  }
}

extension NativeLayoutNode {
  fileprivate mutating func fillWidth(fraction: CGFloat = 1) {
    widthType = NativeSwiftDimensionType.fill
    widthValue = fraction
  }

  fileprivate mutating func fillHeight(fraction: CGFloat = 1) {
    heightType = NativeSwiftDimensionType.fill
    heightValue = fraction
  }
}
