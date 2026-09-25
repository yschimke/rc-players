#if canImport(AppKit) && !targetEnvironment(macCatalyst)
import AppKit
import CoreText
import Darwin
import QuartzCore
#if canImport(RcNativePlayerCore)
  @_spi(Conformance) import RcNativePlayerCore
#endif
#if canImport(RcPlayerAppleFonts)
  import RcPlayerAppleFonts
#endif

func nativeEventSummary(_ event: NativeSwiftEvent) -> String {
  switch event {
  case .namedAction(let name, let value): return "Named \(name): \(value.summary)"
  }
}

func nativeEventSummary(_ event: RemoteComposeNativePlayerEvent) -> String {
  switch event {
  case .action(let id): "Action \(id)"
  case .actionWithMetadata(let id, let metadata): "Action \(id): \(metadata)"
  case .namedAction(let name, let value): "Named \(name): \(value.summary)"
  case .debug(let message, let value, let flags): "Debug \(message): \(value) [\(flags)]"
  }
}

private extension NativeSwiftActionValue {
  var summary: String {
    switch self {
    case .none: "none"
    case .float(let value): String(value)
    case .integer(let value): String(value)
    case .text(let value): value
    }
  }
}

private extension RemoteComposeNativePlayerActionValue {
  var summary: String {
    switch self {
    case .none: "none"
    case .float(let value): String(value)
    case .integer(let value): String(value)
    case .text(let value): value
    case .floatList(let value): value.description
    }
  }
}

private func nativePlayerActionValue(_ value: NativeSwiftActionValue) -> RemoteComposeNativePlayerActionValue {
  switch value {
  case .none: .none
  case .float(let value): .float(value)
  case .integer(let value): .integer(value)
  case .text(let value): .text(value)
  }
}

extension NativeSwiftWallClock {
  /// The instant a capture renders against, so a corpus run is reproducible.
  ///
  /// A document that reads a calendar or time-of-day variable would otherwise render differently
  /// depending on when the lane ran, and two frames of one gold could straddle a second boundary.
  /// 2026-01-01T00:00:00Z, chosen because it is a round instant rather than because anything
  /// depends on the date.
  public static let capture = NativeSwiftWallClock(epochMillis: 1_767_225_600_000)
}

enum NativeMacFrameDriverMode: Equatable {
  case idle
  case displayLink
  case wake(after: TimeInterval)

  static func resolve(
    needsContinuousFrames: Bool,
    requestsNextFrame: Bool,
    wakeAfter: TimeInterval?,
    isActive: Bool,
    isVisible: Bool,
    reduceMotion: Bool
  ) -> NativeMacFrameDriverMode {
    guard isActive, isVisible else { return .idle }
    if requestsNextFrame || (needsContinuousFrames && !reduceMotion) { return .displayLink }
    if let wakeAfter { return wakeAfter <= 0 ? .displayLink : .wake(after: wakeAfter) }
    return .idle
  }
}

/// One input step the conformance lane drove before a capture.
///
/// The corpus's timeline is a sequence of gestures and clock moves, and a check bound to a step
/// asserts the document *after* everything up to it. The player opens a document fresh per frame, so
/// the lane sends the whole sequence with each frame and the player replays it.
struct NativeMacInputStep: Decodable {
  enum Kind: String, Decodable {
    case click, longPress, doubleClick
    case touchDown = "touch_down"
    case touchDrag = "touch_drag"
    case touchUp = "touch_up"
  }

  let kind: Kind
  let point: CGPoint
  let velocity: CGVector
  /// The clock the step happens at, in seconds.
  let at: TimeInterval
  /// The clock after the step's requested delay, when its repaint is observed.
  let captureAt: TimeInterval
  /// The viewport in force when this input was dispatched.
  let viewport: CGSize?

  private enum CodingKeys: String, CodingKey {
    case kind, x, y, dx, dy, at, width, height
    case captureAt = "capture_at"
  }

  init(from decoder: Decoder) throws {
    let values = try decoder.container(keyedBy: CodingKeys.self)
    kind = try values.decode(Kind.self, forKey: .kind)
    point = CGPoint(
      x: try values.decodeIfPresent(Double.self, forKey: .x) ?? 0,
      y: try values.decodeIfPresent(Double.self, forKey: .y) ?? 0)
    velocity = CGVector(
      dx: try values.decodeIfPresent(Double.self, forKey: .dx) ?? 0,
      dy: try values.decodeIfPresent(Double.self, forKey: .dy) ?? 0)
    at = try values.decodeIfPresent(TimeInterval.self, forKey: .at) ?? 0
    captureAt = try values.decodeIfPresent(TimeInterval.self, forKey: .captureAt) ?? at
    if let width = try values.decodeIfPresent(CGFloat.self, forKey: .width),
      let height = try values.decodeIfPresent(CGFloat.self, forKey: .height)
    {
      viewport = CGSize(width: width, height: height)
    } else {
      viewport = nil
    }
  }
}

/// The scroll a touch sequence picked up. Its starting offset and point stay fixed for the whole
/// sequence, so two move samples at -40 and -80 produce offsets 40 and 80 rather than 40 and 120.
private struct NativeMacScrollDrag {
  let positionID: Int
  let direction: NativeSwiftScrollDirection
  let startPoint: CGPoint
  let startOffset: Float
  var currentOffset: Float
  let maximum: Float
}

private struct NativeMacScrollFling {
  let positionID: Int
  let startOffset: Float
  let velocity: Float
  let maximum: Float
  let startedAt: TimeInterval
}

private struct NativeMacStateTransition {
  let stateLayoutID: Int
  let duration: TimeInterval
  let timingFunction: CAMediaTimingFunction
  let startedAt: TimeInterval

  /// The AndroidX `GeneralEasing` values native Core Animation timing functions support.
  static func timingFunction(for easingType: Int?) -> CAMediaTimingFunction {
    switch easingType {
    case Int(NativeSwiftFloatEasingType.cubicStandard.rawValue):
      return CAMediaTimingFunction(controlPoints: 0.4, 0, 0.2, 1)
    case Int(NativeSwiftFloatEasingType.cubicAccelerate.rawValue):
      return CAMediaTimingFunction(controlPoints: 0.4, 0.05, 0.8, 0.7)
    case Int(NativeSwiftFloatEasingType.cubicDecelerate.rawValue):
      return CAMediaTimingFunction(controlPoints: 0, 0, 0.2, 0.95)
    case Int(NativeSwiftFloatEasingType.cubicLinear.rawValue):
      return CAMediaTimingFunction(name: .linear)
    case Int(NativeSwiftFloatEasingType.cubicAnticipate.rawValue):
      return CAMediaTimingFunction(controlPoints: 0.36, 0, 0.66, -0.56)
    case Int(NativeSwiftFloatEasingType.cubicOvershoot.rawValue):
      return CAMediaTimingFunction(controlPoints: 0.34, 1.56, 0.64, 1)
    default: return CAMediaTimingFunction(controlPoints: 0.4, 0, 0.2, 1)
    }
  }
}

private struct NativeMacScrollTarget {
  let positionID: Int
  let direction: NativeSwiftScrollDirection
  let offset: Float
  let maximum: Float
}

private enum NativeMacScrollMotion {
  case idle
  case dragging(NativeMacScrollDrag)
  case flinging(NativeMacScrollFling)
}

/// Stateful replay of one frame's input history.
///
/// Keeping the drag and fling here makes illegal combinations unrepresentable to the renderer: the
/// window controller asks this value to consume an input; it does not coordinate two optional
/// `inout` parameters and a string switch itself.
///
/// Main-actor isolated because it hit-tests the document view, as the window controller that drives
/// it is.
@MainActor
private struct NativeMacInputReplay {
  private var scroll: NativeMacScrollMotion = .idle

  /// `onEvent` receives the host events the gesture raises (a click's host action, for one), as the
  /// live view's own gesture path hands them to the host.
  mutating func consume(
    _ step: NativeMacInputStep, session: NativeSwiftDocumentSession, view: NativeMacDocumentView,
    onEvent: (NativeSwiftEvent) -> Void
  ) throws -> Bool {
    func publishPointer() {
      _ = session.setFloat(Float(step.point.x), forID: NativeSwiftSystemVariables.touchX)
      _ = session.setFloat(Float(step.point.y), forID: NativeSwiftSystemVariables.touchY)
    }
    var handledByScroll = false
    let gesture: NativeSwiftGestureKind
    switch step.kind {
    case .click:
      publishPointer()
      gesture = .tap
    case .longPress:
      publishPointer()
      gesture = .longPress
    case .doubleClick:
      publishPointer()
      gesture = .doubleTap
    case .touchDown:
      publishPointer()
      if let target = view.scrollTarget(at: step.point) {
        handledByScroll = true
        scroll = .dragging(
          NativeMacScrollDrag(
            positionID: target.positionID, direction: target.direction, startPoint: step.point,
            startOffset: target.offset, currentOffset: target.offset, maximum: target.maximum))
      } else {
        scroll = .idle
      }
      gesture = .touchDown
    case .touchDrag:
      publishPointer()
      guard case .dragging(var activeDrag) = scroll else { return false }
      let delta = Float(
        activeDrag.direction == .horizontal
          ? step.point.x - activeDrag.startPoint.x : step.point.y - activeDrag.startPoint.y)
      activeDrag.currentOffset = NativeSwiftScrollGesture.offset(
        afterDragging: activeDrag.startOffset, delta: delta, maximum: activeDrag.maximum)
      scroll = .dragging(activeDrag)
      session.setFloat(activeDrag.currentOffset, forID: activeDrag.positionID)
      return true
    case .touchUp:
      publishPointer()
      if case .dragging(let activeDrag) = scroll {
        handledByScroll = true
        let fingerVelocity = Float(
          activeDrag.direction == .horizontal ? step.velocity.dx : step.velocity.dy)
        if fingerVelocity != 0 {
          scroll = .flinging(
            NativeMacScrollFling(
              positionID: activeDrag.positionID, startOffset: activeDrag.currentOffset,
              // The reference establishes a fling on the first post-release repaint; later
              // `advance_time` steps decay from that observed frame.
              velocity: -fingerVelocity, maximum: activeDrag.maximum, startedAt: step.captureAt))
        } else {
          scroll = .idle
        }
      } else {
        scroll = .idle
      }
      gesture = .touchUp
    }

    guard let componentID = view.gestureTarget(at: step.point, for: gesture) else {
      return handledByScroll
    }
    let events = try session.gesture(
      gesture, componentID: componentID,
      sample: NativeSwiftPointerSample(
        x: Float(step.point.x), y: Float(step.point.y),
        velocityX: Float(step.velocity.dx), velocityY: Float(step.velocity.dy)),
      timeSeconds: step.at)
    events?.forEach(onEvent)
    return events != nil || handledByScroll
  }

  func finish(at time: TimeInterval, session: NativeSwiftDocumentSession) {
    guard case .flinging(let fling) = scroll else { return }
    let elapsed = Float(max(time - fling.startedAt, 0))
    // The reference's decay loses the supplied velocity linearly over one second: a 1200 pt/s
    // release travels 114 points in 100 ms, 306 in 300 ms and 600 in one second.
    let duration = min(elapsed, 1)
    let displacement = fling.velocity * (duration - duration * duration / 2)
    session.setFloat(
      min(max(fling.startOffset + displacement, 0), fling.maximum), forID: fling.positionID)
  }
}

/// A request for the document's own values, for the conformance corpus's value probes.
///
/// The corpus's `float`, `int`, `text` and `color` probes read a document's state rather than its
/// rendering, so the runner asks for the slots it asserts rather than for a whole dump: a gold
/// asserts a handful, and a document can hold hundreds.
struct NativeMacValueRequest: Decodable {
  let floats: [String]
  let integers: [String]
  let texts: [String]
  let colors: [String]
  let matrices: [String]
  let dynamicFloatArrays: [String]
  let dataFloatArrays: [String]

  init(
    floats: [String] = [], integers: [String] = [], texts: [String] = [], colors: [String] = [],
    matrices: [String] = [],
    dynamicFloatArrays: [String] = [], dataFloatArrays: [String] = []
  ) {
    self.floats = floats
    self.integers = integers
    self.texts = texts
    self.colors = colors
    self.matrices = matrices
    self.dynamicFloatArrays = dynamicFloatArrays
    self.dataFloatArrays = dataFloatArrays
  }

  private enum CodingKeys: String, CodingKey {
    case floats, integers, texts, colors, matrices
    case dynamicFloatArrays = "float_arrays_dynamic"
    case dataFloatArrays = "float_arrays_data"
  }

  init(from decoder: Decoder) throws {
    let values = try decoder.container(keyedBy: CodingKeys.self)
    self.init(
      floats: try values.decodeIfPresent([String].self, forKey: .floats) ?? [],
      integers: try values.decodeIfPresent([String].self, forKey: .integers) ?? [],
      texts: try values.decodeIfPresent([String].self, forKey: .texts) ?? [],
      colors: try values.decodeIfPresent([String].self, forKey: .colors) ?? [],
      matrices: try values.decodeIfPresent([String].self, forKey: .matrices) ?? [],
      dynamicFloatArrays: try values.decodeIfPresent([String].self, forKey: .dynamicFloatArrays)
        ?? [],
      dataFloatArrays: try values.decodeIfPresent([String].self, forKey: .dataFloatArrays) ?? [])
  }

  var isEmpty: Bool {
    floats.isEmpty && integers.isEmpty && texts.isEmpty && colors.isEmpty
      && matrices.isEmpty && dynamicFloatArrays.isEmpty && dataFloatArrays.isEmpty
  }
}

struct NativeMacRenderedFrame {
  let png: Data
  let tree: [[String: Any]]
  let values: [String: Any]?
  /// Decoded-document observations that do not depend on a transient presentation layer.
  let records: [String: Any]
  /// Whether the final replayed input was delivered to a document component.
  let inputHandled: Bool?
}

@MainActor
public final class NativeAppKitWindowController: NSObject, NSWindowDelegate {
  public static let shared = NativeAppKitWindowController()
  private var windows: [NSWindow] = []

  /// - Parameter viewport: the size to lay the document out in, when that differs from the size the
  ///   document declares. The conformance corpus needs it: `resize` is its most common step kind,
  ///   more common than `paint`, and a capture that always uses the document's own size answers a
  ///   different question from the one the gold asked.
  public static func renderPNG(
    data: Data,
    timeSeconds: TimeInterval = 0,
    wallClock: NativeSwiftWallClock = .capture,
    downloadedFonts: [String: RemoteComposeDownloadedFont] = [:],
    viewport: CGSize? = nil
  ) throws -> Data {
    try renderFrame(
      data: data, timeSeconds: timeSeconds, wallClock: wallClock,
      downloadedFonts: downloadedFonts, viewport: viewport
    ).png
  }

