#if canImport(UIKit)
  import Foundation
  #if canImport(RcNativePlayerCore)
    import RcNativePlayerCore
  #endif
  #if canImport(RcPlayerAppleFonts)
    import RcPlayerAppleFonts
  #endif
  import UIKit

  @MainActor
  private final class NativeDisplayLinkTarget: NSObject {
    weak var owner: RemoteComposeNativePlayerView?
    weak var displayLink: CADisplayLink?

    init(owner: RemoteComposeNativePlayerView) {
      self.owner = owner
    }

    @objc func fire() {
      guard let owner else {
        displayLink?.invalidate()
        return
      }
      owner.displayLinkDidFire()
    }
  }

  extension RemoteComposeNativePlayerBackground {
    /// The background a player has when the host does not choose one: `.opaque` on iOS, and
    /// `.transparent` on visionOS, where an opaque system background paints over the window's
    /// glass material.
    public static var platformDefault: RemoteComposeNativePlayerBackground {
      #if os(visionOS)
        return .transparent
      #else
        return .opaque
      #endif
    }
  }

  /// A UIKit-native Remote Compose player proof of concept.
  ///
  /// The wire decoder, retained state, lifecycle, component hierarchy, layout, and drawing are all
  /// implemented in Swift with UIKit, CoreGraphics, and CoreText.
  @MainActor
  public final class RemoteComposeNativePlayerViewController: UIViewController {
    public private(set) var playerView: RemoteComposeNativePlayerView

    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .platformDefault,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility = .disabled,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      executionLimits: RemoteComposeNativeExecutionLimits = .default,
      customComponents: RemoteComposeNativeCustomComponentRegistry? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? =
        RemoteComposeNativeURLSessionResourceResolver.shared,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      clock: any RemoteComposeNativePlayerClock = RemoteComposeNativeSystemClock(),
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in },
      onError: @escaping (RemoteComposeNativePlayerError) -> Void = { _ in }
    ) {
      let customComponents = customComponents ?? RemoteComposeNativeCustomComponentRegistry()
      playerView = RemoteComposeNativePlayerView(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
        androidCompatibility: androidCompatibility,
        resourceLimits: resourceLimits, executionLimits: executionLimits,
        customComponents: customComponents,
        resourceResolver: resourceResolver,
        downloadableFontResolver: downloadableFontResolver,
        clock: clock,
        onEvent: onEvent,
        onDiagnostics: onDiagnostics,
        onError: onError)
      super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    public override func loadView() {
      view = playerView
    }

    public func load(_ data: Data) {
      playerView.load(data)
    }

    public func configureResources(
      limits: RemoteComposeNativeResourceLimits,
      resolver: (any RemoteComposeNativeResourceResolving)?
    ) {
      playerView.configureResources(limits: limits, resolver: resolver)
    }

    public func configureExecutionLimits(_ limits: RemoteComposeNativeExecutionLimits) {
      playerView.configureExecutionLimits(limits)
    }

    /// Select the density contract and retry the retained document when it changes.
    public func configureAndroidCompatibility(
      _ compatibility: RemoteComposeNativePlayerAndroidCompatibility
    ) {
      playerView.androidCompatibility = compatibility
    }

    /// Tell a deferred-density document what density this host plays at.
    public func configureHostDensity(_ density: Float, fontScale: Float = 1) {
      playerView.configureHostDensity(density, fontScale: fontScale)
    }

    public func configureDownloadableFonts(
      resolver: (any RemoteComposeDownloadableFontResolving)?
    ) {
      playerView.configureDownloadableFonts(resolver: resolver)
    }

    public func configureCustomComponents(
      _ registry: RemoteComposeNativeCustomComponentRegistry
    ) {
      playerView.configureCustomComponents(registry)
    }

    public func renderFrame(at timeSeconds: TimeInterval) {
      playerView.renderFrame(at: timeSeconds)
    }

    /// Sets a float named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)`` for
    /// what the answer means and why a value does not survive a reopen.
    @discardableResult
    public func setFloat(_ value: Float, for name: String) async -> Bool {
      await playerView.setFloat(value, for: name)
    }

    /// Sets a string named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)``.
    @discardableResult
    public func setString(_ value: String, for name: String) async -> Bool {
      await playerView.setString(value, for: name)
    }

    /// Sets an ARGB colour named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)``.
    @discardableResult
    public func setColor(_ argb: UInt32, for name: String) async -> Bool {
      await playerView.setColor(argb, for: name)
    }

    /// Sets an integer named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)``.
    @discardableResult
    public func setInteger(_ value: Int, for name: String) async -> Bool {
      await playerView.setInteger(value, for: name)
    }

    /// Whether the player is still opening its document — see
    /// ``RemoteComposeNativePlayerView/isOpeningDocument``.
    public var isOpeningDocument: Bool { playerView.isOpeningDocument }
  }

  /// How many host inputs may wait behind the one in flight. Beyond this an input is deferred
  /// (`set*` returns false, a gesture is dropped) rather than queued without bound.
  private let nativePlayerInputCapacity = 256

  /// What a presentation's pump is waiting on.
  private enum NativePumpActivity {
    case idle
    case frame
    case input
  }

  private struct NativePendingInput {
    let input: NativePlayerEngine.Input
    /// Receives the document's answer; nil for a gesture nobody awaits.
    let reply: ((RemoteComposeNativeInputAdmission) -> Void)?
  }

  private struct NativeFrameRequest {
    let time: TimeInterval
    let wallClock: NativeSwiftWallClock?
  }

  /// A named value a new session starts from, applied in order before its first frame.
  private struct NativeStartingValue {
    let name: String
    let value: RemoteComposeNativeNamedValue
    /// Whether it came from `namedValueSeed` rather than from the session being replaced.
    let isSeeded: Bool

    /// `values`, then `later`, without an earlier entry that a later one repeats exactly: applying
    /// it twice changes nothing, and if it breaks a limit it would be reported twice.
    static func merging(_ values: [Self], with later: [Self]) -> [Self] {
      values.filter { value in
        !later.contains { $0.name == value.name && $0.value == value.value }
      } + later
    }
  }

  /// A starting value a reopened session was opened without, and why.
  private struct NativeLeftOutValue {
    let value: NativeStartingValue
    let error: any Error
  }

  /// A session's first frame and what was built from it, before its resources are prepared.
  private struct NativeOpenedFirstFrame {
    let engine: NativePlayerEngine
    let frame: NativePlayerEngine.Frame
    let model: NativeDocument
    let diagnostics: RemoteComposeNativePlayerDiagnostics
  }

  /// One open document and the work queued against it.
  ///
  /// Frames and input run one at a time, in arrival order, on a single pump task, so a frame never
  /// lands on top of the state a later input produced and an input always resolves at the time of
  /// the frame on screen. The pump exists only while there is work, and holds no chain of tasks.
  @MainActor
  private final class NativePlayerPresentation {
    let engine: NativePlayerEngine
    let resources: NativeResourceStore
    var inputs = NativeBoundedQueue<NativePendingInput>(capacity: nativePlayerInputCapacity)
    var activity = NativePumpActivity.idle
    /// A frame `renderFrame(at:)` accepted; the pump starts it as soon as it is free.
    var pendingFrame: NativeFrameRequest?
    /// A `renderFrame(at:)` time that arrived while input was outstanding, retried once it drains.
    var deferredFrameTime: TimeInterval?
    /// The task draining this presentation's work, while there is any.
    var pump: Task<Void, Never>?

    init(engine: NativePlayerEngine, resources: NativeResourceStore) {
      self.engine = engine
      self.resources = resources
    }

    var hasOutstandingInput: Bool { activity == .input || !inputs.isEmpty }

    var isBusy: Bool { activity != .idle || !inputs.isEmpty || pendingFrame != nil }

    /// Drop everything pending. Queued input is answered `.deferred`, since the session that would
    /// have applied it is being replaced; whatever the pump is awaiting is discarded by the view's
    /// generation check when it returns.
    func abandon() {
      pump?.cancel()
      pump = nil
      activity = .idle
      pendingFrame = nil
      deferredFrameTime = nil
      for pending in inputs.removeAll() { pending.reply?(.deferred) }
    }
  }

  /// Root UIKit view. It can be embedded without SwiftUI or the supplied view controller.
  @MainActor
  public final class RemoteComposeNativePlayerView: UIView {
    /// What the view is doing with its document. Exactly one holds at a time, so combinations such
    /// as "loading while presenting" or "failed yet rendering frames" cannot be expressed.
    private enum State {
      /// Nothing is open: before the first load, or after a load was refused before it started.
      case idle
      /// Opening the retained bytes: resolving the first frame and the resources.
      case loading(Task<Void, Never>)
      /// A session is open and its latest frame is installed.
      case presenting(NativePlayerPresentation)
      /// The error label is showing. A session that was already open keeps accepting input, and an
      /// input that installs returns the view to `presenting`; frames wait for the next load.
      case failed(NativePlayerPresentation?)
      /// The application went to the background while a document was opening or input was in
      /// flight, or a load arrived while it was there; the bytes are reopened on activation.
      case awaitingForeground
    }

    public var playerBackground: RemoteComposeNativePlayerBackground {
      didSet { applyBackground() }
    }

    public var compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy {
      didSet {
        guard compatibilityPolicy != oldValue, let documentData else { return }
        render(documentData)
      }
    }

    /// The density a deferred-density capture resolves against.
    ///
    /// A `RemoteDensity.Host` document computes its sizes from `ID_DENSITY`/`ID_FONT_SIZE` instead
    /// of folding a capture device's density in, so the player owes it a value.
    ///
    /// Until the host calls `configureHostDensity(_:fontScale:)` the player chooses: 1.0 while
    /// `androidCompatibility == .disabled`, because that is what it resolves dp geometry at, and
    /// the screen's `traitCollection.displayScale` while reproducing Android geometry, following
    /// trait changes such as a move to another display. An explicit value is kept from then on.
    public private(set) var hostDensity: Float = 1
    public private(set) var hostFontScale: Float = 1
    /// Whether the host pinned the density with `configureHostDensity(_:fontScale:)`.
    private var hostDensityIsExplicit = false

    /// Whether dp-typed geometry follows the document's Android density contract.
    ///
    /// Native rendering resolves dp geometry at density 1.0 by default and reports a document that
    /// expected otherwise. Changing this reloads the retained bytes, so a host can inspect the
    /// diagnostic first and then opt in.
    public var androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility {
      didSet {
        guard androidCompatibility != oldValue else { return }
        if !hostDensityIsExplicit { hostDensity = automaticHostDensity }
        guard let documentData else { return }
        render(documentData)
      }
    }

    public var onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void
    public var onEvent: (RemoteComposeNativePlayerEvent) -> Void
    public var soundPlayer: any RemoteComposeNativeSoundPlaying = RemoteComposeNativeAVSoundPlayer()
    /// Replaces the platform haptic behavior when the embedding app owns feedback policy.
    public var hapticHandler: ((Int) -> Void)?
    public var onError: (RemoteComposeNativePlayerError) -> Void
    public private(set) var resourceLimits: RemoteComposeNativeResourceLimits
    public private(set) var executionLimits: RemoteComposeNativeExecutionLimits
    public private(set) var customComponents: RemoteComposeNativeCustomComponentRegistry
    private var customComponentsRevision: UInt
    public private(set) var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    public private(set) var downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?
    private var documentView: NativeDocumentView?
    private var documentData: Data?
    /// The resources behind the frame on screen, whose fonts stay registered until replaced.
    private var installedResources: NativeResourceStore?
    private var state = State.idle
    /// Bumped whenever in-flight work stops being wanted: a (re)load or a move to the background.
    /// Asynchronous work captures it when it starts and applies its result only while it matches.
    private var generation: UInt64 = 0
    /// Whether the window scene showing this view is in the foreground. A view that has not yet
    /// been in a scene counts as active, so it loads eagerly as it always has.
    private var isSceneActive = true
    /// The window scene whose lifecycle notifications drive `isSceneActive`. Weak: a scene outlives
    /// its windows, not the other way round, and a view keeps following its last scene while it is
    /// out of any window.
    private weak var observedScene: UIScene?
    /// A display-link or wake frame that arrived while the view was busy, taken once it frees up.
    private var scheduledFramePending = false
    /// Signals a caller holding a deferred write when to offer it again.
    @_spi(RemoteComposeController)
    public let inputAvailability = RemoteComposeNativeInputAvailability()
    /// Named values a reopened session starts from on top of the ones it carries over, read as the
    /// open starts: the host's writes the session being replaced has not taken — still on their
    /// way, deferred, refused, or written before it opened. The player's first open runs from
    /// `init`, before a host can set this, and is not seeded.
    ///
    /// A reopen — for the foreground, a new density, policy, limits, resolver or custom components
    /// — carries every named value a write changed in the session it replaces, the host's or the
    /// document's own (read after any input that session was applying), and then applies these,
    /// in order, before its first frame is resolved. So each named variable shows the newest value
    /// written to it, as on the Kotlin player, which keeps its state instead of reopening. State
    /// that is not a named variable — animations, a scroll position, unnamed variables — starts
    /// again from the bytes.
    ///
    /// A value the document declines is skipped silently, as a declined write is. When the first
    /// frame fails with these values — one breaks an execution limit or the compatibility policy
    /// — the session opens with every value its first frame can take and without the others: each
    /// one left out is reported through `onError`, once per session, as a refused write is, a
    /// seeded one is also passed to `onSeedValueLeftOut`, and the session stays open and takes
    /// later writes. (Written into the session, a bad value would stay there and fail every later
    /// frame.) Nil, or empty, adds nothing; a value written while the session opens goes through
    /// `submit`. `load(_:)` with other bytes clears it and the carried values: the values belong
    /// to the document they were written for.
    @_spi(RemoteComposeController)
    public var namedValueSeed: (() -> [(name: String, value: RemoteComposeNativeNamedValue)])?
    /// Called with the name of each `namedValueSeed` value a reopened session was opened without,
    /// after it was reported through `onError`. The session does not hold it, so a host that keeps
    /// it offers it again to the next session.
    @_spi(RemoteComposeController)
    public var onSeedValueLeftOut: ((String) -> Void)?
    /// What the next session to open starts from: the named values of the session it replaces —
    /// read once that session's in-flight input is done — or, while an open is superseded before
    /// it presents, what that open started from.
    private var carriedNamedValues: Task<[NativeStartingValue], Never>?
    private let clock: any RemoteComposeNativePlayerClock
    private var animationTimeline = NativeAnimationTimeline()
    private var frameSchedule = NativeFrameSchedule.idle
    private var displayLink: CADisplayLink?
    private lazy var displayLinkTarget = NativeDisplayLinkTarget(owner: self)
    private var delayedWakeTask: Task<Void, Never>?
    private var wakeCountdown = NativeWakeCountdown()
    private var resourceCache: NativeImageCache
    private let errorLabel = UILabel()

    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .platformDefault,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility = .disabled,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      executionLimits: RemoteComposeNativeExecutionLimits = .default,
      customComponents: RemoteComposeNativeCustomComponentRegistry? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? =
        RemoteComposeNativeURLSessionResourceResolver.shared,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      clock: any RemoteComposeNativePlayerClock = RemoteComposeNativeSystemClock(),
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in },
      onError: @escaping (RemoteComposeNativePlayerError) -> Void = { _ in }
    ) {
      playerBackground = background
      self.compatibilityPolicy = compatibilityPolicy
      self.androidCompatibility = androidCompatibility
      self.resourceLimits = resourceLimits
      self.executionLimits = executionLimits
      let customComponents = customComponents ?? RemoteComposeNativeCustomComponentRegistry()
      self.customComponents = customComponents
      customComponentsRevision = customComponents.revision
      self.resourceResolver = resourceResolver
      self.downloadableFontResolver = downloadableFontResolver
      self.clock = clock
      resourceCache = NativeImageCache(
        countLimit: resourceLimits.maximumResourceCount,
        totalCostLimit: resourceLimits.maximumDecodedImageBytes)
      self.onEvent = onEvent
      self.onDiagnostics = onDiagnostics
      self.onError = onError
      super.init(frame: .zero)
      hostDensity = automaticHostDensity
      animationTimeline.reset(
        at: clock.now(),
        active: isSceneActive && window != nil && !UIAccessibility.isReduceMotionEnabled)
      isAccessibilityElement = false
      clipsToBounds = true
      configureErrorLabel()
      applyBackground()
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(reduceMotionStatusDidChange),
        name: UIAccessibility.reduceMotionStatusDidChangeNotification,
        object: nil)
      load(data)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    deinit {
      // A pump holds the view strongly, so none can be running here; only an open (which holds it
      // weakly, and may be waiting on a resolver) and a wake timer can outlive it.
      if case .loading(let task) = state { task.cancel() }
      delayedWakeTask?.cancel()
      inputAvailability.close()
      NotificationCenter.default.removeObserver(self)
    }

    public func load(_ data: Data) {
      let isSameDocument = data == documentData
      if isSameDocument, !isShowingError { return }
      if !isSameDocument {
        // The seed and the carried values belong to the document they were written for; another
        // one may declare the same names for something else.
        namedValueSeed = nil
        soundPlayer.stopAll()
        animationTimeline.reset(
          at: clock.now(),
          active: isSceneActive && window != nil && !UIAccessibility.isReduceMotionEnabled)
      }
      documentData = data
      render(data, carryingNamedValues: isSameDocument)
    }

    /// Apply host-owned resource policy and retry the current document when it changes.
    public func configureResources(
      limits: RemoteComposeNativeResourceLimits,
      resolver: (any RemoteComposeNativeResourceResolving)?
    ) {
      let resolverChanged: Bool
      switch (resourceResolver, resolver) {
      case (nil, nil): resolverChanged = false
      case (let current?, let next?): resolverChanged = current !== next
      default: resolverChanged = true
      }
      let limitsChanged = limits != resourceLimits
      guard limitsChanged || resolverChanged else { return }
      resourceLimits = limits
      resourceResolver = resolver
      resourceCache = NativeImageCache(
        countLimit: limits.maximumResourceCount,
        totalCostLimit: limits.maximumDecodedImageBytes)
      if let documentData { render(documentData) }
    }

    /// Apply native per-frame work limits and retry the current document when they change.
    public func configureExecutionLimits(_ limits: RemoteComposeNativeExecutionLimits) {
      guard limits != executionLimits else { return }
      executionLimits = limits
      if let documentData { render(documentData) }
    }

    /// Tell a deferred-density document what density this host plays at.
    ///
    /// Reloads the retained bytes, because the first frame is resolved as the session opens and a
    /// document that reads `ID_DENSITY` would otherwise keep the geometry it built from the old
    /// value. Non-finite and non-positive values are ignored by the core rather than stored.
    public func configureHostDensity(_ density: Float, fontScale: Float = 1) {
      // Only a valid density pins the value; an ignored one leaves automatic density following
      // the display scale.
      if density.isFinite && density > 0 { hostDensityIsExplicit = true }
      let density = density.isFinite && density > 0 ? density : hostDensity
      let fontScale = fontScale.isFinite && fontScale > 0 ? fontScale : hostFontScale
      guard density != hostDensity || fontScale != hostFontScale else { return }
      hostDensity = density
      hostFontScale = fontScale
      if let documentData { render(documentData) }
    }

    /// Replace the opt-in network font source and retry the retained document when it changes.
    public func configureDownloadableFonts(
      resolver: (any RemoteComposeDownloadableFontResolving)?
    ) {
      let changed: Bool
      switch (downloadableFontResolver, resolver) {
      case (nil, nil): changed = false
      case (let current?, let next?): changed = current !== next
      default: changed = true
      }
      guard changed else { return }
      downloadableFontResolver = resolver
      if let documentData { render(documentData) }
    }

    /// Replace the host registry and retry the retained document against its declared names.
    public func configureCustomComponents(
      _ registry: RemoteComposeNativeCustomComponentRegistry
    ) {
      guard customComponents !== registry || customComponentsRevision != registry.revision else {
        return
      }
      customComponents = registry
      customComponentsRevision = registry.revision
      if let documentData { render(documentData) }
    }

    // MARK: - State

    private var isShowingError: Bool {
      if case .failed = state { return true }
      return false
    }

    /// Whether a document is on its way to an open session: it is being opened, or will be once the
    /// application returns to the foreground. While this holds, a named-variable write has no
    /// session to reach and is answered `.deferred` (`set*` returns `false`): it may be accepted
    /// once the session opens. Once this is false, the session is open (and accepts input) or the
    /// document failed, and a write with no session is `.refused`.
    public var isOpeningDocument: Bool {
      switch state {
      case .loading, .awaitingForeground: return true
      case .idle, .presenting, .failed: return false
      }
    }

    /// Whether the open session is applying an input right now. A test hook: the input-deferral
    /// tests use it to move the scene to the background while an input is in flight.
    var isApplyingInput: Bool { openPresentation?.activity == .input }

    /// Whether the open session has input in flight or queued — what makes a move to the background
    /// reopen the bytes on activation. A test hook, like ``isApplyingInput``.
    var hasOutstandingInput: Bool { openPresentation?.hasOutstandingInput ?? false }

    /// Called as each session opens, right after its first frame is installed and before anything
    /// else runs on the main actor. A test hook: it lets a test read the first frame a session
    /// shows rather than a later one.
    var onSessionOpened: (() -> Void)?

    /// Called with each input the open session applied. A test hook: it counts the writes a
    /// session took.
    var onInputApplied: ((NativePlayerEngine.Input) -> Void)?

    /// The open session that accepts input, whether or not an error is showing.
    private var openPresentation: NativePlayerPresentation? {
      switch state {
      case .presenting(let presentation): return presentation
      case .failed(let presentation): return presentation
      case .idle, .loading, .awaitingForeground: return nil
      }
    }

    /// Whether a scheduled frame has to wait: a document is opening, or its pump has work. Tests
    /// read it to know the player has settled.
    var isBusy: Bool {
      switch state {
      case .loading: return true
      case .presenting(let presentation): return presentation.isBusy
      case .failed(let presentation): return presentation?.isBusy ?? false
      case .idle, .awaitingForeground: return false
      }
    }

    /// Supersede all in-flight work: cancel an open, and drop the open session's queued frames and
    /// input. Anything already awaiting the engine is discarded when it returns.
    private func abandonWork() {
      generation &+= 1
      switch state {
      case .loading(let task): task.cancel()
      case .presenting(let presentation): presentation.abandon()
      case .failed(let presentation): presentation?.abandon()
      case .idle, .awaitingForeground: break
      }
    }

    // MARK: - Loading

    /// Keep the named values of the open session, which is about to be replaced, for the next one.
    /// Read once the input it is applying is done, so a value it was writing is not lost.
    private func carryNamedValues() {
      let presentation: NativePlayerPresentation
      switch state {
      case .presenting(let open): presentation = open
      case .failed(let open?): presentation = open
      case .idle, .loading, .failed(nil), .awaitingForeground: return
      }
      let engine = presentation.engine
      let pump = presentation.pump
      carriedNamedValues = Task {
        await pump?.value
        return await engine.changedNamedValues().map {
          NativeStartingValue(name: $0.name, value: $0.value, isSeeded: false)
        }
      }
    }

    private func render(_ data: Data, carryingNamedValues: Bool = true) {
      if carryingNamedValues { carryNamedValues() } else { carriedNamedValues = nil }
      abandonWork()
      guard isSceneActive else {
        state = .awaitingForeground
        return
      }
      scheduledFramePending = false
      stopFrameDriver()
      state = .idle
      do {
        try NativeFrameBudget.validate(executionLimits)
        guard data.count <= executionLimits.maximumDocumentBytes else {
          throw RemoteComposeNativeLimitError.documentTooLarge(
            actual: data.count, maximum: executionLimits.maximumDocumentBytes)
        }
      } catch {
        show(error: error, retaining: nil)
        return
      }
      let generation = self.generation
      let executionLimits = executionLimits
      let compatibilityPolicy = compatibilityPolicy
      let androidCompatibility = androidCompatibility
      let hostDensity = hostDensity
      let hostFontScale = hostFontScale
      let availableCustomComponents = customComponents.names
      let resourceLimits = resourceLimits
      let resourceResolver = resourceResolver
      let downloadableFontResolver = downloadableFontResolver
      let resourceCache = resourceCache
      let wallClock = clock.wallClock()
      let carried = carriedNamedValues
      let seeded = (namedValueSeed?() ?? []).map {
        NativeStartingValue(name: $0.name, value: $0.value, isSeeded: true)
      }
      // A reopen that supersedes this open before it presents starts from the same values.
      let startingValues = Task {
        NativeStartingValue.merging(await carried?.value ?? [], with: seeded)
      }
      carriedNamedValues = startingValues
      // Reduce Motion stops the frames a visibility animation needs, so it lands at once instead.
      let visibilityAnimations = !UIAccessibility.isReduceMotionEnabled
      let task = Task { [weak self] in
        do {
          let namedValues = await startingValues.value
          try Task.checkCancellation()
          func openFirstFrame(
            seededWith namedValues: [NativeStartingValue]
          ) async throws -> NativeOpenedFirstFrame {
            let (engine, frame) = try await NativePlayerEngine.open(
              data: data, maximumDocumentBytes: executionLimits.maximumDocumentBytes,
              hostDensity: hostDensity, hostFontScale: hostFontScale,
              wallClock: wallClock, visibilityAnimations: visibilityAnimations,
              namedValues: namedValues.map { (name: $0.name, value: $0.value) })
            try Task.checkCancellation()
            let model = try NativeDocument(
              frame: frame, timeSeconds: 0, limits: executionLimits,
              androidCompatibility: androidCompatibility)
            return NativeOpenedFirstFrame(
              engine: engine, frame: frame, model: model,
              diagnostics: model.diagnostics(
                availableCustomComponents: availableCustomComponents))
          }
          /// The opened session, and the values it was opened without, with their errors.
          func openSeededFirstFrame() async throws -> (
            NativeOpenedFirstFrame, [NativeLeftOutValue]
          ) {
            func openRendering(
              _ namedValues: [NativeStartingValue]
            ) async throws -> NativeOpenedFirstFrame {
              let opened = try await openFirstFrame(seededWith: namedValues)
              guard
                RemoteComposeNativeCompatibilityDecision.shouldRender(
                  policy: compatibilityPolicy, diagnostics: opened.diagnostics)
              else { throw RemoteComposeNativePlayerError.incompatible(opened.diagnostics) }
              return opened
            }
            guard !namedValues.isEmpty else {
              return (try await openFirstFrame(seededWith: []), [])
            }
            do {
              return (try await openRendering(namedValues), [])
            } catch is CancellationError {
              throw CancellationError()
            } catch {
              // A carried or seeded value may be what failed — one that breaks an execution limit
              // or the compatibility policy. A document that fails without them too fails as
              // before.
              try Task.checkCancellation()
              var opened = try await openFirstFrame(seededWith: [])
              guard
                RemoteComposeNativeCompatibilityDecision.shouldRender(
                  policy: compatibilityPolicy, diagnostics: opened.diagnostics)
              else { return (opened, []) }
              // Otherwise keep each value the first frame can take, one at a time. A value that
              // fails it is left out and reported, as a refused write is, rather than written
              // into the session: a session keeps every value it applied, so it would fail every
              // later frame and refuse every later write. Only this failure path pays the extra
              // opens.
              var kept: [NativeStartingValue] = []
              var refusals: [NativeLeftOutValue] = []
              for namedValue in namedValues {
                do {
                  opened = try await openRendering(kept + [namedValue])
                  kept.append(namedValue)
                } catch is CancellationError {
                  throw CancellationError()
                } catch {
                  try Task.checkCancellation()
                  refusals.append(NativeLeftOutValue(value: namedValue, error: error))
                }
              }
              return (opened, refusals)
            }
          }
          let (opened, refusals) = try await openSeededFirstFrame()
          let model = opened.model
          guard generation == self?.generation else { return }
          self?.onDiagnostics(opened.diagnostics)
          if !RemoteComposeNativeCompatibilityDecision.shouldRender(
            policy: compatibilityPolicy, diagnostics: opened.diagnostics)
          {
            throw RemoteComposeNativePlayerError.incompatible(opened.diagnostics)
          }
          try Task.checkCancellation()
          let resources = try await Self.prepareResources(
            for: model, limits: resourceLimits, resolver: resourceResolver,
            downloadableFontResolver: downloadableFontResolver,
            cache: resourceCache)
          try Task.checkCancellation()
          guard let self, generation == self.generation else { return }
          self.presentOpened(
            model, engine: opened.engine, resources: resources, effects: opened.frame.effects,
            reporting: refusals)
        } catch {
          guard !Task.isCancelled, let self, generation == self.generation else { return }
          self.show(error: error, retaining: nil)
        }
      }
      state = .loading(task)
    }

    /// Install the first frame of a freshly opened session, then report `refusals` — the carried
    /// and seeded values it was opened without — as refused writes are reported.
    private func presentOpened(
      _ model: NativeDocument, engine: NativePlayerEngine, resources: NativeResourceStore,
      effects: [RemoteComposeNativePlayerEvent], reporting refusals: [NativeLeftOutValue]
    ) {
      let presentation = NativePlayerPresentation(engine: engine, resources: resources)
      defer {
        for refusal in refusals {
          report(refusal.error)
          if refusal.value.isSeeded { onSeedValueLeftOut?(refusal.value.name) }
        }
        announceOpened()
      }
      do {
        try validateExecution(model, events: effects)
        try install(model, for: presentation)
        dispatch(effects, generation: generation)
      } catch {
        // The session is open even though its first frame could not be installed, so it keeps
        // accepting input, exactly as after any later failure.
        show(error: error, retaining: presentation)
        return
      }
      if scheduledFramePending { requestScheduledFrame() }
    }

    /// A session has opened: release the writes that waited for it.
    private func announceOpened() {
      onSessionOpened?()
      inputAvailability.signal()
    }

    private static func prepareResources(
      for model: NativeDocument,
      limits: RemoteComposeNativeResourceLimits,
      resolver: (any RemoteComposeNativeResourceResolving)?,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?,
      cache: NativeImageCache
    ) async throws -> NativeResourceStore {
      let resources = try NativeResourceStore(
        resources: model.images,
        fonts: model.fonts,
        limits: limits,
        cache: cache)
      if !resources.unresolvedImages.isEmpty {
        guard let resolver else {
          throw RemoteComposeNativeResourceError.unresolvedReference(
            id: resources.unresolvedImages[0].id)
        }
        for request in resources.unresolvedImages {
          let resolved = try await resolver.resolve(request)
          try Task.checkCancellation()
          try resources.insertResolved(data: resolved, for: request)
        }
      }
      if !model.downloadableFonts.isEmpty {
        if let downloadableFontResolver {
          for request in model.downloadableFonts {
            let font = try await downloadableFontResolver.resolve(
              RemoteComposeDownloadableFontRequest(family: request.family))
            guard font.family.caseInsensitiveCompare(request.family) == .orderedSame else {
              throw RemoteComposeDownloadableFontError.familyMismatch(
                expected: request.family, actual: font.family)
            }
            try Task.checkCancellation()
            try resources.insertDownloadedFont(data: font.data, id: request.id)
          }
        }
      }
      return resources
    }

    // MARK: - Frames and input

    /// Resolve another immutable frame from the retained runtime without decoding the document.
    ///
    /// Ignored while a frame is already on its way; deferred until queued input has been applied.
    public func renderFrame(at timeSeconds: TimeInterval) {
      guard isSceneActive, case .presenting(let presentation) = state else { return }
      guard presentation.activity != .frame, presentation.pendingFrame == nil else { return }
      guard !presentation.hasOutstandingInput else {
        presentation.deferredFrameTime = timeSeconds
        return
      }
      let frameTime = animationTimeline.advance(to: timeSeconds, at: clock.now())
      presentation.pendingFrame = NativeFrameRequest(
        time: frameTime, wallClock: clock.wallClock())
      startPump(for: presentation)
    }

    /// Sets a float named variable in the open session and answers whether it was applied.
    ///
    /// `true` means the document applied the value to the open session. `false` means it was not
    /// applied: the name is undeclared or of another type, the document refused it or
    /// failed, the document is still opening, the scene is in the background, the input queue is
    /// full, or the session that took it was superseded by a reopen. Nothing is queued for later;
    /// write it again when the player can take it.
    ///
    /// A value lives in the session. The player reopens its document when it returns from the
    /// background with input outstanding, and when its density, policy, limits, resolvers or
    /// custom components change; the new session starts from the named values the old one held
    /// (see `namedValueSeed`), less any that now breaks a limit, which is reported. Other bytes
    /// start again from their own values.
    @discardableResult
    public func setFloat(_ value: Float, for name: String) async -> Bool {
      await submit(.setFloat(value, name: name))
    }

    /// Sets a string named variable; answers as ``setFloat(_:for:)`` does.
    @discardableResult
    public func setString(_ value: String, for name: String) async -> Bool {
      await submit(.setString(value, name: name))
    }

    /// Sets an ARGB colour named variable; answers as ``setFloat(_:for:)`` does.
    @discardableResult
    public func setColor(_ argb: UInt32, for name: String) async -> Bool {
      await submit(.setColor(argb, name: name))
    }

    /// Sets an integer named variable — a boolean is 1 or 0 — and answers as
    /// ``setFloat(_:for:)`` does.
    @discardableResult
    public func setInteger(_ value: Int, for name: String) async -> Bool {
      await submit(.setInteger(value, name: name))
    }

    /// Writes a named variable and answers how the document took it. Unlike the `set*` methods,
    /// it tells a write the player cannot take yet (`.deferred`) from one the document refuses;
    /// offer a deferred write again once ``inputAvailability`` signals.
    @_spi(RemoteComposeController)
    public func submit(
      _ value: RemoteComposeNativeNamedValue, for name: String
    ) async -> RemoteComposeNativeInputAdmission {
      await admit(Self.input(value, for: name))
    }

    private static func input(
      _ value: RemoteComposeNativeNamedValue, for name: String
    ) -> NativePlayerEngine.Input {
      switch value {
      case .float(let value): .setFloat(value, name: name)
      case .text(let value): .setString(value, name: name)
      case .color(let argb): .setColor(argb, name: name)
      case .integer(let value): .setInteger(value, name: name)
      }
    }

    private func submit(_ input: NativePlayerEngine.Input) async -> Bool {
      await admit(input) == .accepted
    }

    private func admit(
      _ input: NativePlayerEngine.Input
    ) async -> RemoteComposeNativeInputAdmission {
      await withCheckedContinuation {
        (continuation: CheckedContinuation<RemoteComposeNativeInputAdmission, Never>) in
        enqueue(input) { admission in continuation.resume(returning: admission) }
      }
    }

    /// Queue `input` behind the open session's pending work. `reply` is called exactly once:
    /// `.deferred` while the document is opening, the scene is in the background or the queue is
    /// full, and `.refused` when no document will open.
    private func enqueue(
      _ input: NativePlayerEngine.Input, reply: ((RemoteComposeNativeInputAdmission) -> Void)?
    ) {
      guard let presentation = openPresentation else {
        reply?(isOpeningDocument ? .deferred : .refused)
        return
      }
      guard isSceneActive,
        presentation.inputs.append(NativePendingInput(input: input, reply: reply))
      else {
        reply?(.deferred)
        return
      }
      startPump(for: presentation)
    }

    private func startPump(for presentation: NativePlayerPresentation) {
      guard presentation.pump == nil else { return }
      let generation = self.generation
      presentation.pump = Task { [weak self] in
        await self?.drain(presentation, generation: generation)
      }
    }

    /// Run a presentation's work to completion, one item at a time, in arrival order: an accepted
    /// frame (only ever accepted while no input is outstanding, so it predates any queued input),
    /// then queued input, then the frame that was deferred behind that input. Stops as soon as the
    /// generation moves on, leaving the presentation to whoever superseded it.
    private func drain(_ presentation: NativePlayerPresentation, generation: UInt64) async {
      while generation == self.generation {
        if let request = presentation.pendingFrame {
          presentation.pendingFrame = nil
          presentation.activity = .frame
          await presentFrame(request, on: presentation, generation: generation)
          guard generation == self.generation else { return }
          presentation.activity = .idle
          if scheduledFramePending { requestScheduledFrame() }
        } else if let pending = presentation.inputs.popFirst() {
          presentation.activity = .input
          // The queue has room for one more input: wake one write waiting for it, not every one,
          // or `n` waiting writes would cost about `n²/2` offers. Once it is empty, wake them all,
          // so a woken write that gave up instead cannot leave the others waiting.
          if presentation.inputs.isEmpty {
            inputAvailability.signal()
          } else {
            inputAvailability.signalOne()
          }
          await applyInput(pending, to: presentation, generation: generation)
        } else if presentation.activity == .input {
          // The last input is done: take the frame that waited for it.
          presentation.activity = .idle
          if let deferredFrameTime = presentation.deferredFrameTime {
            presentation.deferredFrameTime = nil
            renderFrame(at: deferredFrameTime)
          } else if scheduledFramePending {
            requestScheduledFrame()
          }
        } else {
          presentation.pump = nil
          return
        }
      }
    }

    private func presentFrame(
      _ request: NativeFrameRequest, on presentation: NativePlayerPresentation,
      generation: UInt64
    ) async {
      do {
        let frame = try await presentation.engine.frame(
          at: request.time, wallClock: request.wallClock,
          visibilityAnimations: !UIAccessibility.isReduceMotionEnabled)
        guard generation == self.generation else { return }
        let model = try NativeDocument(
          frame: frame, timeSeconds: request.time, limits: executionLimits,
          androidCompatibility: androidCompatibility)
        try validate(model)
        try validateExecution(model, events: frame.effects)
        guard generation == self.generation else { return }
        var installError: (any Error)?
        do {
          try install(model, for: presentation)
        } catch {
          installError = error
        }
        // Input from here on resolves at this frame's time, even if installing it failed.
        await presentation.engine.present(frameTime: request.time)
        if let installError { throw installError }
        dispatch(frame.effects, generation: generation)
      } catch {
        guard generation == self.generation else { return }
        show(error: error, retaining: presentation)
      }
    }

    private func applyInput(
      _ pending: NativePendingInput, to presentation: NativePlayerPresentation,
      generation: UInt64
    ) async {
      let result: Result<NativePlayerEngine.Update, any Error>
      do {
        result = .success(try await presentation.engine.apply(pending.input))
      } catch {
        result = .failure(error)
      }
      guard generation == self.generation else {
        // Superseded by a reload or the background: the session that applied it is discarded and
        // the bytes are reopened, so the frame it produced is never shown and the writer is told
        // `.deferred`. Nothing from a superseded session reaches the screen or the host's
        // callbacks; a named value it changed is carried into a session reopened over the same
        // bytes.
        pending.reply?(.deferred)
        return
      }
      switch result {
      case .success(let update):
        onInputApplied?(pending.input)
        // Evaluated first: an optional-chained call skips its argument when a gesture has no reply.
        let accepted = presentInput(update, on: presentation, generation: generation)
        pending.reply?(accepted ? .accepted : .refused)
      case .failure(let error):
        if !(error is CancellationError) { show(error: error, retaining: presentation) }
        pending.reply?(.refused)
      }
    }

    /// Install the frame an input produced and deliver its events. Returns whether the document
    /// accepted the input.
    private func presentInput(
      _ update: NativePlayerEngine.Update, on presentation: NativePlayerPresentation,
      generation: UInt64
    ) -> Bool {
      do {
        if !presentation.inputs.isEmpty {
          // A newer input will install its own frame; validate and deliver this input's events in
          // order without building and discarding an intermediate NativeDocument.
          var budget = NativeFrameBudget()
          try validateEvents(update.events, budget: &budget)
          dispatch(update.events, generation: generation)
          return update.accepted
        }
        let model = try NativeDocument(
          frame: update.frame, timeSeconds: update.timeSeconds, limits: executionLimits,
          androidCompatibility: androidCompatibility)
        try validateExecution(model, events: update.events)
        try validate(model)
        guard generation == self.generation, presentation.inputs.isEmpty else {
          return update.accepted
        }
        try install(model, for: presentation)
        dispatch(update.events, generation: generation)
        return update.accepted
      } catch {
        guard generation == self.generation else { return false }
        show(error: error, retaining: presentation)
        return false
      }
    }

    private func dispatch(_ events: [RemoteComposeNativePlayerEvent], generation: UInt64) {
      for event in events {
        guard generation == self.generation else { return }
        switch event {
        case .haptic(let type):
          if let hapticHandler { hapticHandler(type) } else { performNativeDocumentHaptic(type) }
        case .playSound(let id, let data):
          soundPlayer.playSound(id: id, data: data)
        default: break
        }
        onEvent(event)
      }
    }

    private func validate(_ model: NativeDocument) throws {
      let diagnostics = model.diagnostics(availableCustomComponents: customComponents.names)
      onDiagnostics(diagnostics)
      if !RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: compatibilityPolicy, diagnostics: diagnostics)
      {
        throw RemoteComposeNativePlayerError.incompatible(diagnostics)
      }
    }

    private func validateExecution(
      _ model: NativeDocument, events: [RemoteComposeNativePlayerEvent]
    ) throws {
      var budget = model.executionBudget
      try validateEvents(events, budget: &budget)
    }

    private func validateEvents(
      _ events: [RemoteComposeNativePlayerEvent], budget: inout NativeFrameBudget
    ) throws {
      for event in events {
        switch event {
        case .action:
          try budget.recordEvent(limits: executionLimits)
        case .actionWithMetadata(_, let metadata):
          try budget.recordEvent(strings: [metadata], limits: executionLimits)
        case .namedAction(let name, let value):
          switch value {
          case .none, .float, .integer:
            try budget.recordEvent(strings: [name], limits: executionLimits)
          case .text(let text):
            try budget.recordEvent(strings: [name, text], limits: executionLimits)
          case .floatList(let values):
            try budget.recordEvent(
              additionalWork: values.count, strings: [name], limits: executionLimits)
          }
        case .debug(let message, _, _):
          try budget.recordEvent(strings: [message], limits: executionLimits)
        case .haptic:
          try budget.recordEvent(limits: executionLimits)
        case .playSound:
          try budget.recordEvent(limits: executionLimits)
        }
      }
    }

    private func install(
      _ model: NativeDocument, for presentation: NativePlayerPresentation
    ) throws {
      try presentation.resources.activateFonts(replacing: installedResources)
      installedResources = presentation.resources
      // Before the view update, so a callback it raises already finds this session.
      state = .presenting(presentation)
      if documentView?.update(
        document: model, resources: presentation.resources, customComponents: customComponents)
        != true
      {
        let nextView = NativeDocumentView(
          document: model,
          resources: presentation.resources,
          customComponents: customComponents,
          onGesture: { [weak self] componentID, gesture, sample in
            self?.performGesture(gesture, componentID: componentID, sample: sample)
          },
          onClickArea: { [weak self] x, y in
            self?.enqueue(.clickAreas(x: x, y: y), reply: nil)
          },
          onCustomReturn: { [weak self] componentID, propertyID, value in
            guard let self else {
              if case .acknowledgedText(_, let completion) = value { completion(false) }
              return
            }
            self.performCustomReturn(
              componentID: componentID, propertyID: propertyID, value: value)
          })
        replaceDocumentView(with: nextView)
      }
      frameSchedule = model.frameSchedule
      wakeCountdown.reset(after: frameSchedule.wakeAfter)
      errorLabel.isHidden = true
      updateFrameDriver()
    }

    private func show(error: Error, retaining presentation: NativePlayerPresentation?) {
      stopFrameDriver()
      state = .failed(presentation)
      if presentation == nil {
        // The open failed. A write waiting for it gets its answer, `.refused`, from the failed
        // state; the next session to open starts from the values this one would have, and from
        // `namedValueSeed` again.
        inputAvailability.signal()
      }
      scheduledFramePending = false
      errorLabel.text = error.localizedDescription
      errorLabel.isHidden = false
      report(error)
    }

    private func report(_ error: any Error) {
      if let error = error as? RemoteComposeNativePlayerError {
        onError(error)
      } else {
        onError(.decode(error.localizedDescription))
      }
    }

    private func performGesture(
      _ gesture: NativeSwiftGestureKind, componentID: Int,
      sample: NativeSwiftPointerSample?
    ) {
      enqueue(.gesture(gesture, componentID: componentID, sample: sample), reply: nil)
    }

    private func performCustomReturn(
      componentID: Int, propertyID: Int, value: NativeCustomReturnValue
    ) {
      switch value {
      case .float(let value):
        enqueue(
          .customFloat(value, componentID: componentID, propertyID: propertyID), reply: nil)
      case .text(let value):
        enqueue(
          .customText(value, componentID: componentID, propertyID: propertyID), reply: nil)
      case .acknowledgedText(let value, let completion):
        enqueue(
          .customText(value, componentID: componentID, propertyID: propertyID),
          reply: { completion($0 == .accepted) })
      }
    }

    // MARK: - View

    public override func layoutSubviews() {
      super.layoutSubviews()
      documentView?.frame = bounds
      errorLabel.frame = bounds.insetBy(dx: 24, dy: 24)
    }

    public override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
      super.traitCollectionDidChange(previousTraitCollection)
      guard previousTraitCollection?.displayScale != traitCollection.displayScale else { return }
      followDisplayScale()
    }

    public override func didMoveToWindow() {
      super.didMoveToWindow()
      followWindowScene()
      if window == nil {
        animationTimeline.pause(at: clock.now())
      } else if isSceneActive {
        if UIAccessibility.isReduceMotionEnabled {
          animationTimeline.pause(at: clock.now())
        } else {
          animationTimeline.resume(at: clock.now())
        }
      }
      updateFrameDriver()
    }

    private func replaceDocumentView(with nextView: NativeDocumentView) {
      documentView?.removeFromSuperview()
      insertSubview(nextView, belowSubview: errorLabel)
      documentView = nextView
      setNeedsLayout()
      // A replaced tree drops every element VoiceOver knew; tell it to re-read the screen. An
      // in-place update keeps the elements, so it does not announce anything.
      if UIAccessibility.isVoiceOverRunning {
        UIAccessibility.post(notification: .layoutChanged, argument: nil)
      }
    }

    private func configureErrorLabel() {
      errorLabel.accessibilityIdentifier = "rc-native-error"
      errorLabel.numberOfLines = 0
      errorLabel.textAlignment = .center
      errorLabel.textColor = .secondaryLabel
      errorLabel.isHidden = true
      addSubview(errorLabel)
    }

    private func applyBackground() {
      let opaque = playerBackground == .opaque
      isOpaque = opaque
      layer.isOpaque = opaque
      backgroundColor = opaque ? .systemBackground : .clear
    }

    // MARK: - Frame driver

    private func updateFrameDriver() {
      let now = clock.now()
      wakeCountdown.pause(at: now)
      delayedWakeTask?.cancel()
      delayedWakeTask = nil
      let mode = frameSchedule.driverMode(
        isActive: isSceneActive,
        isVisible: window != nil,
        reduceMotion: UIAccessibility.isReduceMotionEnabled)
      switch mode {
      case .idle:
        displayLink?.invalidate()
        displayLink = nil
      case .displayLink:
        guard displayLink == nil else { return }
        let link = CADisplayLink(
          target: displayLinkTarget, selector: #selector(NativeDisplayLinkTarget.fire))
        if #available(iOS 15.0, *) {
          // A document states whether it animates, not at what rate, so ask for the system's
          // range: full rate on ProMotion and visionOS where the app allows it, 60 Hz elsewhere.
          link.preferredFrameRateRange = .default
        }
        displayLinkTarget.displayLink = link
        link.add(to: .main, forMode: .common)
        displayLink = link
      case .wake(let delay):
        displayLink?.invalidate()
        displayLink = nil
        let remainingDelay = wakeCountdown.start(after: delay, at: now)
        // Replaced timers are cancelled on the main actor, the same actor this resumes on, so a
        // cancelled one always observes it here.
        delayedWakeTask = Task { [weak self] in
          let maximumDelay = TimeInterval(UInt64.max / 1_000_000_000)
          let nanoseconds = UInt64(min(remainingDelay, maximumDelay) * 1_000_000_000)
          try? await Task.sleep(nanoseconds: nanoseconds)
          guard !Task.isCancelled, let self else { return }
          self.delayedWakeTask = nil
          self.requestScheduledFrame()
        }
      }
    }

    private func stopFrameDriver() {
      wakeCountdown.pause(at: clock.now())
      delayedWakeTask?.cancel()
      delayedWakeTask = nil
      displayLink?.invalidate()
      displayLink = nil
    }

    fileprivate func displayLinkDidFire() {
      if frameSchedule.requestsNextFrame, !frameSchedule.needsContinuousFrames {
        displayLink?.invalidate()
        displayLink = nil
      }
      requestScheduledFrame()
    }

    private func requestScheduledFrame() {
      guard isSceneActive, window != nil else { return }
      guard !isBusy else {
        scheduledFramePending = true
        return
      }
      scheduledFramePending = false
      let now = clock.now()
      let time: TimeInterval
      if UIAccessibility.isReduceMotionEnabled {
        time = animationTimeline.sampleFunctional(at: now)
      } else {
        time = animationTimeline.sample(at: now)
      }
      renderFrame(at: time)
    }

    // MARK: - Lifecycle

    @objc private func reduceMotionStatusDidChange() {
      if UIAccessibility.isReduceMotionEnabled {
        animationTimeline.pause(at: clock.now())
      } else if isSceneActive, window != nil {
        animationTimeline.resume(at: clock.now())
      }
      updateFrameDriver()
    }

    /// The density the player uses until the host configures one; see `hostDensity`.
    private var automaticHostDensity: Float {
      guard androidCompatibility == .enabled else { return 1 }
      let scale = Float(traitCollection.displayScale)
      return scale.isFinite && scale > 0 ? scale : 1
    }

    /// Re-derive an unconfigured host density after a trait change, reloading if it moved.
    private func followDisplayScale() {
      guard !hostDensityIsExplicit else { return }
      let density = automaticHostDensity
      guard density != hostDensity else { return }
      hostDensity = density
      if let documentData { render(documentData) }
    }

    /// Observe the lifecycle of the window scene this view is now in, and adopt its state.
    ///
    /// Scene notifications rather than the application's: an app extension has no
    /// `UIApplication.shared`, and on iPadOS and visionOS one scene can be in the background while
    /// another is on screen. A view out of any window keeps following its last scene. Like the
    /// application-level contract this replaces, only the background pauses the player; a scene
    /// that is merely inactive (an overlay, Control Center) keeps animating.
    private func followWindowScene() {
      guard let scene = window?.windowScene, scene !== observedScene else { return }
      let center = NotificationCenter.default
      if let observedScene {
        center.removeObserver(
          self, name: UIScene.didEnterBackgroundNotification, object: observedScene)
        center.removeObserver(self, name: UIScene.didActivateNotification, object: observedScene)
      }
      observedScene = scene
      center.addObserver(
        self, selector: #selector(sceneDidEnterBackground),
        name: UIScene.didEnterBackgroundNotification, object: scene)
      center.addObserver(
        self, selector: #selector(sceneDidActivate),
        name: UIScene.didActivateNotification, object: scene)
      let isBackground = scene.activationState == .background
      if isBackground, isSceneActive {
        sceneDidEnterBackground()
      } else if !isBackground, !isSceneActive {
        sceneDidActivate()
      }
    }

    @objc private func sceneDidEnterBackground() {
      isSceneActive = false
      // An interrupted open, or input that never reached the screen, is replayed from the bytes on
      // activation; an idle session simply resumes.
      let reopen: Bool
      switch state {
      case .loading, .awaitingForeground: reopen = true
      case .presenting(let presentation): reopen = presentation.hasOutstandingInput
      case .failed(let presentation): reopen = presentation?.hasOutstandingInput ?? false
      case .idle: reopen = false
      }
      animationTimeline.pause(at: clock.now())
      stopFrameDriver()
      // The session is replaced on activation; what it holds is read before its work is dropped.
      if reopen { carryNamedValues() }
      abandonWork()
      scheduledFramePending = false
      if reopen, documentData != nil { state = .awaitingForeground }
    }

    @objc private func sceneDidActivate() {
      isSceneActive = true
      if UIAccessibility.isReduceMotionEnabled {
        animationTimeline.pause(at: clock.now())
      } else {
        animationTimeline.resume(at: clock.now())
      }
      if case .awaitingForeground = state, let documentData {
        render(documentData)
      } else {
        updateFrameDriver()
      }
      // Input refused for the background may be taken now, or deferred to the reopen.
      inputAvailability.signal()
    }
  }
#endif
