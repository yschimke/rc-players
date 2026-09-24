import Foundation
import Testing

@_spi(Conformance) @testable import RcNativePlayerCore

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
/// Two mutation families, because they reach different code. Byte-level edits cover truncation and
/// arbitrary corruption, and are overwhelmingly rejected at the header or the first bad opcode —
/// which is the property "malformed input fails closed", already well covered. Structure-aware
/// edits walk the seed with the decoder's own operation spans, then perturb operands, duplicate,
/// drop and swap whole operations, and unbalance container begin/end pairs by one. Those stay
/// walkable, so they reach expression evaluation, layout and resource metadata with hostile
/// content rather than hostile framing. The run prints how many cases decoded so shallow coverage
/// is visible rather than implied, and the gate is a proportion of cases rather than a bare seed
/// count so a corpus that stops reaching the stream fails the run.
///
/// Reproduction is deterministic. `RC_NATIVE_FUZZ_SEED` and `RC_NATIVE_FUZZ_ITERATIONS` select the
/// same case sequence on any host, and a failure writes the offending bytes to
/// `RC_NATIVE_FUZZ_CORPUS_OUT` (default: a temporary directory, printed) before exiting.
///
/// The seeds are the bundled comparative fixtures in `NativeTestFixtures.comparativeDocuments`
/// order, after the synthetic seeds. Each seed's PRNG stream is keyed by its position, so that
/// order is part of the corpus: `minimumDecodedRatio` was measured against it.
@Suite struct NativeSwiftFuzzTests {
  private static let defaultIterations = 96
  private static let caseTimeoutSeconds = 10.0
  /// The proportion of cases that must decode for the run to pass.
  ///
  /// Measured rather than guessed: with structure-aware edits in the mix the corpus decodes around
  /// 20% of cases, so the floor sits below that to leave room for host and fixture drift while
  /// still failing a corpus that dies at the header.
  private static let minimumDecodedRatio = 0.12

  /// The stack the corpus runs on, the size of a main thread's.
  ///
  /// swift-testing runs a test on a cooperative-pool thread, whose stack is 512 KiB on macOS. The
  /// address-sanitized debug build this suite also runs under roughly doubles the decoder's frame
  /// (from about 103 KiB to 204 KiB on x86-64, and more on arm64), and that overflowed the pool
  /// thread. A workqueue thread has no alternate signal stack for the sanitizer's handler, so the
  /// process died with SIGILL and no report. On a thread of its own the build configuration no
  /// longer decides whether the fuzzer can run.
  private static let corpusStackBytes = 8 << 20

