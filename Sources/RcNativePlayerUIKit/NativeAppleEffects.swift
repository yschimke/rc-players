#if canImport(AVFoundation)
  import AVFoundation
  import Foundation
  #if canImport(UIKit)
    import UIKit
  #elseif canImport(AppKit)
    import AppKit
  #endif

  /// Audio boundary for document sounds. Replace this on a player to use an app-owned audio engine.
  @MainActor public protocol RemoteComposeNativeSoundPlaying: AnyObject {
    func playSound(id: Int, data: Data)
    func stopAll()
  }

  /// AVFoundation backend for inline WAV, AndroidX SC PCM, and synthesized tone sounds.
  @MainActor public final class RemoteComposeNativeAVSoundPlayer: RemoteComposeNativeSoundPlaying {
    private var players: [Int: (data: Data, player: AVAudioPlayer)] = [:]

    public init() {}

    public func playSound(id: Int, data: Data) {
      guard let wav = Self.wavData(data) else { return }
      if let cached = players[id], cached.data == wav {
        cached.player.stop()
        cached.player.currentTime = 0
        cached.player.prepareToPlay()
        cached.player.play()
        return
      }
      guard let player = try? AVAudioPlayer(data: wav), player.prepareToPlay() else { return }
      players[id]?.player.stop()
      players[id] = (wav, player)
      player.play()
    }

    public func stopAll() {
      players.values.forEach { $0.player.stop() }
      players.removeAll()
    }

    private static func wavData(_ data: Data) -> Data? {
      let bytes = [UInt8](data)
      if bytes.count >= 44, bytes[0...3].elementsEqual(Array("RIFF".utf8)),
        bytes[8...11].elementsEqual(Array("WAVE".utf8))
      {
        return data
      }
      guard bytes.count >= 11, bytes[0] == 0x53, bytes[1] == 0x43 else { return nil }
      let bits = Int(bytes[3])
      let channels = Int(bytes[4])
      let rate = Int(bytes[5]) << 8 | Int(bytes[6])
      let samples = UInt64(bytes[7]) << 24 | UInt64(bytes[8]) << 16
        | UInt64(bytes[9]) << 8 | UInt64(bytes[10])
      guard (bits == 8 || bits == 16), (1...2).contains(channels), rate > 0 else { return nil }
      let length = samples * UInt64(bits / 8 * channels)
      guard length <= UInt64(bytes.count - 11), length <= UInt64(Int.max - 44) else { return nil }
      var wav = [UInt8](repeating: 0, count: 44 + Int(length))
      func ascii(_ offset: Int, _ value: String) {
        for (index, byte) in value.utf8.enumerated() { wav[offset + index] = byte }
      }
      func write(_ offset: Int, _ value: Int, _ count: Int) {
        for index in 0..<count { wav[offset + index] = UInt8(truncatingIfNeeded: value >> (index * 8)) }
      }
      ascii(0, "RIFF")
      write(4, 36 + Int(length), 4)
      ascii(8, "WAVE")
      ascii(12, "fmt ")
      write(16, 16, 4)
      write(20, 1, 2)
      write(22, channels, 2)
      write(24, rate, 4)
      write(28, rate * channels * bits / 8, 4)
      write(32, channels * bits / 8, 2)
      write(34, bits, 2)
      ascii(36, "data")
      write(40, Int(length), 4)
      wav.replaceSubrange(44..<wav.count, with: bytes[11..<(11 + Int(length))])
      return Data(wav)
    }
  }

  @MainActor func performNativeDocumentHaptic(_ type: Int) {
    guard type >= 0, let cue = NativeDocumentHaptic(rawValue: type % 21), cue != .none else {
      return
    }
    #if canImport(UIKit)
      switch cue {
      case .longPress, .contextClick, .gestureThresholdActivate, .dragStart:
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
      case .gestureStart, .gestureEnd, .gestureThresholdDeactivate:
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
      case .virtualKey, .keyboardTap, .clockTick, .keyboardPress, .keyboardRelease,
        .virtualKeyRelease, .textHandleMove, .segmentTick, .segmentFrequentTick:
        UISelectionFeedbackGenerator().selectionChanged()
      case .confirm, .toggleOn:
        UINotificationFeedbackGenerator().notificationOccurred(.success)
      case .toggleOff:
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
      case .reject:
        UINotificationFeedbackGenerator().notificationOccurred(.error)
      case .none:
        break
      }
    #elseif canImport(AppKit)
      NSHapticFeedbackManager.defaultPerformer.perform(.generic, performanceTime: .now)
    #endif
  }

  /// AndroidX's full wire vocabulary. The host maps intent to the nearest platform feedback.
  private enum NativeDocumentHaptic: Int {
    case none = 0
    case longPress = 1
    case virtualKey = 2
    case keyboardTap = 3
    case clockTick = 4
    case contextClick = 5
    case keyboardPress = 6
    case keyboardRelease = 7
    case virtualKeyRelease = 8
    case textHandleMove = 9
    case gestureStart = 10
    case gestureEnd = 11
    case confirm = 12
    case reject = 13
    case toggleOn = 14
    case toggleOff = 15
    case gestureThresholdActivate = 16
    case gestureThresholdDeactivate = 17
    case dragStart = 18
    case segmentTick = 19
    case segmentFrequentTick = 20
  }
#endif
