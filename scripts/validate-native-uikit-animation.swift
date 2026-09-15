import CoreGraphics
import Foundation
import ImageIO

guard CommandLine.arguments.count == 3 else {
  fatalError("usage: validate-native-uikit-animation.swift first.png second.png")
}

func pixels(at path: String) -> (width: Int, height: Int, bytes: [UInt8]) {
  let url = URL(fileURLWithPath: path) as CFURL
  guard let source = CGImageSourceCreateWithURL(url, nil),
    let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
  else { fatalError("could not decode \(path)") }
  var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
  guard
    let context = CGContext(
      data: &bytes, width: image.width, height: image.height, bitsPerComponent: 8,
      bytesPerRow: image.width * 4, space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
  else { fatalError("could not allocate screenshot bitmap") }
  context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
  return (image.width, image.height, bytes)
}

let first = pixels(at: CommandLine.arguments[1])
let second = pixels(at: CommandLine.arguments[2])
guard first.width == second.width, first.height == second.height else {
  fatalError("animation screenshots have different dimensions")
}

// The sample's document surface occupies the central portion of the detail pane on every iPad
// layout used in CI. Restricting comparison to it excludes the intentionally animated host chrome.
let xRange = Int(Double(first.width) * 0.40)..<Int(Double(first.width) * 0.98)
let yRange = Int(Double(first.height) * 0.27)..<Int(Double(first.height) * 0.78)
var ink = 0
var changed = 0
for y in yRange {
  for x in xRange {
    let offset = (y * first.width + x) * 4
    let a = (0..<3).map { Int(first.bytes[offset + $0]) }
    let b = (0..<3).map { Int(second.bytes[offset + $0]) }
    if a.min()! < 238 || b.min()! < 238 { ink += 1 }
    if zip(a, b).reduce(0, { $0 + abs($1.0 - $1.1) }) > 30 { changed += 1 }
  }
}

guard ink > 100, changed > 25 else {
  fputs("native animation not visible or not moving (ink=\(ink), changed=\(changed))\n", stderr)
  exit(1)
}
print("native UIKit animation: ok (ink=\(ink), changed=\(changed))")
