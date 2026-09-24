/// Bilinear upsampling as the reference players sample a scaled bitmap.
///
/// AndroidX draws a bitmap through a paint that filters bilinearly by default, and Skia samples a
/// destination pixel at its centre: `u = (x + 0.5) · sourceWidth / destinationWidth − 0.5`, blending
/// the four nearest source pixels and clamping at the edges. Core Graphics' own interpolation
/// levels are not specified to be that filter, and an upscaled bitmap drawn with them differed from
/// `canvas_bitmap_scaled`'s reference well past the raster tolerance. Resampling here and drawing
/// the result one to one gives the reference's pixels on every host.
enum NativeBilinearScaler {
  /// The most destination pixels one resample produces (2048 × 2048). A live view redraws every
  /// frame, so a larger upscale is left to Core Graphics rather than stall a cache miss.
  static let maximumPixels = 4_194_304

  /// `pixels` holds `width × height` premultiplied RGBA8 pixels, row-major and tightly packed. The
  /// result holds `targetWidth × targetHeight` of them, or nil when either size is empty or the
  /// input is short.
  static func scale(
    _ pixels: [UInt8], width: Int, height: Int, targetWidth: Int, targetHeight: Int
  ) -> [UInt8]? {
    guard width > 0, height > 0, targetWidth > 0, targetHeight > 0,
      pixels.count >= width * height * 4,
      targetWidth.multipliedReportingOverflow(by: targetHeight).overflow == false,
      targetWidth * targetHeight <= maximumPixels
    else { return nil }
    let columns = samples(count: targetWidth, source: width)
    let rows = samples(count: targetHeight, source: height)
    var output = [UInt8](repeating: 0, count: targetWidth * targetHeight * 4)
    pixels.withUnsafeBufferPointer { input in
      output.withUnsafeMutableBufferPointer { result in
        for (y, row) in rows.enumerated() {
          let top = row.lower * width * 4
          let bottom = row.upper * width * 4
          for (x, column) in columns.enumerated() {
            let left = column.lower * 4
            let right = column.upper * 4
            let target = (y * targetWidth + x) * 4
            for channel in 0..<4 {
              let upper =
                Float(input[top + left + channel]) * (1 - column.fraction)
                + Float(input[top + right + channel]) * column.fraction
              let lower =
                Float(input[bottom + left + channel]) * (1 - column.fraction)
                + Float(input[bottom + right + channel]) * column.fraction
              let value = upper * (1 - row.fraction) + lower * row.fraction
              result[target + channel] = UInt8(min(max(value + 0.5, 0), 255))
            }
          }
        }
      }
    }
    return output
  }

  /// For each destination index, the two source indices it blends and the weight of the second.
  private static func samples(
    count: Int, source: Int
  ) -> [(lower: Int, upper: Int, fraction: Float)] {
    let step = Float(source) / Float(count)
    return (0..<count).map { index in
      let position = (Float(index) + 0.5) * step - 0.5
      let floor = position.rounded(.down)
      let lower = Int(floor)
      return (
        lower: min(max(lower, 0), source - 1),
        upper: min(max(lower + 1, 0), source - 1),
        fraction: position - floor
      )
    }
  }
}
