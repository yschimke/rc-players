import Foundation

/// Bounded PCM synthesis for the wire's tone sound expression.
enum NativeSwiftToneSynthesizer {
  static func synthesize(parameters: [UInt32], values: [Int: Float]) -> Data? {
    // The first word is a synthesis tag, not a variable reference.
    guard parameters.count >= 4, parameters[0] == 0xff80_000a else { return nil }
    let frequency = NativeSwiftFloatExpression.resolve(parameters[1], values: values)
    let duration = NativeSwiftFloatExpression.resolve(parameters[2], values: values)
    let waveform = NativeSwiftFloatExpression.resolve(parameters[3], values: values)
    guard frequency.isFinite, duration.isFinite, waveform.isFinite else { return nil }
    let kind = waveform >= 0 && waveform <= 3 ? Int(waveform) : 0
    let sampleRate = 22_050
    let maximumSamples = (256 * 1024 - 44) / 2
    let requested = Double(duration) * Double(sampleRate)
    guard requested <= Double(maximumSamples) else { return nil }
    let count = requested <= 0 ? 1 : max(1, Int(requested))
    let pcmBytes = count * 2
    var bytes = [UInt8](repeating: 0, count: 44 + pcmBytes)
    func ascii(_ offset: Int, _ value: String) {
      for (index, byte) in value.utf8.enumerated() { bytes[offset + index] = byte }
    }
    func littleEndian(_ offset: Int, _ value: Int, count: Int) {
      for index in 0..<count { bytes[offset + index] = UInt8(truncatingIfNeeded: value >> (index * 8)) }
    }
    ascii(0, "RIFF")
    littleEndian(4, 36 + pcmBytes, count: 4)
    ascii(8, "WAVE")
    ascii(12, "fmt ")
    littleEndian(16, 16, count: 4)
    littleEndian(20, 1, count: 2)
    littleEndian(22, 1, count: 2)
    littleEndian(24, sampleRate, count: 4)
    littleEndian(28, sampleRate * 2, count: 4)
    littleEndian(32, 2, count: 2)
    littleEndian(34, 16, count: 2)
    ascii(36, "data")
    littleEndian(40, pcmBytes, count: 4)
    for index in 0..<count {
      let cycles = Double(frequency) * Double(index) / Double(sampleRate)
      let phase = 2 * Double.pi * cycles
      let signal: Double
      switch kind {
      case 1: signal = sin(phase) >= 0 ? 1 : -1
      case 2: signal = 2 * (cycles - floor(cycles + 0.5))
      case 3: signal = 2 * abs(2 * (cycles - floor(cycles + 0.5))) - 1
      default: signal = sin(phase)
      }
      let envelope = min(1, Double(index) / 100, Double(count - index) / 100)
      let sample = Int16(clamping: Int(signal * envelope * Double(Int16.max)))
      littleEndian(44 + index * 2, Int(sample), count: 2)
    }
    return Data(bytes)
  }
}
