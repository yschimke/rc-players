import Foundation
import Testing

@_spi(Conformance) @testable import RcNativePlayerCore

@Suite struct NativeSwiftCoreTests {
  @Test func documentCore() throws {
    // A host surfaces a failed frame through `localizedDescription`, not `description`: only the
    // document-open path in NativeSession downcasts to NativeSwiftCoreError. Without
    // `LocalizedError` that goes through the NSError bridge and the user reads "The operation
    // couldn't be completed. (…NativeSwiftCoreError error 1.)" instead of the real reason.
    for sample in [
      NativeSwiftCoreError.malformed(offset: 12, reason: "sample"),
      NativeSwiftCoreError.unsupported(opcode: 81, offset: 4, reason: "sample"),
    ] {
      #expect(
        sample.localizedDescription == sample.description,
        "NativeSwiftCoreError must surface its own description through localizedDescription")
    }

    // Two size modifiers on one component are a Compose modifier *chain*, and AndroidX emits
    // exactly this pair on a title card: `width(172.dp)` then `fillMaxWidth()`. The outer one fixes
    // the constraint and the inner fill then fills precisely that, so the outer decides and the
    // inner is a no-op. Reading the pair as a property and keeping the last value drew the card at
    // the document's full width instead.
    let chained = try NativeSwiftDocumentSession.open(data: chainedSizeModifierDocument())
      .snapshot()
    let chainedColumn = try #require(
      chained.root.children.first?.children.first,
      "the chained-size fixture decoded no child component")
    #expect(
      chainedColumn.widthType == 6 && chainedColumn.widthValue == 172,
      Comment(
        rawValue: "expected the outer width(172.dp) to win, got "
          + "\(chainedColumn.widthType)/\(chainedColumn.widthValue)"))
    #expect(
      chainedColumn.heightType == 6 && chainedColumn.heightValue == 64,
      Comment(
        rawValue: "expected the outer height(64.dp) to win, got "
          + "\(chainedColumn.heightType)/\(chainedColumn.heightValue)"))

    // A scroll modifier opens a container of its own, and the opcode stream closes it with the same
    // 214 that closes every other container. Reading 226 as a plain modifier left that 214 to pop the
    // *component*, so the component's content was attached to its parent: a scrolled row's children
    // arrived as siblings of the row, laid out by nobody. `row_scroll_basic` is the gold that caught
    // it, and this fixture is that shape.
    let scrolled = try NativeSwiftDocumentSession.open(data: scrolledRowDocument()).snapshot()
    let scrolledRow = try #require(
      scrolled.root.children.first, "the scrolled-row fixture decoded no row")
    #expect(
      scrolledRow.componentKind == "RowLayout",
      "expected the scrolled component to be a row, got \(scrolledRow.componentKind)")
    #expect(
      scrolledRow.scrollDirection == .horizontal && scrolledRow.scrollOffset == 40
        && scrolledRow.scrollMaximum == 240,
      Comment(
        rawValue: "expected a horizontal scroll at 40/240, got "
          + "\(String(describing: scrolledRow.scrollDirection))/\(scrolledRow.scrollOffset)"
          + "/\(scrolledRow.scrollMaximum)"))
    #expect(
      NativeSwiftScrollGesture.offset(afterDragging: 40, delta: -30, maximum: 60) == 60
        && NativeSwiftScrollGesture.offset(afterDragging: 40, delta: 60, maximum: 60) == 0,
      "scroll drag arithmetic did not clamp at both ends")
    let scrolledContent = try #require(
      scrolledRow.children.first, "the scrolled row's content did not stay under the row")
    try #require(
      scrolledContent.children.count == 1, "the scrolled row's content did not stay under the row")
    #expect(
      scrolledContent.children.first?.componentID == -5,
      Comment(
        rawValue: "the scrolled row's child landed at "
          + "\(String(describing: scrolledContent.children.first?.componentID))"))
    // A *data-only* document declares values and nothing to draw, so it carries no root component at
    // all. Refusing it is right for a renderer and wrong for a conformance run, which still has to
    // answer the scalar probes those documents assert — so the mode is asked for explicitly and the
    // strict path stays the default. `expr_color_blending` and `expr_integer_bitwise_ops` are the
    // golds that need it.
    let rootless = rootlessValuesDocument()
    do {
      _ = try NativeSwiftDocumentSession.open(data: rootless)
      Issue.record("a document with no root component was accepted without the mode")
    } catch let error as NativeSwiftCoreError {
      #expect(error.description.contains("Missing root component"))
    }
    let dataOnly = try NativeSwiftDocumentSession.open(
      data: rootless, toleratingRootlessData: true)
    let dataOnlySnapshot = try dataOnly.snapshot()
    #expect(
      dataOnlySnapshot.root.children.isEmpty,
      "a data-only document's synthetic root grew children")
    let dataOnlyValues = try dataOnly.probeValues(timeSeconds: 0)
    #expect(
      dataOnly.namedVariableID("answer") == 5 && dataOnlyValues.floats[5] == 42,
      Comment(
        rawValue: "a data-only document's own values did not resolve: "
          + "\(String(describing: dataOnlyValues.floats[5]))"))
    #expect(dataOnly.setFloat(24, forID: 99), "a finite float slot update was refused")
    let updatedDataOnlyValues = try dataOnly.probeValues(timeSeconds: 0)
    #expect(
      updatedDataOnlyValues.floats[99] == 24,
      "a float slot update did not survive resolution")
    #expect(
      !dataOnly.setFloat(.nan, forID: 99), "a non-finite float slot update was accepted")
  }

  @Test func dataMapsParticlesAndAnimationDescriptors() throws {
    // AndroidX data maps resolve a key text through a typed resource-id table. This is a rootless
    // conformance document, so it also proves that scalar-only captures do not need a draw root.
    let dataMap = Data(
      base64Encoded:
        "AASMAAEAAAABAAAAAAAAAAMABQAEAAABkAAGAAQAAAGQAA4ABAAAAgFmAAAACgAAAAl0aXRsZV9rZXlmAAAACwAAABJSZW1vdGVDb21wb3NlIFNEVUlQAAAADEIoAABmAAAADQAAAAdhZ2Vfa2V5kQAAABQAAAACAAAACXRpdGxlX2tleQAAAAALAAAAB2FnZV9rZXkCAAAADJoAAAAeAAAAFAAAAAqaAAAAHwAAABQAAAAN"
    )!
    let dataMapValues = try NativeSwiftDocumentSession.open(
      data: dataMap, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(
      dataMapValues.floats[31] == 42 && dataMapValues.texts[30] == "RemoteCompose SDUI",
      "data-map lookup did not expose its typed values: \(dataMapValues)")

    // Particle definitions used to be consumed and discarded, so every frame silently saw no
    // particle state. A retained session now initialises the system once and applies the loop's
    // equations on each logical frame.
    let particleSession = try NativeSwiftDocumentSession.open(data: particleDocument())
    let firstParticles = try particleSession.particleSnapshot(id: 9, timeSeconds: 0)
    let secondParticles = try particleSession.particleSnapshot(id: 9, timeSeconds: 1)
    #expect(
      firstParticles?.variableIDs == [70] && firstParticles?.particles == [[3]],
      Comment(
        rawValue: "particle definition did not initialise and simulate its first frame: "
          + "\(String(describing: firstParticles))"))
    #expect(
      secondParticles?.particles == [[4]],
      "particle loop did not retain state between frames: \(String(describing: secondParticles))")

    // AndroidX permits a compact FloatAnimation descriptor with its duration alone; it implies
    // CUBIC_STANDARD. Several Wear catalog components use it for transition values.
    let compactAnimation = try NativeSwiftDocumentSession.open(data: compactAnimationDocument())
    _ = try compactAnimation.snapshot()

    let wire = editableTextDocument()
    let session = try NativeSwiftDocumentSession.open(data: wire)
    let initial = try session.snapshot()
    #expect(initial.width == 360 && initial.height == 150)
    #expect(initial.density == 1 && initial.densityBehavior == 0)
    #expect(initial.root.children.first?.children.first?.kind == .column)

    let column = initial.root.children[0].children[0]
    #expect(column.children.count == 1)
    let content = column.children[0]
    #expect(content.children.count == 3)
    #expect(content.children[0].custom?.config == "demo:EditableText")
    #expect(content.children[0].custom?.properties[0].textValue == "Hello from the document")
    #expect(content.children[2].text?.value == "Hello from the document")

    #expect(session.setColor(0xff12_3456, for: "accent"))
    let accepted = session.returnCustomText(
      "Edited in Swift", componentID: 5, propertyID: 2)
    #expect(accepted)
    let updated = try session.snapshot()
    let updatedContent = updated.root.children[0].children[0].children[0]
    #expect(updatedContent.children[0].custom?.properties[0].textValue == "Edited in Swift")
    #expect(
      updatedContent.children[0].custom?.properties[2].integerValue
        == Int(Int32(bitPattern: 0xff12_3456)))
    #expect(updatedContent.children[2].text?.value == "Edited in Swift")
    let rejected = session.returnCustomText("Ignored", componentID: 5, propertyID: 99)
    #expect(!rejected)

    let floatSession = try NativeSwiftDocumentSession.open(data: dynamicCustomFloatDocument())
    let initialFloat = try floatSession.snapshot().root.children[0].children[0]
    #expect(initialFloat.custom?.properties[0].floatValue == 0.4)
    let acceptedFloat = floatSession.returnCustomFloat(0.85, componentID: 3, propertyID: 2)
    #expect(acceptedFloat, "declared float return should be accepted")
    let returnedFloat = try floatSession.snapshot().root.children[0].children[0]
    #expect(abs((returnedFloat.custom?.properties[0].floatValue ?? 0) - 0.85) < 0.001)
    let rejectedFloat = floatSession.returnCustomFloat(0.5, componentID: 3, propertyID: 99)
    #expect(!rejectedFloat, "an undeclared return channel should be rejected")

    // A Row whose spacing is computed rather than stated. Before float words were carried to
    // resolution time this stored the reference's raw NaN bits into a plain `Float` and laid the
    // row out at NaN — silently, while the Column one opcode later refused the identical word.
    let computedSpacing = Writer()
    computedSpacing.header(width: 100, height: 100)
    computedSpacing.u8(80).int(40).float(3)
    computedSpacing.u8(81).int(41).int(3)
      .int(Writer.nanReference(40)).float(4).int(Writer.floatOperator(3))
    computedSpacing.u8(200).int(1)
    computedSpacing.u8(203).int(2).int(0).int(1).int(4).int(Writer.nanReference(41))
    computedSpacing.u8(214).u8(214)
    let spacingSnapshot = try NativeSwiftDocumentSession.open(data: computedSpacing.data).snapshot()
    #expect(
      spacingSnapshot.root.children[0].spacing == 12,
      "computed row spacing resolved to \(spacingSnapshot.root.children[0].spacing)")

    // The other half of moving validation to resolution time: a size that resolves to something
    // unusable has to fail closed with a typed error rather than reach UIKit.
    let poisonedSize = Writer()
    poisonedSize.header(width: 100, height: 100)
    poisonedSize.text(id: 20, "Zero")
    poisonedSize.u8(80).int(60).float(0)
    poisonedSize.u8(200).int(1).u8(201).int(2)
    poisonedSize.u8(208).int(3).int(0).int(20).int(Int(Int32(bitPattern: UInt32(0xff00_0000))))
      .int(Writer.nanReference(60)).int(0).float(400).int(-1).int(1).int(1).int(1)
    poisonedSize.u8(214).u8(214).u8(214)
    do {
      _ = try NativeSwiftDocumentSession.open(data: poisonedSize.data).snapshot()
      Issue.record("a text size resolving to zero was accepted")
    } catch let error as NativeSwiftCoreError {
      #expect(
        error.description.contains("text size"),
        "expected the failure to name the text size, got \(error.description)")
    }

    // A component-value binding on a structural content node inside a fill canvas inside a fixed
    // box. The pre-layout estimate used to resolve the fill to the whole document, so an icon's
    // scale derived from its own measured width was 454/24 rather than 26/24 and the glyph was
    // transformed off-canvas. The estimate now walks up to the ancestor that decides.
    let fillBinding = Writer()
    fillBinding.header(width: 454, height: 400)
    fillBinding.u8(200).int(-2)
    fillBinding.u8(201).int(-3)
    fillBinding.u8(202).int(-4).int(0).int(1).int(4)
    fillBinding.u8(16).int(6).float(26)
    fillBinding.u8(67).int(6).float(26)
    fillBinding.u8(205).int(-5).int(0)
    fillBinding.u8(16).int(1).float(1)
    fillBinding.u8(67).int(1).float(1)
    fillBinding.u8(201).int(-6)
    fillBinding.u8(150).int(0).int(-6).int(101)
    fillBinding.u8(150).int(1).int(-6).int(102)
    fillBinding.u8(81).int(103).int(3)
      .int(Writer.nanReference(101)).float(24).int(Writer.floatOperator(4))
    fillBinding.u8(126).int(Writer.nanReference(103)).int(Writer.nanReference(103))
      .int(Int(Int32(bitPattern: Float.nan.bitPattern)))
      .int(Int(Int32(bitPattern: Float.nan.bitPattern)))
    fillBinding.u8(214).u8(214).u8(214).u8(214).u8(214)
    let fillSnapshot = try NativeSwiftDocumentSession.open(data: fillBinding.data).snapshot()
    let fillScale = fillSnapshot.root.allCommands.first(where: { $0.kind == 3 })?.values.first
    #expect(
      abs((fillScale ?? 0) - 26.0 / 24.0) < 0.001,
      "a fill inside a fixed box measured the document, got scale \(String(describing: fillScale))")
  }

  @Test func wallClockDataTextAndScheduling() throws {
    // The wall clock a document's calendar and time-of-day variables read. The reference publishes
    // these from its clock every frame; this core leaves them unset unless a host supplies an
    // absolute instant, so a test and a corpus capture stay deterministic.
    let calendar = Writer()
    calendar.header(width: 100, height: 100)
    calendar.u8(200).int(1)
    for id in [35, 9, 12, 11, 34, 2, 3, 4, 10] {
      calendar.u8(42).int(Writer.nanReference(id)).int(0).int(0).int(0)
    }
    calendar.u8(214)
    let calendarSession = try NativeSwiftDocumentSession.open(data: calendar.data)
    let unset = try calendarSession.snapshot()
    #expect(
      unset.root.commands[0].values[0] == 0,
      "an unsupplied calendar field resolved to \(unset.root.commands[0].values[0])")
    // 2026-09-19T12:34:56.789Z.
    let wallClock = NativeSwiftWallClock(epochMillis: 1_789_821_296_789, offsetSeconds: 0)
    let fields = try calendarSession.snapshot(wallClock: wallClock).root.commands
      .map { $0.values[0] }
    #expect(
      fields == [2026, 9, 19, 6, 262, 2096, 754, 12, 0],
      "calendar fields resolved to \(fields)")
    // A zone offset moves the local fields but not the instant.
    let shifted = try calendarSession.snapshot(
      wallClock: NativeSwiftWallClock(epochMillis: 1_789_821_296_789, offsetSeconds: 3600)
    ).root.commands.map { $0.values[0] }
    #expect(
      shifted == [2026, 9, 19, 6, 262, 2096, 814, 13, 3600],
      "offset calendar fields resolved to \(shifted)")

    // A document that declares one of the ids itself keeps it, matching the reference's
    // claimed-id rule.
    let claimed = Writer()
    claimed.header(width: 100, height: 100)
    claimed.u8(80).int(35).float(1999)
    claimed.u8(200).int(1)
    claimed.u8(42).int(Writer.nanReference(35)).int(0).int(0).int(0)
    claimed.u8(214)
    let claimedYear = try NativeSwiftDocumentSession.open(data: claimed.data)
      .snapshot(wallClock: wallClock).root.commands[0].values[0]
    #expect(claimedYear == 1999, "a claimed calendar id resolved to \(claimedYear)")

    // Pre-epoch instants: `-1 ms` is the last millisecond of 1969, and the fields have to describe
    // that rather than 1970-01-01T00:00:00.999. A week earlier is a Sunday, ISO 7, which a negative
    // remainder would put at 0.
    let preEpoch = try calendarSession.snapshot(
      wallClock: NativeSwiftWallClock(epochMillis: -1)
    ).root.commands.map { $0.values[0] }
    #expect(
      preEpoch == [1969, 12, 31, 3, 365, 3599, 1439, 23, 0],
      "pre-epoch calendar fields resolved to \(preEpoch)")
    let sunday = try calendarSession.snapshot(
      wallClock: NativeSwiftWallClock(epochMillis: -345_600_000)
    ).root.commands.map { $0.values[0] }
    #expect(
      sunday[3] == 7 && sunday[4] == 362,
      "a pre-epoch weekday resolved to \(sunday[3]) / day-of-year \(sunday[4])")

    // Data and text operations that used to refuse the document: an id lookup into a list, a
    // text's length (directly and as a text attribute), a slice, a theme marker and a text
    // measurement, which needs host metrics and so leaves its slot as it was.
    let texty = Writer()
    texty.header(width: 100, height: 100)
    texty.text(id: 1, "Hello RemoteCompose")
    texty.u8(146).int(2).int(3).int(7).int(8).int(9)
    texty.u8(192).int(40).int(2).float(1)
    texty.u8(192).int(41).int(2).float(5)
    texty.u8(156).int(42).int(1)
    texty.u8(170).int(43).int(1).u16(6).u16(0)
    texty.u8(182).int(44).int(1).float(6).float(6)
    texty.u8(182).int(45).int(1).float(6).float(-1)
    texty.u8(63).int(-2)
    texty.u8(155).int(46).int(1).int(0)
    let textyValues = try NativeSwiftDocumentSession.open(
      data: texty.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(
      textyValues.integers[40] == 8 && textyValues.floats[40] == 8, Comment(rawValue:
      "an id lookup resolved to \(String(describing: textyValues.integers[40]))"))
    #expect(
      textyValues.integers[41] == nil, Comment(rawValue: "an out-of-range id lookup wrote its slot"))
    #expect(
      textyValues.floats[42] == 19 && textyValues.floats[43] == 19, Comment(rawValue:
      "text lengths resolved to \(String(describing: textyValues.floats[42])), "
        + "\(String(describing: textyValues.floats[43]))"))
    #expect(
      textyValues.texts[44] == "Remote" && textyValues.texts[45] == "RemoteCompose", Comment(rawValue:
      "subtexts resolved to \(String(describing: textyValues.texts[44])), "
        + "\(String(describing: textyValues.texts[45]))"))
    #expect(textyValues.floats[46] == nil, Comment(rawValue: "a text measurement invented a value"))

    // A CoreText that names a TextStyle inherits what it does not set itself, through the style's
    // own parent. A TextLayout overflow outside AndroidX's five renders as clip.
    let styled = Writer()
    styled.header(width: 200, height: 100)
    styled.text(id: 1, "Styled")
    styled.u8(242).u16(2).u8(1).int(10).u8(5).float(20)
    styled.u8(242).u16(3).u8(1).int(11).u8(24).int(10).u8(7).float(700)
    styled.u8(200).int(1)
    styled.u8(239).int(1).u16(2).u8(1).int(5).u8(24).int(11)
    styled.u8(214)
    styled.u8(208).int(6).int(0).int(1).int(-1).float(12).int(0).float(400).int(-1).int(1)
      .int(0).int(1)
    styled.u8(214)
    styled.u8(214)
    let styledTexts = try NativeSwiftDocumentSession.open(data: styled.data).snapshot().root
      .children.flatMap { [$0] + $0.children }.compactMap(\.text)
    #expect(
      styledTexts.count == 2 && styledTexts[0].size == 20 && styledTexts[0].weight == 700
        && styledTexts[1].overflow == 1, Comment(rawValue:
      "styled text resolved to \(styledTexts.map { ($0.size, $0.weight, $0.overflow) })"))

    // An impulse opens a window on the animation clock. Its setup draws once, on the first frame
    // inside the window; its trailing process body draws on every later frame inside it; nothing
    // draws before or after. WAKE_IN and a waiting impulse ask the host to come back.
    let impulse = Writer()
    impulse.header(width: 100, height: 100)
    impulse.u8(164).float(1).float(0.5)
    impulse.u8(42).float(0).float(0).float(10).float(10)
    impulse.u8(165)
    impulse.u8(42).float(1).float(1).float(20).float(20)
    impulse.u8(214)
    impulse.u8(214)
    impulse.u8(191).float(0.25)
    let impulseSession = try NativeSwiftDocumentSession.open(data: impulse.data)
    func impulseFrame(_ time: TimeInterval) throws -> (right: [Float], wake: TimeInterval?) {
      let frame = try impulseSession.snapshot(timeSeconds: time)
      return (frame.root.commands.map { $0.values[2] }, frame.wakeAfter)
    }
    let waiting = try impulseFrame(0)
    #expect(
      waiting.right.isEmpty && waiting.wake == 0.25, Comment(rawValue:
      "a waiting impulse drew \(waiting.right) and woke after \(String(describing: waiting.wake))"))
    let first = try impulseFrame(0.6)
    let again = try impulseFrame(0.6)
    let later = try impulseFrame(1.0)
    let after = try impulseFrame(2.0)
    #expect(
      first.right == [10] && again.right == [10] && first.wake == 0, Comment(rawValue:
      "an impulse's first frame drew \(first.right), then \(again.right) on re-resolution"))
    #expect(later.right == [20], Comment(rawValue: "an impulse's process body drew \(later.right)"))
    #expect(
      after.right.isEmpty && after.wake == 0.25, Comment(rawValue:
      "a closed impulse drew \(after.right), woke after \(String(describing: after.wake))"))
    let impulseRecord = try impulseSession.snapshot(timeSeconds: 0).impulses
    #expect(
      impulseRecord == [NativeSwiftImpulseSnapshot(duration: 1, startAt: 0.5)], Comment(rawValue:
      "impulse records resolved to \(impulseRecord)"))
    // Only drawing is gated; state inside an impulse would run outside its window, so it refuses.
    let stateful = Writer()
    stateful.header(width: 100, height: 100)
    stateful.u8(164).float(1).float(0)
    stateful.u8(80).int(40).float(1)
    stateful.u8(214)
    do {
      _ = try NativeSwiftDocumentSession.open(data: stateful.data)
      Issue.record("state inside an impulse was accepted")
    } catch NativeSwiftCoreError.unsupported(let opcode, _, _) {
      #expect(opcode == 80, Comment(rawValue: "the impulse refused opcode \(opcode)"))
    }

    // EPOCH_SECOND is whole seconds since the epoch, floored, as an integer and as a float.
    let epoch = try NativeSwiftDocumentSession.open(
      data: rootlessValuesDocument(), toleratingRootlessData: true
    ).probeValues(timeSeconds: 0, wallClock: NativeSwiftWallClock(epochMillis: 1_789_050_600_999))
    #expect(
      epoch.integers[32] == 1_789_050_600 && epoch.floats[32] == Float(1_789_050_600), Comment(rawValue:
      "EPOCH_SECOND resolved to \(String(describing: epoch.integers[32]))"))

    // A loop unrolls with the index bound per pass, and ends holding its last value. A path built
    // with PATH_CREATE / PATH_APPEND is drawable, and a leading RESET empties it.
    let looped = Writer()
    looped.header(width: 100, height: 100)
    looped.u8(80).int(70).float(0)
    looped.u8(215).int(70).float(0).float(1).float(3)
    looped.u8(42).int(Writer.nanReference(70)).float(0).float(10).float(10)
    looped.u8(214)
    looped.u8(159).int(50).float(0).float(0)
    looped.u8(160).int(50).int(5).int(Writer.nanReference(11)).float(0).float(0).float(10).float(10)
    looped.u8(124).int(50)
    let loopedSession = try NativeSwiftDocumentSession.open(data: looped.data)
    let loopedCommands = try loopedSession.snapshot().root.commands
    #expect(
      loopedCommands.filter { $0.kind == 10 }.map { $0.values[0] } == [0, 1, 2], Comment(rawValue:
      "a loop drew at \(loopedCommands.map { ($0.kind, $0.values) })"))
    let loopIndex = try loopedSession.probeValues(timeSeconds: 0).floats[70]
    #expect(loopIndex == 2, Comment(rawValue: "a loop's index ended on \(String(describing: loopIndex))"))
    #expect(
      loopedCommands.contains { $0.kind == 18 }, Comment(rawValue: "a created and appended path did not draw"))

    // A ColorTheme resolves to its dark fallback under a dark theme, and to its light one under
    // a light or unspecified theme. A colour the host has set stays the host's.
    let themed = Writer()
    themed.header(width: 100, height: 100)
    themed.u8(196).int(10).int(1).u16(0).u16(0).int(Int(Int32(bitPattern: 0xFF11_1111)))
      .int(Int(Int32(bitPattern: 0xFF22_2222)))
    let themedSession = try NativeSwiftDocumentSession.open(
      data: themed.data, toleratingRootlessData: true)
    let unthemed = try themedSession.probeValues(timeSeconds: 0).colors[10]
    themedSession.setRequestedTheme(NativeSwiftTheme.dark)
    let dark = try themedSession.probeValues(timeSeconds: 0).colors[10]
    themedSession.setRequestedTheme(NativeSwiftTheme.light)
    let light = try themedSession.probeValues(timeSeconds: 0).colors[10]
    #expect(
      unthemed == 0xFF11_1111 && dark == 0xFF22_2222 && light == 0xFF11_1111, Comment(rawValue:
      "a colour theme resolved to \(String(describing: unthemed)), \(String(describing: dark)), "
        + "\(String(describing: light))"))

    // TEXT_FROM_FLOAT formats as AndroidX's StringUtils does: zero and space padding either side of
    // the point, grouping, separators, rounding, parentheses and the legacy mode. Each expectation
    // is the CMP player's RcTextFormatter output, which a compatibility test holds to AndroidX.
    let formatted: [(Float, Int, Int, Int, String)] = [
      (123.456, 5, 1, 12, "00123.5"),
      (123.456, 3, 2, 0, "123.46"),
      (-12345.678, 10, 2, 535, "-12,345.68"),
      (9.5, 2, 0, 12, "09"),
      (0.05, 1, 2, 3, "0.05"),
      (1234567.8, 10, 2, 80, " 1.234.567,8 "),
      (-123.456, 3, 1, 256, "(123.5)"),
      (0.999, 1, 2, 1024, "0.  "),
      (7.1, 2, 3, 15, "07.100"),
      // Java's own spelling of a tiny fraction, and digit counts past what an Int64 power holds:
      // the reference's quirks are kept, and nothing traps.
      (0.0001, 0, 4, 5, ".0"),
      (0.0001, 1, 4, 0, "0.0   "),
      (0.00012, 1, 5, 3, "0.20000"),
      (1.5, 2, 19, 0, " 1.5                  "),
      (1.5, 2, 20, 3, " 1.50000000000000000000"),
      (1.5, 2, 25, 1, " 1.5"),
      (1.5, 2, 20, 1024, " 1.1474848E-11         "),
      (2.25, 3, 30, 515, "  2.250000000000000000000000000000"),
      (0.0005, 0, 5, 1024, ".0E-4 "),
      // FULL_FORMAT is Java's Float.toString.
      (0.0001, 0, 0, 4096, "1.0E-4"),
      (1.0e7, 0, 0, 4096, "1.0E7"),
      (12345678, 0, 0, 4096, "1.2345678E7"),
      (1234567, 0, 0, 4096, "1234567.0"),
      (0.001, 0, 0, 4096, "0.001"),
      (0.00012345, 0, 0, 4096, "1.2345E-4"),
      (3.4e38, 0, 0, 4096, "3.4E38"),
      (-0.0, 0, 0, 4096, "-0.0"),
      (100, 0, 0, 4096, "100.0"),
      (.infinity, 0, 0, 4096, "Infinity"),
    ]
    // A negative digit count, which the reference's substring would throw on, formats instead.
    _ = NativeSwiftTextFormatter.format(1.5, digitsBefore: -3, digitsAfter: -2, flags: 0)
    _ = NativeSwiftTextFormatter.format(1.5, digitsBefore: -3, digitsAfter: -2, flags: 1024)
    for (value, before, after, flags, expected) in formatted {
      let actual = NativeSwiftTextFormatter.format(
        value, digitsBefore: before, digitsAfter: after, flags: flags)
      #expect(
        actual == expected,
        Comment(rawValue:
        "\(value) with \(before).\(after) flags \(flags) formatted as [\(actual)], not [\(expected)]"))
    }

    // A document that reads a discrete wall-clock field asks a host to re-resolve at least once a
    // second; one that only reads the animation clock does not.
    let refreshed = try calendarSession.snapshot(wallClock: wallClock)
    #expect(
      refreshed.needsWallClockRefresh,
      "a document reading calendar fields did not ask for a refresh")
    let animated = Writer()
    animated.header(width: 100, height: 100)
    animated.u8(81).int(41).int(3)
      .int(Writer.nanReference(30)).float(1).int(Writer.floatOperator(3))
    animated.u8(200).int(1)
    animated.u8(42).int(Writer.nanReference(41)).int(0).int(0).int(0)
    animated.u8(214)
    let animatedSnapshot = try NativeSwiftDocumentSession.open(data: animated.data).snapshot()
    #expect(
      !animatedSnapshot.needsWallClockRefresh,
      "an animation-only document asked for a wall-clock refresh")
    #expect(
      animatedSnapshot.needsContinuousFrames,
      "an animation-only document did not ask for continuous frames")
  }

  @Test func clockTextColourExpressionsAndStateLayout() throws {
    // A clock display that converts a system variable straight to text names it in the
    // text-from-float operation, not in an expression or a draw command. That store has to be
    // scanned too, or the display freezes after the first frame.
    let clockText = Writer()
    clockText.header(width: 100, height: 100)
    clockText.u8(135).int(40).int(Writer.nanReference(2)).int(0).int(0)
    clockText.u8(200).int(1).u8(214).u8(214)
    let clockTextSnapshot = try NativeSwiftDocumentSession.open(data: clockText.data).snapshot()
    #expect(
      clockTextSnapshot.needsWallClockRefresh,
      "a clock-to-text conversion did not ask for a wall-clock refresh")
    let plainText = Writer()
    plainText.header(width: 100, height: 100)
    plainText.u8(135).int(40).float(1.5).int(0).int(0)
    plainText.u8(200).int(1).u8(214).u8(214)
    let plainTextSnapshot = try NativeSwiftDocumentSession.open(data: plainText.data).snapshot()
    #expect(
      !plainTextSnapshot.needsWallClockRefresh,
      "a constant text-from-float conversion asked for a wall-clock refresh")

    // A colour expression's first two fields are colours, not float words, in modes 0...3. Their
    // bit patterns can look exactly like clock references — 0xff800001 is ARGB but reads as id 1 —
    // and a static document must not end up on a display link because of a colour.
    let staticColor = Writer()
    staticColor.header(width: 100, height: 100)
    staticColor.u8(134).int(40).int(0)
      .int(Int(Int32(bitPattern: 0xff80_0001)))
      .int(Int(Int32(bitPattern: 0xff80_0002)))
      .float(0.5)
    staticColor.u8(200).int(1).u8(214).u8(214)
    let staticColorSnapshot = try NativeSwiftDocumentSession.open(data: staticColor.data).snapshot()
    #expect(
      !staticColorSnapshot.needsContinuousFrames && !staticColorSnapshot.needsWallClockRefresh,
      Comment(
        rawValue: "a static colour expression asked for frames: continuous="
          + "\(staticColorSnapshot.needsContinuousFrames) wallClock="
          + "\(staticColorSnapshot.needsWallClockRefresh)"))
    // The tween of the same mode *is* a float word, so a clock there is still found.
    let tweenedColor = Writer()
    tweenedColor.header(width: 100, height: 100)
    tweenedColor.u8(134).int(40).int(0)
      .int(Int(Int32(bitPattern: 0xff00_0000)))
      .int(Int(Int32(bitPattern: 0xff00_00ff)))
      .int(Writer.nanReference(2))
    tweenedColor.u8(200).int(1).u8(214).u8(214)
    let tweenedSnapshot = try NativeSwiftDocumentSession.open(data: tweenedColor.data).snapshot()
    #expect(
      tweenedSnapshot.needsWallClockRefresh,
      "a colour tween reading the clock did not ask for a refresh")
    // Modes 4...6 build a colour from float channels, so all three are words.
    let channelColor = Writer()
    channelColor.header(width: 100, height: 100)
    channelColor.u8(134).int(40).int(4).int(Writer.nanReference(2)).int(0).int(0)
    channelColor.u8(200).int(1).u8(214).u8(214)
    let channelSnapshot = try NativeSwiftDocumentSession.open(data: channelColor.data).snapshot()
    #expect(
      channelSnapshot.needsWallClockRefresh,
      "a colour channel reading the clock did not ask for a refresh")

    // A StateLayout shows the child its index integer selects and marks the rest GONE. Laid out as
    // a plain box it showed every branch stacked, which was the largest remaining raster
    // difference on the native lane.
    let stateLayout = Writer()
    stateLayout.header(width: 300, height: 200)
    stateLayout.u8(140).int(20).int(0)
    stateLayout.u8(200).int(-2)
    stateLayout.u8(217).int(-3).int(0).int(1).int(4).int(20)
    stateLayout.u8(67).int(6).float(100)
    stateLayout.u8(202).int(-5).int(0).int(1).int(4)
    stateLayout.u8(16).int(6).float(120)
    stateLayout.u8(67).int(6).float(80)
    stateLayout.u8(214)
    stateLayout.u8(202).int(-6).int(0).int(1).int(4)
    stateLayout.u8(16).int(6).float(200)
    stateLayout.u8(67).int(6).float(150)
    stateLayout.u8(214).u8(214).u8(214)
    let stateSnapshot = try NativeSwiftDocumentSession.open(data: stateLayout.data).snapshot()
    let stateNode = try #require(
      stateSnapshot.root.children.first, "the state-layout fixture decoded no state layout")
    #expect(
      stateNode.componentKind == "StateLayout",
      "a state layout reported kind \(stateNode.componentKind)")
    #expect(stateNode.stateIndex == 0, "state index resolved to \(String(describing: stateNode.stateIndex))")
    // The container takes the active child's size whatever the document asks for: both its fill
    // width and fixed 100-point height are dropped.
    #expect(
      stateNode.widthType == 2 && stateNode.heightType == 2,
      "a dimensioned state layout kept its own size: \(stateNode.widthType)/\(stateNode.heightType)")
    #expect(
      stateNode.children.map(\.visibility) == [1, 0],
      "state layout children were \(stateNode.children.map(\.visibility)), expected only the first")
    // The same document with the index set to the second branch.
    let secondBranch = Writer()
    secondBranch.header(width: 300, height: 200)
    secondBranch.u8(140).int(20).int(1)
    secondBranch.u8(200).int(-2)
    secondBranch.u8(217).int(-3).int(0).int(1).int(4).int(20)
    secondBranch.u8(202).int(-5).int(0).int(1).int(4)
    secondBranch.u8(16).int(6).float(120)
    secondBranch.u8(67).int(6).float(80)
    secondBranch.u8(214)
    secondBranch.u8(202).int(-6).int(0).int(1).int(4)
    secondBranch.u8(16).int(6).float(200)
    secondBranch.u8(67).int(6).float(150)
    secondBranch.u8(214).u8(214).u8(214)
    let secondSnapshot = try NativeSwiftDocumentSession.open(data: secondBranch.data).snapshot()
    #expect(
      secondSnapshot.root.children.first?.children.map(\.visibility) == [0, 1],
      "the second state branch was not the visible one")
    // The reported index counts the *branches*, not the wrapper they sit in, so it agrees with the
    // branch that is actually visible.
    #expect(
      secondSnapshot.root.children.first?.stateIndex == 1,
      Comment(
        rawValue: "the second branch reported index "
          + "\(String(describing: secondSnapshot.root.children.first?.stateIndex))"))
  }

  @Test func flowLayout() throws {
    // A flow container's wrap bounds reach the snapshot, so a renderer can wrap rather than lay
    // every child out on one line.
    let flow = Writer()
    flow.header(width: 300, height: 300)
    flow.u8(200).int(-2)
    flow.u8(240).int(-3).int(0).int(1).int(4).float(12).int(2).int(3)
    flow.u8(202).int(-5).int(0).int(1).int(4)
    flow.u8(16).int(6).float(100)
    flow.u8(67).int(6).float(50)
    flow.u8(214).u8(214).u8(214)
    let flowSnapshot = try NativeSwiftDocumentSession.open(data: flow.data).snapshot()
    let flowNode = try #require(
      flowSnapshot.root.children.first, "the flow fixture decoded no flow container")
    #expect(
      flowNode.componentKind == "FlowLayout",
      "a flow container reported kind \(flowNode.componentKind)")
    #expect(
      flowNode.flowMaximumItems == 2 && flowNode.flowMaximumLines == 3,
      Comment(
        rawValue: "flow bounds resolved to \(String(describing: flowNode.flowMaximumItems)) / "
          + "\(String(describing: flowNode.flowMaximumLines))"))

    // A weighted flow child reserves its `widthIn` minimum while the line is segmented, so its
    // siblings are not placed beside it when that minimum cannot fit; without it the weight is
    // zero-width, everything lands on one line, and the allocator then shrinks the child below its
    // own minimum.
    let weightedFlow = [
      NativeSwiftFlow.Child(measuredWidth: 0, weight: 1, minimumWidth: 120),
      NativeSwiftFlow.Child(measuredWidth: 100),
      NativeSwiftFlow.Child(measuredWidth: 100),
    ]
    let segmented = NativeSwiftFlow.segment(weightedFlow, available: 300, spacing: 0)
    #expect(
      segmented.lines == [[0, 1], [2]],
      "weighted flow segmentation produced \(segmented.lines)")
    let withoutMinimum = NativeSwiftFlow.segment(
      [NativeSwiftFlow.Child(measuredWidth: 0, weight: 1), weightedFlow[1], weightedFlow[2]],
      available: 300, spacing: 0)
    #expect(
      withoutMinimum.lines == [[0, 1, 2]],
      "an unconstrained weighted child should not force a wrap: \(withoutMinimum.lines)")
    // The caps still apply: items per line, and lines before the rest are discarded.
    let capped = NativeSwiftFlow.segment(
      [NativeSwiftFlow.Child(measuredWidth: 10), NativeSwiftFlow.Child(measuredWidth: 10),
       NativeSwiftFlow.Child(measuredWidth: 10)],
      available: 1000, spacing: 0, maximumItems: 2, maximumLines: 1)
    #expect(
      capped.lines == [[0, 1]] && capped.discarded == [2],
      "flow caps produced \(capped.lines) / \(capped.discarded)")
    // Once the line cap is reached, everything from that child on is discarded — a later, smaller
    // child must not land on the still-open line and come out ahead of the one that was dropped.
    let cappedSuffix = NativeSwiftFlow.segment(
      [NativeSwiftFlow.Child(measuredWidth: 80),
       NativeSwiftFlow.Child(measuredWidth: 0, weight: 1, minimumWidth: 30),
       NativeSwiftFlow.Child(measuredWidth: 20)],
      available: 100, spacing: 0, maximumLines: 1)
    #expect(
      cappedSuffix.lines == [[0]] && cappedSuffix.discarded == [1, 2],
      "a capped suffix produced \(cappedSuffix.lines) / \(cappedSuffix.discarded)")
    // A GONE child is in neither the line, its spacing, nor its item count.
    let goneFlow = NativeSwiftFlow.segment(
      [NativeSwiftFlow.Child(measuredWidth: 100, isGone: true),
       NativeSwiftFlow.Child(measuredWidth: 100),
       NativeSwiftFlow.Child(measuredWidth: 100)],
      available: 100, spacing: 10, maximumItems: 2)
    #expect(
      goneFlow.lines == [[1], [2]],
      "a GONE child consumed a slot or a gap: \(goneFlow.lines)")

    let modern = Writer()
    modern.modernHeader(width: 100, height: 50, unrelatedKey: 69, unrelatedValue: 999)
    modern.u8(200).int(1).u8(214).u8(214)
    let modernSnapshot = try NativeSwiftDocumentSession.open(data: modern.data).snapshot()
    #expect(modernSnapshot.width == 100 && modernSnapshot.height == 50)

    let googleFont = Writer()
    googleFont.header(width: 100, height: 50)
    googleFont.text(id: 20, "Downloadable")
    googleFont.text(id: 21, "google:Orbitron")
    googleFont.u8(200).int(1).u8(201).int(2)
    googleFont.textLayout(
      id: 3, textID: 20, color: 0xff00_0000, size: 16, familyID: 21)
    googleFont.u8(214).u8(214)
    let googleSnapshot = try NativeSwiftDocumentSession.open(data: googleFont.data).snapshot()
    #expect(googleSnapshot.root.firstTextSnapshot?.familyName == "google:Orbitron")

    let canvasOperations = Writer()
    canvasOperations.header(width: 100, height: 100)
    canvasOperations.u8(200).int(1)
    canvasOperations.u8(201).int(2)
    canvasOperations.u8(205).int(3).int(-1)
    canvasOperations.u8(173).u8(130).u8(131).u8(214)
    canvasOperations.u8(214).u8(214).u8(214)
    let canvasSnapshot = try NativeSwiftDocumentSession.open(data: canvasOperations.data).snapshot()
    #expect(canvasSnapshot.root.children[0].children[0].commands.count == 2)
  }

  @Test func loomCanvasesAndMacros() throws {
    // AndroidX LOOM streams are also allowed to paint directly into the document canvas before
    // any structural definition.  That is a real drawable document, unlike a rootless data-only
    // stream, and must therefore gain an implicit canvas root at the first draw operation.
    let standaloneCanvas = Writer()
    standaloneCanvas.header(width: 100, height: 100)
    standaloneCanvas.u8(42).float(0).float(0).float(40).float(30).u8(214)
    let standaloneCanvasSnapshot = try NativeSwiftDocumentSession.open(data: standaloneCanvas.data)
      .snapshot()
    #expect(
      standaloneCanvasSnapshot.root.componentKind == "Canvas"
        && standaloneCanvasSnapshot.root.commands.count == 1
        && standaloneCanvasSnapshot.root.commands[0].kind == 10,
      "a standalone canvas draw did not produce an implicit root")

    // A standalone canvas is not a layout container. AndroidX can finish the wire stream directly
    // after a transform/draw command, so those commands must create the same implicit root as a
    // primitive draw and EOF must not require an artificial ContainerEnd.
    let standaloneTransform = Writer()
    standaloneTransform.header(width: 100, height: 100)
    standaloneTransform.u8(127).float(8).float(12)
    standaloneTransform.u8(42).float(0).float(0).float(20).float(20)
    let standaloneTransformSnapshot = try NativeSwiftDocumentSession.open(
      data: standaloneTransform.data
    ).snapshot()
    #expect(
      standaloneTransformSnapshot.root.componentKind == "Canvas"
        && standaloneTransformSnapshot.root.commands.map(\.kind) == [2, 10],
      "standalone transform commands did not share the implicit canvas root")

    // AndroidX writes a MacroDefine as an empty byte body followed by its container contents. The
    // body must be retained and executed only by MacroCall: decoding it where it is defined makes
    // definitions draw even when never called, and loses the caller's state.
    let loomMacro = Writer()
    loomMacro.header(width: 100, height: 100)
    loomMacro.u8(42).float(0).float(0).float(20).float(20)
    loomMacro.u8(246).int(30).int(0).int(0)
    loomMacro.u8(42).float(20).float(10).float(76).float(50).u8(214)
    loomMacro.u8(247).int(30).int(0).u8(214)
    let loomMacroSnapshot = try NativeSwiftDocumentSession.open(data: loomMacro.data).snapshot()
    #expect(
      loomMacroSnapshot.root.commands.count == 2,
      "a container-form LOOM macro was not expanded at its call site")

    // Parameters name caller-owned IDs. The macro body must therefore be rewritten while it is
    // decoded, not when the definition is captured: its template path id 100 has no resource, but
    // its call-site argument 200 does.
    let parameterizedLoomMacro = Writer()
    parameterizedLoomMacro.header(width: 100, height: 100)
    parameterizedLoomMacro.u8(200).int(1).u8(205).int(2).int(-1)
    parameterizedLoomMacro.u8(123).int(200).int(5)
      .int(Writer.nanReference(10)).float(0).float(0)
      .int(Writer.nanReference(15)).int(Writer.nanReference(16))
    parameterizedLoomMacro.u8(246).int(31).int(1).int(100).int(0)
    parameterizedLoomMacro.u8(124).int(100).u8(214)
    parameterizedLoomMacro.u8(247).int(31).int(1).int(200).u8(214)
    parameterizedLoomMacro.u8(214).u8(214)
    let parameterizedLoomSnapshot = try NativeSwiftDocumentSession.open(
      data: parameterizedLoomMacro.data
    ).snapshot()
    #expect(
      parameterizedLoomSnapshot.root.children[0].commands.first?.kind == 18,
      "a LOOM parameter id was not remapped to the call-site path")

    // A MacroArgument is an insertion point for a named MacroBlock under the call. The block is
    // captured structurally and decoded in the template's place, rather than being drawn as part
    // of the call container itself.
    let blockLoomMacro = Writer()
    blockLoomMacro.header(width: 100, height: 100)
    blockLoomMacro.u8(246).int(32).int(0).int(0)
    blockLoomMacro.u8(248).int(0).u8(214)
    blockLoomMacro.u8(247).int(32).int(0)
    blockLoomMacro.u8(249).int(0)
    blockLoomMacro.u8(42).float(10).float(10).float(40).float(40).u8(214)
    blockLoomMacro.u8(214)
    let blockLoomSnapshot = try NativeSwiftDocumentSession.open(data: blockLoomMacro.data).snapshot()
    #expect(
      blockLoomSnapshot.root.commands.count == 1,
      "a LOOM macro argument did not expand its supplied block")

    // ReferencedOperations is the sibling structural container used by AndroidX generated
    // documents. Its definition is inert until IncludeReferencedOperations injects it.
    let structuralReference = Writer()
    structuralReference.header(width: 100, height: 100)
    structuralReference.u8(42).float(0).float(0).float(20).float(20)
    structuralReference.u8(142).int(20)
    structuralReference.u8(42).float(20).float(10).float(76).float(50).u8(214)
    structuralReference.u8(245).int(20)
    let structuralReferenceSnapshot = try NativeSwiftDocumentSession.open(
      data: structuralReference.data
    ).snapshot()
    #expect(
      structuralReferenceSnapshot.root.commands.count == 2,
      "referenced operations were not expanded at their include site")

    let drawPath = Writer()
    drawPath.header(width: 100, height: 100)
    drawPath.u8(200).int(1).u8(201).int(2).u8(205).int(3).int(-1)
    drawPath.u8(123).int(42).int(5)
      .int(Writer.nanReference(10)).float(0).float(0)
      .int(Writer.nanReference(15)).int(Writer.nanReference(16))
    drawPath.u8(124).int(42)
    drawPath.u8(214).u8(214).u8(214)
    let pathSnapshot = try NativeSwiftDocumentSession.open(data: drawPath.data).snapshot()
    let pathCommand = pathSnapshot.root.children[0].children[0].commands[0]
    #expect(pathCommand.kind == 18 && pathCommand.path.count == 2)

    let staticDrawing = Writer()
    staticDrawing.header(width: 100, height: 100)
    staticDrawing.u8(123).int(42).int(5)
      .int(Writer.nanReference(10)).float(0).float(0)
      .int(Writer.nanReference(15)).int(Writer.nanReference(16))
    staticDrawing.u8(200).int(1).u8(205).int(2).int(-1)
    for (opcode, values) in [
      (39, [Float(0), 0, 90, 90]),
      (127, [5, 6]),
      (126, [1.2, 0.8, Float.nan, Float.nan]),
      (128, [0.1, 0.2]),
      (42, [0, 0, 20, 20]),
      (46, [20, 20, 10]),
      (47, [0, 0, 30, 30]),
      (51, [0, 0, 40, 40, 4, 4]),
      (52, [0, 0, 40, 40, 0, 90]),
      (56, [0, 0, 30, 20]),
    ] {
      staticDrawing.u8(opcode)
      for value in values { staticDrawing.float(value) }
    }
    staticDrawing.u8(38).int(42).u8(214).u8(214).u8(214)
    let drawingCommands =
      try NativeSwiftDocumentSession.open(data: staticDrawing.data).snapshot().root.children[0]
        .commands
    #expect(drawingCommands.map(\.kind) == [6, 2, 3, 5, 10, 12, 13, 14, 16, 11, 7])
    #expect(drawingCommands.last?.path.count == 2)
  }

  @Test func filterQualityAndCollapsibleLayout() throws {
    // Filter quality is paint state: an IMAGE_FILTER_QUALITY field (10) names a quality, the
    // legacy FILTER_BITMAP flag (17) names two, and a command that never saw either leaves it unset
    // so the renderer's own default stands.
    let filterQuality = Writer()
    filterQuality.header(width: 100, height: 100)
    filterQuality.u8(200).int(1)
    filterQuality.u8(42).int(0).int(0).int(10).int(10)
    filterQuality.u8(40).int(1).int((3 << 16) | 10)
    filterQuality.u8(42).int(0).int(10).int(10).int(20)
    filterQuality.u8(40).int(1).int(17)
    filterQuality.u8(42).int(0).int(20).int(10).int(30)
    filterQuality.u8(40).int(1).int((1 << 16) | 17)
    filterQuality.u8(42).int(0).int(30).int(10).int(40)
    filterQuality.u8(40).int(1).int((9 << 16) | 10)
    filterQuality.u8(42).int(0).int(40).int(10).int(50)
    filterQuality.u8(214).u8(214)
    let filterCommands =
      try NativeSwiftDocumentSession.open(data: filterQuality.data).snapshot().root.commands
    #expect(
      filterCommands.map(\.filterQuality) == [nil, 3, 0, 1, 1],
      "paint filter quality resolved to \(filterCommands.map { String(describing: $0.filterQuality) })")

    // A collapsible container keeps the children that fit and drops the rest, in priority order.
    // The decision is a pure function so both renderers and this test share it.
    func kept(_ sizes: [Float], available: Float, spacing: Float = 0) -> [Bool] {
      NativeSwiftCollapsible.keptChildren(
        sizes.map { NativeSwiftCollapsible.Child(mainSize: $0) },
        available: available, spacing: spacing)
    }
    #expect(
      kept([70, 70, 70], available: 250, spacing: 20) == [true, true, true],
      "a container with room for every child dropped one")
    #expect(
      kept([70, 70, 70], available: 180, spacing: 20) == [true, true, false],
      "the child that did not fit was not the one dropped")
    #expect(
      kept([70, 70, 70], available: 89, spacing: 20) == [true, false, false],
      "overflow did not stop at the first child that did not fit")
    // A priority sorts a child ahead of another: the reference keeps absent priorities first, then
    // the highest value, and drops from there.
    let prioritised = [
      NativeSwiftCollapsible.Child(mainSize: 100, priority: 1),
      NativeSwiftCollapsible.Child(mainSize: 100, priority: 5),
      NativeSwiftCollapsible.Child(mainSize: 100),
    ]
    #expect(
      NativeSwiftCollapsible.keptChildren(prioritised, available: 200, spacing: 0)
        == [false, true, true],
      "priority order did not decide which child was dropped")
    // A weighted child never counts against the available space, so it is never the reason another
    // child is dropped.
    let weighted = [
      NativeSwiftCollapsible.Child(mainSize: 100, weight: 1),
      NativeSwiftCollapsible.Child(mainSize: 200),
    ]
    #expect(
      NativeSwiftCollapsible.keptChildren(weighted, available: 150, spacing: 0)
        == [true, false],
      "a weighted child consumed space in the fit test")
    // A child the document marked GONE is never kept and never consumes space.
    let gone = [
      NativeSwiftCollapsible.Child(mainSize: 100, isGone: true),
      NativeSwiftCollapsible.Child(mainSize: 100),
    ]
    #expect(
      NativeSwiftCollapsible.keptChildren(gone, available: 100, spacing: 0) == [false, true],
      "a GONE child was kept or consumed space")
    // A fill child has no natural size, so it is not measured unbounded — an unbounded fill
    // resolves to infinity and the fit test then drops it from any container.
    #expect(
      !NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 1)
        && !NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 7)
        && !NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 8),
      "a fill dimension was measured unbounded")
    #expect(
      NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 2)
        && NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 6)
        && NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 3),
      "a fixed, wrapping or weighted dimension was not measured unbounded")

    // Unbounded space keeps everything the document did not hide.
    #expect(
      kept([10, 10], available: .infinity) == [true, true],
      "an unbounded container dropped a child")

    // The wire side: a collapsible column and a priority modifier reach the snapshot as such.
    let collapsible = Writer()
    collapsible.header(width: 200, height: 180)
    collapsible.u8(200).int(-2)
    collapsible.u8(233).int(-3).int(0).int(1).int(4).float(20)
    collapsible.u8(202).int(-4).int(0).int(1).int(4)
    collapsible.u8(16).int(6).float(100)
    collapsible.u8(67).int(6).float(70)
    collapsible.u8(235).int(1).float(2)
    collapsible.u8(214).u8(214).u8(214)
    let collapsibleSnapshot = try NativeSwiftDocumentSession.open(data: collapsible.data).snapshot()
    let collapsibleColumn = try #require(
      collapsibleSnapshot.root.children.first, "the collapsible fixture decoded no column")
    #expect(
      collapsibleColumn.componentKind == "CollapsibleColumnLayout",
      "a collapsible column reported kind \(collapsibleColumn.componentKind)")
    #expect(collapsibleColumn.isCollapsible, "a collapsible column did not report itself as one")
    #expect(
      collapsibleColumn.spacing == 20,
      "collapsible spacing resolved to \(collapsibleColumn.spacing)")
    let child = try #require(
      collapsibleColumn.children.first, "the collapsible fixture decoded no child")
    #expect(
      child.collapsiblePriority == 2 && child.collapsiblePriorityOrientation == 1,
      Comment(
        rawValue: "the priority modifier resolved to "
          + "\(String(describing: child.collapsiblePriority)) / "
          + "\(String(describing: child.collapsiblePriorityOrientation))"))

    let semantics = Writer()
    semantics.header(width: 100, height: 100)
    semantics.text(id: 10, "activate")
    semantics.text(id: 11, "Disabled action")
    semantics.text(id: 12, "Unavailable")
    semantics.u8(200).int(1).u8(201).int(2).u8(202).int(3).int(-1).int(1).int(4)
    semantics.u8(59).u8(210).int(10).int(-1).int(-1).u8(214)
    semantics.u8(250).int(11).u8(0).int(-1).int(12).u8(0).u8(0).u8(1)
    semantics.u8(214).u8(214).u8(214)
    let semanticsSession = try NativeSwiftDocumentSession.open(data: semantics.data)
    let semanticButton = try semanticsSession.snapshot().root.children[0].children[0]
    #expect(semanticButton.accessibility?.contentDescription == "Disabled action")
    #expect(semanticButton.accessibility?.stateDescription == "Unavailable")
    #expect(semanticButton.accessibility?.isEnabled == false)
    let disabledEvents = try semanticsSession.click(componentID: 3, timeSeconds: 0)
    #expect(disabledEvents == nil)

    let gestures = Writer()
    gestures.header(width: 100, height: 100)
    gestures.text(id: 10, "gesture")
    gestures.u8(200).int(1).u8(202).int(3).int(-1).int(1).int(4)
    for (opcode, payload) in [(83, 1), (83, 2), (219, -1), (220, -1), (225, -1)] {
      gestures.u8(opcode)
      if payload >= 0 { gestures.int(payload) }
      gestures.u8(210).int(10).int(-1).int(-1).u8(214)
    }
    gestures.u8(214).u8(214)
    let gestureSession = try NativeSwiftDocumentSession.open(data: gestures.data)
    let gestureNode = try gestureSession.snapshot().root.children[0]
    #expect(
      gestureNode.supportedGestures == [
        .longPress, .doubleTap, .touchDown, .touchUp, .touchCancel,
      ])
    for gesture in gestureNode.supportedGestures {
      let events = try gestureSession.gesture(gesture, componentID: 3, timeSeconds: 0)
      #expect(events == [.namedAction(name: "gesture", value: .none)])
    }
    let missingTap = try gestureSession.click(componentID: 3, timeSeconds: 0)
    #expect(missingTap == nil)
  }

  @Test func clickActionsAndMalformedInput() throws {
    // A click can set a float and copy a text. The same actions, and host actions, outside any
    // click modifier are inert rather than refusing the document, as in the reference.
    let valueActions = Writer()
    valueActions.header(width: 100, height: 100)
    valueActions.text(id: 10, "before")
    valueActions.text(id: 11, "after")
    valueActions.u8(80).int(40).float(1)
    valueActions.u8(222).int(40).float(99)
    valueActions.u8(216).int(1).int(10)
    valueActions.u8(200).int(1).u8(202).int(3).int(-1).int(1).int(4)
    valueActions.u8(59)
    valueActions.u8(222).int(40).float(7)
    valueActions.u8(213).int(10).int(11)
    valueActions.u8(214).u8(214).u8(214)
    let valueSession = try NativeSwiftDocumentSession.open(data: valueActions.data)
    let untouched = try valueSession.probeValues(timeSeconds: 0)
    #expect(
      untouched.floats[40] == 1 && untouched.texts[10] == "before", Comment(rawValue:
      "an inert action ran: \(String(describing: untouched.floats[40]))"))
    _ = try valueSession.click(componentID: 3, timeSeconds: 0)
    let clicked = try valueSession.probeValues(timeSeconds: 0)
    #expect(
      clicked.floats[40] == 7 && clicked.texts[10] == "after", Comment(rawValue:
      "value actions set \(String(describing: clicked.floats[40])), "
        + "\(String(describing: clicked.texts[10]))"))

    let integerAction = Writer()
    integerAction.header(width: 100, height: 100)
    integerAction.text(id: 10, "count")
    integerAction.u8(140).int(20).int(1)
    integerAction.u8(144).int(31).int(3).int(2).int(20).int(65_553)
    integerAction.u8(200).int(1).u8(202).int(3).int(-1).int(1).int(4)
    integerAction.u8(59)
      .u8(218).long(20).long(31)
      .u8(210).int(10).int(1).int(20)
      .u8(214).u8(214).u8(214)
    let integerSession = try NativeSwiftDocumentSession.open(data: integerAction.data)
    let integerEvents = try integerSession.click(componentID: 3, timeSeconds: 0)
    #expect(integerEvents == [.namedAction(name: "count", value: .integer(2))])

    let malformedPaint = Writer()
    malformedPaint.header(width: 100, height: 100)
    malformedPaint.u8(200).int(1)
    malformedPaint.u8(40).int(1).int(Int(Int32(bitPattern: 0xffff_0017)))
    malformedPaint.u8(214)
    do {
      _ = try NativeSwiftDocumentSession.open(data: malformedPaint.data)
      Issue.record("negative variable paint count was accepted")
    } catch let error as NativeSwiftCoreError {
      #expect(!error.isUnsupported)
    }

    do {
      _ = try NativeSwiftDocumentSession.open(data: editableTextDocument().dropLast())
      Issue.record("truncated input was accepted")
    } catch let error as NativeSwiftCoreError {
      #expect(!error.isUnsupported)
    }

    let unsupported = Writer()
    unsupported.header(width: 1, height: 1)
    unsupported.u8(255)
    do {
      _ = try NativeSwiftDocumentSession.open(data: unsupported.data)
      Issue.record("unsupported opcode was accepted")
    } catch let error as NativeSwiftCoreError {
      #expect(error.isUnsupported)
    }

    let tweenSession = try NativeSwiftDocumentSession.open(data: animatedFloatDocument())
    let firstTween = try tweenSession.snapshot(timeSeconds: 0)
    #expect(firstTween.root.commands[0].values[0] == 0)
    #expect(!firstTween.needsContinuousFrames, "an unchanged initial target should be idle")
    #expect(tweenSession.setFloat(10, for: "target"))
    let tweenStart = try tweenSession.snapshot(timeSeconds: 0)
    let tweenMiddle = try tweenSession.snapshot(timeSeconds: 0.5)
    let tweenEnd = try tweenSession.snapshot(timeSeconds: 1)
    #expect(tweenStart.root.commands[0].values[0] == 0)
    #expect(abs(tweenMiddle.root.commands[0].values[0] - 5) < 0.01)
    #expect(tweenEnd.root.commands[0].values[0] == 10)
    #expect(tweenStart.needsContinuousFrames && tweenMiddle.needsContinuousFrames)
    #expect(!tweenEnd.needsContinuousFrames, "a completed tween kept requesting frames")

    let staticSession = try NativeSwiftDocumentSession.open(
      data: animatedFloatDocument(includeAnimation: false))
    _ = try staticSession.snapshot(timeSeconds: 0)
    #expect(staticSession.setFloat(10, for: "target"))
    let staticUpdate = try staticSession.snapshot(timeSeconds: 0.5)
    #expect(staticUpdate.root.commands[0].values[0] == 10)
    #expect(!staticUpdate.needsContinuousFrames)
  }

  @Test func floatAnimationSamples() throws {
    // These samples are from the Kotlin RcFloatAnimation reference at 250ms intervals, scaled from
    // 0 to 10. They exercise the descriptor's packed type/parameter fields as well as each curve.
    let easingCases: [(type: Int, parameters: [Float], values: [Float])] = [
      (1, [], [2.366626, 7.75385, 9.592315]),
      (2, [], [1.020607, 3.217343, 6.313163]),
      (3, [], [5.569931, 8.179307, 9.507004]),
      (4, [], [2.5, 5, 7.5]),
      (5, [], [-0.596345, -0.872611, 1.837701]),
      (6, [], [8.162299, 10.872611, 10.596345]),
      (11, [0.1, 0.2, 0.8, 0.9], [3.158111, 5.74734, 8.091467]),
      (12, [0, 0.15, 0.7, 1], [0.886719, 4.15625, 7.960938]),
      (13, [], [5.299479, 7.65625, 9.726562]),
      (14, [], [9.116117, 10.15625, 10.055243]),
    ]
    for easing in easingCases {
      let session = try NativeSwiftDocumentSession.open(
        data: animatedFloatDocument(easingType: easing.type, parameters: easing.parameters))
      _ = try session.snapshot(timeSeconds: 0)
      #expect(session.setFloat(10, for: "target"))
      _ = try session.snapshot(timeSeconds: 0)
      for (time, expected) in zip([0.25, 0.5, 0.75], easing.values) {
        let actual = try session.snapshot(timeSeconds: time).root.commands[0].values[0]
        #expect(
          abs(actual - expected) < 0.01,
          "easing \(easing.type) at \(time)s: expected \(expected), got \(actual)")
      }
      let endpoint = try session.snapshot(timeSeconds: 1).root.commands[0].values[0]
      #expect(abs(endpoint - 10) < 0.01, "easing \(easing.type) did not reach its target")
    }

    let springSession = try NativeSwiftDocumentSession.open(data: springFloatDocument())
    let springStart = try springSession.snapshot(timeSeconds: 0)
    let springMoving = try springSession.snapshot(timeSeconds: 0.016)
    var springSettled = springMoving
    for frame in 2...600 {
      springSettled = try springSession.snapshot(timeSeconds: Double(frame) * 0.016)
    }
    #expect(springStart.root.commands[0].values[0] == 0)
    #expect(abs(springMoving.root.commands[0].values[0] - 0.004904) < 0.0001)
    #expect(springStart.needsContinuousFrames && springMoving.needsContinuousFrames)
    #expect(abs(springSettled.root.commands[0].values[0] - 1) < 0.001)
    #expect(!springSettled.needsContinuousFrames, "a settled spring kept requesting frames")

    do {
      _ = try NativeSwiftDocumentSession.open(data: unsupportedFloatAnimationDocument())
      Issue.record("an unknown easing mode was silently accepted")
    } catch let error as NativeSwiftCoreError {
      #expect(error.isUnsupported, "unknown easing should be an explicit unsupported error")
    }

    let concurrentSession = try NativeSwiftDocumentSession.open(data: concurrentStringDocument())
    // Worker threads carry no test context, so their failures are collected and reported here.
    let concurrentFailures = FailureLog()
    DispatchQueue.concurrentPerform(iterations: 1_000) { index in
      switch index % 3 {
      case 0:
        if !concurrentSession.setString("value-\(index)", for: "message") {
          concurrentFailures.append("concurrent setString refused value-\(index)")
        }
      case 1:
        do {
          _ = try concurrentSession.snapshot()
        } catch {
          concurrentFailures.append("concurrent snapshot failed: \(error)")
        }
      default:
        do {
          _ = try concurrentSession.detachedCopy().snapshot()
        } catch {
          concurrentFailures.append("concurrent detached snapshot failed: \(error)")
        }
      }
    }
    #expect(concurrentFailures.messages.isEmpty, "\(concurrentFailures.messages)")
  }

  // MARK: - Committed fixtures
  //
  // These were one `main` that dispatched on `CommandLine.arguments.count`, and the script passed
  // eight fixtures — so the blocks guarded by `== 2`, `== 4`, `== 6` and `== 8` never ran. They
  // were ported verbatim but disabled, so switching one on is a reviewed change rather than a
  // silent one. Everything but the encoder comparison below is now switched on.

  // The committed fixture is written by the Kotlin demo generator (`rc-player/demos`,
  // `-Prc.demo.output=…/editable-text.rc`); `editableTextDocument()` is a hand-written Swift
  // encoder of the same document, and the two have drifted apart. Regenerating the fixture would
  // not close the gap — it is the Kotlin writer's output either way — and it would reseed the fuzz
  // corpus, which is keyed by fixture position. Re-enable once the Swift encoder is brought back in
  // line with the Kotlin one.
  @Test(
    .disabled(
      "editable-text.rc (Kotlin demo generator) no longer matches the Swift test encoder byte for byte"
    ))
  func editableTextFixtureMatchesTheSwiftEncoder() throws {
    let kotlinFixture = try NativeTestFixtures.data("editable-text.rc")
    #expect(
      kotlinFixture == editableTextDocument(), "Swift test fixture differs from the Kotlin encoder")
  }

  @Test func titleCardFixture() throws {
    let titleData = try NativeTestFixtures.data("TitleCardRemote-640x480.rc")
    let titleSession = try NativeSwiftDocumentSession.open(data: titleData)
    let title = try titleSession.snapshot()
    #expect(title.width == 640 && title.height == 480)
    #expect(title.density == 2 && title.densityBehavior == 2)
    #expect(title.root.firstText == "Morning run")
    #expect(title.root.allText.contains("5.2 km · 28 min"))
    let backgroundPath = try #require(
      title.root.firstPathCommand, "title card has no native background path")
    #expect(backgroundPath.usesComponentGeometry)
    let backgroundY = backgroundPath.path.flatMap { element in
      stride(from: 1, to: element.values.count - (element.kind == 13 ? 1 : 0), by: 2).map {
        element.values[$0]
      }
    }
    #expect(backgroundY.max() ?? 0 > 100, "title background did not use measured content")
    let button = try #require(
      title.root.firstClickable, "title card has no native clickable component")
    let events = try titleSession.click(componentID: button.componentID, timeSeconds: 2)
    #expect(events == [.namedAction(name: "catalogAction", value: .float(1))])
  }

  @Test func indeterminateProgressFixture() throws {
    let progressData = try NativeTestFixtures.data("IndeterminateCircularProgress-400x400.rc")
    let progressSession = try NativeSwiftDocumentSession.open(data: progressData)
    let first = try progressSession.snapshot(timeSeconds: 0.25)
    let second = try progressSession.snapshot(timeSeconds: 0.75)
    #expect(first.needsContinuousFrames)
    #expect(first.root.allCommandValues != second.root.allCommandValues)
    #expect(first.root.allCommandValues.count >= 15)
  }

  @Test func circularAndArcProgressFixtures() throws {
    for (name, size) in zip(
      ["CircularProgressRemote-384x384.rc", "ArcProgressRemote-454x400.rc"],
      [(384, 384), (454, 400)])
    {
      let data = try NativeTestFixtures.data(name)
      let snapshot = try NativeSwiftDocumentSession.open(data: data).snapshot()
      #expect(snapshot.width == size.0 && snapshot.height == size.1)
      #expect(snapshot.root.allCommandKinds.contains(15))
    }
  }

  @Test func hostDensityFixture() throws {
    // The deferred-density capture — the one fixture here that computes its text size from
    // ID_DENSITY/ID_FONT_SIZE instead of folding a capture device's density in.
    //
    // This used to assert a refusal: the size arrives NaN-boxed and `literalFloat` rejected it
    // outright, so a `RemoteDensity.Host` capture was declined rather than drawn. The core now
    // carries the word to resolution time, so the same fixture renders, and its size is whatever
    // the host supplied — the inversion is the point, so it is written out rather than deleted.
    let hostData = try NativeTestFixtures.data("host-density.rc")
    let hostSession = try NativeSwiftDocumentSession.open(data: hostData)
    // `([33] 14.0 / [27] / 15.0 *)` cancels the density back out, leaving the font scale times
    // the 15sp the capture asked for: 15 unscaled, 22.5 at fontScale 1.5.
    let unscaledSnapshot = try hostSession.snapshot()
    let unscaled = try #require(
      unscaledSnapshot.root.firstTextSnapshot, "the deferred-density fixture rendered no text")
    #expect(
      abs(unscaled.size - 15) < 0.01, "expected a 15pt deferred size, got \(unscaled.size)")
    hostSession.setHostDensity(2.2625, fontScale: 1.5)
    let scaledSnapshot = try hostSession.snapshot()
    let scaled = try #require(
      scaledSnapshot.root.firstTextSnapshot,
      "the deferred-density fixture rendered no text after rescaling")
    #expect(
      abs(scaled.size - 22.5) < 0.01, "expected a 22.5pt deferred size, got \(scaled.size)")

    // The density the document resolves against is the player's own, and the host sets it. Kept
    // next to the assertions above because these are the two halves of the same contract: what
    // the player supplies, and what it refuses to be talked into.
    let session = try NativeSwiftDocumentSession.open(data: editableTextDocument())
    session.setHostDensity(2.2625, fontScale: 1.3)
    session.setHostDensity(0, fontScale: -1)  // Ignored: both reach a document as a divisor.
    _ = try session.snapshot()
  }

  @Test func imageBackgroundButtonFixture() throws {
    let imageData = try NativeTestFixtures.data("ImageBackgroundRemoteButton-454x200.rc")
    let session = try NativeSwiftDocumentSession.open(data: imageData)
    let snapshot = try session.snapshot()
    #expect(snapshot.width == 454 && snapshot.height == 200)
    #expect(snapshot.density == 2 && snapshot.densityBehavior == 0)
    try #require(snapshot.images.count == 1)
    #expect(snapshot.images[0].width == 8 && snapshot.images[0].height == 8)
    let imageID = snapshot.root.firstImage?.imageID ?? snapshot.root.firstTextureImageID
    #expect(imageID == snapshot.images[0].id, "image button has no native bitmap draw")
    #expect(snapshot.root.firstTextureCommand?.usesComponentGeometry == true)
    // The texture's shader matrix maps the 8x8 bitmap over the button — a 21.5x scale and a
    // -56 vertical offset, both computed by a MATRIX_EXPRESSION. The renderer used to drop it and
    // tile the bitmap at its natural size.
    let matrix = snapshot.root.firstTextureCommand?.shaderMatrix
    #expect(matrix?.count == 9, "texture shader matrix was \(String(describing: matrix))")
    #expect(
      abs((matrix?[0] ?? 0) - 21.5) < 0.01 && abs((matrix?[4] ?? 0) - 21.5) < 0.01,
      "texture matrix scale was \(String(describing: matrix))")
    #expect(
      abs((matrix?[5] ?? 0) + 56) < 0.01,
      "texture matrix translateY was \(String(describing: matrix))")
    // Setting the scrim's gradient replaces the texture shader. The texture id used to survive
    // the gradient, and the renderer's texture branch then drew the image a second time.
    let scrim = snapshot.root.allCommands.first { $0.gradient != nil }
    #expect(
      scrim?.textureImageID == nil, "the gradient scrim still carried the texture shader")
    let button = try #require(
      snapshot.root.firstClickable, "image button has no clickable component")
    let events = try session.click(componentID: button.componentID, timeSeconds: 0)
    #expect(events == [])
  }

  @Test func demoTileFixture() throws {
    let tileData = try NativeTestFixtures.data("demo-tile.rc")
    let tileSession = try NativeSwiftDocumentSession.open(data: tileData)
    let activeColors = try tileSession.snapshot().root.allColors
    #expect(tileSession.namedVariableID("light.kitchen.is_on") == 51)
    #expect(activeColors.contains(0xffff_be3e))
    #expect(activeColors.contains(0x33ff_be3e))
    #expect(tileSession.setInteger(0, for: "light.kitchen.is_on"))
    let inactiveColors = try tileSession.snapshot().root.allColors
    #expect(inactiveColors.contains(0xffb0_b0b0))
    #expect(inactiveColors.contains(0x33b0_b0b0))
  }

  // `ATTRIBUTE_TIME` reads a calendar field or an interval from a `LongConstant` instant, or
  // from the wall clock when it names none. The operations around it used to be refused, and the
  // whole document with them: root content behaviour, boolean and long constants, a debug
  // message and the sound family now decode, and the ones that are host side effects do nothing.
  // 1_700_000_000_000 is 2023-11-14T22:13:20Z, a Tuesday; the second instant is 1000 s earlier.
  @Test func timeAttributes() throws {
    let timed = Writer()
    timed.header(width: 100, height: 100)
    timed.u8(148).int(1).int64(1_700_000_000_000)
    timed.u8(148).int(2).int64(1_699_999_000_000)
    timed.u8(143).int(3).u8(1)
    timed.u8(65).int(1).int(0).int(1).int(0)
    timed.text(id: 6, "debug")
    timed.u8(179).int(6).float(3.25).int(0)
    timed.u8(169).int(4).int(4).u8(1).u8(2).u8(3).u8(4)
    timed.u8(141).int(4)
    timed.u8(206).int(5).float(1).float(1).float(1).int(1).float(440)
    let timeFields: [(output: Int, type: Int, arguments: [Int], expected: Float)] = [
      (20, 6, [], 20), (26, 7, [], 13), (27, 8, [], 22), (21, 9, [], 14),
      (23, 10, [], 10), (22, 11, [], 1), (24, 12, [], 2023), (29, 15, [], 318),
      (25, 3, [2], 1000), (30, 4, [2], 1000.0 / 60),
    ]
    for field in timeFields {
      timed.u8(172).int(field.output).int(1).u16(field.type).u16(field.arguments.count)
      for argument in field.arguments { timed.int(argument) }
    }
    timed.u8(172).int(28).int(1).u16(0).u16(0)
    let timedSession = try NativeSwiftDocumentSession.open(
      data: timed.data, toleratingRootlessData: true)
    let timedValues = try timedSession.probeValues(
      timeSeconds: 0, wallClock: NativeSwiftWallClock(epochMillis: 1_700_000_010_000))
    for field in timeFields {
      let resolved = timedValues.floats[field.output]
      #expect(
        resolved == field.expected,
        Comment(
          rawValue: "time attribute type \(field.type) resolved to "
            + "\(String(describing: resolved)), not \(field.expected)"))
    }
    #expect(
      timedValues.floats[28] == -10,
      "a from-now interval resolved to \(String(describing: timedValues.floats[28]))")
    // Without a wall clock there is no "now" to measure from, and the slot stays unwritten rather
    // than measuring from the 1970 epoch; fields of a stated instant still resolve.
    let unclocked = try timedSession.probeValues(timeSeconds: 0)
    #expect(
      unclocked.floats[28] == nil && unclocked.floats[24] == 2023,
      "an unclocked time attribute resolved to \(String(describing: unclocked.floats[28]))")
    let timedSnapshot = try timedSession.snapshot()
    #expect(
      !timedSnapshot.needsWallClockRefresh,
      "time attributes on a stated instant asked for a wall-clock refresh")
    let nowAttribute = Writer()
    nowAttribute.header(width: 100, height: 100)
    nowAttribute.u8(172).int(20).int(99).u16(6).u16(0)
    let nowSnapshot = try NativeSwiftDocumentSession.open(
      data: nowAttribute.data, toleratingRootlessData: true
    ).snapshot()
    #expect(
      nowSnapshot.needsWallClockRefresh,
      "a time attribute read from now did not ask for a wall-clock refresh")
  }

  // Semantics declared outside any component describe the document, and attach to its root
  // rather than refusing the whole document for having no component to modify.
  @Test func documentLevelSemantics() throws {
    let described = Writer()
    described.header(width: 100, height: 100)
    described.text(id: 1, "Label")
    described.u8(103).int(1)
    described.u8(250).int(1).u8(5).int(1).int(0).u8(1).u8(1).u8(1)
    let describedSnapshot = try NativeSwiftDocumentSession.open(
      data: described.data, toleratingRootlessData: true
    ).snapshot()
    #expect(
      describedSnapshot.root.accessibility?.role == 5
        && describedSnapshot.accessibilityRecords.count == 1,
      Comment(
        rawValue: "document-level semantics did not reach the root: "
          + "\(String(describing: describedSnapshot.root.accessibility))"))
  }

  /// A pre-layout `ClickArea` draws nothing and must not stop the document loading.
  @Test func legacyClickAreaLoads() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(64).int(7).int(0).float(0).float(0).float(50).float(50).int(0)
    document.u8(80).int(60).float(101)
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    #expect(
      values.floats[60] == 101,
      Comment(rawValue: "float 60: \(String(describing: values.floats[60]))"))
  }

  /// `conditional_nested_branches`: paths number conditionals within their own nesting, one in a
  /// branch that did not run is still traced as not executed, and `executedChildOps` counts the
  /// direct children of a branch that ran.
  @Test func nestedConditionalTracePaths() throws {
    let document = try #require(
      Data(
        base64Encoded:
          "AASMAAEAAAABAAAAAAAAAAQABQAEAAABkAAGAAQAAAGQDAkAHwAAABtjb25kaXRpb25hbF9uZXN0ZWRfYnJhbmNo"
          + "ZXMADgAEAAACAcj////+zf////3/////EAAAAAF/wAAAQwAAAAF/wAAAyf////zP////+7IEP4AAAEAAAACyAE"
          + "BAAABAQAAALkGgAABBoAAAQIAAANbWsgI/gAAAQAAAALIAQEAAAEBAAAAuQiAAAEGgAABAgAAA1rIBQEAAAEBA"
          + "AAA4QnAAAEEgAABCtAAAQiAAANbW1tbW1g=="))
    let traces = try NativeSwiftDocumentSession.open(data: document).snapshot().conditionalTraces
    let actual = traces.map { "\($0.path):\($0.executed):\($0.executedChildOps)" }
    #expect(
      actual == ["0:false:0", "0.0:false:0", "1:true:2", "1.0:true:1", "1.1:false:0"],
      Comment(rawValue: "conditional traces: \(actual)"))
  }

  /// Values that feed expressions but are produced after them — a derived text's length, an
  /// `ID_LOOKUP`, `EPOCH_SECOND` — reach those expressions in the same resolution, and the epoch
  /// keeps moving after the first frame.
  @Test func producedValuesReachTheirReadersInOneResolution() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.text(id: 1, "Hello RemoteCompose")
    document.u8(182).int(44).int(1).float(6).float(6)
    document.u8(156).int(50).int(44)
    document.u8(81).int(51).int(3)
      .int(Writer.nanReference(50)).float(2).int(Writer.floatOperator(3))
    document.u8(146).int(2).int(3).int(7).int(8).int(9)
    document.u8(192).int(40).int(2).float(1)
    document.u8(81).int(52).int(3)
      .int(Writer.nanReference(40)).float(1).int(Writer.floatOperator(1))
    document.u8(144).int(60).int(5).int(3).int(32).int(1).int(65_538)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    let first = try session.probeValues(
      timeSeconds: 0, wallClock: NativeSwiftWallClock(epochMillis: 1_789_050_600_999))
    #expect(
      first.floats[51] == 12 && first.floats[52] == 9 && first.integers[60] == 1_789_050_599,
      Comment(rawValue:
        "first resolution read length×2 \(String(describing: first.floats[51])), lookup+1 "
          + "\(String(describing: first.floats[52])), epoch-1 "
          + "\(String(describing: first.integers[60]))"))
    let later = try session.probeValues(
      timeSeconds: 5, wallClock: NativeSwiftWallClock(epochMillis: 1_789_050_605_000))
    #expect(
      later.integers[32] == 1_789_050_605 && later.integers[60] == 1_789_050_604,
      Comment(rawValue:
        "a later frame's epoch resolved to \(String(describing: later.integers[32])), "
          + "epoch-1 to \(String(describing: later.integers[60]))"))
  }

  /// A loop whose body conditions on its own index unrolls, with each pass's condition reading
  /// that pass's index, instead of refusing the document.
  @Test func loopBodyConditionsOnItsIndex() throws {
    let looped = Writer()
    looped.header(width: 100, height: 100)
    looped.u8(80).int(70).float(0)
    looped.u8(215).int(70).float(0).float(1).float(3)
    looped.u8(178).u8(4).int(Writer.nanReference(70)).float(0)
    looped.u8(42).int(Writer.nanReference(70)).float(0).float(10).float(10)
    looped.u8(214)
    looped.u8(214)
    let commands = try NativeSwiftDocumentSession.open(data: looped.data).snapshot().root.commands
    #expect(
      commands.filter { $0.kind == 10 }.map { $0.values[0] } == [1, 2],
      Comment(rawValue: "a conditioned loop drew at \(commands.map { ($0.kind, $0.values) })"))
  }

  /// A colour the host has set by name stays the host's under a dark theme, even when it equals
  /// the document's light fallback.
  @Test func hostColourSurvivesAThemeSwitch() throws {
    let themed = Writer()
    themed.header(width: 100, height: 100)
    themed.u8(196).int(10).int(1).u16(0).u16(0).int(Int(Int32(bitPattern: 0xFF11_1111)))
      .int(Int(Int32(bitPattern: 0xFF22_2222)))
    themed.namedVariable(id: 10, type: NativeSwiftNamedVariableType.color, name: "tint")
    let session = try NativeSwiftDocumentSession.open(
      data: themed.data, toleratingRootlessData: true)
    #expect(session.setColor(0xFF11_1111, for: "tint"))
    session.setRequestedTheme(NativeSwiftTheme.dark)
    let color = try session.probeValues(timeSeconds: 0).colors[10]
    #expect(
      color == 0xFF11_1111,
      Comment(rawValue: "a host colour became \(String(describing: color)) under a dark theme"))
  }

  /// A click's actions run in order against live state: a float action reads the integer an
  /// earlier action wrote, and a float write is visible, truncated, as an integer.
  @Test func clickActionsSeeEarlierWrites() throws {
    let clicked = Writer()
    clicked.header(width: 100, height: 100)
    clicked.u8(200).int(1).u8(202).int(3).int(-1).int(1).int(4)
    clicked.u8(59)
      .u8(212).int(20).int(3)
      .u8(222).int(21).int(Writer.nanReference(20))
      .u8(222).int(22).float(2.7)
      .u8(214).u8(214).u8(214)
    let session = try NativeSwiftDocumentSession.open(data: clicked.data)
    _ = try session.click(componentID: 3, timeSeconds: 0)
    let values = try session.probeValues(timeSeconds: 0)
    #expect(
      values.floats[21] == 3 && values.integers[22] == 2,
      Comment(rawValue:
        "after a click, float 21 is \(String(describing: values.floats[21])) and integer 22 is "
          + "\(String(describing: values.integers[22]))"))
  }

  /// A stored instant is read in its own offset: January in London is GMT even when the wall clock
  /// is in summer time. And an interval measured in hours from now moves with the clock.
  @Test func storedInstantsUseTheirOwnOffset() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(148).int(1).int64(1_768_478_400_000)
    document.u8(172).int(40).int(1).u16(8).u16(0)
    document.u8(172).int(41).int(1).u16(2).u16(0)
    let session = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true)
    let london = try #require(TimeZone(identifier: "Europe/London"))
    let summer = NativeSwiftWallClock(
      epochMillis: 1_782_907_200_000, offsetSeconds: 3600, timeZone: london)
    let hour = try session.probeValues(timeSeconds: 0, wallClock: summer).floats[40]
    #expect(
      hour == 12,
      Comment(rawValue: "a January noon UTC read as hour \(String(describing: hour))"))
    #expect(
      try session.snapshot(wallClock: summer).needsContinuousFrames,
      "an hours-from-now interval did not ask for continuous frames")
  }

  /// `animation_spec_component_binding`: a component adopts the `AnimationSpec` among its own
  /// operations, as the reference binds it, whatever animation id it declares.
  @Test func componentsAdoptTheirOwnAnimationSpec() throws {
    let document = try #require(
      Data(
        base64Encoded:
          "AASMAAEAAAABAAAAAAAAAAQABQAEAAABkAAGAAQAAAGQDAkAJAAAACBhbmltYXRpb25fc3BlY19jb21wb25lbnRf"
          + "YmluZGluZwAOAAQAAAIByP////7M/////f////8AAAABAAAABAAAAAAQAAAAAX/AAABDAAAAAX/AAADJ/////Mr/"
          + "///7/////wAAAAIAAAACEAAAAABCyAAAQwAAAABCSAAADgAAAAtC8AAAAAAAAkQgAAAAAAAEAAAABAAAAAXWyv//"
          + "//r/////AAAAAgAAAAIQAAAAAELIAABDAAAAAEJIAAAOAAAAFkRhAAAAAAADQiAAAAAAAAUAAAAAAAAAB9bW1tY="))
    let root = try NativeSwiftDocumentSession.open(data: document).snapshot().root
    var adopted: [Int?] = []
    func collect(_ node: NativeSwiftNodeSnapshot) {
      if node.componentKind == "BoxLayout" { adopted.append(node.animationSpecID) }
      node.children.forEach(collect)
    }
    collect(root)
    #expect(adopted == [11, 22], Comment(rawValue: "boxes adopted specs \(adopted)"))
  }

  /// Two components may each carry a spec under the same id; each keeps the one it read, not the
  /// last one the document declared.
  @Test func componentsKeepTheirOwnSpecUnderASharedID() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(200).int(1)
    document.u8(202).int(3).int(-1).int(1).int(1)
    document.u8(14).int(5).float(120).int(2).float(640).int(4).int(0).int(1)
    document.u8(214)
    document.u8(202).int(4).int(-1).int(1).int(1)
    document.u8(14).int(5).float(900).int(3).float(40).int(5).int(0).int(1)
    document.u8(214)
    document.u8(214)
    let root = try NativeSwiftDocumentSession.open(data: document.data).snapshot().root
    var durations: [Float?] = []
    func collect(_ node: NativeSwiftNodeSnapshot) {
      if node.componentKind == "BoxLayout" { durations.append(node.animationSpec?.motionDuration) }
      node.children.forEach(collect)
    }
    collect(root)
    #expect(durations == [120, 900], Comment(rawValue: "boxes adopted durations \(durations)"))
  }

  /// A spec nested in a body inside a component (here a canvas-operations body) is not one of the
  /// component's own operations, and the component keeps the default.
  @Test func aNestedSpecIsNotAdopted() throws {
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(200).int(1)
    document.u8(202).int(3).int(-1).int(1).int(1)
    document.u8(173)
    document.u8(14).int(5).float(120).int(2).float(640).int(4).int(0).int(1)
    document.u8(214)
    document.u8(214)
    document.u8(214)
    let root = try NativeSwiftDocumentSession.open(data: document.data).snapshot().root
    var adopted: [Int?] = []
    func collect(_ node: NativeSwiftNodeSnapshot) {
      if node.componentKind == "BoxLayout" { adopted.append(node.animationSpecID) }
      node.children.forEach(collect)
    }
    collect(root)
    #expect(adopted == [nil], Comment(rawValue: "a nested spec was adopted: \(adopted)"))
  }

  /// `text_anchored_pan_alignment`: a `panY` of the id-0 NaN is the reference's "no vertical pan"
  /// and stays NaN, so the text keeps its baseline; a numeric `panY` resolves as it is.
  @Test func anchoredTextKeepsTheNoPanSentinel() throws {
    let document = try #require(
      Data(
        base64Encoded:
          "AASMAAEAAAABAAAAAAAAAAQABQAEAAABkAAGAAQAAAGQDAkAHwAAABt0ZXh0X2FuY2hvcmVkX3Bhbl9hbGlnbm1l"
          + "bnQADgAEAAACAcj////+zf////3/////EAAAAAF/wAAAQwAAAAF/wAAAyf////woAAAABQAAAAT/OL34AAAACAAA"
          + "AAFBwAAAZgAAACoAAAAGQW5jaG9yhQAAACpDSAAAQ0gAAL+AAAB/wAAAAAAAAIUAAAAqQ0gAAENIAAAAAAAAf8AA"
          + "AAAAAACFAAAAKkNIAABDSAAAP4AAAH/AAAAAAAAAhQAAACpCyAAAQ5YAAAAAAAC/gAAAAAAAAIUAAAAqQsgAAEOW"
          + "AAAAAAAAAAAAAAAAAACFAAAAKkLIAABDlgAAAAAAAD+AAAAAAAAA1tbW"))
    var pans: [Float] = []
    var unset: [[Int]] = []
    var geometryIsFinite = true
    func collect(_ node: NativeSwiftNodeSnapshot) {
      for command in node.commands where command.kind == NativeSwiftDrawKind.text {
        pans.append(command.values[3])
        unset.append(command.unsetValueIndices)
        geometryIsFinite = geometryIsFinite && command.geometryValues.allSatisfy(\.isFinite)
      }
      node.children.forEach(collect)
    }
    collect(try NativeSwiftDocumentSession.open(data: document).snapshot().root)
    #expect(
      pans.count == 6 && pans.prefix(3).allSatisfy(\.isNaN) && Array(pans.suffix(3)) == [-1, 0, 1],
      Comment(rawValue: "anchored panY values \(pans)"))
    // The hosts validate `geometryValues`, which must leave out exactly the sentinel: otherwise
    // they refuse every document that uses it before the baseline branch can run.
    #expect(unset == [[3], [3], [3], [], [], []], Comment(rawValue: "unset indices \(unset)"))
    #expect(geometryIsFinite)
  }

  // MARK: - Graphics-layer attribute ids (#423)
  //
  // Each attribute is read from the id AndroidX's `GraphicsLayerModifierOperation` gives it:
  // 5/6 used to be read as the translation and 8 as the alpha.

  @Test func graphicsLayerScaleAndRotation() throws {
    let layer = try graphicsLayerSnapshot([
      (NativeSwiftGraphicsLayerAttribute.scaleX, 2),
      (NativeSwiftGraphicsLayerAttribute.scaleY, 3),
      (NativeSwiftGraphicsLayerAttribute.rotationZ, 45),
    ])
    #expect(layer.scaleX == 2)
    #expect(layer.scaleY == 3)
    #expect(layer.rotationZ == 45)
    #expect(layer.translationX == 0 && layer.translationY == 0)
    #expect(layer.alpha == 1)
    #expect(layer.transformOriginX == 0.5 && layer.transformOriginY == 0.5)
  }

  @Test func graphicsLayerTranslationIsSevenAndEight() throws {
    let layer = try graphicsLayerSnapshot([
      (NativeSwiftGraphicsLayerAttribute.translationX, 12),
      (NativeSwiftGraphicsLayerAttribute.translationY, -7),
    ])
    #expect(layer.translationX == 12)
    #expect(layer.translationY == -7)
    #expect(layer.alpha == 1, "TRANSLATION_Y (8) was read as the alpha")
    #expect(layer.transformOriginX == 0.5 && layer.transformOriginY == 0.5)
    #expect(!layer.isIdentity)
  }

  @Test func graphicsLayerTransformOriginIsFiveAndSix() throws {
    let layer = try graphicsLayerSnapshot([
      (NativeSwiftGraphicsLayerAttribute.transformOriginX, 0.25),
      (NativeSwiftGraphicsLayerAttribute.transformOriginY, 1),
    ])
    #expect(layer.transformOriginX == 0.25)
    #expect(layer.transformOriginY == 1)
    #expect(
      layer.translationX == 0 && layer.translationY == 0,
      "TRANSFORM_ORIGIN (5/6) was read as the translation")
    // A pivot alone moves nothing.
    #expect(layer.isIdentity)
  }

  @Test func graphicsLayerWrittenTopLeftOriginIsKept() throws {
    // A written 0 is a pivot, not a missing attribute; only an absent one is the centre.
    let layer = try graphicsLayerSnapshot([
      (NativeSwiftGraphicsLayerAttribute.transformOriginX, 0),
      (NativeSwiftGraphicsLayerAttribute.scaleX, -1),
    ])
    #expect(layer.transformOriginX == 0)
    #expect(layer.transformOriginY == 0.5)
    #expect(layer.scaleX == -1)
  }

  @Test func graphicsLayerAlphaIsEleven() throws {
    let layer = try graphicsLayerSnapshot([(NativeSwiftGraphicsLayerAttribute.alpha, 0.4)])
    #expect(layer.alpha == 0.4)
    #expect(layer.translationX == 0 && layer.translationY == 0)
    #expect(layer.scaleX == 1 && layer.scaleY == 1 && layer.rotationZ == 0)
    #expect(!layer.isIdentity)
  }

  @Test func graphicsLayerUnappliedAttributesLeaveTheLayerAlone() throws {
    // Parsed and deliberately not applied: none of these may leak into a 2D field.
    let layer = try graphicsLayerSnapshot([
      (NativeSwiftGraphicsLayerAttribute.rotationX, 30),
      (NativeSwiftGraphicsLayerAttribute.rotationY, 60),
      (NativeSwiftGraphicsLayerAttribute.translationZ, 4),
      (NativeSwiftGraphicsLayerAttribute.shadowElevation, 8),
      (NativeSwiftGraphicsLayerAttribute.cameraDistance, 16),
    ])
    #expect(
      layer
        == NativeSwiftGraphicsLayerSnapshot(
          scaleX: 1, scaleY: 1, translationX: 0, translationY: 0, rotationZ: 0, alpha: 1,
          transformOriginX: 0.5, transformOriginY: 0.5))
    #expect(layer.isIdentity)
  }

  @Test func graphicsLayerSnapshotInitDefaultsToACentredOrigin() {
    let layer = NativeSwiftGraphicsLayerSnapshot(
      scaleX: 1, scaleY: 1, translationX: 0, translationY: 0, rotationZ: 0, alpha: 1)
    #expect(layer.transformOriginX == 0.5 && layer.transformOriginY == 0.5)
  }

  /// Decodes a column carrying one MODIFIER_GRAPHICS_LAYER of float attributes and returns the
  /// layer the snapshot resolved for it.
  private func graphicsLayerSnapshot(
    _ attributes: [(id: Int, value: Float)],
    sourceLocation: SourceLocation = #_sourceLocation
  ) throws -> NativeSwiftGraphicsLayerSnapshot {
    let snapshot = try NativeSwiftDocumentSession.open(data: graphicsLayerDocument(attributes))
      .snapshot()
    let column = try #require(
      snapshot.root.children.first?.children.first,
      "the graphics-layer fixture decoded no child component", sourceLocation: sourceLocation)
    return try #require(
      column.graphicsLayer, "the graphics-layer modifier did not reach the snapshot",
      sourceLocation: sourceLocation)
  }

  // MARK: - Regressions for #397
  //
  // Each malformed document here used to trap the process instead of failing the open or
  // degrading one frame.

  @Test func unboundedNestedMacroCallIsMalformed() {
    expectMalformed(
      nestedMacroCallDocument(depth: 300), containing: "Macro body nesting",
      "an unbounded nested macro-call chain")
  }

  /// A conditional that does not run still traces the conditionals nested in its body, and that
  /// walk recursed once per level with no bound: a debug build overflows swift-testing's 512 KiB
  /// worker stack long before a document this size runs out of bytes. It is now iterative and
  /// bounded like an executed chain, so the document is refused rather than crashing the host.
  @Test func unboundedNestedSkippedConditionalIsMalformed() {
    expectMalformed(
      nestedSkippedConditionalDocument(depth: 300), containing: "Conditional nesting",
      "an unbounded chain of conditionals inside one that does not run")
  }

  /// Time-attribute intervals between two document-chosen instants were Int64 differences, and
  /// `LongConstant`s of Int64.max and Int64.min overflowed them: a 55-byte document trapped. They
  /// are taken in Double now, and every interval form resolves to a finite value.
  @Test func extremeTimeAttributeIntervalsDoNotTrap() throws {
    typealias TimeType = NativeSwiftTimeAttributeType
    let document = Writer()
    document.header(width: 100, height: 100)
    document.u8(NativeSwiftWireOpcode.dataLong).int(1).int64(.max)
    document.u8(NativeSwiftWireOpcode.dataLong).int(2).int64(.min)
    // ATTRIBUTE_TIME(output, time id, type, argument count, arguments...).
    document.u8(NativeSwiftWireOpcode.attributeTime).int(60).int(1)
      .u16(TimeType.fromArgumentSeconds).u16(1).int(2)
    document.u8(NativeSwiftWireOpcode.attributeTime).int(61).int(1)
      .u16(TimeType.fromNowSeconds).u16(0)
    document.u8(NativeSwiftWireOpcode.attributeTime).int(62).int(1)
      .u16(TimeType.fromLoadSeconds).u16(0)
    let values = try NativeSwiftDocumentSession.open(
      data: document.data, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0, wallClock: NativeSwiftWallClock(epochMillis: .min))
    for id in 60...62 {
      let value = values.floats[id]
      #expect(
        value.map { $0.isFinite && $0 > 0 } == true,
        Comment(rawValue: "interval \(id): \(String(describing: value))"))
    }
  }

  /// A MacroDefine that names one parameter id twice built its argument mapping with
  /// `Dictionary(uniqueKeysWithValues:)`, which traps on the duplicate as soon as the macro is
  /// called. The call is refused instead.
  @Test func repeatedMacroParameterIsMalformed() {
    let document = Writer()
    document.header(width: 100, height: 100)
    // MacroDefine(id 1, parameters [7, 7], body size 0 = container body), with an empty body.
    document.u8(NativeSwiftWireOpcode.macroDefine).int(1).int(2).int(7).int(7).int(0)
    document.u8(NativeSwiftWireOpcode.containerEnd)
    // MacroCall(id 1, arguments [10, 11]) with no blocks.
    document.u8(NativeSwiftWireOpcode.macroCall).int(1).int(2).int(10).int(11)
    document.u8(NativeSwiftWireOpcode.containerEnd)
    expectMalformed(
      document.data, containing: "Macro parameter id 7 is repeated",
      "a macro that repeats a parameter id")
  }

  /// `CoreText` folds a `TextStyle`'s parent chain, which the document makes as long as it likes;
  /// folding it by recursion overflowed a 512 KiB stack on a few thousand styles. It is iterative
  /// and bounded now.
  @Test func unboundedTextStyleChainIsMalformed() {
    let document = Writer()
    document.header(width: 100, height: 100)
    let depth = 300
    for id in 1...depth {
      // TextStyle(two properties: its own id, and its parent's, -1 for none).
      document.u8(NativeSwiftWireOpcode.textStyle).u16(2)
      document.u8(NativeSwiftTextProperty.componentID).int(id)
      document.u8(NativeSwiftTextProperty.textStyleID).int(id == 1 ? -1 : id - 1)
    }
    // CoreText(text id 5, one property: the deepest style).
    document.u8(NativeSwiftWireOpcode.coreText).int(5).u16(1)
    document.u8(NativeSwiftTextProperty.textStyleID).int(depth)
    expectMalformed(
      document.data, containing: "TextStyle inheritance exceeds",
      "a TextStyle chain deeper than the nesting bound")
  }

  /// A LoopStart unrolls every pass into memory before any of it runs. Ten thousand passes of a
  /// 4 KB body -- a skipped conditional around a 1,024-word paint -- would build 40 MB from a
  /// document a few kilobytes long; the expansion is charged against a byte budget as it grows and
  /// refused once it passes it.
  @Test func marqueeModifierFollowsTheFirstPaintClock() throws {
    // `modifier_marquee_ticker`: a 240-wide text carrying MODIFIER_MARQUEE (228), which used to
    // refuse the whole document. Its offset runs on the wall clock from the first paint; the corpus
    // harness holds that clock a second before the sequence's base, which a host pins here.
    let document = Data(
      base64Encoded:
        "AASMAAEAAAABAAAAAAAAAAQABQAEAAABLAAGAAQAAABkDAkAGwAAABdtb2RpZmllcl9tYXJxdWVlX3RpY2tlcgAOAAQAAAIByP////7L/////f////8AAAABAAAABAAAAAAQAAAAAT+AAABDAAAAAT+AAAA3AAAAAAAAAAAAAAAAAAAAAD3w8PE+JKSlPmzs7T+AAAAAAAAAyf////xmAAAAKgAAABxSZW1vdGVDb21wb3NlIE1hcnF1ZWUgVGlja2Vy7wAAACoABQH////7BUGAAAAJAAAABQoAAAACCwAAAAEQAAAAAENwAABDAAAAAEIgAAA3AAAAAAAAAAAAAAAAAAAAAD8Tk5Q/RcXGP339/j+AAAAAAAAA5P////8AAAAAAAAAAEP6AABCAAAAQnAAAMn////61tbW1tY="
    )!
    let session = try NativeSwiftDocumentSession.open(data: document)
    session.setFirstPaintTime(9)
    func marquee(in node: NativeSwiftNodeSnapshot) -> NativeSwiftMarqueeSnapshot? {
      node.marquee ?? node.children.lazy.compactMap(marquee(in:)).first
    }
    let first = try session.snapshot(timeSeconds: 10)
    let decoded = try #require(marquee(in: first.root))
    #expect(
      decoded
        == NativeSwiftMarqueeSnapshot(
          iterations: -1, animationMode: 0, repeatDelayMillis: 0, initialDelayMillis: 500,
          spacing: 32, velocity: 60),
      "the marquee's fields did not decode: \(decoded)")
    // Frames are the host's to ask for, once it has measured an overflow; the core only makes sure
    // the clock-driven offset is never served from its static snapshot cache.
    #expect(!first.needsContinuousFrames, "a marquee that may fit asked for frames by itself")
    #expect(
      try session.snapshot(timeSeconds: 11).marqueeElapsedSeconds == 2,
      "a marquee's clock was served from the static snapshot cache")
    // Ahem at 16 sets the 28-character line 448 wide; with its 32 spacing it overruns 240 by 240.
    // Frame n of the sequence is 10 + n/60 seconds; the corpus's `scroll_x` at each capture:
    let expected: [(frame: Double, offset: Float)] = [
      (0, 0), (30, -120), (60, -204.85), (90, -240), (150, -120), (210, 0), (240, -35.15),
    ]
    for (frame, offset) in expected {
      let snapshot = try session.snapshot(timeSeconds: 10 + frame / 60)
      let actual = decoded.offset(
        overflowDistance: 240, density: 1, elapsedSeconds: snapshot.marqueeElapsedSeconds)
      #expect(abs(actual - offset) < 0.5, "frame \(frame): \(actual), expected \(offset)")
    }
    // Unpinned, the first snapshot is the first paint.
    let live = try NativeSwiftDocumentSession.open(data: document)
    #expect(try live.snapshot(timeSeconds: 3).marqueeElapsedSeconds == 0)
    #expect(try live.snapshot(timeSeconds: 4.5).marqueeElapsedSeconds == 1.5)

    // A velocity of zero never moves the content, yet would keep a host painting forever; the
    // Kotlin player refuses it, and so does this one. The velocity is the modifier's last word.
    var stalled = [UInt8](document)
    let velocity: [UInt8] = [0x42, 0x70, 0x00, 0x00]
    let at = try #require(
      (0...(stalled.count - velocity.count)).last { Array(stalled[$0..<$0 + 4]) == velocity })
    stalled.replaceSubrange(at..<at + 4, with: [0, 0, 0, 0])
    #expect(throws: NativeSwiftCoreError.self) {
      try NativeSwiftDocumentSession.open(data: Data(stalled)).snapshot(timeSeconds: 0)
    }
  }

  @Test func amplifyingLoopIsMalformed() {
    let document = Writer()
    document.header(width: 100, height: 100)
    // LoopStart(index id 0, from 0, step 1, until 10,000).
    document.u8(NativeSwiftWireOpcode.loopStart).int(0).float(0).float(1).float(10_000)
    document.u8(NativeSwiftWireOpcode.conditionalOperations).u8(NativeSwiftConditionalType.equal)
      .float(0).float(1)
    document.u8(NativeSwiftWireOpcode.paintValues).int(1_024)
    for _ in 0..<1_024 { document.int(0) }
    document.u8(NativeSwiftWireOpcode.containerEnd)  // The conditional.
    document.u8(NativeSwiftWireOpcode.containerEnd)  // The loop body.
    expectMalformed(
      document.data, containing: "LOOM expansion exceeds", "a loop that amplifies its body")
  }

  private func expectMalformed(
    _ data: Data, containing reason: String, _ label: String,
    sourceLocation: SourceLocation = #_sourceLocation
  ) {
    do {
      _ = try NativeSwiftDocumentSession.open(data: data)
      Issue.record("\(label) was accepted", sourceLocation: sourceLocation)
    } catch let error as NativeSwiftCoreError {
      guard case .malformed = error else {
        Issue.record(
          "\(label): expected a malformed error, got \(error)", sourceLocation: sourceLocation)
        return
      }
      #expect(
        error.description.contains(reason), "\(label): \(error)", sourceLocation: sourceLocation)
    } catch {
      Issue.record("\(label): unexpected error \(error)", sourceLocation: sourceLocation)
    }
  }

  /// DrawTextRun offsets are UTF-16 code units; out-of-range bounds clamp instead of trapping.
  @Test func drawTextRunSlicingRegression() throws {
    let cases: [(start: Int, end: Int, expected: String)] = [
      (2, 3, "a"),  // The emoji is two UTF-16 units, so unit 2 is "a".
      (0, -1, "👋ab"),  // -1 runs to the end, as in AndroidX.
      (2, 100, "ab"),
      (-5, -3, ""),
      (3, 1, ""),
      (100, 200, ""),
    ]
    for run in cases {
      let snapshot = try NativeSwiftDocumentSession.open(
        data: drawTextRunDocument(start: run.start, end: run.end)
      ).snapshot()
      let text = snapshot.root.allCommands.lazy.compactMap(\.text).first
      #expect(
        text == run.expected,
        Comment(
          rawValue: "DrawTextRun \(run.start)..<\(run.end): expected \(run.expected), "
            + "got \(String(describing: text))"))
    }
  }

  /// Non-finite float indices and lengths used to trap in `Int(Float)`.
  @Test func nonFiniteIndexRegression() throws {
    let session = try NativeSwiftDocumentSession.open(
      data: nonFiniteIndexDocument(), toleratingRootlessData: true)
    let values = try session.probeValues(timeSeconds: 0)
    #expect(
      values.texts[61] == "",
      "an infinite text-transform range selected \(String(describing: values.texts[61]))")
    let infiniteList = try session.probeFloatList(id: 70, dynamic: true, timeSeconds: 0)
    #expect(infiniteList == nil, "an infinite dynamic list length was allocated")
    let updatedList = try session.probeFloatList(id: 71, dynamic: true, timeSeconds: 0)
    #expect(
      updatedList == [0, 0],
      "an infinite list index was applied: \(String(describing: updatedList))")
  }

  /// A gradient kind that reserves no tile-mode word used to read one past the paint words.
  @Test func unknownGradientKindRegression() throws {
    _ = try NativeSwiftDocumentSession.open(data: unknownGradientKindDocument()).snapshot()
  }

  /// A NaN animation progress used to force-unwrap a failed spline segment search.
  @Test func nanSplineProgressRegression() throws {
    let session = try NativeSwiftDocumentSession.open(
      data: animatedFloatDocument(easingType: 12, parameters: [0, 0.5, 1]))
    _ = try session.snapshot(timeSeconds: 0)
    #expect(session.setFloat(10, for: "target"))
    do {
      _ = try session.snapshot(timeSeconds: .nan)
    } catch is NativeSwiftCoreError {
      // Rejecting the frame is fine; trapping is not.
    }
  }

  /// A container-bodied MacroDefine whose body is a MacroCall → MacroBlock chain that never closes.
  private func nestedMacroCallDocument(depth: Int) -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(246).int(1).int(0).int(0)
    for _ in 0..<depth { output.u8(247).int(2).int(0).u8(249).int(0) }
    return output.data
  }

  /// A top-level conditional that does not run (0 == 1), wrapping `depth` nested conditionals.
  private func nestedSkippedConditionalDocument(depth: Int) -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    let conditional = NativeSwiftWireOpcode.conditionalOperations
    output.u8(conditional).u8(NativeSwiftConditionalType.equal).float(0).float(1)
    for _ in 0..<depth {
      output.u8(conditional).u8(NativeSwiftConditionalType.equal).float(0).float(0)
    }
    for _ in 0...depth { output.u8(NativeSwiftWireOpcode.containerEnd) }
    return output.data
  }

  /// A container-bodied MacroDefine holding more operations than a whole document may.
  private func drawTextRunDocument(start: Int, end: Int) -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.text(id: 60, "👋ab")
    output.u8(200).int(1)
    // DrawTextRun(text, start, end, context start, context end, x, y, rtl).
    output.u8(43).int(60).int(start).int(end).int(0).int(0).float(0).float(0).u8(0)
    output.u8(214)
    return output.data
  }

  private func nonFiniteIndexDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.text(id: 60, "hello")
    // TextTransform(output, source, start, length, identity) over an infinite range.
    output.u8(199).int(61).int(60).float(.infinity).float(-.infinity).int(0)
    // A dynamic float list of infinite length, and a two-element one updated at -infinity.
    output.u8(197).int(70).float(.infinity)
    output.u8(197).int(71).float(2)
    output.u8(198).int(71).float(-.infinity).float(5)
    return output.data
  }

  private func unknownGradientKindDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    // PaintData with one gradient command (11) of unknown kind 3: a one-colour meta word, the
    // colour, no stops and two coordinates, sized like a sweep with no tile-mode word after them.
    output.u8(40).int(6).int((3 << 16) | 11).int(1).int(Int(Int32(bitPattern: 0xff00_00ff)))
      .int(0).float(0).float(0)
    output.u8(200).int(1).u8(42).float(0).float(0).float(10).float(10).u8(214)
    return output.data
  }

  /// A root holding one column that carries both an exact size and a fill, in AndroidX's order.
  private func chainedSizeModifierDocument() -> Data {
    let output = Writer()
    output.header(width: 454, height: 400)
    output.u8(200).int(-2)
    output.u8(201).int(-3)
    output.u8(204).int(-4).int(0).int(1).int(4).float(0)
    output.u8(16).int(6).float(172)  // width(172.dp)
    output.u8(16).int(1).float(1)  // .fillMaxWidth()
    output.u8(67).int(6).float(64)  // height(64.dp)
    output.u8(67).int(1).float(1)  // .fillMaxHeight()
    output.u8(201).int(-5)
    for _ in 0..<4 { output.u8(214) }
    return output.data
  }

  /// A root holding one column with a graphics-layer modifier of float attributes. Each tag is the
  /// attribute id with data type 1 (float) in bits 10-11.
  private func graphicsLayerDocument(_ attributes: [(id: Int, value: Float)]) -> Data {
    let output = Writer()
    output.header(width: 200, height: 200)
    output.u8(200).int(-2)
    output.u8(201).int(-3)
    output.u8(204).int(-4).int(0).int(1).int(4).float(0)
    output.u8(NativeSwiftWireOpcode.modifierGraphicsLayer).int(attributes.count)
    for attribute in attributes {
      output.int(attribute.id | (1 << 10)).float(attribute.value)
    }
    output.u8(201).int(-5)
    for _ in 0..<4 { output.u8(214) }
    return output.data
  }

  /// A root holding one row with a horizontal scroll modifier, in AndroidX's order: the row, the
  /// container the scroll modifier opens, its 214, and only then the row's content.
  private func scrolledRowDocument() -> Data {
    let output = Writer()
    output.header(width: 200, height: 100)
    output.u8(200).int(-2)
    output.u8(203).int(-3).int(0).int(1).int(4).float(0)
    output.u8(226).int(1).float(40).float(240).float(0)
    output.u8(214)
    output.u8(201).int(-4)
    output.u8(202).int(-5).int(0).int(2).int(2)
    for _ in 0..<4 { output.u8(214) }
    return output.data
  }

  /// A document with values and no components: one float expression and the name it answers to.
  private func rootlessValuesDocument() -> Data {
    let output = Writer()
    output.header(width: 300, height: 200)
    output.u8(81).int(5).int(1).float(42)
    output.namedVariable(id: 5, type: 1, name: "answer")
    return output.data
  }

  private func particleDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    // ParticleDefine(id, count, variable count, [variable id, initial equation]).
    output.u8(161).int(9).int(1).int(1).int(70).int(1).float(2)
    // ParticleLoop(id, restart equation, variable equations): `value + 1`.
    output.u8(163).int(9).int(1).float(0).int(1).int(3)
      .int(Writer.nanReference(70)).float(1).int(Writer.floatOperator(1))
    output.u8(200).int(1).u8(214)
    return output.data
  }

  private func editableTextDocument() -> Data {
    let output = Writer()
    output.header(width: 360, height: 150)
    output.u8(138).int(50).int(Int(Int32(bitPattern: 0xff20_2124)))
    output.namedVariable(id: 50, type: 2, name: "accent")
    output.text(id: 40, "demo:EditableText")
    output.text(id: 60, "Hello from the document")
    output.text(id: 44, "The document sees:")
    output.u8(200).int(1)
    output.u8(201).int(2)
    output.u8(204).int(3).int(0).int(1).int(4).float(12)
    output.u8(55).int(0).int(0).int(0).int(0)
      .float(0.97).float(0.97).float(0.98).float(1).int(0)
    output.u8(58).float(16).float(16).float(16).float(16)
    output.u8(16).int(0).float(360)
    output.u8(67).int(0).float(150)
    output.u8(201).int(4)
    output.u8(93).int(5).int(0).int(40).int(3)
      .u16(1).u16(2).int(60)
      .u16(2).u16(4).int(60)
      .u16(3).u16(7).int(50)
    output.u8(16).int(0).float(328)
    output.u8(67).int(0).float(40)
    output.u8(214)
    output.textLayout(id: 6, textID: 44, color: 0xff5f_6368, size: 12)
    output.textLayout(id: 7, textID: 60, color: 0xff20_2124, size: 15)
    for _ in 0..<4 { output.u8(214) }
    return output.data
  }

  private func dynamicCustomFloatDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(81).int(70).int(1).float(0.4)
    output.text(id: 40, "demo:SwiftControls")
    output.u8(200).int(1)
    output.u8(201).int(2)
    output.u8(93).int(3).int(0).int(40).int(2)
      .u16(1).u16(1).int(Writer.nanReference(70))
      .u16(2).u16(3).int(Writer.nanReference(70))
    output.u8(214).u8(214).u8(214)
    return output.data
  }

  private func animatedFloatDocument(
    easingType: Int = 4, parameters: [Float] = [], includeAnimation: Bool = true
  ) -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(80).int(50).float(0)
    output.namedVariable(id: 50, type: 1, name: "target")
    let animationWordCount = includeAnimation ? parameters.count + 2 : 0
    let metadata = (parameters.count << 16) | easingType
    output.u8(81).int(51).int((animationWordCount << 16) | 1)
      .int(Writer.nanReference(50))
    if includeAnimation {
      output.float(1).int(metadata)
      for parameter in parameters {
        output.float(parameter)
      }
    }
    output.u8(200).int(1).u8(42).int(Writer.nanReference(51)).int(0).int(0).int(0).u8(214)
    return output.data
  }

  private func compactAnimationDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(81).int(51).int((1 << 16) | 1).float(1).float(0.25)
    output.u8(200).int(1).u8(42).int(Writer.nanReference(51)).int(0).int(0).int(0).u8(214)
    return output.data
  }

  private func springFloatDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(81).int(51).int((5 << 16) | 1)
      .float(1).float(0).float(40).float(8).float(0.001).int(0)
    output.u8(200).int(1).u8(42).int(Writer.nanReference(51)).int(0).int(0).int(0).u8(214)
    return output.data
  }

  private func unsupportedFloatAnimationDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(81).int(51).int((2 << 16) | 1)
      .float(1).float(1).int(15)
    output.u8(200).int(1).u8(42).int(Writer.nanReference(51)).int(0).int(0).int(0).u8(214)
    return output.data
  }

  private func concurrentStringDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.namedVariable(id: 55, type: 0, name: "message")
    output.text(id: 56, " suffix")
    output.u8(136).int(57).int(55).int(56)
    output.u8(200).int(1).u8(214)
    return output.data
  }
}

