#if canImport(UIKit)
  import Foundation
  import RcComposePlayer
  import UIKit

  /// A UIKit-native Remote Compose player proof of concept.
  ///
  /// The Kotlin framework currently decodes the wire format into an immutable snapshot. Everything
  /// from this type down is Swift and UIKit: lifecycle, component hierarchy, layout, and drawing.
  @MainActor
  public final class RemoteComposeNativePlayerViewController: UIViewController {
    public private(set) var playerView: RemoteComposeNativePlayerView

    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .opaque,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = nil,
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      playerView = RemoteComposeNativePlayerView(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
        resourceLimits: resourceLimits, resourceResolver: resourceResolver,
        onEvent: onEvent,
        onDiagnostics: onDiagnostics)
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
  }

  public enum RemoteComposeNativePlayerBackground: Equatable, Sendable {
    case opaque
    case transparent
  }

  /// Root UIKit view. It can be embedded without SwiftUI or the supplied view controller.
  @MainActor
  public final class RemoteComposeNativePlayerView: UIView {
    private enum PendingWork {
      case documentLoad
      case frame
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

    public var onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void
    public var onEvent: (RemoteComposeNativePlayerEvent) -> Void
    public private(set) var resourceLimits: RemoteComposeNativeResourceLimits
    public private(set) var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    private var documentView: NativeDocumentView?
    private var documentData: Data?
    private var loadTask: Task<Void, Never>?
    private var pendingWork: PendingWork?
    private var inputTail: Task<Void, Never>?
    private var loadGeneration: UInt64 = 0
    private var inputGeneration: UInt64 = 0
    private var sessionEpoch: UInt64 = 0
    private var retainedSessionEpoch: UInt64 = 0
    private var isApplicationActive = true
    private var needsForegroundRender = false
    private var isRenderingDocument = false
    private var needsRetry = false
    private var retainedSession: NativeSnapshotSessionHandle?
    private var retainedSessionData: Data?
    private var retainedResources: NativeResourceStore?
    private var currentFrameTime: TimeInterval = 0
    private var resourceCache: NativeImageCache
    private let errorLabel = UILabel()

    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .opaque,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = nil,
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      playerBackground = background
      self.compatibilityPolicy = compatibilityPolicy
      self.resourceLimits = resourceLimits
      self.resourceResolver = resourceResolver
      resourceCache = NativeImageCache(
        countLimit: resourceLimits.maximumResourceCount,
        totalCostLimit: resourceLimits.maximumDecodedImageBytes)
      self.onEvent = onEvent
      self.onDiagnostics = onDiagnostics
      super.init(frame: .zero)
      isApplicationActive = UIApplication.shared.applicationState != .background
      isAccessibilityElement = false
      clipsToBounds = true
      configureErrorLabel()
      applyBackground()
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(applicationDidEnterBackground),
        name: UIApplication.didEnterBackgroundNotification,
        object: nil)
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(applicationDidBecomeActive),
        name: UIApplication.didBecomeActiveNotification,
        object: nil)
      load(data)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    deinit {
      loadTask?.cancel()
      NotificationCenter.default.removeObserver(self)
    }

    public func load(_ data: Data) {
      if data == documentData, !needsRetry { return }
      loadTask?.cancel()
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
      case let (current?, next?): resolverChanged = current !== next
      default: resolverChanged = true
      }
      let limitsChanged = limits != resourceLimits
      guard limitsChanged || resolverChanged else { return }
      loadTask?.cancel()
      loadGeneration &+= 1
      resourceLimits = limits
      resourceResolver = resolver
      resourceCache = NativeImageCache(
        countLimit: limits.maximumResourceCount,
        totalCostLimit: limits.maximumDecodedImageBytes)
      if let documentData { render(documentData) }
    }

    private func render(_ data: Data) {
      guard isApplicationActive else {
        needsForegroundRender = true
        return
      }
      needsRetry = false
      needsForegroundRender = false
      isRenderingDocument = true
      loadTask?.cancel()
      inputTail = nil
      loadGeneration &+= 1
      inputGeneration &+= 1
      sessionEpoch &+= 1
      let generation = loadGeneration
      let compatibilityPolicy = compatibilityPolicy
      let resourceLimits = resourceLimits
      let resourceResolver = resourceResolver
      let resourceCache = resourceCache
      pendingWork = .documentLoad
      let epoch = sessionEpoch
      loadTask = Task { [weak self] in
        do {
          let (session, frame) = try await NativeSnapshotSessionHandle.open(data: data)
          try Task.checkCancellation()
          let model = NativeDocument(snapshot: frame.snapshot)
          guard generation == self?.loadGeneration else { return }
          self?.onDiagnostics(model.diagnostics)
          if !RemoteComposeNativeCompatibilityDecision.shouldRender(
            policy: compatibilityPolicy, diagnostics: model.diagnostics)
          {
            throw RemoteComposeNativePlayerError.incompatible(model.diagnostics)
          }
          try Task.checkCancellation()
          let resources = try await Self.prepareResources(
            for: model, limits: resourceLimits, resolver: resourceResolver, cache: resourceCache)
          try Task.checkCancellation()
          guard let self, generation == self.loadGeneration else { return }
          self.retainedSession = session
          self.retainedSessionData = data
          self.retainedSessionEpoch = epoch
          self.currentFrameTime = 0
          try self.install(model, resources: resources)
        } catch let error as CancellationError {
          guard !Task.isCancelled, let self, generation == self.loadGeneration else { return }
          self.show(error: error)
        } catch {
          guard !Task.isCancelled, let self, generation == self.loadGeneration else { return }
          self.show(error: error)
        }
      }
    }

    /// Resolve another immutable frame from the retained runtime without decoding the document.
    public func renderFrame(at timeSeconds: TimeInterval) {
      guard isApplicationActive else { return }
      guard loadTask == nil else { return }
      guard !needsRetry, retainedSessionData == documentData else { return }
      guard let retainedSession, let retainedResources else { return }
      loadTask?.cancel()
      loadGeneration &+= 1
      let generation = loadGeneration
      pendingWork = .frame
      loadTask = Task { [weak self] in
        do {
          let frame = try await retainedSession.frame(at: timeSeconds)
          try Task.checkCancellation()
          guard let self, generation == self.loadGeneration else { return }
          let model = NativeDocument(snapshot: frame.snapshot)
          try self.validate(model)
          try Task.checkCancellation()
          guard generation == self.loadGeneration else { return }
          self.currentFrameTime = timeSeconds
          try self.install(model, resources: retainedResources)
        } catch let error as CancellationError {
          guard !Task.isCancelled, let self, generation == self.loadGeneration else { return }
          self.show(error: error)
        } catch {
          guard !Task.isCancelled, let self, generation == self.loadGeneration else { return }
          self.show(error: error)
        }
      }
    }

    @discardableResult
    public func setFloat(_ value: Float, for name: String) async -> Bool {
      await updateSession { session, time in
        try await session.setFloat(value, for: name, at: time)
      }
    }

    @discardableResult
    public func setString(_ value: String, for name: String) async -> Bool {
      await updateSession { session, time in
        try await session.setString(value, for: name, at: time)
      }
    }

    @discardableResult
    public func setColor(_ argb: UInt32, for name: String) async -> Bool {
      await updateSession { session, time in
        try await session.setColor(argb, for: name, at: time)
      }
    }

    private func updateSession(
      _ operation: @escaping @Sendable (NativeSnapshotSessionHandle, TimeInterval) async throws ->
        NativeSnapshotSessionHandle.Update
    ) async -> Bool {
      guard
        isApplicationActive, retainedSessionEpoch == sessionEpoch,
        let retainedSession, let retainedResources
      else { return false }
      loadTask?.cancel()
      loadGeneration &+= 1
      let epoch = sessionEpoch
      let time = currentFrameTime
      let (input, task) = enqueueInput {
        try await operation(retainedSession, time)
      }
      switch await task.value {
      case .success(let update):
        guard
          epoch == sessionEpoch, retainedSessionEpoch == epoch,
          self.retainedSession === retainedSession
        else { return update.accepted }
        guard input == inputGeneration else {
          update.events.forEach(onEvent)
          return update.accepted
        }
        do {
          let model = NativeDocument(snapshot: update.frame.snapshot)
          try validate(model)
          try install(model, resources: retainedResources)
          update.events.forEach(onEvent)
          return update.accepted
        } catch {
          show(error: error)
          return false
        }
      case .failure(let error):
        guard epoch == sessionEpoch else { return false }
        if !(error is CancellationError) { show(error: error) }
        return false
      }
    }

    private func enqueueInput(
      _ operation: @escaping @Sendable () async throws -> NativeSnapshotSessionHandle.Update
    ) -> (UInt64, Task<Result<NativeSnapshotSessionHandle.Update, any Error>, Never>) {
      inputGeneration &+= 1
      let input = inputGeneration
      let previous = inputTail
      let task = Task<Result<NativeSnapshotSessionHandle.Update, any Error>, Never> {
        await previous?.value
        do {
          try Task.checkCancellation()
          return .success(try await operation())
        } catch {
          return .failure(error)
        }
      }
      inputTail = Task { _ = await task.value }
      return (input, task)
    }

    private func validate(_ model: NativeDocument) throws {
      onDiagnostics(model.diagnostics)
      if !RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: compatibilityPolicy, diagnostics: model.diagnostics)
      {
        throw RemoteComposeNativePlayerError.incompatible(model.diagnostics)
      }
    }

    private static func prepareResources(
      for model: NativeDocument,
      limits: RemoteComposeNativeResourceLimits,
      resolver: (any RemoteComposeNativeResourceResolving)?,
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
      return resources
    }

    private func install(_ model: NativeDocument, resources: NativeResourceStore) throws {
      try resources.activateFonts(replacing: retainedResources)
      isRenderingDocument = false
      if documentView?.update(document: model, resources: resources) != true {
        let nextView = NativeDocumentView(
          document: model,
          resources: resources,
          onClick: { [weak self] componentID in self?.performClick(componentID: componentID) })
        replaceDocumentView(with: nextView)
      }
      retainedResources = resources
      needsRetry = false
      errorLabel.isHidden = true
      loadTask = nil
      pendingWork = nil
    }

    private func show(error: Error) {
      isRenderingDocument = false
      needsRetry = true
      errorLabel.text = error.localizedDescription
      errorLabel.isHidden = false
      loadTask = nil
      pendingWork = nil
    }

    private func performClick(componentID: Int) {
      guard
        isApplicationActive, retainedSessionEpoch == sessionEpoch,
        let retainedSession, let retainedResources
      else { return }
      let epoch = sessionEpoch
      let time = currentFrameTime
      let (input, inputTask) = enqueueInput {
        try await retainedSession.click(componentID: componentID, at: time)
      }
      Task { [weak self] in
        switch await inputTask.value {
        case .success(let update):
          guard
            let self, epoch == self.sessionEpoch, self.retainedSessionEpoch == epoch,
            self.retainedSession === retainedSession
          else { return }
          if input == self.inputGeneration {
            do {
              let model = NativeDocument(snapshot: update.frame.snapshot)
              try self.validate(model)
              self.install(model, resources: retainedResources)
            } catch {
              self.show(error: error)
              return
            }
          }
          update.events.forEach(self.onEvent)
        case .failure(let error):
          guard let self, epoch == self.sessionEpoch else { return }
          if !(error is CancellationError) { self.show(error: error) }
        }
      }
    }

    public override func layoutSubviews() {
      super.layoutSubviews()
      documentView?.frame = bounds
      errorLabel.frame = bounds.insetBy(dx: 24, dy: 24)
    }

    private func replaceDocumentView(with nextView: NativeDocumentView) {
      documentView?.removeFromSuperview()
      insertSubview(nextView, belowSubview: errorLabel)
      documentView = nextView
      setNeedsLayout()
    }

    private func configureErrorLabel() {
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

    @objc private func applicationDidEnterBackground() {
      isApplicationActive = false
      needsForegroundRender = isRenderingDocument && documentData != nil
      isRenderingDocument = false
      loadTask?.cancel()
      loadTask = nil
      pendingWork = nil
      loadGeneration &+= 1
    }

    @objc private func applicationDidBecomeActive() {
      isApplicationActive = true
      guard needsForegroundRender, let documentData else { return }
      render(documentData)
    }
  }
#endif
