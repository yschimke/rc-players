import CoreGraphics
import Foundation
import ImageIO

guard CommandLine.arguments.count == 2 else {
  fatalError("usage: validate-apple-player-screenshot.swift screenshot.png")
}
let url = URL(fileURLWithPath: CommandLine.arguments[1]) as CFURL
guard let source = CGImageSourceCreateWithURL(url, nil),
  let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
else {
  fatalError("could not decode screenshot")
}

let width = image.width
let height = image.height
var pixels = [UInt8](repeating: 0, count: width * height * 4)
guard
  let context = CGContext(
    data: &pixels,
    width: width,
    height: height,
    bitsPerComponent: 8,
    bytesPerRow: width * 4,
    space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
  )
else {
  fatalError("could not allocate screenshot bitmap")
}
context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))

var white = 0
var darkNeutral = 0
var coloured = 0
for offset in stride(from: 0, to: pixels.count, by: 4) {
  let red = Int(pixels[offset])
  let green = Int(pixels[offset + 1])
  let blue = Int(pixels[offset + 2])
  if red > 245 && green > 245 && blue > 245 { white += 1 }
  if red < 80 && green < 80 && blue < 80 && max(red, green, blue) - min(red, green, blue) < 25 {
    darkNeutral += 1
  }
  if max(red, green, blue) - min(red, green, blue) > 45 { coloured += 1 }
}

let count = width * height
let whiteRatio = Double(white) / Double(count)
let darkRatio = Double(darkNeutral) / Double(count)
let colourRatio = Double(coloured) / Double(count)
guard whiteRatio > 0.15, darkRatio > 0.01, colourRatio > 0.08 else {
  fputs(
    "render not ready (white=\(whiteRatio), dark=\(darkRatio), colour=\(colourRatio))\n",
    stderr
  )
  exit(1)
}
