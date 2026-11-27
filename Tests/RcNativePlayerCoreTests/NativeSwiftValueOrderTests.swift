import Foundation
import Testing

@testable @_spi(Conformance) @_spi(RemoteComposeController) import RcNativePlayerCore

/// Resolution order and one-id interleavings, against what the Kotlin `RcPlayerState` gives: it
/// runs `applyContentStateOperation` once per pass in wire order, integer expressions read
/// `integerVariable`, float expressions publish both views through `setFloat`, integer expressions
/// through `setInteger`, and a document action's integer write is an `overrideInteger` that
/// `applyIntegerExpression` respects.
@Suite struct NativeSwiftValueOrderTests {
  private typealias Op = NativeSwiftWireOpcode
  private typealias Named = NativeSwiftNamedVariableType

  static let floatAdd = Writer.floatOperator(NativeSwiftFloatOperator.add)
  static let floatDiv = Writer.floatOperator(NativeSwiftFloatOperator.div)

  // MARK: - Resolution order: a cycle and a deep chain

  /// int 60 = [70] + 1; float 70 = [60] + 1. One Kotlin pass: 60 = 1, 70 = 2; each further pass
  /// adds 2 to both.
  static func cycleDocument() -> Data {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.integerExpression).int(60).int(0b101).int(3).int(70).int(1).int(
      Writer.integerOperator(NativeSwiftIntegerOperator.add))
    document.u8(Op.animatedFloat).int(70).int(3).int(Writer.nanReference(60)).int(Writer.literal(1))
      .int(floatAdd)
    return document.data
  }

  @Test func cycleIntFloatIntResolvesLikeOneWirePass() throws {
    let session = try NativeSwiftDocumentSession.open(
      data: Self.cycleDocument(), toleratingRootlessData: true)
    let first = try session.probeValues(timeSeconds: 0)
    #expect(
      first.integers[60] == 1 && first.floats[70] == 2,
      "Kotlin 60=1 70=2: \(first.integers[60] as Any) \(first.floats[70] as Any)"
    )
  }

  /// F100 = 1.5; I101 = [100] + 1; F102 = [101] + 0.5; ... I109; F110. Wire order is forward, so
  /// a single Kotlin pass yields I109 = 6, F110 = 6.5 on the first frame.
  static func chainDocument(withBox: Bool) -> Data {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.animatedFloat).int(100).int(1).int(Writer.literal(1.5))
    for step in 0..<5 {
      let floatIn = 100 + step * 2
      let intOut = floatIn + 1
      let floatOut = floatIn + 2
      document.u8(Op.integerExpression).int(intOut).int(0b101).int(3).int(floatIn).int(1)
        .int(Writer.integerOperator(NativeSwiftIntegerOperator.add))
      document.u8(Op.animatedFloat).int(floatOut).int(3).int(Writer.nanReference(intOut))
        .int(Writer.literal(0.5)).int(floatAdd)
    }
    if withBox {
      document.u8(Op.layoutRoot).int(1)
      document.u8(Op.layoutBox).int(5).int(-1).int(1).int(1)
      document.u8(Op.modifierWidth).int(NativeSwiftDimensionType.exact)
        .int(Writer.nanReference(110))
      document.u8(Op.modifierHeight).int(NativeSwiftDimensionType.exact).float(10)
      document.u8(Op.layoutContent).int(-1).u8(Op.containerEnd)
      document.u8(Op.containerEnd).u8(Op.containerEnd)
    }
    return document.data
  }

  @Test func fiveDeepIntFloatChainSettlesOnTheFirstResolution() throws {
    let session = try NativeSwiftDocumentSession.open(
      data: Self.chainDocument(withBox: false), toleratingRootlessData: true)
    let values = try session.probeValues(timeSeconds: 0)
    let ints = [101, 103, 105, 107, 109].map { values.integers[$0] ?? -1 }
    let floats = [102, 104, 106, 108, 110].map { values.floats[$0] ?? -1 }
    withKnownIssue("the session resolves integer expressions before the floats they read") {
      #expect(ints == [2, 3, 4, 5, 6], "Kotlin [2,3,4,5,6]; Swift \(ints)")
      #expect(floats == [2.5, 3.5, 4.5, 5.5, 6.5], "Kotlin [2.5..6.5]; Swift \(floats)")
    }
  }

  /// The drawn width on the first frame, and on a later frame of the same static document.
  @Test func fiveDeepChainDrawsTheSettledWidthOnTheFirstFrame() throws {
    let session = try NativeSwiftDocumentSession.open(data: Self.chainDocument(withBox: true))
    let first = try #require(try session.snapshot(timeSeconds: 0).root.node(withComponentID: 5))
    let later = try #require(try session.snapshot(timeSeconds: 1).root.node(withComponentID: 5))
    withKnownIssue("the session resolves integer expressions before the floats they read") {
      #expect(first.widthValue == 6.5, "Kotlin 6.5; Swift first frame \(first.widthValue)")
      #expect(later.widthValue == 6.5, "Kotlin 6.5; Swift later frame \(later.widthValue)")
    }
  }

  /// int 60 = [TIME_IN_SEC]; animated float 51 = [60] over 1 s. Kotlin: target = seconds into the
  /// hour from the first frame, never animating (first target sets the initial value too).
  @Test func animatedFloatOverAClockIntegerShowsTheClock() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.integerExpression).int(60).int(1).int(1).int(
      NativeSwiftSystemVariables.timeInSeconds)
    document.u8(Op.animatedFloat).int(51).int((2 << 16) | 1).int(Writer.nanReference(60))
      .float(1).int(4)
    document.u8(Op.layoutRoot).int(1).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    // 30:45 into the hour -> 1845 s.
    let clock = NativeSwiftWallClock(epochMillis: KotlinReference.epochMillis)
    for time in [0.0, 0.5, 2.0] {
      let snapshot = try session.snapshot(timeSeconds: time, wallClock: clock)
      let values = try session.probeValues(timeSeconds: time, wallClock: clock)
      #expect(
        values.integers[60] == 1845, "t=\(time) int \(String(describing: values.integers[60]))")
      #expect(
        values.floats[51] == 1845,
        "t=\(time) Kotlin 1845; Swift \(String(describing: values.floats[51]))")
      #expect(!snapshot.needsContinuousFrames, "t=\(time) still animating")
    }
  }

  /// Float 50 = 4; int 60 = [50]; float 52 = 8 / [60]. Kotlin: 52 = 2.
  @Test func divisionByAnIntegerViewOfAFloatResolves() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.animatedFloat).int(50).int(1).int(Writer.literal(4))
    document.u8(Op.integerExpression).int(60).int(1).int(1).int(50)
    document.u8(Op.animatedFloat).int(52).int(3).int(Writer.literal(8))
      .int(Writer.nanReference(60)).int(Self.floatDiv)
    document.u8(Op.layoutRoot).int(1).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    let result = Result { try session.probeValues(timeSeconds: 0) }
    switch result {
    case .success(let values):
      #expect(values.floats[52] == 2, "\(String(describing: values.floats[52]))")
    case .failure(let error):
      withKnownIssue("the integer view of a computed float reads 0") {
        Issue.record("Kotlin 52 = 2; Swift threw \(error)")
      }
    }
  }

  // MARK: - Writes onto an integer expression's output

  /// Click: ValueIntegerChange(60 := 9) where 60 is an integer expression ([52] + 0). Kotlin
  /// `overrideInteger` adds 60 to `overriddenIntegers`, and `applyIntegerExpression` skips it.
  @Test func actionIntegerWriteOntoAnIntegerExpressionSticks() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataInt).int(52).int(4)
    document.u8(Op.integerExpression).int(60).int(0b101).int(3).int(52).int(0).int(
      Writer.integerOperator(NativeSwiftIntegerOperator.add))
    document.u8(Op.layoutRoot).int(1)
    document.u8(Op.layoutBox).int(3).int(-1).int(1).int(4)
    document.u8(Op.modifierClick).u8(Op.valueIntegerChangeAction).int(60).int(9)
      .u8(Op.containerEnd)
    document.u8(Op.containerEnd).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    _ = try session.click(componentID: 3, timeSeconds: 0)
    let values = try session.probeValues(timeSeconds: 0)
    withKnownIssue("a document action's integer write is not kept over its declaring expression") {
      #expect(
        values.integers[60] == 9, "Kotlin 9; Swift \(String(describing: values.integers[60]))")
    }
  }

  /// A host integer write by name onto an integer expression: Kotlin `setNamedValue` ->
  /// `setInteger` (not an override), so the next pass's expression replaces it. Same in Swift.
  @Test func hostIntegerWriteOntoAnIntegerExpressionIsReplacedAsInKotlin() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataInt).int(52).int(4)
    document.u8(Op.integerExpression).int(60).int(1).int(1).int(52)
    document.namedVariable(id: 60, type: Named.int, name: "derived")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    _ = session.setInteger(9, for: "derived")
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.integers[60] == 4, "Kotlin 4; Swift \(String(describing: values.integers[60]))")
    // `setInteger` writes the float view too, and the expression rewrites both.
    #expect(values.floats[60] == 4, "\(values.floats[60] as Any)")
  }

  /// An integer expression and a float expression writing the same id. Kotlin: the later in wire
  /// order wins both views (setFloat/setInteger each write both).
  @Test(arguments: [true, false])
  func integerAndFloatExpressionAtOneId(floatFirst: Bool) throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataInt).int(52).int(4)
    func floatExpression() {
      document.u8(Op.animatedFloat).int(60).int(1).int(Writer.literal(7.5))
    }
    func integerExpression() { document.u8(Op.integerExpression).int(60).int(1).int(1).int(52) }
    if floatFirst {
      floatExpression()
      integerExpression()
    } else {
      integerExpression()
      floatExpression()
    }
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    let values = try session.probeValues(timeSeconds: 0)
    let expected: (Int, Float) = floatFirst ? (4, 4) : (7, 7.5)
    withKnownIssue("integer expressions all run before float expressions") {
      #expect(
        values.integers[60] == expected.0 && values.floats[60] == expected.1,
        "floatFirst=\(floatFirst): \(values.integers[60] as Any) \(values.floats[60] as Any)"
      )
    }
  }

  // MARK: - Constants and expressions at one id

  /// Duplicates and interleavings at one id, against `RcSeedValues.load`.
  @Test(arguments: [
    // (sequence, expected integer, expected float); "f:x" float constant, "i:n" integer constant
    (["f:1.5", "f:2.5"], 2, Float(2.5)),
    (["i:7", "i:9"], 9, Float(9)),
    (["i:7", "f:2.75", "i:7"], 7, Float(7)),
    (["f:2.75", "i:2"], 2, Float(2.75)),
    (["f:2.75", "i:2", "f:3.25"], 3, Float(3.25)),
    (["i:2", "f:2.75", "i:2", "i:5"], 5, Float(5)),
  ])
  func constantsReplayInWireOrder(sequence: [String], integer: Int, float: Float) throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    for item in sequence {
      let value = String(item.dropFirst(2))
      if item.hasPrefix("f:") {
        document.u8(Op.dataFloat).int(51).float(Float(value)!)
      } else {
        document.u8(Op.dataInt).int(51).int(Int(value)!)
      }
    }
    document.u8(Op.integerExpression).int(53).int(1).int(1).int(51)
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(
      values.integers[51] == integer && values.floats[51] == float
        && values.integers[53] == integer,
      "\(sequence): \(values.integers[51] as Any) \(values.floats[51] as Any)"
    )
  }

  /// A float constant and an integer expression at one id, either order. Kotlin: constants load
  /// once; the expression's `setInteger` rewrites both views each pass -> 4 and 4.0.
  @Test(arguments: [true, false])
  func floatConstantAndIntegerExpressionAtOneId(constantFirst: Bool) throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataInt).int(52).int(4)
    if constantFirst { document.u8(Op.dataFloat).int(60).float(7.5) }
    document.u8(Op.integerExpression).int(60).int(1).int(1).int(52)
    if !constantFirst { document.u8(Op.dataFloat).int(60).float(7.5) }
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(
      values.integers[60] == 4 && values.floats[60] == 4,
      "constantFirst=\(constantFirst): \(values.integers[60] as Any) \(values.floats[60] as Any)"
    )
  }

  /// A float expression, then a float constant at its id. Kotlin: the expression wins both views.
  @Test func floatConstantAfterAFloatExpressionAtOneId() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.animatedFloat).int(60).int(1).int(Writer.literal(7.5))
    document.u8(Op.dataFloat).int(60).float(2.25)
    document.u8(Op.integerExpression).int(61).int(1).int(1).int(60)
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    // The constant's integer view (2) is not seeded under the expression's float.
    #expect(values.integers[60] != 2, "\(values.integers[60] as Any)")
    #expect(
      values.floats[60] == 7.5 && values.integers[60] == 7 && values.integers[61] == 7,
      "Kotlin 7.5/7/7: \(values.floats[60] as Any) \(values.integers[60] as Any)"
    )
  }

  /// DATA_FLOAT 80 = 1, an 8x4 bitmap, then ATTRIBUTE_IMAGE writing its width into 80. Kotlin
  /// `applyImageAttribute` -> `setFloat(80, 8)` every pass.
  @Test func imageAttributeAfterAFloatConstantAtOneId() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(80).float(1)
    document.u8(Op.dataBitmap).int(90).int(NativeSwiftBitmapType.raw8888 << 16 | 8).int(4)
      .int(128).raw(Data(repeating: 0, count: 128))
    document.u8(Op.attributeImage).int(80).int(90).u16(NativeSwiftImageAttributeType.width).u16(0)
    document.u8(Op.integerExpression).int(81).int(1).int(1).int(80)
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(
      values.floats[80] == 8 && values.integers[81] == 8,
      "Kotlin 8/8; \(values.floats[80] as Any) \(values.integers[81] as Any)"
    )
  }

  // MARK: - Floats computed during a resolution

  /// Data-map float lookup (2.75) read by float expression 75 = [73] * 2. Kotlin `storeFloat`:
  /// 75 = 5.5; an integer expression over 73 reads 2 on the first resolution.
  @Test func dataMapFloatOutputKeepsItsFloatForExpressions() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(71).float(2.75)
    document.text(id: 72, "k")
    let key = Array("k".utf8)
    document.u8(Op.idMap).int(70).int(1).int(key.count).raw(Data(key))
      .u8(NativeSwiftDataMapType.float).int(71)
    document.u8(Op.dataMapLookup).int(73).int(70).int(72)
    document.u8(Op.integerExpression).int(74).int(1).int(1).int(73)
    document.u8(Op.animatedFloat).int(75).int(3).int(Writer.nanReference(73))
      .int(Writer.literal(2)).int(Writer.floatOperator(NativeSwiftFloatOperator.mul))
    document.u8(Op.layoutRoot).int(1).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    let first = try session.probeValues(timeSeconds: 0)
    withKnownIssue("a data-map lookup runs after the float expressions") {
      #expect(
        first.integers[74] == 2,
        "first resolution: Kotlin 2; Swift \(String(describing: first.integers[74]))")
    }
    for time in [0.0, 1.0] {
      _ = try session.snapshot(timeSeconds: time)
      let values = try session.probeValues(timeSeconds: time)
      withKnownIssue("a data-map lookup runs after the float expressions") {
        #expect(
          values.floats[75] == 5.5,
          "t=\(time) Kotlin 5.5; Swift \(String(describing: values.floats[75]))")
      }
    }
  }

  /// int 60 = DENSITY, int 61 = [60]; click: ValueIntegerChange(71 := 5), then
  /// ValueIntegerExpressionChange(70 := expr 61). Kotlin: 70 = 3 at density 3.
  @Test func aLaterActionReadsADensityIntegerAfterAnEarlierAction() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.integerExpression).int(60).int(1).int(1).int(
      NativeSwiftSystemVariables.density)
    document.u8(Op.integerExpression).int(61).int(1).int(1).int(60)
    document.u8(Op.layoutRoot).int(1)
    document.u8(Op.layoutBox).int(3).int(-1).int(1).int(4)
    document.u8(Op.modifierClick)
      .u8(Op.valueIntegerChangeAction).int(71).int(5)
      .u8(Op.valueIntegerExpressionChangeAction).long(70).long(61)
      .u8(Op.containerEnd)
    document.u8(Op.containerEnd).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    session.setHostDensity(3)
    let before = try session.probeValues(timeSeconds: 0)
    #expect(before.integers[61] == 3, "before click: \(String(describing: before.integers[61]))")
    _ = try session.click(componentID: 3, timeSeconds: 0)
    let after = try session.probeValues(timeSeconds: 0)
    #expect(after.integers[70] == 3, "Kotlin 3; Swift \(String(describing: after.integers[70]))")
  }
}
