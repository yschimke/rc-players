import Foundation
import RcNativePlayerCore

// Raster evidence consumes the same resolved colors as the native renderer. This executable
// does not claim to capture UIKit; the paired PNGs explicitly identify the core snapshot source.
let cases: [(destination: UInt32, source: UInt32)] = [
  (0x8000_0000, 0xffff_0000), (0x8000_0000, 0x80ff_0000), (0x0000_0000, 0xffff_0000),
]
var result: [[String: UInt32]] = []
for sample in cases {
  var bytes = Data([UInt8(NativeSwiftWireOpcode.header)])
  func word(_ value: UInt32) {
    bytes.append(contentsOf: [
      UInt8(truncatingIfNeeded: value >> 24), UInt8(truncatingIfNeeded: value >> 16),
      UInt8(truncatingIfNeeded: value >> 8), UInt8(truncatingIfNeeded: value),
    ])
  }
  // Legacy header followed by a root, four paint words, a rectangle and container end.
  [1, 0, 0, 100, 100, 0, 0].forEach { word(UInt32($0)) }
  bytes.append(UInt8(NativeSwiftWireOpcode.layoutRoot))
  word(0)
  bytes.append(UInt8(NativeSwiftWireOpcode.paintValues))
  word(4)
  word(4)  // Paint COLOR, followed by literal ARGB.
  word(sample.destination)
  word(13 | (UInt32(NativeSwiftPaintBlendMode.sourceIn.rawValue) << 16))  // Paint COLOR_FILTER.
  word(sample.source)
  bytes.append(UInt8(NativeSwiftWireOpcode.drawRect))
  [Float(0), 0, 100, 100].forEach { word($0.bitPattern) }
  bytes.append(UInt8(NativeSwiftWireOpcode.containerEnd))
  let session = try NativeSwiftDocumentSession.open(data: bytes)
  guard let draw = try session.snapshot().root.commands.first else {
    fatalError("Evidence rectangle was not decoded")
  }
  result.append([
    "destination": sample.destination, "source": sample.source, "resolved": draw.colorARGB,
  ])
}
let json = try JSONSerialization.data(
  withJSONObject: result, options: [.prettyPrinted, .sortedKeys])
FileHandle.standardOutput.write(json)