  /// Builds an embeddable AppKit view from the same validation and snapshot path as a window.
  public static func makeView(
    data: Data,
    compatibility: NativeMacCompatibility = .compatible,
    onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
    onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in },
    onError: @escaping (String) -> Void = { _ in }
  ) throws -> NSView {
    try NativeMacPolicy.validateDocument(data)
    let session = try NativeSwiftDocumentSession.open(data: data)
    let wallClock = nativeSystemWallClock()
    let snapshot = try session.snapshot(timeSeconds: 0, wallClock: wallClock)
    let report = try NativeMacPolicy.evaluate(snapshot, compatibility: compatibility)
    let fonts = try NativeMacFontRegistry.register(snapshot: snapshot, downloadedFonts: [:])
    return try NativeMacDocumentView(
      snapshot: snapshot, resolvedAt: 0, wallClock: wallClock, session: session,
      compatibility: compatibility, report: report,
      fonts: fonts,
      onEvent: { event in
        guard case let .namedAction(name, value) = event else { return }
        onEvent(.namedAction(name: name, value: nativePlayerActionValue(value)))
      }, onDiagnostics: onDiagnostics, onError: onError)
  }

  /// One captured frame and the tree it was laid out as.
  ///
  /// The tree is what the conformance corpus's `tree` probe reads: the laid-out geometry of every
  /// component, in the vocabulary the golds use. It comes from the same view the pixels do, so the
  /// two channels cannot disagree about what was rendered.
  static func renderFrame(
    data: Data,
    timeSeconds: TimeInterval = 0,
    wallClock: NativeSwiftWallClock = .capture,
    theme: Int = NativeSwiftTheme.unspecified,
    downloadedFonts: [String: RemoteComposeDownloadedFont] = [:],
    viewport: CGSize? = nil,
    values: NativeMacValueRequest = NativeMacValueRequest(),
    steps: [NativeMacInputStep] = [],
    conformanceFontName: String? = nil,
    particleSession: NativeSwiftDocumentSession? = nil,
    firstPaintTime: TimeInterval? = nil
  ) throws -> NativeMacRenderedFrame {
    try NativeMacPolicy.validateDocument(data)
    // A *data-only* document declares values and nothing to draw. A conformance capture still has to
    // answer the probes those documents assert, so the batch tolerates one; the window path below
    // does not, and a host that is about to show a document is still told it has nothing to paint.
    let session = try NativeSwiftDocumentSession.open(
      data: data, toleratingRootlessData: true)
    session.setRequestedTheme(theme)
    // Each frame opens a fresh session, which would take this frame as the first paint and hold a
    // marquee still; the batch names the instant its warm-up paints ran at instead.
    if let firstPaintTime { session.setFirstPaintTime(firstPaintTime) }
    // A gesture needs a laid-out view to hit-test against, and the document as it stood when the
    // first gesture arrived — not as it stands at the capture. The frame's own instant is the start
    // only when nothing was driven.
    let start = steps.first?.at ?? timeSeconds
    let snapshot = try session.snapshot(timeSeconds: start, wallClock: wallClock)
    let report = try NativeMacPolicy.evaluate(snapshot, compatibility: .compatible)
    let fonts = try NativeMacFontRegistry.register(
      snapshot: snapshot, downloadedFonts: downloadedFonts)
    var hostActionSummaries: [String] = []
    let player = try NativeMacDocumentView(
      snapshot: snapshot, resolvedAt: start, wallClock: wallClock, session: session,
      compatibility: .compatible, report: report, fonts: fonts,
      conformanceFontName: conformanceFontName,
      onEvent: { hostActionSummaries.append(nativeEventSummary($0)) }, onDiagnostics: { _ in }, onError: { _ in })
    let captureSize = viewport ?? CGSize(width: snapshot.width, height: snapshot.height)
    let initialSize = steps.first?.viewport ?? captureSize
    let frame = NSRect(origin: .zero, size: initialSize)
    let window = NSWindow(
      contentRect: frame, styleMask: .borderless, backing: .buffered, defer: false)
    window.contentView = player
    player.frame = frame
    player.layoutSubtreeIfNeeded()
    var replay = NativeMacInputReplay()
    var inputHandled: Bool?
    for step in steps {
      if let viewport = step.viewport, player.frame.size != viewport {
        window.setContentSize(viewport)
        player.frame = NSRect(origin: .zero, size: viewport)
        player.layoutSubtreeIfNeeded()
      }
      // A replayed gesture's events reach the host-action record the live view's would.
      inputHandled = try replay.consume(
        step, session: session, view: player,
        onEvent: { hostActionSummaries.append(nativeEventSummary($0)) })
      // A later input must hit-test the state left by the one before it. This matters when a click
      // switches a StateLayout before the next gesture, and also keeps the scroll tree current while
      // a multi-sample drag is replayed.
      try player.refresh(timeSeconds: step.captureAt, wallClock: wallClock)
      player.layoutSubtreeIfNeeded()
    }
    replay.finish(at: timeSeconds, session: session)
    if player.frame.size != captureSize {
      window.setContentSize(captureSize)
      player.frame = NSRect(origin: .zero, size: captureSize)
    }
    if !steps.isEmpty {
      // Re-resolve after the gestures and lay out again: a gesture changes the document's state, and
      // the frame a check is bound to has to show the result rather than the state it started in.
      try player.refresh(timeSeconds: timeSeconds, wallClock: wallClock)
      player.layoutSubtreeIfNeeded()
    }
    guard let bitmap = player.bitmapImageRepForCachingDisplay(in: player.bounds) else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Could not allocate AppKit capture")
    }
    player.cacheDisplay(in: player.bounds, to: bitmap)
    guard let png = bitmap.representation(using: .png, properties: [:]) else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Could not encode AppKit capture")
    }
    var records = operationRecords(
      try session.snapshot(timeSeconds: timeSeconds, wallClock: wallClock),
      operationCount: session.linkedOperationCount, operationNames: try operationNames(in: data))
    // The particle probe observes one system's particle rows. A document with no system reports an
    // empty matrix; multiple systems are deliberately not flattened, as that would lose the wire
    // declaration boundary needed by a future targeted probe.
    records["particles"] = try (particleSession ?? session).particleSnapshots(timeSeconds: timeSeconds)
      .first?.particles
      .map { $0.map(Double.init) } ?? []
    records["draw_log_commands"] = try drawLog(in: data)
    records["host_actions"] = hostActionSummaries.compactMap { summary -> [String: Any]? in
      guard summary.hasPrefix("Named "), let separator = summary.range(of: ": ") else { return nil }
      return [
        "name": String(summary.dropFirst("Named ".count).prefix(upTo: separator.lowerBound)),
        "value": String(summary[separator.upperBound...]),
      ]
    }
    return NativeMacRenderedFrame(
      png: png, tree: player.layoutTree(),
      values: values.isEmpty
        ? nil
        : try reportedValues(
           session: session, timeSeconds: timeSeconds, wallClock: wallClock, request: values),
      records: records,
      inputHandled: inputHandled)
  }

  /// The conformance draw log is the decoded canvas stream in wire order. Its checks are ordered
  /// subsequences, so reporting supported canvas commands here is faithful without pretending to
  /// record host-only work that an AppKit view might perform while painting.
  private static func drawLog(in data: Data) throws -> [String] {
    let names: [Int: String] = [
      NativeSwiftWireOpcode.drawText: "DrawText",
      NativeSwiftWireOpcode.drawTextAnchored: "DrawTextAnchored",
      NativeSwiftWireOpcode.drawTextOnPath: "DrawTextOnPath",
      NativeSwiftWireOpcode.drawTextOnCircle: "DrawTextOnCircleStub",
      NativeSwiftWireOpcode.conditionalOperations: "ConditionalOperations",
      NativeSwiftWireOpcode.clipPath: "clipPath",
      NativeSwiftWireOpcode.clipRect: "clipRect",
      NativeSwiftWireOpcode.paintValues: "paint",
      NativeSwiftWireOpcode.drawRect: "drawRect",
      NativeSwiftWireOpcode.drawBitmap: "drawBitmap",
      NativeSwiftWireOpcode.drawCircle: "drawCircle",
      NativeSwiftWireOpcode.drawLine: "drawLine",
      NativeSwiftWireOpcode.drawRoundRect: "drawRoundRect",
      NativeSwiftWireOpcode.drawSector: "drawSector",
      NativeSwiftWireOpcode.drawOval: "drawOval",
      NativeSwiftWireOpcode.drawPath: "drawPath",
      NativeSwiftWireOpcode.drawTweenPath: "drawTweenPath",
      NativeSwiftWireOpcode.matrixScale: "scale",
      NativeSwiftWireOpcode.matrixTranslate: "translate",
      NativeSwiftWireOpcode.matrixSkew: "skew",
      NativeSwiftWireOpcode.matrixRotate: "rotate",
      NativeSwiftWireOpcode.matrixSave: "save",
      NativeSwiftWireOpcode.matrixRestore: "restore",
      NativeSwiftWireOpcode.drawBitmapScaled: "drawBitmapScaled",
      NativeSwiftWireOpcode.drawArc: "drawArc",
      NativeSwiftWireOpcode.matrixFromPath: "matrixFromPath",
    ]
    return try NativeSwiftDocumentSession.operationSpans(
      in: data, toleratingRootlessData: true
    ).compactMap { names[$0.opcode] }
  }

  /// The operation census the corpus's `ops` probes read: every operation on the wire, by name,
  /// once each — as the reference counts `document.operations`, so a branch that does not run
  /// still counts and a macro called twice does not count its body twice.
  ///
  /// The header is an operation on the wire like any other, and every document that decodes has
  /// one, so it leads the list; the decoder consumes it before the operation loop starts.
  private static func operationNames(in data: Data) throws -> [String] {
    let census = try NativeSwiftDocumentSession.open(
      data: data, toleratingRootlessData: true
    ).operationCensus
    return ([NativeSwiftWireOpcode.header] + census).flatMap { censusNames[$0] ?? [] }
  }

  /// `ops:counts` over the whole vocabulary. An operation the document does not contain is counted
  /// as zero rather than left out: the corpus asserts absences as `0`, and a missing key reads as
  /// an unobservable count rather than an observed one.
  private static func operationCounts(_ names: [String]) -> [String: Int] {
    var counts = Dictionary(
      censusNames.values.flatMap { $0 }.map { ($0, 0) }, uniquingKeysWith: { first, _ in first })
    for name in names { counts[name, default: 0] += 1 }
    return counts
  }

  /// The names an operation answers to in the census, for every opcode in the AndroidX manifest.
  ///
  /// The corpus never says which vocabulary `ops:*` uses, and its golds mix three (see
  /// `docs/design/RC_CONFORMANCE_PUSHBACK.md` §2). Each operation reports AndroidX's concrete class
  /// name and the name derived from its constant, as the CMP lane does: `CoreSemantics` and
  /// `AccessibilitySemantics`, `DrawText` and `DrawTextRun`. Names only the TypeScript player uses
  /// (`TouchDownModifier`, `CanvasOperationsOp`, `…Stub`) are neither, and are left to fail.
  /// Generated from `rc-player/protocol/src/main/rc-operations.manifest`.
  private static let censusNames: [Int: [String]] = [
    NativeSwiftWireOpcode.header: ["Header"],
    NativeSwiftWireOpcode.componentStart: ["ComponentStart"],
    NativeSwiftWireOpcode.loadBitmap: ["LoadBitmap"],
    NativeSwiftWireOpcode.animationSpec: ["AnimationSpec"],
    NativeSwiftWireOpcode.modifierWidth: ["WidthModifierOperation", "ModifierWidth"],
    NativeSwiftWireOpcode.clipPath: ["ClipPath"],
    NativeSwiftWireOpcode.clipRect: ["ClipRect"],
    NativeSwiftWireOpcode.paintValues: ["PaintData", "PaintValues"],
    NativeSwiftWireOpcode.drawRect: ["DrawRect"],
    NativeSwiftWireOpcode.drawText: ["DrawText", "DrawTextRun"],
    NativeSwiftWireOpcode.drawBitmap: ["DrawBitmap"],
    NativeSwiftWireOpcode.dataShader: ["ShaderData", "DataShader"],
    NativeSwiftWireOpcode.drawCircle: ["DrawCircle"],
    NativeSwiftWireOpcode.drawLine: ["DrawLine"],
    NativeSwiftWireOpcode.drawBitmapFontTextRun: ["DrawBitmapFontText", "DrawBitmapFontTextRun"],
    NativeSwiftWireOpcode.drawBitmapFontTextRunOnPath: ["DrawBitmapFontTextOnPath", "DrawBitmapFontTextRunOnPath"],
    NativeSwiftWireOpcode.drawRoundRect: ["DrawRoundRect"],
    NativeSwiftWireOpcode.drawSector: ["DrawSector"],
    NativeSwiftWireOpcode.drawTextOnPath: ["DrawTextOnPath"],
    NativeSwiftWireOpcode.modifierRoundedClipRect: ["RoundedClipRectModifierOperation", "ModifierRoundedClipRect"],
    NativeSwiftWireOpcode.modifierBackground: ["BackgroundModifierOperation", "ModifierBackground"],
    NativeSwiftWireOpcode.drawOval: ["DrawOval"],
    NativeSwiftWireOpcode.drawTextOnCircle: ["DrawTextOnCircle"],
    NativeSwiftWireOpcode.modifierPadding: ["PaddingModifierOperation", "ModifierPadding"],
    NativeSwiftWireOpcode.modifierClick: ["ClickModifierOperation", "ModifierClick"],
    NativeSwiftWireOpcode.theme: ["Theme"],
    NativeSwiftWireOpcode.clickArea: ["ClickArea"],
    NativeSwiftWireOpcode.rootContentBehavior: ["RootContentBehavior"],
    NativeSwiftWireOpcode.drawBitmapInt: ["DrawBitmapInt"],
    NativeSwiftWireOpcode.modifierHeight: ["HeightModifierOperation", "ModifierHeight"],
    NativeSwiftWireOpcode.dataFloat: ["FloatConstant", "DataFloat"],
    NativeSwiftWireOpcode.animatedFloat: ["FloatExpression", "AnimatedFloat"],
    NativeSwiftWireOpcode.modifierMultiClick: ["MultiClickModifier", "ModifierMultiClick"],
    NativeSwiftWireOpcode.layoutCustom: ["Custom", "LayoutCustom"],
    NativeSwiftWireOpcode.dataBitmap: ["BitmapData", "DataBitmap"],
    NativeSwiftWireOpcode.dataText: ["TextData", "DataText"],
    NativeSwiftWireOpcode.rootContentDescription: ["RootContentDescription"],
    NativeSwiftWireOpcode.modifierBorder: ["BorderModifierOperation", "ModifierBorder"],
    NativeSwiftWireOpcode.modifierClipRect: ["ClipRectModifierOperation", "ModifierClipRect"],
    NativeSwiftWireOpcode.eventAction: ["EventActionOperation", "EventAction"],
    NativeSwiftWireOpcode.dataPath: ["PathData", "DataPath"],
    NativeSwiftWireOpcode.drawPath: ["DrawPath"],
    NativeSwiftWireOpcode.drawTweenPath: ["DrawTweenPath"],
    NativeSwiftWireOpcode.matrixScale: ["MatrixScale"],
    NativeSwiftWireOpcode.matrixTranslate: ["MatrixTranslate"],
    NativeSwiftWireOpcode.matrixSkew: ["MatrixSkew"],
    NativeSwiftWireOpcode.matrixRotate: ["MatrixRotate"],
    NativeSwiftWireOpcode.matrixSave: ["MatrixSave"],
    NativeSwiftWireOpcode.matrixRestore: ["MatrixRestore"],
    NativeSwiftWireOpcode.matrixSet: ["MatrixSet"],
    NativeSwiftWireOpcode.drawTextAnchored: ["DrawTextAnchored", "DrawTextAnchor"],
    NativeSwiftWireOpcode.colorExpressions: ["ColorExpression", "ColorExpressions"],
    NativeSwiftWireOpcode.textFromFloat: ["TextFromFloat"],
    NativeSwiftWireOpcode.textMerge: ["TextMerge"],
    NativeSwiftWireOpcode.namedVariable: ["NamedVariable"],
    NativeSwiftWireOpcode.colorConstant: ["ColorConstant"],
    NativeSwiftWireOpcode.drawContent: ["DrawContent"],
    NativeSwiftWireOpcode.dataInt: ["IntegerConstant", "DataInt"],
    NativeSwiftWireOpcode.playSound: ["PlaySound"],
    NativeSwiftWireOpcode.referencedOperations: ["ReferencedOperations"],
    NativeSwiftWireOpcode.dataBoolean: ["BooleanConstant", "DataBoolean"],
    NativeSwiftWireOpcode.integerExpression: ["IntegerExpression"],
    NativeSwiftWireOpcode.idMap: ["DataMapIds", "IdMap"],
    NativeSwiftWireOpcode.idList: ["DataListIds", "IdList"],
    NativeSwiftWireOpcode.floatList: ["DataListFloat", "FloatList"],
    NativeSwiftWireOpcode.dataLong: ["LongConstant", "DataLong"],
    NativeSwiftWireOpcode.drawBitmapScaled: ["DrawBitmapScaled"],
    NativeSwiftWireOpcode.componentValue: ["ComponentValue"],
    NativeSwiftWireOpcode.textLookup: ["TextLookup"],
    NativeSwiftWireOpcode.drawArc: ["DrawArc"],
    NativeSwiftWireOpcode.textLookupInt: ["TextLookupInt"],
    NativeSwiftWireOpcode.dataMapLookup: ["DataMapLookup"],
    NativeSwiftWireOpcode.textMeasure: ["TextMeasure"],
    NativeSwiftWireOpcode.textLength: ["TextLength"],
    NativeSwiftWireOpcode.touchExpression: ["TouchExpression"],
    NativeSwiftWireOpcode.pathTween: ["PathTween"],
    NativeSwiftWireOpcode.pathCreate: ["PathCreate"],
    NativeSwiftWireOpcode.pathAdd: ["PathAppend", "PathAdd"],
    NativeSwiftWireOpcode.particleDefine: ["ParticlesCreate", "ParticleDefine"],
    NativeSwiftWireOpcode.particleProcess: ["ParticleProcess"],
    NativeSwiftWireOpcode.particleLoop: ["ParticlesLoop", "ParticleLoop"],
    NativeSwiftWireOpcode.impulseStart: ["ImpulseOperation", "ImpulseStart"],
    NativeSwiftWireOpcode.impulseProcess: ["ImpulseProcess"],
    NativeSwiftWireOpcode.functionCall: ["FloatFunctionCall", "FunctionCall"],
    NativeSwiftWireOpcode.dataBitmapFont: ["BitmapFontData", "DataBitmapFont"],
    NativeSwiftWireOpcode.functionDefine: ["FloatFunctionDefine", "FunctionDefine"],
    NativeSwiftWireOpcode.dataSound: ["SoundData", "DataSound"],
    NativeSwiftWireOpcode.attributeText: ["TextAttribute", "AttributeText"],
    NativeSwiftWireOpcode.attributeImage: ["ImageAttribute", "AttributeImage"],
    NativeSwiftWireOpcode.attributeTime: ["TimeAttribute", "AttributeTime"],
    NativeSwiftWireOpcode.canvasOperations: ["CanvasOperations"],
    NativeSwiftWireOpcode.modifierDrawContent: ["DrawContentOperation", "ModifierDrawContent"],
    NativeSwiftWireOpcode.pathCombine: ["PathCombine"],
    NativeSwiftWireOpcode.layoutFitBox: ["FitBoxLayout", "LayoutFitBox"],
    NativeSwiftWireOpcode.hapticFeedback: ["HapticFeedback"],
    NativeSwiftWireOpcode.conditionalOperations: ["ConditionalOperations"],
    NativeSwiftWireOpcode.debugMessage: ["DebugMessage"],
    NativeSwiftWireOpcode.attributeColor: ["ColorAttribute", "AttributeColor"],
    NativeSwiftWireOpcode.matrixFromPath: ["MatrixFromPath"],
    NativeSwiftWireOpcode.textSubtext: ["TextSubtext"],
    NativeSwiftWireOpcode.bitmapTextMeasure: ["BitmapTextMeasure"],
    NativeSwiftWireOpcode.drawBitmapTextAnchored: ["DrawBitmapTextAnchored"],
    NativeSwiftWireOpcode.rem: ["Rem"],
    NativeSwiftWireOpcode.matrixConstant: ["MatrixConstant"],
    NativeSwiftWireOpcode.matrixExpression: ["MatrixExpression"],
    NativeSwiftWireOpcode.matrixVectorMath: ["MatrixVectorMath"],
    NativeSwiftWireOpcode.dataFont: ["FontData", "DataFont"],
    NativeSwiftWireOpcode.drawToBitmap: ["DrawToBitmap"],
    NativeSwiftWireOpcode.wakeIn: ["WakeIn"],
    NativeSwiftWireOpcode.idLookup: ["IdLookup"],
    NativeSwiftWireOpcode.pathExpression: ["PathExpression"],
    NativeSwiftWireOpcode.particleCompare: ["ParticlesCompare", "ParticleCompare"],
    NativeSwiftWireOpcode.update: ["Update"],
    NativeSwiftWireOpcode.colorTheme: ["ColorTheme"],
    NativeSwiftWireOpcode.dynamicFloatList: ["DataDynamicListFloat", "DynamicFloatList"],
    NativeSwiftWireOpcode.updateDynamicFloatList: ["UpdateDynamicFloatList"],
    NativeSwiftWireOpcode.textTransform: ["TextTransform"],
    NativeSwiftWireOpcode.layoutRoot: ["RootLayoutComponent", "LayoutRoot"],
    NativeSwiftWireOpcode.layoutContent: ["LayoutComponentContent", "LayoutContent"],
    NativeSwiftWireOpcode.layoutBox: ["BoxLayout", "LayoutBox"],
    NativeSwiftWireOpcode.layoutRow: ["RowLayout", "LayoutRow"],
    NativeSwiftWireOpcode.layoutColumn: ["ColumnLayout", "LayoutColumn"],
    NativeSwiftWireOpcode.layoutCanvas: ["CanvasLayout", "LayoutCanvas"],
    NativeSwiftWireOpcode.soundExpression: ["SoundExpression"],
    NativeSwiftWireOpcode.layoutCanvasContent: ["CanvasContent", "LayoutCanvasContent"],
    NativeSwiftWireOpcode.layoutText: ["TextLayout", "LayoutText"],
    NativeSwiftWireOpcode.hostAction: ["HostActionOperation", "HostAction"],
    NativeSwiftWireOpcode.hostNamedAction: ["HostNamedActionOperation", "HostNamedAction"],
    NativeSwiftWireOpcode.modifierVisibility: ["ComponentVisibilityOperation", "ModifierVisibility"],
    NativeSwiftWireOpcode.valueIntegerChangeAction: ["ValueIntegerChangeActionOperation", "ValueIntegerChangeAction"],
    NativeSwiftWireOpcode.valueStringChangeAction: ["ValueStringChangeActionOperation", "ValueStringChangeAction"],
    NativeSwiftWireOpcode.containerEnd: ["ContainerEnd"],
    NativeSwiftWireOpcode.loopStart: ["LoopOperation", "LoopStart"],
    NativeSwiftWireOpcode.hostMetadataAction: ["HostActionMetadataOperation", "HostMetadataAction"],
    NativeSwiftWireOpcode.layoutState: ["StateLayout", "LayoutState"],
    NativeSwiftWireOpcode.valueIntegerExpressionChangeAction: ["ValueIntegerExpressionChangeActionOperation", "ValueIntegerExpressionChangeAction"],
    NativeSwiftWireOpcode.modifierTouchDown: ["TouchDownModifierOperation", "ModifierTouchDown"],
    NativeSwiftWireOpcode.modifierTouchUp: ["TouchUpModifierOperation", "ModifierTouchUp"],
    NativeSwiftWireOpcode.modifierOffset: ["OffsetModifierOperation", "ModifierOffset"],
    NativeSwiftWireOpcode.valueFloatChangeAction: ["ValueFloatChangeActionOperation", "ValueFloatChangeAction"],
    NativeSwiftWireOpcode.modifierZindex: ["ZIndexModifierOperation", "ModifierZindex"],
    NativeSwiftWireOpcode.modifierGraphicsLayer: ["GraphicsLayerModifierOperation", "ModifierGraphicsLayer"],
    NativeSwiftWireOpcode.modifierTouchCancel: ["TouchCancelModifierOperation", "ModifierTouchCancel"],
    NativeSwiftWireOpcode.modifierScroll: ["ScrollModifierOperation", "ModifierScroll"],
    NativeSwiftWireOpcode.valueFloatExpressionChangeAction: ["ValueFloatExpressionChangeActionOperation", "ValueFloatExpressionChangeAction"],
    NativeSwiftWireOpcode.modifierMarquee: ["MarqueeModifierOperation", "ModifierMarquee"],
    NativeSwiftWireOpcode.modifierRipple: ["RippleModifierOperation", "ModifierRipple"],
    NativeSwiftWireOpcode.layoutCollapsibleRow: ["CollapsibleRowLayout", "LayoutCollapsibleRow"],
    NativeSwiftWireOpcode.modifierWidthIn: ["WidthInModifierOperation", "ModifierWidthIn"],
    NativeSwiftWireOpcode.modifierHeightIn: ["HeightInModifierOperation", "ModifierHeightIn"],
    NativeSwiftWireOpcode.layoutCollapsibleColumn: ["CollapsibleColumnLayout", "LayoutCollapsibleColumn"],
    NativeSwiftWireOpcode.layoutImage: ["ImageLayout", "LayoutImage"],
    NativeSwiftWireOpcode.modifierCollapsiblePriority: ["CollapsiblePriorityModifierOperation", "ModifierCollapsiblePriority"],
    NativeSwiftWireOpcode.runAction: ["RunActionOperation", "RunAction"],
    NativeSwiftWireOpcode.modifierAlignBy: ["AlignByModifierOperation", "ModifierAlignBy"],
    NativeSwiftWireOpcode.layoutCompute: ["LayoutComputeOperation", "LayoutCompute"],
    NativeSwiftWireOpcode.coreText: ["CoreText"],
    NativeSwiftWireOpcode.layoutFlow: ["FlowLayout", "LayoutFlow"],
    NativeSwiftWireOpcode.skip: ["Skip"],
    NativeSwiftWireOpcode.textStyle: ["TextStyle"],
    NativeSwiftWireOpcode.modifierDimensionConstraints: ["DimensionConstraintsModifierOperation", "ModifierDimensionConstraints"],
    NativeSwiftWireOpcode.macroForEach: ["PatternForEach", "MacroForEach"],
    NativeSwiftWireOpcode.includeReferencedOperations: ["IncludeReferencedOperations"],
    NativeSwiftWireOpcode.macroDefine: ["PatternDefine", "MacroDefine"],
    NativeSwiftWireOpcode.macroCall: ["PatternInflation", "MacroCall"],
    NativeSwiftWireOpcode.macroArgument: ["PatternArgument", "MacroArgument"],
    NativeSwiftWireOpcode.macroBlock: ["PatternBlock", "MacroBlock"],
    NativeSwiftWireOpcode.accessibilitySemantics: ["CoreSemantics", "AccessibilitySemantics"],
    NativeSwiftWireOpcode.extensionRangeReserved4: ["ExtensionRangeReserved4"],
    NativeSwiftWireOpcode.extensionRangeReserved3: ["ExtensionRangeReserved3"],
    NativeSwiftWireOpcode.extensionRangeReserved2: ["ExtensionRangeReserved2"],
    NativeSwiftWireOpcode.extensionRangeReserved1: ["ExtensionRangeReserved1"],
    NativeSwiftWireOpcode.extendedOpcode: ["ExtendedOpcode"],
  ]

  /// Exposes decoded operation fields exactly as the corpus's `records` probes define them. These
  /// are document facts, not reconstructed AppKit animation state.
  private static func operationRecords(
    _ snapshot: NativeSwiftDocumentSnapshot, operationCount: Int, operationNames: [String]
  ) -> [String: Any] {
    func specRecord(_ id: Int, _ spec: NativeSwiftAnimationSpec) -> [String: Any] {
      [
        "animationId": id, "animationEnabled": id != 0,
        "enterAnimation": spec.enterAnimation, "exitAnimation": spec.exitAnimation,
        "motionDuration": spec.motionDuration, "motionEasingType": spec.motionEasingType,
        "visibilityDuration": spec.visibilityDuration,
        "visibilityEasingType": spec.visibilityEasingType,
      ]
    }
    let defaultSpec = NativeSwiftAnimationSpec(
      motionDuration: 300, motionEasingType: Int(NativeSwiftFloatEasingType.cubicStandard.rawValue),
      visibilityDuration: 300,
      visibilityEasingType: Int(NativeSwiftFloatEasingType.cubicStandard.rawValue),
      enterAnimation: NativeSwiftLayoutAnimation.fadeIn,
      exitAnimation: NativeSwiftLayoutAnimation.fadeOut)
    var components: [NativeSwiftNodeSnapshot] = []
    func collect(_ node: NativeSwiftNodeSnapshot) {
      if node.componentKind != "", node.kind != .root { components.append(node) }
      node.children.forEach(collect)
    }
    collect(snapshot.root)
    let componentIDs = components.map(\.componentID)
    // Bindings list only the leaf components, as the reference harness does: the corpus names the
    // boxes in a column and not the column that holds them.
    var leaves: [NativeSwiftNodeSnapshot] = []
    @discardableResult func collectLeaves(_ node: NativeSwiftNodeSnapshot) -> Bool {
      var below = false
      for child in node.children where collectLeaves(child) { below = true }
      let isComponent = node.componentKind != "" && node.kind != .root
      if isComponent, !below { leaves.append(node) }
      return isComponent || below
    }
    collectLeaves(snapshot.root)
    let bindings: [[String: Any]] = leaves.enumerated().map { index, node in
      let id = node.animationSpecID ?? -1
      let usesDefaultSpec = node.animationSpec == nil
      let spec = node.animationSpec ?? defaultSpec
      var record = specRecord(id, spec)
      record["componentAnimationId"] = id
      record["componentIndex"] = index
      record["componentType"] = node.componentKind
      record["usesDefaultSpec"] = usesDefaultSpec
      return record
    }
    let semantics: [[String: Any]] = snapshot.accessibilityRecords.map { accessibility in
      return [
        "contentDescriptionId": accessibility.contentDescriptionID,
        "role": accessibility.role, "textId": accessibility.textID,
        "stateDescriptionId": accessibility.stateDescriptionID, "mode": accessibility.mode,
        "enabled": accessibility.isEnabled, "clickable": accessibility.isClickable,
      ]
    }
    let conditionalTypes: [Int: String] = [
      NativeSwiftConditionalType.equal: "eq",
      NativeSwiftConditionalType.notEqual: "neq",
      NativeSwiftConditionalType.lessThan: "lt",
      NativeSwiftConditionalType.lessThanOrEqual: "lte",
      NativeSwiftConditionalType.greaterThan: "gt",
      NativeSwiftConditionalType.greaterThanOrEqual: "gte",
      NativeSwiftConditionalType.changed: "changed",
    ]
    let branches: [[String: Any]] = snapshot.conditionalTraces.map { trace in
      [
        "a": trace.left, "b": trace.right, "executed": trace.executed,
        "executedChildOps": trace.executedChildOps, "path": trace.path,
        "type": conditionalTypes[trace.type] ?? "unknown",
      ]
    }
    var anchoredRuns: [[String: Any]] = []
    func collectTextRuns(_ node: NativeSwiftNodeSnapshot) {
      for command in node.commands where command.kind == NativeSwiftDrawKind.text {
        let width = Float(command.text?.count ?? 0) * command.textSize * 0.5
        // A NaN panY leaves the baseline where the document put it, as the reference does.
        let panY = command.values[safe: 3] ?? -1
        anchoredRuns.append([
          "x": (command.values[safe: 0] ?? 0)
            - width * ((command.values[safe: 2] ?? -1) + 1) / 2,
          "y": (command.values[safe: 1] ?? 0)
            + (panY.isNaN ? 0 : command.textSize * (panY + 1) / 2),
        ])
      }
      node.children.forEach(collectTextRuns)
    }
    collectTextRuns(snapshot.root)
    var glyphRuns: [[String: Any]] = []
    var totalGlyphs = 0
    func collectGlyphRuns(_ node: NativeSwiftNodeSnapshot) {
      for command in node.commands
      where command.kind == NativeSwiftDrawKind.textOnPath
        || command.kind == NativeSwiftDrawKind.textOnCircle
      {
        let count = command.text?.count ?? 0
        totalGlyphs += count
        guard command.kind == NativeSwiftDrawKind.textOnPath, command.path.count >= 2 else {
          continue
        }
        let start = command.path[0].values
        let end = command.path[1].values
        guard start.count >= 2, end.count >= 2 else { continue }
        let dx = end[0] - start[0]
        let dy = end[1] - start[1]
        let length = hypotf(dx, dy)
        guard length > 0 else { continue }
        let h = command.values[safe: 0] ?? 0
        let v = command.values[safe: 1] ?? 0
        let unitX = dx / length
        let unitY = dy / length
        let firstX = start[0] + unitX * h - unitY * v
        let firstY = start[1] + unitY * h + unitX * v
        let horizontal = abs(dx) >= abs(dy)
        // No `label`: the corpus writes it as prose for the reader, and compares it only when a
        // player reports one.
        var run: [String: Any] = [
          "text": command.text ?? "", "glyphCount": count,
          "allRotationsDeg": horizontal ? 0 : 90,
        ]
        if horizontal {
          run["firstDeviceX"] = firstX
          run["allDeviceY"] = firstY
          run["deviceXOrder"] = "increasing"
        } else {
          run["allDeviceX"] = firstX
          run["firstDeviceY"] = firstY
          run["deviceYOrder"] = "increasing"
        }
        glyphRuns.append(run)
      }
      node.children.forEach(collectGlyphRuns)
    }
    collectGlyphRuns(snapshot.root)
    // Keyed by the core's `ParsedDrawCommand` kind, named as the reference names the opcode that
    // produced it (`RcOperationInventory` stable names, which the CMP lane records). Kinds 0-8 are
    // save/restore, translate, scale, rotate, skew, clipRect, clipPath and the path matrix: matrix
    // and clip state, not draws, so they are not recorded. Kind 17 is left out because the core
    // emits it for both DrawTextRun and DrawTextAnchor and the snapshot cannot tell the two apart.
    let drawNames: [Int: String] = [
      NativeSwiftDrawKind.rect: "DrawRect",
      NativeSwiftDrawKind.oval: "DrawOval",
      NativeSwiftDrawKind.circle: "DrawCircle",
      NativeSwiftDrawKind.line: "DrawLine",
      NativeSwiftDrawKind.roundRect: "DrawRoundRect",
      NativeSwiftDrawKind.arc: "DrawArc",
      NativeSwiftDrawKind.sector: "DrawSector",
      NativeSwiftDrawKind.path: "DrawPath",
      NativeSwiftDrawKind.bitmap: "DrawBitmap",
      NativeSwiftDrawKind.textOnPath: "DrawTextOnPath",
      NativeSwiftDrawKind.textOnCircle: "DrawTextOnCircle",
      NativeSwiftDrawKind.drawToBitmap: "DrawToBitmap",
      NativeSwiftDrawKind.tweenPath: "DrawTweenPath",
    ]
    var drawComponents: [String] = []
    func collectDrawComponents(_ node: NativeSwiftNodeSnapshot) {
      drawComponents.append(contentsOf: node.commands.compactMap { drawNames[$0.kind] })
      node.children.forEach(collectDrawComponents)
    }
    collectDrawComponents(snapshot.root)
    return [
      "ops_count": operationCount,
      "ops_present": Array(Set(operationNames)).sorted(),
      "ops_counts": operationCounts(operationNames),
      "component_count": components.count,
      "distinct_ids": Set(componentIDs).count == componentIDs.count,
      "animation_specs": snapshot.animationSpecOrder.compactMap { id in
        snapshot.animationSpecs[id].map { specRecord(id, $0) }
      },
      "component_bindings": bindings,
      "components": drawComponents,
      "semantics": semantics,
      "paths": Dictionary(
        uniqueKeysWithValues: snapshot.pathIDs.sorted().map { (String($0), ["present": true]) }),
      "tweens": Dictionary(
        uniqueKeysWithValues: snapshot.pathTweenIDs.sorted().map {
          (String($0), ["present": true])
        }),
      "uniforms": Dictionary(uniqueKeysWithValues: snapshot.shaderUniformNames.map { id, names in
        (String(id), Dictionary(uniqueKeysWithValues: names.map { ($0, true) }))
      }),
      "branches": branches,
      "impulses": snapshot.impulses.map {
        ["duration": Double($0.duration), "startAt": Double($0.startAt)]
      },
      "anchor_runs": anchoredRuns,
      "glyph_runs": glyphRuns,
      "total_glyphs": totalGlyphs,
    ]
  }

  /// The values a probe asked for, each resolved at the frame's own instant.
  ///
  /// A named target addresses a variable the document declared; a numeric one addresses a slot
  /// directly. A slot the document left empty reports null rather than being omitted — that is an
  /// observation, and the runner distinguishes it from a probe this player cannot answer.
  private static func reportedValues(
    session: NativeSwiftDocumentSession, timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock,
    request: NativeMacValueRequest
  ) throws -> [String: Any] {
    let resolved = try session.probeValues(timeSeconds: timeSeconds, wallClock: wallClock)
    func report(_ targets: [String], _ read: (Int) throws -> Any?) throws -> [String: Any] {
      var result: [String: Any] = [:]
      for target in targets {
        // A name the document never declared is left out, and that absence is what tells the runner
        // the probe is unobservable; a slot that exists and holds nothing reports null instead,
        // which is an observation.
        guard let slot = Int(target) ?? session.namedVariableID(target) else { continue }
        result[target] = try read(slot) ?? NSNull()
      }
      return result
    }
    // A *numeric* slot the document never wrote reads 0, which is what the reference's own state
    // arrays do — `expr_color_blending` and `expr_integer_bitwise_ops` assert exactly that for the
    // ids their (value-only, nothing-to-draw) documents never reach. Text is the exception: it has
    // no such zero, and a slot the document never wrote stays unobservable rather than reporting an
    // empty string the corpus never asserted.
    return [
      "floats": try report(request.floats) { id in
        resolved.floats[id].map { Double($0) } ?? 0
      },
      "integers": try report(request.integers) { resolved.integers[$0] ?? 0 },
      "texts": try report(request.texts) { resolved.texts[$0] },
      "colors": try report(request.colors) { id in
        resolved.colors[id].map { NSNumber(value: $0) } ?? NSNumber(value: UInt32(0))
      },
      "matrices": try report(request.matrices) { id in
        try session.probeMatrix(id: id, timeSeconds: timeSeconds)?.map(Double.init)
      },
      "float_arrays_dynamic": try report(request.dynamicFloatArrays) { id in
        try session.probeFloatList(id: id, dynamic: true, timeSeconds: timeSeconds)?
          .map(Double.init)
      },
      "float_arrays_data": try report(request.dataFloatArrays) { id in
        try session.probeFloatList(id: id, dynamic: false, timeSeconds: timeSeconds)?
          .map(Double.init)
      },
    ]
  }

  /// Machine-readable AppKit performance evidence for one document.
  ///
  /// The iOS lane measures the UIKit tree and `scripts/measure-native-swift-core.sh` measures the
  /// shared core; this covers what only AppKit can answer — building and drawing the native macOS
  /// view tree, and whether repeatedly replacing the document releases what it retained.
  static func measureEvidence(
    data: Data, fixture: String, iterations: Int = 5, frames: Int = 60
  ) throws -> NativeAppKitEvidenceReport {
    var decodeSamples: [Double] = []
    var buildSamples: [Double] = []
    var captureSamples: [Double] = []
    var viewCount = 0
    var labelCount = 0
    var controlCount = 0
    let residentBefore = nativeAppKitResidentBytes()

    for _ in 0..<iterations {
      var started = ProcessInfo.processInfo.systemUptime
      try NativeMacPolicy.validateDocument(data)
      let session = try NativeSwiftDocumentSession.open(data: data)
      let snapshot = try session.snapshot(timeSeconds: 0, wallClock: .capture)
      decodeSamples.append(nativeAppKitMilliseconds(since: started))

      started = ProcessInfo.processInfo.systemUptime
      let report = try NativeMacPolicy.evaluate(snapshot, compatibility: .compatible)
      let fonts = try NativeMacFontRegistry.register(snapshot: snapshot, downloadedFonts: [:])
      let player = try NativeMacDocumentView(
        snapshot: snapshot, resolvedAt: 0, wallClock: .capture, session: session,
        compatibility: .compatible, report: report,
        fonts: fonts, onEvent: { _ in }, onDiagnostics: { _ in }, onError: { _ in })
      let bounds = NSRect(x: 0, y: 0, width: snapshot.width, height: snapshot.height)
      let window = NSWindow(
        contentRect: bounds, styleMask: .borderless, backing: .buffered, defer: false)
      window.contentView = player
      player.frame = bounds
      player.layoutSubtreeIfNeeded()
      buildSamples.append(nativeAppKitMilliseconds(since: started))

      started = ProcessInfo.processInfo.systemUptime
      guard let bitmap = player.bitmapImageRepForCachingDisplay(in: player.bounds) else {
        throw NativeSwiftCoreError.malformed(offset: 0, reason: "Could not allocate AppKit capture")
      }
      player.cacheDisplay(in: player.bounds, to: bitmap)
      captureSamples.append(nativeAppKitMilliseconds(since: started))

      var views = 0
      var labels = 0
      var controls = 0
      nativeAppKitCount(player, views: &views, labels: &labels, controls: &controls)
      // Activatable semantic nodes are accessibility elements rather than NSControls now; count
      // them as the controls they replace.
      controls += (player.accessibilityChildren() ?? []).filter {
        ($0 as? NativeMacSemanticElement)?.action != nil
      }.count
      viewCount = max(viewCount, views)
      labelCount = max(labelCount, labels)
      controlCount = max(controlCount, controls)
      window.contentView = nil
    }

    // Steady state: the frame a display link drives, end to end, on one retained view — resolve,
    // reconcile the native tree, lay out, draw.
    let session = try NativeSwiftDocumentSession.open(data: data)
    let snapshot = try session.snapshot(timeSeconds: 0, wallClock: .capture)
    let steadyPlayer = try NativeMacDocumentView(
      snapshot: snapshot, resolvedAt: 0, wallClock: .capture, session: session,
      compatibility: .compatible,
      report: try NativeMacPolicy.evaluate(snapshot, compatibility: .compatible),
      fonts: try NativeMacFontRegistry.register(snapshot: snapshot, downloadedFonts: [:]),
      onEvent: { _ in }, onDiagnostics: { _ in }, onError: { _ in })
    let steadyBounds = NSRect(x: 0, y: 0, width: snapshot.width, height: snapshot.height)
    let steadyWindow = NSWindow(
      contentRect: steadyBounds, styleMask: .borderless, backing: .buffered, defer: false)
    steadyWindow.contentView = steadyPlayer
    steadyPlayer.frame = steadyBounds
    steadyPlayer.layoutSubtreeIfNeeded()
    guard let steadyBitmap = steadyPlayer.bitmapImageRepForCachingDisplay(in: steadyPlayer.bounds)
    else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Could not allocate AppKit capture")
    }
    var steadySamples: [Double] = []
    for frame in 0..<frames {
      let started = ProcessInfo.processInfo.systemUptime
      try steadyPlayer.renderEvidenceFrame(
        at: TimeInterval(frame) / 60, into: steadyBitmap)
      steadySamples.append(nativeAppKitMilliseconds(since: started))
    }
    steadyWindow.contentView = nil
    let residentAfter = nativeAppKitResidentBytes()

    return NativeAppKitEvidenceReport(
      fixture: fixture,
      iterations: iterations,
      frames: frames,
      metrics: NativeAppKitEvidenceReport.Metrics(
        medianDecodeMilliseconds: nativeAppKitMedian(decodeSamples),
        medianBuildMilliseconds: nativeAppKitMedian(buildSamples),
        medianCaptureMilliseconds: nativeAppKitMedian(captureSamples),
        medianSteadyFrameMilliseconds: nativeAppKitMedian(steadySamples),
        viewCount: viewCount,
        labelCount: labelCount,
        controlCount: controlCount,
        residentByteGrowth: Int64(residentAfter) - Int64(residentBefore)))
  }

  static func downloadableFontFamilies(data: Data) throws -> [String] {
    try NativeMacPolicy.validateDocument(data)
    let session = try NativeSwiftDocumentSession.open(data: data)
    return NativeMacFontRegistry.requests(
      in: try session.snapshot(timeSeconds: 0, wallClock: nativeSystemWallClock()).root
    ).map(\.family)
  }

  public func open(
    data: Data,
    title: String,
    compatibility: NativeMacCompatibility,
    downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
    onFontFallback: @escaping (String) -> Void = { _ in },
    onEvent: @escaping (String) -> Void,
    onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void,
    onError: @escaping (String) -> Void
  ) async throws {
    try await openNative(
      data: data, title: title, compatibility: compatibility,
      downloadableFontResolver: downloadableFontResolver,
      onFontFallback: onFontFallback,
      onEvent: { onEvent(nativeEventSummary($0)) },
      onDiagnostics: onDiagnostics, onError: onError)
  }

  /// Opens a native AppKit player and forwards the wire-level host event without reducing it to
  /// a diagnostic string. SwiftUI clients use this path so named-action values remain typed.
  public func openNative(
    data: Data,
    title: String,
    compatibility: NativeMacCompatibility,
    width: CGFloat? = nil,
    height: CGFloat? = nil,
    opaque: Bool = true,
    downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
    onFontFallback: @escaping (String) -> Void = { _ in },
    onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void,
    onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void,
    onError: @escaping (String) -> Void
  ) async throws {
    try NativeMacPolicy.validateDocument(data)
    let session = try NativeSwiftDocumentSession.open(data: data)
    let wallClock = nativeSystemWallClock()
    let snapshot = try session.snapshot(timeSeconds: 0, wallClock: wallClock)
    let report = try NativeMacPolicy.evaluate(snapshot, compatibility: compatibility)
    let fonts: NativeMacFontRegistry
    do {
      fonts = try await NativeMacFontRegistry.resolve(
        snapshot: snapshot, resolver: downloadableFontResolver)
    } catch is CancellationError {
      throw CancellationError()
    } catch {
      onFontFallback("Google Fonts unavailable; using the system font: \(error.localizedDescription)")
      fonts = NativeMacFontRegistry()
    }
    let player = try NativeMacDocumentView(
      snapshot: snapshot, resolvedAt: 0, wallClock: wallClock, session: session,
      compatibility: compatibility, report: report, fonts: fonts,
      onEvent: { event in
        guard case let .namedAction(name, value) = event else { return }
        onEvent(.namedAction(name: name, value: nativePlayerActionValue(value)))
      }, onDiagnostics: onDiagnostics, onError: onError)
    let scroll = NSScrollView()
    scroll.drawsBackground = opaque
    scroll.backgroundColor = opaque ? .windowBackgroundColor : .clear
    scroll.hasHorizontalScroller = true
    scroll.hasVerticalScroller = true
    player.frame = NSRect(
      x: 0, y: 0, width: CGFloat(snapshot.width), height: CGFloat(snapshot.height))
    scroll.documentView = player

    let size = NSSize(
      width: max(width ?? max(CGFloat(snapshot.width), 640), 1),
      height: max(height ?? max(CGFloat(snapshot.height), 480), 1))
    let window = NSWindow(
      contentRect: NSRect(origin: .zero, size: size),
      styleMask: [.titled, .closable, .miniaturizable, .resizable],
      backing: .buffered,
      defer: false)
    window.title = "\(title) — Native AppKit POC (\(compatibility.title))"
    window.isOpaque = opaque
    window.backgroundColor = opaque ? .windowBackgroundColor : .clear
    window.contentView = scroll
    window.delegate = self
    window.center()
    window.makeKeyAndOrderFront(nil)
    windows.append(window)
  }

  public func windowWillClose(_ notification: Notification) {
    guard let window = notification.object as? NSWindow else { return }
    windows.removeAll { $0 === window }
  }
}

