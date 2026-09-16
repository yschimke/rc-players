import Foundation

/// Mutation fuzzing for the pure-Swift document core.
///
/// The core is the only thing between host bytes and a retained render session, so the property it
/// has to hold is narrow and absolute: *any* input either decodes into a bounded snapshot or fails
/// with a typed `NativeSwiftCoreError`. It may never trap, never throw an untyped error, and never
/// run unbounded work. Mutation coverage is derived rather than committed: every supported
/// comparative fixture is a seed, and a deterministic PRNG expands each one into truncated,
/// corrupted, spliced, and extreme-valued variants, so the corpus follows the fixture set rather
/// than drifting from it.
///
/// Reproduction is deterministic. `RC_NATIVE_FUZZ_SEED` and `RC_NATIVE_FUZZ_ITERATIONS` select the
/// same case sequence on any host, and a failure writes the offending bytes to
/// `RC_NATIVE_FUZZ_CORPUS_OUT` (default: a temporary directory, printed) before exiting.
@main
enum NativeSwiftFuzzTests {
  private static let defaultIterations = 96
  private static let caseTimeoutSeconds = 10.0

  static func main() {
    let arguments = Array(CommandLine.arguments.dropFirst())
    var seeds: [(name: String, data: Data)] = syntheticSeeds()
    for path in arguments {
      guard let data = FileManager.default.contents(atPath: path) else {
        FileHandle.standardError.write(Data("fuzz seed not readable: \(path)\n".utf8))
        exit(1)
      }
      seeds.append((name: URL(fileURLWithPath: path).lastPathComponent, data: data))
    }
    guard arguments.count >= 1 else {
      FileHandle.standardError.write(
        Data("usage: native-swift-fuzz-tests <fixture.rc>...\n".utf8))
      exit(1)
    }

    let iterations = max(environmentInt("RC_NATIVE_FUZZ_ITERATIONS") ?? defaultIterations, 0)
    let seedValue = UInt64(
      bitPattern: Int64(environmentInt("RC_NATIVE_FUZZ_SEED") ?? 0x5EED))
    let watchdog = Watchdog(timeout: caseTimeoutSeconds)
    watchdog.start()

    var decoded = 0
    var rejected = 0
    var cases = 0
    let started = ProcessInfo.processInfo.systemUptime

    for (index, seed) in seeds.enumerated() {
      // The pristine seed first: a supported fixture must stay supported, and a synthetic
      // malformed seed must stay typed.
      watchdog.begin(label: "\(seed.name)#pristine", data: seed.data)
      exercise(seed.data, label: "\(seed.name)#pristine", decoded: &decoded, rejected: &rejected)
      cases += 1

      var random = SplitMix64(seed: seedValue &+ UInt64(index) &* 0x9E37_79B9_7F4A_7C15)
      for iteration in 0..<iterations {
        let label = "\(seed.name)#\(iteration)"
        let mutant = mutate(seed.data, using: &random)
        watchdog.begin(label: label, data: mutant)
        exercise(mutant, label: label, decoded: &decoded, rejected: &rejected)
        cases += 1
      }
    }
    watchdog.stop()
    let elapsed = ProcessInfo.processInfo.systemUptime - started

    // Bounded work, measured over the whole run so one slow host does not decide a single case.
    let budget = Double(cases) * 0.25
    precondition(
      elapsed < budget,
      "fuzzing \(cases) cases took \(elapsed)s, over the \(budget)s bounded-work budget")
    precondition(
      decoded >= seeds.count,
      "only \(decoded) of \(cases) cases decoded; the corpus is dying at the header instead of "
        + "reaching the operation stream")
    precondition(rejected > 0, "no fuzz case was rejected; the mutations are not reaching the core")
    print(
      "native Swift fuzz: ok (\(cases) cases, \(decoded) decoded, \(rejected) typed rejections, "
        + "\(String(format: "%.2f", elapsed))s, seed=\(seedValue))")
  }

