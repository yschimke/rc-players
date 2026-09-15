import Darwin
import RcComposePlayer
import SwiftUI
import UIKit

private struct NativePlayerEvidenceReport: Codable {
  struct Metrics: Codable {
    let medianDecodeMilliseconds: Double
    let medianFirstFrameMilliseconds: Double
    let medianUpdateMilliseconds: Double
    let nativeViewCount: Int
    let labelCount: Int
    let controlCount: Int
    let buttonCount: Int
    let accessibilityElementCount: Int
    let exposedAccessibilityElementCount: Int
    let allocatedByteDelta: UInt64
    let residentByteDelta: UInt64
    let executableBytes: UInt64
    let appBundleBytes: UInt64
  }

  struct Budgets: Codable {
    let decodeMilliseconds: Double
    let firstFrameMilliseconds: Double
    let updateMilliseconds: Double
    let nativeViewCount: Int
    let minimumLabelCount: Int
    let minimumControlCount: Int
    let minimumButtonCount: Int
    let minimumAccessibilityElementCount: Int
    let expectedExposedAccessibilityElementCount: Int
    let allocatedByteDelta: UInt64
    let residentByteDelta: UInt64
    let executableBytes: UInt64
    let appBundleBytes: UInt64
  }

  struct Lifecycle: Codable {
    let resumedAfterBackground: Bool
    let loadedSubviewCount: Int
    let releasedAfterResume: Bool
  }

  let schemaVersion: Int
  let fixture: String
  let sourceRevision: String
  let device: String
  let systemVersion: String
  let iterations: Int
  let metrics: Metrics
  let budgets: Budgets
  let lifecycle: Lifecycle
  let passed: Bool
}

@MainActor
private final class WeakNativePlayerReference {
  weak var value: RemoteComposeNativePlayerView?
}

struct NativePlayerEvidenceView: View {
  @State private var status = "Measuring native UIKit player…"

  var body: some View {
    Text(status)
      .font(.headline)
      .padding(32)
      .task {
        do {
          let report = try await NativePlayerEvidence.measure()
          let encoder = JSONEncoder()
          encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
          let data = try encoder.encode(report)
          let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
          try data.write(
            to: directory.appendingPathComponent("native-uikit-evidence.json"), options: .atomic)
          status =
            report.passed ? "Native UIKit evidence: pass" : "Native UIKit evidence: over budget"
        } catch {
          let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
          let message = String(describing: error)
          try? Data(message.utf8).write(
            to: directory.appendingPathComponent("native-uikit-evidence-error.txt"),
            options: .atomic)
          status = "Native UIKit evidence failed: \(error.localizedDescription)"
        }
      }
  }
}

@MainActor
private enum NativePlayerEvidence {
  private static let fixture = "TitleCardRemote-640x480"
  private static let iterations = 7
  private static let budgets = NativePlayerEvidenceReport.Budgets(
    decodeMilliseconds: 250,
    firstFrameMilliseconds: 250,
    updateMilliseconds: 100,
    nativeViewCount: 500,
    minimumLabelCount: 2,
    minimumControlCount: 1,
    minimumButtonCount: 1,
    minimumAccessibilityElementCount: 3,
    expectedExposedAccessibilityElementCount: 1,
    allocatedByteDelta: 32 * 1024 * 1024,
    residentByteDelta: 64 * 1024 * 1024,
    executableBytes: 200 * 1024 * 1024,
    appBundleBytes: 300 * 1024 * 1024)