@MainActor
private final class NativeMacFontRegistry {
  private struct Registration {
    let data: Data
    let font: CGFont
    var owners: Int
  }

  private static var registrations: [String: Registration] = [:]
  private(set) var namesByID: [Int: String] = [:]
  private var ownedNames = Set<String>()

  static func resolve(
    snapshot: NativeSwiftDocumentSnapshot,
    resolver: (any RemoteComposeDownloadableFontResolving)?
  ) async throws -> NativeMacFontRegistry {
    let registry = NativeMacFontRegistry()
    guard let resolver else { return registry }
    for request in requests(in: snapshot.root) {
      let font = try await resolver.resolve(
        RemoteComposeDownloadableFontRequest(family: request.family))
      guard font.family.caseInsensitiveCompare(request.family) == .orderedSame else {
        throw RemoteComposeDownloadableFontError.familyMismatch(
          expected: request.family, actual: font.family)
      }
      try Task.checkCancellation()
      registry.namesByID[request.id] = try registry.register(data: font.data, id: request.id)
    }
    return registry
  }

  static func register(
    snapshot: NativeSwiftDocumentSnapshot,
    downloadedFonts: [String: RemoteComposeDownloadedFont]
  ) throws -> NativeMacFontRegistry {
    let registry = NativeMacFontRegistry()
    for request in requests(in: snapshot.root) {
      guard let font = downloadedFonts[request.family.lowercased()] else { continue }
      guard font.family.caseInsensitiveCompare(request.family) == .orderedSame else {
        throw RemoteComposeDownloadableFontError.familyMismatch(
          expected: request.family, actual: font.family)
      }
      registry.namesByID[request.id] = try registry.register(data: font.data, id: request.id)
    }
    return registry
  }

