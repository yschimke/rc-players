import Foundation
import Testing

@_spi(Conformance) @_spi(RemoteComposeController) @testable import RcNativePlayerCore

/// Clear, long and float-array writes on `NativeSwiftDocumentSession` across every host type and
/// the transitions around them. Each test asserts what the Kotlin player
/// (`RcPlayerState.clearNamedValue`, `setNamedValue`) does for the same sequence; a test that
/// follows a Kotlin test names it. The lifecycle cases themselves are in
/// `NativeSwiftNamedValueClearTests`.
@Suite struct NativeSwiftNamedValueClearMatrixTests {
  typealias Op = NativeSwiftWireOpcode
  typealias Kind = NativeSwiftNamedVariableType
  typealias Time = NativeSwiftTimeAttributeType

  /// 2026-05-28T20:26:40Z.
  static let clock = NativeSwiftWallClock(epochMillis: 1_780_000_000_000)

  /// float "USER:temperature" 21.5 (9), string "USER:day" "Tuesday" (10), colour "USER:accent"
  /// (11), int "USER:count" 3 (12), long "USER:when" 2023-11-14 (13) read by a year attribute into
  /// float 24, float list "USER:series" [1, 2] (14), image "USER:avatar" (15).
  static func everyType() throws -> NativeSwiftDocumentSession {
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
    document.u8(Op.dataLong).int(13).int64(1_700_000_000_000)
    document.namedVariable(id: 13, type: Kind.long, name: "USER:when")
    document.u8(Op.attributeTime).int(24).int(13).u16(Time.year).u16(0)
    document.u8(Op.floatList).int(14).int(2).float(1).float(2)
    document.namedVariable(id: 14, type: Kind.floatArray, name: "USER:series")
    document.namedVariable(id: 15, type: Kind.image, name: "USER:avatar")
    return try NativeSwiftDocumentSession.open(data: document.data, toleratingRootlessData: true)
  }

  static let names = [
    "USER:temperature", "USER:day", "USER:accent", "USER:count", "USER:when", "USER:series",
  ]

