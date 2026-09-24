import Foundation

/// Which children a collapsible container keeps.
///
/// A `CollapsibleColumnLayout` (233) or `CollapsibleRowLayout` (230) measures its children and hides
/// the ones that do not fit, dropping them in the order their `CollapsiblePriority` modifiers give.
/// This is the decision, separated from either renderer so it can be tested without a view hierarchy
/// — and so the UIKit and AppKit paths cannot disagree about it.
///
/// The rules are the reference's, including the two that are easy to get backwards:
///
/// * a child with **no** priority modifier sorts ahead of every child that has one, because upstream
///   orders by descending priority with `Float.greatestFiniteMagnitude` as the absent value;
/// * a child with a **weight** on the container's axis does not count against the available space,
///   so it is never the reason another child is dropped.
/// How a flow container's children divide into lines.
///
/// The reference segments a `FlowLayout` by walking its children and starting a new line when the
/// next one no longer fits. A child with a **weight** has no measured width yet — it takes its
/// line's leftover — so it contributes its `widthIn` *minimum* instead, which is what stops a
/// weighted child's minimum from being quietly ignored when its siblings are placed beside it.
/// A child the document marked GONE contributes nothing.
///
/// Kept here rather than in either renderer so the two cannot disagree about where a line breaks,
/// and so the rule is testable without a view hierarchy.
public enum NativeSwiftFlow {
  public struct Child: Sendable, Equatable {
    /// The child's measured width. Ignored when `weight` is positive.
    public let measuredWidth: Float
    /// The child's width weight, 0 when unweighted.
    public let weight: Float
    /// The child's `widthIn` minimum, 0 when it declares none.
    public let minimumWidth: Float
    public let isGone: Bool

    public init(
      measuredWidth: Float, weight: Float = 0, minimumWidth: Float = 0, isGone: Bool = false
    ) {
      self.measuredWidth = measuredWidth
      self.weight = weight
      self.minimumWidth = minimumWidth
      self.isGone = isGone
    }

    /// What this child contributes to the line it is placed on.
    var provisionalWidth: Float {
      if isGone { return 0 }
      if weight > 0 { return max(minimumWidth, 0) }
      return max(measuredWidth, 0)
    }
  }

  /// The line each child lands on, in document order, plus the children a maximum-line cap
  /// discarded.
  public static func segment(
    _ children: [Child], available: Float, spacing: Float = 0, maximumItems: Int = 0,
    maximumLines: Int = 0
  ) -> (lines: [[Int]], discarded: [Int]) {
    var lines: [[Int]] = [[]]
    var discarded: [Int] = []
    var width: Float = 0
    var capped = false
    let gap = spacing.isFinite && spacing > 0 ? spacing : 0
    let itemCap = maximumItems > 0 ? maximumItems : Int.max
    let lineCap = maximumLines > 0 ? maximumLines : Int.max
    for (index, child) in children.enumerated() {
      // A GONE child is in neither the line nor its spacing or item count: it is not drawn, so
      // letting it occupy a slot would push a visible sibling onto a line of its own.
      if child.isGone { continue }
      if capped {
        discarded.append(index)
        continue
      }
      let provisional = child.provisionalWidth
      let current = lines[lines.count - 1]
      let wraps =
        !current.isEmpty
        && (current.count >= itemCap
          || width + gap + provisional > available)
      if wraps {
        guard lines.count < lineCap else {
          // No further line may be created, so this child and every child after it is discarded.
          // Letting a later, smaller one land on the current line would reorder the document.
          capped = true
          discarded.append(index)
          continue
        }
        lines.append([])
        width = 0
      }
      if !lines[lines.count - 1].isEmpty { width += gap }
      lines[lines.count - 1].append(index)
      width += provisional
    }
    return (lines, discarded)
  }
}