  static func requests(in root: NativeSwiftNodeSnapshot) -> [(id: Int, family: String)] {
    var byID: [Int: String] = [:]
    var pending = [root]
    while let node = pending.popLast() {
      if let value = node.text?.familyName?.trimmingCharacters(in: .whitespacesAndNewlines),
        value.lowercased().hasPrefix("google:")
      {
        let family = String(value.dropFirst("google:".count)).trimmingCharacters(
          in: .whitespacesAndNewlines)
        if !family.isEmpty { byID[node.text!.familyID] = family }
      }
      pending.append(contentsOf: node.children)
    }
    return byID.sorted { $0.key < $1.key }.map { ($0.key, $0.value) }
  }

  private func register(data: Data, id: Int) throws -> String {
    guard
      let provider = CGDataProvider(data: data as CFData),
      let font = CGFont(provider),
      let name = font.postScriptName as String?
    else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Downloaded font \(id) is invalid")
    }
    if ownedNames.contains(name) {
      guard Self.registrations[name]?.data == data else {
        throw NativeSwiftCoreError.malformed(
          offset: 0, reason: "Downloaded font \(id) conflicts with \(name)")
      }
      return name
    }
    if var existing = Self.registrations[name] {
      guard existing.data == data else {
        throw NativeSwiftCoreError.malformed(
          offset: 0, reason: "Downloaded font \(id) conflicts with \(name)")
      }
      existing.owners += 1
      Self.registrations[name] = existing
      ownedNames.insert(name)
      return name
    }
    var error: Unmanaged<CFError>?
    guard CTFontManagerRegisterGraphicsFont(font, &error) else {
      throw NativeSwiftCoreError.malformed(
        offset: 0, reason: "Downloaded font \(id) could not be registered")
    }
    Self.registrations[name] = Registration(data: data, font: font, owners: 1)
    ownedNames.insert(name)
    return name
  }

  deinit {
    let names = ownedNames
    // Release synchronously when the last owner drops on the main thread, so a replacement
    // registry created right after can register the same PostScript name.
    if Thread.isMainThread {
      MainActor.assumeIsolated { Self.release(names) }
    } else {
      Task { @MainActor in Self.release(names) }
    }
  }

  private static func release(_ names: Set<String>) {
    for name in names {
      guard var registration = registrations[name] else { continue }
      registration.owners -= 1
      if registration.owners == 0 {
        var error: Unmanaged<CFError>?
        CTFontManagerUnregisterGraphicsFont(registration.font, &error)
        registrations.removeValue(forKey: name)
      } else {
        registrations[name] = registration
      }
    }
  }
}

private struct MacInsets: Equatable {
  let top: CGFloat
  let left: CGFloat
  let bottom: CGFloat
  let right: CGFloat
  static let zero = MacInsets(top: 0, left: 0, bottom: 0, right: 0)
}

private final class NativeMacDocumentView: NSView {
  private let session: NativeSwiftDocumentSession
  private let compatibility: NativeMacCompatibility
  private let onEvent: (NativeSwiftEvent) -> Void
  private let onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void
  private let onError: (String) -> Void
  private var snapshot: NativeSwiftDocumentSnapshot
  /// How long the document has been on screen, which a marquee's component reads during layout.
  var marqueeElapsedSeconds: TimeInterval { snapshot.marqueeElapsedSeconds }

  /// A marquee started or stopped overflowing. Re-decided after the layout pass that noticed, so the
  /// frame driver is not rebuilt from inside it.
  func marqueeDemandChanged() {
    DispatchQueue.main.async { [weak self] in self?.updateFrameDriver() }
  }
  private let images: [Int: NSImage]
  private let fonts: NativeMacFontRegistry
  private let conformanceFontName: String?
  private var reportedDiagnostics: RemoteComposeNativePlayerDiagnostics?
  /// The document's `DrawToBitmap` targets, shared by every canvas in the component tree (and a
  /// StateLayout's outgoing branch) so a bitmap id is one target for the whole document, as the
  /// core's resource budget counts it.
  private let offscreenTargets = NativeOffscreenTargets()
  private var component: NativeMacComponentView!
  /// The outgoing StateLayout branch during a native transition. AppKit owns the interpolation here
  /// — this is intentionally a view transition, not a second implementation of Android's layout
  /// animator.
  private var outgoingStateComponent: NativeMacComponentView?
  private var stateTransition: NativeMacStateTransition?
  private var appliedMeasurements: [Int: NativeSwiftMeasuredSize] = [:]
  private var isRefiningBoundGeometry = false
  private var currentSnapshotUsesMeasurements = false
  /// The instant and wall clock the installed snapshot was resolved at. A refinement re-resolves
  /// the session against real geometry at this same instant, as the UIKit host does: resolving at
  /// the live clock instead would replace a conformance capture's frame, and a `.capture` wall
  /// clock, with whatever the process clock says.
  private var snapshotTimeSeconds: TimeInterval
  private var snapshotWallClock: NativeSwiftWallClock?

  /// The active document component tree, for the conformance corpus's `tree` probe.
  ///
  /// One entry per component the corpus names — a structural content wrapper has no class in the
  /// vocabulary and is skipped, though its children are not — with the geometry its parent's layout
  /// assigned it. `depth` counts those named ancestors, so it matches the golds' numbering rather
  /// than this view hierarchy's. An outgoing StateLayout branch is a transient AppKit presentation
  /// view and deliberately stays out of this logical-state observation.
  ///
  /// §2.7: a node's x/y is only what the layout manager assigned. Padding and offset are modifier
  /// translation, applied at paint time, and are *not* in them — a padded child reports x: 0 and
  /// the padding shows up as the parent being larger. This renderer places children inside the
  /// parent's padded content rect and adds the child's own offset when it frames them, so both are
  /// taken back out here; the parent's reported size already carries the padding.
  func layoutTree() -> [[String: Any]] {
    var nodes: [[String: Any]] = []
    func walk(
      _ view: NativeMacComponentView, depth: Int, parentPadding: MacInsets, parentShift: CGFloat
    ) {
      let kind = view.node.componentKind
      let named = !kind.isEmpty
      var childDepth = depth
      if named {
        let frame = view.frame
        // The effective visibility, not the document's: a container that collapsed this child away
        // — or a state layout that is showing another branch — has hidden it, and the corpus reads
        // that as GONE. Taking the document's own field reported a dropped child as VISIBLE while
        // its pixels were absent, which is exactly the disagreement the tree channel exists to
        // catch.
        let gone =
           view.isHidden
             || (view.node.visibility == NativeSwiftVisibility.gone && !view.ignoresOwnVisibility)
        var entry: [String: Any] = [
          "id": view.node.componentID,
          "kind": kind,
          "x": Double(
            frame.origin.x - parentPadding.left - CGFloat(view.node.offsetX) - parentShift),
          "y": Double(frame.origin.y - parentPadding.top - CGFloat(view.node.offsetY)),
          "width": Double(frame.size.width),
          "height": Double(frame.size.height),
          "depth": depth,
          "isGone": gone,
          "visibility": gone
             ? "GONE"
              : (view.node.visibility == NativeSwiftVisibility.invisible
                && !view.ignoresOwnVisibility
                ? "INVISIBLE" : "VISIBLE"),
        ]
        // §4.3 reports the paint translation on the scrolled component, while its children keep
        // their layout positions. Zero is omitted rather than serialized as a meaningless field.
        if abs(view.node.scrollOffset) > Float.ulpOfOne {
          if view.node.scrollDirection == .horizontal {
            entry["scroll_x"] = Double(-view.node.scrollOffset)
          } else if view.node.scrollDirection == .vertical {
            entry["scroll_y"] = Double(-view.node.scrollOffset)
          }
        }
        // A marquee is a scroll the clock drives, and the corpus reads its offset the same way.
        if view.node.marquee != nil, abs(view.marqueeOffset) > CGFloat(Float.ulpOfOne) {
          entry["scroll_x"] = Double(view.marqueeOffset)
        }
        nodes.append(entry)
        childDepth = depth + 1
      }
      // A structural wrapper is transparent to layout: it sits at its parent's origin and carries no
      // padding of its own, so the inset its children were placed against is the nearest named
      // ancestor's.
      let padding = named ? view.insets : parentPadding
      // A container marquee slides its children's frames, but the reference translates them at
      // paint time, so their layout positions are reported without the slide; it is the
      // container's `scroll_x`. A structural child's own children sit in its coordinates, which
      // already carry the slide, so only the direct children take it back out.
      let shift = view.node.marquee != nil && view.node.kind != .text ? view.marqueeOffset : 0
      for child in view.componentChildren {
        walk(child, depth: childDepth, parentPadding: padding, parentShift: shift)
      }
    }
    walk(component, depth: 0, parentPadding: MacInsets.zero, parentShift: 0)
    // §4.3: nodes are sorted by component id ascending, which puts a node after all its descendants.
    return nodes.sorted { ($0["id"] as? Int ?? 0) < ($1["id"] as? Int ?? 0) }
  }
  private var displayLinkDriver: AnyObject?
  private var fallbackFrameTimer: Timer?
  private var delayedWakeTimer: Timer?
  private var remainingWake: TimeInterval?
  private var wakeStartedAt: TimeInterval?
  /// Active animation time, shared with the UIKit host so both clocks accumulate identically.
  private var timeline = NativeAnimationTimeline()

  override var isFlipped: Bool { true }

