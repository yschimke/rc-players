import Foundation

@main
enum GenerateFixture {
  static func main() throws {
    guard CommandLine.arguments.count == 2 else {
      throw FixtureError.usage
    }
    let writer = Writer()
    writer.header(width: 640, height: 240)
    writer.text(id: 20, "ORBITRON 123")
    writer.text(id: 21, "google:Orbitron")
    writer.u8(200).int(1)
    writer.u8(201).int(2)
    writer.textLayout(
      id: 3, textID: 20, color: 0xff17_2534, size: 64, familyID: 21)
    writer.u8(214).u8(214)
    try writer.data.write(to: URL(fileURLWithPath: CommandLine.arguments[1]), options: .atomic)
  }

  private enum FixtureError: Error {
    case usage
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

  func textLayout(id: Int, textID: Int, color: UInt32, size: Float, familyID: Int) {
    u8(208).int(id).int(0).int(textID).int(Int(Int32(bitPattern: color)))
      .float(size).int(0).float(400).int(familyID).int(1).int(1).int(1)
    u8(214)
  }
}