extension NativeSwiftNodeSnapshot {
  fileprivate var firstTextSnapshot: NativeSwiftTextSnapshot? {
    text ?? children.lazy.compactMap(\.firstTextSnapshot).first
  }

  fileprivate var firstText: String? {
    text?.value ?? children.lazy.compactMap(\.firstText).first
  }

  fileprivate var allText: [String] {
    text.map { [$0.value] } ?? children.flatMap(\.allText)
  }

  fileprivate var firstClickable: NativeSwiftNodeSnapshot? {
    isClickable ? self : children.lazy.compactMap(\.firstClickable).first
  }

  fileprivate var firstPathCommand: NativeSwiftDrawCommandSnapshot? {
    commands.first(where: { $0.kind == 18 })
      ?? children.lazy.compactMap(\.firstPathCommand).first
  }

  fileprivate var allCommandValues: [Float] {
    commands.flatMap(\.values) + children.flatMap(\.allCommandValues)
  }

  fileprivate var allCommandKinds: [Int] {
    commands.map(\.kind) + children.flatMap(\.allCommandKinds)
  }

  fileprivate var allColors: Set<UInt32> {
    var result = Set(commands.map(\.colorARGB))
    if let backgroundARGB { result.insert(backgroundARGB) }
    if let text { result.insert(text.colorARGB) }
    for child in children { result.formUnion(child.allColors) }
    return result
  }

