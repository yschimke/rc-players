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
    private var resourceTask: Task<Void, Never>?
    private var resourceCache: NativeImageCache
    private var fontRegistry: NativeFontRegistry
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
      fontRegistry = NativeFontRegistry(countLimit: resourceLimits.maximumResourceCount)
      self.onDiagnostics = onDiagnostics
      super.init(frame: .zero)
      isAccessibilityElement = false
      clipsToBounds = true
      configureErrorLabel()
      applyBackground()
      load(data)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    deinit {
      resourceTask?.cancel()
    }

    public func load(_ data: Data) {
      guard data != documentData else { return }
      resourceTask?.cancel()
      documentData = data
      render(data)
    }

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
      guard limits != resourceLimits || resolverChanged else { return }
      resourceTask?.cancel()
      resourceLimits = limits
      resourceResolver = resolver
      resourceCache = NativeImageCache(
        countLimit: limits.maximumResourceCount,
        totalCostLimit: limits.maximumDecodedImageBytes)
      fontRegistry.reset()
      fontRegistry = NativeFontRegistry(countLimit: limits.maximumResourceCount)
      if let documentData { render(documentData) }
    }

    private func render(_ data: Data) {
      resourceTask?.cancel()
      resourceTask = nil
      fontRegistry.reset()
      do {
        let snapshot = try Self.decode(data)
        let model = NativeDocument(snapshot: snapshot)
        onDiagnostics(model.diagnostics)
        if !RemoteComposeNativeCompatibilityDecision.shouldRender(
          policy: compatibilityPolicy, diagnostics: model.diagnostics)
        {
          throw RemoteComposeNativePlayerError.incompatible(model.diagnostics)
        }
        let resources = try NativeResourceStore(
          resources: model.images,
          fonts: model.fonts,
          limits: resourceLimits,
          cache: resourceCache,
          fontRegistry: fontRegistry)
        if resources.unresolvedImages.isEmpty {
          install(model, resources: resources)
        } else {
          guard let resourceResolver else {
            throw RemoteComposeNativeResourceError.unresolvedReference(
              id: resources.unresolvedImages[0].id)
          }
          resourceTask = Task { [weak self] in
            do {
              for request in resources.unresolvedImages {
                let data = try await resourceResolver.resolve(request)
                try Task.checkCancellation()
                try resources.insertResolved(data: data, for: request)
              }
              try Task.checkCancellation()
              self?.install(model, resources: resources)
            } catch is CancellationError {
              return
            } catch {
              guard !Task.isCancelled else { return }
              self?.show(error: error)
            }
          }
        }
      } catch {
        show(error: error)
      }
    }

    private func install(_ model: NativeDocument, resources: NativeResourceStore) {
      let nextView = NativeDocumentView(document: model, resources: resources)
      replaceDocumentView(with: nextView)
      errorLabel.isHidden = true
      resourceTask = nil
    }

    private func show(error: Error) {
      documentView?.removeFromSuperview()
      documentView = nil
      errorLabel.text = error.localizedDescription
      errorLabel.isHidden = false
      resourceTask = nil
    }

    public override func layoutSubviews() {
      super.layoutSubviews()
      documentView?.frame = bounds
      errorLabel.frame = bounds.insetBy(dx: 24, dy: 24)
    }

    private static func decode(_ data: Data) throws -> RcNativeDocumentSnapshot {
      guard data.count <= Int(Int32.max) else {
        throw RemoteComposeNativePlayerError.documentTooLarge(data.count)
      }
      let bytes = RcDataBridgeKt.rcByteArray(data: data)
      do {
        return try RcNativeSnapshotBridge.shared.decode(bytes: bytes)
      } catch {
        if let error = error as? RemoteComposeNativePlayerError { throw error }
        throw RemoteComposeNativePlayerError.decode(error.localizedDescription)
      }
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
  }
#endif
