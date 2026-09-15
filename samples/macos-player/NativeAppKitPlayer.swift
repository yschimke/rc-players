import AppKit
import CoreText
import QuartzCore
#if canImport(RcNativePlayerCore)
  import RcNativePlayerCore
#endif

func nativeEventSummary(_ event: NativeSwiftEvent) -> String {
  switch event {
  case .namedAction(let name, let value): return "Named \(name): \(value.summary)"
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

@MainActor
final class NativeAppKitWindowController: NSObject, NSWindowDelegate {
  static let shared = NativeAppKitWindowController()
  private var windows: [NSWindow] = []

  static func renderPNG(data: Data, timeSeconds: TimeInterval = 0) throws -> Data {
    try NativeMacPolicy.validateDocument(data)
    let session = try NativeSwiftDocumentSession.open(data: data)
    let snapshot = try session.snapshot(timeSeconds: timeSeconds)
    let report = try NativeMacPolicy.evaluate(snapshot, compatibility: .compatible)
    let player = try NativeMacDocumentView(
      snapshot: snapshot, session: session, compatibility: .compatible, report: report,
      onEvent: { _ in }, onDiagnostics: { _ in }, onError: { _ in })
    let frame = NSRect(x: 0, y: 0, width: snapshot.width, height: snapshot.height)
    let window = NSWindow(
      contentRect: frame, styleMask: .borderless, backing: .buffered, defer: false)
    window.contentView = player
    player.frame = frame
    player.layoutSubtreeIfNeeded()
    guard let bitmap = player.bitmapImageRepForCachingDisplay(in: player.bounds) else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Could not allocate AppKit capture")
    }
    player.cacheDisplay(in: player.bounds, to: bitmap)
    guard let png = bitmap.representation(using: .png, properties: [:]) else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "Could not encode AppKit capture")
    }
    return png
  }

  func open(
    data: Data,
    title: String,
    compatibility: NativeMacCompatibility,
    onEvent: @escaping (String) -> Void,
    onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void,
    onError: @escaping (String) -> Void
  ) throws {
    try NativeMacPolicy.validateDocument(data)
    let session = try NativeSwiftDocumentSession.open(data: data)
    let snapshot = try session.snapshot(timeSeconds: 0)
    let report = try NativeMacPolicy.evaluate(snapshot, compatibility: compatibility)
    let player = try NativeMacDocumentView(
      snapshot: snapshot, session: session, compatibility: compatibility, report: report,
      onEvent: onEvent, onDiagnostics: onDiagnostics, onError: onError)
    let scroll = NSScrollView()
    scroll.drawsBackground = true
    scroll.backgroundColor = .windowBackgroundColor
    scroll.hasHorizontalScroller = true
    scroll.hasVerticalScroller = true
    scroll.documentView = player

    let size = NSSize(
      width: max(CGFloat(snapshot.width), 640),
      height: max(CGFloat(snapshot.height), 480))
    let window = NSWindow(
      contentRect: NSRect(origin: .zero, size: size),
      styleMask: [.titled, .closable, .miniaturizable, .resizable],
      backing: .buffered,
      defer: false)
    window.title = "\(title) — Native AppKit POC (\(compatibility.title))"
    window.contentView = scroll
    window.delegate = self
    window.center()
    window.makeKeyAndOrderFront(nil)
    windows.append(window)
  }

  func windowWillClose(_ notification: Notification) {
    guard let window = notification.object as? NSWindow else { return }
    windows.removeAll { $0 === window }
  }
}

private struct MacInsets: Equatable {
  let top: CGFloat
  let left: CGFloat
  let bottom: CGFloat
  let right: CGFloat
  static let zero = MacInsets(top: 0, left: 0, bottom: 0, right: 0)
}

private struct MacDimension {
  let type: Int
  let value: CGFloat
  let minimum: CGFloat
  let maximum: CGFloat?

  var weight: CGFloat? { type == 3 ? max(value, .leastNonzeroMagnitude) : nil }