  init(
    snapshot: NativeSwiftDocumentSnapshot,
    resolvedAt timeSeconds: TimeInterval,
    wallClock: NativeSwiftWallClock?,
    session: NativeSwiftDocumentSession,
    compatibility: NativeMacCompatibility,
    report: NativeMacPolicyReport,
    fonts: NativeMacFontRegistry,
    conformanceFontName: String? = nil,
    onEvent: @escaping (NativeSwiftEvent) -> Void,
    onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void,
    onError: @escaping (String) -> Void
  ) throws {
    self.snapshot = snapshot
    snapshotTimeSeconds = timeSeconds
    snapshotWallClock = wallClock
    self.fonts = fonts
    self.conformanceFontName = conformanceFontName
    images = try Dictionary(
      uniqueKeysWithValues: snapshot.images.map { resource in
        guard resource.encoding == NativeSwiftBitmapEncoding.inline,
          let image = NSImage(data: resource.data)
        else {
          throw NativeSwiftCoreError.malformed(
            offset: 0, reason: "Could not decode embedded image \(resource.id)")
        }
        if let representation = image.representations.first,
          representation.pixelsWide != resource.width
            || representation.pixelsHigh != resource.height
        {
          throw NativeSwiftCoreError.malformed(
            offset: 0, reason: "Embedded image \(resource.id) dimensions do not match its data")
        }
        return (resource.id, image)
      })
    self.session = session
    self.compatibility = compatibility
    self.onEvent = onEvent
    self.onDiagnostics = onDiagnostics
    self.onError = onError
    super.init(
      frame: NSRect(
        x: 0, y: 0, width: CGFloat(snapshot.width),
        height: CGFloat(snapshot.height)))
    timeline.resume(at: Self.now)
    try install(snapshot, report: report)
    // The document is the one accessibility container, as on UIKit: it lists every element its
    // components publish, and each semantic element names it as its parent.
    setAccessibilityElement(true)
    setAccessibilityRole(.group)
    setAccessibilityIdentifier("rc-native-document")
    NotificationCenter.default.addObserver(
      self, selector: #selector(applicationDidBecomeActive),
      name: NSApplication.didBecomeActiveNotification, object: nil)
    NotificationCenter.default.addObserver(
      self, selector: #selector(applicationDidResignActive),
      name: NSApplication.didResignActiveNotification, object: nil)
    NSWorkspace.shared.notificationCenter.addObserver(
      self, selector: #selector(accessibilityDisplayOptionsDidChange),
      name: NSWorkspace.accessibilityDisplayOptionsDidChangeNotification, object: nil)
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

  deinit {
    // `deinit` is nonisolated, and the frame driver and timers are main-actor state. AppKit
    // releases its views on the main thread, and there they are stopped synchronously, as they
    // always were. A view released anywhere else leaves them to lapse on their own: the display
    // link invalidates itself on its next tick once its owner is gone, and a pending one-shot timer
    // finds a nil `self`. (`MainActor.assumeIsolated` is emitted into the client and back-deploys
    // to macOS 10.15, so this does not raise the floor.)
    if Thread.isMainThread {
      MainActor.assumeIsolated {
        stopDisplayFrames()
        delayedWakeTimer?.invalidate()
        NSWorkspace.shared.notificationCenter.removeObserver(self)
      }
    }
    NotificationCenter.default.removeObserver(self)
  }

  /// Re-resolves the document at a later instant and lays it out again.
  ///
  /// The conformance lane drives gestures between the frame it hit-tested against and the frame it
  /// captures, and this is the step between them: the document's own state has changed, so the view
  /// has to be re-resolved from a fresh snapshot and reconciled onto the native tree, not redrawn.
  func refresh(timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock) throws {
    try resolveAndInstall(timeSeconds: timeSeconds, wallClock: wallClock)
    layoutSubtreeIfNeeded()
  }

  /// Resolves the document at `timeSeconds` with the component sizes the last layout measured, and
  /// installs it. Supplying the measurements up front is what keeps a document with bound geometry
  /// to one resolution per frame: `refineBoundGeometry()` re-resolves only when a layout pass
  /// actually changes a bound component's size, instead of first installing an unmeasured frame
  /// and then correcting it on every tick and every click.
  private func resolveAndInstall(
    timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock?, events: [NativeSwiftEvent] = []
  ) throws {
    let next = try session.snapshot(
      timeSeconds: timeSeconds, wallClock: wallClock, measuredComponents: appliedMeasurements)
    snapshotTimeSeconds = timeSeconds
    snapshotWallClock = wallClock
    try install(next, events: events, usesMeasurements: !appliedMeasurements.isEmpty)
  }

  /// The component a point lands on, for the conformance lane's input steps.
  ///
  /// Walks outwards from the deepest view under the point: the first component that *accepts* this
  /// gesture is the one the reference would have delivered to, and one that merely contains the point
  /// is not.
  func gestureTarget(at point: CGPoint, for kind: NativeSwiftGestureKind) -> Int? {
    // `point` is in this view's (document) coordinates; `hitTest` wants its superview's, and the
    // flip between them is the difference between a click landing and missing by a mirrored y.
    let hitPoint = superview.map { convert(point, to: $0) } ?? point
    var view: NSView? = hitTest(hitPoint)
    while let current = view {
      if let component = current as? NativeMacComponentView,
        component.node.supportedGestures.contains(kind)
      {
        return component.node.componentID
      }
      view = current.superview
    }
    return nil
  }

  /// The innermost scrolled component under a document-space point. Scroll handling belongs to the
  /// modifier itself, so this geometric lookup must not require the component to declare a document
  /// gesture action.
  func scrollTarget(at point: CGPoint) -> NativeMacScrollTarget? {
    component.scrollTarget(at: point)
  }

  private func install(
    _ next: NativeSwiftDocumentSnapshot,
    events: [NativeSwiftEvent] = [],
    report suppliedReport: NativeMacPolicyReport? = nil,
    usesMeasurements: Bool = false
  ) throws {
    let report: NativeMacPolicyReport
    if let suppliedReport {
      report = suppliedReport
    } else {
      report = try NativeMacPolicy.evaluate(next, compatibility: compatibility)
    }
    try NativeMacPolicy.validate(events: events, against: report)
    let outgoing = component
    let changedStateLayoutIDs = changedStateLayouts(
      from: snapshot.root, to: next.root)
    snapshot = next
    currentSnapshotUsesMeasurements = usesMeasurements
    if reportedDiagnostics != report.diagnostics {
      reportedDiagnostics = report.diagnostics
      onDiagnostics(report.diagnostics)
    }
    let refreshingTransition = changedStateLayoutIDs.isEmpty ? stateTransition : nil
    if changedStateLayoutIDs.isEmpty, let current = outgoing,
      current.canUpdate(with: snapshot.root, images: images)
    {
      // The common frame: the same components with new values. Reconcile in place, so gesture
      // recognizers, backing stores and an in-flight StateLayout cross-fade (its outgoing branch
      // and the incoming fade) all survive the frame; only children whose identity changed are
      // rebuilt, by their parent.
      current.update(
        node: snapshot.root, images: images, fontNames: fonts.namesByID,
        conformanceFontName: conformanceFontName)
      finishInstall()
      return
    }
    let presentationAlpha = refreshingTransition.flatMap { transition in
      outgoing?.component(withID: transition.stateLayoutID)?.layer?.presentation()?.opacity
    }
    component = NativeMacComponentView(
      node: snapshot.root, images: images, fontNames: fonts.namesByID,
      conformanceFontName: conformanceFontName, offscreenTargets: offscreenTargets
    ) {
      [weak self] componentID, gesture, sample in
      self?.gesture(gesture, componentID: componentID, sample: sample)
    }
    // A StateLayout switching branch rebuilds the whole tree and cross-fades the old one out: the
    // outgoing view is kept frozen on the old branch, so it cannot also be updated in place.
    if let changedStateLayoutID = changedStateLayoutIDs.first, let outgoing {
      let transition = stateTransition(for: changedStateLayoutID, in: snapshot)
      outgoingStateComponent?.removeFromSuperview()
      outgoingStateComponent = outgoing
      outgoing.frame = bounds
      component.frame = bounds
      addSubview(component)
      component.layoutSubtreeIfNeeded()
      // Keep the view logically interactive while Core Animation fades its layer in.
      component.layer?.opacity = 0
      addSubview(outgoing, positioned: .below, relativeTo: component)
      stateTransition = transition
      animateStateLayoutTransition(from: outgoing, to: component, transition: transition)
    } else {
      outgoingStateComponent?.removeFromSuperview()
      outgoingStateComponent = nil
      outgoing?.removeFromSuperview()
      if let transition = refreshingTransition,
        let incomingState = component.component(withID: transition.stateLayoutID)
      {
        incomingState.alphaValue = CGFloat(presentationAlpha ?? 1)
        addSubview(component)
        animateIncomingState(incomingState, transition: transition)
      } else {
        stateTransition = nil
        addSubview(component)
      }
    }
    finishInstall()
  }

  private func finishInstall() {
    // A document that reads a discrete wall-clock field has to be re-resolved at least once a
    // second, or its clock freezes on the first frame; one with a WAKE_IN or an impulse asks for
    // its own time. The driver re-arms this after each wake.
    remainingWake = snapshot.hostWakeAfter
    wakeStartedAt = nil
    needsLayout = true
    updateFrameDriver()
  }

  /// One complete AppKit animation frame for the evidence run: resolve the snapshot, reconcile the
  /// native tree, lay it out and draw it — the same work `frameTimerDidFire` does, minus the
  /// window and activation guards a headless run cannot satisfy. Timing only the core's
  /// `snapshot(timeSeconds:)` here would report a number the AppKit renderer never pays.
  fileprivate func renderEvidenceFrame(
    at timeSeconds: TimeInterval, into bitmap: NSBitmapImageRep
  ) throws {
    try resolveAndInstall(timeSeconds: timeSeconds, wallClock: .capture)
    layoutSubtreeIfNeeded()
    cacheDisplay(in: bounds, to: bitmap)
  }

  private func gesture(
    _ gesture: NativeSwiftGestureKind, componentID: Int, sample: NativeSwiftPointerSample?
  ) {
    do {
      guard
        let events = try session.gesture(
          gesture, componentID: componentID, sample: sample, timeSeconds: sampleTime())
      else { return }
      try resolveAndInstall(
        timeSeconds: sampleTime(), wallClock: nativeSystemWallClock(), events: events)
      for event in events { onEvent(event) }
    } catch {
      onError("Native input failed: \(error.localizedDescription)")
      NSSound.beep()
    }
  }

  /// The active component tree's elements in document order. An outgoing StateLayout branch is a
  /// transient presentation and is not listed, as it is not in the tree dump.
  override func accessibilityChildren() -> [Any]? {
    let children = component?.accessibilityOrder ?? []
    for case let element as NativeMacSemanticElement in children {
      element.setAccessibilityParent(self)
    }
    return children
  }

  /// The innermost listed element under a screen point. Semantic elements are not views, so
  /// AppKit's default view-based hit test cannot find them.
  ///
  /// The SDK declares this override nonisolated while the children and their frames are main-actor
  /// state. AppKit asks on the main thread, so the search runs there, and the non-Sendable result
  /// is handed out through a local rather than returned across the isolation boundary.
  override func accessibilityHitTest(_ point: NSPoint) -> Any? {
    nonisolated(unsafe) var hit: Any?
    MainActor.assumeIsolated { hit = semanticElement(at: point) }
    return hit ?? super.accessibilityHitTest(point)
  }

  private func semanticElement(at point: NSPoint) -> Any? {
    for child in (accessibilityChildren() ?? []).reversed() {
      if let element = child as? NativeMacSemanticElement,
        element.accessibilityFrame().contains(point)
      {
        return element
      }
      if let view = child as? NSView, view.accessibilityFrame().contains(point) {
        return view
      }
    }
    return nil
  }

  override func viewDidMoveToWindow() {
    super.viewDidMoveToWindow()
    if window == nil {
      pauseTimeline()
    } else if NSApplication.shared.isActive {
      resumeTimeline()
    }
    updateFrameDriver()
  }

  override func layout() {
    super.layout()
    component.frame = bounds
    refineBoundGeometry()
  }

  /// Re-resolves bindings that reference a component's measured size after AppKit has assigned
  /// real frames. This mirrors the UIKit host's two-pass layout without retaining a second session.
  private func refineBoundGeometry() {
    guard !isRefiningBoundGeometry, !snapshot.boundComponents.isEmpty else { return }
    var measured: [Int: NativeSwiftMeasuredSize] = [:]
    component.collectMeasuredSizes(of: snapshot.boundComponents, into: &measured)
    guard !currentSnapshotUsesMeasurements || measured != appliedMeasurements else { return }
    appliedMeasurements = measured
    isRefiningBoundGeometry = true
    defer { isRefiningBoundGeometry = false }
    do {
      try install(
        session.snapshot(
          timeSeconds: snapshotTimeSeconds, wallClock: snapshotWallClock,
          measuredComponents: measured),
        usesMeasurements: true)
    } catch {
      onError("Native geometry refinement failed: \(error.localizedDescription)")
    }
  }

  /// StateLayout branch changes are native AppKit cross-fades. The timing curve is provided by Core
  /// Animation; no Android interpolation or frame sampler is duplicated in this player.
  private func animateStateLayoutTransition(
    from outgoing: NativeMacComponentView, to incoming: NativeMacComponentView,
    transition: NativeMacStateTransition
  ) {
    NSAnimationContext.runAnimationGroup { context in
      context.duration = transition.duration
      context.timingFunction = transition.timingFunction
      outgoing.animator().alphaValue = 0
      incoming.layer?.opacity = 1
    } completionHandler: { [weak self, weak outgoing] in
      // The SDK does not promise this block runs on the main actor, so it only captures the two
      // views — main-actor isolated, hence Sendable — and hops to the main queue to tear down.
      guard let self, let outgoing else { return }
      DispatchQueue.main.async { self.finishStateTransition(from: outgoing) }
    }
  }

  private func finishStateTransition(from outgoing: NativeMacComponentView) {
    guard outgoingStateComponent === outgoing else { return }
    outgoing.removeFromSuperview()
    outgoingStateComponent = nil
    stateTransition = nil
  }

  private func animateIncomingState(
    _ incoming: NativeMacComponentView, transition: NativeMacStateTransition
  ) {
    let elapsed = ProcessInfo.processInfo.systemUptime - transition.startedAt
    let remaining = max(transition.duration - elapsed, 0)
    guard remaining > 0 else {
      incoming.alphaValue = 1
      return
    }
    NSAnimationContext.runAnimationGroup { context in
      context.duration = remaining
      context.timingFunction = transition.timingFunction
      incoming.animator().alphaValue = 1
    }
  }

  private func stateTransition(
    for stateLayoutID: Int, in snapshot: NativeSwiftDocumentSnapshot
  ) -> NativeMacStateTransition {
    // The spec the state layout adopts among its own operations, as the reference binds it; the
    // id it names is the fallback.
    let spec = snapshot.root.component(withID: stateLayoutID).flatMap { node in
      node.animationSpec ?? node.animationID.flatMap { snapshot.animationSpecs[$0] }
    }
    let duration = TimeInterval(spec?.motionDuration ?? 300) / 1_000
    return NativeMacStateTransition(
      stateLayoutID: stateLayoutID, duration: duration,
      timingFunction: NativeMacStateTransition.timingFunction(for: spec?.motionEasingType),
      startedAt: ProcessInfo.processInfo.systemUptime)
  }

  private func snapshotStateLayoutIndices(_ node: NativeSwiftNodeSnapshot) -> [Int: Int] {
    var indices: [Int: Int] = [:]
    func collect(_ current: NativeSwiftNodeSnapshot) {
      if let index = current.stateIndex { indices[current.componentID] = index }
      current.children.forEach(collect)
    }
    collect(node)
    return indices
  }

  private func changedStateLayouts(
    from oldRoot: NativeSwiftNodeSnapshot, to newRoot: NativeSwiftNodeSnapshot
  ) -> [Int] {
    let oldIndices = snapshotStateLayoutIndices(oldRoot)
    let newIndices = snapshotStateLayoutIndices(newRoot)
    return newIndices.keys.sorted().filter { oldIndices[$0] != newIndices[$0] }
  }

  private func updateFrameDriver() {
    pauseWakeCountdown()
    delayedWakeTimer?.invalidate()
    delayedWakeTimer = nil
    let mode = NativeMacFrameDriverMode.resolve(
      needsContinuousFrames: snapshot.needsContinuousFrames
        || component?.hasMovingMarquee == true,
      requestsNextFrame: false,
      wakeAfter: remainingWake,
      isActive: NSApplication.shared.isActive,
      isVisible: window != nil,
      reduceMotion: NSWorkspace.shared.accessibilityDisplayShouldReduceMotion)
    switch mode {
    case .displayLink:
      if #available(macOS 14.0, *) {
        fallbackFrameTimer?.invalidate()
        fallbackFrameTimer = nil
        if displayLinkDriver == nil {
          displayLinkDriver = NativeMacDisplayLinkDriver(view: self, owner: self)
        }
      } else {
        displayLinkDriver = nil
        fallbackFrameTimer?.invalidate()
        fallbackFrameTimer = schedule(after: 1.0 / 60.0, repeats: false)
      }
    case .wake(let delay):
      stopDisplayFrames()
      wakeStartedAt = Self.now
      delayedWakeTimer = schedule(after: max(delay, 0), repeats: false)
    case .idle:
      stopDisplayFrames()
    }
  }

  private func schedule(after delay: TimeInterval, repeats: Bool) -> Timer {
    // The block is a nonisolated `@Sendable` closure, so it hops to the main queue — whose closures
    // run on the main actor — rather than touching the view itself, re-capturing it weakly.
    let timer = Timer(timeInterval: delay, repeats: repeats) { [weak self] _ in
      DispatchQueue.main.async { [weak self] in self?.frameTimerDidFire() }
    }
    RunLoop.main.add(timer, forMode: .common)
    return timer
  }

  private func frameTimerDidFire() {
    guard window != nil, NSApplication.shared.isActive else {
      updateFrameDriver()
      return
    }
    remainingWake = nil
    wakeStartedAt = nil
    fallbackFrameTimer = nil
    delayedWakeTimer = nil
    do {
      try resolveAndInstall(timeSeconds: sampleTime(), wallClock: nativeSystemWallClock())
    } catch {
      stopDisplayFrames()
      onError("Native scheduled frame failed: \(error.localizedDescription)")
      NSSound.beep()
    }
  }

  fileprivate func displayLinkDidFire(targetTimestamp: TimeInterval) {
    guard window != nil, NSApplication.shared.isActive else {
      updateFrameDriver()
      return
    }
    do {
      let targetTime = sampleTime(at: targetTimestamp)
      try resolveAndInstall(timeSeconds: targetTime, wallClock: nativeSystemWallClock())
    } catch {
      stopDisplayFrames()
      onError("Native display frame failed: \(error.localizedDescription)")
      NSSound.beep()
    }
  }

  private func stopDisplayFrames() {
    if #available(macOS 14.0, *) {
      (displayLinkDriver as? NativeMacDisplayLinkDriver)?.invalidate()
    }
    displayLinkDriver = nil
    fallbackFrameTimer?.invalidate()
    fallbackFrameTimer = nil
  }

  private func sampleTime() -> TimeInterval {
    sampleTime(at: Self.now)
  }

  /// A display-link frame samples at its future `targetTimestamp`; a later sample at an earlier
  /// `now` must neither move time backwards nor rewind the anchor, or the gap is counted twice.
  /// `NativeAnimationTimeline.sample` keeps `max(now, lastActiveTime)` as the anchor. While paused,
  /// time does not advance — and sampling must not quietly resume it.
  private func sampleTime(at now: TimeInterval) -> TimeInterval {
    timeline.isRunning ? timeline.sample(at: now) : timeline.elapsed
  }

  private func pauseTimeline() {
    timeline.pause(at: Self.now)
  }

  private func resumeTimeline() {
    timeline.resume(at: Self.now)
  }

  private func pauseWakeCountdown() {
    guard let wakeStartedAt, let remainingWake else { return }
    self.remainingWake = max(remainingWake - max(Self.now - wakeStartedAt, 0), 0)
    self.wakeStartedAt = nil
  }

  @objc private func applicationDidBecomeActive() {
    resumeTimeline()
    updateFrameDriver()
  }

  @objc private func applicationDidResignActive() {
    pauseTimeline()
    updateFrameDriver()
  }

  @objc private func accessibilityDisplayOptionsDidChange() {
    if NSWorkspace.shared.accessibilityDisplayShouldReduceMotion {
      pauseTimeline()
    } else if NSApplication.shared.isActive, window != nil {
      resumeTimeline()
    }
    updateFrameDriver()
  }

  private static var now: TimeInterval { ProcessInfo.processInfo.systemUptime }

}

/// The display link's target. Main-actor isolated: it is created by the document view and its link
/// is scheduled on the main run loop, so every callback lands where the view's state lives.
@available(macOS 14.0, *)
@MainActor
private final class NativeMacDisplayLinkDriver: NSObject {
  private weak var owner: NativeMacDocumentView?
  private var link: CADisplayLink!

  init(view: NSView, owner: NativeMacDocumentView) {
    self.owner = owner
    super.init()
    link = view.displayLink(target: self, selector: #selector(fire(_:)))
    link.add(to: .main, forMode: .common)
  }

  func invalidate() {
    link?.invalidate()
    link = nil
  }

  @objc private func fire(_ link: CADisplayLink) {
    // The link retains this target, not the view. A view released without stopping it (off the
    // main thread, where its `deinit` cannot) is noticed here, and the link stops itself.
    guard let owner else {
      invalidate()
      return
    }
    owner.displayLinkDidFire(targetTimestamp: link.targetTimestamp)
  }
}

private typealias NativeMacNode = NativeSwiftNodeSnapshot

/// The accessibility policy reads each component as it is displayed rather than as the document
/// wrote it: a FitBox shows an alternative whose own visibility modifier is GONE, and a merging
/// ancestor has to take that alternative's label and action. Everything else is the node's.
extension NativeMacComponentView: @preconcurrency NativeAccessibilityNode {
  var semanticComponentID: Int { node.semanticComponentID }
  var semanticDescriptor: NativeAccessibilityDescriptor? { node.semanticDescriptor }
  var semanticLocalLabels: [String] { node.semanticLocalLabels }
  var semanticClickActionTypes: [NativeSwiftGestureKind] { node.semanticClickActionTypes }
  var isSemanticallyVisible: Bool {
    !isHidden && (node.isSemanticallyVisible || ignoresOwnVisibility)
  }
  var semanticChildren: [NativeMacComponentView] { componentChildren }
}

private extension NativeSwiftNodeSnapshot {
  func component(withID id: Int) -> NativeSwiftNodeSnapshot? {
    if componentID == id { return self }
    for child in children {
      if let match = child.component(withID: id) { return match }
    }
    return nil
  }
}
private typealias NativeMacDrawCommand = NativeSwiftDrawCommandSnapshot
private typealias NativeMacPathCommand = NativeSwiftPathElementSnapshot

private extension NativeSwiftNodeSnapshot {
  var componentId: Int { componentID }
  var paddingTop: Float { padding.top }
  var paddingLeft: Float { padding.left }
  var paddingBottom: Float { padding.bottom }
  var paddingRight: Float { padding.right }
  var hasBackground: Bool { backgroundARGB != nil }
  var backgroundColor: Int32 { Int32(bitPattern: backgroundARGB ?? 0) }
}

private extension NativeSwiftDrawCommandSnapshot {
  var first: Float { values[safe: 0] ?? 0 }
  var second: Float { values[safe: 1] ?? 0 }
  var third: Float { values[safe: 2] ?? 0 }
  var fourth: Float { values[safe: 3] ?? 0 }
  var fifth: Float { values[safe: 4] ?? 0 }
  var sixth: Float { values[safe: 5] ?? 0 }
  var color: Int32 { Int32(bitPattern: colorARGB) }
  var stroke: Bool { isStroke }
}

private extension NativeSwiftPathElementSnapshot {
  var first: Float { values[safe: 0] ?? 0 }
  var second: Float { values[safe: 1] ?? 0 }
  var third: Float { values[safe: 2] ?? 0 }
  var fourth: Float { values[safe: 3] ?? 0 }
  var fifth: Float { values[safe: 4] ?? 0 }
  var sixth: Float { values[safe: 5] ?? 0 }
}

private extension Collection {
  subscript(safe index: Index) -> Element? { indices.contains(index) ? self[index] : nil }
}

/// How the shared accessibility policy reads on AppKit: the role, subrole and identifier each
/// element kind announces, and whether a node publishes an element at all. Kept apart from the
/// views so it is testable without a window.
@MainActor
enum NativeAppKitAccessibility {
  static func role(for descriptor: NativeAccessibilityDescriptor) -> NSAccessibility.Role {
    switch descriptor.elementKind {
    case .button: return .button
    case .checkbox, .toggle: return .checkBox
    case .radioButton, .tab: return .radioButton
    case .dropdownList, .picker: return .popUpButton
    case .image: return descriptor.isClickable ? .button : .image
    case .carousel: return descriptor.isClickable ? .button : .group
    case .generic: return descriptor.isClickable ? .button : .staticText
    }
  }

  /// A toggle is a switch-styled checkbox and a tab a tab-styled radio button, as AppKit's own
  /// `NSSwitch` and `NSTabView` report them.
  static func subrole(for kind: NativeAccessibilityElementKind) -> NSAccessibility.Subrole? {
    switch kind {
    case .toggle: return NSAccessibility.Subrole(rawValue: "AXSwitch")
    case .tab: return NSAccessibility.Subrole(rawValue: "AXTabButton")
    case .button, .checkbox, .radioButton, .image, .dropdownList, .picker, .carousel, .generic:
      return nil
    }
  }

  /// The same identifier the UIKit host gives the node's element, e.g. `rc-native-button-12`.
  static func identifier(
    for descriptor: NativeAccessibilityDescriptor, componentID: Int
  ) -> String {
    "rc-native-\(String(describing: descriptor.elementKind))-\(componentID)"
  }

  /// Whether the node's element is worth announcing — the UIKit host's rule: it has something to
  /// say, a state, an action, or a role beyond a plain container.
  static func publishes(
    _ descriptor: NativeAccessibilityDescriptor, label: String?, activates: Bool
  ) -> Bool {
    if label != nil || descriptor.stateDescription != nil || activates { return true }
    if descriptor.isClickable || !descriptor.isEnabled { return true }
    switch descriptor.elementKind {
    case .button, .checkbox, .toggle, .radioButton, .tab, .image, .dropdownList: return true
    case .picker, .carousel, .generic: return false
    }
  }
}

/// A component's VoiceOver identity: role, label, value and activation, without a view.
///
/// The document owns every pixel and every pointer gesture, so the semantic node is a plain
/// accessibility element rather than a control overlaid on the component. It announces the owning
/// component view's area (the structural union for a flattened component), and activation
/// dispatches the same tap event the pointer path does.
private final class NativeMacSemanticElement: NSAccessibilityElement {
  let kind: NativeAccessibilityElementKind
  /// Dispatches the node's tap; nil when it has none or is disabled.
  var action: (() -> Void)?
  /// Whether the document lists this element; mirrors `isAccessibilityElement()`.
  var isPublished = false
  private weak var owner: NativeMacComponentView?

  init(owner: NativeMacComponentView, kind: NativeAccessibilityElementKind) {
    self.owner = owner
    self.kind = kind
    super.init()
  }

  /// Resolved on every read, in screen coordinates, so a scrolled ancestor or a moved window is
  /// never stale.
  override func accessibilityFrame() -> NSRect {
    // Nonisolated in the SDK; the owning view's geometry is main-actor state, read on AppKit's
    // main-thread accessibility query. Capture the view, not `self`, so nothing non-Sendable
    // crosses into the main-actor closure.
    let owner = self.owner
    return MainActor.assumeIsolated { owner?.semanticScreenFrame ?? .zero }
  }

