import Foundation

@main
enum NativeSwiftCoreTests {
  static func main() throws {
    let wire = editableTextDocument()
    if CommandLine.arguments.count == 2 {
      let kotlinFixture = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
      precondition(kotlinFixture == wire, "Swift test fixture differs from the Kotlin encoder")
    }
    if CommandLine.arguments.count == 3 {
      let titleData = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2]))
      let title = try NativeSwiftDocumentSession.open(data: titleData).snapshot()
      precondition(title.width == 640 && title.height == 480)
      precondition(title.root.firstText == "Morning run")
      precondition(title.root.allText.contains("5.2 km · 28 min"))
    }
    let session = try NativeSwiftDocumentSession.open(data: wire)
    let initial = try session.snapshot()
    precondition(initial.width == 360 && initial.height == 150)
    precondition(initial.root.children.first?.children.first?.kind == .column)

    let column = initial.root.children[0].children[0]
    precondition(column.children.count == 1)
    let content = column.children[0]
    precondition(content.children.count == 3)
    precondition(content.children[0].custom?.config == "demo:EditableText")
    precondition(content.children[0].custom?.properties[0].textValue == "Hello from the document")
    precondition(content.children[2].text?.value == "Hello from the document")

    let accepted = try session.returnCustomText(
      "Edited in Swift", componentID: 5, propertyID: 2)
    precondition(accepted)
    let updated = try session.snapshot()
    let updatedContent = updated.root.children[0].children[0].children[0]
    precondition(updatedContent.children[0].custom?.properties[0].textValue == "Edited in Swift")
    precondition(updatedContent.children[2].text?.value == "Edited in Swift")
    let rejected = try session.returnCustomText("Ignored", componentID: 5, propertyID: 99)
    precondition(!rejected)

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
}

private final class Writer {
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
  func float(_ value: Float) -> Writer {
    int(Int(Int32(bitPattern: value.bitPattern)))
  }

  func header(width: Int, height: Int) {
    u8(0).int(1).int(0).int(0).int(width).int(height).int(0).int(0)
  }

  func text(id: Int, _ value: String) {
    let encoded = Array(value.utf8)
    u8(102).int(id).int(encoded.count)
    bytes.append(contentsOf: encoded)
  }

  func textLayout(id: Int, textID: Int, color: UInt32, size: Float) {
    u8(208).int(id).int(0).int(textID).int(Int(Int32(bitPattern: color)))
      .float(size).int(0).float(400).int(-1).int(1).int(1).int(1)
    u8(214)
  }
}