  /// Run one input through the whole retained-session surface and assert the typed contract.
  private static func exercise(
    _ data: Data, label: String, decoded: inout Int, rejected: inout Int
  ) {
    let session: NativeSwiftDocumentSession
    do {
      session = try NativeSwiftDocumentSession.open(data: data)
    } catch let error as NativeSwiftCoreError {
      // Typed rejection: unsupported and malformed both fail closed, which is the contract.
      precondition(!error.description.isEmpty)
      rejected += 1
      return
    } catch {
      report(data, label: label, reason: "untyped decode failure: \(error)")
    }

    do {
      let snapshot = try session.snapshot()
      try validate(snapshot, label: label, data: data)

      // Frames must stay bounded and typed across time, including hostile times.
      for time in [0.0, 0.25, 1.0, 60.0, -1.0, 1e9] as [TimeInterval] {
        let frame = try session.snapshot(timeSeconds: time)
        try validate(frame, label: label, data: data)
      }

      // Retained state updates: named values the document may or may not declare.
      for name in ["progress", "accent", "", String(repeating: "x", count: 4096), "\u{0}"] {
        _ = session.setFloat(0.5, for: name)
        _ = session.setFloat(.nan, for: name)
        _ = session.setFloat(.infinity, for: name)
        _ = session.setString("fuzz", for: name)
        _ = session.setColor(0xFF00_FF00, for: name)
      }

      // Input payloads against every component the snapshot exposes, plus ids it never declared.
      var componentIDs = Set<Int>()
      collectComponentIDs(snapshot.root, into: &componentIDs, limit: 256)
      let probed = Array(componentIDs.sorted().prefix(16)) + [-1, 0, Int.max, Int.min]
      for componentID in probed {
        for kind in NativeSwiftGestureKind.allCases {
          _ = try session.gesture(
            kind, componentID: componentID,
            sample: NativeSwiftPointerSample(x: 1, y: 2, velocityX: -3, velocityY: 4),
            timeSeconds: 0.5)
        }
        _ = try session.click(componentID: componentID, timeSeconds: 0)
        _ = try session.returnCustomText("fuzz", componentID: componentID, propertyID: 2)
        _ = try session.returnCustomFloat(1.5, componentID: componentID, propertyID: 1)
        _ = try session.returnCustomFloat(.nan, componentID: componentID, propertyID: 1)
      }

      // Resolving the same time twice must produce the same tree; a frame is a pure function of
      // retained state and time.
      let repeated = try session.snapshot(timeSeconds: 0.25)
      let again = try session.snapshot(timeSeconds: 0.25)
      guard shape(of: repeated.root) == shape(of: again.root) else {
        report(data, label: label, reason: "repeated frame at the same time changed shape")
      }
      decoded += 1
    } catch let error as NativeSwiftCoreError {
      // A typed failure after a successful decode is allowed: resolution enforces its own limits.
      precondition(!error.description.isEmpty)
      decoded += 1
    } catch {
      report(data, label: label, reason: "untyped frame failure: \(error)")
    }
  }

  private static func validate(
    _ snapshot: NativeSwiftDocumentSnapshot, label: String, data: Data
  ) throws {
    guard snapshot.width > 0, snapshot.height > 0 else {
      report(data, label: label, reason: "decoded a document with non-positive dimensions")
    }
    guard snapshot.density.isFinite, snapshot.density > 0 else {
      report(data, label: label, reason: "decoded a document with an invalid density")
    }
    guard (0...2).contains(snapshot.densityBehavior) else {
      report(data, label: label, reason: "decoded an unknown density behavior")
    }
    var nodes = 0
    var depth = 0
    measure(snapshot.root, depth: 1, nodes: &nodes, deepest: &depth)
    guard nodes <= 20_000 else {
      report(data, label: label, reason: "resolved \(nodes) nodes, over the decoder's own cap")
    }
    guard depth <= 256 else {
      report(data, label: label, reason: "resolved a tree \(depth) deep, over the nesting cap")
    }
    for image in snapshot.images {
      guard image.width > 0, image.height > 0 else {
        report(data, label: label, reason: "resolved an image resource with no pixels")
      }
    }
  }

  private static func measure(
    _ node: NativeSwiftNodeSnapshot, depth: Int, nodes: inout Int, deepest: inout Int
  ) {
    nodes += 1
    deepest = max(deepest, depth)
    guard nodes <= 20_000, depth <= 256 else { return }
    for child in node.children {
      measure(child, depth: depth + 1, nodes: &nodes, deepest: &deepest)
    }
  }

