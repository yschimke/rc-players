import Foundation
import Testing

@_spi(Conformance) @_spi(RemoteComposeController) @testable import RcNativePlayerCore

/// Clearing a host value (`clearNamedValue`) and the long and float-array host values, against
/// the Kotlin player's specification: `RcNamedValueLifecycleTest`, `RcNamedValueOverrideTest` and
/// `RcSystemVariableTest` in rc-player-runtime. "The document's recorded value" is the value the
/// document carries, never one a host seeded when the session opened.
@Suite struct NativeSwiftNamedValueClearTests {
  typealias Op = NativeSwiftWireOpcode
  typealias Kind = NativeSwiftNamedVariableType

  /// `RcNamedValueLifecycleTest.document()`: float "USER:temperature" 21.5 (id 9), string
  /// "USER:day" "Tuesday" (id 10), colour "USER:accent" 0xFF102040 (id 11), plus integer
  /// "USER:count" 3 (id 12).
  private func lifecycleDocument() throws -> NativeSwiftDocumentSession {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(9).float(21.5)
    document.namedVariable(id: 9, type: Kind.float, name: "USER:temperature")
    document.text(id: 10, "Tuesday")
    document.namedVariable(id: 10, type: Kind.string, name: "USER:day")
    document.u8(Op.colorConstant).int(11).int(Int(Int32(bitPattern: 0xFF10_2040)))
    document.namedVariable(id: 11, type: Kind.color, name: "USER:accent")
    document.u8(Op.dataInt).int(12).int(3)
    document.namedVariable(id: 12, type: Kind.int, name: "USER:count")
    return try NativeSwiftDocumentSession.open(data: document.data, toleratingRootlessData: true)
  }

  private func values(_ session: NativeSwiftDocumentSession) throws -> NativeSwiftProbeValues {
    try session.probeValues(timeSeconds: 0)
  }

  // MARK: - RcNamedValueLifecycleTest

  /// `clearingRestoresWhatTheDocumentRecorded` (:37).
  @Test func clearingRestoresWhatTheDocumentRecorded() throws {
    let session = try lifecycleDocument()
    #expect(session.setFloat(-4, for: "USER:temperature"))
    #expect(try values(session).floats[9] == -4)

    #expect(session.clearNamedValue("USER:temperature"))

    #expect(try values(session).floats[9] == 21.5)
    #expect(session.writtenNamedValues().isEmpty, "a cleared value is still carried")
  }

  /// `clearingRestoresTheDocumentsValueEvenWhenTheHostSeededADifferentOneAtConstruction` (:48),
  /// for every type: a session opens seeded through `restoreNamedValue`, as a reopen seeds it.
  @Test func clearingRestoresTheDocumentsValueEvenWhenTheHostSeededADifferentOne() throws {
    let session = try lifecycleDocument()
    session.restoreNamedValue(.float(100), forDeclaredName: "USER:temperature")
    session.restoreNamedValue(.text("Seeded"), forDeclaredName: "USER:day")
    session.restoreNamedValue(.color(0xFF00_FF00), forDeclaredName: "USER:accent")
    session.restoreNamedValue(.integer(9), forDeclaredName: "USER:count")
    let seeded = try values(session)
    #expect(seeded.floats[9] == 100 && seeded.texts[10] == "Seeded")
    #expect(seeded.colors[11] == 0xFF00_FF00 && seeded.integers[12] == 9)

    for name in ["USER:temperature", "USER:day", "USER:accent", "USER:count"] {
      #expect(session.clearNamedValue(name), "\(name) was not cleared")
    }

    let restored = try values(session)
    #expect(restored.floats[9] == 21.5)
    #expect(restored.texts[10] == "Tuesday")
    #expect(restored.colors[11] == 0xFF10_2040)
    #expect(restored.integers[12] == 3)
    #expect(session.writtenNamedValues().isEmpty, "a cleared value is still carried")
  }

