#if canImport(UIKit) && canImport(RcComposePlayer)
  import RcComposePlayer
  #if canImport(RcPlayerAppleFonts)
    import RcPlayerAppleFonts
  #endif
  import SwiftUI
  import UIKit

  @MainActor
  public final class RemoteComposePlayerViewController: UIViewController {
    private var documentData: Data
    private var configuration: RemoteComposePlayerConfiguration
    private var eventHandler: (RemoteComposePlayerEvent) -> Void
    private var errorHandler: (RemoteComposePlayerError) -> Void
    private var playerController: RemoteComposePlayerController
    private var downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?
    private var contentController: UIViewController?
    private var fontLoadTask: Task<Void, Never>?
    private var generation = 0

    public init(
      data: Data,
      controller: RemoteComposePlayerController? = nil,
      configuration: RemoteComposePlayerConfiguration = .init(),
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) {
      documentData = data
      playerController = controller ?? RemoteComposePlayerController()
      self.configuration = configuration
      self.downloadableFontResolver = downloadableFontResolver
      eventHandler = onEvent
      errorHandler = onError
      super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    deinit {
      fontLoadTask?.cancel()
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
      update(
        data: data, controller: playerController, configuration: configuration,
        downloadableFontResolver: downloadableFontResolver, onEvent: onEvent, onError: onError)
    }

    public func update(
      data: Data,
      controller: RemoteComposePlayerController,
      configuration: RemoteComposePlayerConfiguration,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void,
      onError: @escaping (RemoteComposePlayerError) -> Void
    ) {
      eventHandler = onEvent
      errorHandler = onError

      guard
        data != documentData || configuration != self.configuration || controller !== playerController
          || !sameResolver(downloadableFontResolver, self.downloadableFontResolver)
      else { return }
      documentData = data
      playerController = controller
      self.configuration = configuration
      self.downloadableFontResolver = downloadableFontResolver
      if isViewLoaded { rebuildContent() }
    }

    private func rebuildContent() {
      generation += 1
      let activeGeneration = generation
      fontLoadTask?.cancel()
      applyBackground()
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

      let requests = RcDownloadableFontsKt.rcDownloadableFontRequests(bytes: bytes)
      guard !requests.isEmpty else {
        buildContent(
          bytes: bytes,
          typefaces: RcAppleTypefaceLoaderKt.rcAppleTypefaceLoader(
            additional: RcTypefaceLoaderCompanion.shared.Default),
          activeGeneration: activeGeneration)
        return
      }
      guard let downloadableFontResolver else {
        buildContent(
          bytes: bytes,
          typefaces: RcAppleTypefaceLoaderKt.rcAppleTypefaceLoader(
            additional: RcDownloadableFontsKt.rcDownloadableFontFallback(
              families: requests.map(\.family))),
          activeGeneration: activeGeneration)
        return
      }
      fontLoadTask = Task { [weak self] in
        do {
          var fonts: [RcDownloadedFont] = []
          fonts.reserveCapacity(requests.count)
          for request in requests {
            try Task.checkCancellation()
            let resolved = try await downloadableFontResolver.resolve(
              RemoteComposeDownloadableFontRequest(family: request.family))
            guard resolved.family.caseInsensitiveCompare(request.family) == .orderedSame else {
              throw RemoteComposeDownloadableFontError.familyMismatch(
                expected: request.family, actual: resolved.family)
            }
            fonts.append(
              RcDownloadedFont(
                family: resolved.family, identity: resolved.identity,
                data: try kotlinBytes(from: resolved.data)))
          }
          try Task.checkCancellation()
          guard let self, self.generation == activeGeneration else { return }
          self.buildContent(
            bytes: bytes,
            typefaces: RcAppleTypefaceLoaderKt.rcAppleTypefaceLoader(
              additional: RcDownloadableFontsKt.rcDownloadedTypefaceLoader(fonts: fonts)),
            activeGeneration: activeGeneration)
        } catch is CancellationError {
          return
        } catch {
          guard let self, self.generation == activeGeneration else { return }
          self.show(.playback("Downloadable font loading failed: \(error.localizedDescription)"))
        }
      }
    }

    private func buildContent(
      bytes: KotlinByteArray, typefaces: RcTypefaceLoader, activeGeneration: Int
    ) {
      var initialError: RemoteComposePlayerError?
      var isBuilding = true
      let player = RcComposeViewControllerKt.RcComposeViewController(
        bytes: bytes,
        theme: configuration.theme.kotlinValue,
        onEvent: { [weak self] event in
          guard let self, self.generation == activeGeneration else { return }
          self.eventHandler(swiftEvent(from: event))
        },
        typefaces: typefaces,
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
        opaque: configuration.background.isOpaque,
        soundHost: RcSoundHostCompanion.shared.None,
        controller: playerController.backend.kotlinController
      )
      isBuilding = false

      if let initialError {
        install(RemoteComposePlayerErrorViewController(error: initialError))
      } else {
        install(player)
      }
    }

    private func sameResolver(
      _ first: (any RemoteComposeDownloadableFontResolving)?,
      _ second: (any RemoteComposeDownloadableFontResolving)?
    ) -> Bool {
      switch (first, second) {
      case (nil, nil): true
      case (let first?, let second?): first === second
      default: false
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
    @MainActor
    public final class Coordinator {
      fileprivate let defaultController = RemoteComposePlayerController()
    }

    public let data: Data
    public let controller: RemoteComposePlayerController?
    public var configuration: RemoteComposePlayerConfiguration
    public var downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?
    public var onEvent: (RemoteComposePlayerEvent) -> Void
    public var onError: (RemoteComposePlayerError) -> Void

    public init(
      data: Data,
      controller: RemoteComposePlayerController? = nil,
      configuration: RemoteComposePlayerConfiguration = .init(),
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) {
      self.data = data
      self.controller = controller
      self.configuration = configuration
      self.downloadableFontResolver = downloadableFontResolver
      self.onEvent = onEvent
      self.onError = onError
    }

    public func makeUIViewController(context: Context) -> RemoteComposePlayerViewController {
      RemoteComposePlayerViewController(
        data: data, controller: controller ?? context.coordinator.defaultController,
        configuration: configuration, downloadableFontResolver: downloadableFontResolver,
        onEvent: onEvent, onError: onError)
    }

    public func makeCoordinator() -> Coordinator {
      Coordinator()
    }

    public func updateUIViewController(
      _ controller: RemoteComposePlayerViewController, context: Context
    ) {
      controller.update(
        data: data, controller: self.controller ?? context.coordinator.defaultController,
        configuration: configuration, downloadableFontResolver: downloadableFontResolver,
        onEvent: onEvent, onError: onError)
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
#elseif canImport(UIKit)
  #if canImport(RcNativePlayerCore)
    import RcNativePlayerCore
  #endif
  #if canImport(RcNativePlayerUIKit)
    @_spi(RemoteComposeController) import RcNativePlayerUIKit
  #endif
  #if canImport(RcPlayerAppleFonts)
    import RcPlayerAppleFonts
  #endif
  import SwiftUI
  import UIKit

  @MainActor
  public final class RemoteComposePlayerViewController: UIViewController {
    private var documentData: Data
    private var configuration: RemoteComposePlayerConfiguration
    private var eventHandler: (RemoteComposePlayerEvent) -> Void
    private var errorHandler: (RemoteComposePlayerError) -> Void
    private var playerController: RemoteComposePlayerController
    private var downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?
    private var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    private var nativeController: RemoteComposeNativePlayerViewController?
    /// The latest native value update for each name, as a token from `nextNativeUpdateToken`. A
    /// delivery gives up once a newer update for its name has started, so a stale value cannot
    /// land last.
    private var latestNativeUpdates: [String: Int] = [:]
    private var nextNativeUpdateToken = 0
    /// The delivery of each name's latest value, cancelled when a newer one starts or the player is
    /// rebuilt, so a delivery waiting for the player does not outlive its value. Internal, not
    /// private, so a test can tell when every delivery has finished.
    private(set) var nativeUpdateTasks: [String: Task<Void, Never>] = [:]
    /// Reads the current document's declarations off the main thread; replaced on every rebuild.
    private var declarationTask: Task<Void, Never>?

    public init(
      data: Data,
      controller: RemoteComposePlayerController? = nil,
      configuration: RemoteComposePlayerConfiguration = .init(),
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = RemoteComposeNativeURLSessionResourceResolver.shared,
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) {
      documentData = data
      playerController = controller ?? RemoteComposePlayerController()
      self.configuration = configuration
      self.resourceResolver = resourceResolver
      self.downloadableFontResolver = downloadableFontResolver
      eventHandler = onEvent
      errorHandler = onError
      super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

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
      update(data: data, controller: playerController, configuration: configuration,
             downloadableFontResolver: downloadableFontResolver, resourceResolver: resourceResolver, onEvent: onEvent, onError: onError)
    }

    public func update(
      data: Data,
      controller: RemoteComposePlayerController,
      configuration: RemoteComposePlayerConfiguration,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = RemoteComposeNativeURLSessionResourceResolver.shared,
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void,
      onError: @escaping (RemoteComposePlayerError) -> Void
    ) {
      eventHandler = onEvent
      errorHandler = onError
      guard data != documentData || configuration != self.configuration || controller !== playerController
          || !sameResolver(downloadableFontResolver, self.downloadableFontResolver)
          || resourceResolver !== self.resourceResolver else { return }
      playerController.backend.installUpdateHandler(nil)
      documentData = data
      playerController = controller
      self.configuration = configuration
      self.resourceResolver = resourceResolver
      self.downloadableFontResolver = downloadableFontResolver
      if isViewLoaded { rebuildContent() }
    }

    private func rebuildContent() {
      playerController.backend.installUpdateHandler(nil)
      latestNativeUpdates.removeAll()
      for task in nativeUpdateTasks.values { task.cancel() }
      nativeUpdateTasks.removeAll()
      declarationTask?.cancel()
      declarationTask = nil
      // Writes are refused until the new document's declarations are read below; the values
      // written so far are kept for them rather than judged against no document at all.
      playerController.backend.detach()
      guard configuration.nativeFallbackSupportsTheme else {
        errorHandler(.playback("The native Swift fallback does not support an explicit dark theme yet."))
        return
      }
      let native = RemoteComposeNativePlayerViewController(
        data: documentData,
        background: configuration.nativeFallbackBackground,
        compatibilityPolicy: configuration.compatibility.nativeValue,
        resourceResolver: resourceResolver,
        downloadableFontResolver: downloadableFontResolver,
        onEvent: { [weak self] event in self?.eventHandler(.init(nativeEvent: event)) },
        onDiagnostics: { _ in },
        onError: { [weak self] error in self?.errorHandler(.playback(error.localizedDescription)) })
      install(native)
      // A session the player reopens over the same bytes — in this host, after the background
      // with input outstanding, since a configuration change rebuilds the player instead —
      // carries the named values the one it replaces held, the controller's and the document's
      // own, and is seeded with the controller's values; the player applies each one unless the
      // old session took that very write, so its first frame shows the newest value of each.
      // (The first open starts inside `init`, before this is set, and is not seeded.) The Kotlin
      // player does not reopen and keeps its state.
      //
      // A write still on its way when the reopen starts was meant for the session it discards; the
      // seed hands the new session the same value — the controller's latest for that name — so
      // that write is dropped rather than applied a second time.
      native.playerView.namedValueSeed = { [weak self, weak native] in
        guard let self, let native, self.nativeController === native else { return [] }
        let seed = self.playerController.backend.seedValues
        for (name, _) in seed {
          self.latestNativeUpdates[name] = nil
          self.nativeUpdateTasks.removeValue(forKey: name)?.cancel()
        }
        return seed
      }
      // Reading the declarations decodes the whole document, so it runs off the main thread, like
      // the session's own open. The controller is attached as soon as it finishes — normally well
      // before the session has opened — and replays the values written so far.
      let data = documentData
      let limits = native.playerView.executionLimits
      declarationTask = Task { [weak self, weak native] in
        let declarations = await Task.detached(priority: .userInitiated) {
          RemoteComposePlayerViewController.declarations(of: data, within: limits)
        }.value
        guard !Task.isCancelled, let self, let native, self.nativeController === native else {
          return
        }
        self.declarationTask = nil
        guard let declarations else { return }
        self.playerController.backend.attach(declarations)
        self.playerController.backend.installUpdateHandler { [weak self, weak native] name, value in
          guard let self else { return }
          let token = self.beginNativeUpdate(for: name)
          // The task takes its own weak references rather than sharing the handler's captures.
          self.nativeUpdateTasks[name] = Task { @MainActor [weak self, weak native] in
            await value.deliver(for: name, to: { native }) {
              self?.latestNativeUpdates[name] == token
            }
            if self?.latestNativeUpdates[name] == token { self?.nativeUpdateTasks[name] = nil }
          }
        }
      }
    }

    /// The declarations of `data`, or nil when the native player will refuse the document — over
    /// its size limit, or not decodable — and report that failure itself. The limit is checked
    /// first so an oversized document is not decoded here only to be refused.
    nonisolated private static func declarations(
      of data: Data, within limits: RemoteComposeNativeExecutionLimits
    ) -> NativeSwiftNamedVariableDeclarations? {
      guard data.count <= limits.maximumDocumentBytes, data.count <= Int(Int32.max) else {
        return nil
      }
      return try? NativeSwiftNamedVariableDeclarations.declared(in: data)
    }

    private func beginNativeUpdate(for name: String) -> Int {
      let token = nextNativeUpdateToken
      nextNativeUpdateToken += 1
      latestNativeUpdates[name] = token
      nativeUpdateTasks.removeValue(forKey: name)?.cancel()
      return token
    }

    private func install(_ controller: RemoteComposeNativePlayerViewController) {
      nativeController?.willMove(toParent: nil)
      nativeController?.view.removeFromSuperview()
      nativeController?.removeFromParent()
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
      nativeController = controller
    }

    private func sameResolver(
      _ first: (any RemoteComposeDownloadableFontResolving)?,
      _ second: (any RemoteComposeDownloadableFontResolving)?
    ) -> Bool {
      switch (first, second) {
      case (nil, nil): true
      case (let first?, let second?): first === second
      default: false
      }
    }
  }

  @MainActor
  public struct RemoteComposePlayerView: UIViewControllerRepresentable {
    @MainActor public final class Coordinator { fileprivate let defaultController = RemoteComposePlayerController() }
    public let data: Data
    public let controller: RemoteComposePlayerController?
    public var configuration: RemoteComposePlayerConfiguration
    public var downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?
    public var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    public var onEvent: (RemoteComposePlayerEvent) -> Void
    public var onError: (RemoteComposePlayerError) -> Void

    public init(data: Data, controller: RemoteComposePlayerController? = nil,
                configuration: RemoteComposePlayerConfiguration = .init(),
                downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? = RemoteComposeNativeURLSessionResourceResolver.shared,
                onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
                onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }) {
      self.data = data; self.controller = controller; self.configuration = configuration
      self.resourceResolver = resourceResolver
      self.downloadableFontResolver = downloadableFontResolver; self.onEvent = onEvent; self.onError = onError
    }

    public func makeUIViewController(context: Context) -> RemoteComposePlayerViewController {
      RemoteComposePlayerViewController(data: data, controller: controller ?? context.coordinator.defaultController,
        configuration: configuration, downloadableFontResolver: downloadableFontResolver, resourceResolver: resourceResolver, onEvent: onEvent, onError: onError)
    }
    public func makeCoordinator() -> Coordinator { Coordinator() }
    public func updateUIViewController(_ controller: RemoteComposePlayerViewController, context: Context) {
      controller.update(data: data, controller: self.controller ?? context.coordinator.defaultController,
        configuration: configuration, downloadableFontResolver: downloadableFontResolver, resourceResolver: resourceResolver, onEvent: onEvent, onError: onError)
    }
  }

  extension RemoteComposeNativePlayerViewController: NativeNamedValueTarget {
    func submit(_ value: RemoteComposeNativeNamedValue, for name: String) async
      -> RemoteComposeNativeInputAdmission
    {
      await playerView.submit(value, for: name)
    }

    var inputAvailability: RemoteComposeNativeInputAvailability { playerView.inputAvailability }
  }

  private extension RemoteComposePlayerBackground {
    var nativeValue: RemoteComposeNativePlayerBackground { isOpaque ? .opaque : .transparent }
  }
  private extension RemoteComposePlayerConfiguration {
    /// The native player renders the document's own colours and has no dark palette to switch to,
    /// so only an explicit `.dark` request is refused. `.system` resolves to the document's theme
    /// whatever the host appearance: refusing it under a dark trait collection left visionOS,
    /// which is always dark, with nothing but an error.
    var nativeFallbackSupportsTheme: Bool { theme != .dark }

    /// visionOS presents apps on glass, so a `.system` player draws on a clear background there
    /// rather than an opaque slab. Every other platform keeps the configured background.
    var nativeFallbackBackground: RemoteComposeNativePlayerBackground {
      #if os(visionOS)
        if theme == .system { return .transparent }
      #endif
      return background.nativeValue
    }
  }
  private extension RemoteComposePlayerCompatibility {
    var nativeValue: RemoteComposeNativePlayerCompatibilityPolicy { isLenient ? .compatible : .strict }
  }
  private extension RemoteComposePlayerActionValue {
    init(nativeValue: RemoteComposeNativePlayerActionValue) {
      switch nativeValue {
      case .none: self = .none
      case .float(let value): self = .float(value)
      case .integer(let value): self = .integer(value)
      case .text(let value): self = .text(value)
      case .floatList(let value): self = .floatList(value)
      }
    }
  }
  private extension RemoteComposePlayerEvent {
    init(nativeEvent: RemoteComposeNativePlayerEvent) {
      switch nativeEvent {
      case .action(let id): self = .action(id: id)
      case .actionWithMetadata(let id, let metadata): self = .actionWithMetadata(id: id, metadata: metadata)
      case .namedAction(let name, let value): self = .namedAction(name: name, value: .init(nativeValue: value))
      case .haptic(let type): self = .haptic(type: type)
      case .playSound(let id, _): self = .playSound(id: id)
      case .debug(let message, let value, let flags): self = .debug(message: message, value: value, flags: flags)
      }
    }
  }
#endif