  func resolve(intrinsic: CGFloat, available: CGFloat) -> CGFloat {
    let proposed: CGFloat
    switch type {
    case 0, 6: proposed = max(value, 0)
    case 1, 7, 8: proposed = available * (value.isNaN ? 1 : max(value, 0))
    case 3: proposed = available
    default: proposed = intrinsic
    }
    return min(max(proposed, minimum), max(maximum.map { min($0, available) } ?? available, 0))
  }
}

private enum MacLinearLayout {
  static func allocate(available: CGFloat, natural: [CGFloat], weights: [CGFloat?]) -> [CGFloat] {
    let fixed = zip(natural, weights).reduce(CGFloat.zero) { $0 + ($1.1 == nil ? $1.0 : 0) }
    let total = weights.compactMap { $0 }.reduce(0, +)
    guard total > 0 else { return natural }
    let remaining = max(available - fixed, 0)
    return zip(natural, weights).map { size, weight in weight.map { remaining * $0 / total } ?? size
    }
  }

  static func positions(total: CGFloat, sizes: [CGFloat], positioning: Int, spacing: CGFloat)
    -> [CGFloat]
  {
    guard !sizes.isEmpty else { return [] }
    let childSize = sizes.reduce(0, +)
    let contentSize = childSize + spacing * CGFloat(max(sizes.count - 1, 0))
    var distributed: CGFloat = 0
    var current: CGFloat
    switch positioning {
    case 2: current = (total - contentSize) / 2
    case 3, 5: current = total - contentSize
    case 6:
      distributed = sizes.count > 1 ? (total - childSize) / CGFloat(sizes.count - 1) : 0
      current = sizes.count > 1 ? 0 : (total - contentSize) / 2
    case 7:
      distributed = (total - childSize) / CGFloat(sizes.count + 1)
      current = distributed
    case 8:
      distributed = (total - childSize) / CGFloat(sizes.count)
      current = distributed / 2
    default: current = 0
    }
    return sizes.map { size in
      defer { current += size + spacing + ((6...8).contains(positioning) ? distributed : 0) }
      return current.rounded()
    }
  }
}

private final class NativeMacDocumentView: NSView {
  private let session: NativeSwiftDocumentSession
  private let compatibility: NativeMacCompatibility
  private let onEvent: (String) -> Void
  private let onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void
  private let onError: (String) -> Void
  private var snapshot: NativeSwiftDocumentSnapshot
  private var reportedDiagnostics: RemoteComposeNativePlayerDiagnostics?
  private var component: NativeMacComponentView!
  private var displayLinkDriver: AnyObject?
  private var fallbackFrameTimer: Timer?
  private var delayedWakeTimer: Timer?
  private var remainingWake: TimeInterval?
  private var wakeStartedAt: TimeInterval?
  private var elapsed: TimeInterval = 0
  private var lastActiveTime: TimeInterval?

  override var isFlipped: Bool { true }