  private static func collectComponentIDs(
    _ node: NativeSwiftNodeSnapshot, into ids: inout Set<Int>, limit: Int
  ) {
    guard ids.count < limit else { return }
    ids.insert(node.componentID)
    for child in node.children { collectComponentIDs(child, into: &ids, limit: limit) }
  }

  /// A cheap structural digest, enough to notice a frame that stopped being deterministic.
  private static func shape(of node: NativeSwiftNodeSnapshot) -> String {
    var text = "\(node.componentID):\(node.commands.count):\(node.text?.value.count ?? -1)("
    for child in node.children { text += shape(of: child) }
    return text + ")"
  }

  // MARK: - Corpus

  private static func syntheticSeeds() -> [(name: String, data: Data)] {
    [
      (name: "empty", data: Data()),
      (name: "single-byte", data: Data([0])),
      (name: "header-only", data: Data([0, 0, 0, 0, 1])),
      (name: "noise", data: Data((0..<512).map { UInt8(truncatingIfNeeded: $0 &* 31 &+ 7) })),
      (name: "zeros", data: Data(repeating: 0, count: 1024)),
      (name: "ones", data: Data(repeating: 0xFF, count: 1024)),
    ]
  }

  private enum Mutation: CaseIterable {
    case truncate, flipBits, spliceNoise, deleteRange, insertRange, zeroRange, duplicateChunk
    case extremeWord
  }

  /// Where a byte-level mutation lands.
  ///
  /// Uniform offsets overwhelmingly corrupt the header, and an input rejected at byte 0 never
  /// reaches the operation stream — the part of the decoder that does the interesting work. Half
  /// the offsets are therefore biased past a header floor, so the corpus keeps a plausible document
  /// prefix and gets deep enough to matter. The run prints how many cases decoded so shallow
  /// coverage is visible rather than implied.
  private static func offset(in count: Int, using random: inout SplitMix64) -> Int {
    guard count > 0 else { return 0 }
    let floor = min(64, count / 4)
    guard floor > 0, random.next(upperBound: 2) == 1 else {
      return Int(random.next(upperBound: UInt64(count)))
    }
    return floor + Int(random.next(upperBound: UInt64(count - floor)))
  }

  private static func mutate(_ seed: Data, using random: inout SplitMix64) -> Data {
    var bytes = [UInt8](seed)
    let rounds = 1 + Int(random.next(upperBound: 3))
    for _ in 0..<rounds {
      let choice = Int(random.next(upperBound: UInt64(Mutation.allCases.count)))
      let mutation = Mutation.allCases[choice]
      switch mutation {
      case .truncate:
        guard !bytes.isEmpty else { break }
        bytes = Array(bytes.prefix(offset(in: bytes.count, using: &random)))
      case .flipBits:
        guard !bytes.isEmpty else { break }
        let index = offset(in: bytes.count, using: &random)
        bytes[index] ^= UInt8(truncatingIfNeeded: random.next())
      case .spliceNoise:
        guard !bytes.isEmpty else { break }
        let start = offset(in: bytes.count, using: &random)
        let length = min(bytes.count - start, 1 + Int(random.next(upperBound: 32)))
        for offset in start..<(start + length) {
          bytes[offset] = UInt8(truncatingIfNeeded: random.next())
        }
      case .deleteRange:
        guard bytes.count > 1 else { break }
        let start = offset(in: bytes.count, using: &random)
        let length = min(bytes.count - start, 1 + Int(random.next(upperBound: 16)))
        bytes.removeSubrange(start..<(start + length))
      case .insertRange:
        let start = offset(in: bytes.count, using: &random)
        let inserted = (0..<(1 + Int(random.next(upperBound: 16)))).map { _ in
          UInt8(truncatingIfNeeded: random.next())
        }
        bytes.insert(contentsOf: inserted, at: start)
      case .zeroRange:
        guard !bytes.isEmpty else { break }
        let start = offset(in: bytes.count, using: &random)
        let length = min(bytes.count - start, 1 + Int(random.next(upperBound: 24)))
        for offset in start..<(start + length) { bytes[offset] = 0 }
      case .duplicateChunk:
        guard !bytes.isEmpty, bytes.count < 1 << 20 else { break }
        let start = offset(in: bytes.count, using: &random)
        let length = min(bytes.count - start, 1 + Int(random.next(upperBound: 64)))
        let chunk = Array(bytes[start..<(start + length)])
        bytes.insert(contentsOf: chunk, at: start)
      case .extremeWord:
        // Counts, lengths, and floats are read as big-endian words; extremes are where a decoder
        // stops being bounded.
        guard bytes.count >= 8 else { break }
        let word = [
          [0xFF, 0xFF, 0xFF, 0xFF] as [UInt8],  // -1 / huge count
          [0x7F, 0xFF, 0xFF, 0xFF],  // Int32.max
          [0x7F, 0x80, 0x00, 0x00],  // +infinity
          [0xFF, 0x80, 0x00, 0x00],  // -infinity
          [0x7F, 0xC0, 0x00, 0x00],  // NaN
          [0x80, 0x00, 0x00, 0x00],  // -0 / Int32.min
        ][Int(random.next(upperBound: 6))]
        let start = offset(in: bytes.count - 3, using: &random)
        bytes.replaceSubrange(start..<(start + 4), with: word)
      }
    }
    return Data(bytes)
  }

