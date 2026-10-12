#if os(macOS)
  import Darwin
  import Foundation

  /// The pooled mode: a long-lived process rendering one document per request frame, so the
  /// preview server pays process start-up and AppKit initialization once rather than per render.
  ///
  /// The frames are compose-ai-tools' `rc-render-jvm` worker protocol, byte for byte, so one pool
  /// client drives both renderers. Big-endian:
  ///
  /// ```
  /// worker -> pool, once at startup:
  ///   int32 MAGIC_HELLO, int32 PROTOCOL_VERSION
  /// pool -> worker, per request:
  ///   int32 MAGIC_REQUEST, int32 requestId, int32 width, int32 height,
  ///   int32 densityBits (IEEE 754 float bits), int32 format (0=png, 1=svg),
  ///   int32 theme (0=light, 1=dark), int32 seedsLen, <seedsLen bytes UTF-8>,
  ///   int32 docLen, <docLen bytes>
  /// worker -> pool, per response:
  ///   int32 MAGIC_RESPONSE, int32 requestId, int32 status (0=ok, 1=failed),
  ///   int32 payloadLen, <payloadLen bytes>   // PNG bytes on ok, UTF-8 reason on failure
  /// ```
  ///
  /// The pool refuses a worker whose hello names another protocol version, so the two sides change
  /// together or not at all. A request for SVG is answered as a failure: the AppKit renderer has no
  /// vector export.
  enum RcSwiftRenderWorker {
    // 'RCW1' / 'RCQ1' / 'RCR1' as big-endian ASCII, as `RcJvmRenderWorkerMain.kt` spells them.
    static let magicHello: Int32 = 0x5243_5731
    static let magicRequest: Int32 = 0x5243_5131
    static let magicResponse: Int32 = 0x5243_5231
    static let protocolVersion: Int32 = 2
    static let statusOK: Int32 = 0
    static let statusFailed: Int32 = 1
    static let formatSVG: Int32 = 1
    static let themeDark: Int32 = 1
    /// 256 MB: far above any real document, far below "allocate until the process dies".
    static let maximumPayloadBytes: Int32 = 256 * 1024 * 1024

    @MainActor
    static func run() -> Never {
      // Claim the real stdout for frames before anything can print, and point fd 1 at stderr: one
      // stray `print` from a library would otherwise desynchronise the stream.
      let frames = dup(STDOUT_FILENO)
      guard frames >= 0, dup2(STDERR_FILENO, STDOUT_FILENO) >= 0 else {
        fail("rc-swift-render worker: could not claim stdout", code: 4)
      }
      let output = FrameWriter(fd: frames)
      let input = FrameReader(fd: STDIN_FILENO)

      output.int32(magicHello)
      output.int32(protocolVersion)
      output.flush()

      while true {
        guard let magic = input.int32() else {
          // The pool closed our stdin: an ordinary shutdown.
          exit(0)
        }
        guard magic == magicRequest else {
          // No safe way to resynchronise mid-frame. Exit so the pool replaces this worker.
          fail("rc-swift-render worker: unexpected frame magic \(magic); exiting", code: 4)
        }
        guard let requestID = input.int32(), let width = input.int32(),
          let height = input.int32(), let densityBits = input.int32(),
          let format = input.int32(), let theme = input.int32(),
          let seeds = input.payload(limit: maximumPayloadBytes),
          let document = input.payload(limit: maximumPayloadBytes)
        else {
          fail("rc-swift-render worker: truncated or implausible request; exiting", code: 4)
        }

        let status: Int32
        let payload: Data
        if format == formatSVG {
          status = statusFailed
          payload = Data("svg is not supported by the AppKit renderer".utf8)
        } else {
          var request = RcSwiftRenderRequest(width: Int(width), height: Int(height))
          let density = Float(bitPattern: UInt32(bitPattern: densityBits))
          if density.isFinite, density > 0 { request.density = CGFloat(density) }
          request.dark = theme == themeDark
          request.seeds = parseSeedText(String(decoding: seeds, as: UTF8.self))
          do {
            payload = try rcSwiftRender(document, request)
            status = statusOK
          } catch {
            // An undrawable document is this request's failure; the worker stays alive.
            status = statusFailed
            payload = Data("\(error)".utf8)
          }
        }

        output.int32(magicResponse)
        output.int32(requestID)
        output.int32(status)
        output.int32(Int32(payload.count))
        output.bytes(payload)
        output.flush()
      }
    }
  }

  /// Blocking, exact-length reads from a file descriptor. `nil` means end of stream.
  private final class FrameReader {
    private let fd: Int32

    init(fd: Int32) { self.fd = fd }

    func exactly(_ count: Int) -> Data? {
      var data = Data(count: count)
      var filled = 0
      while filled < count {
        let read = data.withUnsafeMutableBytes { buffer in
          Darwin.read(fd, buffer.baseAddress!.advanced(by: filled), count - filled)
        }
        if read > 0 {
          filled += read
        } else if read < 0, errno == EINTR {
          continue
        } else {
          return nil
        }
      }
      return data
    }

    func int32() -> Int32? {
      guard let bytes = exactly(4) else { return nil }
      return Int32(bitPattern: bytes.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) })
    }

    func payload(limit: Int32) -> Data? {
      guard let length = int32(), length >= 0, length <= limit else { return nil }
      return length == 0 ? Data() : exactly(Int(length))
    }
  }

  /// Buffered big-endian writes to a file descriptor, flushed once per frame.
  private final class FrameWriter {
    private let fd: Int32
    private var buffer = Data()

    init(fd: Int32) { self.fd = fd }

    func int32(_ value: Int32) {
      withUnsafeBytes(of: value.bigEndian) { buffer.append(contentsOf: $0) }
    }

    func bytes(_ data: Data) { buffer.append(data) }

    func flush() {
      let total = buffer.count
      var written = 0
      while written < total {
        let count = buffer.withUnsafeBytes { bytes in
          Darwin.write(fd, bytes.baseAddress!.advanced(by: written), total - written)
        }
        if count > 0 {
          written += count
        } else if count < 0, errno == EINTR {
          continue
        } else {
          fail("rc-swift-render worker: stdout closed", code: 4)
        }
      }
      buffer.removeAll(keepingCapacity: true)
    }
  }
#endif
