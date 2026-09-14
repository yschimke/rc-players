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
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      playerView = RemoteComposeNativePlayerView(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
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
    private var documentView: NativeDocumentView?
    private var documentData: Data?
    private let errorLabel = UILabel()

    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .opaque,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in }
    ) {
      playerBackground = background
      self.compatibilityPolicy = compatibilityPolicy
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

    public func load(_ data: Data) {
      guard data != documentData else { return }
      documentData = data
      render(data)
    }

    private func render(_ data: Data) {
      do {
        let snapshot = try Self.decode(data)
        let model = NativeDocument(snapshot: snapshot)
        onDiagnostics(model.diagnostics)
        if !RemoteComposeNativeCompatibilityDecision.shouldRender(
          policy: compatibilityPolicy, diagnostics: model.diagnostics)
        {
          throw RemoteComposeNativePlayerError.incompatible(model.diagnostics)
        }
        let nextView = NativeDocumentView(document: model)
        replaceDocumentView(with: nextView)
        errorLabel.isHidden = true
      } catch {
        documentView?.removeFromSuperview()
        documentView = nil
        errorLabel.text = error.localizedDescription
        errorLabel.isHidden = false
      }
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