  override func accessibilityPerformPress() -> Bool {
    guard let action else { return false }
    action()
    return true
  }
}

/// `NativeLayoutItem` is nonisolated so the shared engine runs in tests without views. This view
/// only hands itself to the engine from its own main-actor methods, which the `@preconcurrency`
/// conformance checks at run time — as the UIKit renderer's component view does.
private final class NativeMacComponentView: NSView, NSGestureRecognizerDelegate,
  @preconcurrency NativeLayoutItem
{
  /// The resolved component this view draws. Read by the document view's tree dump, which the
  /// conformance corpus's `tree` probe reads.
  ///
  /// Replaced in place when the document view reconciles a newer snapshot onto this view (see
  /// `canUpdate(with:images:)`), so gesture handlers always dispatch against the current node.
  private(set) var node: NativeMacNode
  /// What the shared layout engine reads of `node`, kept in step with it.
  private(set) var layoutNode: NativeLayoutNode

  /// Set by a `FitBox` on its alternatives. The reference ignores an alternative's own visibility
  /// modifier — a document switches alternatives with it — so the tree reports the box's choice
  /// rather than the modifier's, and the fit test sees the alternative's real size.
  var ignoresOwnVisibility = false
  /// The horizontal offset a marquee draws its text at, placed by `layout()`. The tree reports it as
  /// `scroll_x`.
  private(set) var marqueeOffset: CGFloat = 0
  /// What autosize chose for each space this text was measured in. The size is the one the
  /// constraint it was measured under gave — as Compose chooses it — so layout reuses it rather than
  /// searching again inside the box it produced, where "strictly shorter" would pick a smaller one.
  private var autosizedTexts: [MacAvailableSpace: MacAutosizedText] = [:]
  /// Whether the marquee's content overflows, so it moves and needs frames.
  private var marqueeMoves = false
  private(set) var componentChildren: [NativeMacComponentView]
  private let canvas: NativeMacCanvasView?
  private let labels: [NSTextField]
  private let imageViews: [NSImageView]
  /// This component's VoiceOver identity, when the node has semantics of its own. It is not a
  /// view: the document view publishes it, and this view only supplies its frame and activation.
  private var semanticElement: NativeMacSemanticElement?
  private let onGesture: (Int, NativeSwiftGestureKind, NativeSwiftPointerSample?) -> Void
  /// The document view's `DrawToBitmap` targets, handed to this view's canvas and to every child,
  /// including children built when an update replaces one.
  private let offscreenTargets: NativeOffscreenTargets
  private weak var tapRecognizer: NSClickGestureRecognizer?
  private weak var doubleClickRecognizer: NSClickGestureRecognizer?
  private var lastTapTimestamp: TimeInterval = -.infinity
  /// Rows, columns, flows and collapsible containers ask a child for its size several times per
  /// layout pass with the same constraint, and each ask recurses through the child's subtree. A
  /// measurement depends only on this subtree's nodes and the constraint, so it is kept until the
  /// next update replaces a node. Keyed on identical constraints only; a weighted child's final
  /// allocation is a separate entry.
  let layoutCache = NativeLayoutSizeCache()

  override var isFlipped: Bool { true }

  init(
    node: NativeMacNode,
    images: [Int: NSImage],
    fontNames: [Int: String],
    conformanceFontName: String?,
    offscreenTargets: NativeOffscreenTargets,
    onGesture: @escaping (Int, NativeSwiftGestureKind, NativeSwiftPointerSample?) -> Void
  ) {
    self.node = node
    layoutNode = NativeLayoutNode(snapshot: node)
    self.onGesture = onGesture
    self.offscreenTargets = offscreenTargets
    componentChildren = node.children.map {
      NativeMacComponentView(
        node: $0, images: images, fontNames: fontNames, conformanceFontName: conformanceFontName,
        offscreenTargets: offscreenTargets, onGesture: onGesture)
    }
    let drawCommands = Self.canvasCommands(for: node)
    canvas =
      drawCommands.isEmpty
      ? nil
      : NativeMacCanvasView(
        commands: drawCommands, images: images, conformanceFontName: conformanceFontName,
        offscreenTargets: offscreenTargets)
    labels = Self.labelTexts(for: node).map {
      Self.makeLabel($0, fontNames: fontNames, conformanceFontName: conformanceFontName)
    }
    imageViews = Self.imageItems(for: node, images: images).map {
      Self.makeImageView($0.image, draw: $0.draw, alpha: $0.alpha)
    }
    super.init(frame: .zero)
    semanticElement = node.semanticBehavior.map {
      NativeMacSemanticElement(owner: self, kind: $0.descriptor.elementKind)
    }
    configureSemanticElement()
    wantsLayer = true
    applyLayerStyle()
    applyVisibility()
    setAccessibilityIdentifier("rc-native-component-\(node.componentId)")
    if let canvas { addSubview(canvas) }
    labels.forEach(addSubview)
    imageViews.forEach(addSubview)
    componentChildren.forEach(addSubview)
    installGestureRecognizers()
  }

  /// Whether `next` can be applied to this view in place rather than by building a new one.
  ///
  /// Only this view's own identity is compared: the same component ID, the same kind and class,
  /// the same gestures (so the installed recognizers stay right), and the same helper subviews —
  /// canvas, promoted text label, promoted images, semantic element kind. Children are reconciled
  /// one by one in `update(node:…)`, so a child whose identity changed is rebuilt on its own
  /// without discarding this view or its siblings.
  func canUpdate(with next: NativeMacNode, images: [Int: NSImage]) -> Bool {
    guard
      node.componentID == next.componentID,
      node.kind == next.kind,
      node.componentKind == next.componentKind,
      node.supportedGestures == next.supportedGestures,
      semanticElement?.kind == next.semanticBehavior?.descriptor.elementKind,
      (canvas != nil) == !Self.canvasCommands(for: next).isEmpty,
      labels.count == Self.labelTexts(for: next).count
    else { return false }
    let currentImages = Self.imageItems(for: node, images: images)
    let nextImages = Self.imageItems(for: next, images: images)
    guard imageViews.count == nextImages.count, currentImages.count == nextImages.count else {
      return false
    }
    // An image view only ever gains an accessibility label; one that should lose it is rebuilt.
    return zip(currentImages, nextImages).allSatisfy {
      ($0.draw.contentDescription == nil) == ($1.draw.contentDescription == nil)
    }
  }

  /// Applies a newer snapshot of the same component to this view and reconciles its children.
  ///
  /// Everything the initializer derives from the node is re-derived here; frames are left to the
  /// next layout pass, which every updated view is marked for. The caller has checked
  /// `canUpdate(with:images:)`.
  func update(
    node next: NativeMacNode,
    images: [Int: NSImage],
    fontNames: [Int: String],
    conformanceFontName: String?
  ) {
    node = next
    layoutNode = NativeLayoutNode(snapshot: next)
    // A FitBox parent sets this again during its layout, exactly as it does on a fresh view.
    ignoresOwnVisibility = false
    layoutCache.removeAll()
    autosizedTexts.removeAll(keepingCapacity: true)
    canvas?.update(commands: Self.canvasCommands(for: next))
    for (label, text) in zip(labels, Self.labelTexts(for: next)) {
      Self.configure(
        label, text: text, fontNames: fontNames, conformanceFontName: conformanceFontName)
    }
    for (view, item) in zip(imageViews, Self.imageItems(for: next, images: images)) {
      Self.configure(view, image: item.image, draw: item.draw, alpha: item.alpha)
    }
    reconcileChildren(
      next.children, images: images, fontNames: fontNames,
      conformanceFontName: conformanceFontName)
    applyLayerStyle()
    applyVisibility()
    // After the children: a merging or unlabeled node resolves its label, role and action from
    // its descendants' views, which now hold the new snapshot.
    configureSemanticElement()
    needsLayout = true
  }

  /// Keeps every child whose component survives (matched by component ID, in document order) and
  /// can take its new node, and builds a new view only for the rest.
  private func reconcileChildren(
    _ nextNodes: [NativeMacNode],
    images: [Int: NSImage],
    fontNames: [Int: String],
    conformanceFontName: String?
  ) {
    let previous = componentChildren
    var candidates = Dictionary(grouping: previous) { $0.node.componentID }
    var next: [NativeMacComponentView] = []
    next.reserveCapacity(nextNodes.count)
    for childNode in nextNodes {
      if var matches = candidates[childNode.componentID], !matches.isEmpty {
        let candidate = matches.removeFirst()
        candidates[childNode.componentID] = matches
        if candidate.canUpdate(with: childNode, images: images) {
          candidate.update(
            node: childNode, images: images, fontNames: fontNames,
            conformanceFontName: conformanceFontName)
          next.append(candidate)
          continue
        }
      }
      next.append(
        NativeMacComponentView(
          node: childNode, images: images, fontNames: fontNames,
          conformanceFontName: conformanceFontName, offscreenTargets: offscreenTargets,
          onGesture: onGesture))
    }
    componentChildren = next
    if next.count == previous.count, zip(next, previous).allSatisfy({ $0 === $1 }) { return }
    let previousIDs = Set(previous.map { ObjectIdentifier($0) })
    let keptIDs = Set(next.map { ObjectIdentifier($0) })
    // The common case of a changed child: the same slot, a new view. Swap it where AppKit already
    // has the old one so no surviving sibling is detached, even briefly.
    let positional =
      next.count == previous.count
      && zip(next, previous).allSatisfy { incoming, outgoing in
        incoming === outgoing
          || (!previousIDs.contains(ObjectIdentifier(incoming))
            && !keptIDs.contains(ObjectIdentifier(outgoing)))
      }
    if positional {
      for (incoming, outgoing) in zip(next, previous) where incoming !== outgoing {
        replaceSubview(outgoing, with: incoming)
      }
      return
    }
    for child in previous where !keptIDs.contains(ObjectIdentifier(child)) {
      child.removeFromSuperview()
    }
    // Children paint above the canvas, labels and images; re-adding an existing subview only
    // moves it, so this restores document order.
    for child in next { addSubview(child) }
  }

  /// The document view this component is installed in, for the document-wide clock.
  private var documentView: NativeMacDocumentView? {
    var ancestor = superview
    while let view = ancestor {
      if let document = view as? NativeMacDocumentView { return document }
      ancestor = view.superview
    }
    return nil
  }

  /// The text's own one-line advance, rounded up to a whole point as the reference's measured
  /// placeable is. Read from the attributed string rather than the field, whose cell adds padding
  /// the overflow distance must not count.
  private static func unboundedWidth(of label: NSTextField) -> CGFloat {
    label.attributedStringValue.size().width.rounded(.up)
  }

  /// The horizontal padding an `NSTextField` label's cell draws its text inside, on each side. A
  /// label asked to fit `core_text_simple`'s 228-point line answered 232.
  private static let labelInset: CGFloat = 2

  /// The height of one line of the label's font, as TextKit sets it.
  private static func lineHeight(of label: NSTextField) -> CGFloat {
    guard let font = label.font else { return label.intrinsicContentSize.height }
    return NSLayoutManager().defaultLineHeight(for: font).rounded(.up)
  }

  /// The size a `CoreText` component's text takes within `maxWidth`, laid out by TextKit as the
  /// UIKit renderer measures it: no line-fragment padding, the policy's line count, and a
  /// truncating last line under an ellipsis. The field's own fitting size is not used — it adds
  /// its cell padding (`core_text_simple`'s 228-point line answered 232) — and a truncating
  /// line-break mode there makes it one line, where TextKit truncates only the last it keeps.
  /// Line breaking is TextKit's own: where it wraps differently from Android, that is an accepted
  /// difference, not something this host imitates.
  private static func measuredText(
    _ label: NSTextField, text: NativeSwiftTextSnapshot, maxWidth: CGFloat
  ) -> CGSize {
    guard let font = label.font else { return .zero }
    let size = textLayoutSize(
      text, font: font, maxWidth: maxWidth,
      maximumLines: NativeTextPolicy.numberOfLines(
        overflow: text.overflow, maximum: text.maximumLines))
    return CGSize(width: size.width.rounded(.up), height: size.height.rounded(.up))
  }

  /// The block TextKit lays `text` out in at `font`, within `maxWidth` and at most `maximumLines`
  /// lines (0 for no cap).
  private static func textLayoutSize(
    _ text: NativeSwiftTextSnapshot, font: NSFont, maxWidth: CGFloat, maximumLines: Int
  ) -> CGSize {
    guard maxWidth > 0 else { return .zero }
    let storage = NSTextStorage(string: text.value, attributes: [.font: font])
    let manager = NSLayoutManager()
    let container = NSTextContainer(
      size: CGSize(width: maxWidth, height: .greatestFiniteMagnitude))
    container.lineFragmentPadding = 0
    container.maximumNumberOfLines = maximumLines
    container.lineBreakMode = lineBreakMode(overflow: text.overflow, maximumLines: 1)
    if maximumLines != 1, NativeTextPolicy.lineBreak(overflow: text.overflow) == .clip {
      container.lineBreakMode = .byWordWrapping
    }
    manager.addTextContainer(container)
    storage.addLayoutManager(manager)
    manager.ensureLayout(for: container)
    let used = manager.usedRect(for: container)
    return CGSize(width: min(used.width, maxWidth), height: used.height)
  }

  /// `font` at another point size, the rest of its description kept.
  private static func resized(_ font: NSFont, to size: CGFloat) -> NSFont {
    NSFont(descriptor: font.fontDescriptor, size: size) ?? font
  }

  /// `CoreText` autosize within `available`: the font size `NativeSwiftTextAutosize.fontSize`
  /// chooses, testing each candidate with TextKit's own layout, and the block it takes.
  ///
  /// The candidate block is the declared `maxLines` of text, whatever the overflow, as the
  /// reference measures it; it fits when it is strictly shorter than the space. The size is not
  /// rounded: a half-point font lays out a half-point block, as `core_text_autosize_height_driven`
  /// records (23.5 in a 24-point box).
  private static func autosizedText(
    _ label: NSTextField, text: NativeSwiftTextSnapshot, autosize: NativeSwiftTextAutosize,
    available: CGSize
  ) -> MacAutosizedText? {
    guard let font = label.font else { return nil }
    let lineCap = text.maximumLines == Int.max ? 0 : text.maximumLines
    func block(_ size: Float) -> CGSize {
      textLayoutSize(
        text, font: resized(font, to: CGFloat(size)), maxWidth: available.width,
        maximumLines: lineCap)
    }
    let fontSize = autosize.fontSize { block($0).height < available.height }
    return MacAutosizedText(fontSize: CGFloat(fontSize), size: block(fontSize))
  }

  private func applyLayerStyle() {
    // A scrolled container's children are laid out against their content, which is larger than the
    // viewport by design, so the viewport has to clip them or the overflow paints outside it. A
    // marquee's text is wider than its box for the same reason.
    layer?.masksToBounds =
      node.cornerRadius > 0 || node.scrollDirection != nil || node.marquee != nil
    layer?.cornerRadius = CGFloat(node.cornerRadius)
    layer?.backgroundColor = node.hasBackground ? Self.color(node.backgroundColor).cgColor : nil
    if let border = node.borderARGB, node.borderWidth > 0 {
      layer?.borderColor = Self.color(Int32(bitPattern: border)).cgColor
      layer?.borderWidth = CGFloat(node.borderWidth)
    } else {
      layer?.borderColor = nil
      layer?.borderWidth = 0
    }
  }

  /// The document's own visibility; containers (collapsible, flow, FitBox) override it during their
  /// layout. Written only when it changes, so an in-flight fade on this view is not reset.
  private func applyVisibility() {
    let hidden = node.visibility == NativeSwiftVisibility.gone
    if isHidden != hidden { isHidden = hidden }
    let alpha: CGFloat = node.visibility == NativeSwiftVisibility.invisible ? 0 : 1
    if alphaValue != alpha { alphaValue = alpha }
  }

  private static func canvasCommands(for node: NativeMacNode) -> [NativeMacDrawCommand] {
    let promotesImage = node.kind == .image
    return node.commands.filter { !(promotesImage && $0.kind == NativeSwiftDrawKind.bitmap) }
  }

  private static func labelTexts(for node: NativeMacNode) -> [NativeSwiftTextSnapshot] {
    guard node.kind == .text, let text = node.text else { return [] }
    return [text]
  }

  private static func imageItems(
    for node: NativeMacNode, images: [Int: NSImage]
  ) -> [(image: NSImage, draw: NativeSwiftImageDrawSnapshot, alpha: Float)] {
    guard node.kind == .image else { return [] }
    return node.commands.compactMap {
      command -> (image: NSImage, draw: NativeSwiftImageDrawSnapshot, alpha: Float)? in
      guard command.kind == NativeSwiftDrawKind.bitmap, let draw = command.image,
        let image = images[draw.imageID]
      else { return nil }
      return (image, draw, command.alpha)
    }
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

  /// Finds a document component without depending on the AppKit subview order, which also contains
  /// text, image and canvas helper views.
  func component(withID id: Int) -> NativeMacComponentView? {
    if node.componentId == id { return self }
    for child in componentChildren {
      if let match = child.component(withID: id) { return match }
    }
    return nil
  }

  func collectMeasuredSizes(
    of wanted: Set<Int>, into result: inout [Int: NativeSwiftMeasuredSize],
    inherited: CGSize? = nil
  ) {
    let measurable = bounds.size == .zero ? inherited : bounds.size
    if wanted.contains(node.componentID), let measurable {
      result[node.componentID] = NativeSwiftMeasuredSize(
        width: Float(measurable.width), height: Float(measurable.height))
    }
    for child in componentChildren {
      child.collectMeasuredSizes(of: wanted, into: &result, inherited: measurable)
    }
  }

  /// The elements VoiceOver reaches in this subtree, in document order, the same list the UIKit
  /// host publishes: promoted text and described images, and each semantic element. A node that
  /// merges, clears or derives its label from its descendants hides them behind its own element.
  var accessibilityOrder: [Any] {
    // The *effective* state, not the document's field: a FitBox displays an alternative whose own
    // visibility modifier is GONE, and a displayed button has to be reachable by VoiceOver rather
    // than filtered out by the modifier the box deliberately ignored.
    guard !isHidden, alphaValue > 0.01 else { return [] }
    let descendants = componentChildren.flatMap { $0.accessibilityOrder }
    let local: [Any] =
      labels.filter { $0.isAccessibilityElement() }.map { $0 as Any }
      + imageViews.filter { $0.isAccessibilityElement() }.map { $0 as Any }
    guard let semanticElement, let behavior = semanticBehavior else {
      return local + descendants
    }
    let owner: [Any] = semanticElement.isPublished ? [semanticElement] : []
    return behavior.descriptor.hidesDescendants ? owner : owner + local + descendants
  }

  /// The area this component's semantic element announces, in screen coordinates: its bounds, or
  /// for a flattened structural component, the union of what it lays out, clipped to its bounds.
  /// Resolved on every read so a scrolled ancestor or a moved window is always current.
  var semanticScreenFrame: NSRect {
    guard let window else { return .zero }
    let local = isStructural ? structuralSemanticBounds : bounds
    guard !local.isEmpty else { return .zero }
    return window.convertToScreen(convert(local, to: nil))
  }

  private var structuralSemanticBounds: NSRect {
    let rendered =
      flattenedLayoutItems
      .filter { !$0.isHidden && $0.alphaValue > 0.01 }
      .map { convert($0.bounds, from: $0) }
      .filter { !$0.isEmpty && !$0.isNull }
      .reduce(NSRect.null) { $0.union($1) }
    let clipped = rendered.intersection(bounds)
    return clipped.isNull ? .zero : clipped
  }

  /// Writes the node's resolved semantics onto its element. Activation dispatches the same tap the
  /// pointer path does: this component's own through `dispatchTap`, or, for a merging node whose
  /// action belongs to a descendant, that descendant's.
  private func configureSemanticElement() {
    guard let semanticElement, let behavior = semanticBehavior else { return }
    let descriptor = behavior.descriptor
    let label = resolvedSemanticLabel
    if behavior.activatesTap {
      let target = behavior.componentID
      let ownsTap = target == node.componentID
      semanticElement.action = { [weak self] in
        guard let self else { return }
        if ownsTap {
          self.dispatchTap(sample: nil)
        } else {
          self.onGesture(target, .tap, nil)
        }
      }
    } else {
      semanticElement.action = nil
    }
    semanticElement.isPublished = NativeAppKitAccessibility.publishes(
      descriptor, label: label, activates: behavior.activatesTap)
    semanticElement.setAccessibilityElement(semanticElement.isPublished)
    semanticElement.setAccessibilityRole(NativeAppKitAccessibility.role(for: descriptor))
    semanticElement.setAccessibilitySubrole(
      NativeAppKitAccessibility.subrole(for: descriptor.elementKind))
    semanticElement.setAccessibilityLabel(label)
    semanticElement.setAccessibilityValue(descriptor.stateDescription)
    semanticElement.setAccessibilityEnabled(descriptor.isEnabled)
    semanticElement.setAccessibilityIdentifier(
      NativeAppKitAccessibility.identifier(for: descriptor, componentID: node.componentID))
  }

  private func installGestureRecognizers() {
    if node.supportedGestures.contains(.doubleTap) {
      let recognizer = NSClickGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
      recognizer.numberOfClicksRequired = 2
      recognizer.delegate = self
      addGestureRecognizer(recognizer)
      self.doubleClickRecognizer = recognizer
    }
    if node.supportedGestures.contains(.tap) {
      let recognizer = NSClickGestureRecognizer(target: self, action: #selector(handleTap(_:)))
      recognizer.delegate = self
      addGestureRecognizer(recognizer)
      tapRecognizer = recognizer
    }
    if node.supportedGestures.contains(.longPress) {
      let recognizer = NSPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
      recognizer.minimumPressDuration = 0.5
      recognizer.delegate = self
      addGestureRecognizer(recognizer)
    }
    let pointerGestures: Set<NativeSwiftGestureKind> = [.touchDown, .touchUp, .touchCancel]
    if node.supportedGestures.contains(where: pointerGestures.contains) {
      let recognizer = NSPressGestureRecognizer(
        target: self, action: #selector(handlePointerLifecycle(_:)))
      recognizer.minimumPressDuration = 0
      recognizer.allowableMovement = .greatestFiniteMagnitude
      recognizer.delegate = self
      addGestureRecognizer(recognizer)
    }
  }

  @objc private func handleTap(_ recognizer: NSClickGestureRecognizer) {
    guard recognizer.state == .ended else { return }
    dispatchTap(sample: pointerSample(recognizer))
  }

  private func dispatchTap(sample: NativeSwiftPointerSample?) {
    let timestamp = NSApp.currentEvent?.timestamp ?? ProcessInfo.processInfo.systemUptime
    guard timestamp - lastTapTimestamp > 0.001 else { return }
    lastTapTimestamp = timestamp
    onGesture(Int(node.componentId), .tap, sample)
  }

  func gestureRecognizer(
    _ gestureRecognizer: NSGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: NSGestureRecognizer
  ) -> Bool {
    !isClickPair(gestureRecognizer, otherGestureRecognizer)
  }

  func gestureRecognizer(
    _ gestureRecognizer: NSGestureRecognizer,
    shouldRequireFailureOf otherGestureRecognizer: NSGestureRecognizer
  ) -> Bool {
    gestureRecognizer === tapRecognizer && otherGestureRecognizer === doubleClickRecognizer
  }

  private func isClickPair(
    _ first: NSGestureRecognizer, _ second: NSGestureRecognizer
  ) -> Bool {
    (first === tapRecognizer && second === doubleClickRecognizer)
      || (first === doubleClickRecognizer && second === tapRecognizer)
  }

  @objc private func handleLongPress(_ recognizer: NSPressGestureRecognizer) {
    guard recognizer.state == .began else { return }
    onGesture(Int(node.componentId), .longPress, pointerSample(recognizer))
  }

  @objc private func handleDoubleTap(_ recognizer: NSClickGestureRecognizer) {
    guard recognizer.state == .ended else { return }
    onGesture(Int(node.componentId), .doubleTap, pointerSample(recognizer))
  }

  @objc private func handlePointerLifecycle(_ recognizer: NSPressGestureRecognizer) {
    let sample = pointerSample(recognizer)
    switch recognizer.state {
    case .began:
      if node.supportedGestures.contains(.touchDown) {
        onGesture(Int(node.componentId), .touchDown, sample)
      }
    case .ended:
      if node.supportedGestures.contains(.touchUp) {
        onGesture(Int(node.componentId), .touchUp, sample)
      }
    case .cancelled, .failed:
      if node.supportedGestures.contains(.touchCancel) {
        onGesture(Int(node.componentId), .touchCancel, sample)
      }
    default: break
    }
  }

  private func pointerSample(
    _ recognizer: NSGestureRecognizer, velocity: NSPoint = .zero
  ) -> NativeSwiftPointerSample {
    let point = recognizer.location(in: self)
    return NativeSwiftPointerSample(
      x: Float(point.x), y: Float(point.y), velocityX: Float(velocity.x),
      velocityY: Float(velocity.y))
  }

  override func hitTest(_ point: NSPoint) -> NSView? {
    // `hitTest` is handed a point in the **superview's** coordinates, and the superview is not
    // always this view's own origin — the document view is flipped while the window's content view
    // is not, so an unconverted point lands mirrored, and a child at a non-zero frame origin lands
    // offset. Convert once, here, and hand the children a point in *this* view's coordinates, which
    // is what their own hit test expects.
    let local = superview.map { convert(point, from: $0) } ?? point
    guard !isHidden, alphaValue > 0.01, bounds.contains(local) else { return nil }
    for child in componentChildren.reversed() {
      if let hit = child.hitTest(local) { return hit }
    }
    return node.supportedGestures.isEmpty ? nil : self
  }

  override func acceptsFirstMouse(for event: NSEvent?) -> Bool {
    !node.supportedGestures.isEmpty
  }

  override func mouseDown(with event: NSEvent) {
    guard !node.supportedGestures.isEmpty else {
      super.mouseDown(with: event)
      return
    }
    // Gesture recognizers have already received the event; do not bubble it into an enclosing
    // NSHostingView where a drag or tap recognizer can steal the document interaction.
  }

  override func mouseUp(with event: NSEvent) {
    guard !node.supportedGestures.isEmpty else {
      super.mouseUp(with: event)
      return
    }
    // See mouseDown(with:).
  }

  /// Finds a scroll modifier geometrically. `point` is in the superview's coordinates, matching
  /// AppKit's hit-test convention; children receive this view's local point because this view is
  /// their superview.
  func scrollTarget(at point: CGPoint) -> NativeMacScrollTarget? {
    let local = superview.map { convert(point, from: $0) } ?? point
    guard !isHidden, alphaValue > 0.01, bounds.contains(local) else { return nil }
    for child in componentChildren.reversed() {
      if let target = child.scrollTarget(at: local) { return target }
    }
    guard let positionID = node.scrollPositionID, let direction = node.scrollDirection else {
      return nil
    }
    // The modifier's maximum is an output slot the reference player fills from measurement; a fresh
    // document therefore resolves it to zero. Derive the same travel from the laid-out content so a
    // first drag can move, while still respecting a non-zero maximum the document already holds.
    let contentEnd = flattenedLayoutItems.reduce(CGFloat.zero) { result, child in
      let frame = child.convert(child.bounds, to: self)
      return max(result, direction == .horizontal ? frame.maxX : frame.maxY)
    }
    // `contentEnd` and the trailing edge are in this component's coordinates. Subtracting only the
    // content size would count leading padding as overflow and let exactly-fitting content scroll.
    let viewportEnd = direction == .horizontal ? contentRect.maxX : contentRect.maxY
    let measuredMaximum = Float(max(contentEnd - viewportEnd, 0))
    return NativeMacScrollTarget(
      positionID: positionID, direction: direction, offset: node.scrollOffset,
      maximum: max(node.scrollMaximum, measuredMaximum))
  }

  override func layout() {
    super.layout()
    canvas?.frame = bounds
    imageViews.forEach { $0.frame = bounds }
    prepareStructuralChildren()
    if isStructural { return }
    if node.kind == .text {
      // `preferredSize` measures the label in the content box and adds the padding back, so the
      // label is placed in that same box rather than at the bounds' origin.
      let content = contentRect
      if node.text?.autosize != nil, let label = labels.first, let font = label.font {
        // The size chosen for the space whose block fills this content box; searching afresh with
        // the box itself only when none was measured.
        let chosen =
          autosizedTexts.values.min {
            abs($0.size.width - content.width) + abs($0.size.height - content.height)
              < abs($1.size.width - content.width) + abs($1.size.height - content.height)
          }
          ?? node.text.flatMap { text in
            text.autosize.flatMap {
              Self.autosizedText(label, text: text, autosize: $0, available: content.size)
            }
          }
        if let chosen, font.pointSize != chosen.fontSize {
          label.font = Self.resized(font, to: chosen.fontSize)
        }
      }
      for label in labels {
        // The field draws its text inset by its cell's padding, so it is widened by that much
        // either side and the glyphs start where the measured box does.
        let inset = Self.labelInset
        let height = min(
          node.text.map { Self.measuredText(label, text: $0, maxWidth: content.width).height }
            ?? label.intrinsicContentSize.height,
          content.height)
        if let marquee = node.marquee {
          // A marquee measures its content unbounded along x and slides it under the component's
          // clip, by the distance the content and its spacing overrun the box
          // (`applyAndroidXMarquee`). AppKit points are the document's dp, so density is 1.
          let natural = Self.unboundedWidth(of: label)
          let overflow = Float(natural + CGFloat(marquee.spacing) - content.width)
          setMarquee(
            offset: CGFloat(
              marquee.offset(
                overflowDistance: max(overflow, 0), density: 1,
                elapsedSeconds: documentView?.marqueeElapsedSeconds ?? 0)),
            moves: overflow > 0 && overflow.isFinite)
          label.frame = NSRect(
            x: content.minX + marqueeOffset - inset, y: content.minY,
            width: max(content.width, natural) + inset * 2, height: height)
        } else {
          setMarquee(offset: 0, moves: false)
          label.frame = NSRect(
            x: content.minX - inset, y: content.minY, width: content.width + inset * 2,
            height: height)
        }
      }
    }
    // Rows, columns, flows, boxes, FitBoxes and the root: the shared engine decides, in document
    // units and left to right, and this view only writes the result onto its children.
    apply(NativeLayoutEngine().arrange(self, in: bounds))
    applyContainerMarquee()
  }

  /// A marquee on anything but text: the children are measured unbounded along x, the way
  /// `applyAndroidXMarquee` measures its content, and every child slides by the overflow under the
  /// component's clip. A canvas or image has no content wider than its box, so it stays still.
  private func applyContainerMarquee() {
    guard node.kind != .text else { return }
    guard let marquee = node.marquee, !componentChildren.isEmpty else {
      setMarquee(offset: 0, moves: false)
      return
    }
    // The engine laid the children out unbounded along x (`NativeLayoutNode.contentAxis`), so
    // their frames already reach as far as the content does; this is how far that, plus the
    // spacing, runs past the box.
    let content = contentRect
    let reach = componentChildren.filter { !$0.isHidden }.map(\.frame.maxX).max() ?? content.minX
    let natural = reach - content.minX
    let overflow = Float(natural + CGFloat(marquee.spacing) - content.width)
    let offset = CGFloat(
      marquee.offset(
        overflowDistance: max(overflow, 0), density: 1,
        elapsedSeconds: documentView?.marqueeElapsedSeconds ?? 0))
    setMarquee(offset: offset, moves: overflow > 0 && overflow.isFinite)
    guard offset != 0 else { return }
    for child in componentChildren { child.frame.origin.x += offset }
  }

  /// Records where the marquee is and whether it can move, telling the document view when the
  /// latter changes: frames are requested only while some marquee overflows.
  private func setMarquee(offset: CGFloat, moves: Bool) {
    marqueeOffset = offset
    guard marqueeMoves != moves else { return }
    marqueeMoves = moves
    documentView?.marqueeDemandChanged()
  }

  /// Whether this component or any below it has a marquee whose content overflows.
  var hasMovingMarquee: Bool {
    marqueeMoves || componentChildren.contains { $0.hasMovingMarquee }
  }

  // MARK: NativeLayoutItem

  var layoutChildren: [NativeMacComponentView] { componentChildren }

  /// What this view draws itself, for the engine's text and image measurements. This renderer has
  /// no host custom views, so a custom component measures as nothing.
  func layoutContentSize(fitting available: CGSize) -> CGSize {
    switch node.kind {
    case .text:
      guard let label = labels.first, let text = node.text else { return .zero }
      // A marquee's text is one unbroken line however narrow the box: the overflow scrolls rather
      // than wrapping, so it is measured unbounded and the box takes what fits.
      if node.marquee != nil {
        return CGSize(
          width: min(Self.unboundedWidth(of: label), available.width),
          height: Self.lineHeight(of: label))
      }
      if let autosize = text.autosize {
        guard
          let chosen = Self.autosizedText(
            label, text: text, autosize: autosize, available: available)
        else { return .zero }
        autosizedTexts[MacAvailableSpace(available)] = chosen
        return chosen.size
      }
      return Self.measuredText(label, text: text, maxWidth: available.width)
    case .image:
      return imageViews.first?.image?.size ?? .zero
    default:
      return .zero
    }
  }

  /// Writes an arrangement the engine produced for this container onto its children.
  private func apply(_ arrangement: NativeLayoutArrangement<NativeMacComponentView>) {
    for change in arrangement.visibilityChanges {
      change.item.isHidden = change.isHidden
      if change.resetsAlpha, change.item.alphaValue != 1 { change.item.alphaValue = 1 }
      if change.isFitBoxAlternative { change.item.ignoresOwnVisibility = true }
    }
    if let containerIsHidden = arrangement.containerIsHidden { isHidden = containerIsHidden }
    for placement in arrangement.placements { placement.item.frame = placement.frame }
    if arrangement.visibilityChanges.contains(where: \.isFitBoxAlternative) {
      refreshSemanticElements()
    }
  }

  /// A FitBox decides at layout which alternative it displays, and a merging ancestor's label and
  /// action come from that alternative, so every semantic element from here up is re-resolved
  /// once the decision is made.
  private func refreshSemanticElements() {
    var view: NSView? = self
    while let current = view {
      (current as? NativeMacComponentView)?.configureSemanticElement()
      view = current.superview
    }
  }

  private var isStructural: Bool {
    NativeLayoutEngine.isStructural(layoutNode)
  }
  private var flattenedLayoutItems: [NativeMacComponentView] {
    NativeLayoutEngine.flattenedLayoutItems(of: self)
  }
  private func prepareStructuralChildren() {
    componentChildren.forEach { child in
      if child.isStructural {
        child.frame = bounds
        child.prepareStructuralChildren()
      }
    }
  }
  /// This component's padding, which the tree dump takes back out of its children's positions
  /// (§2.7: padding is modifier translation, not the layout manager's assignment).
  var insets: MacInsets {
    MacInsets(
      top: CGFloat(node.paddingTop), left: CGFloat(node.paddingLeft),
      bottom: CGFloat(node.paddingBottom), right: CGFloat(node.paddingRight))
  }
  private var contentRect: CGRect {
    CGRect(
      x: insets.left, y: insets.top, width: max(bounds.width - insets.left - insets.right, 0),
      height: max(bounds.height - insets.top - insets.bottom, 0))
  }

  private static func makeLabel(
    _ text: NativeSwiftTextSnapshot, fontNames: [Int: String], conformanceFontName: String?
  ) -> NSTextField {
    let label = NSTextField(labelWithString: text.value)
    label.drawsBackground = false
    label.isSelectable = false
    configure(label, text: text, fontNames: fontNames, conformanceFontName: conformanceFontName)
    return label
  }

  /// Everything a label takes from its text snapshot, for a new label and an updated one alike.
  /// Each property is written only when it differs, so an unchanged label is not redisplayed.
  private static func configure(
    _ label: NSTextField, text: NativeSwiftTextSnapshot, fontNames: [Int: String],
    conformanceFontName: String?
  ) {
    if label.stringValue != text.value { label.stringValue = text.value }
    let size = max(CGFloat(text.size), 1)
    let weight = NSFont.Weight(rawValue: min(max(CGFloat(text.weight - 400) / 500, -1), 1))
    var font = NSFont.systemFont(ofSize: size, weight: weight)
    if let name = conformanceFontName ?? fontNames[text.familyID],
      let downloaded = NSFont(name: name, size: size)
    {
      font = downloaded
    }
    if (text.style & 2) != 0,
      let italic = NSFontManager.shared.convert(font, toHaveTrait: .italicFontMask) as NSFont?
    {
      font = italic
    }
    if label.font != font { label.font = font }
    let textColor = color(Int32(bitPattern: text.colorARGB))
    if label.textColor != textColor { label.textColor = textColor }
    // The same line count the component is measured with (`measuredText`), so the field paints
    // every line it was given room for: a multi-line clip or visible layout is not capped.
    let maximumLines = NativeTextPolicy.numberOfLines(
      overflow: text.overflow, maximum: text.maximumLines)
    if label.maximumNumberOfLines != maximumLines {
      label.maximumNumberOfLines = maximumLines
    }
    let lineBreakMode = Self.lineBreakMode(
      overflow: text.overflow, maximumLines: text.maximumLines)
    if label.lineBreakMode != lineBreakMode { label.lineBreakMode = lineBreakMode }
    // A truncating mode makes the field one line; several lines with an ellipsis wrap instead and
    // truncate only the last one they show.
    let truncatesLast =
      text.maximumLines > 1 && NativeTextPolicy.lineBreak(overflow: text.overflow) != .clip
      && NativeTextPolicy.lineBreak(overflow: text.overflow) != .wordWrap
    if label.cell?.truncatesLastVisibleLine != truncatesLast {
      label.cell?.truncatesLastVisibleLine = truncatesLast
    }
    let alignment = Self.alignment(
      text.alignment, direction: label.userInterfaceLayoutDirection)
    if label.alignment != alignment { label.alignment = alignment }
  }

  /// The shared `NativeTextPolicy` alignment, so AppKit and UIKit resolve start/end, RTL and the
  /// explicit values identically.
  private static func alignment(
    _ value: NativeSwiftTextAlignment, direction: NSUserInterfaceLayoutDirection
  ) -> NSTextAlignment {
    switch NativeTextPolicy.alignment(
      value: value, justified: false,
      direction: direction == .rightToLeft ? .rightToLeft : .leftToRight
    ) {
    case .left: return .left
    case .right: return .right
    case .center: return .center
    case .justified: return .justified
    }
  }

  /// The shared `NativeTextPolicy` line break. An `NSTextField` only wraps under a wrapping mode,
  /// so a multi-line clipped label wraps and is clipped by its frame rather than being cut to one
  /// line by `.byClipping`.
  private static func lineBreakMode(overflow: Int, maximumLines: Int) -> NSLineBreakMode {
    switch NativeTextPolicy.lineBreak(overflow: overflow) {
    case .clip: return maximumLines > 1 ? .byWordWrapping : .byClipping
    case .wordWrap: return .byWordWrapping
    case .tail: return maximumLines > 1 ? .byWordWrapping : .byTruncatingTail
    case .head: return maximumLines > 1 ? .byWordWrapping : .byTruncatingHead
    case .middle: return maximumLines > 1 ? .byWordWrapping : .byTruncatingMiddle
    }
  }

  private static func makeImageView(
    _ image: NSImage, draw: NativeSwiftImageDrawSnapshot, alpha: Float
  ) -> NSImageView {
    let view = NSImageView(image: image)
    view.imageFrameStyle = .none
    view.imageAlignment = .alignCenter
    configure(view, image: image, draw: draw, alpha: alpha)
    return view
  }

  /// Everything an image view takes from its draw command, for a new view and an updated one alike.
  private static func configure(
    _ view: NSImageView, image: NSImage, draw: NativeSwiftImageDrawSnapshot, alpha: Float
  ) {
    if view.image !== image { view.image = image }
    let scaling: NSImageScaling =
      draw.scaleType == NativeSwiftImageScaleType.fillBounds
      ? .scaleAxesIndependently : .scaleProportionallyUpOrDown
    if view.imageScaling != scaling { view.imageScaling = scaling }
    let opacity = CGFloat(min(max(alpha, 0), 1))
    if view.alphaValue != opacity { view.alphaValue = opacity }
    view.setAccessibilityIdentifier("rc-native-image-\(draw.imageID)")
    if let label = draw.contentDescription {
      view.setAccessibilityElement(true)
      view.setAccessibilityLabel(label)
    }
  }

  fileprivate static func color(_ argb: Int32) -> NSColor {
    let value = UInt32(bitPattern: argb)
    return NSColor(
      red: CGFloat((value >> 16) & 0xff) / 255, green: CGFloat((value >> 8) & 0xff) / 255,
      blue: CGFloat(value & 0xff) / 255, alpha: CGFloat((value >> 24) & 0xff) / 255)
  }
}

/// A space a component was measured in, as a dictionary key.
private struct MacAvailableSpace: Hashable {
  let width: CGFloat
  let height: CGFloat

  init(_ size: CGSize) {
    width = size.width
    height = size.height
  }
}

/// The font size `CoreText` autosize chose, and the block the text takes at it.
private struct MacAutosizedText {
  let fontSize: CGFloat
  let size: CGSize
}

private final class NativeMacCanvasView: NSView {
  private(set) var commands: [NativeMacDrawCommand]
  let images: [Int: NSImage]
  /// The face every label uses under the conformance lane (Ahem), so canvas text measures the same.
  ///
  /// Canvas text has no family of its own to look up in the registry's `fontNames`: the core skips
  /// the paint's typeface command, so a draw-command snapshot carries no family ID. UIKit's canvas
  /// looks one up with the default style's family (-1), which never matches.
  let conformanceFontName: String?
  /// The bitmaps `DrawToBitmap` commands draw into: the document view's pool, shared by every
  /// canvas in its tree and kept between draws.
  private let offscreenTargets: NativeOffscreenTargets
  override var isFlipped: Bool { true }
  init(
    commands: [NativeMacDrawCommand], images: [Int: NSImage], conformanceFontName: String?,
    offscreenTargets: NativeOffscreenTargets
  ) {
    self.commands = commands
    self.images = images
    self.conformanceFontName = conformanceFontName
    self.offscreenTargets = offscreenTargets
    super.init(frame: .zero)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

  /// A newer frame's commands for the same component. Draw commands carry no equality, so the
  /// canvas simply redraws; its backing store is kept.
  func update(commands: [NativeMacDrawCommand]) {
    self.commands = commands
    needsDisplay = true
  }

  override func hitTest(_ point: NSPoint) -> NSView? { nil }

  override func draw(_ dirtyRect: NSRect) {
    guard let context = NSGraphicsContext.current?.cgContext else { return }
    // A `DrawToBitmap` sends the commands after it to an offscreen bitmap until the next one;
    // drawing returns to this canvas at the end of the node's commands, as the CMP player's
    // draw-target scope does. The target becomes AppKit's current context, flipped as this view
    // is, because image draws go through `NSGraphicsContext.current` rather than the context
    // passed. A target that cannot be allocated drops its commands rather than drawing them here.
    var redirect = NativeOffscreenRedirect.canvas
    for command in commands {
      guard command.kind == NativeSwiftDrawKind.drawToBitmap else {
        switch redirect {
        case .canvas: draw(command, context)
        case .offscreen(let target): draw(command, target)
        case .dropped: break
        }
        continue
      }
      if case .offscreen = redirect { NSGraphicsContext.restoreGraphicsState() }
      redirect =
        command.offscreenTarget.map { next in
          offscreenTargets.begin(next, seed: images[next.bitmapID].flatMap { Self.cgImage($0) })
        } ?? NativeOffscreenRedirect.canvas
      if case .offscreen(let target) = redirect {
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = NSGraphicsContext(cgContext: target, flipped: true)
      }
    }
    if case .offscreen = redirect { NSGraphicsContext.restoreGraphicsState() }
  }

  private func draw(_ command: NativeMacDrawCommand, _ context: CGContext) {
    let v = [
      command.first, command.second, command.third, command.fourth, command.fifth, command.sixth,
    ].map(CGFloat.init)
    let color = NativeMacComponentView.color(command.color).cgColor
    context.setAlpha(CGFloat(command.alpha))
    context.setFillColor(color)
    context.setStrokeColor(color)
    NativeGraphicsState.apply(
      to: context, strokeWidth: CGFloat(command.strokeWidth), strokeCap: command.strokeCap,
      strokeJoin: command.strokeJoin, blendMode: command.blendMode)
    // DESTINATION leaves the buffer unchanged, so a draw under it paints nothing (UIKit's canvas
    // skips it the same way). Kinds 0-8 are matrix, clip and save/restore state, which must still
    // apply; every kind from 10 up is a draw.
    if command.kind >= NativeSwiftDrawKind.rect,
      command.blendMode == NativeSwiftPaintBlendMode.destination
    {
      return
    }
    switch command.kind {
    case NativeSwiftDrawKind.matrixSave: context.saveGState()
    case NativeSwiftDrawKind.matrixRestore: context.restoreGState()
    case NativeSwiftDrawKind.matrixTranslate: context.translateBy(x: v[0], y: v[1])
    case NativeSwiftDrawKind.matrixScale:
      let pivot = CGPoint(x: v[2].isNaN ? 0 : v[2], y: v[3].isNaN ? 0 : v[3])
      context.translateBy(x: pivot.x, y: pivot.y)
      context.scaleBy(x: v[0], y: v[1])
      context.translateBy(x: -pivot.x, y: -pivot.y)
    case NativeSwiftDrawKind.matrixRotate:
      let pivot = CGPoint(x: v[1].isNaN ? 0 : v[1], y: v[2].isNaN ? 0 : v[2])
      context.translateBy(x: pivot.x, y: pivot.y)
      context.rotate(by: v[0] * .pi / 180)
      context.translateBy(x: -pivot.x, y: -pivot.y)
    case NativeSwiftDrawKind.matrixSkew:
      context.concatenate(CGAffineTransform(a: 1, b: v[1], c: v[0], d: 1, tx: 0, ty: 0))
    case NativeSwiftDrawKind.matrixFromPath:
      // The core measured the path; its six values are the affine matrix, in this order.
      context.concatenate(
        CGAffineTransform(a: v[0], b: v[1], c: v[2], d: v[3], tx: v[4], ty: v[5]))
    case NativeSwiftDrawKind.clipRect:
      context.clip(to: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]))
    case NativeSwiftDrawKind.clipPath:
      NativeGraphicsState.clip(
        context, to: path(command.path), winding: command.pathWinding,
        regionOp: command.values.first.map { Int($0) } ?? NativeSwiftClipRegionOp.intersect)
    case NativeSwiftDrawKind.rect:
      paint(
        CGPath(
          rect: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]), transform: nil),
        command, context)
    case NativeSwiftDrawKind.oval:
      paint(
        CGPath(
          ellipseIn: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]),
          transform: nil), command, context)
    case NativeSwiftDrawKind.circle:
      paint(
        CGPath(
          ellipseIn: CGRect(x: v[0] - v[2], y: v[1] - v[2], width: v[2] * 2, height: v[2] * 2),
          transform: nil), command, context)
    case NativeSwiftDrawKind.line:
      let path = CGMutablePath()
      path.move(to: CGPoint(x: v[0], y: v[1]))
      path.addLine(to: CGPoint(x: v[2], y: v[3]))
      // DrawLine is a stroke operation regardless of the current paint style: an open path has no
      // fill area. It still goes through `paint` so a gradient shader strokes it, as in UIKit.
      paint(path, command, context, forceStroke: true)
    case NativeSwiftDrawKind.roundRect:
      paint(
        NativeGraphicsState.roundedRectPath(
          CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]),
          cornerWidth: v[4], cornerHeight: v[5]), command,
        context)
    case NativeSwiftDrawKind.arc, NativeSwiftDrawKind.sector:
      let center = CGPoint(x: (v[0] + v[2]) / 2, y: (v[1] + v[3]) / 2)
      let path = CGMutablePath()
      if command.kind == NativeSwiftDrawKind.sector { path.move(to: center) }
      path.addArc(
        center: center, radius: min(v[2] - v[0], v[3] - v[1]) / 2, startAngle: v[4] * .pi / 180,
        endAngle: (v[4] + v[5]) * .pi / 180, clockwise: false)
      if command.kind == NativeSwiftDrawKind.sector { path.closeSubpath() }
      paint(path, command, context)
    case NativeSwiftDrawKind.text: drawText(command, context)
    case NativeSwiftDrawKind.path, NativeSwiftDrawKind.tweenPath:
      paint(path(command.path), command, context)
    case NativeSwiftDrawKind.bitmap: drawImage(command, context)
    default: break
    }
  }

  private func paint(
    _ path: CGPath, _ command: NativeMacDrawCommand, _ context: CGContext,
    forceStroke: Bool = false
  ) {
    let stroke = command.stroke || forceStroke
    if let imageID = command.textureImageID, let image = images[imageID], !stroke {
      context.saveGState()
      context.addPath(path)
      context.clip(using: NativeGraphicsState.fillRule(command.pathWinding))
      // `respectFlipped` because this view is flipped: without it the texture paints upside down.
      // Fraction 1 because the paint alpha is already the context's alpha (`draw` sets it).
      image.draw(
        in: bounds, from: .zero, operation: .sourceOver, fraction: 1, respectFlipped: true,
        hints: nil)
      context.restoreGState()
      return
    }
    context.addPath(path)
    let fillRule: CGPathFillRule =
      command.pathWinding == NativeSwiftPathWinding.evenOdd ? .evenOdd : .winding
    // A gradient shader paints inside the shape, or inside its stroke outline, as UIKit's canvas
    // does. Without this every gradient drew as the paint's flat colour.
    if let gradient = command.gradient {
      context.saveGState()
      if stroke { context.replacePathWithStrokedPath() }
      context.clip(using: stroke ? .winding : fillRule)
      NativeGradientRenderer.draw(Self.gradient(gradient), in: context)
      context.restoreGState()
      return
    }
    context.drawPath(using: stroke ? .stroke : (fillRule == .evenOdd ? .eoFill : .fill))
  }

  /// The renderer's gradient for a decoded shader, its colours in sRGB as the reference's are.
  private static func gradient(_ snapshot: NativeSwiftGradientSnapshot) -> NativeGradient {
    NativeGradient(
      kind: snapshot.kind,
      colors: snapshot.colorsARGB.map { argb in
        CGColor(
          srgbRed: CGFloat((argb >> 16) & 0xff) / 255, green: CGFloat((argb >> 8) & 0xff) / 255,
          blue: CGFloat(argb & 0xff) / 255, alpha: CGFloat((argb >> 24) & 0xff) / 255)
      },
      stops: snapshot.stops.map { CGFloat($0) },
      values: snapshot.values.map { CGFloat($0) },
      tileMode: snapshot.tileMode)
  }

  private func drawImage(_ command: NativeMacDrawCommand, _ context: CGContext) {
    // The source rectangle is in bitmap pixels from the top-left, as AndroidX and UIKit read it.
    // NSImage's `from:` rectangle is in points from the bottom-left, so crop the pixels instead and
    // draw the cropped image whole. A bitmap a `DrawToBitmap` drew into shows what it holds now.
    guard let draw = command.image,
      let bitmap = offscreenTargets.image(draw.imageID)
        ?? images[draw.imageID].flatMap({ Self.cgImage($0) })
    else { return }
    let source = NSRect(
      x: CGFloat(draw.sourceLeft), y: CGFloat(draw.sourceTop),
      width: CGFloat(draw.sourceRight - draw.sourceLeft),
      height: CGFloat(draw.sourceBottom - draw.sourceTop))
    let destination = NSRect(
      x: CGFloat(draw.destinationLeft), y: CGFloat(draw.destinationTop),
      width: CGFloat(draw.destinationRight - draw.destinationLeft),
      height: CGFloat(draw.destinationBottom - draw.destinationTop))
    guard let cropped = bitmap.cropping(to: source) else { return }
    // AndroidX clips every image scaling mode to the declared destination. Crop may deliberately
    // overflow it, while a malformed or fixed draw must never leak outside it.
    context.saveGState()
    context.clip(to: destination)
    let target = scaledImageDestination(
      source: source, destination: destination, scaleType: draw.scaleType,
      scaleFactor: CGFloat(draw.scaleFactor))
    // The paint's filter quality, bilinear when it named none, as the reference players filter;
    // Core Graphics' own interpolation at that level draws it. NSImage takes its interpolation
    // from the hint rather than the context, so both are set.
    let quality = NativeTexturePolicy.interpolationQuality(
      forFilterQuality: command.filterQuality)
    context.interpolationQuality = quality
    // Fraction 1: the paint alpha is already the context's alpha, as it is for UIKit's image draw.
    NSImage(cgImage: cropped, size: target.size).draw(
      in: target, from: .zero, operation: .sourceOver, fraction: 1, respectFlipped: true,
      hints: [.interpolation: NSNumber(value: Self.imageInterpolation(quality).rawValue)])
    context.restoreGState()
  }

  /// AppKit's name for a Core Graphics interpolation quality.
  private static func imageInterpolation(
    _ quality: CGInterpolationQuality
  ) -> NSImageInterpolation {
    switch quality {
    case CGInterpolationQuality.none: return NSImageInterpolation.none
    case .low: return .low
    case .medium: return .medium
    case .high: return .high
    default: return .default
    }
  }

  /// The pixel image behind a decoded bitmap, at its native pixel size rather than its point size.
  private static func cgImage(_ image: NSImage) -> CGImage? {
    if let bitmap = image.representations.lazy.compactMap({ $0 as? NSBitmapImageRep }).first,
      let cgImage = bitmap.cgImage
    {
      return cgImage
    }
    return image.cgImage(forProposedRect: nil, context: nil, hints: nil)
  }

  private func scaledImageDestination(
    source: NSRect, destination: NSRect, scaleType: Int, scaleFactor: CGFloat
  ) -> NSRect {
    guard source.width > 0, source.height > 0, destination.width >= 0, destination.height >= 0,
      scaleFactor.isFinite
    else { return .zero }
    let sourceWidth = Int(source.width)
    let sourceHeight = Int(source.height)
    let destinationWidth = Int(destination.width)
    let destinationHeight = Int(destination.height)
    guard sourceWidth > 0, sourceHeight > 0, destinationWidth >= 0, destinationHeight >= 0 else {
      return .zero
    }
    var width = destinationWidth
    var height = destinationHeight
    switch scaleType {
    case NativeSwiftImageScaleType.none: width = sourceWidth; height = sourceHeight
    case NativeSwiftImageScaleType.inside:
      if !(destinationHeight > sourceHeight && destinationWidth > sourceWidth) {
        if sourceWidth * destinationHeight > destinationWidth * sourceHeight {
          height = destinationWidth * sourceHeight / sourceWidth
        } else { width = destinationHeight * sourceWidth / sourceHeight }
      } else { width = sourceWidth; height = sourceHeight }
    case NativeSwiftImageScaleType.fitWidth: height = destinationWidth * sourceHeight / sourceWidth
    case NativeSwiftImageScaleType.fitHeight: width = destinationHeight * sourceWidth / sourceHeight
    case NativeSwiftImageScaleType.fit:
      if sourceWidth * destinationHeight > destinationWidth * sourceHeight {
        height = destinationWidth * sourceHeight / sourceWidth
      } else { width = destinationHeight * sourceWidth / sourceHeight }
    case NativeSwiftImageScaleType.crop:
      if sourceWidth * destinationHeight < destinationWidth * sourceHeight {
        height = destinationWidth * sourceHeight / sourceWidth
      } else { width = destinationHeight * sourceWidth / sourceHeight }
    case NativeSwiftImageScaleType.fillBounds: break
    case NativeSwiftImageScaleType.fixed:
      width = Int(CGFloat(sourceWidth) * scaleFactor)
      height = Int(CGFloat(sourceHeight) * scaleFactor)
    default: return .zero
    }
    return NSRect(
      x: destination.minX + CGFloat((destinationWidth - width) / 2),
      y: destination.minY + CGFloat((destinationHeight - height) / 2), width: CGFloat(width),
      height: CGFloat(height))
  }

  private func path(_ commands: [NativeMacPathCommand]) -> CGPath {
    let path = CGMutablePath()
    for item in commands {
      switch item.kind {
      case NativeSwiftPathCommand.move:
        path.move(to: CGPoint(x: CGFloat(item.first), y: CGFloat(item.second)))
      case NativeSwiftPathCommand.line:
        path.addLine(to: CGPoint(x: CGFloat(item.first), y: CGFloat(item.second)))
      case NativeSwiftPathCommand.quadratic, NativeSwiftPathCommand.conic:
        path.addQuadCurve(
          to: CGPoint(x: CGFloat(item.third), y: CGFloat(item.fourth)),
          control: CGPoint(x: CGFloat(item.first), y: CGFloat(item.second)))
      case NativeSwiftPathCommand.cubic:
        path.addCurve(
          to: CGPoint(x: CGFloat(item.fifth), y: CGFloat(item.sixth)),
          control1: CGPoint(x: CGFloat(item.first), y: CGFloat(item.second)),
          control2: CGPoint(x: CGFloat(item.third), y: CGFloat(item.fourth)))
      case NativeSwiftPathCommand.close: path.closeSubpath()
      default: break
      }
    }
    return path
  }

  private func drawText(_ command: NativeMacDrawCommand, _ context: CGContext) {
    guard let text = command.text else { return }
    let size = max(CGFloat(command.textSize), 1)
    let font =
      conformanceFontName.flatMap { NSFont(name: $0, size: size) }
      ?? NSFont.systemFont(ofSize: size)
    let paragraph = NSMutableParagraphStyle()
    if command.textFlags & NativeSwiftDrawTextAnchoredFlag.textRTL != 0 {
      paragraph.baseWritingDirection = .rightToLeft
    }
    let string = NSAttributedString(
      string: text,
      attributes: [
        .font: font,
        // The colour keeps its own alpha; the paint alpha is already the context's alpha (`draw`
        // sets it), so replacing the colour's alpha with it both dropped the colour's alpha and
        // applied the paint alpha twice.
        .foregroundColor: NativeMacComponentView.color(command.color),
        .paragraphStyle: paragraph,
      ])
    let line = CTLineCreateWithAttributedString(string)
    var ascent: CGFloat = 0
    var descent: CGFloat = 0
    var leading: CGFloat = 0
    let width = CGFloat(CTLineGetTypographicBounds(line, &ascent, &descent, &leading))
    let x = CGFloat(command.first) - width * ((CGFloat(command.third) + 1) / 2)
    let baseline: CGFloat
    // A NaN panY is the reference's "no vertical pan": the y given is the baseline, whatever the
    // flags say (`DrawTextAnchored.paint`).
    if command.fourth.isNaN {
      baseline = CGFloat(command.second)
    } else {
      baseline = CGFloat(command.second) - (ascent + descent + leading)
        * ((CGFloat(command.fourth) + 1) / 2)
    }
    context.saveGState()
    context.textMatrix = .identity
    context.translateBy(x: 0, y: baseline * 2)
    context.scaleBy(x: 1, y: -1)
    context.textPosition = CGPoint(x: x, y: baseline)
    CTLineDraw(line, context)
    context.restoreGState()
  }
}