  /// `clearingATextRemovesTheOverrideRatherThanWritingTheOldStringBack` (:61): the next frames
  /// show the document's text, and the session no longer reports it as written, so a reopen does
  /// not carry it.
  @Test func clearingATextRemovesTheOverrideRatherThanWritingTheOldStringBack() throws {
    let session = try lifecycleDocument()
    #expect(session.setString("Wednesday", for: "USER:day"))
    #expect(try values(session).texts[10] == "Wednesday")

    #expect(session.clearNamedValue("USER:day"))

    #expect(try values(session).texts[10] == "Tuesday")
    #expect(try values(session).texts[10] == "Tuesday")
    #expect(!session.writtenNamedValues().contains { $0.name == "USER:day" })
  }

  /// `clearingAColourRestoresTheRecordedArgb` (:77).
  @Test func clearingAColourRestoresTheRecordedArgb() throws {
    let session = try lifecycleDocument()
    #expect(session.setColor(0xFF00_FF00, for: "USER:accent"))
    #expect(try values(session).colors[11] == 0xFF00_FF00)

    #expect(session.clearNamedValue("USER:accent"))

    #expect(try values(session).colors[11] == 0xFF10_2040)
  }

  /// `clearingIsIdempotentAndUndeclaredNamesAreIgnored` (:88).
  @Test func clearingIsIdempotentAndUndeclaredNamesAreIgnored() throws {
    let session = try lifecycleDocument()
    #expect(session.clearNamedValue("USER:temperature"))
    #expect(session.clearNamedValue("USER:temperature"))
    #expect(try values(session).floats[9] == 21.5)

    #expect(!session.setFloat(1, for: "USER:nothing"))
    #expect(!session.clearNamedValue("USER:nothing"))
    #expect(try values(session).floats[9] == 21.5)
  }

  /// An integer cleared after a float write reached it goes back to the document's value in both
  /// views (they share the id).
  @Test func clearingAnIntegerRestoresBothViews() throws {
    let session = try lifecycleDocument()
    #expect(session.setFloat(7.5, forID: 12))
    #expect(session.setInteger(8, for: "count"))

    #expect(session.clearNamedValue("count"))

    let restored = try values(session)
    #expect(restored.integers[12] == 3)
    #expect(restored.floats[12] == 3, "the float view a float write gave the id is left behind")
    #expect(session.writtenNamedValues().isEmpty)
  }

  /// Clearing an integer variable whose id also declared a float constant restores the seeded
  /// float view too, not only the integer slot `storeInteger` wrote.
  @Test func clearingAnIntegerRestoresASeededFloatViewOnTheSameID() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(12).float(2.5)
    document.namedVariable(id: 12, type: Kind.int, name: "USER:count")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    let initial = try values(session)
    try #require(initial.floats[12] == 2.5 && initial.integers[12] == 2)

    #expect(session.setInteger(8, for: "count"))
    let written = try values(session)
    #expect(written.integers[12] == 8 && written.floats[12] == 8)