public enum NativeSwiftCollapsible {
  /// One child as the container sees it.
  public struct Child: Sendable, Equatable {
    /// The child's size along the container's axis, as measured. Ignored when `weight` is positive,
    /// matching the reference, which does not measure a weighted child until the kept set is known.
    public let mainSize: Float
    /// The child's weight on the container's axis, 0 when unweighted.
    public let weight: Float
    /// The priority the child declared for this axis, or nil when it declared none.
    public let priority: Float?
    /// True when the document itself marked the child GONE. Such a child is never kept and never
    /// consumes space.
    public let isGone: Bool

    public init(
      mainSize: Float, weight: Float = 0, priority: Float? = nil, isGone: Bool = false
    ) {
      self.mainSize = mainSize
      self.weight = weight
      self.priority = priority
      self.isGone = isGone
    }
  }

  /// Whether a child's main axis is measured unbounded when the fit test asks for its natural size.
  ///
  /// A fixed or wrapping child has a natural size worth measuring; a **fill** child does not — it
  /// takes whatever it is given, so measuring it unbounded resolves it to infinity and the fit test
  /// then drops it even when it is the container's only child. Fill dimensions keep the container's
  /// own bound instead.
  public static func measuresUnbounded(mainAxisType: Int) -> Bool {
    !(mainAxisType == NativeSwiftDimensionType.fill
      || mainAxisType == NativeSwiftDimensionType.fillParentMaxWidth
      || mainAxisType == NativeSwiftDimensionType.fillParentMaxHeight)
  }

  /// One flag per child, in the order they were given.
  ///
  /// - Parameters:
  ///   - available: the container's space along its axis. A non-finite value is unbounded, which
  ///     keeps every child the document did not mark GONE.
  ///   - spacing: the gap between kept children, counted the way the container lays them out.
  public static func keptChildren(
    _ children: [Child], available: Float, spacing: Float
  ) -> [Bool] {
    var kept = [Bool](repeating: false, count: children.count)
    guard !children.isEmpty else { return kept }
    let gap = spacing.isFinite && spacing > 0 ? spacing : 0
    guard available.isFinite else {
      for (index, child) in children.enumerated() where !child.isGone { kept[index] = true }
      return kept
    }
    // Descending priority. An absent priority sorts first, and document order breaks ties, which is
    // what the reference's list sort does for equal priorities.
    let hasPriorities = children.contains { $0.priority != nil }
    let order: [Int]
    if hasPriorities {
      order = children.indices.sorted { first, second in
        let left = children[first].priority ?? Float.greatestFiniteMagnitude
        let right = children[second].priority ?? Float.greatestFiniteMagnitude
        if left == right { return first < second }
        return left > right
      }
    } else {
      order = Array(children.indices)
    }
    var used: Float = 0
    var keptCount = 0
    var overflow = false
    for index in order {
      let child = children[index]
      if child.isGone { continue }
      let size = child.weight > 0 ? 0 : max(child.mainSize, 0)
      let neededSpacing = keptCount > 0 ? gap : 0
      if overflow || used + neededSpacing + size > available {
        overflow = true
        continue
      }
      used += neededSpacing + size
      keptCount += 1
      kept[index] = true
    }
    return kept
  }
}

/// One operation of a `LayoutComputeOperation` (238) body, in wire order.
///
/// AndroidX's writer (`RemoteComposeWriter.addLayoutCompute`) fills the body with float expressions
/// that read the bounds array through `A_DEREF`, and `UpdateDynamicFloatList` operations that
/// write results back into it. Those, float constants and the bounds array's own
/// `DynamicFloatList` declaration are the body this core runs; anything else refuses the document.
enum NativeSwiftLayoutComputeStep: Sendable, Equatable {
  /// A `FloatConstant` or `FloatExpression`: `id` takes the value of `words`, evaluated as RPN.
  case float(id: Int, words: [UInt32], offset: Int)
  /// An `UpdateDynamicFloatList`: element `index` of list `listID` takes `value`.
  case update(listID: Int, index: UInt32, value: UInt32)

  /// Every NaN-encoded id the step reads, arrays included.
  var referencedIDs: [Int] {
    switch self {
    case .float(_, let words, _):
      return words.compactMap(NativeSwiftFloatExpression.referenceID)
    case .update(let listID, let index, let value):
      return [listID] + [index, value].compactMap(NativeSwiftFloatExpression.referenceID)
    }
  }
}