  @Test func mutationFuzz() async throws {
    var seeds: [(name: String, data: Data)] = Self.syntheticSeeds()
    for name in NativeTestFixtures.comparativeDocuments {
      seeds.append((name: name, data: try NativeTestFixtures.data(name)))
    }

    let iterations = max(
      Self.environmentInt("RC_NATIVE_FUZZ_ITERATIONS") ?? Self.defaultIterations, 0)
    let seedValue = UInt64(
      bitPattern: Int64(Self.environmentInt("RC_NATIVE_FUZZ_SEED") ?? 0x5EED))
    let corpus = seeds
    let run = await withCheckedContinuation { continuation in
      let worker = Thread {
        continuation.resume(
          returning: Self.runCorpus(corpus, iterations: iterations, seedValue: seedValue))
      }
      worker.stackSize = Self.corpusStackBytes
      worker.start()
    }

    // Bounded work, measured over the whole run so one slow host does not decide a single case.
    let budget = Double(run.cases) * 0.25
    #expect(
      run.elapsed < budget,
      "fuzzing \(run.cases) cases took \(run.elapsed)s, over the \(budget)s bounded-work budget")
    #expect(
      Double(run.decoded) >= Double(run.cases) * Self.minimumDecodedRatio,
      Comment(
        rawValue: "only \(run.decoded) of \(run.cases) cases decoded; the corpus is dying at the "
          + "header instead of reaching the operation stream"))
    #expect(run.rejected > 0, "no fuzz case was rejected; the mutations are not reaching the core")
    print(
      "native Swift fuzz: \(run.cases) cases, \(run.structuralCases) structure-aware, "
        + "\(run.decoded) decoded, \(run.rejected) typed rejections, "
        + "\(String(format: "%.2f", run.elapsed))s, seed=\(seedValue)")
  }

  /// What one pass over the corpus did.
  private struct CorpusRun: Sendable {
    var cases = 0
    var structuralCases = 0
    var decoded = 0
    var rejected = 0
    var elapsed = 0.0
  }

  /// Every seed, pristine and then mutated. A contract violation never returns: `report` writes
  /// the case out and exits, so this records no test issues and runs on any thread.
  private static func runCorpus(
    _ seeds: [(name: String, data: Data)], iterations: Int, seedValue: UInt64
  ) -> CorpusRun {
    let watchdog = Watchdog(timeout: Self.caseTimeoutSeconds)
    watchdog.start()

    var decoded = 0
    var rejected = 0
    var cases = 0
    var structuralCases = 0
    let started = ProcessInfo.processInfo.systemUptime

    for (index, seed) in seeds.enumerated() {
      // The pristine seed first: a supported fixture must stay supported, and a synthetic
      // malformed seed must stay typed.
      watchdog.begin(label: "\(seed.name)#pristine", data: seed.data)
      Self.exercise(
        seed.data, label: "\(seed.name)#pristine", decoded: &decoded, rejected: &rejected)
      cases += 1

      // The operation spans the decoder itself walked, so the mutator and the decoder cannot
      // disagree about framing. A seed the decoder refuses has none, and stays byte-level only.
      // An operation replayed by a macro, loop or branch expansion is spanned in the expanded
      // bytes, not in the seed; only the in-order spans that lie in the seed itself are its own.
      var spans: [NativeSwiftOperationSpan] = []
      for span in (try? NativeSwiftDocumentSession.operationSpans(in: seed.data)) ?? []
      where span.offset >= (spans.last?.endOffset ?? 0) && span.endOffset > span.offset
        && span.endOffset <= seed.data.count
      {
        spans.append(span)
      }
      var random = SplitMix64(seed: seedValue &+ UInt64(index) &* 0x9E37_79B9_7F4A_7C15)
      for iteration in 0..<iterations {
        let label = "\(seed.name)#\(iteration)"
        let mutant: Data
        if !spans.isEmpty, random.next(upperBound: 2) == 1 {
          mutant = Self.structuralMutation(seed.data, spans: spans, using: &random)
          structuralCases += 1
        } else {
          mutant = Self.mutate(seed.data, using: &random)
        }
        watchdog.begin(label: label, data: mutant)
        Self.exercise(mutant, label: label, decoded: &decoded, rejected: &rejected)
        cases += 1
      }
    }
    watchdog.stop()
    return CorpusRun(
      cases: cases, structuralCases: structuralCases, decoded: decoded, rejected: rejected,
      elapsed: ProcessInfo.processInfo.systemUptime - started)
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
      if error.description.isEmpty {
        report(data, label: label, reason: "typed decode failure without a description")
      }
      rejected += 1
      return
    } catch {
      report(data, label: label, reason: "untyped decode failure: \(error)")
    }

    do {
      let snapshot = try session.snapshot()
      try validate(snapshot, label: label, data: data)
      var computeBudget = 64
      evaluateLayoutComputes(in: snapshot.root, budget: &computeBudget)

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
        _ = session.returnCustomText("fuzz", componentID: componentID, propertyID: 2)
        _ = session.returnCustomFloat(1.5, componentID: componentID, propertyID: 1)
        _ = session.returnCustomFloat(.nan, componentID: componentID, propertyID: 1)
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
      if error.description.isEmpty {
        report(data, label: label, reason: "typed frame failure without a description")
      }
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
      (name: "layout-compute-macro-block", data: layoutComputeAndMacroBlockSeed()),
    ]
  }

  /// A box with a LayoutCompute body, a stray MacroBlock, and a macro forwarding a MacroBlock to a
  /// nested call: none of the bundled fixtures carries these, and mutations should reach them.
  private static func layoutComputeAndMacroBlockSeed() -> Data {
    typealias Op = NativeSwiftWireOpcode
    var bytes: [UInt8] = []
    /// One operation: its opcode byte, then each operand as a big-endian word.
    func emit(_ opcode: Int, _ operands: [Int] = []) {
      bytes.append(UInt8(truncatingIfNeeded: opcode))
      for operand in operands {
        let raw = UInt32(bitPattern: Int32(truncatingIfNeeded: operand))
        for shift: UInt32 in [24, 16, 8, 0] {
          bytes.append(UInt8(truncatingIfNeeded: raw >> shift))
        }
      }
    }
    func float(_ value: Float) -> Int { Int(Int32(bitPattern: value.bitPattern)) }
    func reference(_ id: Int) -> Int { Int(Int32(bitPattern: 0xff80_0000 | UInt32(id))) }
    func floatOperator(_ operation: Int) -> Int { reference(0x0031_0000 + operation) }
    let bounds = NativeSwiftIDRegion.array + 42
    let end = Op.containerEnd
    emit(Op.header, [1, 0, 0, 100, 100, 0, 0])
    emit(Op.macroBlock, [3])
    emit(Op.drawRect, [0, 0, 4, 4].map(float))
    emit(end)
    emit(Op.macroDefine, [41, 1, 101, 0])
    emit(Op.macroArgument, [0])
    emit(Op.drawRect, [1, 1, 2, 2].map(float))
    emit(end)
    emit(Op.macroDefine, [40, 1, 100, 0])
    emit(Op.macroCall, [41, 1, 100])
    emit(Op.macroBlock, [0])
    emit(Op.drawRect, [3, 3, 6, 6].map(float))
    emit(end)
    emit(end)
    emit(end)
    emit(Op.layoutRoot, [1])
    emit(Op.layoutBox, [2, -1, 1, 1])
    emit(Op.layoutCompute, [NativeSwiftLayoutComputeType.measure, bounds])
    bytes.append(1)  // animateChanges
    emit(Op.dynamicFloatList, [bounds, float(6)])
    emit(
      Op.animatedFloat,
      [
        50, 5, reference(bounds), float(4),
        floatOperator(NativeSwiftFloatOperator.arrayDeref), float(2),
        floatOperator(NativeSwiftFloatOperator.div),
      ])
    emit(Op.updateDynamicFloatList, [bounds, float(2), reference(50)])
    emit(end)
    emit(Op.layoutCanvas, [3, -1])
    emit(Op.macroCall, [40, 1, 200])
    emit(end)
    emit(end)
    emit(end)
    emit(end)
    return Data(bytes)
  }

  /// Runs every LayoutCompute the tree carries over ordinary and hostile boxes, as a host's layout
  /// pass would: a computation may decline, never trap.
  private static func evaluateLayoutComputes(in node: NativeSwiftNodeSnapshot, budget: inout Int) {
    guard budget > 0 else { return }
    for compute in node.layoutComputes {
      budget -= 1
      for value: Float in [0, 12.5, -3, .nan, .infinity, .greatestFiniteMagnitude] {
        _ = compute.evaluate(
          x: value, y: 1, width: value, height: 2, parentWidth: 100, parentHeight: value)
      }
    }
    for child in node.children { evaluateLayoutComputes(in: child, budget: &budget) }
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

  // MARK: - Structure-aware corpus

  /// Where a structure-aware edit can land.
  ///
  /// The point of each is that the result stays walkable: an operation is self-delimiting, so
  /// duplicating, dropping or reordering whole operations leaves the framing intact and makes the
  /// decoder judge the *content*. Only the container pair is deliberately unbalanced.
  private enum StructuralMutation: CaseIterable {
    case perturbOperand, duplicateOperation, dropOperation, swapOperations, unbalanceContainers
  }

  /// The words an operand is perturbed toward: counts, lengths, ids and floats all read one of
  /// these as hostile rather than as a typo.
  private static let boundaryWords: [[UInt8]] = [
    [0x00, 0x00, 0x00, 0x00],  // zero / empty
    [0xFF, 0xFF, 0xFF, 0xFF],  // -1, or Int32.max as an unsigned count
    [0x7F, 0xFF, 0xFF, 0xFF],  // Int32.max
    [0x80, 0x00, 0x00, 0x00],  // Int32.min / -0
    [0x7F, 0x80, 0x00, 0x00],  // +infinity
    [0xFF, 0x80, 0x00, 0x00],  // -infinity
    [0x7F, 0xC0, 0x00, 0x00],  // NaN
    [0x00, 0x00, 0x00, 0x01],  // one
    [0x00, 0x01, 0x00, 0x00],  // 65536
    [0x00, 0x00, 0xFF, 0xFF],  // 65535
  ]

  private static let containerEndOpcode = 214
  private static let containerStartOpcodes: Set<Int> = [
    176, 200, 201, 202, 203, 204, 205, 207, 208, 217, 230, 233, 234, 239, 240,
  ]

  private static func structuralMutation(
    _ seed: Data, spans: [NativeSwiftOperationSpan], using random: inout SplitMix64
  ) -> Data {
    var bytes = [UInt8](seed)
    guard !spans.isEmpty else { return Data(bytes) }
    let mutation = StructuralMutation.allCases[
      Int(random.next(upperBound: UInt64(StructuralMutation.allCases.count)))]
    switch mutation {
    case .perturbOperand:
      // An operand word, never the opcode byte, and only inside an operation that has one.
      let candidates = spans.filter { $0.byteCount >= 5 }
      guard let span = candidates.randomElement(using: &random) else { return Data(bytes) }
      let operandStart = span.offset + 1
      let wordCount = (span.endOffset - operandStart) / 4
      guard wordCount > 0 else { return Data(bytes) }
      let start = operandStart + Int(random.next(upperBound: UInt64(wordCount))) * 4
      let word = boundaryWords[Int(random.next(upperBound: UInt64(boundaryWords.count)))]
      bytes.replaceSubrange(start..<(start + 4), with: word)
    case .duplicateOperation:
      let span = spans[Int(random.next(upperBound: UInt64(spans.count)))]
      bytes.insert(contentsOf: bytes[span.offset..<span.endOffset], at: span.endOffset)
    case .dropOperation:
      let span = spans[Int(random.next(upperBound: UInt64(spans.count)))]
      bytes.removeSubrange(span.offset..<span.endOffset)
    case .swapOperations:
      guard spans.count >= 2 else { return Data(bytes) }
      let first = Int(random.next(upperBound: UInt64(spans.count)))
      var second = Int(random.next(upperBound: UInt64(spans.count)))
      if second == first { second = (second + 1) % spans.count }
      let low = min(first, second)
      let high = max(first, second)
      let lowRange = spans[low].offset..<spans[low].endOffset
      let highRange = spans[high].offset..<spans[high].endOffset
      let firstBytes = Array(bytes[lowRange])
      let secondBytes = Array(bytes[highRange])
      var swapped = Array(bytes[..<lowRange.lowerBound])
      swapped += secondBytes
      swapped += bytes[lowRange.upperBound..<highRange.lowerBound]
      swapped += firstBytes
      swapped += bytes[highRange.upperBound...]
      bytes = swapped
    case .unbalanceContainers:
      // Drop one end, or duplicate one begin: a pair that is exactly one out of balance is the
      // shape a stack-based decoder is most likely to mishandle.
      if random.next(upperBound: 2) == 0,
        let end = spans.filter({ $0.opcode == containerEndOpcode })
          .randomElement(using: &random)
      {
        bytes.removeSubrange(end.offset..<end.endOffset)
      } else if let start = spans.filter({ containerStartOpcodes.contains($0.opcode) })
        .randomElement(using: &random)
      {
        bytes.insert(contentsOf: bytes[start.offset..<start.endOffset], at: start.endOffset)
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
private struct SplitMix64: RandomNumberGenerator {
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