  // MARK: - Failure reporting

  private static func report(_ data: Data, label: String, reason: String) -> Never {
    let path = persist(data, label: label)
    FileHandle.standardError.write(
      Data("native Swift fuzz failure: \(reason)\n  case: \(label)\n  bytes: \(path)\n".utf8))
    exit(1)
  }

  /// Write one case's bytes where the run can be reproduced from, and return where they landed.
  ///
  /// Both failure paths need this: a hang is terminated by the watchdog on another thread and never
  /// reaches `report`, so without a shared writer the uploaded corpus would be empty for exactly
  /// the unbounded-work failure it exists to capture.
  static func persist(_ data: Data, label: String) -> String {
    let directory =
      ProcessInfo.processInfo.environment["RC_NATIVE_FUZZ_CORPUS_OUT"]
      ?? NSTemporaryDirectory().appending("rc-native-fuzz")
    try? FileManager.default.createDirectory(
      atPath: directory, withIntermediateDirectories: true)
    let safeLabel = label.replacingOccurrences(of: "/", with: "_")
    let path = directory.appending("/\(safeLabel).rc")
    try? data.write(to: URL(fileURLWithPath: path), options: .atomic)
    return path
  }

  private static func environmentInt(_ name: String) -> Int? {
    ProcessInfo.processInfo.environment[name].flatMap(Int.init)
  }
}

/// Deterministic PRNG: the same seed replays the same corpus on any host.
private struct SplitMix64 {
  private var state: UInt64

  init(seed: UInt64) { state = seed }

  mutating func next() -> UInt64 {
    state = state &+ 0x9E37_79B9_7F4A_7C15
    var z = state
    z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
    z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
    return z ^ (z >> 31)
  }

  mutating func next(upperBound: UInt64) -> UInt64 {
    upperBound == 0 ? 0 : next() % upperBound
  }
}

/// Turns a hang into a failure. Without it an unbounded loop in the decoder is a CI timeout with no
/// input attached.
private final class Watchdog: @unchecked Sendable {
  private let timeout: TimeInterval
  private let lock = NSLock()
  private var label = "<none>"
  private var data = Data()
  private var startedAt = ProcessInfo.processInfo.systemUptime
  private var running = true

  init(timeout: TimeInterval) { self.timeout = timeout }

  func start() {
    let thread = Thread { [self] in
      while true {
        Thread.sleep(forTimeInterval: 0.25)
        lock.lock()
        let active = running
        let elapsed = ProcessInfo.processInfo.systemUptime - startedAt
        let current = label
        let bytes = data
        lock.unlock()
        if !active { return }
        if elapsed > timeout {
          let path = NativeSwiftFuzzTests.persist(bytes, label: current)
          FileHandle.standardError.write(
            Data(
              "native Swift fuzz hang: \(current) exceeded \(timeout)s\n  bytes: \(path)\n"
                .utf8))
          exit(1)
        }
      }
    }
    thread.stackSize = 1 << 20
    thread.start()
  }

  func begin(label: String, data: Data) {
    lock.lock()
    self.label = label
    self.data = data
    startedAt = ProcessInfo.processInfo.systemUptime
    lock.unlock()
  }

  func stop() {
    lock.lock()
    running = false
    lock.unlock()
  }
}