/// The reviewed AppKit performance contract. Budgets travel with the report so a CI artifact can be
/// judged without rerunning it; the timings are order-of-magnitude ceilings rather than a tuning
/// gate, because a hosted runner's clock is noisy.
struct NativeAppKitEvidenceReport: Codable {
  struct Metrics: Codable {
    let medianDecodeMilliseconds: Double
    let medianBuildMilliseconds: Double
    let medianCaptureMilliseconds: Double
    let medianSteadyFrameMilliseconds: Double
    let viewCount: Int
    let labelCount: Int
    let controlCount: Int
    let residentByteGrowth: Int64
  }

  struct Budgets: Codable {
    var decodeMilliseconds: Double = 100
    var buildMilliseconds: Double = 250
    var captureMilliseconds: Double = 250
    // A drawn AppKit frame, not a resolved snapshot: the renderer reconciles its component tree,
    // lays it out and draws it every frame, so this ceiling is one 30Hz frame rather than the
    // core's 8ms.
    var steadyFrameMilliseconds: Double = 33
    var maximumViewCount: Int = 500
    var minimumLabelCount: Int = 1
    var residentByteGrowth: Int64 = 64 * 1024 * 1024
  }

  let schemaVersion: Int
  let sourceRevision: String
  let fixture: String
  let iterations: Int
  let frames: Int
  let metrics: Metrics
  let budgets: Budgets
  let overBudget: [String]
  let passed: Bool