/// A decoded `LayoutComputeOperation`, attached to the component it modifies.
struct ParsedLayoutCompute {
  let type: Int
  let boundsID: Int
  let animateChanges: Bool
  let steps: [NativeSwiftLayoutComputeStep]
}

/// A `LayoutComputeOperation` as one frame resolved it: a component-attached program that, given
/// the component's measured or placed box and its parent's size, computes a new width and height
/// (`NativeSwiftLayoutComputeType.measure`) or x and y (`.position`).
///
/// The reference runs it from `BoxLayout`'s measure and layout passes (`applyComputedLayout`): it
/// copies `[x, y, width, height, parentWidth, parentHeight]` into the bounds array when that array
/// is a `DynamicFloatList`, applies the body's operations in order, and reads the array back. This
/// is that, as a pure function a host's layout engine can call: the frame's float values and lists
/// the body reads are captured when the snapshot is taken.
///
/// Values are in document units, the units the body's expressions are written in.
public struct NativeSwiftLayoutComputeSnapshot: Sendable, Equatable {
  /// `NativeSwiftLayoutComputeType`: which half of the bounds the host applies.
  public let type: Int
  /// The float-list id the bounds travel through.
  public let boundsID: Int
  /// The reference's `animateChanges`: whether a change the computation makes may animate.
  public let animateChanges: Bool
  let steps: [NativeSwiftLayoutComputeStep]
  /// The lists the body reads or writes that are `DynamicFloatList`s. The reference writes the
  /// component's box into the bounds array only when it is one, and `UpdateDynamicFloatList`
  /// changes only a list its state holds as dynamic (`getDynamicFloats`), so an update to a static
  /// `DataListFloat` is ignored rather than altering a copy the body reads back.
  let dynamicListIDs: Set<Int>
  /// Whether the bounds array is a `DynamicFloatList`, which the component's box is seeded into.
  var seedsBounds: Bool { dynamicListIDs.contains(boundsID) }
  /// The lists the body reads or writes, as this frame resolved them.
  let lists: [Int: [Float]]
  /// The float values the body reads, as this frame resolved them.
  let values: [Int: Float]

  /// Runs the computation over a component's box.
  ///
  /// - Returns: the bounds array after the body ran — at least `x`, `y`, `width` and `height` —
  ///   or nil when the computation does not apply: the bounds array does not exist, the body
  ///   failed to evaluate, or a result is not finite. The reference leaves the box as measured
  ///   when it has no array, and a nil here asks the host to do the same.
  public func evaluate(
    x: Float, y: Float, width: Float, height: Float, parentWidth: Float, parentHeight: Float
  ) -> [Float]? {
    var arrays = lists
    if seedsBounds {
      arrays[boundsID] = [x, y, width, height, parentWidth, parentHeight]
    }
    guard arrays[boundsID] != nil else { return nil }
    var locals = values
    for step in steps {
      switch step {
      case .float(let id, let words, let offset):
        guard
          let value = try? NativeSwiftFloatExpression.evaluate(
            words, values: locals, arrays: arrays, opcode: NativeSwiftWireOpcode.animatedFloat,
            offset: offset)
        else { return nil }
        locals[id] = value
      case .update(let listID, let indexWord, let valueWord):
        // The reference ignores an update to a list it does not hold as dynamic, or past its end.
        guard dynamicListIDs.contains(listID), var list = arrays[listID] else { continue }
        let index = nativeSwiftClampedInt(
          NativeSwiftFloatExpression.resolve(indexWord, values: locals))
        guard list.indices.contains(index) else { continue }
        list[index] = NativeSwiftFloatExpression.resolve(valueWord, values: locals)
        arrays[listID] = list
      }
    }
    guard let result = arrays[boundsID], result.count > NativeSwiftLayoutComputeBound.height,
      result[0...NativeSwiftLayoutComputeBound.height].allSatisfy(\.isFinite)
    else { return nil }
    return result
  }
}
