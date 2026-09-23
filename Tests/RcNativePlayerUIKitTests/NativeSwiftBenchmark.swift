import Darwin
import Foundation
import RcNativePlayerCore
import Testing

/// Host-independent performance evidence for the pure-Swift document core.
///
/// The simulator evidence run measures the UIKit view tree; this measures what both Apple renderers
/// sit on, so a regression in decode, frame resolution, retained-session reuse, or document
/// replacement is attributable to the core rather than to a host. Output is machine-readable and
/// carries the budgets it was judged against, so a run is reviewable without rerunning it.
///
/// Timing on a hosted runner is noisy, so the timing budgets are order-of-magnitude ceilings meant
/// to catch a real regression, not a tuning gate. The structural numbers — node and command
/// counts, retained-frame allocation growth — are the parts that hold exactly.
///
/// Timing only means something in an optimized build, so this runs only when
/// `RC_NATIVE_CORE_BENCHMARK_OUTPUT` names the report to write; `scripts/measure-native-swift-core.sh`
/// sets it and builds the release configuration. An ordinary `swift test` skips it.
@Suite struct NativeSwiftBenchmark {
  private struct FixtureReport: Codable {
    let id: String
    let bytes: Int
    let nodeCount: Int
    let drawCommandCount: Int
    let medianDecodeMilliseconds: Double
    let medianFirstFrameMilliseconds: Double
    let medianSteadyFrameMilliseconds: Double
    let medianReplacementMilliseconds: Double
    let retainedFrameResidentByteGrowth: Int64
  }

  private struct Budgets: Codable {
    let decodeMilliseconds: Double
    let firstFrameMilliseconds: Double
    let steadyFrameMilliseconds: Double
    let replacementMilliseconds: Double
    let retainedFrameResidentByteGrowth: Int64
  }

  private struct Report: Codable {
    let schemaVersion: Int
    let sourceRevision: String
    let iterations: Int
    let frames: Int
    let budgets: Budgets
    let fixtures: [FixtureReport]
    let overBudget: [String]
    let passed: Bool
  }

  private static let iterations = 9
  private static let frames = 120
  private static let budgets = Budgets(
    decodeMilliseconds: 50,
    firstFrameMilliseconds: 50,
    // 8ms keeps a 120-frame second inside half a frame budget on a 60Hz host before any drawing.
    steadyFrameMilliseconds: 8,
    replacementMilliseconds: 100,
    retainedFrameResidentByteGrowth: 16 * 1024 * 1024)

  static let outputPath =
    ProcessInfo.processInfo.environment["RC_NATIVE_CORE_BENCHMARK_OUTPUT"]

