#if os(macOS)
  import AppKit
  import RcComposePlayer
  #if canImport(RcPlayerAppleFonts)
    import RcPlayerAppleFonts
  #endif

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
            configuration: configuration, typefaces: RcTypefaceLoaderCompanion.shared.Default,
            onEvent: onEvent, onError: onError)
          return
        }
        guard let downloadableFontResolver else {
          openWindow(
            bytes: bytes, title: title, width: width, height: height,
            configuration: configuration,
            typefaces: RcDownloadableFontsKt.rcDownloadableFontFallback(
              families: requests.map(\.family)),
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
              typefaces: RcDownloadableFontsKt.rcDownloadedTypefaceLoader(fonts: fonts),
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
#endif
