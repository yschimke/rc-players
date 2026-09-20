import QuartzCore
import RcComposePlayer
import SwiftUI
import UIKit

/// Simulator-oriented, comparative performance evidence for the two Apple player implementations.
///
/// This deliberately measures both players in the same process, app host, and simulator session.
/// The numbers are useful for catching relative regressions while the native player is evolving,
/// but are not device-performance claims: run the same scenarios through Instruments on physical
/// hardware before setting release budgets.
struct ApplePlayerBenchmarkView: View {
  @State private var status = "Benchmarking Apple players…"

  var body: some View {
    Text(status)
      .font(.headline)
      .padding(32)
      .task {
        do {
          let report = try await ApplePlayerBenchmark.run()
          let encoder = JSONEncoder()
          encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
          let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
          try encoder.encode(report).write(
            to: directory.appendingPathComponent("apple-player-benchmark.json"), options: .atomic)
          status = "Apple player benchmark complete"
        } catch {
          let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
          try? Data(String(describing: error).utf8).write(
            to: directory.appendingPathComponent("apple-player-benchmark-error.txt"), options: .atomic)
          status = "Apple player benchmark failed: \(error.localizedDescription)"
        }
      }
  }
}

/// Minimal gesture host for the UI test lane. XCTest drives this with a real swipe rather than the
/// programmatic scroll used for the repeatable timing sample above.
struct ApplePlayerBenchmarkInteractionView: UIViewControllerRepresentable {
  let player: String

  func makeUIViewController(context: Context) -> BenchmarkInteractionController {
    BenchmarkInteractionController(player: player == "cmp" ? .cmp : .nativeUIKit)
  }

  func updateUIViewController(_ uiViewController: BenchmarkInteractionController, context: Context) {}
}

@MainActor
private enum ApplePlayerBenchmark {
  fileprivate enum Player: String, CaseIterable, Codable { case cmp, nativeUIKit }

  private struct Timing: Codable {
    let medianPlayerStartupMilliseconds: Double
    let medianFirstPresentationMilliseconds: Double
    let animation: Animation
    let scroll: Scroll
  }

  private struct Animation: Codable {
    let samples: Int
    let medianFrameIntervalMilliseconds: Double
    let p95FrameIntervalMilliseconds: Double
    let missedFrames: Int
  }

  private struct Scroll: Codable {
    let medianCompletionMilliseconds: Double
    let samples: Int
  }

  private struct Report: Codable {
    let schemaVersion: Int
    let sourceRevision: String
    let device: String
    let systemVersion: String
    let iterations: Int
    let methodology: String
    let results: [String: Timing]
  }

  private static let iterations = 7
  private static let playerSize = CGSize(width: 640, height: 480)

  static func run() async throws -> Report {
    guard
      let startupURL = Bundle.main.url(forResource: "TitleCardRemote-640x480", withExtension: "rc"),
      let animationURL = Bundle.main.url(
        forResource: "IndeterminateCircularProgress-400x400", withExtension: "rc")
    else { throw CocoaError(.fileNoSuchFile) }
    let startupData = try Data(contentsOf: startupURL)
    let animationData = try Data(contentsOf: animationURL)
    var results: [String: Timing] = [:]
    for player in Player.allCases {
      var startup: [Double] = []
      var firstPresentation: [Double] = []
      for _ in 0..<iterations {
        let sample = try await startupSample(player, data: startupData)
        startup.append(sample.startup)
        firstPresentation.append(sample.firstPresentation)
      }
      results[player.rawValue] = Timing(
        medianPlayerStartupMilliseconds: median(startup),
        medianFirstPresentationMilliseconds: median(firstPresentation),
        animation: try await animationSample(player, data: animationData),
        scroll: try await scrollSample(player, data: startupData))
    }
    return Report(
      schemaVersion: 1,
      sourceRevision: ProcessInfo.processInfo.environment["RC_SOURCE_REVISION"] ?? "unknown",
      device: ProcessInfo.processInfo.environment["RC_BENCHMARK_DEVICE"] ?? UIDevice.current.model,
      systemVersion: UIDevice.current.systemVersion,
      iterations: iterations,
      methodology: "Release app; same booted simulator and process; player creation through the first CADisplayLink callback; animation records 120 display-link intervals; scroll uses UIScrollView's animated content-offset pipeline. Directional simulator evidence only.",
      results: results)
  }

