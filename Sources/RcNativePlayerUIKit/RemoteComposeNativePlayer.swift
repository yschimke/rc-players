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
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      playerView = RemoteComposeNativePlayerView(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
        resourceLimits: resourceLimits, resourceResolver: resourceResolver,
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
  }

  public enum RemoteComposeNativePlayerBackground: Equatable, Sendable {
    case opaque
    case transparent
  }

  /// Root UIKit view. It can be embedded without SwiftUI or the supplied view controller.
  @MainActor
  public final class RemoteComposeNativePlayerView: UIView {
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
    public private(set) var resourceLimits: RemoteComposeNativeResourceLimits
    public private(set) var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    private var documentView: NativeDocumentView?
    private var documentData: Data?
    private var loadTask: Task<Void, Never>?
    private var loadGeneration: UInt64 = 0
    private var isApplicationActive = true
    private var needsForegroundRender = false
    private var needsRetry = false
    private var retainedSession: NativeSnapshotSessionHandle?
    private var retainedResources: NativeResourceStore?
    private var resourceCache: NativeImageCache
    private let errorLabel = UILabel()

    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .opaque,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = nil,
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      playerBackground = background
      self.compatibilityPolicy = compatibilityPolicy
      self.resourceLimits = resourceLimits
      self.resourceResolver = resourceResolver
      resourceCache = NativeImageCache(
        countLimit: resourceLimits.maximumResourceCount,
        totalCostLimit: resourceLimits.maximumDecodedImageBytes)
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
      guard data != documentData || retainedSession == nil || needsRetry else { return }
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
      needsForegroundRender = false
      loadTask?.cancel()
      loadGeneration &+= 1
      let generation = loadGeneration
      loadTask = Task { [weak self] in
        do {
          let (session, frame) = try await NativeSnapshotSessionHandle.open(data: data)
          try Task.checkCancellation()
          guard let self, generation == self.loadGeneration else { return }
          let model = NativeDocument(snapshot: frame.snapshot)
          try self.validate(model)
          try Task.checkCancellation()
          guard generation == self.loadGeneration else { return }
          let resources = try await self.prepareResources(for: model, generation: generation)
          try Task.checkCancellation()
          guard generation == self.loadGeneration else { return }
          self.retainedSession = session
          self.install(model, resources: resources)
        } catch is CancellationError {
          return
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
      guard let retainedSession, let retainedResources else { return }
      loadTask?.cancel()
      loadGeneration &+= 1
      let generation = loadGeneration
      loadTask = Task { [weak self] in
        do {
          let frame = try await retainedSession.frame(at: timeSeconds)
          try Task.checkCancellation()
          guard let self, generation == self.loadGeneration else { return }
          let model = NativeDocument(snapshot: frame.snapshot)
          try self.validate(model)
          try Task.checkCancellation()
          guard generation == self.loadGeneration else { return }
          self.install(model, resources: retainedResources)
        } catch is CancellationError {
          return
        } catch {
          guard !Task.isCancelled, let self, generation == self.loadGeneration else { return }
          self.show(error: error)
        }
      }
    }

    private func validate(_ model: NativeDocument) throws {
      onDiagnostics(model.diagnostics)
      if !RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: compatibilityPolicy, diagnostics: model.diagnostics)
      {
        throw RemoteComposeNativePlayerError.incompatible(model.diagnostics)
      }
    }

    private func prepareResources(
      for model: NativeDocument,
      generation: UInt64
    ) async throws -> NativeResourceStore {
      let resources = try NativeResourceStore(
        resources: model.images,
        fonts: model.fonts,
        limits: resourceLimits,
        cache: resourceCache)
      if !resources.unresolvedImages.isEmpty {
        guard let resourceResolver else {
          throw RemoteComposeNativeResourceError.unresolvedReference(
            id: resources.unresolvedImages[0].id)
        }
        for request in resources.unresolvedImages {
          let resolved = try await resourceResolver.resolve(request)
          try Task.checkCancellation()
          guard generation == loadGeneration else { throw CancellationError() }
          try resources.insertResolved(data: resolved, for: request)
        }
      }
      return resources
    }

    private func install(_ model: NativeDocument, resources: NativeResourceStore) {
      if documentView?.update(document: model, resources: resources) != true {
        let nextView = NativeDocumentView(document: model, resources: resources)
        replaceDocumentView(with: nextView)
      }
      retainedResources = resources
      needsRetry = false
      errorLabel.isHidden = true
      loadTask = nil
    }

    private func show(error: Error) {
      needsRetry = true
      errorLabel.text = error.localizedDescription
      errorLabel.isHidden = false
      loadTask = nil
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
      if loadTask != nil { needsForegroundRender = true }
      loadTask?.cancel()
      loadTask = nil
      loadGeneration &+= 1
    }

    @objc private func applicationDidBecomeActive() {
      isApplicationActive = true
      guard needsForegroundRender, let documentData else { return }
      render(documentData)
    }
  }
#endif
