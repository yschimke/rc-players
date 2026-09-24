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
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = nil,
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

    @discardableResult
    public func setFloat(_ value: Float, for name: String) async -> Bool {
      await playerView.setFloat(value, for: name)
    }

    @discardableResult
    public func setString(_ value: String, for name: String) async -> Bool {
      await playerView.setString(value, for: name)
    }

    @discardableResult
    public func setColor(_ argb: UInt32, for name: String) async -> Bool {
      await playerView.setColor(argb, for: name)
    }

    @discardableResult
    public func setInteger(_ value: Int, for name: String) async -> Bool {
      await playerView.setInteger(value, for: name)
    }
  }

  /// How many host inputs may wait behind the one in flight. Beyond this an input is refused
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
    /// Receives whether the document accepted the input; nil for a gesture nobody awaits.
    let reply: ((Bool) -> Void)?
  }

  private struct NativeFrameRequest {
    let time: TimeInterval
    let wallClock: NativeSwiftWallClock?
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

    /// Drop everything pending. Queued input is answered `false`; whatever the pump is awaiting is
    /// discarded by the view's generation check when it returns.
    func abandon() {
      pump?.cancel()
      pump = nil
      activity = .idle
      pendingFrame = nil
      deferredFrameTime = nil
      for pending in inputs.removeAll() { pending.reply?(false) }
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
    public var onError: (RemoteComposeNativePlayerError) -> Void
    public private(set) var resourceLimits: RemoteComposeNativeResourceLimits
    public private(set) var executionLimits: RemoteComposeNativeExecutionLimits
    public private(set) var customComponents: RemoteComposeNativeCustomComponentRegistry
    private var customComponentsRevision: UInt
    public private(set) var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    public private(set) var downloadableFontResolver:
      (any RemoteComposeDownloadableFontResolving)?
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
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = nil,
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
      NotificationCenter.default.removeObserver(self)
    }

    public func load(_ data: Data) {
      if data == documentData, !isShowingError { return }
      if data != documentData {
        animationTimeline.reset(
          at: clock.now(),
          active: isSceneActive && window != nil && !UIAccessibility.isReduceMotionEnabled)
      }
      documentData = data
      render(data)
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
      hostDensityIsExplicit = true
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

    /// The open session that accepts input, whether or not an error is showing.
    private var openPresentation: NativePlayerPresentation? {
      switch state {
      case .presenting(let presentation): return presentation
      case .failed(let presentation): return presentation
      case .idle, .loading, .awaitingForeground: return nil
      }
    }

    /// Whether a scheduled frame has to wait: a document is opening, or its pump has work.
    private var isBusy: Bool {
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

    private func render(_ data: Data) {
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
      let task = Task { [weak self] in
        do {
          let (engine, frame) = try await NativePlayerEngine.open(
            data: data, maximumDocumentBytes: executionLimits.maximumDocumentBytes,
            hostDensity: hostDensity, hostFontScale: hostFontScale,
            wallClock: wallClock)
          try Task.checkCancellation()
          let model = try NativeDocument(
            frame: frame, timeSeconds: 0, limits: executionLimits,
            androidCompatibility: androidCompatibility)
          guard generation == self?.generation else { return }
          let diagnostics = model.diagnostics(
            availableCustomComponents: availableCustomComponents)
          self?.onDiagnostics(diagnostics)
          if !RemoteComposeNativeCompatibilityDecision.shouldRender(
            policy: compatibilityPolicy, diagnostics: diagnostics)
          {
            throw RemoteComposeNativePlayerError.incompatible(diagnostics)
          }
          try Task.checkCancellation()
          let resources = try await Self.prepareResources(
            for: model, limits: resourceLimits, resolver: resourceResolver,
            downloadableFontResolver: downloadableFontResolver,
            cache: resourceCache)
          try Task.checkCancellation()
          guard let self, generation == self.generation else { return }
          self.presentOpened(model, engine: engine, resources: resources)
        } catch {
          guard !Task.isCancelled, let self, generation == self.generation else { return }
          self.show(error: error, retaining: nil)
        }
      }
      state = .loading(task)
    }

    /// Install the first frame of a freshly opened session.
    private func presentOpened(
      _ model: NativeDocument, engine: NativePlayerEngine, resources: NativeResourceStore
    ) {
      let presentation = NativePlayerPresentation(engine: engine, resources: resources)
      do {
        try install(model, for: presentation)
      } catch {
        // The session is open even though its first frame could not be installed, so it keeps
        // accepting input, exactly as after any later failure.
        show(error: error, retaining: presentation)
        return
      }
      if scheduledFramePending { requestScheduledFrame() }
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

    @discardableResult
    public func setFloat(_ value: Float, for name: String) async -> Bool {
      await submit(.setFloat(value, name: name))
    }

    @discardableResult
    public func setString(_ value: String, for name: String) async -> Bool {
      await submit(.setString(value, name: name))
    }

    @discardableResult
    public func setColor(_ argb: UInt32, for name: String) async -> Bool {
      await submit(.setColor(argb, name: name))
    }

    @discardableResult
    public func setInteger(_ value: Int, for name: String) async -> Bool {
      await submit(.setInteger(value, name: name))
    }

    private func submit(_ input: NativePlayerEngine.Input) async -> Bool {
      await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
        enqueue(input) { accepted in continuation.resume(returning: accepted) }
      }
    }

    /// Queue `input` behind the open session's pending work. `reply` is called exactly once.
    private func enqueue(_ input: NativePlayerEngine.Input, reply: ((Bool) -> Void)?) {
      guard isSceneActive, let presentation = openPresentation,
        presentation.inputs.append(NativePendingInput(input: input, reply: reply))
      else {
        reply?(false)
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
          at: request.time, wallClock: request.wallClock)
        guard generation == self.generation else { return }
        let model = try NativeDocument(
          frame: frame, timeSeconds: request.time, limits: executionLimits,
          androidCompatibility: androidCompatibility)
        try validate(model)
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
        // Superseded by a reload or the background: report what the session said, but nothing
        // from a superseded session reaches the screen or the host's callbacks.
        if case .success(let update) = result {
          pending.reply?(update.accepted)
        } else {
          pending.reply?(false)
        }
        return
      }
      switch result {
      case .success(let update):
        // Evaluated first: an optional-chained call skips its argument when a gesture has no reply.
        let accepted = presentInput(update, on: presentation, generation: generation)
        pending.reply?(accepted)
      case .failure(let error):
        if !(error is CancellationError) { show(error: error, retaining: presentation) }
        pending.reply?(false)
      }
    }

    /// Install the frame an input produced and deliver its events. Returns whether the document
    /// accepted the input.
    private func presentInput(
      _ update: NativePlayerEngine.Update, on presentation: NativePlayerPresentation,
      generation: UInt64
    ) -> Bool {
      do {
        let model = try NativeDocument(
          frame: update.frame, timeSeconds: update.timeSeconds, limits: executionLimits,
          androidCompatibility: androidCompatibility)
        try validateExecution(model, events: update.events)
        guard presentation.inputs.isEmpty else {
          // A newer input will install its own frame; this one's events still go out, in order.
          dispatch(update.events, generation: generation)
          return update.accepted
        }
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
          onCustomReturn: { [weak self] componentID, propertyID, value in
            self?.performCustomReturn(
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
      scheduledFramePending = false
      errorLabel.text = error.localizedDescription
      errorLabel.isHidden = false
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
    }
  }
#endif