  private static func startupSample(_ player: Player, data: Data) async throws -> (startup: Double, firstPresentation: Double) {
    let host = benchmarkHost()
    let started = CACurrentMediaTime()
    let view = try makePlayer(player, data: data)
    let startup = milliseconds(since: started)
    host.addSubview(view)
    view.frame = CGRect(origin: .zero, size: playerSize)
    view.setNeedsLayout()
    view.layoutIfNeeded()
    try await waitForPlayer(player, view: view)
    _ = await DisplayLinkSampler.nextFrame()
    let firstPresentation = milliseconds(since: started)
    view.removeFromSuperview()
    return (startup, firstPresentation)
  }

  private static func animationSample(_ player: Player, data: Data) async throws -> Animation {
    let host = benchmarkHost()
    let view = try makePlayer(player, data: data)
    host.addSubview(view)
    view.frame = CGRect(origin: .zero, size: playerSize)
    try await waitForPlayer(player, view: view)
    let intervals = await DisplayLinkSampler.intervals(count: 120)
    view.removeFromSuperview()
    let nominal = 1_000 / max(UIScreen.main.maximumFramesPerSecond, 1)
    return Animation(
      samples: intervals.count,
      medianFrameIntervalMilliseconds: median(intervals),
      p95FrameIntervalMilliseconds: percentile(intervals, 0.95),
      missedFrames: intervals.filter { $0 > nominal * 1.5 }.count)
  }

  private static func scrollSample(_ player: Player, data: Data) async throws -> Scroll {
    let host = benchmarkHost()
    var samples: [Double] = []
    for offset in [CGFloat(480), 0, 480] {
      let scroll = BenchmarkScrollView(frame: host.bounds)
      scroll.accessibilityIdentifier = "apple-benchmark-scroll-\(player.rawValue)"
      scroll.contentSize = CGSize(width: playerSize.width, height: playerSize.height * 3)
      let view = try makePlayer(player, data: data)
      view.frame = CGRect(origin: .zero, size: CGSize(width: playerSize.width, height: playerSize.height * 3))
      scroll.addSubview(view)
      host.addSubview(scroll)
      try await waitForPlayer(player, view: view)
      let started = CACurrentMediaTime()
      try await scroll.move(to: CGPoint(x: 0, y: offset))
      samples.append(milliseconds(since: started))
      scroll.removeFromSuperview()
    }
    return Scroll(medianCompletionMilliseconds: median(samples), samples: samples.count)
  }

  private static func benchmarkHost() -> UIView {
    guard
      let host = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
        .flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController?.view
    else { fatalError("Apple player benchmark needs a key window") }
    return host
  }

  fileprivate static func makePlayer(_ player: Player, data: Data) throws -> UIView {
    switch player {
    case .cmp:
      let controller = RemoteComposePlayerViewController(
        data: data,
        configuration: .init(theme: .light, compatibility: .compatible, background: .opaque))
      return BenchmarkComposeContainer(controller: controller)
    case .nativeUIKit:
      return RemoteComposeNativePlayerView(data: data, background: .opaque, compatibilityPolicy: .compatible)
    }
  }

  private static func waitForPlayer(_ player: Player, view: UIView) async throws {
    let deadline = CACurrentMediaTime() + 5
    while CACurrentMediaTime() < deadline {
      if player == .cmp || containsNativeDocument(view) { return }
      try await Task.sleep(nanoseconds: 10_000_000)
    }
    throw CocoaError(.coderReadCorrupt)
  }

  private static func containsNativeDocument(_ view: UIView) -> Bool {
    view.accessibilityIdentifier == "rc-native-document" || view.subviews.contains(where: containsNativeDocument)
  }

  private static func milliseconds(since start: CFTimeInterval) -> Double { (CACurrentMediaTime() - start) * 1_000 }
  private static func median(_ values: [Double]) -> Double { values.sorted()[values.count / 2] }
  private static func percentile(_ values: [Double], _ percentile: Double) -> Double {
    let sorted = values.sorted()
    return sorted[min(Int((Double(sorted.count - 1) * percentile).rounded(.up)), sorted.count - 1)]
  }
}