    #expect(session.clearNamedValue("count"))
    let restored = try values(session)
    #expect(restored.integers[12] == 2)
    #expect(restored.floats[12] == 2.5)
    #expect(session.writtenNamedValues().isEmpty)
  }

  /// A float variable a document action wrote (a click's float action, which publishes the
  /// integer view too, as `RcPlayerState.storeFloat` does) is the document's again once cleared,
  /// in both views, as the Kotlin clear writes the recorded float back through `setFloat`.
  @Test func clearingAFloatAnActionWroteRestoresBothViews() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(22).float(1.5)
    document.namedVariable(id: 22, type: Kind.float, name: "USER:t")
    document.u8(200).int(1).u8(202).int(3).int(-1).int(1).int(4)
    document.u8(59).u8(222).int(22).float(2.7).u8(214).u8(214).u8(214)
    let fresh = try values(try NativeSwiftDocumentSession.open(data: document.data))
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    _ = try session.click(componentID: 3, timeSeconds: 0)
    let clicked = try values(session)
    try #require(clicked.floats[22] == 2.7 && clicked.integers[22] == 2)

    #expect(session.clearNamedValue("t"))

    let restored = try values(session)
    #expect(restored.floats[22] == 1.5)
    #expect(restored.integers[22] == fresh.integers[22], "the action's integer view stayed")
    #expect(session.writtenNamedValues().isEmpty)
  }

  /// A float variable a document action wrote as an integer (`storeInteger`, as
  /// `RcPlayerState.setInteger`, replaces the float view too) is the document's again once cleared,
  /// in both views: the Kotlin clear writes the recorded float back through `setFloat`, so the
  /// float is 1.5 again and the integer its view, not the action's 9.
  @Test func clearingAFloatAnIntegerActionWroteRestoresBothViews() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(22).float(1.5)
    document.namedVariable(id: 22, type: Kind.float, name: "USER:t")
    document.u8(200).int(1).u8(202).int(3).int(-1).int(1).int(4)
    document.u8(59).u8(212).int(22).int(9).u8(214).u8(214).u8(214)
    let fresh = try values(try NativeSwiftDocumentSession.open(data: document.data))
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    _ = try session.click(componentID: 3, timeSeconds: 0)
    let clicked = try values(session)
    try #require(clicked.floats[22] == 9 && clicked.integers[22] == 9)

    #expect(session.clearNamedValue("t"))

    let restored = try values(session)
    #expect(restored.floats[22] == 1.5)
    #expect(restored.integers[22] == fresh.integers[22])
    #expect(fresh.integers[22] == 1)
  }

  /// `clearNamedValue` restores the document's recorded value even when it was the document's
  /// own action, not the host, that changed it — `RcPlayerState.clearNamedValue` writes the
  /// recorded value back whoever wrote last.
  @Test func clearingRestoresTheRecordedValueOverADocumentActionsWrite() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataInt).int(22).int(3)
    document.namedVariable(id: 22, type: Kind.int, name: "USER:count")
    document.u8(200).int(1).u8(202).int(3).int(-1).int(1).int(4)
    document.u8(59).u8(212).int(22).int(9).u8(214).u8(214).u8(214)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    _ = try session.click(componentID: 3, timeSeconds: 0)
    try #require(try values(session).integers[22] == 9)

    #expect(session.clearNamedValue("count"))

    #expect(try values(session).integers[22] == 3)
  }

  /// A data-map entry of the long type is looked up from the long, as the Kotlin player reads
  /// `longs` (truncated to an integer), so a host's long reaches it and a clear gives it back.
  @Test func aDataMapLongLookupReadsTheHostsLong() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataLong).int(40).int64(7)
    document.namedVariable(id: 40, type: Kind.long, name: "USER:n")
    document.text(id: 41, "k")
    let key = Array("k".utf8)
    document.u8(Op.idMap).int(42).int(1).int(key.count).raw(Data(key))
      .u8(NativeSwiftDataMapType.long).int(40)
    document.u8(Op.dataMapLookup).int(43).int(42).int(41)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(try values(session).integers[43] == 7)

    #expect(session.setLong(123, for: "n"))
    #expect(try values(session).integers[43] == 123)
    // Truncated to the integer's 32 bits, as Kotlin's `Long.toInt()`.
    #expect(session.setLong(0x1_0000_0005, for: "n"))
    #expect(try values(session).integers[43] == 5)

    #expect(session.clearNamedValue("n"))
    #expect(try values(session).integers[43] == 7)
  }

  /// A long the document declares by name only, with no `LongConstant`: a time attribute reads
  /// the clock (AndroidX `TimeAttribute.paint` with no constant), a host long replaces it, and a
  /// clear hands it back to the clock rather than to epoch 0.
  @Test func aLongWithNoConstantClearsBackToTheClock() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.namedVariable(id: 13, type: Kind.long, name: "USER:when")
    document.u8(Op.attributeTime).int(24).int(13).u16(NativeSwiftTimeAttributeType.year).u16(0)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    // 2026-05-28T20:26:40Z.
    let clock = NativeSwiftWallClock(epochMillis: 1_780_000_000_000)
    func year() throws -> Float? {
      try session.probeValues(timeSeconds: 0, wallClock: clock).floats[24]
    }
    #expect(try year() == 2026)
    #expect(session.setLong(946_684_800_000, for: "when"))
    #expect(try year() == 2000)

    #expect(session.clearNamedValue("when"))

    #expect(try year() == 2026)
  }

  /// `aDocumentDeclaringATypeWithNoHostValueStillConstructs` (:124), for the float array the
  /// document declares with no list behind it: a float is refused, a float array stands in for
  /// the missing list, and a clear leaves no list again.
  @Test func aFloatArrayWithNoBackingList() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.namedVariable(id: 13, type: Kind.floatArray, name: "USER:series")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    func list() throws -> [Float]? {
      try session.probeFloatList(id: 13, dynamic: false, timeSeconds: 0)
    }
    #expect(try list() == nil)
    #expect(!session.setFloat(1, for: "series"))

    #expect(session.setFloatArray([1, 2], for: "series"))
    #expect(try list() == [1, 2])

    #expect(session.clearNamedValue("series"))
    #expect(try list() == nil)
    #expect(session.clearNamedValue("series"))
  }

  /// A host colour stays the host's under a dark theme (`hostColourSurvivesAThemeSwitch`); once
  /// cleared it is the document's again, so the dark palette applies.
  @Test func aClearedColourFollowsTheDarkThemeAgain() throws {
    let themed = Writer()
    themed.header(width: 100, height: 100)
    themed.u8(196).int(10).int(1).u16(0).u16(0).int(Int(Int32(bitPattern: 0xFF11_1111)))
      .int(Int(Int32(bitPattern: 0xFF22_2222)))
    themed.namedVariable(id: 10, type: Kind.color, name: "tint")
    let session = try NativeSwiftDocumentSession.open(
      data: themed.data, toleratingRootlessData: true)
    #expect(session.setColor(0xFF33_3333, for: "tint"))
    session.setRequestedTheme(NativeSwiftTheme.dark)
    #expect(try values(session).colors[10] == 0xFF33_3333)

    #expect(session.clearNamedValue("tint"))

    #expect(try values(session).colors[10] == 0xFF22_2222)
  }

  // MARK: - RcSystemVariableTest

  /// `aHostOverrideOfANamedSystemVariableHoldsUntilCleared` (:173): the cleared variable is the
  /// clock's again, not a remembered value.
  @Test func aHostOverrideOfANamedSystemVariableHoldsUntilCleared() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.namedVariable(id: NativeSwiftSystemVariables.year, type: Kind.float, name: "cal_year")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    session.restoreNamedValue(.float(1999), forDeclaredName: "cal_year")
    // 2026-05-28T20:26:40Z.
    let clock = NativeSwiftWallClock(epochMillis: 1_780_000_000_000)
    let year = NativeSwiftSystemVariables.year
    #expect(try session.probeValues(timeSeconds: 0, wallClock: clock).floats[year] == 1999)

    #expect(session.clearNamedValue("cal_year"))

    #expect(try session.probeValues(timeSeconds: 1, wallClock: clock).floats[year] == 2026)
  }

  // MARK: - RcNamedValueOverrideTest

  /// `aFloatArrayOverrideReplacesTheListAndClearingRestoresTheAuthoredOne` (:48), with the name
  /// written unqualified (:60) and the session's carry.
  @Test func aFloatArrayOverrideReplacesTheListAndClearingRestoresTheAuthoredOne() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.floatList).int(51).int(2).float(1).float(2)
    document.namedVariable(id: 51, type: Kind.floatArray, name: "USER:series")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    func list() throws -> [Float]? {
      try session.probeFloatList(id: 51, dynamic: false, timeSeconds: 0)
    }
    #expect(try list() == [1, 2])

    #expect(session.setFloatArray([5, 6, 7], for: "series"))
    #expect(try list() == [5, 6, 7])
    #expect(session.writtenNamedValues().map(\.value) == [.floatArray([5, 6, 7])])

    #expect(session.clearNamedValue("USER:series"))
    #expect(try list() == [1, 2])
    #expect(session.writtenNamedValues().isEmpty)
  }

  /// `anOverrideOfADynamicListSurvivesTheDocumentReplayingItsDeclaration` (:83): the document's
  /// two-slot dynamic list is rebuilt on every resolve; the host's three values seeded at open
  /// stand over it until cleared.
  @Test func anOverrideOfADynamicListSurvivesTheDocumentReplayingItsDeclaration() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dynamicFloatList).int(60).float(2)
    document.namedVariable(id: 60, type: Kind.floatArray, name: "USER:dyn")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(session.restoreNamedValue(.floatArray([1, 2, 3]), forDeclaredName: "USER:dyn"))
    func list(_ time: TimeInterval) throws -> [Float]? {
      try session.probeFloatList(id: 60, dynamic: true, timeSeconds: time)
    }
    #expect(try list(0) == [1, 2, 3])
    #expect(try list(1) == [1, 2, 3])

    #expect(session.clearNamedValue("dyn"))
    #expect(try list(2) == [0, 0])
  }

  /// `aColourOverrideSurvivesTheExpressionThatDefaultsItAndClearingHandsItBack` (:110): the host's
  /// override stands over the colour expression that defaults it, and clearing hands it back.
  @Test func clearingAColourAnExpressionDefaultsHandsItBackToTheExpression() throws {
    let red: UInt32 = 0xFFFF_0000
    let blue: UInt32 = 0xFF00_00FF
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.colorConstant).int(60).int(Int(Int32(bitPattern: red)))
    document.u8(Op.colorExpressions).int(61).int(NativeSwiftColorExpressionMode.idIDInterpolate)
      .int(60).int(60).float(0)
    document.namedVariable(id: 61, type: Kind.color, name: "USER:accent")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(try values(session).colors[61] == red)

    #expect(session.setColor(blue, for: "USER:accent"))
    #expect(try values(session).colors[61] == blue)

    #expect(session.clearNamedValue("USER:accent"))
    #expect(try values(session).colors[61] == red)
    #expect(session.writtenNamedValues().isEmpty)
  }

  // MARK: - Long values

  /// A long is the instant a time attribute reads: setting it moves the attribute, clearing it
  /// gives back the document's `LongConstant`. The session carries it while it is set.
  @Test func aLongDrivesTheTimeAttributeThatReadsItUntilCleared() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    // 2023-11-14T22:13:20Z.
    document.u8(Op.dataLong).int(1).int64(1_700_000_000_000)
    document.namedVariable(id: 1, type: Kind.long, name: "USER:when")
    document.u8(Op.attributeTime).int(24).int(1).u16(NativeSwiftTimeAttributeType.year).u16(0)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(try values(session).floats[24] == 2023)

    // 2000-01-01T00:00:00Z.
    #expect(session.setLong(946_684_800_000, for: "when"))
    #expect(try values(session).floats[24] == 2000)
    #expect(session.writtenNamedValues().map(\.value) == [.long(946_684_800_000)])

    #expect(session.clearNamedValue("when"))
    #expect(try values(session).floats[24] == 2023)
    #expect(session.writtenNamedValues().isEmpty)
  }

  /// Long and float-array writes are typed against the declarations, as the other setters are.
  @Test func longAndFloatArrayWritesAreTypedAgainstTheDeclarations() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataLong).int(1).int64(5)
    document.namedVariable(id: 1, type: Kind.long, name: "when")
    document.u8(Op.floatList).int(2).int(1).float(1)
    document.namedVariable(id: 2, type: Kind.floatArray, name: "series")
    document.u8(Op.dataFloat).int(3).float(1)
    document.namedVariable(id: 3, type: Kind.float, name: "scalar")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    let declarations = session.namedVariableDeclarations

    #expect(declarations.acceptsLong(for: "when"))
    #expect(!declarations.acceptsLong(for: "series") && !declarations.acceptsLong(for: "scalar"))
    #expect(!session.setLong(1, for: "scalar"))
    #expect(!session.setFloat(1, for: "when"))
    #expect(!session.setLong(1, for: "missing"))

    let limit = NativeSwiftDocumentDecoder.maximumFloatListValues
    #expect(declarations.acceptsFloatArray([], for: "series"))
    #expect(declarations.acceptsFloatArray(Array(repeating: 1, count: limit), for: "series"))
    #expect(!declarations.acceptsFloatArray(Array(repeating: 1, count: limit + 1), for: "series"))
    #expect(!declarations.acceptsFloatArray([1, .nan], for: "series"))
    #expect(!declarations.acceptsFloatArray([.infinity], for: "series"))
    #expect(!declarations.acceptsFloatArray([1], for: "scalar"))
    #expect(!session.setFloatArray([1, .nan], for: "series"))
    #expect(!session.setFloatArray([1], for: "when"))
    #expect(session.setFloatArray([4], for: "series"))
    #expect(try session.probeFloatList(id: 2, dynamic: false, timeSeconds: 0) == [4])
  }

  /// Every new type round-trips through the reopen carry: what a session wrote is what the next
  /// one restores.
  @Test func longAndFloatArrayValuesAreCarriedToTheNextSession() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataLong).int(1).int64(5)
    document.namedVariable(id: 1, type: Kind.long, name: "when")
    document.u8(Op.dynamicFloatList).int(2).float(2)
    document.namedVariable(id: 2, type: Kind.floatArray, name: "series")
    let first = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(first.setLong(-7, for: "when"))
    #expect(first.setFloatArray([1, 2, 3], for: "series"))
    let carried = first.writtenNamedValues()
    #expect(carried.map(\.name) == ["series", "when"])

    let second = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    for (name, value) in carried {
      #expect(second.restoreNamedValue(value, forDeclaredName: name))
    }
    #expect(second.writtenNamedValues().map(\.value) == [.floatArray([1, 2, 3]), .long(-7)])
  }

  /// A float array over a layout computation's dynamic bounds list is what the body reads, and is
  /// not seeded with the box; cleared, the list is the document's dynamic one again.
  @Test func aFloatArrayOverALayoutComputeBoundsListIsReadInsteadOfTheBox() throws {
    let bounds = NativeSwiftIDRegion.array + 42
    let document = Writer()
    document.header(width: 100, height: 100)
    document.namedVariable(id: bounds, type: Kind.floatArray, name: "bounds")
    document.u8(Op.layoutRoot).int(1).u8(Op.layoutBox).int(3).int(-1).int(1).int(1)
    document.u8(Op.layoutCompute).int(NativeSwiftLayoutComputeType.measure).int(bounds).u8(0)
    document.u8(Op.dynamicFloatList).int(bounds).float(6)
    document.u8(Op.containerEnd)
    document.u8(Op.containerEnd).u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    func evaluated() throws -> [Float]? {
      func find(_ node: NativeSwiftNodeSnapshot) -> NativeSwiftNodeSnapshot? {
        node.componentID == 3 ? node : node.children.lazy.compactMap(find).first
      }
      let box = try #require(find(try session.snapshot().root))
      let compute = try #require(box.layoutComputes.first)
      return compute.evaluate(x: 9, y: 9, width: 9, height: 9, parentWidth: 9, parentHeight: 9)
    }
    #expect(try evaluated() == [9, 9, 9, 9, 9, 9])

    #expect(session.setFloatArray([1, 2, 3, 4, 5, 6], for: "bounds"))
    #expect(try evaluated() == [1, 2, 3, 4, 5, 6])

    #expect(session.clearNamedValue("bounds"))
    #expect(try evaluated() == [9, 9, 9, 9, 9, 9])
  }

  /// An image variable carries no host value: clearing one is refused, as writing one is.
  @Test func clearingAnImageVariableIsIgnored() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.namedVariable(id: 12, type: Kind.image, name: "USER:avatar")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(!session.clearNamedValue("USER:avatar"))
  }

  /// A cleared host long on an id the document gives no `LongConstant`, read as a `fromArgument`
  /// time attribute's argument and through a long data-map entry: the argument is read as now, as
  /// AndroidX `TimeAttribute.paint` reads a missing instant (`TimeAttribute.java:243-244`; its
  /// argument would cast a null and throw, `:259-260`), and the lookup reads 0 where
  /// `DataMapLookup.apply` would throw (`DataMapLookup.java:145-146`) — as on the Kotlin player
  /// (`RcNamedValueLongClearTest`).
  @Test func aClearedLongWithNoConstantStillEvaluatesItsReaders() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataLong).int(1).int64(1_700_000_000_000)
    document.namedVariable(id: 13, type: Kind.long, name: "USER:start")
    document.u8(Op.attributeTime).int(24).int(1)
      .u16(NativeSwiftTimeAttributeType.fromArgumentSeconds).u16(1).int(13)
    document.text(id: 21, "k")
    let key = Array("k".utf8)
    document.u8(Op.idMap).int(20).int(1).int(key.count).raw(Data(key))
      .u8(NativeSwiftDataMapType.long).int(13)
    document.u8(Op.dataMapLookup).int(30).int(20).int(21)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(session.setLong(1_699_999_990_000, for: "start"))
    let set = try values(session)
    #expect(set.floats[24] == 10)
    #expect(set.integers[30] == Int(Int32(truncatingIfNeeded: Int64(1_699_999_990_000))))

    #expect(session.clearNamedValue("start"))

    let cleared = try values(session)
    #expect(cleared.integers[30] == 0)
    // Measured from now: the document's instant is 5 s before this frame's clock.
    let now = NativeSwiftWallClock(epochMillis: 1_700_000_005_000)
    #expect(try session.probeValues(timeSeconds: 0, wallClock: now).floats[24] == -5)
  }

  /// Once its argument is read as now, the attribute moves with the clock, so the snapshot asks for
  /// the wall-clock refresh it did not need while the host's long was set — as the Kotlin player
  /// counts it a clock read (`RcPlayerState.applyTimeAttribute`, `noteTimeAttributeClockRead`).
  @Test func aClearedArgumentLongAsksForTheWallClockRefresh() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataLong).int(1).int64(1_700_000_000_000)
    document.namedVariable(id: 13, type: Kind.long, name: "USER:start")
    document.u8(Op.attributeTime).int(24).int(1)
      .u16(NativeSwiftTimeAttributeType.fromArgumentSeconds).u16(1).int(13)
    document.u8(Op.layoutRoot).int(1)
    document.u8(Op.containerEnd)
    let session = try NativeSwiftDocumentSession.open(data: document.data)
    let now = NativeSwiftWallClock(epochMillis: 1_700_000_005_000)
    #expect(session.setLong(1_699_999_990_000, for: "start"))
    #expect(try !session.snapshot(wallClock: now).needsWallClockRefresh)

    #expect(session.clearNamedValue("start"))

    #expect(try session.snapshot(wallClock: now).needsWallClockRefresh)
  }

  /// The interval is a `long` subtraction in AndroidX (`TimeAttribute.java:260`), which wraps, and
  /// so is the Kotlin player's (`anIntervalBetweenTheExtremeLongsWrapsAsAndroidXDoes`): Int64.max
  /// minus Int64.min is -1 ms, not 1.8e19 ms.
  @Test func anIntervalBetweenTheExtremeLongsWrapsAsAndroidXDoes() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataLong).int(1).int64(Int64.max)
    document.u8(Op.dataLong).int(13).int64(Int64.min)
    document.u8(Op.attributeTime).int(25).int(1)
      .u16(NativeSwiftTimeAttributeType.fromArgumentHours).u16(1).int(13)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(try values(session).floats[25] == Float(-1.0 * 0.001 / 3600.0))
  }
}
