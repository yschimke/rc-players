import Foundation
import Testing

@testable @_spi(Conformance) @_spi(RemoteComposeController) import RcNativePlayerCore

/// Every path that writes a float, and the integer view it must publish under the same id, against
/// what the Kotlin `RcPlayerState` does: `storeFloat` stores `value.toInt()` too, `setInteger`
/// rewrites both views, `RcSeedValues.load` applies declarations in wire order, and `setColor`
/// ignores a document write to a host-overridden colour.
@Suite struct NativeSwiftFloatIntegerViewTests {
  private typealias Op = NativeSwiftWireOpcode
  private typealias Named = NativeSwiftNamedVariableType

  /// Kotlin `Float.toInt()`: truncation toward zero, saturating at Int32, NaN -> 0.
  static let edgeCases: [(Float, Int)] = [
    (-0.0, 0),
    (0.9999999, 0), (-0.9999999, 0),
    (Float(Int32.max), Int(Int32.max)),  // 2^31 exactly (Int32.max rounds up)
    (Float(Int32.max) + 1, Int(Int32.max)),
    (Float(Int32.max).nextDown, Int(Float(Int32.max).nextDown)),  // largest float below 2^31
    (-Float(Int32.max).nextDown, -Int(Float(Int32.max).nextDown)),
    (Float(Int32.min), Int(Int32.min)),  // -2^31 exactly
    (Float(Double(Int32.min) - 1), Int(Int32.min)),  // -2^31 - 1 rounds to -2^31
    (Float(Int32.min).nextDown, Int(Int32.min)),  // next float below -2^31
    (3e9, Int(Int32.max)), (-3e9, Int(Int32.min)),
    (1e20, Int(Int32.max)), (-1e20, Int(Int32.min)),
    (.greatestFiniteMagnitude, Int(Int32.max)), (-.greatestFiniteMagnitude, Int(Int32.min)),
    (Float.leastNormalMagnitude / 2, 0), (-Float.leastNormalMagnitude / 2, 0),  // subnormals
    (.leastNonzeroMagnitude, 0), (-.leastNonzeroMagnitude, 0),
    (Float(Double(1 << 24) + 1), 1 << 24),  // 2^24 + 1 is not representable: it rounds to 2^24
    (-7.5, -7), (7.5, 7),
  ]

  /// Float 51 (named "page"), int 52 named "count", integer expression 53 = view of 51.
  static func valuesDocument(clickActions: ((Writer) -> Void)? = nil) -> Data {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(51).float(1.75)
    document.namedVariable(id: 51, type: Named.float, name: "page")
    document.u8(Op.dataInt).int(52).int(4)
    document.namedVariable(id: 52, type: Named.int, name: "count")
    document.u8(Op.integerExpression).int(53).int(1).int(1).int(51)
    document.u8(Op.layoutRoot).int(1)
    document.u8(Op.layoutBox).int(3).int(-1).int(1).int(4)
    if let clickActions {
      document.u8(Op.modifierClick)
      clickActions(document)
      document.u8(Op.containerEnd)
    }
    document.u8(Op.containerEnd).u8(Op.containerEnd)
    return document.data
  }

  // MARK: - Edge values, host write by id and by name

