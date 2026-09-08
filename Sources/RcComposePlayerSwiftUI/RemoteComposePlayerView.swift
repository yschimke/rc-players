#if canImport(UIKit)
  import RcComposePlayer
  import SwiftUI
  import UIKit

  @MainActor
  public final class RemoteComposePlayerViewController: UIViewController {
    private var documentData: Data
    private var configuration: RemoteComposePlayerConfiguration
    private var eventHandler: (RemoteComposePlayerEvent) -> Void
    private var errorHandler: (RemoteComposePlayerError) -> Void
    private var contentController: UIViewController?
    private var generation = 0

    public init(
      data: Data,
      configuration: RemoteComposePlayerConfiguration = .init(),
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) {
      documentData = data
      self.configuration = configuration
      eventHandler = onEvent
      errorHandler = onError
      super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    public override func viewDidLoad() {
      super.viewDidLoad()
      rebuildContent()
    }

    public func update(
      data: Data,
      configuration: RemoteComposePlayerConfiguration,
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void,
      onError: @escaping (RemoteComposePlayerError) -> Void
    ) {
      eventHandler = onEvent
      errorHandler = onError

      guard data != documentData || configuration != self.configuration else { return }
      documentData = data
      self.configuration = configuration
      if isViewLoaded { rebuildContent() }
    }

    private func rebuildContent() {
      generation += 1
      let activeGeneration = generation
      applyBackground()
      guard
        Bundle.main.object(forInfoDictionaryKey: "CADisableMinimumFrameDurationOnPhone") as? Bool
          == true
      else {
        show(.missingHighRefreshRatePlistEntry)
        return
      }

      let bytes: KotlinByteArray
      do {
        bytes = try kotlinBytes(from: documentData)
      } catch let error as RemoteComposePlayerError {
        show(error)
        return
      } catch {
        show(.playback(error.localizedDescription))
        return
      }

      var initialError: RemoteComposePlayerError?
      var isBuilding = true
      let player = RcComposeViewControllerKt.RcComposeViewController(
        bytes: bytes,
        theme: configuration.theme.kotlinValue,
        onEvent: { [weak self] event in
          guard let self, self.generation == activeGeneration else { return }
          self.eventHandler(swiftEvent(from: event))
        },
        typefaces: RcTypefaceLoaderCompanion.shared.Default,
        onError: { [weak self] message in
          guard let self, self.generation == activeGeneration else { return }
          let error = RemoteComposePlayerError.playback(message)
          if isBuilding {
            initialError = error
          } else {
            self.show(error)
          }
          self.errorHandler(error)
        },
        lenient: configuration.compatibility.isLenient,
        opaque: configuration.background.isOpaque
      )
      isBuilding = false

      if let initialError {
        install(RemoteComposePlayerErrorViewController(error: initialError))
      } else {
        install(player)
      }
    }

    private func show(_ error: RemoteComposePlayerError) {
      errorHandler(error)
      install(RemoteComposePlayerErrorViewController(error: error))
    }

    private func install(_ controller: UIViewController) {
      contentController?.willMove(toParent: nil)
      contentController?.view.removeFromSuperview()
      contentController?.removeFromParent()

      addChild(controller)
      view.addSubview(controller.view)
      controller.view.translatesAutoresizingMaskIntoConstraints = false
      NSLayoutConstraint.activate([
        controller.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
        controller.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        controller.view.topAnchor.constraint(equalTo: view.topAnchor),
        controller.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
      ])
      controller.didMove(toParent: self)
      contentController = controller
    }

    private func applyBackground() {
      let isOpaque = configuration.background.isOpaque
      view.isOpaque = isOpaque
      view.backgroundColor = isOpaque ? .systemBackground : .clear
      view.layer.isOpaque = isOpaque
    }
  }

  @MainActor
  public struct RemoteComposePlayerView: UIViewControllerRepresentable {
    public let data: Data
    public var configuration: RemoteComposePlayerConfiguration
    public var onEvent: (RemoteComposePlayerEvent) -> Void
    public var onError: (RemoteComposePlayerError) -> Void

    public init(
      data: Data,
      configuration: RemoteComposePlayerConfiguration = .init(),
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) {
      self.data = data
      self.configuration = configuration
      self.onEvent = onEvent
      self.onError = onError
    }

    public func makeUIViewController(context: Context) -> RemoteComposePlayerViewController {
      RemoteComposePlayerViewController(
        data: data, configuration: configuration, onEvent: onEvent, onError: onError)
    }

    public func updateUIViewController(
      _ controller: RemoteComposePlayerViewController, context: Context
    ) {
      controller.update(
        data: data, configuration: configuration, onEvent: onEvent, onError: onError)
    }
  }

  private final class RemoteComposePlayerErrorViewController: UIViewController {
    private let error: RemoteComposePlayerError

    init(error: RemoteComposePlayerError) {
      self.error = error
      super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    override func viewDidLoad() {
      super.viewDidLoad()
      view.backgroundColor = .clear

      let label = UILabel()
      label.text = error.localizedDescription
      label.textAlignment = .center
      label.textColor = .secondaryLabel
      label.numberOfLines = 0
      label.translatesAutoresizingMaskIntoConstraints = false
      view.addSubview(label)
      NSLayoutConstraint.activate([
        label.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
        label.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24),
        label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
        label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
      ])
    }
  }
#endif
