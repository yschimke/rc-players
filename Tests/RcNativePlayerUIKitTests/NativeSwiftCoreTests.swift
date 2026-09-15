import Foundation

@main
enum NativeSwiftCoreTests {
  static func main() throws {
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
    if CommandLine.arguments.count == 7 {
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
      guard let button = snapshot.root.firstClickable else {
        preconditionFailure("image button has no clickable component")
      }
      let events = try session.click(componentID: button.componentID, timeSeconds: 0)
      precondition(events == [])
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

    let modern = Writer()
    modern.modernHeader(width: 100, height: 50, unrelatedKey: 69, unrelatedValue: 999)
    modern.u8(200).int(1).u8(214).u8(214)
    let modernSnapshot = try NativeSwiftDocumentSession.open(data: modern.data).snapshot()
    precondition(modernSnapshot.width == 100 && modernSnapshot.height == 50)

    let canvasOperations = Writer()
    canvasOperations.header(width: 100, height: 100)
    canvasOperations.u8(200).int(1)
    canvasOperations.u8(201).int(2)
    canvasOperations.u8(205).int(3).int(-1)
    canvasOperations.u8(173).u8(130).u8(131).u8(214)
    canvasOperations.u8(214).u8(214).u8(214)
    let canvasSnapshot = try NativeSwiftDocumentSession.open(data: canvasOperations.data).snapshot()
    precondition(canvasSnapshot.root.children[0].children[0].commands.count == 2)

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

    print("native pure Swift core tests: ok")
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
}

extension NativeSwiftNodeSnapshot {
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
}

private final class Writer {
  static func nanReference(_ id: Int) -> Int {
    Int(Int32(bitPattern: 0xff80_0000 | UInt32(id)))
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

  func textLayout(id: Int, textID: Int, color: UInt32, size: Float) {
    u8(208).int(id).int(0).int(textID).int(Int(Int32(bitPattern: color)))
      .float(size).int(0).float(400).int(-1).int(1).int(1).int(1)
    u8(214)
  }
}
