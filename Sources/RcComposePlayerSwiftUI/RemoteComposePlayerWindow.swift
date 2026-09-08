#if os(macOS)
  import AppKit
  import RcComposePlayer

  public enum RemoteComposePlayerWindow {
    @MainActor
    public static func open(
      data: Data,
      title: String = "Remote Compose",
      width: Float = 800,
      height: Float = 600,
      configuration: RemoteComposePlayerConfiguration = .init(),
      onEvent: @escaping (RemoteComposePlayerEvent) -> Void = { _ in },
      onError: @escaping (RemoteComposePlayerError) -> Void = { _ in }
    ) {
      do {
        let bytes = try kotlinBytes(from: data)
        RcComposeWindowKt.RcComposeWindow(
          bytes: bytes,
          title: title,
          width: width,
          height: height,
          theme: configuration.theme.kotlinValue,
          onEvent: { event in onEvent(swiftEvent(from: event)) },
          typefaces: RcTypefaceLoaderCompanion.shared.Default,
          onError: { message in onError(.playback(message)) },
          lenient: configuration.compatibility.isLenient
        )
      } catch let error as RemoteComposePlayerError {
        onError(error)
      } catch {
        onError(.playback(error.localizedDescription))
      }
    }
  }
#endif
