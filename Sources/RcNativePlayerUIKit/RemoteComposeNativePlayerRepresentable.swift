#if canImport(UIKit) && canImport(SwiftUI)
  import SwiftUI

  /// Optional SwiftUI host adapter. Rendering remains entirely UIKit/Core Graphics.
  @MainActor
  public struct RemoteComposeNativePlayerRepresentable: UIViewRepresentable {
    public let data: Data
    public var background: RemoteComposeNativePlayerBackground
    public var compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy
    public var resourceLimits: RemoteComposeNativeResourceLimits
    public var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    public var onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void

    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .opaque,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = nil,
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      self.data = data
      self.background = background
      self.compatibilityPolicy = compatibilityPolicy
      self.resourceLimits = resourceLimits
      self.resourceResolver = resourceResolver
      self.onDiagnostics = onDiagnostics
    }

    public func makeUIView(context: Context) -> RemoteComposeNativePlayerView {
      RemoteComposeNativePlayerView(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
        resourceLimits: resourceLimits, resourceResolver: resourceResolver,
        onDiagnostics: onDiagnostics)
    }

    public func updateUIView(_ view: RemoteComposeNativePlayerView, context: Context) {
      view.playerBackground = background
      view.onDiagnostics = onDiagnostics
      view.compatibilityPolicy = compatibilityPolicy
      view.configureResources(limits: resourceLimits, resolver: resourceResolver)
      view.load(data)
    }
  }
#endif
