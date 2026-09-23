#if canImport(UIKit) && canImport(SwiftUI)
  #if canImport(RcPlayerAppleFonts)
    import RcPlayerAppleFonts
  #endif
  import SwiftUI

  /// Optional SwiftUI host adapter. Rendering remains entirely UIKit/Core Graphics.
  @MainActor
  public struct RemoteComposeNativePlayerRepresentable: UIViewRepresentable {
    public let data: Data
    public var background: RemoteComposeNativePlayerBackground
    public var compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy
    public var androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility
    public var resourceLimits: RemoteComposeNativeResourceLimits
    public var executionLimits: RemoteComposeNativeExecutionLimits
    public var customComponents: RemoteComposeNativeCustomComponentRegistry?
    public var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    public var downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?
    public var onEvent: (RemoteComposeNativePlayerEvent) -> Void
    public var onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void

    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .opaque,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility = .disabled,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      executionLimits: RemoteComposeNativeExecutionLimits = .default,
      customComponents: RemoteComposeNativeCustomComponentRegistry? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = nil,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      self.data = data
      self.background = background
      self.compatibilityPolicy = compatibilityPolicy
      self.androidCompatibility = androidCompatibility
      self.resourceLimits = resourceLimits
      self.executionLimits = executionLimits
      self.customComponents = customComponents
      self.resourceResolver = resourceResolver
      self.downloadableFontResolver = downloadableFontResolver
      self.onEvent = onEvent
      self.onDiagnostics = onDiagnostics
    }

    public func makeUIView(context: Context) -> RemoteComposeNativePlayerView {
      RemoteComposeNativePlayerView(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
        androidCompatibility: androidCompatibility,
        resourceLimits: resourceLimits, executionLimits: executionLimits,
        customComponents: customComponents ?? .init(),
        resourceResolver: resourceResolver,
        downloadableFontResolver: downloadableFontResolver,
        onEvent: onEvent,
        onDiagnostics: onDiagnostics)
    }

    public func updateUIView(_ view: RemoteComposeNativePlayerView, context: Context) {
      view.playerBackground = background
      view.onEvent = onEvent
      view.onDiagnostics = onDiagnostics
      view.compatibilityPolicy = compatibilityPolicy
      view.androidCompatibility = androidCompatibility
      view.configureResources(limits: resourceLimits, resolver: resourceResolver)
      view.configureDownloadableFonts(resolver: downloadableFontResolver)
      view.configureExecutionLimits(executionLimits)
      if let customComponents { view.configureCustomComponents(customComponents) }
      view.load(data)
    }
  }
#endif

#if canImport(AppKit) && canImport(SwiftUI) && !targetEnvironment(macCatalyst)
  import AppKit
  import SwiftUI

  @MainActor
  public struct RemoteComposeNativePlayerRepresentable: NSViewRepresentable {
    public let data: Data
    public var compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy
    public var onEvent: (RemoteComposeNativePlayerEvent) -> Void
    public var onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void

    public init(
      data: Data,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      self.data = data
      self.compatibilityPolicy = compatibilityPolicy
      self.onEvent = onEvent
      self.onDiagnostics = onDiagnostics
    }

    public func makeCoordinator() -> Coordinator { Coordinator() }

    public func makeNSView(context: Context) -> NativeMacRepresentableHost {
      let host = NativeMacRepresentableHost()
      host.updateHandlers(onEvent: onEvent, onDiagnostics: onDiagnostics)
      host.load(data: data, policy: compatibilityPolicy)
      context.coordinator.data = data
      context.coordinator.policy = compatibilityPolicy
      return host
    }

    public func updateNSView(_ host: NativeMacRepresentableHost, context: Context) {
      host.updateHandlers(onEvent: onEvent, onDiagnostics: onDiagnostics)
      guard context.coordinator.data != data || context.coordinator.policy != compatibilityPolicy
      else { return }
      host.load(data: data, policy: compatibilityPolicy)
      context.coordinator.data = data
      context.coordinator.policy = compatibilityPolicy
    }

    public final class Coordinator {
      fileprivate var data = Data()
      fileprivate var policy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible
    }
  }

  @MainActor
  public final class NativeMacRepresentableHost: NSView {
    private var player: NSView?
    private var onEvent: (RemoteComposeNativePlayerEvent) -> Void = { _ in }
    private var onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }

    public override var isFlipped: Bool { true }

    func updateHandlers(
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void,
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void
    ) {
      self.onEvent = onEvent
      self.onDiagnostics = onDiagnostics
    }

    func load(data: Data, policy: RemoteComposeNativePlayerCompatibilityPolicy) {
      do {
        let compatibility: NativeMacCompatibility = policy == .strict ? .strict : .compatible
        let view = try NativeAppKitWindowController.makeView(
          data: data, compatibility: compatibility,
          onEvent: { [weak self] event in self?.onEvent(event) },
          onDiagnostics: { [weak self] diagnostics in self?.onDiagnostics(diagnostics) })
        player?.removeFromSuperview()
        player = view
        addSubview(view)
        needsLayout = true
      } catch {
        // The UIKit host reports decode failures through its diagnostics path; retain the existing
        // macOS view when a replacement document cannot be opened.
      }
    }

    public override func layout() {
      super.layout()
      player?.frame = bounds
    }
  }
#endif