  fileprivate var firstImage: NativeSwiftImageDrawSnapshot? {
    commands.lazy.compactMap(\.image).first ?? children.lazy.compactMap(\.firstImage).first
  }

  fileprivate var firstTextureImageID: Int? {
    commands.lazy.compactMap(\.textureImageID).first
      ?? children.lazy.compactMap(\.firstTextureImageID).first
  }

  fileprivate var firstTextureCommand: NativeSwiftDrawCommandSnapshot? {
    commands.first(where: { $0.textureImageID != nil })
      ?? children.lazy.compactMap(\.firstTextureCommand).first
  }

  fileprivate var allCommands: [NativeSwiftDrawCommandSnapshot] {
    commands + children.flatMap(\.allCommands)
  }
}

/// Collects failures from threads that carry no test context.
private final class FailureLog: @unchecked Sendable {
  private let lock = NSLock()
  private var recorded: [String] = []

  var messages: [String] {
    lock.lock()
    defer { lock.unlock() }
    return recorded
  }

  func append(_ message: String) {
    lock.lock()
    recorded.append(message)
    lock.unlock()
  }
}

private final class Writer {
  static func nanReference(_ id: Int) -> Int {
    Int(Int32(bitPattern: 0xff80_0000 | UInt32(id)))
  }

  /// A NaN-boxed float-expression operator word. `3` is multiply; see `NativeSwiftFloatExpression`.
  static func floatOperator(_ operation: Int) -> Int {
    Int(Int32(bitPattern: 0xff80_0000 | UInt32(0x0031_0000 + operation)))
  }