  static func measure() async throws -> NativePlayerEvidenceReport {
    guard let url = Bundle.main.url(forResource: fixture, withExtension: "rc") else {
      throw CocoaError(.fileNoSuchFile)
    }
    let data = try Data(contentsOf: url)
    let allocatedBefore = allocatedBytes()
    let residentBefore = residentBytes()
    var decodeSamples: [Double] = []
    var firstFrameSamples: [Double] = []
    var updateSamples: [Double] = []
    var measuredView: NativeDocumentView?

    for _ in 0..<iterations {
      var started = ProcessInfo.processInfo.systemUptime
      let session = try NativeSwiftDocumentSession.open(data: data)
      decodeSamples.append(milliseconds(since: started))

      started = ProcessInfo.processInfo.systemUptime
      let snapshot = try session.snapshot()
      let document = try NativeDocument(
        frame: NativeSnapshotSessionHandle.Frame(snapshot: snapshot), limits: .default)
      let cache = NativeImageCache(
        countLimit: RemoteComposeNativeResourceLimits.default.maximumResourceCount,
        totalCostLimit: RemoteComposeNativeResourceLimits.default.maximumDecodedImageBytes)
      let resources = try NativeResourceStore(
        resources: document.images,
        fonts: document.fonts,
        limits: .default,
        cache: cache)
      let customComponents = RemoteComposeNativeCustomComponentRegistry()
      let view = NativeDocumentView(
        document: document, resources: resources,
        customComponents: customComponents, onClick: { _ in },
        onCustomReturn: { _, _, _ in })
      view.frame = CGRect(origin: .zero, size: document.size)
      view.layoutIfNeeded()
      firstFrameSamples.append(milliseconds(since: started))

      started = ProcessInfo.processInfo.systemUptime
      let updated = try session.snapshot()
      let updatedDocument = try NativeDocument(
        frame: NativeSnapshotSessionHandle.Frame(snapshot: updated), limits: .default)
      _ = view.update(
        document: updatedDocument, resources: resources, customComponents: customComponents)
      view.layoutIfNeeded()
      updateSamples.append(milliseconds(since: started))
      measuredView = view
    }

    guard let measuredView else { throw CocoaError(.coderInvalidValue) }
    let hierarchy = hierarchyMetrics(measuredView)
    let executableBytes = try executableSize()
    let appBundleBytes = try bundleSize()
    let allocatedAfter = allocatedBytes()
    let residentAfter = residentBytes()
    let lifecycle = try await lifecycleEvidence(data: data)
    let metrics = NativePlayerEvidenceReport.Metrics(
      medianDecodeMilliseconds: median(decodeSamples),
      medianFirstFrameMilliseconds: median(firstFrameSamples),
      medianUpdateMilliseconds: median(updateSamples),
      nativeViewCount: hierarchy.views,
      labelCount: hierarchy.labels,
      controlCount: hierarchy.controls,
      buttonCount: hierarchy.buttons,
      accessibilityElementCount: hierarchy.accessibilityElements,
      exposedAccessibilityElementCount: measuredView.accessibilityElements?.count ?? 0,
      allocatedByteDelta: allocatedAfter >= allocatedBefore ? allocatedAfter - allocatedBefore : 0,
      residentByteDelta: residentAfter >= residentBefore ? residentAfter - residentBefore : 0,
      executableBytes: executableBytes,
      appBundleBytes: appBundleBytes)
    let passed =
      metrics.medianDecodeMilliseconds <= budgets.decodeMilliseconds
      && metrics.medianFirstFrameMilliseconds <= budgets.firstFrameMilliseconds
      && metrics.medianUpdateMilliseconds <= budgets.updateMilliseconds
      && metrics.nativeViewCount <= budgets.nativeViewCount
      && metrics.labelCount >= budgets.minimumLabelCount
      && metrics.controlCount >= budgets.minimumControlCount
      && metrics.buttonCount >= budgets.minimumButtonCount
      && metrics.accessibilityElementCount >= budgets.minimumAccessibilityElementCount
      && metrics.exposedAccessibilityElementCount
        == budgets.expectedExposedAccessibilityElementCount
      && metrics.allocatedByteDelta <= budgets.allocatedByteDelta
      && metrics.residentByteDelta <= budgets.residentByteDelta
      && metrics.executableBytes <= budgets.executableBytes
      && metrics.appBundleBytes <= budgets.appBundleBytes && lifecycle.resumedAfterBackground
      && lifecycle.releasedAfterResume
    return NativePlayerEvidenceReport(
      schemaVersion: 1,
      fixture: fixture,
      sourceRevision: ProcessInfo.processInfo.environment["RC_SOURCE_REVISION"] ?? "unknown",
      device: ProcessInfo.processInfo.environment["RC_EVIDENCE_DEVICE"] ?? UIDevice.current.model,
      systemVersion: UIDevice.current.systemVersion,
      iterations: iterations,
      metrics: metrics,
      budgets: budgets,
      lifecycle: lifecycle,
      passed: passed)
  }

