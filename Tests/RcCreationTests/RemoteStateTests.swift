import Foundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import RcWireOperations
import Testing

@testable import RcCreation

/// The building blocks component libraries are made from: conditions, actions that compute
/// their value, the environment, paths and system values, read back through the native player.
@Suite struct RemoteStateTests {
  private func components(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftNodeSnapshot] {
    node.children.flatMap { child in child.kind == .content ? components(child) : [child] }
  }

  private func open(_ document: RemoteDocument) throws -> NativeSwiftDocumentSession {
    try NativeSwiftDocumentSession.open(data: document.encodedData())
  }

  private func operations(_ document: RemoteDocument) throws -> [WireOperation] {
    try WireDocument.load(from: document.encode()).operations
  }

  @Test func toggleFlipsABoolAndConditionsFollow() throws {
    let isOn = RemoteVariable("wifi", default: false)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteColor.green.frame(width: 10, height: 10).visible(if: RemoteBool(isOn))
        RemoteColor.red.frame(width: 10, height: 10).visible(if: !isOn)
        RemoteColor.blue.frame(width: 40, height: 20).onTapGesture(.toggle(isOn))
      }
    }
    let session = try open(document)
    func visibilities() throws -> [Int?] {
      let stack = try #require(components(try session.snapshot().root).first)
      return components(stack).prefix(2).map(\.visibility)
    }
    #expect(try visibilities() == [NativeSwiftVisibility.gone, NativeSwiftVisibility.visible])
    let stack = try #require(components(try session.snapshot().root).first)
    let button = try #require(components(stack).last)
    _ = try session.click(componentID: button.componentID, timeSeconds: 0)
    #expect(try visibilities() == [NativeSwiftVisibility.visible, NativeSwiftVisibility.gone])
    _ = try session.click(componentID: button.componentID, timeSeconds: 1)
    #expect(try visibilities() == [NativeSwiftVisibility.gone, NativeSwiftVisibility.visible])
  }

  @Test func equalityPicksOneOfSeveral() throws {
    let selection = RemoteVariable("tab", default: 1)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteForEach(0..<3) { index in
          RemoteColor.blue.frame(width: 10, height: 10).visible(if: selection == index)
        }
      }
    }
    let session = try open(document)
    func shown() throws -> [Bool] {
      let stack = try #require(components(try session.snapshot().root).first)
      return components(stack).map { $0.visibility == NativeSwiftVisibility.visible }
    }
    #expect(try shown() == [false, true, false])
    #expect(session.setInteger(2, for: selection.name))
    #expect(try shown() == [false, false, true])
  }

  @Test func incrementStaysInItsRange() throws {
    let value = RemoteVariable("count", default: 2.0)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteText("\(value, decimals: 0)")
        RemoteColor.blue.frame(width: 20, height: 20).onTapGesture(
          .increment(value, by: 1, in: 0...3))
      }
    }
    let session = try open(document)
    let stack = try #require(components(try session.snapshot().root).first)
    let button = try #require(components(stack).last)
    func text() throws -> String? {
      components(try #require(components(try session.snapshot().root).first)).first?.text?.value
    }
    #expect(try text() == "2")
    _ = try session.click(componentID: button.componentID, timeSeconds: 0)
    #expect(try text() == "3")
    _ = try session.click(componentID: button.componentID, timeSeconds: 1)
    #expect(try text() == "3")
  }

  @Test func zeroPaddedNumbersAndModulo() throws {
    let minutes = RemoteVariable("minutes", default: 427.0)
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText(
        "\(RemoteFloat(minutes) / 60 - (RemoteFloat(minutes) % 60) / 60, integerDigits: 2, zeroPadded: true):\(RemoteFloat(minutes) % 60, integerDigits: 2, zeroPadded: true)"
      )
    }
    let session = try open(document)
    #expect(components(try session.snapshot().root).first?.text?.value == "07:07")
  }

  @Test func clockReadsTheSystemTime() throws {
    let document = RemoteDocument(width: 200, height: 50) {
      RemoteText("\(RemoteFloat(.hour), integerDigits: 2, zeroPadded: true)")
    }
    let expressions = try operations(document).compactMap {
      if case .textFromFloat(let text) = $0 { text } else { nil }
    }
    #expect(
      expressions.first?.value.bitPattern
        == RemoteWriter.reference(RemoteContextIds.idTimeInHr).bitPattern)
    #expect(expressions.first?.flags == TextFromFloat.padPreZero | TextFromFloat.padAfterZero)
  }

  @Test func environmentReachesReaders() throws {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteEnvironmentReader { environment in
        RemoteColor(storage: environment.tint.storage).frame(width: 10, height: 10)
      }
      .tint(.hex(0x123456))
    }
    #expect(
      try components(try open(document).snapshot().root).first?.backgroundARGB == 0xFF12_3456)
  }

  @Test func pathsAreDeclaredOnceAndDrawn() throws {
    let check = try #require(RemotePath(svg: "M4 12 L9 17 L20 6"))
    let document = RemoteDocument(width: 100, height: 100, displayScale: 2) {
      RemoteCanvas { context, _ in
        context.stroke(.path(check), with: .black, lineWidth: 2)
        context.fill(.path(check), with: .red)
      }
      .frame(width: 24, height: 24)
    }
    let operations = try operations(document)
    let paths = operations.compactMap { if case .pathData(let path) = $0 { path } else { nil } }
    try #require(paths.count == 1)
    #expect(
      PathCommand.decode(paths[0].pathData) == [
        .moveTo(x: 8, y: 24), .lineTo(x: 18, y: 34), .lineTo(x: 40, y: 12), .done,
      ])
    #expect(operations.filter { $0.opCode == .drawPath }.count == 2)
    _ = try open(document).snapshot()
  }

  @Test func svgPathDataParses() throws {
    let path = try #require(RemotePath(svg: "m1,2 h3 v4 c1 1 2 2 3 3 s1 1 2 2 q1 0 2 1 t1 1 z"))
    #expect(path.elements.count == 8)
    #expect(path.elements.first == .move(1, 2))
    #expect(path.elements[1] == .line(4, 2))
    #expect(path.elements[2] == .line(4, 6))
    #expect(RemotePath(svg: "M0 0 A1 1 0 0 1 2 2") == nil)
  }

  /// A button runs its actions when tapped and reads as a button.
  @Test func buttonsRunTheirActionsAndReadAsButtons() throws {
    let page = RemoteVariable("page", default: 0)
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteVStack {
        RemoteButton("Next", action: .increment(page, in: 0...9))
        RemoteText("On page 1").visible(if: page == 1)
        RemoteText("Hidden but holding its space").hidden()
      }
    }
    let operations = try WireDocument.load(from: document.encode()).operations
    let roles = operations.compactMap {
      if case .coreSemantics(let semantics) = $0 {
        Int32(Int8(bitPattern: semantics.role))
      } else {
        nil
      }
    }
    #expect(roles == [CoreSemantics.roleButton])
    let session = try open(document)
    let stack = try #require(components(try session.snapshot().root).first)
    let button = try #require(components(stack).first)
    #expect(button.isClickable)
    #expect(components(stack)[1].visibility == NativeSwiftVisibility.gone)
    #expect(components(stack)[2].visibility == NativeSwiftVisibility.invisible)
    _ = try session.click(componentID: button.componentID, timeSeconds: 0)
    let shown = components(try #require(components(try session.snapshot().root).first))
    #expect(shown[1].visibility == NativeSwiftVisibility.visible)
  }

  /// Integer arithmetic and ordering, evaluated by the player as the variable changes.
  @Test func integerArithmeticAndOrdering() throws {
    let count = RemoteVariable("count", default: 7)
    let document = RemoteDocument(width: 200, height: 100) {
      RemoteVStack {
        RemoteSwitch((RemoteInt(count) * 2) % 3, in: 0..<3) { remainder in
          RemoteText("r\(remainder)")
        }
        RemoteSwitch(RemoteInt(count) / 3, in: 0..<4) { quotient in
          RemoteText("q\(quotient)")
        }
        RemoteText("lt5").visible(if: count < 5)
        RemoteText("le7").visible(if: count <= 7)
        RemoteText("gt7").visible(if: count > 7)
        RemoteText("ge7").visible(if: count >= 7)
        RemoteText("neg").visible(if: RemoteInt(count).signum() == -1)
      }
    }
    let session = try open(document)
    func shown() throws -> [String] {
      func walk(_ node: NativeSwiftNodeSnapshot) -> [String] {
        if node.visibility == NativeSwiftVisibility.gone { return [] }
        return (node.text.map { [$0.value] } ?? []) + node.children.flatMap(walk)
      }
      return walk(try session.snapshot().root)
    }
    // 7: 14 % 3 = 2, 7 / 3 = 2.
    #expect(try shown() == ["r2", "q2", "le7", "ge7"])
    #expect(session.setInteger(3, for: count.name))
    // 3: 6 % 3 = 0, 3 / 3 = 1.
    #expect(try shown() == ["r0", "q1", "lt5", "le7"])
    #expect(session.setInteger(8, for: count.name))
    #expect(try shown() == ["r1", "q2", "gt7", "ge7"])
    #expect(session.setInteger(-2, for: count.name))
    // Truncating division and Swift's `%`: -2 / 3 = 0, and -4 % 3 = -1, an index outside the
    // states, which shows the first.
    #expect(try shown() == ["r0", "q0", "lt5", "le7", "neg"])
  }

  /// Constants fold in Swift, except where only the player decides the result.
  @Test func integerConstantsFold() throws {
    #expect((RemoteInt(7) * 3 + 1).node == .constant(22))
    #expect((RemoteInt(-7) / 2).node == .constant(-3))
    #expect((RemoteInt(-7) % 2).node == .constant(-1))
    #expect(RemoteInt(-5).signum().node == .constant(-1))
    #expect((RemoteInt(7) / 0).node == .binary(.divide, .constant(7), .constant(0)))
  }

  /// An action can copy a variable or a system value: its source is written as an expression,
  /// which is what a value-change action reads.
  @Test func actionsCopyBareReferences() throws {
    let source = RemoteVariable("source", default: 7.5)
    let target = RemoteVariable("target", default: 0.0)
    let count = RemoteVariable("count", default: 3)
    let copy = RemoteVariable("copy", default: 0)
    let clock = RemoteVariable("clock", default: -1.0)
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteColor.blue.frame(width: 40, height: 20)
          .onTapGesture(
            .set(target, to: RemoteFloat(source)), .set(copy, to: RemoteInt(count)),
            .set(clock, to: RemoteFloat(.hour)))
      }
    }
    let operations = try WireDocument.load(from: document.encode()).operations
    let floatExpressions = Set(
      operations.compactMap { if case .floatExpression(let e) = $0 { e.id } else { nil } })
    let intExpressions = Set(
      operations.compactMap { if case .integerExpression(let e) = $0 { e.id } else { nil } })
    let floatSources = operations.compactMap {
      if case .valueFloatExpressionChangeActionOperation(let a) = $0 {
        a.valueExpressionId
      } else {
        nil
      }
    }
    let intSources = operations.compactMap {
      if case .valueIntegerExpressionChangeActionOperation(let a) = $0 {
        Int32(truncatingIfNeeded: a.valueExpressionId)
      } else {
        nil
      }
    }
    #expect(floatSources.count == 2 && floatSources.allSatisfy(floatExpressions.contains))
    #expect(intSources.count == 1 && intSources.allSatisfy(intExpressions.contains))
    // The native player opens it and runs the copies.
    let session = try open(document)
    let stack = try #require(components(try session.snapshot().root).first)
    let button = try #require(components(stack).first)
    _ = try session.click(componentID: button.componentID, timeSeconds: 0)
  }

  /// `!=` holds for every pair of distinct 32-bit values, even when their difference wraps to
  /// `Int32.min`, whose magnitude is negative.
  @Test func inequalityHoldsAcrossTheWholeRange() throws {
    let value = RemoteVariable("value", default: Int(Int32.min))
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteText("nonzero").visible(if: value != 0)
        RemoteText("zero").visible(if: value == 0)
      }
    }
    let session = try open(document)
    func shown() throws -> [String] {
      func walk(_ node: NativeSwiftNodeSnapshot) -> [String] {
        if node.visibility == NativeSwiftVisibility.gone { return [] }
        return (node.text.map { [$0.value] } ?? []) + node.children.flatMap(walk)
      }
      return walk(try session.snapshot().root)
    }
    #expect(try shown() == ["nonzero"])
    #expect(session.setInteger(0, for: value.name))
    #expect(try shown() == ["zero"])
    #expect(session.setInteger(Int(Int32.max), for: value.name))
    #expect(try shown() == ["nonzero"])
  }

  /// A flexible spacer shares a row's leftover space as a weighted child does, so the row writes
  /// its gaps as padding; a rounded background on it keeps its weight.
  @Test func spacersAreWeightedChildren() throws {
    struct Gap: RemoteView {
      var body: some RemoteView { RemoteSpacer() }
    }
    func rowSpacing(_ document: RemoteDocument) throws -> [Float] {
      try WireDocument.load(from: document.encode()).operations.compactMap {
        if case .rowLayout(let row) = $0 { row.spacedBy } else { nil }
      }
    }
    func weights(_ document: RemoteDocument) throws -> Int {
      try WireDocument.load(from: document.encode()).operations.filter {
        if case .widthModifierOperation(let width) = $0 {
          width.type == WidthModifierOperation.weight
        } else {
          false
        }
      }.count
    }
    let plain = RemoteDocument(width: 200, height: 50) {
      RemoteHStack(spacing: 12) {
        RemoteText("A")
        RemoteSpacer()
        RemoteText("B")
      }
    }
    #expect(try rowSpacing(plain) == [0])
    let custom = RemoteDocument(width: 200, height: 50) {
      RemoteHStack(spacing: 12) {
        RemoteText("A")
        Gap().padding(2)
        RemoteText("B")
      }
    }
    #expect(try rowSpacing(custom) == [0])
    struct Identity: RemoteViewModifier {
      func body(content: Content) -> some RemoteView { content }
    }
    let viaModifier = RemoteDocument(width: 200, height: 50) {
      RemoteHStack(spacing: 12) {
        RemoteText("A")
        RemoteSpacer().modifier(Identity())
        AnyRemoteView(RemoteSpacer())
        RemoteText("B")
      }
    }
    #expect(try rowSpacing(viaModifier) == [0])
    let fixed = RemoteDocument(width: 200, height: 50) {
      RemoteHStack(spacing: 12) {
        RemoteText("A")
        RemoteSpacer(length: 10)
        RemoteText("B")
      }
    }
    #expect(try rowSpacing(fixed) == [12])
    let background = RemoteDocument(width: 200, height: 50) {
      RemoteHStack(spacing: 12) {
        RemoteText("A")
        RemoteSpacer().background(.red, in: .roundedRectangle(cornerRadius: 4))
        RemoteText("B")
      }
    }
    #expect(try weights(background) == 1)
  }

  /// A row's spacing leaves no gap beside a gone child, as `spacedBy` does; a row with flexible
  /// children writes its gaps as padding instead, which the reference players need.
  @Test func rowGapsFollowVisibilityWhenNothingIsWeighted() throws {
    let showFirst = RemoteVariable("showFirst", default: false)
    let plain = RemoteDocument(width: 200, height: 50) {
      RemoteHStack(spacing: 12) {
        RemoteColor.red.frame(width: 20, height: 20).visible(if: RemoteBool(showFirst))
        RemoteColor.blue.frame(width: 20, height: 20)
      }
    }
    let row = try #require(components(try open(plain).snapshot().root).first)
    #expect(components(row).allSatisfy { $0.padding.left == 0 })
    let operations = try WireDocument.load(from: plain.encode()).operations
    let spacings = operations.compactMap {
      if case .rowLayout(let row) = $0 { row.spacedBy } else { nil }
    }
    #expect(spacings == [12])

    let weighted = RemoteDocument(width: 200, height: 50) {
      RemoteHStack(spacing: 12) {
        RemoteColor.red.frame(height: 20).flexWeight(1)
        RemoteColor.blue.frame(height: 20).flexWeight(1)
      }
    }
    let weightedRow = try #require(components(try open(weighted).snapshot().root).first)
    #expect(components(weightedRow).map(\.padding.left) == [0, 12])
  }

  /// `S` reflects a control point only after `C` or `S`, and `T` only after `Q` or `T`.
  @Test func smoothCurvesReflectOnlyTheirOwnFamily() throws {
    let cubicAfterQuad = try #require(RemotePath(svg: "M0 0 Q10 10 20 0 S30 10 40 0"))
    #expect(cubicAfterQuad.elements[2] == .cubic(20, 0, 30, 10, 40, 0))
    let quadAfterCubic = try #require(RemotePath(svg: "M0 0 C5 5 15 5 20 0 T40 0"))
    #expect(quadAfterCubic.elements[2] == .quad(20, 0, 40, 0))
    let cubicAfterCubic = try #require(RemotePath(svg: "M0 0 C5 5 15 5 20 0 S35 5 40 0"))
    #expect(cubicAfterCubic.elements[2] == .cubic(25, -5, 35, 5, 40, 0))
  }

  /// A condition longer than an `IntegerExpression`'s 32-bit mask is split into expressions that
  /// reference each other, and still evaluates.
  @Test func longConditionsSplitAndEvaluate() throws {
    let flags = (0..<10).map { RemoteVariable("flag\($0)", default: 1) }
    let all = flags.dropFirst().reduce(flags[0] == 1) { $0 && $1 == 1 }
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteColor.green.frame(width: 10, height: 10).visible(if: all)
      }
    }
    let operations = try WireDocument.load(from: document.encode()).operations
    let expressions = operations.compactMap { operation -> IntegerExpression? in
      if case .integerExpression(let expression) = operation { expression } else { nil }
    }
    #expect(expressions.count > 1)
    #expect(expressions.allSatisfy { $0.values.count <= 32 })
    let session = try open(document)
    func visibility() throws -> Int? {
      let stack = try #require(components(try session.snapshot().root).first)
      return components(stack).first?.visibility
    }
    #expect(try visibility() == NativeSwiftVisibility.visible)
    #expect(session.setInteger(0, for: "flag9"))
    #expect(try visibility() == NativeSwiftVisibility.gone)
  }

  @Test func boolAndIntVariablesWithOneNameConflict() {
    let document = RemoteDocument(width: 100, height: 100) {
      RemoteVStack {
        RemoteColor.red.frame(width: 1, height: 1).visible(
          if: RemoteBool(RemoteVariable("x", default: true)))
        RemoteColor.red.frame(width: 1, height: 1).visible(if: RemoteVariable("x", default: 1) == 1)
      }
    }
    #expect(throws: RemoteEncodingError.conflictingVariable(name: "x")) { try document.encode() }
  }
}
