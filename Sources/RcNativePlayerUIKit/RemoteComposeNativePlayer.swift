#if canImport(UIKit)
  import Foundation
  #if canImport(RcNativePlayerCore)
    @_spi(RemoteComposeController) import RcNativePlayerCore
  #endif
  #if canImport(RcPlayerAppleFonts)
    import RcPlayerAppleFonts
  #endif
  import UIKit

  @MainActor
  private final class NativeDisplayLinkTarget: NSObject {
    weak var owner: RemoteComposeNativePlayerView?
    weak var displayLink: CADisplayLink?

    init(owner: RemoteComposeNativePlayerView) {
      self.owner = owner
    }

    @objc func fire() {
      guard let owner else {
        displayLink?.invalidate()
        return
      }
      owner.displayLinkDidFire()
    }
  }

  extension RemoteComposeNativePlayerBackground {
    /// The background a player has when the host does not choose one: `.opaque` on iOS, and
    /// `.transparent` on visionOS, where an opaque system background paints over the window's
    /// glass material.
    public static var platformDefault: RemoteComposeNativePlayerBackground {
      #if os(visionOS)
        return .transparent
      #else
        return .opaque
      #endif
    }
  }

  /// A UIKit-native Remote Compose player proof of concept.
  ///
  /// The wire decoder, retained state, lifecycle, component hierarchy, layout, and drawing are all
  /// implemented in Swift with UIKit, CoreGraphics, and CoreText.
  @MainActor
  public final class RemoteComposeNativePlayerViewController: UIViewController {
    public private(set) var playerView: RemoteComposeNativePlayerView

    public convenience init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .platformDefault,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility = .disabled,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      executionLimits: RemoteComposeNativeExecutionLimits = .default,
      customComponents: RemoteComposeNativeCustomComponentRegistry? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? =
        RemoteComposeNativeURLSessionResourceResolver.shared,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      clock: any RemoteComposeNativePlayerClock = RemoteComposeNativeSystemClock(),
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in },
      onError: @escaping (RemoteComposeNativePlayerError) -> Void = { _ in }
    ) {
      self.init(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
        androidCompatibility: androidCompatibility, resourceLimits: resourceLimits,
        executionLimits: executionLimits, customComponents: customComponents,
        resourceResolver: resourceResolver, downloadableFontResolver: downloadableFontResolver,
        clock: clock, initialNamedValues: [], onEvent: onEvent, onDiagnostics: onDiagnostics,
        onError: onError)
    }

    @_spi(RemoteComposeController)
    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .platformDefault,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility = .disabled,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      executionLimits: RemoteComposeNativeExecutionLimits = .default,
      customComponents: RemoteComposeNativeCustomComponentRegistry? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? =
        RemoteComposeNativeURLSessionResourceResolver.shared,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      clock: any RemoteComposeNativePlayerClock = RemoteComposeNativeSystemClock(),
      initialNamedValues: [(name: String, value: RemoteComposeNativeNamedValue)],
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in },
      onError: @escaping (RemoteComposeNativePlayerError) -> Void = { _ in }
    ) {
      let customComponents = customComponents ?? RemoteComposeNativeCustomComponentRegistry()
      playerView = RemoteComposeNativePlayerView(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
        androidCompatibility: androidCompatibility,
        resourceLimits: resourceLimits, executionLimits: executionLimits,
        customComponents: customComponents,
        resourceResolver: resourceResolver,
        downloadableFontResolver: downloadableFontResolver,
        clock: clock,
        initialNamedValues: initialNamedValues,
        onEvent: onEvent,
        onDiagnostics: onDiagnostics,
        onError: onError)
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

    public func configureExecutionLimits(_ limits: RemoteComposeNativeExecutionLimits) {
      playerView.configureExecutionLimits(limits)
    }

    /// Select the density contract and retry the retained document when it changes.
    public func configureAndroidCompatibility(
      _ compatibility: RemoteComposeNativePlayerAndroidCompatibility
    ) {
      playerView.androidCompatibility = compatibility
    }

    /// Tell a deferred-density document what density this host plays at.
    public func configureHostDensity(_ density: Float, fontScale: Float = 1) {
      playerView.configureHostDensity(density, fontScale: fontScale)
    }

    public func configureDownloadableFonts(
      resolver: (any RemoteComposeDownloadableFontResolving)?
    ) {
      playerView.configureDownloadableFonts(resolver: resolver)
    }

    public func configureCustomComponents(
      _ registry: RemoteComposeNativeCustomComponentRegistry
    ) {
      playerView.configureCustomComponents(registry)
    }

    public func renderFrame(at timeSeconds: TimeInterval) {
      playerView.renderFrame(at: timeSeconds)
    }

    /// Sets a float named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)`` for
    /// what the answer means and what a reopened session starts from.
    @discardableResult
    public func setFloat(_ value: Float, for name: String) async -> Bool {
      await playerView.setFloat(value, for: name)
    }

    /// Sets a string named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)``.
    @discardableResult
    public func setString(_ value: String, for name: String) async -> Bool {
      await playerView.setString(value, for: name)
    }

    /// Sets an ARGB colour named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)``.
    @discardableResult
    public func setColor(_ argb: UInt32, for name: String) async -> Bool {
      await playerView.setColor(argb, for: name)
    }

    /// Sets an integer named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)``.
    @discardableResult
    public func setInteger(_ value: Int, for name: String) async -> Bool {
      await playerView.setInteger(value, for: name)
    }

    /// Sets a long named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)``.
    @discardableResult
    public func setLong(_ value: Int64, for name: String) async -> Bool {
      await playerView.setLong(value, for: name)
    }

    /// Sets a float-array named variable — see ``RemoteComposeNativePlayerView/setFloat(_:for:)``.
    @discardableResult
    public func setFloatArray(_ values: [Float], for name: String) async -> Bool {
      await playerView.setFloatArray(values, for: name)
    }

    /// Restores a named variable to the document's value — see
    /// ``RemoteComposeNativePlayerView/clear(_:)``.
    @discardableResult
    public func clear(_ name: String) async -> Bool {
      await playerView.clear(name)
    }

    /// Whether the player is still opening its document — see
    /// ``RemoteComposeNativePlayerView/isOpeningDocument``.
    public var isOpeningDocument: Bool { playerView.isOpeningDocument }
  }

  /// How many host inputs may wait behind the one in flight. Beyond this an input is deferred
  /// (`set*` returns false, a gesture is dropped) rather than queued without bound.
  private let nativePlayerInputCapacity = 256

  /// What a presentation's pump is waiting on.
  private enum NativePumpActivity {
    case idle
    case frame
    case input
  }

  private struct NativePendingInput {
    let input: NativePlayerEngine.Input
    /// Receives the document's answer; nil for a gesture nobody awaits.
    let reply: ((RemoteComposeNativeInputAdmission) -> Void)?
    /// For a write through `submit`, from the host that seeds reopens (`namedValueSeed`): which
    /// of that host's writes to the name it is (`hostWriteCounts`). Nil for a `set*` call or a
    /// gesture.
    var hostWrite: UInt64?
  }

  private struct NativeFrameRequest {
    let time: TimeInterval
    let wallClock: NativeSwiftWallClock?
  }

  /// A named value a new session starts from, by the name the document declares it under.
  struct NativeStartingValue: Equatable {
    let name: String
    let value: NativeSwiftNamedValue

    /// `values`, then `later`, without an earlier entry for a name `later` sets again. Entries of
    /// one name within `later` are all kept, in order, so the last of them is the value applied.
    /// Linear: one set of the later names, one pass over each list.
    static func merging(_ values: [Self], with later: [Self]) -> [Self] {
      let names = Set(later.map(\.name))
      return values.filter { !names.contains($0.name) } + later
    }
  }

  /// A value the seeding host wrote to a name, or its clear of the name (nil), and which of its
  /// writes to that name it was.
  private struct NativeHostValue: Equatable {
    let value: NativeSwiftNamedValue?
    /// The player's count of the host's writes to the name when this one reached it
    /// (`hostWriteCounts`).
    let write: UInt64
  }

  /// What a session over the same bytes starts from.
  private struct NativeCarriedValues {
    static let none = NativeCarriedValues(values: [], hostValues: [:])
    /// Applied, in order, before its first frame.
    let values: [NativeStartingValue]
    /// The seeding host's write of each name the sessions so far took — through `submit`, or
    /// from a seed — keyed as every per-name record is (`NativeHostNameKey`). A seed value is not
    /// applied again when it is that same write: the session's own value, which the document may
    /// have changed since, is newer. It is applied when the host has written the name since, even
    /// back to an equal value.
    let hostValues: [String: NativeHostValue]

    /// These values keyed by the names `declarations` declares them under. Idempotent: a declared
    /// name resolves to itself. Two host names that resolve to one variable leave no host value
    /// for it — their write counts are not comparable — so the seed's write of it is applied.
    /// O(n log n) in the number of names, once per open.
    func keyed(by declarations: NativeSwiftNamedVariableDeclarations) -> NativeCarriedValues {
      var keyedHostValues: [String: NativeHostValue] = [:]
      var collided: Set<String> = []
      for name in hostValues.keys.sorted() {  // lint-ok: O(n log n) once per open
        let key = NativeHostNameKey.key(name, by: declarations)
        if keyedHostValues[key] != nil || collided.contains(key) {  // lint-ok: O(1) lookups
          keyedHostValues[key] = nil
          collided.insert(key)
        } else {
          keyedHostValues[key] = hostValues[name]
        }
      }
      return NativeCarriedValues(
        values: values.map {
          NativeStartingValue(
            name: NativeHostNameKey.key($0.name, by: declarations), value: $0.value)
        },
        hostValues: keyedHostValues)
    }
  }

  /// The one rule every per-name record of the player uses — host write counts, host values, seed
  /// and cleared names, carried, left-out and failed values: the name the document declares,
  /// once its declarations are known ("shown" and "USER:shown" can be one variable, as the Kotlin
  /// player resolves a host name), otherwise the name as written.
  enum NativeHostNameKey {
    static func key(
      _ name: String, by declarations: NativeSwiftNamedVariableDeclarations?
    ) -> String {
      declarations?.declaredName(for: name) ?? name
    }
  }

  /// A starting value a reopened session was opened without, and why.
  private struct NativeLeftOutValue {
    let value: NativeStartingValue
    let error: any Error
  }

  /// A session's first frame and what was built from it, before its resources are prepared.
  private struct NativeOpenedFirstFrame {
    let engine: NativePlayerEngine
    let frame: NativePlayerEngine.Frame
    let model: NativeDocument
    let diagnostics: RemoteComposeNativePlayerDiagnostics
  }

  /// One open document and the work queued against it.
  ///
  /// Frames and input run one at a time, in arrival order, on a single pump task, so a frame never
  /// lands on top of the state a later input produced and an input always resolves at the time of
  /// the frame on screen. The pump exists only while there is work, and holds no chain of tasks.
  @MainActor
  private final class NativePlayerPresentation {
    let engine: NativePlayerEngine
    let resources: NativeResourceStore
    var inputs = NativeBoundedQueue<NativePendingInput>(capacity: nativePlayerInputCapacity)
    var activity = NativePumpActivity.idle
    /// A frame `renderFrame(at:)` accepted; the pump starts it as soon as it is free.
    var pendingFrame: NativeFrameRequest?
    /// A `renderFrame(at:)` time that arrived while input was outstanding, retried once it drains.
    var deferredFrameTime: TimeInterval?
    /// The task draining this presentation's work, while there is any.
    var pump: Task<Void, Never>?
    /// The starting values it was opened without (each broke a limit). The variable still holds
    /// the document's value, so the next session is offered them again until this session
    /// applies a write of the name or a clear of it.
    var leftOut: [NativeStartingValue]
    /// The seeding host's write of each name this session holds or took: `startedFrom`'s, then
    /// each `submit` write it applied — also one it applied after a reopen superseded it, since
    /// its written values are carried — by the name the document declares.
    var hostValues: [String: NativeHostValue]
    /// Each named write that failed this session — applying it, or the frame it produced, broke a
    /// limit or the policy — and was refused, by the name the document declares, with its value.
    /// While the session stays failed its variable may still hold that value, which is what
    /// failed it, so the next session is not handed it, neither as a written value nor as a
    /// left-out one (the seeding host offers its own write again in the seed, judged afresh). A
    /// later accepted write of the name removes it, and a frame installed again clears them all:
    /// every value the session then holds renders.
    var failedWrites: [String: NativeSwiftNamedValue] = [:]

    init(
      engine: NativePlayerEngine, resources: NativeResourceStore,
      startedFrom: NativeCarriedValues, leftOut: [NativeStartingValue]
    ) {
      self.engine = engine
      self.resources = resources
      self.leftOut = leftOut
      hostValues = startedFrom.hostValues
    }

    var hasOutstandingInput: Bool { activity == .input || !inputs.isEmpty }

    var isBusy: Bool { activity != .idle || !inputs.isEmpty || pendingFrame != nil }

    /// Drop everything pending. Queued input is answered `.deferred`, since the session that would
    /// have applied it is being replaced; whatever the pump is awaiting is discarded by the view's
    /// generation check when it returns.
    func abandon() {
      pump?.cancel()
      pump = nil
      activity = .idle
      pendingFrame = nil
      deferredFrameTime = nil
      for pending in inputs.removeAll() { pending.reply?(.deferred) }
    }
  }

  /// Root UIKit view. It can be embedded without SwiftUI or the supplied view controller.
  @MainActor
  public final class RemoteComposeNativePlayerView: UIView {
    /// What the view is doing with its document. Exactly one holds at a time, so combinations such
    /// as "loading while presenting" or "failed yet rendering frames" cannot be expressed.
    private enum State {
      /// Nothing is open: before the first load, or after a load was refused before it started.
      case idle
      /// Opening the retained bytes: resolving the first frame and the resources.
      case loading(Task<Void, Never>)
      /// A session is open and its latest frame is installed.
      case presenting(NativePlayerPresentation)
      /// The error label is showing. A session that was already open keeps accepting input, and an
      /// input that installs returns the view to `presenting`; frames wait for the next load.
      case failed(NativePlayerPresentation?)
      /// The application went to the background while a document was opening or input was in
      /// flight, or a load arrived while it was there; the bytes are reopened on activation.
      case awaitingForeground
    }

    public var playerBackground: RemoteComposeNativePlayerBackground {
      didSet { applyBackground() }
    }

    public var compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy {
      didSet {
        guard compatibilityPolicy != oldValue, let documentData else { return }
        render(documentData)
      }
    }

    /// The density a deferred-density capture resolves against.
    ///
    /// A `RemoteDensity.Host` document computes its sizes from `ID_DENSITY`/`ID_FONT_SIZE` instead
    /// of folding a capture device's density in, so the player owes it a value.
    ///
    /// Until the host calls `configureHostDensity(_:fontScale:)` the player chooses: 1.0 while
    /// `androidCompatibility == .disabled`, because that is what it resolves dp geometry at, and
    /// the screen's `traitCollection.displayScale` while reproducing Android geometry, following
    /// trait changes such as a move to another display. An explicit value is kept from then on.
    public private(set) var hostDensity: Float = 1
    public private(set) var hostFontScale: Float = 1
    /// Whether the host pinned the density with `configureHostDensity(_:fontScale:)`.
    private var hostDensityIsExplicit = false

    /// Whether dp-typed geometry follows the document's Android density contract.
    ///
    /// Native rendering resolves dp geometry at density 1.0 by default and reports a document that
    /// expected otherwise. Changing this reloads the retained bytes, so a host can inspect the
    /// diagnostic first and then opt in.
    public var androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility {
      didSet {
        guard androidCompatibility != oldValue else { return }
        if !hostDensityIsExplicit { hostDensity = automaticHostDensity }
        guard let documentData else { return }
        render(documentData)
      }
    }

    public var onDiagnostics: (RemoteComposeNativePlayerDiagnostics) -> Void
    public var onEvent: (RemoteComposeNativePlayerEvent) -> Void
    public var soundPlayer: any RemoteComposeNativeSoundPlaying = RemoteComposeNativeAVSoundPlayer()
    /// Replaces the platform haptic behavior when the embedding app owns feedback policy.
    public var hapticHandler: ((Int) -> Void)?
    public var onError: (RemoteComposeNativePlayerError) -> Void
    public private(set) var resourceLimits: RemoteComposeNativeResourceLimits
    public private(set) var executionLimits: RemoteComposeNativeExecutionLimits
    public private(set) var customComponents: RemoteComposeNativeCustomComponentRegistry
    private var customComponentsRevision: UInt
    public private(set) var resourceResolver: (any RemoteComposeNativeResourceResolving)?
    public private(set) var downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?
    private var documentView: NativeDocumentView?
    private var documentData: Data?
    /// The resources behind the frame on screen, whose fonts stay registered until replaced.
    private var installedResources: NativeResourceStore?
    private var state = State.idle
    /// Bumped whenever in-flight work stops being wanted: a (re)load or a move to the background.
    /// Asynchronous work captures it when it starts and applies its result only while it matches.
    private var generation: UInt64 = 0
    /// Whether the window scene showing this view is in the foreground. A view that has not yet
    /// been in a scene counts as active, so it loads eagerly as it always has.
    private var isSceneActive = true
    /// The window scene whose lifecycle notifications drive `isSceneActive`. Weak: a scene outlives
    /// its windows, not the other way round, and a view keeps following its last scene while it is
    /// out of any window.
    private weak var observedScene: UIScene?
    /// A display-link or wake frame that arrived while the view was busy, taken once it frees up.
    private var scheduledFramePending = false
    /// Signals a caller holding a deferred write when to offer it again.
    @_spi(RemoteComposeController)
    public let inputAvailability = RemoteComposeNativeInputAvailability()
    /// The host's named values, by the name the document declares them under, read as a reopen
    /// starts and applied over the values it carries before its first frame is resolved. The
    /// player's first open runs from `init`, before a host can set this, and is not seeded.
    ///
    /// A reopen — for the foreground, a new density, policy, limits, resolver or custom components
    /// — carries every named value a write set in the session it replaces, the host's or the
    /// document's own (read after any input that session was applying), and each value an
    /// earlier reopen left out that no write has replaced since. A seed value is applied over
    /// them unless it is the very write the sessions over these bytes already took for that name
    /// from this host — through `submit`, or an earlier seed — with no write of that name handed
    /// to `submit` since: then the session's own value, which the document (or a `set*` call) may
    /// have changed since, is newer. A host that wrote the name again, even back to the value the
    /// session took, has the newer write. So each named variable shows the newest value written
    /// to it, as on the Kotlin player, which keeps its state instead of reopening. Seed names are
    /// the names the host writes through `submit`. State that is not a named variable — animations,
    /// a scroll position, unnamed variables — starts again from the bytes. A reopen out of a failed
    /// session keeps what that session held except the writes that failed it (`failedWrites`).
    ///
    /// A value the document does not declare, or that does not fit its variable, is skipped
    /// silently, as a declined write is. When the first frame fails with the starting values —
    /// one breaks an execution limit or the compatibility policy — the session opens with every
    /// value its first frame can take and without the others: each one left out is reported
    /// through `onError`, once per session, as a refused write is, and offered again to the next
    /// session until a write replaces it, and the session stays open and takes later writes.
    /// (Written into the session, a bad value would stay there and fail every later frame.) Nil,
    /// or empty, adds nothing; a value written while the session opens goes through `submit`.
    /// `load(_:)` with other bytes clears it and the carried values: the values belong to the
    /// document they were written for.
    ///
    /// An entry with no value is the host's clear of the name, as `submitClear` hands one over.
    /// Unless the sessions over these bytes already took that clear, the reopened session starts
    /// that name from the document's own value, not from the value carried over from the session
    /// it replaces.
    @_spi(RemoteComposeController)
    public var namedValueSeed: (() -> [(name: String, value: RemoteComposeNativeNamedValue?)])?
    /// How many writes the seeding host has handed the player through `submit`, and clears through
    /// `submitClear`, per name, since these bytes were loaded — taken or not, deferred or queued.
    /// A seed entry is the host's newest write of its name (a value, or a clear); the session took
    /// that write only if it took the one with this count. Writes the host makes and replaces
    /// within one main-queue turn — a write and its clear included — never reach `submit` or
    /// `submitClear`, so they count as no change, as the Kotlin player, which diffs the host's
    /// values once per pass, sees them.
    ///
    /// Keyed by `NativeHostNameKey` under `documentDeclarations`: by the name as written until a
    /// session over these bytes has read their declarations, then re-keyed by the declared name.
    private var hostWriteCounts: [String: UInt64] = [:]
    /// The named-variable declarations of `documentData`, once a session over these bytes has read
    /// them; nil before that and after `load(_:)` with other bytes.
    private var documentDeclarations: NativeSwiftNamedVariableDeclarations?
    /// What the next session to open starts from: what the session it replaces held, read once
    /// that session's in-flight input is done, or, while an open is superseded before it
    /// presents, what that open started from.
    private var carriedNamedValues: Task<NativeCarriedValues, Never>?
    /// Host values handed to the player's first open before declarations were read; kept until a
    /// session presents (or other bytes are loaded) so an early reopen does not drop them.
    private var pendingInitialNamedValues: [(name: String, value: RemoteComposeNativeNamedValue)] =
      []
    private let clock: any RemoteComposeNativePlayerClock
    private var animationTimeline = NativeAnimationTimeline()
    private var frameSchedule = NativeFrameSchedule.idle
    private var displayLink: CADisplayLink?
    private lazy var displayLinkTarget = NativeDisplayLinkTarget(owner: self)
    private var delayedWakeTask: Task<Void, Never>?
    private var wakeCountdown = NativeWakeCountdown()
    private var resourceCache: NativeImageCache
    private let errorLabel = UILabel()

    public convenience init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .platformDefault,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility = .disabled,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      executionLimits: RemoteComposeNativeExecutionLimits = .default,
      customComponents: RemoteComposeNativeCustomComponentRegistry? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? =
        RemoteComposeNativeURLSessionResourceResolver.shared,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      clock: any RemoteComposeNativePlayerClock = RemoteComposeNativeSystemClock(),
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in },
      onError: @escaping (RemoteComposeNativePlayerError) -> Void = { _ in }
    ) {
      self.init(
        data: data, background: background, compatibilityPolicy: compatibilityPolicy,
        androidCompatibility: androidCompatibility, resourceLimits: resourceLimits,
        executionLimits: executionLimits, customComponents: customComponents,
        resourceResolver: resourceResolver, downloadableFontResolver: downloadableFontResolver,
        clock: clock, initialNamedValues: [], onEvent: onEvent, onDiagnostics: onDiagnostics,
        onError: onError)
    }

    @_spi(RemoteComposeController)
    public init(
      data: Data,
      background: RemoteComposeNativePlayerBackground = .platformDefault,
      compatibilityPolicy: RemoteComposeNativePlayerCompatibilityPolicy = .compatible,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility = .disabled,
      resourceLimits: RemoteComposeNativeResourceLimits = .default,
      executionLimits: RemoteComposeNativeExecutionLimits = .default,
      customComponents: RemoteComposeNativeCustomComponentRegistry? = nil,
      resourceResolver: (any RemoteComposeNativeResourceResolving)? =
        RemoteComposeNativeURLSessionResourceResolver.shared,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)? = nil,
      clock: any RemoteComposeNativePlayerClock = RemoteComposeNativeSystemClock(),
      initialNamedValues: [(name: String, value: RemoteComposeNativeNamedValue)],
      onEvent: @escaping (RemoteComposeNativePlayerEvent) -> Void = { _ in },
      onDiagnostics: @escaping (RemoteComposeNativePlayerDiagnostics) -> Void = { _ in },
      onError: @escaping (RemoteComposeNativePlayerError) -> Void = { _ in }
    ) {
      playerBackground = background
      self.compatibilityPolicy = compatibilityPolicy
      self.androidCompatibility = androidCompatibility
      self.resourceLimits = resourceLimits
      self.executionLimits = executionLimits
      let customComponents = customComponents ?? RemoteComposeNativeCustomComponentRegistry()
      self.customComponents = customComponents
      customComponentsRevision = customComponents.revision
      self.resourceResolver = resourceResolver
      self.downloadableFontResolver = downloadableFontResolver
      self.clock = clock
      resourceCache = NativeImageCache(
        countLimit: resourceLimits.maximumResourceCount,
        totalCostLimit: resourceLimits.maximumDecodedImageBytes)
      self.onEvent = onEvent
      self.onDiagnostics = onDiagnostics
      self.onError = onError
      super.init(frame: .zero)
      hostDensity = automaticHostDensity
      animationTimeline.reset(
        at: clock.now(),
        active: isSceneActive && window != nil && !UIAccessibility.isReduceMotionEnabled)
      isAccessibilityElement = false
      clipsToBounds = true
      configureErrorLabel()
      applyBackground()
      NotificationCenter.default.addObserver(
        self,
        selector: #selector(reduceMotionStatusDidChange),
        name: UIAccessibility.reduceMotionStatusDidChangeNotification,
        object: nil)
      pendingInitialNamedValues = initialNamedValues
      documentData = data
      render(data, carryingNamedValues: false)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    deinit {
      // A pump holds the view strongly, so none can be running here; only an open (which holds it
      // weakly, and may be waiting on a resolver) and a wake timer can outlive it.
      if case .loading(let task) = state { task.cancel() }
      delayedWakeTask?.cancel()
      inputAvailability.close()
      NotificationCenter.default.removeObserver(self)
    }

    public func load(_ data: Data) {
      let isSameDocument = data == documentData
      if isSameDocument, !isShowingError { return }
      if !isSameDocument {
        // The seed and the carried values belong to the document they were written for; another
        // one may declare the same names for something else.
        namedValueSeed = nil
        pendingInitialNamedValues = []
        hostWriteCounts = [:]
        documentDeclarations = nil
        soundPlayer.stopAll()
        animationTimeline.reset(
          at: clock.now(),
          active: isSceneActive && window != nil && !UIAccessibility.isReduceMotionEnabled)
      }
      documentData = data
      render(data, carryingNamedValues: isSameDocument)
    }

    /// Apply host-owned resource policy and retry the current document when it changes.
    public func configureResources(
      limits: RemoteComposeNativeResourceLimits,
      resolver: (any RemoteComposeNativeResourceResolving)?
    ) {
      let resolverChanged: Bool
      switch (resourceResolver, resolver) {
      case (nil, nil): resolverChanged = false
      case (let current?, let next?): resolverChanged = current !== next
      default: resolverChanged = true
      }
      let limitsChanged = limits != resourceLimits
      guard limitsChanged || resolverChanged else { return }
      resourceLimits = limits
      resourceResolver = resolver
      resourceCache = NativeImageCache(
        countLimit: limits.maximumResourceCount,
        totalCostLimit: limits.maximumDecodedImageBytes)
      if let documentData { render(documentData) }
    }

    /// Apply native per-frame work limits and retry the current document when they change.
    public func configureExecutionLimits(_ limits: RemoteComposeNativeExecutionLimits) {
      guard limits != executionLimits else { return }
      executionLimits = limits
      if let documentData { render(documentData) }
    }

    /// Tell a deferred-density document what density this host plays at.
    ///
    /// Reloads the retained bytes, because the first frame is resolved as the session opens and a
    /// document that reads `ID_DENSITY` would otherwise keep the geometry it built from the old
    /// value. Non-finite and non-positive values are ignored by the core rather than stored.
    public func configureHostDensity(_ density: Float, fontScale: Float = 1) {
      // Only a valid density pins the value; an ignored one leaves automatic density following
      // the display scale.
      if density.isFinite && density > 0 { hostDensityIsExplicit = true }
      let density = density.isFinite && density > 0 ? density : hostDensity
      let fontScale = fontScale.isFinite && fontScale > 0 ? fontScale : hostFontScale
      guard density != hostDensity || fontScale != hostFontScale else { return }
      hostDensity = density
      hostFontScale = fontScale
      if let documentData { render(documentData) }
    }

    /// Replace the opt-in network font source and retry the retained document when it changes.
    public func configureDownloadableFonts(
      resolver: (any RemoteComposeDownloadableFontResolving)?
    ) {
      let changed: Bool
      switch (downloadableFontResolver, resolver) {
      case (nil, nil): changed = false
      case (let current?, let next?): changed = current !== next
      default: changed = true
      }
      guard changed else { return }
      downloadableFontResolver = resolver
      if let documentData { render(documentData) }
    }

    /// Replace the host registry and retry the retained document against its declared names.
    public func configureCustomComponents(
      _ registry: RemoteComposeNativeCustomComponentRegistry
    ) {
      guard customComponents !== registry || customComponentsRevision != registry.revision else {
        return
      }
      customComponents = registry
      customComponentsRevision = registry.revision
      if let documentData { render(documentData) }
    }

    // MARK: - State

    private var isShowingError: Bool {
      if case .failed = state { return true }
      return false
    }

    /// Whether a document is on its way to an open session: it is being opened, or will be once the
    /// application returns to the foreground. While this holds, a named-variable write has no
    /// session to reach and is answered `.deferred` (`set*` returns `false`): it may be accepted
    /// once the session opens. Once this is false, the session is open (and accepts input) or the
    /// document failed, and a write with no session is `.refused`.
    public var isOpeningDocument: Bool {
      switch state {
      case .loading, .awaitingForeground: return true
      case .idle, .presenting, .failed: return false
      }
    }

    /// Whether the open session is applying an input right now. A test hook: the input-deferral
    /// tests use it to move the scene to the background while an input is in flight.
    var isApplyingInput: Bool { openPresentation?.activity == .input }

    /// Whether the open session has input in flight or queued — what makes a move to the background
    /// reopen the bytes on activation. A test hook, like ``isApplyingInput``.
    var hasOutstandingInput: Bool { openPresentation?.hasOutstandingInput ?? false }

    /// The named values the open session holds from a write (see `writtenNamedValues` on the
    /// core), empty with no open session. A test hook, like ``isApplyingInput``: it shows what a
    /// seeded first open started from without a document that draws each type.
    func writtenNamedValues() async -> [(name: String, value: NativeSwiftNamedValue)] {
      guard let engine = openPresentation?.engine else { return [] }
      return await engine.writtenNamedValues()
    }

    /// Called once as each session opens, in the same main-actor turn that installed its first
    /// frame (or showed the error that frame raised), after its left-out values are reported and
    /// before the writes waiting for it are released, so no later frame or input has landed yet.
    /// A test hook: it lets a test read the first frame a session shows rather than a later one.
    var onSessionOpened: (() -> Void)?

    /// Called each time an open starts building a first frame's model — one call per attempt,
    /// whether or not the model passes the limits — on the thread building it. A test hook: it
    /// counts the attempts an open makes and checks they stay off the main thread.
    var onFirstFrameModelBuilt: (@Sendable () -> Void)?

    /// Called with each input the open session applied. A test hook: it counts the writes a
    /// session took.
    var onInputApplied: ((NativePlayerEngine.Input) -> Void)?

    /// The open session that accepts input, whether or not an error is showing.
    private var openPresentation: NativePlayerPresentation? {
      switch state {
      case .presenting(let presentation): return presentation
      case .failed(let presentation): return presentation
      case .idle, .loading, .awaitingForeground: return nil
      }
    }

    /// Whether a scheduled frame has to wait: a document is opening, or its pump has work. Tests
    /// read it to know the player has settled.
    var isBusy: Bool {
      switch state {
      case .loading: return true
      case .presenting(let presentation): return presentation.isBusy
      case .failed(let presentation): return presentation?.isBusy ?? false
      case .idle, .awaitingForeground: return false
      }
    }

    /// Supersede all in-flight work: cancel an open, and drop the open session's queued frames and
    /// input. Anything already awaiting the engine is discarded when it returns.
    private func abandonWork() {
      generation &+= 1
      switch state {
      case .loading(let task): task.cancel()
      case .presenting(let presentation): presentation.abandon()
      case .failed(let presentation): presentation?.abandon()
      case .idle, .awaitingForeground: break
      }
    }

    // MARK: - Loading

    /// Keep what the open session, which is about to be replaced, holds for the next one: its
    /// written named values, read once the input it is applying is done so a value it was writing
    /// is not lost, and the left-out values no write has replaced. A failed session hands on the
    /// same, less the values of the writes that failed it (`failedWrites`): the document's own
    /// values and the host's accepted ones survive one refused write, as on the Kotlin player. A
    /// value that still breaks a limit is left out of the next session's first frame and
    /// reported, as any starting value is.
    private func carryNamedValues() {
      let presentation: NativePlayerPresentation
      let failed: Bool
      switch state {
      case .presenting(let open):
        presentation = open
        failed = false
      case .failed(let open?):
        presentation = open
        failed = true
      case .idle, .loading, .failed(nil), .awaitingForeground:
        return
      }
      let pump = presentation.pump
      carriedNamedValues = Task {
        await pump?.value
        let failedWrites = failed ? presentation.failedWrites : [:]
        let written = await presentation.engine.writtenNamedValues().map {
          NativeStartingValue(name: $0.name, value: $0.value)
        }
        // A write of a left-out name replaces it, even one that failed; then neither a written nor
        // a left-out value that failed the session is handed on.
        return NativeCarriedValues(
          values: NativeStartingValue.merging(presentation.leftOut, with: written).filter {
            failedWrites[$0.name] != $0.value
          },
          hostValues: presentation.hostValues)
      }
    }

    /// Records the declarations of `documentData` the first time a session reads them, and re-keys
    /// the host's write counts by them (`NativeHostNameKey`). Two names written as different
    /// spellings of one variable add up, so the count is past every write either one recorded and
    /// the seed's write of that variable is applied, never taken for one already held.
    private func adoptDeclarations(_ declarations: NativeSwiftNamedVariableDeclarations) {
      guard documentDeclarations == nil else { return }
      documentDeclarations = declarations
      var keyed: [String: UInt64] = [:]
      for (name, count) in hostWriteCounts {  // lint-ok: O(n) once per document
        keyed[NativeHostNameKey.key(name, by: declarations), default: 0] &+= count
      }
      hostWriteCounts = keyed
    }

    private func render(_ data: Data, carryingNamedValues: Bool = true) {
      if carryingNamedValues { carryNamedValues() } else { carriedNamedValues = nil }
      abandonWork()
      guard isSceneActive else {
        state = .awaitingForeground
        return
      }
      scheduledFramePending = false
      stopFrameDriver()
      state = .idle
      do {
        try NativeFrameBudget.validate(executionLimits)
        guard data.count <= executionLimits.maximumDocumentBytes else {
          throw RemoteComposeNativeLimitError.documentTooLarge(
            actual: data.count, maximum: executionLimits.maximumDocumentBytes)
        }
      } catch {
        show(error: error, retaining: nil)
        return
      }
      let generation = self.generation
      let executionLimits = executionLimits
      let compatibilityPolicy = compatibilityPolicy
      let androidCompatibility = androidCompatibility
      let hostDensity = hostDensity
      let hostFontScale = hostFontScale
      let availableCustomComponents = customComponents.names
      let resourceLimits = resourceLimits
      let resourceResolver = resourceResolver
      let downloadableFontResolver = downloadableFontResolver
      let resourceCache = resourceCache
      let wallClock = clock.wallClock()
      let carried = carriedNamedValues
      let initialNamedValues = pendingInitialNamedValues
      let onFirstFrameModelBuilt = onFirstFrameModelBuilt
      let hostWriteCounts = hostWriteCounts
      // Every name below is keyed by one rule (`NativeHostNameKey`): by the declarations a session
      // over these bytes read, when one has, and re-keyed by the prepared document's below.
      let declarations = documentDeclarations
      let seed = (namedValueSeed?() ?? []).map { entry in
        let name = NativeHostNameKey.key(entry.name, by: declarations)
        let write = NativeHostValue(
          value: entry.value?.coreValue, write: hostWriteCounts[name] ?? 0)
        return (name: name, write: write)
      }
      // A reopen that supersedes this open before it presents starts from the same values.
      let startingValues = Task {
        let carriedAsWritten = await carried?.value ?? .none
        let carried = declarations.map { carriedAsWritten.keyed(by: $0) } ?? carriedAsWritten
        var seeded: [NativeStartingValue] = []
        var clearedNames: Set<String> = []
        var hostValues = carried.hostValues
        for (name, write) in seed where carried.hostValues[name] != write {
          if let value = write.value {
            seeded.append(NativeStartingValue(name: name, value: value))
          } else {
            clearedNames.insert(name)
          }
          hostValues[name] = write
        }
        return NativeCarriedValues(
          values: NativeStartingValue.merging(
            carried.values.filter { !clearedNames.contains($0.name) },  // lint-ok: Set, O(n)
            with: seeded),
          hostValues: hostValues)
      }
      carriedNamedValues = startingValues
      // Reduce Motion stops the frames a visibility animation needs, so it lands at once instead.
      let visibilityAnimations = !UIAccessibility.isReduceMotionEnabled
      let task = Task { [weak self] in
        do {
          let startingValues = await startingValues.value
          try Task.checkCancellation()
          let prepared = try await NativePlayerEngine.prepare(
            data: data, maximumDocumentBytes: executionLimits.maximumDocumentBytes)
          if let self, generation == self.generation {
            self.adoptDeclarations(prepared.namedVariableDeclarations)
          }
          // Keyed as written until a session had read these declarations: keyed by them now.
          var startedFrom = startingValues.keyed(by: prepared.namedVariableDeclarations)
          if !initialNamedValues.isEmpty {
            let declarations = prepared.namedVariableDeclarations
            var resolvedByName: [String: NativeSwiftNamedValue] = [:]
            for (name, value) in initialNamedValues {
              let coreValue = value.coreValue
              guard let declared = declarations.declaredName(for: name),
                declarations.accepts(coreValue, for: declared)
              else { continue }
              resolvedByName[declared] = coreValue
            }
            var initialStarting: [NativeStartingValue] = []
            var hostValues = startedFrom.hostValues
            for name in resolvedByName.keys.sorted() {
              // The host cleared it in a reopen's seed before any session presented: the
              // document's own value, not the one the first open was handed.
              if let host = hostValues[name], host.value == nil { continue }
              if let value = resolvedByName[name] {
                initialStarting.append(NativeStartingValue(name: name, value: value))
                if hostValues[name] == nil {
                  hostValues[name] = NativeHostValue(value: value, write: 0)
                }
              }
            }
            startedFrom = NativeCarriedValues(
              values: NativeStartingValue.merging(initialStarting, with: startedFrom.values),
              hostValues: hostValues)
          }
          let namedValues = startedFrom.values
          // The local functions below are nonisolated, so they run on the global executor, off the
          // main actor: an open builds a first-frame model, and judges it, once per attempt, and
          // the fallback below makes up to `2 + 2 log2 n` attempts
          // (NativeNamedValueCarryTests.theFallbackOpenIsBoundedAndOffTheMainThread pins both).
          func openFirstFrame(
            seededWith namedValues: [NativeStartingValue]
          ) async throws -> NativeOpenedFirstFrame {
            let (engine, frame) = try await NativePlayerEngine.open(
              prepared: prepared, hostDensity: hostDensity, hostFontScale: hostFontScale,
              wallClock: wallClock, visibilityAnimations: visibilityAnimations,
              namedValues: namedValues.map { (name: $0.name, value: $0.value) })
            try Task.checkCancellation()
            onFirstFrameModelBuilt?()
            let model = try NativeDocument(
              frame: frame, timeSeconds: 0, limits: executionLimits,
              androidCompatibility: androidCompatibility)
            return NativeOpenedFirstFrame(
              engine: engine, frame: frame, model: model,
              diagnostics: model.diagnostics(
                availableCustomComponents: availableCustomComponents))
          }
          /// Throws unless the player will present `opened`: the policy renders it, and its work
          /// and its events fit the limits, as the player checks every frame.
          func checkRendering(_ opened: NativeOpenedFirstFrame) throws {
            guard
              RemoteComposeNativeCompatibilityDecision.shouldRender(
                policy: compatibilityPolicy, diagnostics: opened.diagnostics)
            else { throw RemoteComposeNativePlayerError.incompatible(opened.diagnostics) }
            try Self.validateExecution(
              opened.model, events: opened.frame.effects, limits: executionLimits)
          }
          /// Why `opened` will not be presented, or nil when it will.
          func renderingError(_ opened: NativeOpenedFirstFrame) -> (any Error)? {
            do {
              try checkRendering(opened)
              return nil
            } catch {
              return error
            }
          }
          /// The opened session, the values it was opened without that are reported, with their
          /// errors, those it was opened without that are not, and why its first frame fails, if
          /// it does — judged once, here, rather than again on the main actor.
          func openSeededFirstFrame() async throws -> (
            NativeOpenedFirstFrame, [NativeLeftOutValue], [NativeStartingValue], (any Error)?
          ) {
            func openRendering(
              _ namedValues: [NativeStartingValue]
            ) async throws -> NativeOpenedFirstFrame {
              let opened = try await openFirstFrame(seededWith: namedValues)
              try checkRendering(opened)
              return opened
            }
            guard !namedValues.isEmpty else {
              let opened = try await openFirstFrame(seededWith: [])
              return (opened, [], [], renderingError(opened))
            }
            let firstError: any Error
            var seededOpened: NativeOpenedFirstFrame?
            do {
              let seeded = try await openFirstFrame(seededWith: namedValues)
              do {
                try checkRendering(seeded)
                return (seeded, [], [], nil)
              } catch {
                seededOpened = seeded
                firstError = error
              }
            } catch is CancellationError {
              throw CancellationError()
            } catch {
              firstError = error
            }
            // A carried or seeded value may be what failed — one that breaks an execution limit
            // or the compatibility policy.
            try Task.checkCancellation()
            var opened: NativeOpenedFirstFrame
            do {
              opened = try await openFirstFrame(seededWith: [])
            } catch is CancellationError {
              throw CancellationError()
            } catch {
              guard let seededOpened else { throw error }
              return (seededOpened, [], [], firstError)
            }
            if let unseededError = renderingError(opened) {
              // The document fails without them too, so they are not what failed it: the session
              // keeps them, fails as the document does, and shows them once input repairs it.
              // Without a session that holds them, it was opened without each one, and the next
              // session is offered them again.
              if let seededOpened { return (seededOpened, [], [], firstError) }
              return (opened, [], namedValues, unseededError)
            }
            // Otherwise keep every value the first frame can take, and leave out, and report as a
            // refused write is, each one that fails it, rather than writing it into the session: a
            // session keeps every value it applied, so it would fail every later frame and refuse
            // every later write. A group of values that opens is kept whole, and one that fails is
            // halved, so one bad value among `n` costs about `2 log2 n` opens rather than `n`.
            // Only this failure path pays the extra opens.
            var kept: [NativeStartingValue] = []
            var refusals: [NativeLeftOutValue] = []
            func keep(_ group: ArraySlice<NativeStartingValue>, failing knownError: (any Error)?)
              async throws
            {
              var failure = knownError
              if failure == nil {
                do {
                  opened = try await openRendering(kept + group)
                  kept += group
                  return
                } catch is CancellationError {
                  throw CancellationError()
                } catch {
                  try Task.checkCancellation()
                  failure = error
                }
              }
              guard group.count > 1 else {
                if let value = group.first, let failure {
                  refusals.append(NativeLeftOutValue(value: value, error: failure))
                }
                return
              }
              let middle = group.startIndex + group.count / 2
              try await keep(group[..<middle], failing: nil)
              try await keep(group[middle...], failing: nil)
            }
            try await keep(namedValues[...], failing: firstError)
            // `opened` is the last attempt that passed `checkRendering`.
            return (opened, refusals, [], nil)
          }
          let (opened, refusals, unreported, firstFrameError) = try await openSeededFirstFrame()
          let model = opened.model
          guard generation == self?.generation else { return }
          self?.onDiagnostics(opened.diagnostics)
          // A document the policy does not render has no session; one whose first frame breaks a
          // limit is opened and shows the error, and keeps taking input.
          if let error = firstFrameError as? RemoteComposeNativePlayerError,
            case .incompatible = error
          {
            throw error
          }
          try Task.checkCancellation()
          let resources = try await Self.prepareResources(
            for: model, limits: resourceLimits, resolver: resourceResolver,
            downloadableFontResolver: downloadableFontResolver,
            cache: resourceCache)
          try Task.checkCancellation()
          guard let self, generation == self.generation else { return }
          self.presentOpened(
            model, engine: opened.engine, resources: resources, effects: opened.frame.effects,
            startedFrom: startedFrom, reporting: refusals, alsoLeftOut: unreported,
            failing: firstFrameError)
        } catch {
          guard !Task.isCancelled, let self, generation == self.generation else { return }
          self.show(error: error, retaining: nil)
        }
      }
      state = .loading(task)
    }

    /// Install the first frame of a freshly opened session, then report `refusals` — starting
    /// values it was opened without because each broke a limit — as refused writes are reported.
    /// `alsoLeftOut` are starting values it was opened without that are not reported.
    /// `firstFrameError` is why the first frame breaks the limits, as the open judged it.
    private func presentOpened(
      _ model: NativeDocument, engine: NativePlayerEngine, resources: NativeResourceStore,
      effects: [RemoteComposeNativePlayerEvent], startedFrom: NativeCarriedValues,
      reporting refusals: [NativeLeftOutValue], alsoLeftOut: [NativeStartingValue],
      failing firstFrameError: (any Error)?
    ) {
      pendingInitialNamedValues = []
      let presentation = NativePlayerPresentation(
        engine: engine, resources: resources, startedFrom: startedFrom,
        leftOut: refusals.map(\.value) + alsoLeftOut)
      defer {
        for refusal in refusals { report(refusal.error) }
        announceOpened()
      }
      do {
        if let firstFrameError { throw firstFrameError }
        try install(model, for: presentation)
        dispatch(effects, generation: generation)
      } catch {
        // The session is open even though its first frame could not be installed, so it keeps
        // accepting input, exactly as after any later failure.
        show(error: error, retaining: presentation)
        return
      }
      if scheduledFramePending { requestScheduledFrame() }
    }

    /// A session has opened: release the writes that waited for it.
    private func announceOpened() {
      onSessionOpened?()
      inputAvailability.signal()
    }

    private static func prepareResources(
      for model: NativeDocument,
      limits: RemoteComposeNativeResourceLimits,
      resolver: (any RemoteComposeNativeResourceResolving)?,
      downloadableFontResolver: (any RemoteComposeDownloadableFontResolving)?,
      cache: NativeImageCache
    ) async throws -> NativeResourceStore {
      let resources = try NativeResourceStore(
        resources: model.images,
        fonts: model.fonts,
        limits: limits,
        cache: cache)
      if !resources.unresolvedImages.isEmpty {
        guard let resolver else {
          throw RemoteComposeNativeResourceError.unresolvedReference(
            id: resources.unresolvedImages[0].id)
        }
        for request in resources.unresolvedImages {
          let resolved = try await resolver.resolve(request)
          try Task.checkCancellation()
          try resources.insertResolved(data: resolved, for: request)
        }
      }
      if !model.downloadableFonts.isEmpty {
        if let downloadableFontResolver {
          for request in model.downloadableFonts {
            let font = try await downloadableFontResolver.resolve(
              RemoteComposeDownloadableFontRequest(family: request.family))
            guard font.family.caseInsensitiveCompare(request.family) == .orderedSame else {
              throw RemoteComposeDownloadableFontError.familyMismatch(
                expected: request.family, actual: font.family)
            }
            try Task.checkCancellation()
            try resources.insertDownloadedFont(data: font.data, id: request.id)
          }
        }
      }
      return resources
    }

    // MARK: - Frames and input

    /// Resolve another immutable frame from the retained runtime without decoding the document.
    ///
    /// Ignored while a frame is already on its way; deferred until queued input has been applied.
    public func renderFrame(at timeSeconds: TimeInterval) {
      guard isSceneActive, case .presenting(let presentation) = state else { return }
      guard presentation.activity != .frame, presentation.pendingFrame == nil else { return }
      guard !presentation.hasOutstandingInput else {
        presentation.deferredFrameTime = timeSeconds
        return
      }
      let frameTime = animationTimeline.advance(to: timeSeconds, at: clock.now())
      presentation.pendingFrame = NativeFrameRequest(
        time: frameTime, wallClock: clock.wallClock())
      startPump(for: presentation)
    }

    /// Sets a float named variable in the open session and answers whether it was applied.
    ///
    /// `true` means the document applied the value to the open session. `false` means the open
    /// session does not show it: the name is undeclared or of another type, the document refused
    /// it or failed, the document is still opening, the scene is in the background, or the input
    /// queue is full — or the session that applied it was superseded by a reopen, whose new
    /// session starts from the values the old one held and so may already show it. Nothing is
    /// queued for later; write it again when the player can take it (writing a value the session
    /// holds again changes nothing).
    ///
    /// A value lives in the session. The player reopens its document when it returns from the
    /// background with input outstanding, and when its density, policy, limits, resolvers or
    /// custom components change; the new session starts from the named values the old one held
    /// (see `namedValueSeed`), less any that now breaks a limit, which is reported and offered to
    /// the next session again. Other bytes start again from their own values.
    @discardableResult
    public func setFloat(_ value: Float, for name: String) async -> Bool {
      await submit(.setNamed(name, value: .float(value)))
    }

    /// Sets a string named variable; answers as ``setFloat(_:for:)`` does.
    @discardableResult
    public func setString(_ value: String, for name: String) async -> Bool {
      await submit(.setNamed(name, value: .text(value)))
    }

    /// Sets an ARGB colour named variable; answers as ``setFloat(_:for:)`` does.
    @discardableResult
    public func setColor(_ argb: UInt32, for name: String) async -> Bool {
      await submit(.setNamed(name, value: .color(argb)))
    }

    /// Sets an integer named variable — a boolean is 1 or 0 — and answers as
    /// ``setFloat(_:for:)`` does.
    @discardableResult
    public func setInteger(_ value: Int, for name: String) async -> Bool {
      await submit(.setNamed(name, value: .integer(value)))
    }

    /// Sets a long named variable — an instant in epoch milliseconds — and answers as
    /// ``setFloat(_:for:)`` does.
    @discardableResult
    public func setLong(_ value: Int64, for name: String) async -> Bool {
      await submit(.setNamed(name, value: .long(value)))
    }

    /// Replaces the list a float-array named variable holds, and answers as ``setFloat(_:for:)``
    /// does. Every value must be finite, and there may be no more of them than a float list holds
    /// (AndroidX `DataListFloat.MAX_FLOAT_ARRAY`, 2000).
    @discardableResult
    public func setFloatArray(_ values: [Float], for name: String) async -> Bool {
      await submit(.setNamed(name, value: .floatArray(values)))
    }

    /// Restores a named variable to the value the document recorded, discarding the value a host
    /// set — as the Kotlin player's `clearNamedValue` does — and answers as ``setFloat(_:for:)``
    /// does: `false` for a name the document does not declare as a value a host can write. Like the
    /// Kotlin player's, the clear restores the recorded value whoever changed it last — a value the
    /// document's own action wrote is restored too — and clearing twice changes nothing more.
    @discardableResult
    public func clear(_ name: String) async -> Bool {
      await submit(.clearNamed(name))
    }

    /// Writes a named variable and answers how the document took it. Unlike the `set*` methods,
    /// it tells a write the player cannot take yet (`.deferred`) from one the document refuses;
    /// offer a deferred write again once ``inputAvailability`` signals.
    @_spi(RemoteComposeController)
    public func submit(
      _ value: RemoteComposeNativeNamedValue, for name: String
    ) async -> RemoteComposeNativeInputAdmission {
      let key = NativeHostNameKey.key(name, by: documentDeclarations)
      let write = (hostWriteCounts[key] ?? 0) &+ 1
      hostWriteCounts[key] = write
      return await admit(.setNamed(name, value: value), hostWrite: write)
    }

    /// Clears a named variable and answers how the document took it, as ``submit(_:for:)`` does
    /// for a write: the clear counts as that host's newest write of the name.
    @_spi(RemoteComposeController)
    public func submitClear(for name: String) async -> RemoteComposeNativeInputAdmission {
      let key = NativeHostNameKey.key(name, by: documentDeclarations)
      let write = (hostWriteCounts[key] ?? 0) &+ 1
      hostWriteCounts[key] = write
      return await admit(.clearNamed(name), hostWrite: write)
    }

    private func submit(_ input: NativePlayerEngine.Input) async -> Bool {
      await admit(input) == .accepted
    }

    private func admit(
      _ input: NativePlayerEngine.Input, hostWrite: UInt64? = nil
    ) async -> RemoteComposeNativeInputAdmission {
      await withCheckedContinuation {
        (continuation: CheckedContinuation<RemoteComposeNativeInputAdmission, Never>) in
        enqueue(input, hostWrite: hostWrite) { admission in
          continuation.resume(returning: admission)
        }
      }
    }

    /// Queue `input` behind the open session's pending work. `reply` is called exactly once:
    /// `.deferred` while the document is opening, the scene is in the background or the queue is
    /// full, and `.refused` when no document will open.
    private func enqueue(
      _ input: NativePlayerEngine.Input, hostWrite: UInt64? = nil,
      reply: ((RemoteComposeNativeInputAdmission) -> Void)?
    ) {
      guard let presentation = openPresentation else {
        reply?(isOpeningDocument ? .deferred : .refused)
        return
      }
      guard isSceneActive,
        presentation.inputs.append(
          NativePendingInput(input: input, reply: reply, hostWrite: hostWrite))
      else {
        reply?(.deferred)
        return
      }
      startPump(for: presentation)
    }

    private func startPump(for presentation: NativePlayerPresentation) {
      guard presentation.pump == nil else { return }
      let generation = self.generation
      presentation.pump = Task { [weak self] in
        await self?.drain(presentation, generation: generation)
      }
    }

    /// Run a presentation's work to completion, one item at a time, in arrival order: an accepted
    /// frame (only ever accepted while no input is outstanding, so it predates any queued input),
    /// then queued input, then the frame that was deferred behind that input. Stops as soon as the
    /// generation moves on, leaving the presentation to whoever superseded it.
    private func drain(_ presentation: NativePlayerPresentation, generation: UInt64) async {
      while generation == self.generation {
        if let request = presentation.pendingFrame {
          presentation.pendingFrame = nil
          presentation.activity = .frame
          await presentFrame(request, on: presentation, generation: generation)
          guard generation == self.generation else { return }
          presentation.activity = .idle
          if scheduledFramePending { requestScheduledFrame() }
        } else if let pending = presentation.inputs.popFirst() {
          presentation.activity = .input
          // The queue has room for one more input: wake one write waiting for it, not every one,
          // or `n` waiting writes would cost about `n²/2` offers. Once it is empty, wake them all,
          // so a woken write that gave up instead cannot leave the others waiting.
          if presentation.inputs.isEmpty {
            inputAvailability.signal()
          } else {
            inputAvailability.signalOne()
          }
          await applyInput(pending, to: presentation, generation: generation)
        } else if presentation.activity == .input {
          // The last input is done: take the frame that waited for it.
          presentation.activity = .idle
          if let deferredFrameTime = presentation.deferredFrameTime {
            presentation.deferredFrameTime = nil
            renderFrame(at: deferredFrameTime)
          } else if scheduledFramePending {
            requestScheduledFrame()
          }
        } else {
          presentation.pump = nil
          return
        }
      }
    }

    private func presentFrame(
      _ request: NativeFrameRequest, on presentation: NativePlayerPresentation,
      generation: UInt64
    ) async {
      do {
        let frame = try await presentation.engine.frame(
          at: request.time, wallClock: request.wallClock,
          visibilityAnimations: !UIAccessibility.isReduceMotionEnabled)
        guard generation == self.generation else { return }
        let model = try NativeDocument(
          frame: frame, timeSeconds: request.time, limits: executionLimits,
          androidCompatibility: androidCompatibility)
        try validate(model)
        try validateExecution(model, events: frame.effects)
        guard generation == self.generation else { return }
        var installError: (any Error)?
        do {
          try install(model, for: presentation)
        } catch {
          installError = error
        }
        // Input from here on resolves at this frame's time, even if installing it failed.
        await presentation.engine.present(frameTime: request.time)
        if let installError { throw installError }
        dispatch(frame.effects, generation: generation)
      } catch {
        guard generation == self.generation else { return }
        show(error: error, retaining: presentation)
      }
    }

    private func applyInput(
      _ pending: NativePendingInput, to presentation: NativePlayerPresentation,
      generation: UInt64
    ) async {
      let result: Result<NativePlayerEngine.Update, any Error>
      do {
        result = .success(try await presentation.engine.apply(pending.input))
      } catch {
        result = .failure(error)
      }
      guard generation == self.generation else {
        // Superseded by a reload or the background: the session that applied it is discarded and
        // the bytes are reopened, so the frame it produced is never shown and the writer is told
        // `.deferred`. Nothing from a superseded session reaches the screen or the host's
        // callbacks; a named value it set is carried into a session reopened over the same bytes,
        // which then holds the host's value too.
        if case .success(let update) = result, update.accepted {
          note(.accepted, of: pending, on: presentation)
        }
        pending.reply?(.deferred)
        return
      }
      switch result {
      case .success(let update):
        onInputApplied?(pending.input)
        // Evaluated first: an optional-chained call skips its argument when a gesture has no reply.
        let outcome = presentInput(
          update, of: pending.input, on: presentation, generation: generation)
        note(outcome, of: pending, on: presentation)
        pending.reply?(outcome == .accepted ? .accepted : .refused)
      case .failure(let error):
        if !(error is CancellationError) {
          show(error: error, retaining: presentation)
          note(.failed, of: pending, on: presentation)
        }
        pending.reply?(.refused)
      }
    }

    /// How the open session took one input.
    private enum NativeInputOutcome {
      /// The document applied it.
      case accepted
      /// The document declined it — an undeclared name, another type, a gesture nothing
      /// handles — or a reopen superseded the session before its frame was judged.
      case declined
      /// Applying it, or the frame it produced, failed the session.
      case failed
    }

    /// Record what `presentation`'s session made of the named write `pending`:
    /// - a seeding host's write it took, so a seed of that same write does not overwrite a newer
    ///   change by the document (or by a `set*` call) at the next reopen;
    /// - a write that failed it, whose value the next session is not handed (`failedWrites`).
    private func note(
      _ outcome: NativeInputOutcome, of pending: NativePendingInput,
      on presentation: NativePlayerPresentation
    ) {
      let name: String
      let value: NativeSwiftNamedValue?
      switch pending.input {
      case .setNamed(let writtenName, let writtenValue):
        name = writtenName
        value = writtenValue.coreValue
      case .clearNamed(let clearedName):
        name = clearedName
        value = nil
      default:
        return
      }
      // Every record is keyed by the name the document declares (`NativeHostNameKey`), as
      // `writtenNamedValues()` names each variable: "label" and "USER:label" are one variable.
      let declared = NativeHostNameKey.key(name, by: presentation.engine.declarations)
      switch outcome {
      case .accepted:
        presentation.failedWrites[declared] = nil
        // A cleared variable holds the document's value: a value left out for it is replaced.
        if value == nil { presentation.leftOut.removeAll { $0.name == declared } }
        if let write = pending.hostWrite {
          presentation.hostValues[declared] = NativeHostValue(value: value, write: write)
        }
      case .failed:
        if let value {
          presentation.failedWrites[declared] = value
        } else {
          // A clear that failed the session still drops a value left out for the name, as an
          // accepted clear does: the session never held that value (its variable holds the
          // document's), and the clear asked for the document's. Kept, it would be offered to the
          // next session, undoing the clear; the Kotlin player's `clearNamedValue` drops the
          // host's value whatever the frame then does.
          presentation.leftOut.removeAll { $0.name == declared }
        }
      case .declined:
        break
      }
    }

    /// Install the frame an input produced and deliver its events, and say how the session took
    /// the input.
    private func presentInput(
      _ update: NativePlayerEngine.Update, of input: NativePlayerEngine.Input,
      on presentation: NativePlayerPresentation, generation: UInt64
    ) -> NativeInputOutcome {
      let outcome: NativeInputOutcome = update.accepted ? .accepted : .declined
      do {
        let queued = !presentation.inputs.isEmpty
        if queued, !Self.isNamedWrite(input) {
          // A newer input will install its own frame; validate and deliver this input's events in
          // order without building and discarding an intermediate NativeDocument.
          var budget = NativeFrameBudget()
          try validateEvents(update.events, budget: &budget)
          dispatch(update.events, generation: generation)
          return outcome
        }
        // A named write's frame is judged even with input queued behind it, so the write whose
        // value breaks a limit is the one refused (and kept out of the next session), not the input
        // after it, whatever happens to be queued.
        let model = try NativeDocument(
          frame: update.frame, timeSeconds: update.timeSeconds, limits: executionLimits,
          androidCompatibility: androidCompatibility)
        try validateExecution(model, events: update.events)
        try validate(model)
        guard generation == self.generation else { return outcome }
        // Checked again: `validate` calls the host's diagnostics handler, which may queue input.
        if presentation.inputs.isEmpty { try install(model, for: presentation) }
        dispatch(update.events, generation: generation)
        return outcome
      } catch {
        guard generation == self.generation else { return .declined }
        show(error: error, retaining: presentation)
        return .failed
      }
    }

    private static func isNamedWrite(_ input: NativePlayerEngine.Input) -> Bool {
      switch input {
      case .setNamed, .clearNamed: true
      default: false
      }
    }

    private func dispatch(_ events: [RemoteComposeNativePlayerEvent], generation: UInt64) {
      for event in events {
        guard generation == self.generation else { return }
        switch event {
        case .haptic(let type):
          if let hapticHandler { hapticHandler(type) } else { performNativeDocumentHaptic(type) }
        case .playSound(let id, let data):
          soundPlayer.playSound(id: id, data: data)
        default: break
        }
        onEvent(event)
      }
    }

    private func validate(_ model: NativeDocument) throws {
      let diagnostics = model.diagnostics(availableCustomComponents: customComponents.names)
      onDiagnostics(diagnostics)
      if !RemoteComposeNativeCompatibilityDecision.shouldRender(
        policy: compatibilityPolicy, diagnostics: diagnostics)
      {
        throw RemoteComposeNativePlayerError.incompatible(diagnostics)
      }
    }

    private func validateExecution(
      _ model: NativeDocument, events: [RemoteComposeNativePlayerEvent]
    ) throws {
      try Self.validateExecution(model, events: events, limits: executionLimits)
    }

    /// Whether a frame's work and the events it raises fit `limits`, as the player checks every
    /// frame before installing it.
    private nonisolated static func validateExecution(
      _ model: NativeDocument, events: [RemoteComposeNativePlayerEvent],
      limits: RemoteComposeNativeExecutionLimits
    ) throws {
      var budget = model.executionBudget
      try validateEvents(events, budget: &budget, limits: limits)
    }

    private func validateEvents(
      _ events: [RemoteComposeNativePlayerEvent], budget: inout NativeFrameBudget
    ) throws {
      try Self.validateEvents(events, budget: &budget, limits: executionLimits)
    }

    private nonisolated static func validateEvents(
      _ events: [RemoteComposeNativePlayerEvent], budget: inout NativeFrameBudget,
      limits executionLimits: RemoteComposeNativeExecutionLimits
    ) throws {
      for event in events {
        switch event {
        case .action:
          try budget.recordEvent(limits: executionLimits)
        case .actionWithMetadata(_, let metadata):
          try budget.recordEvent(strings: [metadata], limits: executionLimits)
        case .namedAction(let name, let value):
          switch value {
          case .none, .float, .integer:
            try budget.recordEvent(strings: [name], limits: executionLimits)
          case .text(let text):
            try budget.recordEvent(strings: [name, text], limits: executionLimits)
          case .floatList(let values):
            try budget.recordEvent(
              additionalWork: values.count, strings: [name], limits: executionLimits)
          }
        case .debug(let message, _, _):
          try budget.recordEvent(strings: [message], limits: executionLimits)
        case .haptic:
          try budget.recordEvent(limits: executionLimits)
        case .playSound:
          try budget.recordEvent(limits: executionLimits)
        }
      }
    }

    private func install(
      _ model: NativeDocument, for presentation: NativePlayerPresentation
    ) throws {
      try presentation.resources.activateFonts(replacing: installedResources)
      installedResources = presentation.resources
      // Before the view update, so a callback it raises already finds this session.
      state = .presenting(presentation)
      // The session renders again with every value it holds, so none of them still fails it.
      presentation.failedWrites = [:]
      if documentView?.update(
        document: model, resources: presentation.resources, customComponents: customComponents)
        != true
      {
        let nextView = NativeDocumentView(
          document: model,
          resources: presentation.resources,
          customComponents: customComponents,
          onGesture: { [weak self] componentID, gesture, sample in
            self?.performGesture(gesture, componentID: componentID, sample: sample)
          },
          onClickArea: { [weak self] x, y in
            self?.enqueue(.clickAreas(x: x, y: y), reply: nil)
          },
          onCustomReturn: { [weak self] componentID, propertyID, value in
            guard let self else {
              if case .acknowledgedText(_, let completion) = value { completion(false) }
              return
            }
            self.performCustomReturn(
              componentID: componentID, propertyID: propertyID, value: value)
          })
        replaceDocumentView(with: nextView)
      }
      frameSchedule = model.frameSchedule
      wakeCountdown.reset(after: frameSchedule.wakeAfter)
      errorLabel.isHidden = true
      updateFrameDriver()
    }

    private func show(error: Error, retaining presentation: NativePlayerPresentation?) {
      stopFrameDriver()
      state = .failed(presentation)
      if presentation == nil {
        // The open failed. A write waiting for it gets its answer, `.refused`, from the failed
        // state; the next session to open starts from the values this one would have, and from
        // `namedValueSeed` again.
        inputAvailability.signal()
      }
      scheduledFramePending = false
      errorLabel.text = error.localizedDescription
      errorLabel.isHidden = false
      report(error)
    }

    private func report(_ error: any Error) {
      if let error = error as? RemoteComposeNativePlayerError {
        onError(error)
      } else {
        onError(.decode(error.localizedDescription))
      }
    }

    private func performGesture(
      _ gesture: NativeSwiftGestureKind, componentID: Int,
      sample: NativeSwiftPointerSample?
    ) {
      enqueue(.gesture(gesture, componentID: componentID, sample: sample), reply: nil)
    }

    private func performCustomReturn(
      componentID: Int, propertyID: Int, value: NativeCustomReturnValue
    ) {
      switch value {
      case .float(let value):
        enqueue(
          .customFloat(value, componentID: componentID, propertyID: propertyID), reply: nil)
      case .text(let value):
        enqueue(
          .customText(value, componentID: componentID, propertyID: propertyID), reply: nil)
      case .acknowledgedText(let value, let completion):
        enqueue(
          .customText(value, componentID: componentID, propertyID: propertyID),
          reply: { completion($0 == .accepted) })
      }
    }

    // MARK: - View

    public override func layoutSubviews() {
      super.layoutSubviews()
      documentView?.frame = bounds
      errorLabel.frame = bounds.insetBy(dx: 24, dy: 24)
    }

    public override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
      super.traitCollectionDidChange(previousTraitCollection)
      guard previousTraitCollection?.displayScale != traitCollection.displayScale else { return }
      followDisplayScale()
    }

    public override func didMoveToWindow() {
      super.didMoveToWindow()
      followWindowScene()
      if window == nil {
        animationTimeline.pause(at: clock.now())
      } else if isSceneActive {
        if UIAccessibility.isReduceMotionEnabled {
          animationTimeline.pause(at: clock.now())
        } else {
          animationTimeline.resume(at: clock.now())
        }
      }
      updateFrameDriver()
    }

    private func replaceDocumentView(with nextView: NativeDocumentView) {
      documentView?.removeFromSuperview()
      insertSubview(nextView, belowSubview: errorLabel)
      documentView = nextView
      setNeedsLayout()
      // A replaced tree drops every element VoiceOver knew; tell it to re-read the screen. An
      // in-place update keeps the elements, so it does not announce anything.
      if UIAccessibility.isVoiceOverRunning {
        UIAccessibility.post(notification: .layoutChanged, argument: nil)
      }
    }

    private func configureErrorLabel() {
      errorLabel.accessibilityIdentifier = "rc-native-error"
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

    // MARK: - Frame driver

    private func updateFrameDriver() {
      let now = clock.now()
      wakeCountdown.pause(at: now)
      delayedWakeTask?.cancel()
      delayedWakeTask = nil
      let mode = frameSchedule.driverMode(
        isActive: isSceneActive,
        isVisible: window != nil,
        reduceMotion: UIAccessibility.isReduceMotionEnabled)
      switch mode {
      case .idle:
        displayLink?.invalidate()
        displayLink = nil
      case .displayLink:
        guard displayLink == nil else { return }
        let link = CADisplayLink(
          target: displayLinkTarget, selector: #selector(NativeDisplayLinkTarget.fire))
        if #available(iOS 15.0, *) {
          // A document states whether it animates, not at what rate, so ask for the system's
          // range: full rate on ProMotion and visionOS where the app allows it, 60 Hz elsewhere.
          link.preferredFrameRateRange = .default
        }
        displayLinkTarget.displayLink = link
        link.add(to: .main, forMode: .common)
        displayLink = link
      case .wake(let delay):
        displayLink?.invalidate()
        displayLink = nil
        let remainingDelay = wakeCountdown.start(after: delay, at: now)
        // Replaced timers are cancelled on the main actor, the same actor this resumes on, so a
        // cancelled one always observes it here.
        delayedWakeTask = Task { [weak self] in
          let maximumDelay = TimeInterval(UInt64.max / 1_000_000_000)
          let nanoseconds = UInt64(min(remainingDelay, maximumDelay) * 1_000_000_000)
          try? await Task.sleep(nanoseconds: nanoseconds)
          guard !Task.isCancelled, let self else { return }
          self.delayedWakeTask = nil
          self.requestScheduledFrame()
        }
      }
    }

    private func stopFrameDriver() {
      wakeCountdown.pause(at: clock.now())
      delayedWakeTask?.cancel()
      delayedWakeTask = nil
      displayLink?.invalidate()
      displayLink = nil
    }

    fileprivate func displayLinkDidFire() {
      if frameSchedule.requestsNextFrame, !frameSchedule.needsContinuousFrames {
        displayLink?.invalidate()
        displayLink = nil
      }
      requestScheduledFrame()
    }

    private func requestScheduledFrame() {
      guard isSceneActive, window != nil else { return }
      guard !isBusy else {
        scheduledFramePending = true
        return
      }
      scheduledFramePending = false
      let now = clock.now()
      let time: TimeInterval
      if UIAccessibility.isReduceMotionEnabled {
        time = animationTimeline.sampleFunctional(at: now)
      } else {
        time = animationTimeline.sample(at: now)
      }
      renderFrame(at: time)
    }

    // MARK: - Lifecycle

    @objc private func reduceMotionStatusDidChange() {
      if UIAccessibility.isReduceMotionEnabled {
        animationTimeline.pause(at: clock.now())
      } else if isSceneActive, window != nil {
        animationTimeline.resume(at: clock.now())
      }
      updateFrameDriver()
    }

    /// The density the player uses until the host configures one; see `hostDensity`.
    private var automaticHostDensity: Float {
      guard androidCompatibility == .enabled else { return 1 }
      let scale = Float(traitCollection.displayScale)
      return scale.isFinite && scale > 0 ? scale : 1
    }

    /// Re-derive an unconfigured host density after a trait change, reloading if it moved.
    private func followDisplayScale() {
      guard !hostDensityIsExplicit else { return }
      let density = automaticHostDensity
      guard density != hostDensity else { return }
      hostDensity = density
      if let documentData { render(documentData) }
    }

    /// Observe the lifecycle of the window scene this view is now in, and adopt its state.
    ///
    /// Scene notifications rather than the application's: an app extension has no
    /// `UIApplication.shared`, and on iPadOS and visionOS one scene can be in the background while
    /// another is on screen. A view out of any window keeps following its last scene. Like the
    /// application-level contract this replaces, only the background pauses the player; a scene
    /// that is merely inactive (an overlay, Control Center) keeps animating.
    private func followWindowScene() {
      guard let scene = window?.windowScene, scene !== observedScene else { return }
      let center = NotificationCenter.default
      if let observedScene {
        center.removeObserver(
          self, name: UIScene.didEnterBackgroundNotification, object: observedScene)
        center.removeObserver(self, name: UIScene.didActivateNotification, object: observedScene)
      }
      observedScene = scene
      center.addObserver(
        self, selector: #selector(sceneDidEnterBackground),
        name: UIScene.didEnterBackgroundNotification, object: scene)
      center.addObserver(
        self, selector: #selector(sceneDidActivate),
        name: UIScene.didActivateNotification, object: scene)
      let isBackground = scene.activationState == .background
      if isBackground, isSceneActive {
        sceneDidEnterBackground()
      } else if !isBackground, !isSceneActive {
        sceneDidActivate()
      }
    }

    @objc private func sceneDidEnterBackground() {
      isSceneActive = false
      // An interrupted open, or input that never reached the screen, is replayed from the bytes on
      // activation; an idle session simply resumes.
      let reopen: Bool
      switch state {
      case .loading, .awaitingForeground: reopen = true
      case .presenting(let presentation): reopen = presentation.hasOutstandingInput
      case .failed(let presentation): reopen = presentation?.hasOutstandingInput ?? false
      case .idle: reopen = false
      }
      animationTimeline.pause(at: clock.now())
      stopFrameDriver()
      // The session is replaced on activation; what it holds is read before its work is dropped.
      if reopen { carryNamedValues() }
      abandonWork()
      scheduledFramePending = false
      if reopen, documentData != nil { state = .awaitingForeground }
    }

    @objc private func sceneDidActivate() {
      isSceneActive = true
      if UIAccessibility.isReduceMotionEnabled {
        animationTimeline.pause(at: clock.now())
      } else {
        animationTimeline.resume(at: clock.now())
      }
      if case .awaitingForeground = state, let documentData {
        render(documentData)
      } else {
        updateFrameDriver()
      }
      // Input refused for the background may be taken now, or deferred to the reopen.
      inputAvailability.signal()
    }
  }
#endif