@MainActor
private final class BenchmarkComposeContainer: UIView {
  private let controller: RemoteComposePlayerViewController

  init(controller: RemoteComposePlayerViewController) {
    self.controller = controller
    super.init(frame: .zero)
    let player = controller.view!
    addSubview(player)
    player.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      player.leadingAnchor.constraint(equalTo: leadingAnchor),
      player.trailingAnchor.constraint(equalTo: trailingAnchor),
      player.topAnchor.constraint(equalTo: topAnchor),
      player.bottomAnchor.constraint(equalTo: bottomAnchor),
    ])
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
}

@MainActor
private final class DisplayLinkSampler: NSObject {
  // CADisplayLink's target ownership is not an API contract; retain the sampler until completion.
  private static var activeSampler: DisplayLinkSampler?
  private var displayLink: CADisplayLink?
  private var previousTimestamp: CFTimeInterval?
  private var intervals: [Double] = []
  private var targetCount = 0
  private var continuation: CheckedContinuation<[Double], Never>?

  static func nextFrame() async -> Double { (await intervals(count: 1)).first ?? 0 }

  static func intervals(count: Int) async -> [Double] {
    await withCheckedContinuation { continuation in
      let sampler = DisplayLinkSampler()
      activeSampler = sampler
      sampler.start(count: count, continuation: continuation)
    }
  }

  private func start(count: Int, continuation: CheckedContinuation<[Double], Never>) {
    targetCount = count
    self.continuation = continuation
    let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
    link.add(to: .main, forMode: .common)
    displayLink = link
  }

  @objc private func tick(_ link: CADisplayLink) {
    defer { previousTimestamp = link.timestamp }
    guard let previousTimestamp else { return }
    intervals.append((link.timestamp - previousTimestamp) * 1_000)
    if intervals.count == targetCount {
      displayLink?.invalidate()
      displayLink = nil
      continuation?.resume(returning: intervals)
      continuation = nil
      Self.activeSampler = nil
    }
  }
}

@MainActor
private final class BenchmarkScrollView: UIScrollView, UIScrollViewDelegate {
  private var continuation: CheckedContinuation<Void, Error>?

  override init(frame: CGRect) {
    super.init(frame: frame)
    delegate = self
    showsVerticalScrollIndicator = false
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

  func move(to offset: CGPoint) async throws {
    try await withCheckedThrowingContinuation { continuation in
      self.continuation = continuation
      setContentOffset(offset, animated: true)
    }
  }

  func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
    continuation?.resume()
    continuation = nil
  }
}

@MainActor
final class BenchmarkInteractionController: UIViewController, UIScrollViewDelegate {
  private let player: ApplePlayerBenchmark.Player
  private let offsetLabel = UILabel()

  init(player: ApplePlayerBenchmark.Player) {
    self.player = player
    super.init(nibName: nil, bundle: nil)
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

  override func viewDidLoad() {
    super.viewDidLoad()
    guard let url = Bundle.main.url(forResource: "TitleCardRemote-640x480", withExtension: "rc"),
      let data = try? Data(contentsOf: url), let playerView = try? ApplePlayerBenchmark.makePlayer(player, data: data)
    else { return }
    let scroll = UIScrollView()
    scroll.accessibilityIdentifier = "apple-benchmark-scroll-\(player.rawValue)"
    scroll.delegate = self
    scroll.contentSize = CGSize(width: 640, height: 1_440)
    scroll.translatesAutoresizingMaskIntoConstraints = false
    playerView.frame = CGRect(x: 0, y: 0, width: 640, height: 1_440)
    scroll.addSubview(playerView)
    view.addSubview(scroll)
    offsetLabel.accessibilityIdentifier = "apple-benchmark-scroll-offset-\(player.rawValue)"
    offsetLabel.text = "0"
    offsetLabel.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(offsetLabel)
    NSLayoutConstraint.activate([
      scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      scroll.topAnchor.constraint(equalTo: view.topAnchor),
      scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
      offsetLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
      offsetLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 12),
    ])
  }

  func scrollViewDidScroll(_ scrollView: UIScrollView) {
    offsetLabel.text = String(Int(scrollView.contentOffset.y.rounded()))
  }
}
