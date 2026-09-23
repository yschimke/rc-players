import Foundation

@main
enum NativeSwiftCoreTests {
  static func main() throws {
    // A host surfaces a failed frame through `localizedDescription`, not `description`: only the
    // document-open path in NativeSession downcasts to NativeSwiftCoreError. Without
    // `LocalizedError` that goes through the NSError bridge and the user reads "The operation
    // couldn't be completed. (…NativeSwiftCoreError error 1.)" instead of the real reason.
    for sample in [
      NativeSwiftCoreError.malformed(offset: 12, reason: "sample"),
      NativeSwiftCoreError.unsupported(opcode: 81, offset: 4, reason: "sample"),
    ] {
      precondition(
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
    guard let chainedColumn = chained.root.children.first?.children.first else {
      preconditionFailure("the chained-size fixture decoded no child component")
    }
    precondition(
      chainedColumn.widthType == 6 && chainedColumn.widthValue == 172,
      "expected the outer width(172.dp) to win, got "
        + "\(chainedColumn.widthType)/\(chainedColumn.widthValue)")
    precondition(
      chainedColumn.heightType == 6 && chainedColumn.heightValue == 64,
      "expected the outer height(64.dp) to win, got "
        + "\(chainedColumn.heightType)/\(chainedColumn.heightValue)")

    // A scroll modifier opens a container of its own, and the opcode stream closes it with the same
    // 214 that closes every other container. Reading 226 as a plain modifier left that 214 to pop the
    // *component*, so the component's content was attached to its parent: a scrolled row's children
    // arrived as siblings of the row, laid out by nobody. `row_scroll_basic` is the gold that caught
    // it, and this fixture is that shape.
    let scrolled = try NativeSwiftDocumentSession.open(data: scrolledRowDocument()).snapshot()
    guard let scrolledRow = scrolled.root.children.first else {
      preconditionFailure("the scrolled-row fixture decoded no row")
    }
    precondition(
      scrolledRow.componentKind == "RowLayout",
      "expected the scrolled component to be a row, got \(scrolledRow.componentKind)")
    precondition(
      scrolledRow.scrollDirection == .horizontal && scrolledRow.scrollOffset == 40
        && scrolledRow.scrollMaximum == 240,
      "expected a horizontal scroll at 40/240, got "
        + "\(String(describing: scrolledRow.scrollDirection))/\(scrolledRow.scrollOffset)"
        + "/\(scrolledRow.scrollMaximum)")
    precondition(
      NativeSwiftScrollGesture.offset(afterDragging: 40, delta: -30, maximum: 60) == 60
        && NativeSwiftScrollGesture.offset(afterDragging: 40, delta: 60, maximum: 60) == 0,
      "scroll drag arithmetic did not clamp at both ends")
    guard let scrolledContent = scrolledRow.children.first, scrolledContent.children.count == 1 else {
      preconditionFailure("the scrolled row's content did not stay under the row")
    }
    precondition(
      scrolledContent.children.first?.componentID == -5,
      "the scrolled row's child landed at "
        + "\(String(describing: scrolledContent.children.first?.componentID))")
    // A *data-only* document declares values and nothing to draw, so it carries no root component at
    // all. Refusing it is right for a renderer and wrong for a conformance run, which still has to
    // answer the scalar probes those documents assert — so the mode is asked for explicitly and the
    // strict path stays the default. `expr_color_blending` and `expr_integer_bitwise_ops` are the
    // golds that need it.
    let rootless = rootlessValuesDocument()
    do {
      _ = try NativeSwiftDocumentSession.open(data: rootless)
      preconditionFailure("a document with no root component was accepted without the mode")
    } catch let error as NativeSwiftCoreError {
      precondition(error.description.contains("Missing root component"))
    }
    let dataOnly = try NativeSwiftDocumentSession.open(
      data: rootless, toleratingRootlessData: true)
    let dataOnlySnapshot = try dataOnly.snapshot()
    precondition(
      dataOnlySnapshot.root.children.isEmpty,
      "a data-only document's synthetic root grew children")
    let dataOnlyValues = try dataOnly.probeValues(timeSeconds: 0)
    precondition(
      dataOnly.namedVariableID("answer") == 5 && dataOnlyValues.floats[5] == 42,
      "a data-only document's own values did not resolve: "
        + "\(String(describing: dataOnlyValues.floats[5]))")
    precondition(dataOnly.setFloat(24, forID: 99), "a finite float slot update was refused")
    let updatedDataOnlyValues = try dataOnly.probeValues(timeSeconds: 0)
    precondition(
      updatedDataOnlyValues.floats[99] == 24,
      "a float slot update did not survive resolution")
    precondition(
      !dataOnly.setFloat(.nan, forID: 99), "a non-finite float slot update was accepted")

    // AndroidX data maps resolve a key text through a typed resource-id table. This is a rootless
    // conformance document, so it also proves that scalar-only captures do not need a draw root.
    let dataMap = Data(
      base64Encoded:
        "AASMAAEAAAABAAAAAAAAAAMABQAEAAABkAAGAAQAAAGQAA4ABAAAAgFmAAAACgAAAAl0aXRsZV9rZXlmAAAACwAAABJSZW1vdGVDb21wb3NlIFNEVUlQAAAADEIoAABmAAAADQAAAAdhZ2Vfa2V5kQAAABQAAAACAAAACXRpdGxlX2tleQAAAAALAAAAB2FnZV9rZXkCAAAADJoAAAAeAAAAFAAAAAqaAAAAHwAAABQAAAAN"
    )!
    let dataMapValues = try NativeSwiftDocumentSession.open(
      data: dataMap, toleratingRootlessData: true
    ).probeValues(timeSeconds: 0)
    precondition(
      dataMapValues.floats[31] == 42 && dataMapValues.texts[30] == "RemoteCompose SDUI",
      "data-map lookup did not expose its typed values: \(dataMapValues)")

    // Particle definitions used to be consumed and discarded, so every frame silently saw no
    // particle state. A retained session now initialises the system once and applies the loop's
    // equations on each logical frame.
    let particleSession = try NativeSwiftDocumentSession.open(data: particleDocument())
    let firstParticles = try particleSession.particleSnapshot(id: 9, timeSeconds: 0)
    let secondParticles = try particleSession.particleSnapshot(id: 9, timeSeconds: 1)
    precondition(
      firstParticles?.variableIDs == [70] && firstParticles?.particles == [[3]],
      "particle definition did not initialise and simulate its first frame: "
        + "\(String(describing: firstParticles))")
    precondition(
      secondParticles?.particles == [[4]],
      "particle loop did not retain state between frames: \(String(describing: secondParticles))")

    // AndroidX permits a compact FloatAnimation descriptor with its duration alone; it implies
    // CUBIC_STANDARD. Several Wear catalog components use it for transition values.
    let compactAnimation = try NativeSwiftDocumentSession.open(data: compactAnimationDocument())
    _ = try compactAnimation.snapshot()

    let wire = editableTextDocument()
    if CommandLine.arguments.count == 2 {
      let kotlinFixture = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
      precondition(kotlinFixture == wire, "Swift test fixture differs from the Kotlin encoder")
    }
    if CommandLine.arguments.count >= 3 {
      let titleData = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2]))
      let titleSession = try NativeSwiftDocumentSession.open(data: titleData)
      let title = try titleSession.snapshot()
      precondition(title.width == 640 && title.height == 480)
      precondition(title.density == 2 && title.densityBehavior == 2)
      precondition(title.root.firstText == "Morning run")
      precondition(title.root.allText.contains("5.2 km · 28 min"))
      guard let backgroundPath = title.root.firstPathCommand else {
        preconditionFailure("title card has no native background path")
      }
      precondition(backgroundPath.usesComponentGeometry)
      let backgroundY = backgroundPath.path.flatMap { element in
        stride(from: 1, to: element.values.count - (element.kind == 13 ? 1 : 0), by: 2).map {
          element.values[$0]
        }
      }
      precondition(backgroundY.max() ?? 0 > 100, "title background did not use measured content")
      guard let button = title.root.firstClickable else {
        preconditionFailure("title card has no native clickable component")
      }
      let events = try titleSession.click(componentID: button.componentID, timeSeconds: 2)
      precondition(events == [.namedAction(name: "catalogAction", value: .float(1))])
    }
    if CommandLine.arguments.count == 4 {
      let progressData = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[3]))
      let progressSession = try NativeSwiftDocumentSession.open(data: progressData)
      let first = try progressSession.snapshot(timeSeconds: 0.25)
      let second = try progressSession.snapshot(timeSeconds: 0.75)
      precondition(first.needsContinuousFrames)
      precondition(first.root.allCommandValues != second.root.allCommandValues)
      precondition(first.root.allCommandValues.count >= 15)
    }
    if CommandLine.arguments.count == 6 {
      for (path, size) in zip(
        CommandLine.arguments[4...5], [(384, 384), (454, 400)])
      {
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        let snapshot = try NativeSwiftDocumentSession.open(data: data).snapshot()
        precondition(snapshot.width == size.0 && snapshot.height == size.1)
        precondition(snapshot.root.allCommandKinds.contains(15))
      }
    }
    if CommandLine.arguments.count == 8 {
      // The deferred-density capture — the one fixture here that computes its text size from
      // ID_DENSITY/ID_FONT_SIZE instead of folding a capture device's density in.
      //
      // This used to assert a refusal: the size arrives NaN-boxed and `literalFloat` rejected it
      // outright, so a `RemoteDensity.Host` capture was declined rather than drawn. The core now
      // carries the word to resolution time, so the same fixture renders, and its size is whatever
      // the host supplied — the inversion is the point, so it is written out rather than deleted.
      let hostData = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[7]))
      let hostSession = try NativeSwiftDocumentSession.open(data: hostData)
      // `([33] 14.0 / [27] / 15.0 *)` cancels the density back out, leaving the font scale times
      // the 15sp the capture asked for: 15 unscaled, 22.5 at fontScale 1.5.
      guard let unscaled = try hostSession.snapshot().root.firstTextSnapshot else {
        preconditionFailure("the deferred-density fixture rendered no text")
      }
      precondition(
        abs(unscaled.size - 15) < 0.01, "expected a 15pt deferred size, got \(unscaled.size)")
      hostSession.setHostDensity(2.2625, fontScale: 1.5)
      guard let scaled = try hostSession.snapshot().root.firstTextSnapshot else {
        preconditionFailure("the deferred-density fixture rendered no text after rescaling")
      }
      precondition(
        abs(scaled.size - 22.5) < 0.01, "expected a 22.5pt deferred size, got \(scaled.size)")

      // The density the document resolves against is the player's own, and the host sets it. Kept
      // next to the assertions above because these are the two halves of the same contract: what
      // the player supplies, and what it refuses to be talked into.
      let session = try NativeSwiftDocumentSession.open(data: wire)
      session.setHostDensity(2.2625, fontScale: 1.3)
      session.setHostDensity(0, fontScale: -1)  // Ignored: both reach a document as a divisor.
      _ = try session.snapshot()
    }
    if CommandLine.arguments.count >= 7 {
      let imageData = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[6]))
      let session = try NativeSwiftDocumentSession.open(data: imageData)
      let snapshot = try session.snapshot()
      precondition(snapshot.width == 454 && snapshot.height == 200)
      precondition(snapshot.density == 2 && snapshot.densityBehavior == 0)
      precondition(snapshot.images.count == 1)
      precondition(snapshot.images[0].width == 8 && snapshot.images[0].height == 8)
      let imageID = snapshot.root.firstImage?.imageID ?? snapshot.root.firstTextureImageID
      precondition(imageID == snapshot.images[0].id, "image button has no native bitmap draw")
      precondition(snapshot.root.firstTextureCommand?.usesComponentGeometry == true)
      // The texture's shader matrix maps the 8x8 bitmap over the button — a 21.5x scale and a
      // -56 vertical offset, both computed by a MATRIX_EXPRESSION. The renderer used to drop it and
      // tile the bitmap at its natural size.
      let matrix = snapshot.root.firstTextureCommand?.shaderMatrix
      precondition(matrix?.count == 9, "texture shader matrix was \(String(describing: matrix))")
      precondition(
        abs((matrix?[0] ?? 0) - 21.5) < 0.01 && abs((matrix?[4] ?? 0) - 21.5) < 0.01,
        "texture matrix scale was \(String(describing: matrix))")
      precondition(
        abs((matrix?[5] ?? 0) + 56) < 0.01,
        "texture matrix translateY was \(String(describing: matrix))")
      // Setting the scrim's gradient replaces the texture shader. The texture id used to survive
      // the gradient, and the renderer's texture branch then drew the image a second time.
      let scrim = snapshot.root.allCommands.first { $0.gradient != nil }
      precondition(
        scrim?.textureImageID == nil, "the gradient scrim still carried the texture shader")
      guard let button = snapshot.root.firstClickable else {
        preconditionFailure("image button has no clickable component")
      }
      let events = try session.click(componentID: button.componentID, timeSeconds: 0)
      precondition(events == [])
    }

    if CommandLine.arguments.count >= 9 {
      let tileData = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[8]))
      let tileSession = try NativeSwiftDocumentSession.open(data: tileData)
      let activeColors = try tileSession.snapshot().root.allColors
      precondition(tileSession.namedVariableID("light.kitchen.is_on") == 51)
      precondition(activeColors.contains(0xffff_be3e))
      precondition(activeColors.contains(0x33ff_be3e))
      precondition(tileSession.setInteger(0, for: "light.kitchen.is_on"))
      let inactiveColors = try tileSession.snapshot().root.allColors
      precondition(inactiveColors.contains(0xffb0_b0b0))
      precondition(inactiveColors.contains(0x33b0_b0b0))
    }
    let session = try NativeSwiftDocumentSession.open(data: wire)
    let initial = try session.snapshot()
    precondition(initial.width == 360 && initial.height == 150)
    precondition(initial.density == 1 && initial.densityBehavior == 0)
    precondition(initial.root.children.first?.children.first?.kind == .column)

    let column = initial.root.children[0].children[0]
    precondition(column.children.count == 1)
    let content = column.children[0]
    precondition(content.children.count == 3)
    precondition(content.children[0].custom?.config == "demo:EditableText")
    precondition(content.children[0].custom?.properties[0].textValue == "Hello from the document")
    precondition(content.children[2].text?.value == "Hello from the document")

    precondition(session.setColor(0xff12_3456, for: "accent"))
    let accepted = try session.returnCustomText(
      "Edited in Swift", componentID: 5, propertyID: 2)
    precondition(accepted)
    let updated = try session.snapshot()
    let updatedContent = updated.root.children[0].children[0].children[0]
    precondition(updatedContent.children[0].custom?.properties[0].textValue == "Edited in Swift")
    precondition(
      updatedContent.children[0].custom?.properties[2].integerValue
        == Int(Int32(bitPattern: 0xff12_3456)))
    precondition(updatedContent.children[2].text?.value == "Edited in Swift")
    let rejected = try session.returnCustomText("Ignored", componentID: 5, propertyID: 99)
    precondition(!rejected)

    let floatSession = try NativeSwiftDocumentSession.open(data: dynamicCustomFloatDocument())
    let initialFloat = try floatSession.snapshot().root.children[0].children[0]
    precondition(initialFloat.custom?.properties[0].floatValue == 0.4)
    let acceptedFloat = try floatSession.returnCustomFloat(0.85, componentID: 3, propertyID: 2)
    precondition(acceptedFloat, "declared float return should be accepted")
    let returnedFloat = try floatSession.snapshot().root.children[0].children[0]
    precondition(abs((returnedFloat.custom?.properties[0].floatValue ?? 0) - 0.85) < 0.001)
    let rejectedFloat = try floatSession.returnCustomFloat(0.5, componentID: 3, propertyID: 99)
    precondition(!rejectedFloat, "an undeclared return channel should be rejected")

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
    precondition(
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
      preconditionFailure("a text size resolving to zero was accepted")
    } catch let error as NativeSwiftCoreError {
      precondition(
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
    precondition(
      abs((fillScale ?? 0) - 26.0 / 24.0) < 0.001,
      "a fill inside a fixed box measured the document, got scale \(String(describing: fillScale))")

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
    precondition(
      unset.root.commands[0].values[0] == 0,
      "an unsupplied calendar field resolved to \(unset.root.commands[0].values[0])")
    // 2026-09-19T12:34:56.789Z.
    let wallClock = NativeSwiftWallClock(epochMillis: 1_789_821_296_789, offsetSeconds: 0)
    let fields = try calendarSession.snapshot(wallClock: wallClock).root.commands
      .map { $0.values[0] }
    precondition(
      fields == [2026, 9, 19, 6, 262, 2096, 754, 12, 0],
      "calendar fields resolved to \(fields)")
    // A zone offset moves the local fields but not the instant.
    let shifted = try calendarSession.snapshot(
      wallClock: NativeSwiftWallClock(epochMillis: 1_789_821_296_789, offsetSeconds: 3600)
    ).root.commands.map { $0.values[0] }
    precondition(
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
    precondition(claimedYear == 1999, "a claimed calendar id resolved to \(claimedYear)")

    // Pre-epoch instants: `-1 ms` is the last millisecond of 1969, and the fields have to describe
    // that rather than 1970-01-01T00:00:00.999. A week earlier is a Sunday, ISO 7, which a negative
    // remainder would put at 0.
    let preEpoch = try calendarSession.snapshot(
      wallClock: NativeSwiftWallClock(epochMillis: -1)
    ).root.commands.map { $0.values[0] }
    precondition(
      preEpoch == [1969, 12, 31, 3, 365, 3599, 1439, 23, 0],
      "pre-epoch calendar fields resolved to \(preEpoch)")
    let sunday = try calendarSession.snapshot(
      wallClock: NativeSwiftWallClock(epochMillis: -345_600_000)
    ).root.commands.map { $0.values[0] }
    precondition(
      sunday[3] == 7 && sunday[4] == 362,
      "a pre-epoch weekday resolved to \(sunday[3]) / day-of-year \(sunday[4])")

    // `ATTRIBUTE_TIME` reads a calendar field or an interval from a `LongConstant` instant, or
    // from the wall clock when it names none. The operations around it used to be refused, and the
    // whole document with them: root content behaviour, boolean and long constants, a debug
    // message and the sound family now decode, and the ones that are host side effects do nothing.
    // 1_700_000_000_000 is 2023-11-14T22:13:20Z, a Tuesday; the second instant is 1000 s earlier.
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
      precondition(
        timedValues.floats[field.output] == field.expected,
        "time attribute type \(field.type) resolved to "
          + "\(String(describing: timedValues.floats[field.output])), not \(field.expected)")
    }
    precondition(
      timedValues.floats[28] == -10,
      "a from-now interval resolved to \(String(describing: timedValues.floats[28]))")
    // Without a wall clock there is no "now" to measure from, and the slot stays unwritten rather
    // than measuring from the 1970 epoch; fields of a stated instant still resolve.
    let unclocked = try timedSession.probeValues(timeSeconds: 0)
    precondition(
      unclocked.floats[28] == nil && unclocked.floats[24] == 2023,
      "an unclocked time attribute resolved to \(String(describing: unclocked.floats[28]))")
    let timedSnapshot = try timedSession.snapshot()
    precondition(
      !timedSnapshot.needsWallClockRefresh,
      "time attributes on a stated instant asked for a wall-clock refresh")
    let nowAttribute = Writer()
    nowAttribute.header(width: 100, height: 100)
    nowAttribute.u8(172).int(20).int(99).u16(6).u16(0)
    let nowSnapshot = try NativeSwiftDocumentSession.open(
      data: nowAttribute.data, toleratingRootlessData: true
    ).snapshot()
    precondition(
      nowSnapshot.needsWallClockRefresh,
      "a time attribute read from now did not ask for a wall-clock refresh")

    // Semantics declared outside any component describe the document, and attach to its root
    // rather than refusing the whole document for having no component to modify.
    let described = Writer()
    described.header(width: 100, height: 100)
    described.text(id: 1, "Label")
    described.u8(103).int(1)
    described.u8(250).int(1).u8(5).int(1).int(0).u8(1).u8(1).u8(1)
    let describedSnapshot = try NativeSwiftDocumentSession.open(
      data: described.data, toleratingRootlessData: true
    ).snapshot()
    precondition(
      describedSnapshot.root.accessibility?.role == 5
        && describedSnapshot.accessibilityRecords.count == 1,
      "document-level semantics did not reach the root: "
        + "\(String(describing: describedSnapshot.root.accessibility))")

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
    precondition(
      textyValues.integers[40] == 8 && textyValues.floats[40] == 8,
      "an id lookup resolved to \(String(describing: textyValues.integers[40]))")
    precondition(
      textyValues.integers[41] == nil, "an out-of-range id lookup wrote its slot")
    precondition(
      textyValues.floats[42] == 19 && textyValues.floats[43] == 19,
      "text lengths resolved to \(String(describing: textyValues.floats[42])), "
        + "\(String(describing: textyValues.floats[43]))")
    precondition(
      textyValues.texts[44] == "Remote" && textyValues.texts[45] == "RemoteCompose",
      "subtexts resolved to \(String(describing: textyValues.texts[44])), "
        + "\(String(describing: textyValues.texts[45]))")
    precondition(textyValues.floats[46] == nil, "a text measurement invented a value")

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
    precondition(
      styledTexts.count == 2 && styledTexts[0].size == 20 && styledTexts[0].weight == 700
        && styledTexts[1].overflow == 1,
      "styled text resolved to \(styledTexts.map { ($0.size, $0.weight, $0.overflow) })")

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
    precondition(
      waiting.right.isEmpty && waiting.wake == 0.25,
      "a waiting impulse drew \(waiting.right) and woke after \(String(describing: waiting.wake))")
    let first = try impulseFrame(0.6)
    let again = try impulseFrame(0.6)
    let later = try impulseFrame(1.0)
    let after = try impulseFrame(2.0)
    precondition(
      first.right == [10] && again.right == [10] && first.wake == 0,
      "an impulse's first frame drew \(first.right), then \(again.right) on re-resolution")
    precondition(later.right == [20], "an impulse's process body drew \(later.right)")
    precondition(
      after.right.isEmpty && after.wake == 0.25,
      "a closed impulse drew \(after.right), woke after \(String(describing: after.wake))")
    let impulseRecord = try impulseSession.snapshot(timeSeconds: 0).impulses
    precondition(
      impulseRecord == [NativeSwiftImpulseSnapshot(duration: 1, startAt: 0.5)],
      "impulse records resolved to \(impulseRecord)")
    // Only drawing is gated; state inside an impulse would run outside its window, so it refuses.
    let stateful = Writer()
    stateful.header(width: 100, height: 100)
    stateful.u8(164).float(1).float(0)
    stateful.u8(80).int(40).float(1)
    stateful.u8(214)
    do {
      _ = try NativeSwiftDocumentSession.open(data: stateful.data)
      preconditionFailure("state inside an impulse was accepted")
    } catch NativeSwiftCoreError.unsupported(let opcode, _, _) {
      precondition(opcode == 80, "the impulse refused opcode \(opcode)")
    }

    // EPOCH_SECOND is whole seconds since the epoch, floored, as an integer and as a float.
    let epoch = try NativeSwiftDocumentSession.open(
      data: rootlessValuesDocument(), toleratingRootlessData: true
    ).probeValues(timeSeconds: 0, wallClock: NativeSwiftWallClock(epochMillis: 1_789_050_600_999))
    precondition(
      epoch.integers[32] == 1_789_050_600 && epoch.floats[32] == Float(1_789_050_600),
      "EPOCH_SECOND resolved to \(String(describing: epoch.integers[32]))")

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
    precondition(
      loopedCommands.filter { $0.kind == 10 }.map { $0.values[0] } == [0, 1, 2],
      "a loop drew at \(loopedCommands.map { ($0.kind, $0.values) })")
    let loopIndex = try loopedSession.probeValues(timeSeconds: 0).floats[70]
    precondition(loopIndex == 2, "a loop's index ended on \(String(describing: loopIndex))")
    precondition(
      loopedCommands.contains { $0.kind == 18 }, "a created and appended path did not draw")

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
    precondition(
      unthemed == 0xFF11_1111 && dark == 0xFF22_2222 && light == 0xFF11_1111,
      "a colour theme resolved to \(String(describing: unthemed)), \(String(describing: dark)), "
        + "\(String(describing: light))")

    // A document that reads a discrete wall-clock field asks a host to re-resolve at least once a
    // second; one that only reads the animation clock does not.
    let refreshed = try calendarSession.snapshot(wallClock: wallClock)
    precondition(
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
    precondition(
      !animatedSnapshot.needsWallClockRefresh,
      "an animation-only document asked for a wall-clock refresh")
    precondition(
      animatedSnapshot.needsContinuousFrames,
      "an animation-only document did not ask for continuous frames")

    // A clock display that converts a system variable straight to text names it in the
    // text-from-float operation, not in an expression or a draw command. That store has to be
    // scanned too, or the display freezes after the first frame.
    let clockText = Writer()
    clockText.header(width: 100, height: 100)
    clockText.u8(135).int(40).int(Writer.nanReference(2)).int(0).int(0)
    clockText.u8(200).int(1).u8(214).u8(214)
    let clockTextSnapshot = try NativeSwiftDocumentSession.open(data: clockText.data).snapshot()
    precondition(
      clockTextSnapshot.needsWallClockRefresh,
      "a clock-to-text conversion did not ask for a wall-clock refresh")
    let plainText = Writer()
    plainText.header(width: 100, height: 100)
    plainText.u8(135).int(40).float(1.5).int(0).int(0)
    plainText.u8(200).int(1).u8(214).u8(214)
    let plainTextSnapshot = try NativeSwiftDocumentSession.open(data: plainText.data).snapshot()
    precondition(
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
    precondition(
      !staticColorSnapshot.needsContinuousFrames && !staticColorSnapshot.needsWallClockRefresh,
      "a static colour expression asked for frames: continuous="
        + "\(staticColorSnapshot.needsContinuousFrames) wallClock="
        + "\(staticColorSnapshot.needsWallClockRefresh)")
    // The tween of the same mode *is* a float word, so a clock there is still found.
    let tweenedColor = Writer()
    tweenedColor.header(width: 100, height: 100)
    tweenedColor.u8(134).int(40).int(0)
      .int(Int(Int32(bitPattern: 0xff00_0000)))
      .int(Int(Int32(bitPattern: 0xff00_00ff)))
      .int(Writer.nanReference(2))
    tweenedColor.u8(200).int(1).u8(214).u8(214)
    let tweenedSnapshot = try NativeSwiftDocumentSession.open(data: tweenedColor.data).snapshot()
    precondition(
      tweenedSnapshot.needsWallClockRefresh,
      "a colour tween reading the clock did not ask for a refresh")
    // Modes 4...6 build a colour from float channels, so all three are words.
    let channelColor = Writer()
    channelColor.header(width: 100, height: 100)
    channelColor.u8(134).int(40).int(4).int(Writer.nanReference(2)).int(0).int(0)
    channelColor.u8(200).int(1).u8(214).u8(214)
    let channelSnapshot = try NativeSwiftDocumentSession.open(data: channelColor.data).snapshot()
    precondition(
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
    guard let stateNode = stateSnapshot.root.children.first else {
      preconditionFailure("the state-layout fixture decoded no state layout")
    }
    precondition(
      stateNode.componentKind == "StateLayout",
      "a state layout reported kind \(stateNode.componentKind)")
    precondition(stateNode.stateIndex == 0, "state index resolved to \(String(describing: stateNode.stateIndex))")
    // The container takes the active child's size whatever the document asks for: both its fill
    // width and fixed 100-point height are dropped.
    precondition(
      stateNode.widthType == 2 && stateNode.heightType == 2,
      "a dimensioned state layout kept its own size: \(stateNode.widthType)/\(stateNode.heightType)")
    precondition(
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
    precondition(
      secondSnapshot.root.children.first?.children.map(\.visibility) == [0, 1],
      "the second state branch was not the visible one")
    // The reported index counts the *branches*, not the wrapper they sit in, so it agrees with the
    // branch that is actually visible.
    precondition(
      secondSnapshot.root.children.first?.stateIndex == 1,
      "the second branch reported index "
        + "\(String(describing: secondSnapshot.root.children.first?.stateIndex))")

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
    guard let flowNode = flowSnapshot.root.children.first else {
      preconditionFailure("the flow fixture decoded no flow container")
    }
    precondition(
      flowNode.componentKind == "FlowLayout",
      "a flow container reported kind \(flowNode.componentKind)")
    precondition(
      flowNode.flowMaximumItems == 2 && flowNode.flowMaximumLines == 3,
      "flow bounds resolved to \(String(describing: flowNode.flowMaximumItems)) / "
        + "\(String(describing: flowNode.flowMaximumLines))")

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
    precondition(
      segmented.lines == [[0, 1], [2]],
      "weighted flow segmentation produced \(segmented.lines)")
    let withoutMinimum = NativeSwiftFlow.segment(
      [NativeSwiftFlow.Child(measuredWidth: 0, weight: 1), weightedFlow[1], weightedFlow[2]],
      available: 300, spacing: 0)
    precondition(
      withoutMinimum.lines == [[0, 1, 2]],
      "an unconstrained weighted child should not force a wrap: \(withoutMinimum.lines)")
    // The caps still apply: items per line, and lines before the rest are discarded.
    let capped = NativeSwiftFlow.segment(
      [NativeSwiftFlow.Child(measuredWidth: 10), NativeSwiftFlow.Child(measuredWidth: 10),
       NativeSwiftFlow.Child(measuredWidth: 10)],
      available: 1000, spacing: 0, maximumItems: 2, maximumLines: 1)
    precondition(
      capped.lines == [[0, 1]] && capped.discarded == [2],
      "flow caps produced \(capped.lines) / \(capped.discarded)")
    // Once the line cap is reached, everything from that child on is discarded — a later, smaller
    // child must not land on the still-open line and come out ahead of the one that was dropped.
    let cappedSuffix = NativeSwiftFlow.segment(
      [NativeSwiftFlow.Child(measuredWidth: 80),
       NativeSwiftFlow.Child(measuredWidth: 0, weight: 1, minimumWidth: 30),
       NativeSwiftFlow.Child(measuredWidth: 20)],
      available: 100, spacing: 0, maximumLines: 1)
    precondition(
      cappedSuffix.lines == [[0]] && cappedSuffix.discarded == [1, 2],
      "a capped suffix produced \(cappedSuffix.lines) / \(cappedSuffix.discarded)")
    // A GONE child is in neither the line, its spacing, nor its item count.
    let goneFlow = NativeSwiftFlow.segment(
      [NativeSwiftFlow.Child(measuredWidth: 100, isGone: true),
       NativeSwiftFlow.Child(measuredWidth: 100),
       NativeSwiftFlow.Child(measuredWidth: 100)],
      available: 100, spacing: 10, maximumItems: 2)
    precondition(
      goneFlow.lines == [[1], [2]],
      "a GONE child consumed a slot or a gap: \(goneFlow.lines)")

    let modern = Writer()
    modern.modernHeader(width: 100, height: 50, unrelatedKey: 69, unrelatedValue: 999)
    modern.u8(200).int(1).u8(214).u8(214)
    let modernSnapshot = try NativeSwiftDocumentSession.open(data: modern.data).snapshot()
    precondition(modernSnapshot.width == 100 && modernSnapshot.height == 50)

    let googleFont = Writer()
    googleFont.header(width: 100, height: 50)
    googleFont.text(id: 20, "Downloadable")
    googleFont.text(id: 21, "google:Orbitron")
    googleFont.u8(200).int(1).u8(201).int(2)
    googleFont.textLayout(
      id: 3, textID: 20, color: 0xff00_0000, size: 16, familyID: 21)
    googleFont.u8(214).u8(214)
    let googleSnapshot = try NativeSwiftDocumentSession.open(data: googleFont.data).snapshot()
    precondition(googleSnapshot.root.firstTextSnapshot?.familyName == "google:Orbitron")

    let canvasOperations = Writer()
    canvasOperations.header(width: 100, height: 100)
    canvasOperations.u8(200).int(1)
    canvasOperations.u8(201).int(2)
    canvasOperations.u8(205).int(3).int(-1)
    canvasOperations.u8(173).u8(130).u8(131).u8(214)
    canvasOperations.u8(214).u8(214).u8(214)
    let canvasSnapshot = try NativeSwiftDocumentSession.open(data: canvasOperations.data).snapshot()
    precondition(canvasSnapshot.root.children[0].children[0].commands.count == 2)

    // AndroidX LOOM streams are also allowed to paint directly into the document canvas before
    // any structural definition.  That is a real drawable document, unlike a rootless data-only
    // stream, and must therefore gain an implicit canvas root at the first draw operation.
    let standaloneCanvas = Writer()
    standaloneCanvas.header(width: 100, height: 100)
    standaloneCanvas.u8(42).float(0).float(0).float(40).float(30).u8(214)
    let standaloneCanvasSnapshot = try NativeSwiftDocumentSession.open(data: standaloneCanvas.data)
      .snapshot()
    precondition(
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
    precondition(
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
    precondition(
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
    precondition(
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
    precondition(
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
    precondition(
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
    precondition(pathCommand.kind == 18 && pathCommand.path.count == 2)

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
    precondition(drawingCommands.map(\.kind) == [6, 2, 3, 5, 10, 12, 13, 14, 16, 11, 7])
    precondition(drawingCommands.last?.path.count == 2)

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
    precondition(
      filterCommands.map(\.filterQuality) == [nil, 3, 0, 1, 1],
      "paint filter quality resolved to \(filterCommands.map { String(describing: $0.filterQuality) })")

    // A collapsible container keeps the children that fit and drops the rest, in priority order.
    // The decision is a pure function so both renderers and this test share it.
    func kept(_ sizes: [Float], available: Float, spacing: Float = 0) -> [Bool] {
      NativeSwiftCollapsible.keptChildren(
        sizes.map { NativeSwiftCollapsible.Child(mainSize: $0) },
        available: available, spacing: spacing)
    }
    precondition(
      kept([70, 70, 70], available: 250, spacing: 20) == [true, true, true],
      "a container with room for every child dropped one")
    precondition(
      kept([70, 70, 70], available: 180, spacing: 20) == [true, true, false],
      "the child that did not fit was not the one dropped")
    precondition(
      kept([70, 70, 70], available: 89, spacing: 20) == [true, false, false],
      "overflow did not stop at the first child that did not fit")
    // A priority sorts a child ahead of another: the reference keeps absent priorities first, then
    // the highest value, and drops from there.
    let prioritised = [
      NativeSwiftCollapsible.Child(mainSize: 100, priority: 1),
      NativeSwiftCollapsible.Child(mainSize: 100, priority: 5),
      NativeSwiftCollapsible.Child(mainSize: 100),
    ]
    precondition(
      NativeSwiftCollapsible.keptChildren(prioritised, available: 200, spacing: 0)
        == [false, true, true],
      "priority order did not decide which child was dropped")
    // A weighted child never counts against the available space, so it is never the reason another
    // child is dropped.
    let weighted = [
      NativeSwiftCollapsible.Child(mainSize: 100, weight: 1),
      NativeSwiftCollapsible.Child(mainSize: 200),
    ]
    precondition(
      NativeSwiftCollapsible.keptChildren(weighted, available: 150, spacing: 0)
        == [true, false],
      "a weighted child consumed space in the fit test")
    // A child the document marked GONE is never kept and never consumes space.
    let gone = [
      NativeSwiftCollapsible.Child(mainSize: 100, isGone: true),
      NativeSwiftCollapsible.Child(mainSize: 100),
    ]
    precondition(
      NativeSwiftCollapsible.keptChildren(gone, available: 100, spacing: 0) == [false, true],
      "a GONE child was kept or consumed space")
    // A fill child has no natural size, so it is not measured unbounded — an unbounded fill
    // resolves to infinity and the fit test then drops it from any container.
    precondition(
      !NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 1)
        && !NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 7)
        && !NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 8),
      "a fill dimension was measured unbounded")
    precondition(
      NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 2)
        && NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 6)
        && NativeSwiftCollapsible.measuresUnbounded(mainAxisType: 3),
      "a fixed, wrapping or weighted dimension was not measured unbounded")

    // Unbounded space keeps everything the document did not hide.
    precondition(
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
    guard let column = collapsibleSnapshot.root.children.first else {
      preconditionFailure("the collapsible fixture decoded no column")
    }
    precondition(
      column.componentKind == "CollapsibleColumnLayout",
      "a collapsible column reported kind \(column.componentKind)")
    precondition(column.isCollapsible, "a collapsible column did not report itself as one")
    precondition(column.spacing == 20, "collapsible spacing resolved to \(column.spacing)")
    guard let child = column.children.first else {
      preconditionFailure("the collapsible fixture decoded no child")
    }
    precondition(
      child.collapsiblePriority == 2 && child.collapsiblePriorityOrientation == 1,
      "the priority modifier resolved to \(String(describing: child.collapsiblePriority)) / "
        + "\(String(describing: child.collapsiblePriorityOrientation))")

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
    precondition(semanticButton.accessibility?.contentDescription == "Disabled action")
    precondition(semanticButton.accessibility?.stateDescription == "Unavailable")
    precondition(semanticButton.accessibility?.isEnabled == false)
    let disabledEvents = try semanticsSession.click(componentID: 3, timeSeconds: 0)
    precondition(disabledEvents == nil)

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
    precondition(
      gestureNode.supportedGestures == [
        .longPress, .doubleTap, .touchDown, .touchUp, .touchCancel,
      ])
    for gesture in gestureNode.supportedGestures {
      let events = try gestureSession.gesture(gesture, componentID: 3, timeSeconds: 0)
      precondition(events == [.namedAction(name: "gesture", value: .none)])
    }
    let missingTap = try gestureSession.click(componentID: 3, timeSeconds: 0)
    precondition(missingTap == nil)

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
    precondition(integerEvents == [.namedAction(name: "count", value: .integer(2))])

    let malformedPaint = Writer()
    malformedPaint.header(width: 100, height: 100)
    malformedPaint.u8(200).int(1)
    malformedPaint.u8(40).int(1).int(Int(Int32(bitPattern: 0xffff_0017)))
    malformedPaint.u8(214)
    do {
      _ = try NativeSwiftDocumentSession.open(data: malformedPaint.data)
      preconditionFailure("negative variable paint count was accepted")
    } catch let error as NativeSwiftCoreError {
      precondition(!error.isUnsupported)
    }

    do {
      _ = try NativeSwiftDocumentSession.open(data: wire.dropLast())
      preconditionFailure("truncated input was accepted")
    } catch let error as NativeSwiftCoreError {
      precondition(!error.isUnsupported)
    }

    let unsupported = Writer()
    unsupported.header(width: 1, height: 1)
    unsupported.u8(255)
    do {
      _ = try NativeSwiftDocumentSession.open(data: unsupported.data)
      preconditionFailure("unsupported opcode was accepted")
    } catch let error as NativeSwiftCoreError {
      precondition(error.isUnsupported)
    }

    let tweenSession = try NativeSwiftDocumentSession.open(data: animatedFloatDocument())
    let firstTween = try tweenSession.snapshot(timeSeconds: 0)
    precondition(firstTween.root.commands[0].values[0] == 0)
    precondition(!firstTween.needsContinuousFrames, "an unchanged initial target should be idle")
    precondition(tweenSession.setFloat(10, for: "target"))
    let tweenStart = try tweenSession.snapshot(timeSeconds: 0)
    let tweenMiddle = try tweenSession.snapshot(timeSeconds: 0.5)
    let tweenEnd = try tweenSession.snapshot(timeSeconds: 1)
    precondition(tweenStart.root.commands[0].values[0] == 0)
    precondition(abs(tweenMiddle.root.commands[0].values[0] - 5) < 0.01)
    precondition(tweenEnd.root.commands[0].values[0] == 10)
    precondition(tweenStart.needsContinuousFrames && tweenMiddle.needsContinuousFrames)
    precondition(!tweenEnd.needsContinuousFrames, "a completed tween kept requesting frames")

    let staticSession = try NativeSwiftDocumentSession.open(
      data: animatedFloatDocument(includeAnimation: false))
    _ = try staticSession.snapshot(timeSeconds: 0)
    precondition(staticSession.setFloat(10, for: "target"))
    let staticUpdate = try staticSession.snapshot(timeSeconds: 0.5)
    precondition(staticUpdate.root.commands[0].values[0] == 10)
    precondition(!staticUpdate.needsContinuousFrames)

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
      precondition(session.setFloat(10, for: "target"))
      _ = try session.snapshot(timeSeconds: 0)
      for (time, expected) in zip([0.25, 0.5, 0.75], easing.values) {
        let actual = try session.snapshot(timeSeconds: time).root.commands[0].values[0]
        precondition(
          abs(actual - expected) < 0.01,
          "easing \(easing.type) at \(time)s: expected \(expected), got \(actual)")
      }
      let endpoint = try session.snapshot(timeSeconds: 1).root.commands[0].values[0]
      precondition(abs(endpoint - 10) < 0.01, "easing \(easing.type) did not reach its target")
    }

    let springSession = try NativeSwiftDocumentSession.open(data: springFloatDocument())
    let springStart = try springSession.snapshot(timeSeconds: 0)
    let springMoving = try springSession.snapshot(timeSeconds: 0.016)
    var springSettled = springMoving
    for frame in 2...600 {
      springSettled = try springSession.snapshot(timeSeconds: Double(frame) * 0.016)
    }
    precondition(springStart.root.commands[0].values[0] == 0)
    precondition(abs(springMoving.root.commands[0].values[0] - 0.004904) < 0.0001)
    precondition(springStart.needsContinuousFrames && springMoving.needsContinuousFrames)
    precondition(abs(springSettled.root.commands[0].values[0] - 1) < 0.001)
    precondition(!springSettled.needsContinuousFrames, "a settled spring kept requesting frames")

    do {
      _ = try NativeSwiftDocumentSession.open(data: unsupportedFloatAnimationDocument())
      preconditionFailure("an unknown easing mode was silently accepted")
    } catch let error as NativeSwiftCoreError {
      precondition(error.isUnsupported, "unknown easing should be an explicit unsupported error")
    }

    let concurrentSession = try NativeSwiftDocumentSession.open(data: concurrentStringDocument())
    DispatchQueue.concurrentPerform(iterations: 1_000) { index in
      switch index % 3 {
      case 0:
        precondition(concurrentSession.setString("value-\(index)", for: "message"))
      case 1:
        do {
          _ = try concurrentSession.snapshot()
        } catch {
          preconditionFailure("concurrent snapshot failed: \(error)")
        }
      default:
        do {
          _ = try concurrentSession.detachedCopy().snapshot()
        } catch {
          preconditionFailure("concurrent detached snapshot failed: \(error)")
        }
      }
    }

    print("native pure Swift core tests: ok")
  }

  /// A root holding one column that carries both an exact size and a fill, in AndroidX's order.
  private static func chainedSizeModifierDocument() -> Data {
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

  /// A root holding one row with a horizontal scroll modifier, in AndroidX's order: the row, the
  /// container the scroll modifier opens, its 214, and only then the row's content.
  private static func scrolledRowDocument() -> Data {
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
  private static func rootlessValuesDocument() -> Data {
    let output = Writer()
    output.header(width: 300, height: 200)
    output.u8(81).int(5).int(1).float(42)
    output.namedVariable(id: 5, type: 1, name: "answer")
    return output.data
  }

  private static func particleDocument() -> Data {
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

  private static func editableTextDocument() -> Data {
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

  private static func dynamicCustomFloatDocument() -> Data {
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

  private static func animatedFloatDocument(
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

  private static func compactAnimationDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(81).int(51).int((1 << 16) | 1).float(1).float(0.25)
    output.u8(200).int(1).u8(42).int(Writer.nanReference(51)).int(0).int(0).int(0).u8(214)
    return output.data
  }

  private static func springFloatDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(81).int(51).int((5 << 16) | 1)
      .float(1).float(0).float(40).float(8).float(0.001).int(0)
    output.u8(200).int(1).u8(42).int(Writer.nanReference(51)).int(0).int(0).int(0).u8(214)
    return output.data
  }

  private static func unsupportedFloatAnimationDocument() -> Data {
    let output = Writer()
    output.header(width: 100, height: 100)
    output.u8(81).int(51).int((2 << 16) | 1)
      .float(1).float(1).int(15)
    output.u8(200).int(1).u8(42).int(Writer.nanReference(51)).int(0).int(0).int(0).u8(214)
    return output.data
  }

  private static func concurrentStringDocument() -> Data {
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