  private static func lifecycleEvidence(
    data: Data
  ) async throws -> NativePlayerEvidenceReport.Lifecycle {
    var player: RemoteComposeNativePlayerView? = RemoteComposeNativePlayerView(data: data)
    player?.frame = CGRect(x: 0, y: 0, width: 640, height: 480)

    // Interrupt the initial asynchronous load. Becoming active must start a clean replacement load.
    NotificationCenter.default.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
    NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)

    let deadline = ProcessInfo.processInfo.systemUptime + 5
    while player?.subviews.count == 1, ProcessInfo.processInfo.systemUptime < deadline {
      try await Task.sleep(nanoseconds: 10_000_000)
    }
    let loadedSubviewCount = player?.subviews.count ?? 0
    let resumedAfterBackground = loadedSubviewCount > 1

    let reference = WeakNativePlayerReference()
    reference.value = player
    player = nil
    for _ in 0..<100 where reference.value != nil {
      await Task.yield()
      try await Task.sleep(nanoseconds: 1_000_000)
    }

    return NativePlayerEvidenceReport.Lifecycle(
      resumedAfterBackground: resumedAfterBackground,
      loadedSubviewCount: loadedSubviewCount,
      releasedAfterResume: reference.value == nil)
  }

  private static func milliseconds(since start: TimeInterval) -> Double {
    (ProcessInfo.processInfo.systemUptime - start) * 1_000
  }

  private static func median(_ values: [Double]) -> Double {
    let sorted = values.sorted()
    return sorted[sorted.count / 2]
  }

  private static func hierarchyMetrics(
    _ root: UIView
  ) -> (views: Int, labels: Int, controls: Int, buttons: Int, accessibilityElements: Int) {
    var pending = [root]
    var views = 0
    var labels = 0
    var controls = 0
    var buttons = 0
    var accessibilityElements = 0
    while let view = pending.popLast() {
      views += 1
      if view is UILabel { labels += 1 }
      if view is UIControl { controls += 1 }
      if view is UIButton { buttons += 1 }
      if view.isAccessibilityElement { accessibilityElements += 1 }
      pending.append(contentsOf: view.subviews)
    }
    return (views, labels, controls, buttons, accessibilityElements)
  }

  private static func allocatedBytes() -> UInt64 {
    var statistics = malloc_statistics_t()
    malloc_zone_statistics(malloc_default_zone(), &statistics)
    return UInt64(statistics.size_in_use)
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

  private static func executableSize() throws -> UInt64 {
    guard let executable = Bundle.main.executableURL else { throw CocoaError(.fileNoSuchFile) }
    let attributes = try FileManager.default.attributesOfItem(atPath: executable.path)
    return (attributes[.size] as? NSNumber)?.uint64Value ?? 0
  }

  private static func bundleSize() throws -> UInt64 {
    guard let root = Bundle.main.resourceURL else { throw CocoaError(.fileNoSuchFile) }
    let keys: Set<URLResourceKey> = [.fileSizeKey, .isRegularFileKey]
    guard
      let files = FileManager.default.enumerator(
        at: root, includingPropertiesForKeys: Array(keys), options: [.skipsHiddenFiles])
    else { throw CocoaError(.fileReadUnknown) }
    var total: UInt64 = 0
    for case let file as URL in files {
      let values = try file.resourceValues(forKeys: keys)
      if values.isRegularFile == true { total += UInt64(values.fileSize ?? 0) }
    }
    return total
  }
}
