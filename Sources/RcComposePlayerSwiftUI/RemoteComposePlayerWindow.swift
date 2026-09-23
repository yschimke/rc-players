#if os(macOS)
  import AppKit
  #if canImport(RcPlayerAppleFonts)
    import RcPlayerAppleFonts
  #endif
  #if canImport(RcComposePlayer)
    import RcComposePlayer
  #elseif canImport(RcNativePlayerUIKit)
    import RcNativePlayerUIKit
  #endif

  #if canImport(RcComposePlayer)
  public enum RemoteComposePlayerWindow {
    @MainActor
    public static func open(
      data: Data,
      title: String = "Remote Compose",
      width: Float = 800,
      height: Float = 600,
      configuration: RemoteComposePlayerConfiguration = .init(),
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) {
      do {
        let bytes = try kotlinBytes(from: data)
        let requests = RcDownloadableFontsKt.rcDownloadableFontRequests(bytes: bytes)
        guard !requests.isEmpty else {
          openWindow(
            bytes: bytes, title: title, width: width, height: height,
            configuration: configuration,
            typefaces: RcAppleTypefaceLoaderKt.rcAppleTypefaceLoader(
              additional: RcTypefaceLoaderCompanion.shared.Default),
            onEvent: onEvent, onError: onError)
          return
        }
        guard let downloadableFontResolver else {
          openWindow(
            bytes: bytes, title: title, width: width, height: height,
            configuration: configuration,
            typefaces: RcAppleTypefaceLoaderKt.rcAppleTypefaceLoader(
              additional: RcDownloadableFontsKt.rcDownloadableFontFallback(
                families: requests.map(\.family))),
            onEvent: onEvent, onError: onError)
          return
        }
        Task { @MainActor in
          do {
            var fonts: [RcDownloadedFont] = []
            fonts.reserveCapacity(requests.count)
            for request in requests {
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
            openWindow(
              bytes: bytes, title: title, width: width, height: height,
              configuration: configuration,
              typefaces: RcAppleTypefaceLoaderKt.rcAppleTypefaceLoader(
                additional: RcDownloadableFontsKt.rcDownloadedTypefaceLoader(fonts: fonts)),
              onEvent: onEvent, onError: onError)
          } catch {
            onError(.playback("Downloadable font loading failed: \(error.localizedDescription)"))
          }
        }
      } catch let error as RemoteComposePlayerError {
        onError(error)
      } catch {
        onError(.playback(error.localizedDescription))
      }
    }

    @MainActor
    private static func openWindow(
      bytes: KotlinByteArray,
      title: String,
      width: Float,
      height: Float,
      configuration: RemoteComposePlayerConfiguration,
      typefaces: RcTypefaceLoader,
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void,
      onError: @escaping (RemoteComposePlayerError) -> Void
    ) {
      RcComposeWindowKt.RcComposeWindow(
        bytes: bytes,
        title: title,
        width: width,
        height: height,
        theme: configuration.theme.kotlinValue,
        onEvent: { event in onEvent(swiftEvent(from: event)) },
        typefaces: typefaces,
        onError: { message in onError(.playback(message)) },
        lenient: configuration.compatibility.isLenient
      )
    }
  }
  #else
    /// A macOS window host that uses the native Swift/AppKit player when the Kotlin XCFramework is
    /// not linked into the application.
    public enum RemoteComposePlayerWindow {
      @MainActor
      public static func open(
        data: Data,
        title: String = "Remote Compose",
        width: Float = 800,
        height: Float = 600,
        configuration: RemoteComposePlayerConfiguration = .init(),
        downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
        onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
        onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
      ) {
        guard configuration.nativeFallbackSupportsCurrentAppearance else {
          onError(.playback("The native Swift fallback currently supports light appearance only."))
          return
        }
        Task { @MainActor in
          do {
            try await NativeAppKitWindowController.shared.openNative(
              data: data,
              title: title,
              compatibility: configuration.compatibility.nativeValue,
              width: CGFloat(width),
              height: CGFloat(height),
              opaque: configuration.background.isOpaque,
              downloadableFontResolver: downloadableFontResolver,
              onFontFallback: { onError(.playback($0)) },
              onEvent: { onEvent(RemoteComposePlayerEvent(nativeEvent: $0)) },
              onDiagnostics: { _ in },
              onError: { onError(.playback($0)) })
          } catch is CancellationError {
            // The caller replaced this request before AppKit presented its window.
          } catch let error as RemoteComposePlayerError {
            onError(error)
          } catch {
            onError(.playback(error.localizedDescription))
          }
        }
      }
    }

    private extension RemoteComposePlayerCompatibility {
      var nativeValue: NativeMacCompatibility {
        self == .strict ? .strict : .compatible
      }
    }

    private extension RemoteComposePlayerConfiguration {
      var nativeFallbackSupportsCurrentAppearance: Bool {
        switch theme {
        case .light: true
        case .dark: false
        case .system:
          NSApp.effectiveAppearance.bestMatch(from: [.aqua, .darkAqua]) != .darkAqua
        }
      }
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
        case .actionWithMetadata(let id, let metadata):
          self = .actionWithMetadata(id: id, metadata: metadata)
        case .namedAction(let name, let value):
          self = .namedAction(name: name, value: .init(nativeValue: value))
        case .debug(let message, let value, let flags):
          self = .debug(message: message, value: value, flags: flags)
        }
      }
    }
  #endif
#endif