  private(set) var bytes: [UInt8] = []
  var data: Data { Data(bytes) }

  @discardableResult
  func u8(_ value: Int) -> Writer {
    bytes.append(UInt8(truncatingIfNeeded: value))
    return self
  }

  @discardableResult
  func u16(_ value: Int) -> Writer {
    bytes.append(UInt8(truncatingIfNeeded: value >> 8))
    bytes.append(UInt8(truncatingIfNeeded: value))
    return self
  }

  @discardableResult
  func int(_ value: Int) -> Writer {
    let raw = UInt32(bitPattern: Int32(value))
    bytes.append(UInt8(truncatingIfNeeded: raw >> 24))
    bytes.append(UInt8(truncatingIfNeeded: raw >> 16))
    bytes.append(UInt8(truncatingIfNeeded: raw >> 8))
    bytes.append(UInt8(truncatingIfNeeded: raw))
    return self
  }

  @discardableResult
  func long(_ value: Int) -> Writer {
    int(0).int(value)
  }

  @discardableResult
  func int64(_ value: Int64) -> Writer {
    int(Int(Int32(truncatingIfNeeded: value >> 32))).int(Int(Int32(truncatingIfNeeded: value)))
  }

  @discardableResult
  func float(_ value: Float) -> Writer {
    int(Int(Int32(bitPattern: value.bitPattern)))
  }

  func header(width: Int, height: Int) {
    u8(0).int(1).int(0).int(0).int(width).int(height).int(0).int(0)
  }

  func modernHeader(width: Int, height: Int, unrelatedKey: Int, unrelatedValue: Int) {
    u8(0).int(0x048c_0001).int(0).int(0).int(3)
    u16(5).u16(4).int(width)
    u16(unrelatedKey).u16(4).int(unrelatedValue)
    u16(6).u16(4).int(height)
  }

  func text(id: Int, _ value: String) {
    let encoded = Array(value.utf8)
    u8(102).int(id).int(encoded.count)
    bytes.append(contentsOf: encoded)
  }

  @discardableResult
  func namedVariable(id: Int, type: Int, name: String) -> Writer {
    let encoded = Array(name.utf8)
    u8(137).int(id).int(type).int(encoded.count)
    bytes.append(contentsOf: encoded)
    return self
  }

  func textLayout(
    id: Int, textID: Int, color: UInt32, size: Float, familyID: Int = -1
  ) {
    u8(208).int(id).int(0).int(textID).int(Int(Int32(bitPattern: color)))
      .float(size).int(0).float(400).int(familyID).int(1).int(1).int(1)
    u8(214)
  }
}