  @Test(
    .enabled(
      if: NativeSwiftBenchmark.outputPath != nil,
      "set RC_NATIVE_CORE_BENCHMARK_OUTPUT, or run scripts/measure-native-swift-core.sh"))
  func coreBenchmark() throws {
    let output = try #require(Self.outputPath)

    var fixtures: [FixtureReport] = []
    for name in NativeTestFixtures.comparativeDocuments {
      fixtures.append(try Self.measure(NativeTestFixtures.url(name)))
    }

    var overBudget: [String] = []
    for fixture in fixtures {
      func check(_ metric: String, _ value: Double, _ budget: Double) {
        if value > budget { overBudget.append("\(fixture.id).\(metric)=\(value) > \(budget)") }
      }
      let budgets = Self.budgets
      check("decode", fixture.medianDecodeMilliseconds, budgets.decodeMilliseconds)
      check("firstFrame", fixture.medianFirstFrameMilliseconds, budgets.firstFrameMilliseconds)
      check("steadyFrame", fixture.medianSteadyFrameMilliseconds, budgets.steadyFrameMilliseconds)
      check("replacement", fixture.medianReplacementMilliseconds, budgets.replacementMilliseconds)
      if fixture.retainedFrameResidentByteGrowth > budgets.retainedFrameResidentByteGrowth {
        overBudget.append(
          "\(fixture.id).retainedFrameResidentByteGrowth="
            + "\(fixture.retainedFrameResidentByteGrowth)")
      }
    }

    let report = Report(
      schemaVersion: 1,
      sourceRevision: ProcessInfo.processInfo.environment["RC_SOURCE_REVISION"] ?? "unknown",
      iterations: Self.iterations,
      frames: Self.frames,
      budgets: Self.budgets,
      fixtures: fixtures,
      overBudget: overBudget,
      passed: overBudget.isEmpty)

    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    try encoder.encode(report).write(to: URL(fileURLWithPath: output), options: .atomic)

    for fixture in fixtures {
      print(
        String(
          format: "%@: decode %.2fms, first frame %.2fms, steady frame %.3fms, replace %.2fms",
          fixture.id, fixture.medianDecodeMilliseconds, fixture.medianFirstFrameMilliseconds,
          fixture.medianSteadyFrameMilliseconds, fixture.medianReplacementMilliseconds))
    }
    #expect(
      report.passed,
      "native Swift core benchmark over budget:\n  \(overBudget.joined(separator: "\n  "))")
    print("native Swift core benchmark: \(output)")
  }

  private static func measure(_ url: URL) throws -> FixtureReport {
    let data = try Data(contentsOf: url)
    var decodeSamples: [Double] = []
    var firstFrameSamples: [Double] = []
    var replacementSamples: [Double] = []

    for _ in 0..<iterations {
      var started = ProcessInfo.processInfo.systemUptime
      let session = try NativeSwiftDocumentSession.open(data: data)
      decodeSamples.append(milliseconds(since: started))

      started = ProcessInfo.processInfo.systemUptime
      _ = try session.snapshot()
      firstFrameSamples.append(milliseconds(since: started))

      // Document replacement: a host that swaps documents pays decode plus first frame every time,
      // and must not retain the previous session to do it.
      started = ProcessInfo.processInfo.systemUptime
      _ = try NativeSwiftDocumentSession.open(data: data).snapshot()
      replacementSamples.append(milliseconds(since: started))
    }

    // Steady state: one retained session resolving a second of animation frames.
    let session = try NativeSwiftDocumentSession.open(data: data)
    let snapshot = try session.snapshot()
    var steadySamples: [Double] = []
    let residentBefore = residentBytes()
    for frame in 0..<frames {
      let started = ProcessInfo.processInfo.systemUptime
      _ = try session.snapshot(timeSeconds: TimeInterval(frame) / 60)
      steadySamples.append(milliseconds(since: started))
    }
    let residentAfter = residentBytes()

    var nodes = 0
    var commands = 0
    count(snapshot.root, nodes: &nodes, commands: &commands)

    return FixtureReport(
      id: url.deletingPathExtension().lastPathComponent,
      bytes: data.count,
      nodeCount: nodes,
      drawCommandCount: commands,
      medianDecodeMilliseconds: median(decodeSamples),
      medianFirstFrameMilliseconds: median(firstFrameSamples),
      medianSteadyFrameMilliseconds: median(steadySamples),
      medianReplacementMilliseconds: median(replacementSamples),
      retainedFrameResidentByteGrowth: Int64(residentAfter) - Int64(residentBefore))
  }

  private static func count(
    _ node: NativeSwiftNodeSnapshot, nodes: inout Int, commands: inout Int
  ) {
    nodes += 1
    commands += node.commands.count
    for child in node.children { count(child, nodes: &nodes, commands: &commands) }
  }

  private static func milliseconds(since start: TimeInterval) -> Double {
    (ProcessInfo.processInfo.systemUptime - start) * 1000
  }

  private static func median(_ samples: [Double]) -> Double {
    guard !samples.isEmpty else { return 0 }
    let sorted = samples.sorted()
    return sorted[sorted.count / 2]
  }

  private static func residentBytes() -> UInt64 {
    var information = task_vm_info_data_t()
    var count = mach_msg_type_number_t(
      MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
    let result = withUnsafeMutablePointer(to: &information) { pointer in
      pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
        task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
      }
    }
    return result == KERN_SUCCESS ? UInt64(information.phys_footprint) : 0
  }
}
