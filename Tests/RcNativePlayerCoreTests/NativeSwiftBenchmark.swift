#if canImport(Darwin)
  import Darwin
#endif
import Foundation
@testable import RcNativePlayerCore
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

  private struct FeedArrivalReport: Codable {
    let fixture: String
    let cardCount: Int
    let iterations: Int
    let medianTotalMilliseconds: Double
    let medianPerCardMilliseconds: Double
  }

  private struct HostValueUpdateReport: Codable {
    let fixture: String
    let mutationsPerUpdate: Int
    let iterations: Int
    let medianMilliseconds: Double
  }

  private struct StaticWallClockReport: Codable {
    let fixture: String
    let frames: Int
    let medianFrameMilliseconds: Double
  }

  private struct Scenarios: Codable {
    let feedArrival: FeedArrivalReport
    let hostValueSingle: HostValueUpdateReport
    let hostValueBurst: HostValueUpdateReport
    let staticWallClockReuse: StaticWallClockReport
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
    let syntheticFixtures: [FixtureReport]
    let scenarios: Scenarios
    let overBudget: [String]
    let passed: Bool
  }

  private static let iterations = 9
  private static let frames = 120
  private static let feedCardCount = 20
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
      let url = try NativeTestFixtures.url(name)
      fixtures.append(
        try Self.measure(
          id: url.deletingPathExtension().lastPathComponent,
          data: try Data(contentsOf: url)))
    }

    let denseCardData = Self.makeDenseFeedCard()
    let denseCardReport = try Self.measure(id: "synthetic-dense-feed-card", data: denseCardData)
    let syntheticFixtures = [denseCardReport]

    let scenarios = Scenarios(
      feedArrival: try Self.measureFeedArrival(
        id: "synthetic-dense-feed-card", data: denseCardData, cardCount: Self.feedCardCount),
      hostValueSingle: try Self.measureHostValueUpdates(
        id: "synthetic-dense-feed-card", data: denseCardData, mutationsPerUpdate: 1),
      hostValueBurst: try Self.measureHostValueUpdates(
        id: "synthetic-dense-feed-card", data: denseCardData, mutationsPerUpdate: 10),
      staticWallClockReuse: try Self.measureStaticWallClockReuse(
        id: "synthetic-dense-feed-card", data: denseCardData))

    var overBudget: [String] = []
    for fixture in fixtures + syntheticFixtures {
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
      syntheticFixtures: syntheticFixtures,
      scenarios: scenarios,
      overBudget: overBudget,
      passed: overBudget.isEmpty)

    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    try encoder.encode(report).write(to: URL(fileURLWithPath: output), options: .atomic)

    for fixture in fixtures + syntheticFixtures {
      print(
        String(
          format:
            "%@ (%d nodes): decode %.3fms, first frame %.3fms, steady frame %.4fms, replace %.3fms",
          fixture.id, fixture.nodeCount, fixture.medianDecodeMilliseconds,
          fixture.medianFirstFrameMilliseconds, fixture.medianSteadyFrameMilliseconds,
          fixture.medianReplacementMilliseconds))
    }
    print(
      String(
        format:
          "scenarios: feed(%d)=%.3fms (%.3fms/card), hostSingle=%.4fms, hostBurst(10)=%.4fms, staticWallClock=%.4fms",
        scenarios.feedArrival.cardCount, scenarios.feedArrival.medianTotalMilliseconds,
        scenarios.feedArrival.medianPerCardMilliseconds,
        scenarios.hostValueSingle.medianMilliseconds,
        scenarios.hostValueBurst.medianMilliseconds,
        scenarios.staticWallClockReuse.medianFrameMilliseconds))
    #expect(
      report.passed,
      "native Swift core benchmark over budget:\n  \(overBudget.joined(separator: "\n  "))")
    print("native Swift core benchmark: \(output)")
  }

  private static func measure(id: String, data: Data) throws -> FixtureReport {
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
      id: id,
      bytes: data.count,
      nodeCount: nodes,
      drawCommandCount: commands,
      medianDecodeMilliseconds: median(decodeSamples),
      medianFirstFrameMilliseconds: median(firstFrameSamples),
      medianSteadyFrameMilliseconds: median(steadySamples),
      medianReplacementMilliseconds: median(replacementSamples),
      retainedFrameResidentByteGrowth: Int64(residentAfter) - Int64(residentBefore))
  }

  private static func measureFeedArrival(
    id: String, data: Data, cardCount: Int
  ) throws -> FeedArrivalReport {
    var totals: [Double] = []
    for _ in 0..<iterations {
      let started = ProcessInfo.processInfo.systemUptime
      for card in 0..<cardCount {
        let session = try NativeSwiftDocumentSession.open(data: data)
        _ = session.setString("Session \(card)", for: "USER:title_0")
        _ = session.setFloat(Float(card % 5) * 20, for: "USER:metric_0")
        _ = try session.snapshot()
      }
      totals.append(milliseconds(since: started))
    }
    let total = median(totals)
    return FeedArrivalReport(
      fixture: id,
      cardCount: cardCount,
      iterations: iterations,
      medianTotalMilliseconds: total,
      medianPerCardMilliseconds: total / Double(max(cardCount, 1)))
  }

  private static func measureHostValueUpdates(
    id: String, data: Data, mutationsPerUpdate: Int
  ) throws -> HostValueUpdateReport {
    let session = try NativeSwiftDocumentSession.open(data: data)
    _ = try session.snapshot()
    var samples: [Double] = []
    let updateIterations = 30
    for step in 0..<updateIterations {
      let started = ProcessInfo.processInfo.systemUptime
      for slot in 0..<mutationsPerUpdate {
        if slot % 2 == 0 {
          let index = (slot / 2) % 5
          _ = session.setString("Updated \(step)-\(slot)", for: "USER:title_\(index)")
        } else {
          let index = (slot / 2) % 5
          _ = session.setFloat(Float((step + slot) % 100), for: "USER:metric_\(index)")
        }
      }
      _ = try session.snapshot(timeSeconds: TimeInterval(step) / 60)
      samples.append(milliseconds(since: started))
    }
    return HostValueUpdateReport(
      fixture: id,
      mutationsPerUpdate: mutationsPerUpdate,
      iterations: updateIterations,
      medianMilliseconds: median(samples))
  }

  private static func measureStaticWallClockReuse(
    id: String, data: Data
  ) throws -> StaticWallClockReport {
    let session = try NativeSwiftDocumentSession.open(data: data)
    let wallClock = NativeSwiftWallClock(
      epochMillis: 1_700_000_000_000, offsetSeconds: 0, timeZone: TimeZone(secondsFromGMT: 0)!)
    _ = try session.snapshot(timeSeconds: 0, wallClock: wallClock)
    var samples: [Double] = []
    let sampleFrames = 60
    for frame in 0..<sampleFrames {
      let started = ProcessInfo.processInfo.systemUptime
      _ = try session.snapshot(timeSeconds: TimeInterval(frame) / 60, wallClock: wallClock)
      samples.append(milliseconds(since: started))
    }
    return StaticWallClockReport(
      fixture: id,
      frames: sampleFrames,
      medianFrameMilliseconds: median(samples))
  }

  /// A realistic ~65-node feed card with 5 named strings, 5 named floats, float expressions,
  /// nested rows/columns, rounded backgrounds, and text labels — matching the density of real
  /// conference/catalog feed cards rather than the tiny 5–15 node comparative fixtures.
  private static func makeDenseFeedCard() -> Data {
    let writer = Writer()
    writer.header(width: 360, height: 180)
    // 5 named string variables + 5 named float variables + 5 derived float expressions.
    for index in 0..<5 {
      let textID = 100 + index
      writer.text(id: textID, "Session Title \(index)")
      writer.namedVariable(
        id: textID, type: NativeSwiftNamedVariableType.string, name: "USER:title_\(index)")
      let floatID = 200 + index
      writer.u8(NativeSwiftWireOpcode.dataFloat).int(floatID).float(Float(16 + index * 10))
      writer.namedVariable(
        id: floatID, type: NativeSwiftNamedVariableType.float, name: "USER:metric_\(index)")
      // Derived width expression: 80 + metric * 2
      let exprID = 300 + index
      writer.u8(NativeSwiftWireOpcode.animatedFloat).int(exprID).int(5)
        .int(Writer.literal(80))
        .int(Writer.nanReference(floatID))
        .int(Writer.literal(2))
        .int(Writer.floatOperator(NativeSwiftFloatOperator.mul))
        .int(Writer.floatOperator(NativeSwiftFloatOperator.add))
    }

    writer.u8(NativeSwiftWireOpcode.layoutRoot).int(1)
    writer.u8(NativeSwiftWireOpcode.layoutColumn).int(10).int(-1).int(1).int(1).float(6)
    writer.u8(NativeSwiftWireOpcode.modifierPadding).float(12).float(12).float(12).float(12)
    writer.u8(NativeSwiftWireOpcode.modifierRoundedClipRect).float(12).float(12).float(12).float(12)
    writer.u8(NativeSwiftWireOpcode.modifierBackground)
      .float(0).float(0).float(0).float(0)
      .float(0.14).float(0.15).float(0.18).float(1)
      .int(0)
    var nextComponentID = 20
    writer.u8(NativeSwiftWireOpcode.layoutContent).int(nextComponentID)
    nextComponentID += 1

    for row in 0..<5 {
      writer.u8(NativeSwiftWireOpcode.layoutRow).int(nextComponentID).int(-1).int(1).int(2).float(8)
      nextComponentID += 1
      writer.u8(NativeSwiftWireOpcode.modifierPadding).float(4).float(4).float(4).float(4)
      writer.u8(NativeSwiftWireOpcode.layoutContent).int(nextComponentID)
      nextComponentID += 1

      // Badge box with derived width
      writer.u8(NativeSwiftWireOpcode.layoutBox).int(nextComponentID).int(-1).int(2).int(2)
      nextComponentID += 1
      writer.u8(NativeSwiftWireOpcode.modifierWidth).int(NativeSwiftDimensionType.exact)
        .int(Writer.nanReference(300 + row))
      writer.u8(NativeSwiftWireOpcode.modifierHeight).int(NativeSwiftDimensionType.exact).float(24)
      writer.u8(NativeSwiftWireOpcode.modifierBackground)
        .float(0).float(0).float(0).float(0)
        .float(0.22).float(0.45).float(0.85).float(1)
        .int(0)
      writer.u8(NativeSwiftWireOpcode.layoutContent).int(nextComponentID)
      nextComponentID += 1
      writer.textLayout(id: nextComponentID, textID: 100 + row, color: 0xFFFF_FFFF, size: 12)
      nextComponentID += 1
      writer.u8(NativeSwiftWireOpcode.containerEnd)  // box content
      writer.u8(NativeSwiftWireOpcode.containerEnd)  // box

      // Two text columns per row
      writer.u8(NativeSwiftWireOpcode.layoutColumn).int(nextComponentID).int(-1).int(1).int(1)
        .float(2)
      nextComponentID += 1
      writer.u8(NativeSwiftWireOpcode.layoutContent).int(nextComponentID)
      nextComponentID += 1
      writer.textLayout(id: nextComponentID, textID: 100 + row, color: 0xFFFF_FFFF, size: 15)
      nextComponentID += 1
      writer.textLayout(
        id: nextComponentID, textID: 100 + ((row + 1) % 5), color: 0xFFB0_B6C4, size: 12)
      nextComponentID += 1
      writer.u8(NativeSwiftWireOpcode.containerEnd)  // column content
      writer.u8(NativeSwiftWireOpcode.containerEnd)  // column

      writer.u8(NativeSwiftWireOpcode.containerEnd)  // row content
      writer.u8(NativeSwiftWireOpcode.containerEnd)  // row
    }

    writer.u8(NativeSwiftWireOpcode.containerEnd)  // root column content
    writer.u8(NativeSwiftWireOpcode.containerEnd)  // root column
    writer.u8(NativeSwiftWireOpcode.containerEnd)  // root
    return writer.data
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
    #if canImport(Darwin)
      var information = task_vm_info_data_t()
      var count = mach_msg_type_number_t(
        MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
      let result = withUnsafeMutablePointer(to: &information) { pointer in
        pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
          task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
        }
      }
      return result == KERN_SUCCESS ? UInt64(information.phys_footprint) : 0
    #else
      return 0
    #endif
  }
}