  init(fixture: String, iterations: Int, frames: Int, metrics: Metrics) {
    let budgets = Budgets()
    var failures: [String] = []
    if metrics.medianDecodeMilliseconds > budgets.decodeMilliseconds { failures.append("decode") }
    if metrics.medianBuildMilliseconds > budgets.buildMilliseconds { failures.append("build") }
    if metrics.medianCaptureMilliseconds > budgets.captureMilliseconds {
      failures.append("capture")
    }
    if metrics.medianSteadyFrameMilliseconds > budgets.steadyFrameMilliseconds {
      failures.append("steadyFrame")
    }
    if metrics.viewCount > budgets.maximumViewCount { failures.append("viewCount") }
    if metrics.labelCount < budgets.minimumLabelCount { failures.append("labelCount") }
    if metrics.residentByteGrowth > budgets.residentByteGrowth { failures.append("resident") }
    schemaVersion = 1
    sourceRevision = ProcessInfo.processInfo.environment["RC_SOURCE_REVISION"] ?? "unknown"
    self.fixture = fixture
    self.iterations = iterations
    self.frames = frames
    self.metrics = metrics
    self.budgets = budgets
    overBudget = failures
    passed = failures.isEmpty
  }
}

private func nativeAppKitMilliseconds(since start: TimeInterval) -> Double {
  (ProcessInfo.processInfo.systemUptime - start) * 1000
}

private func nativeAppKitMedian(_ samples: [Double]) -> Double {
  guard !samples.isEmpty else { return 0 }
  let sorted = samples.sorted()
  return sorted[sorted.count / 2]
}

@MainActor
private func nativeAppKitCount(
  _ view: NSView, views: inout Int, labels: inout Int, controls: inout Int
) {
  views += 1
  if view is NSTextField { labels += 1 }
  if view is NSControl, !(view is NSTextField) { controls += 1 }
  for subview in view.subviews {
    nativeAppKitCount(subview, views: &views, labels: &labels, controls: &controls)
  }
}

private func nativeAppKitResidentBytes() -> UInt64 {
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


/// The system wall clock for a document that reads a calendar or time-of-day variable.
///
/// The AppKit host plays in real time, so it publishes the real instant; a test or a capture that
/// needs a frozen clock supplies its own `NativeSwiftWallClock` instead.
private func nativeSystemWallClock() -> NativeSwiftWallClock {
  let date = Date()
  return NativeSwiftWallClock(
    epochMillis: Int64((date.timeIntervalSince1970 * 1000).rounded()),
    offsetSeconds: TimeZone.current.secondsFromGMT(for: date), timeZone: .current)
}
#endif