  @Test(arguments: [false, true])
  func hostFloatWriteEdgeValues(byName: Bool) throws {
    let session = try NativeSwiftDocumentSession.open(data: Self.valuesDocument())
    for (value, expected) in Self.edgeCases {
      let accepted =
        byName ? session.setFloat(value, for: "page") : session.setFloat(value, forID: 51)
      #expect(accepted, "\(value)")
      let values = try session.probeValues(timeSeconds: 0)
      #expect(
        values.integers[51] == expected,
        "integers[51] for \(value): \(String(describing: values.integers[51]))")
      #expect(
        values.integers[53] == expected,
        "expr view for \(value): \(String(describing: values.integers[53]))")
      #expect(values.floats[51]?.bitPattern == value.bitPattern, "float view of \(value)")
    }
  }

  /// Swift refuses non-finite host writes; both views must stay untouched (consistently by id and
  /// by name). Kotlin would store NaN and integer 0 — a documented API difference.
  @Test(arguments: [false, true])
  func nonFiniteHostWritesLeaveBothViews(byName: Bool) throws {
    let session = try NativeSwiftDocumentSession.open(data: Self.valuesDocument())
    #expect(session.setFloat(-2.75, forID: 51))
    for value in [Float.nan, .infinity, -.infinity, -Float.nan, Float.signalingNaN] {
      let accepted =
        byName ? session.setFloat(value, for: "page") : session.setFloat(value, forID: 51)
      #expect(!accepted, "\(value) accepted")
      let values = try session.probeValues(timeSeconds: 0)
      #expect(values.integers[51] == -2 && values.integers[53] == -2, "\(value)")
      #expect(values.floats[51] == -2.75, "\(value)")
    }
  }

  // MARK: - Document constants at open

  @Test func floatConstantsSeedTheirIntegerViewAtOpen() throws {
    for (value, expected) in Self.edgeCases {
      let document = Writer()
      document.header(width: 100, height: 100)
      document.u8(Op.dataFloat).int(51).float(value)
      document.u8(Op.integerExpression).int(53).int(1).int(1).int(51)
      let session = try NativeSwiftDocumentSession.open(
        data: document.data, toleratingRootlessData: true)
      let values = try session.probeValues(timeSeconds: 0)
      #expect(
        values.integers[51] == expected,
        "constant \(value): \(String(describing: values.integers[51]))")
      #expect(
        values.integers[53] == expected,
        "expr over constant \(value): \(String(describing: values.integers[53]))")
    }
  }

  /// Kotlin `RcSeedValues.load`: declarations apply in order, each publishing both views. Float
  /// then integer: the integer wins both views (integers = 7, floats = 7.0).
  @Test func floatThenIntegerAtTheSameIdKotlinLastWins() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(51).float(2.75)
    document.u8(Op.dataInt).int(51).int(7)
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(values.integers[51] == 7, "integer view: \(String(describing: values.integers[51]))")
    #expect(values.floats[51] == 7, "float view: \(String(describing: values.floats[51]))")
  }

  /// Integer then float: the float wins both views (floats = 2.75, integers = 2).
  @Test func integerThenFloatAtTheSameIdKotlinLastWins() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataInt).int(51).int(7)
    document.u8(Op.dataFloat).int(51).float(2.75)
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(values.floats[51] == 2.75, "float view: \(String(describing: values.floats[51]))")
    #expect(values.integers[51] == 2, "integer view: \(String(describing: values.integers[51]))")
  }

  // MARK: - Document actions

  @Test func clickFloatValueActionEdgeValues() throws {
    for (value, expected) in Self.edgeCases {
      let data = Self.valuesDocument { document in
        document.u8(Op.valueFloatChangeAction).int(51).float(value)
      }
      let session = try NativeSwiftDocumentSession.open(data: data)
      _ = try session.click(componentID: 3, timeSeconds: 0)
      let values = try session.probeValues(timeSeconds: 0)
      #expect(
        values.integers[51] == expected,
        "action \(value): \(String(describing: values.integers[51]))")
      #expect(
        values.integers[53] == expected,
        "expr after action \(value): \(String(describing: values.integers[53]))")
    }
  }

  /// A float action onto the *integer* variable: Kotlin `overrideFloat` -> integers = trunc.
  @Test func clickFloatActionOntoAnIntegerVariable() throws {
    let data = Self.valuesDocument { document in
      document.u8(Op.valueFloatChangeAction).int(52).float(9.9)
    }
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.click(componentID: 3, timeSeconds: 0)
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.integers[52] == 9 && values.floats[52] == 9.9)
    // And it is carried as such.
    let carried = session.writtenNamedValues().first { $0.name == "count" }?.value
    #expect(carried == .integer(9, float: 9.9), "\(String(describing: carried))")
  }

  @Test func clickFloatExpressionActionTruncates() throws {
    // 60 = [51] * -3 = -5.25 at 1.75
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(51).float(1.75)
    document.u8(Op.animatedFloat).int(60).int(3)
      .int(Writer.nanReference(51)).int(Writer.literal(-3))
      .int(Writer.floatOperator(NativeSwiftFloatOperator.mul))
    document.u8(Op.integerExpression).int(53).int(1).int(1).int(54)
    document.u8(Op.layoutRoot).int(1)
    document.u8(Op.layoutBox).int(3).int(-1).int(1).int(4)
    document.u8(Op.modifierClick)
      .u8(Op.valueFloatExpressionChangeAction).int(54).int(60)
      .u8(Op.containerEnd).u8(Op.containerEnd).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    _ = try session.click(componentID: 3, timeSeconds: 0)
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.floats[54] == -5.25)
    #expect(
      values.integers[54] == -5 && values.integers[53] == -5,
      "\(String(describing: values.integers[54])) \(String(describing: values.integers[53]))")
  }

  // MARK: - Event data

  @Test func eventDataEdgeValuesAndRefusedNonFinite() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(40).float(1)
    document.u8(Op.integerExpression).int(41).int(1).int(1).int(40)
    document.u8(Op.eventAction).int(NativeSwiftEventActionWire.version)
      .int(5).int(7).u16(NativeSwiftEventActionWire.flagUnconditional).u16(1)
      .int(1).int(40)
    document.u8(Op.containerEnd)
    document.u8(Op.layoutRoot).int(1).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    for (value, expected) in Self.edgeCases {
      _ = try session.dispatchEvent(type: 5, metadata: 7, data: [value], timeSeconds: 0)
      let values = try session.probeValues(timeSeconds: 0)
      #expect(values.integers[40] == expected && values.integers[41] == expected, "event \(value)")
    }
    _ = try session.dispatchEvent(type: 5, metadata: 7, data: [-2.5], timeSeconds: 0)
    for value in [Float.nan, .infinity, -.infinity] {
      _ = try session.dispatchEvent(type: 5, metadata: 7, data: [value], timeSeconds: 0)
      let values = try session.probeValues(timeSeconds: 0)
      #expect(
        values.integers[40] == -2 && values.floats[40] == -2.5, "event \(value) changed state")
    }
  }

  // MARK: - Custom float return

  @Test func customFloatReturnIsAlsoItsIntegerView() throws {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(Op.animatedFloat).int(70).int(1).float(0.4)
    output.u8(Op.integerExpression).int(71).int(1).int(1).int(70)
    output.text(id: 40, "demo:SwiftControls")
    output.u8(Op.layoutRoot).int(1)
    output.u8(Op.layoutContent).int(2)
    // A custom component (3) with a float property (1) and a float return channel (2), both 70.
    output.u8(Op.layoutCustom).int(3).int(0).int(40).int(2)
      .u16(1).u16(NativeSwiftCustomPropertyType.floatProperty).int(Writer.nanReference(70))
      .u16(2).u16(NativeSwiftCustomPropertyType.floatReturn).int(Writer.nanReference(70))
    output.u8(Op.containerEnd).u8(Op.containerEnd).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: output.data)
    for (value, expected) in Self.edgeCases {
      #expect(session.returnCustomFloat(value, componentID: 3, propertyID: 2))
      let values = try session.probeValues(timeSeconds: 0)
      #expect(
        values.integers[70] == expected && values.integers[71] == expected,
        "custom \(value): \(String(describing: values.integers[71]))")
    }
    #expect(!session.returnCustomFloat(.nan, componentID: 3, propertyID: 2))
  }

  // MARK: - Transitions: theme, detached copy, reopen

  @Test func integerViewSurvivesThemeCopyAndReopen() throws {
    let data = Self.valuesDocument()
    let session = try NativeSwiftDocumentSession.open(data: data)
    #expect(session.setFloat(3e9, for: "page"))
    #expect(session.setFloat(-6.5, forID: 52))  // float write onto the integer variable
    session.setRequestedTheme(NativeSwiftTheme.dark)
    var values = try session.probeValues(timeSeconds: 0)
    #expect(values.integers[53] == Int(Int32.max) && values.integers[52] == -6)
    session.setRequestedTheme(NativeSwiftTheme.light)
    values = try session.detachedCopy().probeValues(timeSeconds: 0)
    #expect(values.integers[53] == Int(Int32.max) && values.integers[52] == -6)
    let reopened = try session.reopened(over: data)
    values = try reopened.probeValues(timeSeconds: 0)
    #expect(values.integers[53] == Int(Int32.max), "\(String(describing: values.integers[53]))")
    #expect(
      values.integers[52] == -6 && values.floats[52] == -6.5,
      "\(String(describing: values.integers[52])) \(String(describing: values.floats[52]))")
  }

  /// Kotlin `setInteger` after a float write: integers = v and floats = v.
  @Test func hostIntegerAfterHostFloatOnTheSameVariable() throws {
    let session = try NativeSwiftDocumentSession.open(data: Self.valuesDocument())
    #expect(session.setFloat(2.75, forID: 52))
    #expect(session.setInteger(9, for: "count"))
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.integers[52] == 9)
    #expect(
      values.floats[52] == 9, "stale float override: \(String(describing: values.floats[52]))")
  }

  /// Kotlin `setInteger` returns early for the integer an id already holds, as AndroidX
  /// `updateInteger` does: after a float write of 2.5, an integer write of 2 (its integer view)
  /// leaves both views, by name and by a document action.
  @Test(arguments: [false, true])
  func anUnchangedIntegerWriteKeepsTheFloatView(byAction: Bool) throws {
    let session = try NativeSwiftDocumentSession.open(
      data: Self.valuesDocument { document in
        document.u8(Op.valueIntegerChangeAction).int(52).int(2)
      })
    #expect(session.setFloat(2.5, forID: 52))
    if byAction {
      _ = try session.click(componentID: 3, timeSeconds: 0)
    } else {
      #expect(session.setInteger(2, for: "count"))
    }
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.integers[52] == 2, "\(values.integers[52] as Any)")
    #expect(values.floats[52] == 2.5, "\(values.floats[52] as Any)")
  }

  /// Kotlin `setInteger` writes `floats[id] = value.toFloat()`: a document action's integer write
  /// to a float variable the host had written moves the float view to the integer, not back to
  /// the document's own float.
  @Test func anActionIntegerWriteOntoAFloatVariableSetsItsFloatView() throws {
    let session = try NativeSwiftDocumentSession.open(
      data: Self.valuesDocument { document in
        document.u8(Op.valueIntegerChangeAction).int(51).int(4)
      })
    #expect(session.setFloat(440.75, for: "page"))
    _ = try session.click(componentID: 3, timeSeconds: 0)
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.integers[51] == 4 && values.integers[53] == 4)
    #expect(values.floats[51] == 4, "\(values.floats[51] as Any)")
  }

  /// Kotlin `setFloat` onto a float id whose integer view an integer action had overridden: the
  /// float write replaces the integer view.
  @Test func hostFloatAfterIntegerActionOnTheSameId() throws {
    let data = Self.valuesDocument { document in
      document.u8(Op.valueIntegerChangeAction).int(51).int(40)
    }
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.click(componentID: 3, timeSeconds: 0)
    #expect(try session.probeValues(timeSeconds: 0).integers[51] == 40)
    #expect(session.setFloat(-1.5, for: "page"))
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.integers[51] == -1 && values.integers[53] == -1 && values.floats[51] == -1.5)
  }

  // MARK: - Visibility read through the integer view

  /// Kotlin/AndroidX visibility reads the integer view; a host float write of 1.0 makes it VISIBLE.
  @Test func aFloatWriteToAVisibilityIdDrivesVisibility() throws {
    let writer = Writer()
    writer.header(width: 400, height: 400)
    writer.u8(Op.dataFloat).int(30).float(0)
    writer.namedVariable(id: 30, type: Named.float, name: "shown")
    writer.u8(Op.layoutRoot).int(1)
    writer.u8(Op.layoutBox).int(5).int(-1).int(1).int(1)
    writer.u8(Op.modifierWidth).int(NativeSwiftDimensionType.exact).float(100)
    writer.u8(Op.modifierHeight).int(NativeSwiftDimensionType.exact).float(100)
    writer.u8(Op.modifierVisibility).int(30)
    writer.u8(Op.layoutContent).int(-1).u8(Op.containerEnd)
    writer.u8(Op.containerEnd).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: writer.data)
    session.setVisibilityAnimationsEnabled(false)
    func box() throws -> NativeSwiftNodeSnapshot {
      try #require(try session.snapshot().root.node(withComponentID: 5))
    }
    #expect(try box().visibility == NativeSwiftVisibility.gone)
    #expect(session.setFloat(1.9, for: "shown"))
    #expect(try box().visibility == NativeSwiftVisibility.visible)
  }

  // MARK: - Colours

  static let red = KotlinReference.red
  static let blue = KotlinReference.blue
  static let green: UInt32 = 0xFF00_FF00
  static let white: UInt32 = 0xFFFF_FFFF
  static let black: UInt32 = 0xFF00_0000

  /// 60 red constant; 61 expression (= 60) named "accent"; 62 ColorTheme white/black named
  /// "surface"; 63 expression interpolating 61 -> 60 at 0 (reads the host's 61).
  static func colorDocument() -> Data {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.colorConstant).int(60).int(Int(Int32(bitPattern: red)))
    document.u8(Op.colorExpressions).int(61).int(NativeSwiftColorExpressionMode.idIDInterpolate)
      .int(60).int(60).float(0)
    document.namedVariable(id: 61, type: Named.color, name: "USER:accent")
    document.u8(Op.colorTheme).int(62).int(0).u16(0).u16(0)
      .int(Int(Int32(bitPattern: white))).int(Int(Int32(bitPattern: black)))
    document.namedVariable(id: 62, type: Named.color, name: "surface")
    document.u8(Op.colorExpressions).int(63).int(NativeSwiftColorExpressionMode.idIDInterpolate)
      .int(61).int(60).float(0)
    // 64: expression writing the themed id 62's neighbour from 62 (reads the host's 62).
    document.u8(Op.colorExpressions).int(64).int(NativeSwiftColorExpressionMode.idIDInterpolate)
      .int(62).int(62).float(0)
    return document.data
  }

  /// An ID-ARGB alpha id of 0x8000 or more makes the signed mode word negative: the player reads
  /// the id from its unsigned upper half rather than trapping on a sign-extended shift.
  @Test func anIdArgbAlphaIdInTheUpperHalfOfItsRange() throws {
    let alphaID = 0x8001
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(alphaID).float(0.6)
    let mode = Int(
      Int32(
        bitPattern: UInt32(alphaID) << 16 | UInt32(NativeSwiftColorExpressionMode.idARGB)))
    #expect(mode < 0)
    document.u8(Op.colorExpressions).int(65).int(mode).float(1).float(0).float(0)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(try session.probeValues(timeSeconds: 0).colors[65] == 0x99FF_0000)
  }

  @Test func documentColoursWithoutHostWrites() throws {
    let session = try NativeSwiftDocumentSession.open(
      data: Self.colorDocument(), toleratingRootlessData: true)
    var colors = try session.probeValues(timeSeconds: 0).colors
    #expect(colors[61] == Self.red && colors[62] == Self.white && colors[63] == Self.red)
    session.setRequestedTheme(NativeSwiftTheme.dark)
    colors = try session.probeValues(timeSeconds: 0).colors
    #expect(colors[62] == Self.black && colors[64] == Self.black)
  }

  @Test func hostColourBeatsExpressionAndThemeInBothThemes() throws {
    let session = try NativeSwiftDocumentSession.open(
      data: Self.colorDocument(), toleratingRootlessData: true)
    #expect(session.setColor(Self.blue, for: "accent"))
    #expect(session.setColor(Self.green, for: "surface"))
    for theme in [
      NativeSwiftTheme.dark, NativeSwiftTheme.light, NativeSwiftTheme.unspecified,
      NativeSwiftTheme.dark,
    ] {
      session.setRequestedTheme(theme)
      let colors = try session.probeValues(timeSeconds: 0).colors
      #expect(colors[61] == Self.blue, "theme \(theme) 61")
      #expect(colors[62] == Self.green, "theme \(theme) 62")
      // A downstream expression reads the host's value (Kotlin: colors[61] is the host's).
      #expect(colors[63] == Self.blue, "theme \(theme) 63: \(String(describing: colors[63]))")
      #expect(colors[64] == Self.green, "theme \(theme) 64: \(String(describing: colors[64]))")
    }
  }

  @Test func hostColourAcrossCopyAndReopen() throws {
    let data = Self.colorDocument()
    let session = try NativeSwiftDocumentSession.open(data: data, toleratingRootlessData: true)
    #expect(session.setColor(Self.blue, for: "accent"))
    #expect(session.setColor(Self.green, for: "surface"))
    session.setRequestedTheme(NativeSwiftTheme.dark)
    var colors = try session.detachedCopy().probeValues(timeSeconds: 0).colors
    #expect(colors[61] == Self.blue && colors[62] == Self.green && colors[63] == Self.blue)
    let reopened = try session.reopened(over: data)
    reopened.setRequestedTheme(NativeSwiftTheme.dark)
    colors = try reopened.probeValues(timeSeconds: 0).colors
    #expect(
      colors[61] == Self.blue && colors[62] == Self.green && colors[63] == Self.blue,
      "\(colors)")
  }

  // MARK: - Float producers outside storeFloat (Kotlin: setFloat / storeFloat -> both views)

  /// Kotlin `RcTextLength` -> `setFloat(outId, length)`: an integer expression reads the length.
  @Test func textLengthIsAlsoItsIntegerView() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.text(id: 20, "hello")
    document.u8(Op.textLength).int(80).int(20)
    document.u8(Op.integerExpression).int(81).int(1).int(1).int(80)
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(values.floats[80] == 5)
    #expect(values.integers[81] == 5, "\(String(describing: values.integers[81]))")
  }

  /// Kotlin `ComponentValue` -> `storeFloat(valueId, width)`: an integer expression reads it.
  @Test func componentWidthIsAlsoItsIntegerView() throws {
    let writer = Writer()
    writer.header(width: 400, height: 400)
    writer.u8(Op.componentValue).int(NativeSwiftComponentValueType.width).int(5).int(90)
    writer.u8(Op.integerExpression).int(91).int(1).int(1).int(90)
    writer.u8(Op.layoutRoot).int(1)
    writer.u8(Op.layoutBox).int(5).int(-1).int(1).int(1)
    writer.u8(Op.modifierWidth).int(NativeSwiftDimensionType.exact).float(100)
    writer.u8(Op.modifierHeight).int(NativeSwiftDimensionType.exact).float(100)
    writer.u8(Op.layoutContent).int(-1).u8(Op.containerEnd)
    writer.u8(Op.containerEnd).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: writer.data)
    _ = try session.snapshot()
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.floats[90] == 100, "\(String(describing: values.floats[90]))")
    #expect(values.integers[91] == 100, "\(String(describing: values.integers[91]))")
  }

  /// Kotlin: a float constant at EPOCH_SECOND claims the id; `load` publishes integers = trunc(f).
  @Test func aFloatConstantAtEpochSecondKeepsItsIntegerViewUnderAWallClock() throws {
    let epoch = NativeSwiftSystemVariables.epochSecond
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(epoch).float(1000.5)
    document.u8(Op.integerExpression).int(95).int(1).int(1).int(epoch)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    let clock = NativeSwiftWallClock(epochMillis: KotlinReference.epochMillis)
    for _ in 0..<2 {
      let values = try session.probeValues(timeSeconds: 0, wallClock: clock)
      #expect(values.floats[epoch] == 1000.5)
      #expect(values.integers[epoch] == 1000, "\(String(describing: values.integers[epoch]))")
      #expect(values.integers[95] == 1000, "\(String(describing: values.integers[95]))")
    }
  }

  /// Kotlin `RcDataMapLookup` of a float entry -> `storeFloat(outId, ...)`: the output's integer
  /// view is the float truncated.
  @Test func aDataMapFloatLookupIsAlsoItsIntegerView() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(71).float(2.75)
    document.text(id: 72, "k")
    let key = Array("k".utf8)
    document.u8(Op.idMap).int(70).int(1).int(key.count).raw(Data(key))
      .u8(NativeSwiftDataMapType.float).int(71)
    document.u8(Op.dataMapLookup).int(73).int(70).int(72)
    document.u8(Op.integerExpression).int(74).int(1).int(1).int(73)
    document.u8(Op.layoutRoot).int(1).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    _ = try session.snapshot(timeSeconds: 0)
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.floats[73] == 2.75, "\(String(describing: values.floats[73]))")
    withKnownIssue("a data-map float lookup publishes no integer view") {
      #expect(values.integers[73] == 2, "\(String(describing: values.integers[73]))")
      #expect(values.integers[74] == 2, "\(String(describing: values.integers[74]))")
    }
  }

  /// Kotlin's particle runtime publishes each register through `setFloat`: the register's integer
  /// view is the float truncated.
  @Test func aParticleRegisterIsAlsoItsIntegerView() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    // ParticleDefine(id 9, one particle, one variable 70 starting at 2.5).
    document.u8(Op.particleDefine).int(9).int(1).int(1).int(70).int(1).float(2.5)
    // ParticleLoop: `value + 1` each frame.
    document.u8(Op.particleLoop).int(9).int(1).float(0).int(1).int(3)
      .int(Writer.nanReference(70)).float(1)
      .int(Writer.floatOperator(NativeSwiftFloatOperator.add))
    document.u8(Op.integerExpression).int(71).int(1).int(1).int(70)
    document.u8(Op.layoutRoot).int(1).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    _ = try session.snapshot(timeSeconds: 0)
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.floats[70] == 3.5, "\(String(describing: values.floats[70]))")
    #expect(values.integers[70] == 3, "\(String(describing: values.integers[70]))")
    #expect(values.integers[71] == 3, "\(String(describing: values.integers[71]))")
  }

  /// Kotlin's particle runtime writes each register through `setFloat` every frame: an integer
  /// write to a register is replaced by the next frame's particle, not kept as an override.
  @Test func aParticleRegisterMovesOnAfterAnIntegerWrite() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.particleDefine).int(9).int(1).int(1).int(70).int(1).float(2.5)
    document.u8(Op.particleLoop).int(9).int(1).float(0).int(1).int(3)
      .int(Writer.nanReference(70)).float(1)
      .int(Writer.floatOperator(NativeSwiftFloatOperator.add))
    document.namedVariable(id: 70, type: Named.int, name: "register")
    document.u8(Op.layoutRoot).int(1).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    _ = try session.snapshot(timeSeconds: 0)
    #expect(session.setInteger(9, for: "register"))
    _ = try session.snapshot(timeSeconds: 1)
    let values = try session.probeValues(timeSeconds: 1)
    #expect(values.floats[70] == 4.5, "\(values.floats[70] as Any)")
    #expect(values.integers[70] == 4, "\(values.integers[70] as Any)")
  }

  /// A `LayoutCompute` body's float constant is a declaration in wire order too: a later one
  /// replaces an earlier top-level constant at the same id, as the reference's data pass loads it.
  @Test func aLayoutComputeConstantAfterATopLevelOneWins() throws {
    let bounds = NativeSwiftIDRegion.array + 42
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(51).float(1.5)
    document.u8(Op.integerExpression).int(53).int(1).int(1).int(51)
    document.u8(Op.layoutRoot).int(1)
    document.u8(Op.layoutBox).int(3).int(-1).int(1).int(1)
    document.u8(Op.layoutCompute).int(NativeSwiftLayoutComputeType.measure).int(bounds).u8(0)
    document.u8(Op.dataFloat).int(51).float(6.5)
    document.u8(Op.containerEnd)
    document.u8(Op.containerEnd).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.floats[51] == 6.5, "\(values.floats[51] as Any)")
    #expect(values.integers[51] == 6 && values.integers[53] == 6, "\(values.integers[53] as Any)")
  }

  /// A computed float keeps its float view across resolutions: its integer view is never
  /// projected back over it, even by a producer the settle pass does not run again.
  @Test func aComputedFloatKeepsItsFloatViewAcrossResolutions() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.colorConstant).int(60).int(Int(Int32(bitPattern: 0xFF80_8080)))
    // 80 = the red channel of 60, 128 / 255.
    document.u8(Op.attributeColor).int(80).int(60).u16(NativeSwiftColorAttributeType.red)
    document.u8(Op.integerExpression).int(81).int(1).int(1).int(80)
    // 82 reads 81, so the integer expressions run again once the floats are known.
    document.u8(Op.animatedFloat).int(82).int(1).int(Writer.nanReference(81))
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    for time in [0.0, 0.5, 1.0] {
      let values = try session.probeValues(timeSeconds: time)
      #expect(
        values.floats[80] == Float(128) / 255,
        "t=\(time): \(String(describing: values.floats[80]))")
      #expect(values.integers[80] == 0 && values.integers[81] == 0 && values.floats[82] == 0)
    }
  }

  // MARK: - Integer slots the float evaluator projects

  /// A host integer write to a variable the document declares with no value of its own: a float
  /// expression reads it through the projection, on every resolution.
  @Test func aHostIntegerWithNoDocumentValueIsProjectedToFloats() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.namedVariable(id: 9, type: Named.int, name: "USER:count")
    document.u8(Op.animatedFloat).int(90).int(1).int(Writer.nanReference(9))
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(session.setInteger(7, for: "count"))
    for time in [0.0, 1.0] {
      let values = try session.probeValues(timeSeconds: time)
      #expect(values.floats[90] == 7, "t=\(time): \(String(describing: values.floats[90]))")
    }
  }

  /// An `ID_LOOKUP` output is an integer slot: a float expression reads it on every resolution,
  /// not only on the one where the lookup moved.
  @Test func anIdLookupOutputIsProjectedToFloats() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.idList).int(40).int(3).int(11).int(12).int(13)
    document.u8(Op.idLookup).int(41).int(40).float(1)
    document.u8(Op.animatedFloat).int(90).int(1).int(Writer.nanReference(41))
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    for time in [0.0, 1.0] {
      let values = try session.probeValues(timeSeconds: time)
      #expect(values.integers[41] == 12, "t=\(time): \(String(describing: values.integers[41]))")
      #expect(values.floats[90] == 12, "t=\(time): \(String(describing: values.floats[90]))")
    }
  }
}