  /// Asserts every variable holds the document's own value.
  static func expectDocumentValues(
    _ session: NativeSwiftDocumentSession, sourceLocation: SourceLocation = #_sourceLocation
  ) throws {
    let values = try session.probeValues(timeSeconds: 0, wallClock: clock)
    #expect(values.floats[9] == 21.5, sourceLocation: sourceLocation)
    #expect(values.texts[10] == "Tuesday", sourceLocation: sourceLocation)
    #expect(values.colors[11] == 0xFF10_2040, sourceLocation: sourceLocation)
    #expect(values.integers[12] == 3, sourceLocation: sourceLocation)
    #expect(values.floats[24] == 2023, sourceLocation: sourceLocation)
    #expect(
      try session.probeFloatList(id: 14, dynamic: false, timeSeconds: 0) == [1, 2],
      sourceLocation: sourceLocation)
    #expect(session.writtenNamedValues().isEmpty, sourceLocation: sourceLocation)
  }

  // MARK: - Clear for every type

  /// Kotlin `clearNamedValue` writes `documentNamedValues` back for float/int (discarding a
  /// document action's write); `RcNamedValueLifecycleTest` :37.
  @Test func clearAfterADocumentActionWroteTheValue() throws {
    let session = try Self.everyType()
    // The path a document action / gesture writes by id.
    #expect(session.setFloat(-1, forID: 9))
    #expect(session.setFloat(7, forID: 12))
    let written = try session.probeValues(timeSeconds: 0, wallClock: Self.clock)
    try #require(written.floats[9] == -1)

    for name in ["USER:temperature", "USER:count"] { #expect(session.clearNamedValue(name)) }

    try Self.expectDocumentValues(session)
  }

  /// `RcNamedValueLifecycleTest` :48, for the two new types as well.
  @Test func clearEveryTypeAfterASeed() throws {
    let session = try Self.everyType()
    #expect(session.restoreNamedValue(.float(100), forDeclaredName: "USER:temperature"))
    #expect(session.restoreNamedValue(.text("Seeded"), forDeclaredName: "USER:day"))
    #expect(session.restoreNamedValue(.color(0xFF00_FF00), forDeclaredName: "USER:accent"))
    #expect(session.restoreNamedValue(.integer(9), forDeclaredName: "USER:count"))
    #expect(session.restoreNamedValue(.long(946_684_800_000), forDeclaredName: "USER:when"))
    #expect(session.restoreNamedValue(.floatArray([5, 6, 7]), forDeclaredName: "USER:series"))
    let seeded = try session.probeValues(timeSeconds: 0, wallClock: Self.clock)
    try #require(seeded.floats[24] == 2000)

    for name in Self.names { #expect(session.clearNamedValue(name), "\(name)") }

    try Self.expectDocumentValues(session)
  }

  /// `clearingIsIdempotentAndUndeclaredNamesAreIgnored` (:88), every type, set first.
  @Test func clearTwiceForEveryType() throws {
    let session = try Self.everyType()
    #expect(session.setFloat(1, for: "temperature"))
    #expect(session.setString("x", for: "day"))
    #expect(session.setColor(0xFF00_0000, for: "accent"))
    #expect(session.setInteger(1, for: "count"))
    #expect(session.setLong(0, for: "when"))
    #expect(session.setFloatArray([], for: "series"))
    for name in Self.names {
      #expect(session.clearNamedValue(name), "\(name)")
      #expect(session.clearNamedValue(name), "\(name) twice")
    }
    try Self.expectDocumentValues(session)
  }

  /// `aDocumentDeclaringATypeWithNoHostValueStillConstructs` (:150): an image clear is a no-op.
  @Test func clearOfAnImageNameIsANoOp() throws {
    let session = try Self.everyType()
    #expect(!session.clearNamedValue("USER:avatar"))
    #expect(!session.clearNamedValue("avatar"))
    try Self.expectDocumentValues(session)
  }

  /// Write X, clear, write X again: the second X applies (Kotlin's map diff sees a change).
  @Test func writeClearWriteTheSameValue() throws {
    let session = try Self.everyType()
    #expect(session.setLong(946_684_800_000, for: "when"))
    #expect(session.setFloatArray([4], for: "series"))
    #expect(session.clearNamedValue("when"))
    #expect(session.clearNamedValue("series"))
    #expect(session.setLong(946_684_800_000, for: "when"))
    #expect(session.setFloatArray([4], for: "series"))
    #expect(try session.probeValues(timeSeconds: 0, wallClock: Self.clock).floats[24] == 2000)
    #expect(try session.probeFloatList(id: 14, dynamic: false, timeSeconds: 0) == [4])
  }

  // MARK: - One key, one rule (R2)

  /// Kotlin `resolveName`: a bare name reaches `USER:name` when declared, else the bare name; a
  /// qualified name is taken as written. Write and clear must resolve the same way.
  @Test func bareAndUserNamesResolveTheSameForWriteAndClear() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataFloat).int(600).float(1)
    document.namedVariable(id: 600, type: Kind.float, name: "foo")
    document.u8(Op.dataFloat).int(601).float(1)
    document.namedVariable(id: 601, type: Kind.float, name: "USER:foo")
    document.u8(Op.dataLong).int(602).int64(5)
    document.namedVariable(id: 602, type: Kind.long, name: "bar")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)

    #expect(session.setFloat(5, for: "foo"))
    #expect(session.setFloat(7, forID: 600))  // the document's own write of the bare variable
    #expect(session.clearNamedValue("foo"))
    let values = try session.probeValues(timeSeconds: 0)
    #expect(values.floats[601] == 1, "the clear of \"foo\" missed USER:foo")
    #expect(values.floats[600] == 7, "the clear of \"foo\" reached the bare variable")

    #expect(session.setLong(9, for: "bar"))
    #expect(!session.clearNamedValue("USER:bar"), "USER:bar is not declared")
    #expect(session.writtenNamedValues().contains { $0.name == "bar" })
    #expect(session.clearNamedValue("bar"))
    #expect(!session.writtenNamedValues().contains { $0.name == "bar" })
  }

  // MARK: - Theme and clock

  /// Kotlin: clearing removes the colour override, so the next theme application lands.
  @Test func aColourClearedBeforeTheThemeChangesFollowsTheDarkTheme() throws {
    let themed = Writer()
    themed.header(width: 100, height: 100)
    themed.u8(196).int(10).int(1).u16(0).u16(0).int(Int(Int32(bitPattern: 0xFF11_1111)))
      .int(Int(Int32(bitPattern: 0xFF22_2222)))
    themed.namedVariable(id: 10, type: Kind.color, name: "tint")
    let session = try NativeSwiftDocumentSession.open(
      data: themed.data, toleratingRootlessData: true)
    #expect(session.setColor(0xFF33_3333, for: "tint"))
    #expect(session.clearNamedValue("tint"))
    session.setRequestedTheme(NativeSwiftTheme.dark)
    #expect(try session.probeValues(timeSeconds: 0).colors[10] == 0xFF22_2222)
    session.setRequestedTheme(NativeSwiftTheme.light)
    #expect(try session.probeValues(timeSeconds: 0).colors[10] == 0xFF11_1111)
  }

  /// `RcSystemVariableTest` :173 with an INT-typed name: Kotlin's Integer write claims the id
  /// and writes the float view too; the clear releases the claim and the clock is back.
  @Test func anIntegerNamedSystemVariableHoldsUntilCleared() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.namedVariable(id: NativeSwiftSystemVariables.year, type: Kind.int, name: "cal_year")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    let year = NativeSwiftSystemVariables.year
    #expect(try session.probeValues(timeSeconds: 0, wallClock: Self.clock).floats[year] == 2026)

    #expect(session.setInteger(1999, for: "cal_year"))
    #expect(try session.probeValues(timeSeconds: 0, wallClock: Self.clock).floats[year] == 1999)

    #expect(session.clearNamedValue("cal_year"))
    #expect(try session.probeValues(timeSeconds: 1, wallClock: Self.clock).floats[year] == 2026)
  }

  /// A float named system variable cleared, then the clock moves: it follows the clock.
  @Test func aFloatNamedSystemVariableFollowsTheClockAfterClear() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.namedVariable(
      id: NativeSwiftSystemVariables.dayOfMonth, type: Kind.float, name: "cal_day")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    let day = NativeSwiftSystemVariables.dayOfMonth
    #expect(session.setFloat(1, for: "cal_day"))
    #expect(session.clearNamedValue("cal_day"))
    #expect(try session.probeValues(timeSeconds: 0, wallClock: Self.clock).floats[day] == 28)
    let nextDay = NativeSwiftWallClock(epochMillis: Self.clock.epochMillis + 86_400_000)
    #expect(try session.probeValues(timeSeconds: 1, wallClock: nextDay).floats[day] == 29)
  }

  // MARK: - Long

  /// Int64 extremes reach every time attribute without a trap, and the instant reads back.
  @Test func int64ExtremesAsTimeAttributes() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dataLong).int(13).int64(0)
    document.namedVariable(id: 13, type: Kind.long, name: "when")
    document.u8(Op.dataLong).int(14).int64(0)
    document.namedVariable(id: 14, type: Kind.long, name: "since")
    let types = [
      Time.year, Time.monthValue, Time.dayOfMonth, Time.dayOfWeek, Time.dayOfYear, Time.hour,
      Time.minute, Time.second, Time.fromNowSeconds, Time.fromLoadSeconds,
    ]
    for (index, type) in types.enumerated() {
      document.u8(Op.attributeTime).int(30 + index).int(13).u16(type).u16(0)
    }
    document.u8(Op.attributeTime).int(29).int(13).u16(Time.fromArgumentHours).u16(1).int(14)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    for (when, since) in [
      (Int64.max, Int64.min), (Int64.min, Int64.max), (Int64.max, Int64.max), (Int64.min, 0),
    ] {
      #expect(session.setLong(when, for: "when"))
      #expect(session.setLong(since, for: "since"))
      let values = try session.probeValues(timeSeconds: 0, wallClock: Self.clock)
      #expect(values.floats[29] != nil, "fromArgumentHours \(when) \(since)")
      #expect(session.writtenNamedValues().map(\.value) == [.long(since), .long(when)])
    }
    #expect(session.clearNamedValue("when"))
    #expect(try session.probeValues(timeSeconds: 0, wallClock: Self.clock).floats[30] == 1970)
  }

  // MARK: - Float arrays

  @Test func floatArrayBoundsAndValues() throws {
    let session = try Self.everyType()
    let limit = NativeSwiftDocumentDecoder.maximumFloatListValues
    #expect(limit == 2000, "AndroidX DataListFloat.MAX_FLOAT_ARRAY")
    #expect(session.setFloatArray([], for: "series"))
    #expect(try session.probeFloatList(id: 14, dynamic: false, timeSeconds: 0) == [])
    #expect(session.setFloatArray(Array(repeating: 3, count: limit), for: "series"))
    #expect(try session.probeFloatList(id: 14, dynamic: false, timeSeconds: 0)?.count == limit)
    // Refused, as the Kotlin controller refuses them, and the previous value stands.
    #expect(!session.setFloatArray(Array(repeating: 3, count: limit + 1), for: "series"))
    #expect(!session.setFloatArray([.nan], for: "series"))
    #expect(!session.setFloatArray([.infinity], for: "series"))
    #expect(!session.setFloatArray([-.infinity], for: "series"))
    #expect(try session.probeFloatList(id: 14, dynamic: false, timeSeconds: 0)?.count == limit)
    // The same rule through the seed path a reopen uses.
    #expect(!session.restoreNamedValue(.floatArray([.nan]), forDeclaredName: "USER:series"))
    #expect(
      !session.restoreNamedValue(
        .floatArray(Array(repeating: 1, count: limit + 1)), forDeclaredName: "USER:series"))
  }

  /// A layout computation whose bounds list a host replaces with a list of another length: one too
  /// short to hold a box is not applied (nil, the box stays as measured), as the Kotlin player's
  /// `applyLayoutComputes` now skips it (`RcLayoutRenderTest`).
  @Test func layoutComputeBoundsListReplacedWithAnotherLength() throws {
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
    #expect(session.setFloatArray([], for: "bounds"))
    #expect(try evaluated() == nil)
    #expect(session.setFloatArray([1, 2, 3], for: "bounds"))
    #expect(try evaluated() == nil)
    #expect(session.setFloatArray([1, 2, 3, 4], for: "bounds"))
    #expect(try evaluated() == [1, 2, 3, 4])
    let limit = NativeSwiftDocumentDecoder.maximumFloatListValues
    #expect(session.setFloatArray(Array(repeating: 2, count: limit), for: "bounds"))
    #expect(try evaluated()?.count == limit)
    #expect(session.clearNamedValue("bounds"))
    #expect(try evaluated() == [9, 9, 9, 9, 9, 9])
  }

  /// A dynamic list replaced, then cleared: the document's list is rebuilt at its own length.
  @Test func replacingADynamicListWithAnotherLengthAndClearing() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(Op.dynamicFloatList).int(60).float(4)
    document.namedVariable(id: 60, type: Kind.floatArray, name: "USER:dyn")
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    #expect(session.setFloatArray([1], for: "dyn"))
    #expect(try session.probeFloatList(id: 60, dynamic: true, timeSeconds: 0) == [1])
    #expect(session.setFloatArray([], for: "dyn"))
    #expect(try session.probeFloatList(id: 60, dynamic: true, timeSeconds: 0) == [])
    #expect(session.clearNamedValue("dyn"))
    #expect(try session.probeFloatList(id: 60, dynamic: true, timeSeconds: 0) == [0, 0, 0, 0])
  }
}