  init(
    snapshot: NativeSwiftDocumentSnapshot,
    session: NativeSwiftDocumentSession,
    compatibility: NativeMacCompatibility,
    report: NativeMacPolicyReport,
    onEvent: @escaping (String) -> Void,
    onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void,
    onError: @escaping (String) -> Void
  ) throws {
    self.snapshot = snapshot
    self.session = session
    self.compatibility = compatibility
    self.onEvent = onEvent
    self.onDiagnostics = onDiagnostics
    self.onError = onError
    super.init(
      frame: NSRect(
        x: 0, y: 0, width: CGFloat(snapshot.width),
        height: CGFloat(snapshot.height)))
    lastActiveTime = Self.now
    try install(snapshot, report: report)
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
    if #available(macOS 14.0, *) {
      (displayLinkDriver as? NativeMacDisplayLinkDriver)?.invalidate()
    }
    fallbackFrameTimer?.invalidate()
    delayedWakeTimer?.invalidate()
    NotificationCenter.default.removeObserver(self)
    NSWorkspace.shared.notificationCenter.removeObserver(self)
  }

  private func install(
    _ next: NativeSwiftDocumentSnapshot,
    events: [NativeSwiftEvent] = [],
    report suppliedReport: NativeMacPolicyReport? = nil
  ) throws {
    let report: NativeMacPolicyReport
    if let suppliedReport {
      report = suppliedReport
    } else {
      report = try NativeMacPolicy.evaluate(next, compatibility: compatibility)
    }
    try NativeMacPolicy.validate(events: events, against: report)
    snapshot = next
    if reportedDiagnostics != report.diagnostics {
      reportedDiagnostics = report.diagnostics
      onDiagnostics(report.diagnostics)
    }
    component?.removeFromSuperview()
    component = NativeMacComponentView(node: snapshot.root) { [weak self] componentID, gesture, sample in
      self?.gesture(gesture, componentID: componentID, sample: sample)
    }
    addSubview(component)
    remainingWake = nil
    wakeStartedAt = nil
    needsLayout = true
    updateFrameDriver()
  }

  private func gesture(
    _ gesture: NativeSwiftGestureKind, componentID: Int, sample: NativeSwiftPointerSample?
  ) {
    do {
      guard
        let events = try session.gesture(
          gesture, componentID: componentID, sample: sample, timeSeconds: sampleTime())
      else { return }
      try install(try session.snapshot(timeSeconds: sampleTime()), events: events)
      for event in events { onEvent(nativeEventSummary(event)) }
    } catch {
      onError("Native input failed: \(error.localizedDescription)")
      NSSound.beep()
    }
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
  }

  private func updateFrameDriver() {
    pauseWakeCountdown()
    delayedWakeTimer?.invalidate()
    delayedWakeTimer = nil
    let mode = NativeMacFrameDriverMode.resolve(
      needsContinuousFrames: snapshot.needsContinuousFrames,
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
    let timer = Timer(timeInterval: delay, repeats: repeats) { [weak self] _ in
      DispatchQueue.main.async { self?.frameTimerDidFire() }
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
      try install(try session.snapshot(timeSeconds: sampleTime()))
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
      try install(try session.snapshot(timeSeconds: targetTime))
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

  private func sampleTime(at now: TimeInterval) -> TimeInterval {
    if let lastActiveTime {
      elapsed += max(now - lastActiveTime, 0)
      self.lastActiveTime = now
    }
    return elapsed
  }

  private func pauseTimeline() {
    _ = sampleTime()
    lastActiveTime = nil
  }

  private func resumeTimeline() {
    if lastActiveTime == nil { lastActiveTime = Self.now }
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

@available(macOS 14.0, *)
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
    owner?.displayLinkDidFire(targetTimestamp: link.targetTimestamp)
  }
}

private typealias NativeMacNode = NativeSwiftNodeSnapshot
private typealias NativeMacDrawCommand = NativeSwiftDrawCommandSnapshot
private typealias NativeMacPathCommand = NativeSwiftPathElementSnapshot

private extension NativeSwiftNodeSnapshot {
  var clickable: Bool { isClickable || accessibility?.isClickable == true }
  var semanticLabel: String? { accessibility?.contentDescription }
  var semanticText: String? { accessibility?.text ?? text?.value }
  var componentId: Int { componentID }
  var paddingTop: Float { padding.top }
  var paddingLeft: Float { padding.left }
  var paddingBottom: Float { padding.bottom }
  var paddingRight: Float { padding.right }
  var minimumWidth: Float { 0 }
  var maximumWidth: Float { -1 }
  var maximumHeight: Float { -1 }
  var offsetX: Float { 0 }
  var offsetY: Float { 0 }
  var visibility: Int { 1 }
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
  var text: String? { nil }
  var textSize: Float { 16 }
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

private final class NativeMacComponentView: NSView, NSGestureRecognizerDelegate {
  private let node: NativeMacNode
  private let componentChildren: [NativeMacComponentView]
  private let canvas: NativeMacCanvasView?
  private let labels: [NSTextField]
  private let semanticButton: NSButton?
  private let onGesture: (Int, NativeSwiftGestureKind, NativeSwiftPointerSample?) -> Void
  private weak var tapRecognizer: NSClickGestureRecognizer?
  private weak var doubleClickRecognizer: NSClickGestureRecognizer?

  override var isFlipped: Bool { true }

  init(
    node: NativeMacNode,
    onGesture: @escaping (Int, NativeSwiftGestureKind, NativeSwiftPointerSample?) -> Void
  ) {
    self.node = node
    self.onGesture = onGesture
    componentChildren = node.children.map {
      NativeMacComponentView(node: $0, onGesture: onGesture)
    }
    let promotesText = node.kind == .text
    let drawCommands = node.commands
    canvas = drawCommands.isEmpty ? nil : NativeMacCanvasView(commands: drawCommands)
    labels = promotesText ? node.text.map { [Self.makeLabel($0)] } ?? [] : []
    if node.clickable {
      let button = NSButton(title: "", target: nil, action: nil)
      button.isBordered = false
      button.isTransparent = true
      button.toolTip = node.semanticLabel ?? node.semanticText
      semanticButton = button
    } else {
      semanticButton = nil
    }
    super.init(frame: .zero)
    wantsLayer = true
    layer?.masksToBounds = node.cornerRadius > 0
    layer?.cornerRadius = CGFloat(node.cornerRadius)
    if node.hasBackground { layer?.backgroundColor = Self.color(node.backgroundColor).cgColor }
    isHidden = node.visibility == 0
    alphaValue = node.visibility == 2 ? 0 : 1
    setAccessibilityIdentifier("rc-native-component-\(node.componentId)")
    if let canvas { addSubview(canvas) }
    labels.forEach(addSubview)
    componentChildren.forEach(addSubview)
    if let semanticButton {
      semanticButton.target = self
      semanticButton.action = #selector(activate(_:))
      addSubview(semanticButton)
    }
    installGestureRecognizers()
  }

  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

  @objc private func activate(_ sender: Any?) {
    onGesture(Int(node.componentId), .tap, nil)
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
    onGesture(Int(node.componentId), .tap, pointerSample(recognizer))
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
    guard !isHidden, alphaValue > 0.01, bounds.contains(point) else { return nil }
    for child in componentChildren.reversed() {
      if let hit = child.hitTest(convert(point, to: child)) { return hit }
    }
    return node.supportedGestures.isEmpty ? nil : self
  }

  override func layout() {
    super.layout()
    canvas?.frame = bounds
    semanticButton?.frame = bounds
    prepareStructuralChildren()
    if isStructural { return }
    if node.kind == .text {
      for label in labels {
        let height = min(label.intrinsicContentSize.height, bounds.height)
        label.frame = NSRect(x: 0, y: 0, width: bounds.width, height: height)
      }
    }
    switch node.kind {
    case .row: layoutRow()
    case .column: layoutColumn()
    case .box: layoutOverlay(aligned: true)
    default: layoutOverlay(aligned: false)
    }
  }

  func preferredSize(in available: CGSize) -> CGSize {
    let padding = insets
    let content = CGSize(
      width: max(available.width - padding.left - padding.right, 0),
      height: max(available.height - padding.top - padding.bottom, 0))
    let sizes = visibleChildren.map { $0.preferredSize(in: content) }
    let intrinsic: CGSize
    switch node.kind {
    case .text:
      let labelSize =
        labels.first?.sizeThatFits(NSSize(width: content.width, height: .greatestFiniteMagnitude))
        ?? .zero
      intrinsic = labelSize
    case .row:
      intrinsic = CGSize(
        width: sizes.reduce(0) { $0 + $1.width } + spacing * CGFloat(max(sizes.count - 1, 0)),
        height: sizes.map(\.height).max() ?? 0)
    case .column:
      intrinsic = CGSize(
        width: sizes.map(\.width).max() ?? 0,
        height: sizes.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(sizes.count - 1, 0)))
    default:
      intrinsic = CGSize(
        width: sizes.map(\.width).max() ?? available.width,
        height: sizes.map(\.height).max() ?? available.height)
    }
    return applyDimensions(
      CGSize(
        width: intrinsic.width + padding.left + padding.right,
        height: intrinsic.height + padding.top + padding.bottom), available: available)
  }

  private var visibleChildren: [NativeMacComponentView] {
    flattenedLayoutItems
  }
  private var isStructural: Bool {
    (node.kind == .content || (node.kind == .canvas && node.commands.isEmpty))
      && node.text == nil && node.custom == nil && !node.clickable && !node.hasBackground
      && node.widthType == 2 && node.heightType == 2 && node.minimumHeight == 0
      && node.paddingTop == 0 && node.paddingLeft == 0 && node.paddingBottom == 0
      && node.paddingRight == 0
  }
  private var flattenedLayoutItems: [NativeMacComponentView] {
    componentChildren.filter { $0.node.visibility != 0 }.flatMap { child in
      child.isStructural ? child.flattenedLayoutItems : [child]
    }
  }
  private func prepareStructuralChildren() {
    componentChildren.forEach { child in
      if child.isStructural {
        child.frame = bounds
        child.prepareStructuralChildren()
      }
    }
  }
  private var spacing: CGFloat { CGFloat(node.spacing) }
  private var insets: MacInsets {
    MacInsets(
      top: CGFloat(node.paddingTop), left: CGFloat(node.paddingLeft),
      bottom: CGFloat(node.paddingBottom), right: CGFloat(node.paddingRight))
  }
  private var contentRect: CGRect {
    CGRect(
      x: insets.left, y: insets.top, width: max(bounds.width - insets.left - insets.right, 0),
      height: max(bounds.height - insets.top - insets.bottom, 0))
  }

  private func layoutOverlay(aligned: Bool) {
    let content = contentRect
    for child in visibleChildren {
      let size = child.preferredSize(in: content.size)
      let x: CGFloat = aligned ? alignedX(size.width, in: content) : content.minX
      let y: CGFloat
      if aligned {
        switch node.verticalPositioning {
        case 2: y = content.midY - size.height / 2
        case 5: y = content.maxY - size.height
        default: y = content.minY
        }
      } else {
        y = content.minY
      }
      child.frame = CGRect(
        x: x + CGFloat(child.node.offsetX), y: y + CGFloat(child.node.offsetY),
        width: aligned ? size.width : content.width, height: aligned ? size.height : content.height)
    }
  }

  private func layoutColumn() {
    let content = contentRect
    let items = visibleChildren
    let natural = items.map { $0.preferredSize(in: content.size) }
    let allocated = MacLinearLayout.allocate(
      available: content.height, natural: natural.map(\.height),
      weights: items.map {
        $0.node.heightType == 3 ? max(CGFloat($0.node.heightValue), .leastNonzeroMagnitude) : nil
      })
    let heights = zip(items, zip(natural, allocated)).map {
      $0.applyDimensions($1.0, available: CGSize(width: content.width, height: $1.1)).height
    }
    let positions = MacLinearLayout.positions(
      total: content.height, sizes: heights, positioning: Int(node.verticalPositioning),
      spacing: spacing)
    for index in items.indices {
      let width = natural[index].width
      items[index].frame = CGRect(
        x: alignedX(width, in: content) + CGFloat(items[index].node.offsetX),
        y: content.minY + positions[index] + CGFloat(items[index].node.offsetY), width: width,
        height: heights[index])
    }
  }

  private func layoutRow() {
    let content = contentRect
    let items = visibleChildren
    let natural = items.map { $0.preferredSize(in: content.size) }
    let widths = MacLinearLayout.allocate(
      available: content.width, natural: natural.map(\.width),
      weights: items.map {
        $0.node.widthType == 3 ? max(CGFloat($0.node.widthValue), .leastNonzeroMagnitude) : nil
      })
    let positions = MacLinearLayout.positions(
      total: content.width, sizes: widths, positioning: Int(node.horizontalPositioning),
      spacing: spacing)
    for index in items.indices {
      let size = items[index].preferredSize(
        in: CGSize(width: widths[index], height: content.height))
      let y: CGFloat
      switch node.verticalPositioning {
      case 2: y = content.midY - size.height / 2
      case 5: y = content.maxY - size.height
      default: y = content.minY
      }
      items[index].frame = CGRect(
        x: content.minX + positions[index] + CGFloat(items[index].node.offsetX),
        y: y + CGFloat(items[index].node.offsetY), width: widths[index], height: size.height)
    }
  }

  private func alignedX(_ width: CGFloat, in rect: CGRect) -> CGFloat {
    switch node.horizontalPositioning {
    case 2: rect.midX - width / 2
    case 3: rect.maxX - width
    default: rect.minX
    }
  }

  private func applyDimensions(_ intrinsic: CGSize, available: CGSize) -> CGSize {
    CGSize(
      width: MacDimension(
        type: Int(node.widthType), value: CGFloat(node.widthValue),
        minimum: CGFloat(node.minimumWidth),
        maximum: node.maximumWidth < 0 ? nil : CGFloat(node.maximumWidth)
      ).resolve(intrinsic: intrinsic.width, available: available.width),
      height: MacDimension(
        type: Int(node.heightType), value: CGFloat(node.heightValue),
        minimum: CGFloat(node.minimumHeight),
        maximum: node.maximumHeight < 0 ? nil : CGFloat(node.maximumHeight)
      ).resolve(intrinsic: intrinsic.height, available: available.height))
  }

  private static func makeLabel(_ text: NativeSwiftTextSnapshot) -> NSTextField {
    let label = NSTextField(labelWithString: text.value)
    let size = max(CGFloat(text.size), 1)
    let weight = NSFont.Weight(rawValue: min(max(CGFloat(text.weight - 400) / 500, -1), 1))
    var font = NSFont.systemFont(ofSize: size, weight: weight)
    if (text.style & 2) != 0,
      let italic = NSFontManager.shared.convert(font, toHaveTrait: .italicFontMask) as NSFont?
    {
      font = italic
    }
    label.font = font
    label.textColor = color(Int32(bitPattern: text.colorARGB))
    label.maximumNumberOfLines = text.maximumLines
    label.lineBreakMode = text.overflow == 2 ? .byTruncatingTail : .byWordWrapping
    label.alignment =
      text.alignment == 2
      ? .center : (text.alignment == 3 ? .right : .left)
    label.drawsBackground = false
    label.isSelectable = false
    return label
  }

  fileprivate static func color(_ argb: Int32) -> NSColor {
    let value = UInt32(bitPattern: argb)
    return NSColor(
      red: CGFloat((value >> 16) & 0xff) / 255, green: CGFloat((value >> 8) & 0xff) / 255,
      blue: CGFloat(value & 0xff) / 255, alpha: CGFloat((value >> 24) & 0xff) / 255)
  }
}

private final class NativeMacCanvasView: NSView {
  let commands: [NativeMacDrawCommand]
  override var isFlipped: Bool { true }

  init(commands: [NativeMacDrawCommand]) {
    self.commands = commands
    super.init(frame: .zero)
  }
  @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }

  override func hitTest(_ point: NSPoint) -> NSView? { nil }

  override func draw(_ dirtyRect: NSRect) {
    guard let context = NSGraphicsContext.current?.cgContext else { return }
    for command in commands { draw(command, context) }
  }

  private func draw(_ command: NativeMacDrawCommand, _ context: CGContext) {
    let v = [
      command.first, command.second, command.third, command.fourth, command.fifth, command.sixth,
    ].map(CGFloat.init)
    let color = NativeMacComponentView.color(command.color).cgColor
    context.setAlpha(CGFloat(command.alpha))
    context.setFillColor(color)
    context.setStrokeColor(color)
    context.setLineWidth(max(CGFloat(command.strokeWidth), 0.5))
    context.setLineCap(command.strokeCap == 1 ? .round : (command.strokeCap == 2 ? .square : .butt))
    context.setLineJoin(
      command.strokeJoin == 1 ? .round : (command.strokeJoin == 2 ? .bevel : .miter))
    switch command.kind {
    case 0: context.saveGState()
    case 1: context.restoreGState()
    case 2: context.translateBy(x: v[0], y: v[1])
    case 3:
      let pivot = CGPoint(x: v[2].isNaN ? 0 : v[2], y: v[3].isNaN ? 0 : v[3])
      context.translateBy(x: pivot.x, y: pivot.y)
      context.scaleBy(x: v[0], y: v[1])
      context.translateBy(x: -pivot.x, y: -pivot.y)
    case 4:
      let pivot = CGPoint(x: v[1].isNaN ? 0 : v[1], y: v[2].isNaN ? 0 : v[2])
      context.translateBy(x: pivot.x, y: pivot.y)
      context.rotate(by: v[0] * .pi / 180)
      context.translateBy(x: -pivot.x, y: -pivot.y)
    case 5: context.concatenate(CGAffineTransform(a: 1, b: v[1], c: v[0], d: 1, tx: 0, ty: 0))
    case 6: context.clip(to: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]))
    case 7:
      context.addPath(path(command.path))
      context.clip(using: command.pathWinding == 1 ? .evenOdd : .winding)
    case 10:
      paint(
        CGPath(
          rect: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]), transform: nil),
        command, context)
    case 11:
      paint(
        CGPath(
          ellipseIn: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]),
          transform: nil), command, context)
    case 12:
      paint(
        CGPath(
          ellipseIn: CGRect(x: v[0] - v[2], y: v[1] - v[2], width: v[2] * 2, height: v[2] * 2),
          transform: nil), command, context)
    case 13:
      let path = CGMutablePath()
      path.move(to: CGPoint(x: v[0], y: v[1]))
      path.addLine(to: CGPoint(x: v[2], y: v[3]))
      paint(path, command, context)
    case 14:
      paint(
        CGPath(
          roundedRect: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]),
          cornerWidth: max(v[4], v[5]), cornerHeight: max(v[4], v[5]), transform: nil), command,
        context)
    case 15, 16:
      let center = CGPoint(x: (v[0] + v[2]) / 2, y: (v[1] + v[3]) / 2)
      let path = CGMutablePath()
      if command.kind == 16 { path.move(to: center) }
      path.addArc(
        center: center, radius: min(v[2] - v[0], v[3] - v[1]) / 2, startAngle: v[4] * .pi / 180,
        endAngle: (v[4] + v[5]) * .pi / 180, clockwise: false)
      if command.kind == 16 { path.closeSubpath() }
      paint(path, command, context)
    case 17: drawText(command, context)
    case 18: paint(path(command.path), command, context)
    default: break
    }
  }

  private func paint(_ path: CGPath, _ command: NativeMacDrawCommand, _ context: CGContext) {
    context.addPath(path)
    context.drawPath(using: command.stroke ? .stroke : (command.pathWinding == 1 ? .eoFill : .fill))
  }

  private func path(_ commands: [NativeMacPathCommand]) -> CGPath {
    let path = CGMutablePath()
    for item in commands {
      switch item.kind {
      case 10: path.move(to: CGPoint(x: CGFloat(item.first), y: CGFloat(item.second)))
      case 11: path.addLine(to: CGPoint(x: CGFloat(item.first), y: CGFloat(item.second)))
      case 12, 13:
        path.addQuadCurve(
          to: CGPoint(x: CGFloat(item.third), y: CGFloat(item.fourth)),
          control: CGPoint(x: CGFloat(item.first), y: CGFloat(item.second)))
      case 14:
        path.addCurve(
          to: CGPoint(x: CGFloat(item.fifth), y: CGFloat(item.sixth)),
          control1: CGPoint(x: CGFloat(item.first), y: CGFloat(item.second)),
          control2: CGPoint(x: CGFloat(item.third), y: CGFloat(item.fourth)))
      case 15: path.closeSubpath()
      default: break
      }
    }
    return path
  }

  private func drawText(_ command: NativeMacDrawCommand, _ context: CGContext) {
    guard let text = command.text else { return }
    let font = NSFont.systemFont(ofSize: max(CGFloat(command.textSize), 1))
    let string = NSAttributedString(
      string: text,
      attributes: [
        .font: font,
        .foregroundColor: NativeMacComponentView.color(command.color).withAlphaComponent(
          CGFloat(command.alpha)),
      ])
    let line = CTLineCreateWithAttributedString(string)
    var ascent: CGFloat = 0
    var descent: CGFloat = 0
    var leading: CGFloat = 0
    let width = CGFloat(CTLineGetTypographicBounds(line, &ascent, &descent, &leading))
    let x = CGFloat(command.first) - width * ((CGFloat(command.third) + 1) / 2)
    let baseline =
      CGFloat(command.second) - (ascent + descent + leading) * ((CGFloat(command.fourth) + 1) / 2)
    context.saveGState()
    context.textMatrix = .identity
    context.translateBy(x: 0, y: baseline * 2)
    context.scaleBy(x: 1, y: -1)
    context.textPosition = CGPoint(x: x, y: baseline)
    CTLineDraw(line, context)
    context.restoreGState()
  }
}
