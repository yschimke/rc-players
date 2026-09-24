import Foundation

/// Retained document state with synchronous, internally serialized access across tasks.
public final class NativeSwiftDocumentSession: @unchecked Sendable {
  private struct StaticSnapshotCache {
    let measuredComponents: [Int: NativeSwiftMeasuredSize]
    let snapshot: NativeSwiftDocumentSnapshot
  }

  // Preserve the synchronous API while serializing its mutable session and snapshot cache. A
  // recursive lock is required because click() forwards to gesture().
  private let stateLock = NSRecursiveLock()
  private let document: ParsedDocument
  private var texts: [Int: String]
  private var floats: [Int: Float]
  private var floatOverrides: [Int: Float] = [:]
  private var hostDensity: Float = 1
  private var hostFontScale: Float = 1
  private var requestedTheme = NativeSwiftTheme.unspecified
  private var colors: [Int: UInt32]
  private var integers: [Int: Int]
  /// Set once a document operation is seen writing `EPOCH_SECOND`'s id itself.
  private var documentClaimsEpochSecond = false
  /// The colours a host has set by name, which a theme switch leaves alone.
  private var hostSetColors: Set<Int> = []
  private var floatAnimationRuntimes: [Int: NativeSwiftFloatAnimationRuntime] = [:]
  private var particleSystems: [Int: NativeSwiftParticleSystemRuntime] = [:]
  /// AndroidX's per-impulse "first frame in the window" bit, and each impulse's phase at the frame
  /// last advanced to.
  private var impulseInitialPass: [Int: Bool] = [:]
  private var impulsePhases: [Int: NativeSwiftImpulsePhase] = [:]
  private var lastImpulseFrameTime: TimeInterval?
  private var lastParticleFrameTime: TimeInterval?
  // A host may request another frame for a document that has no clock-driven state. Preserve the
  // public value snapshot while sharing its copy-on-write storage instead of re-resolving the
  // complete parsed tree each time. Measurements remain part of the cache key because they are the
  // deliberate second pass of the layout contract.
  private var staticSnapshotCache: StaticSnapshotCache?

  private init(document: ParsedDocument) {
    self.document = document
    texts = document.texts
    floats = document.floats
    colors = document.colors
    integers = document.integers
  }

  /// Opens a document.
  ///
  /// - Parameter toleratingRootlessData: decode a *data-only* document — one that declares values
  ///   and nothing to draw — instead of refusing it. The conformance lane asks for this so its value
  ///   probes can be answered; a host that is about to render keeps the default and is told the
  ///   document has nothing to paint.
  public static func open(
    data: Data, toleratingRootlessData: Bool = false
  ) throws -> NativeSwiftDocumentSession {
    NativeSwiftDocumentSession(
      document: try NativeSwiftDocumentDecoder.decode(
        data, toleratingRootlessData: toleratingRootlessData))
  }

  /// The operation spans of a document the decoder accepts, for structure-aware mutation.
  ///
  /// Data-only documents remain strict by default. The conformance value lane can deliberately
  /// opt into the same rootless mode as ``open(data:toleratingRootlessData:)``.
  @_spi(Conformance) public static func operationSpans(
    in data: Data, toleratingRootlessData: Bool = false
  ) throws -> [NativeSwiftOperationSpan] {
    var spans: [NativeSwiftOperationSpan] = []
    _ = try NativeSwiftDocumentDecoder.decode(
      data, spans: &spans, toleratingRootlessData: toleratingRootlessData)
    return spans
  }

  /// The linked document's top-level operation census, with the header included. This is the
  /// operation model conformance exposes; it intentionally differs from raw wire spans.
  @_spi(Conformance) public var linkedOperationCount: Int { document.linkedOperationCount }

  /// Every operation the document carries on the wire, by opcode, once each and in wire order,
  /// header excluded — the census the reference takes of `document.operations`. Unlike
  /// ``operationSpans(in:toleratingRootlessData:)`` it counts a branch that does not run, and a
  /// macro body once however often it is called.
  @_spi(Conformance) public var operationCensus: [Int] { document.operationCensus }

  private init(copying other: NativeSwiftDocumentSession) {
    document = other.document
    texts = other.texts
    floats = other.floats
    floatOverrides = other.floatOverrides
    colors = other.colors
    integers = other.integers
    documentClaimsEpochSecond = other.documentClaimsEpochSecond
    hostSetColors = other.hostSetColors
    hostDensity = other.hostDensity
    hostFontScale = other.hostFontScale
    floatAnimationRuntimes = other.floatAnimationRuntimes.mapValues { $0.detachedCopy() }
    particleSystems = other.particleSystems.mapValues { $0.detachedCopy() }
    impulseInitialPass = other.impulseInitialPass
    requestedTheme = other.requestedTheme
    impulsePhases = other.impulsePhases
    lastImpulseFrameTime = other.lastImpulseFrameTime
    lastParticleFrameTime = other.lastParticleFrameTime
    staticSnapshotCache = other.staticSnapshotCache
  }

  /// A session with this one's state, sharing nothing mutable.
  ///
  /// It exists so a host can re-resolve a snapshot **synchronously**, from inside its own layout
  /// pass, without reaching into the session its frames come from. That matters because `snapshot`
  /// writes as well as reads -- float-to-text conversions, lookups and merges all populate `texts`
  /// -- so calling it from another isolation domain would race whatever is producing frames.
  ///
  /// The copy holds the parsed document, which is immutable once decoded, and its own copies of
  /// everything else. Its state is therefore frozen at the moment it was taken: a host should take
  /// a fresh one with each frame rather than keeping one across document state changes.
  public func detachedCopy() -> NativeSwiftDocumentSession {
    stateLock.lock()
    defer { stateLock.unlock() }
    return NativeSwiftDocumentSession(copying: self)
  }

  /// Tells the document what density it is being played at.
  ///
  /// A `RemoteDensity.Host` capture defers its density instead of folding it in: it writes
  /// expressions over `ID_DENSITY` (27) and `ID_FONT_SIZE` (33), so the same document resolves at
  /// whatever the player supplies. Left unsaid, the native player plays at 1.0 — which is the
  /// density its default layout mode resolves dp geometry at, so the two agree by construction.
  /// A host reproducing Android geometry supplies its real playback density here.
  ///
  /// Non-finite and non-positive values are ignored rather than stored: both reach a document as a
  /// divisor, and a host that reports a zero density mid-layout should not turn the frame's
  /// geometry into NaN.
  public func setHostDensity(_ density: Float, fontScale: Float = 1) {
    stateLock.lock()
    defer { stateLock.unlock() }
    if density.isFinite, density > 0 { hostDensity = density }
    if fontScale.isFinite, fontScale > 0 { hostFontScale = fontScale }
    staticSnapshotCache = nil
  }

  /// The theme the host is showing: `NativeSwiftTheme.dark`, `.light` or `.unspecified`.
  ///
  /// A `ColorTheme` resolves to its dark fallback under a dark theme and to its light one
  /// otherwise. Unspecified stays light rather than following the TypeScript reference's dark,
  /// because light is what the reference JVM lane renders for a player with no theme (see the
  /// `ColorTheme` decode). Operations a `THEME` marker scopes are not filtered by it yet.
  public func setRequestedTheme(_ theme: Int) {
    stateLock.lock()
    defer { stateLock.unlock() }
    requestedTheme = theme
    staticSnapshotCache = nil
  }

  /// - Parameter measuredComponents: what the host laid each bound component out at, once it knows.
  ///   A component named here resolves its width and height bindings from the measurement; one that
  ///   is absent falls back to this core's own estimate, which is what every caller got before this
  ///   parameter existed. Pass nothing on the first pass -- there is nothing to measure yet.
  /// - Parameter wallClock: the host's absolute time, for a document that reads a calendar or
  ///   time-of-day variable. Omitted, those variables are left unset and the elapsed clock is the
  ///   only one a document can animate off.
  public func snapshot(
    timeSeconds: TimeInterval = 0, wallClock: NativeSwiftWallClock? = nil,
    measuredComponents: [Int: NativeSwiftMeasuredSize] = [:]
  ) throws -> NativeSwiftDocumentSnapshot {
    stateLock.lock()
    defer { stateLock.unlock() }
    try resolveIntegerExpressions()
    if canReuseStaticSnapshot(timeSeconds: timeSeconds, wallClock: wallClock),
      let cached = staticSnapshotCache, cached.measuredComponents == measuredComponents
    {
      return cached.snapshot
    }
    // Particle advancement needs current expression targets, but it must not advance a retained
    // float-animation runtime before the final frame resolver sees the measured/particle values.
    // Otherwise one snapshot evaluates the same runtime twice at the same instant with different
    // targets and corrupts its retained initial value.
    var values = try resolvedFloats(
      timeSeconds: timeSeconds, wallClock: wallClock, measuredComponents: measuredComponents,
      resolveAnimatedValues: document.particleLoops.isEmpty)
    advanceParticles(values: values, timeSeconds: timeSeconds)
    // Particle advancement publishes the current particle registers into the session. Resolve one
    // more time before turning commands, colours and text into a snapshot so this frame observes
    // the state it just advanced rather than the previous frame's registers.
    if !document.particleLoops.isEmpty {
      values = try resolvedFloats(
        timeSeconds: timeSeconds, wallClock: wallClock, measuredComponents: measuredComponents,
        resolveAnimatedValues: true)
    }
    advanceImpulses(values: values, timeSeconds: timeSeconds)
    resolveTextOperations(values: values)
    // Data-map lookup is an operation, not a decode-time constant. Its key can be created by a
    // preceding TextFromFloat/TextLookup/TextMerge, so resolve it only after those text producers
    // have run for this frame.
    for lookup in document.dataMapLookups {
      guard let key = texts[lookup.keyTextID], let entry = document.dataMaps[lookup.mapID]?[key]
      else { continue }
      switch entry.type {
      case NativeSwiftDataMapType.string:
        if let value = texts[entry.valueID] { texts[lookup.outputID] = value }
      case NativeSwiftDataMapType.int, NativeSwiftDataMapType.long, NativeSwiftDataMapType.boolean:
        integers[lookup.outputID] = integers[entry.valueID] ?? 0
      case NativeSwiftDataMapType.float:
        values[lookup.outputID] = values[entry.valueID] ?? floats[entry.valueID] ?? 0
      default:
        throw NativeSwiftCoreError.malformed(
          offset: lookup.offset, reason: "Unknown data-map type")
      }
    }
    let resolvedColors = resolveColors(values: values)
    let snapshot = NativeSwiftDocumentSnapshot(
      width: document.width,
      height: document.height,
      density: document.density,
      densityBehavior: document.densityBehavior,
      root: try resolve(document.root, values: values, colors: resolvedColors),
      images: document.imageSnapshots,
      needsContinuousFrames: document.needsContinuousFrames || !document.particleLoops.isEmpty
        || floatAnimationRuntimes.contains {
          floatOverrides[$0.key] == nil && $0.value.isAnimating(at: Float(timeSeconds))
        },
      needsWallClockRefresh: document.needsWallClockRefresh,
      boundComponents: document.boundComponentIDs,
      animationSpecs: document.animationSpecs,
      accessibilityRecords: document.accessibilityRecords.map {
        NativeSwiftAccessibilitySnapshot(
          contentDescriptionID: $0.contentDescriptionID, role: $0.role, textID: $0.textID,
          stateDescriptionID: $0.stateDescriptionID, mode: $0.mode,
          contentDescription: texts[$0.contentDescriptionID], text: texts[$0.textID],
          stateDescription: texts[$0.stateDescriptionID], isEnabled: $0.isEnabled,
          isClickable: $0.isClickable)
      },
      impulses: document.impulses.map {
        NativeSwiftImpulseSnapshot(
          duration: NativeSwiftFloatExpression.resolve($0.durationWord, values: values),
          startAt: NativeSwiftFloatExpression.resolve($0.startAtWord, values: values))
      },
      wakeAfter: wakeAfter(values: values, timeSeconds: timeSeconds),
      conformanceAnimationSpecOrder: document.animationSpecOrder,
      conformancePathIDs: document.pathIDs, conformancePathTweenIDs: document.pathTweenIDs,
      conformanceShaderUniformNames: document.shaderUniformNames,
      conformanceConditionalTraces: document.conditionalTraces)
    if canReuseStaticSnapshot(timeSeconds: timeSeconds, wallClock: wallClock) {
      staticSnapshotCache = StaticSnapshotCache(
        measuredComponents: measuredComponents, snapshot: snapshot)
    }
    return snapshot
  }

  /// AndroidX `ImpulseOperation`: waiting before the window, initializing on the first frame inside
  /// it, processing on every later frame inside it, idle after it, which re-arms the first frame.
  /// Advanced once per frame time, so resolving a frame twice does not consume the first frame.
  private func advanceImpulses(values: [Int: Float], timeSeconds: TimeInterval) {
    guard !document.impulses.isEmpty, lastImpulseFrameTime != timeSeconds else { return }
    lastImpulseFrameTime = timeSeconds
    let time = Float(timeSeconds)
    for (index, impulse) in document.impulses.enumerated() {
      let startAt = NativeSwiftFloatExpression.resolve(impulse.startAtWord, values: values)
      let duration = NativeSwiftFloatExpression.resolve(impulse.durationWord, values: values)
      if time < startAt {
        impulsePhases[index] = .waiting
      } else if time <= startAt + duration {
        impulsePhases[index] = impulseInitialPass[index, default: true] ? .initialize : .process
        impulseInitialPass[index] = false
      } else {
        impulsePhases[index] = .idle
        impulseInitialPass[index] = true
      }
    }
  }

  /// Whether a command drawn inside an impulse belongs to the impulse's phase at this frame.
  private func impulseAllows(_ gate: ParsedImpulseGate?) -> Bool {
    guard let gate else { return true }
    let phase = impulsePhases[gate.impulse] ?? .idle
    if gate.segment == document.impulses[gate.impulse].processSegment { return phase == .process }
    return phase == .initialize
  }

  private func wakeAfter(values: [Int: Float], timeSeconds: TimeInterval) -> TimeInterval? {
    var requests = document.wakeWords.compactMap { word -> TimeInterval? in
      let seconds = NativeSwiftFloatExpression.resolve(word, values: values)
      return seconds.isFinite && seconds >= 0 ? TimeInterval(seconds) : nil
    }
    for (index, impulse) in document.impulses.enumerated() {
      switch impulsePhases[index] ?? .idle {
      case .waiting:
        let startAt = NativeSwiftFloatExpression.resolve(impulse.startAtWord, values: values)
        requests.append(max(TimeInterval(startAt) - timeSeconds, 0))
      case .initialize, .process: requests.append(0)
      case .idle: break
      }
    }
    return requests.min()
  }

  /// Advances retained particle systems to this frame and returns one system's current state.
  /// Rendering a particle loop uses the same retained state; this accessor additionally makes the
  /// behaviour observable to the native conformance host without exposing decoder internals.
  @_spi(Conformance) public func particleSnapshot(id: Int, timeSeconds: TimeInterval = 0) throws
    -> NativeSwiftParticleSystemSnapshot?
  {
    stateLock.lock()
    defer { stateLock.unlock() }
    let values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: nil, measuredComponents: [:])
    advanceParticles(values: values, timeSeconds: timeSeconds)
    return particleSystems[id]?.snapshot
  }

  /// Advances retained particle systems to this frame and returns every decoded system in stable
  /// wire order. Conformance documents can declare more than one system, so callers that observe
  /// particle state must not depend on dictionary iteration order.
  @_spi(Conformance) public func particleSnapshots(timeSeconds: TimeInterval = 0) throws
    -> [NativeSwiftParticleSystemSnapshot]
  {
    stateLock.lock()
    defer { stateLock.unlock() }
    let values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: nil, measuredComponents: [:])
    advanceParticles(values: values, timeSeconds: timeSeconds)
    return document.particleDefinitions.compactMap { particleSystems[$0.id]?.snapshot }
  }

  public func click(componentID: Int, timeSeconds: TimeInterval) throws -> [NativeSwiftEvent]? {
    stateLock.lock()
    defer { stateLock.unlock() }
    return try gesture(.tap, componentID: componentID, sample: nil, timeSeconds: timeSeconds)
  }

  public func gesture(
    _ kind: NativeSwiftGestureKind, componentID: Int,
    sample: NativeSwiftPointerSample? = nil, timeSeconds: TimeInterval
  ) throws -> [NativeSwiftEvent]? {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard let node = document.nodes[componentID], node.isClickable,
      node.accessibility?.isEnabled != false, let actions = node.actions[kind]
    else { return nil }
    try resolveIntegerExpressions()
    // Actions run in order against live state, as the reference's `executeActionsUnchecked` does:
    // each write is visible to the actions after it in the same click.
    var values = try resolvedFloats(timeSeconds: timeSeconds)
    func storeInteger(_ id: Int, _ value: Int) {
      integers[id] = value
      values[id] = Float(value)
    }
    // A float write is published into the integer view too, truncated, as
    // `RcPlayerState.storeFloat` does: the two numeric views share ids.
    func storeFloat(_ id: Int, _ value: Float) {
      floatOverrides[id] = value
      values[id] = value
      integers[id] = Int(Int32(clamping: nativeSwiftClampedInt(value)))
    }
    // A sequence can mutate state before a later expression fails. Invalidate before executing the
    // first action so an error cannot leave a stale static snapshot cached over that mutation.
    staticSnapshotCache = nil
    var events: [NativeSwiftEvent] = []
    for action in actions {
      switch action {
      case .integerExpression(let targetID, let expressionID):
        if let expression = document.integerExpressions[expressionID] {
          storeInteger(
            targetID,
            try NativeSwiftIntegerExpression.evaluate(
              mask: expression.mask, tokens: expression.tokens, values: integers,
              offset: expression.offset))
        } else if let value = integers[expressionID] {
          storeInteger(targetID, value)
        }
      case .floatExpression(let targetID, let expressionID):
        guard let expression = document.expressions.first(where: { $0.id == expressionID })
        else { continue }
        storeFloat(
          targetID,
          try NativeSwiftFloatExpression.evaluate(
            expression.words, values: values, opcode: expression.opcode,
            offset: expression.offset))
      case .integerValue(let targetID, let value):
        storeInteger(targetID, value)
      case .floatValue(let targetID, let value):
        let resolved = NativeSwiftFloatExpression.resolve(value, values: values)
        if resolved.isFinite { storeFloat(targetID, resolved) }
      case .textValue(let targetID, let textID):
        if let text = texts[textID] { texts[targetID] = text }
      case .named(let action):
        guard let name = texts[action.nameTextID] else { continue }
        let value: NativeSwiftActionValue
        switch action.valueType {
        case NativeSwiftHostActionValueType.none: value = .none
        case NativeSwiftHostActionValueType.float: value = .float(values[action.valueID] ?? 0)
        case NativeSwiftHostActionValueType.integer: value = .integer(integers[action.valueID] ?? 0)
        case NativeSwiftHostActionValueType.string: value = .text(texts[action.valueID] ?? "")
        default: continue
        }
        events.append(.namedAction(name: name, value: value))
      }
      try resolveIntegerExpressions()
    }
    return events
  }

  /// Every value the document holds at `timeSeconds`, resolved the way `snapshot` resolves them.
  ///
  /// Called after `snapshot` so the text-from-float conversions and merges it performs are visible
  /// here too; the two reads then describe one instant rather than two.
  @_spi(Conformance) public func probeValues(
    timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock? = nil
  ) throws -> NativeSwiftProbeValues {
    stateLock.lock()
    defer { stateLock.unlock() }
    try resolveIntegerExpressions()
    var values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: wallClock)
    advanceParticles(values: values, timeSeconds: timeSeconds)
    if !document.particleLoops.isEmpty {
      values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: wallClock)
    }
    resolveTextOperations(values: values)
    for lookup in document.dataMapLookups {
      guard let key = texts[lookup.keyTextID], let entry = document.dataMaps[lookup.mapID]?[key]
      else { continue }
      switch entry.type {
      case NativeSwiftDataMapType.string:
        if let value = texts[entry.valueID] { texts[lookup.outputID] = value }
      case NativeSwiftDataMapType.int, NativeSwiftDataMapType.long, NativeSwiftDataMapType.boolean:
        integers[lookup.outputID] = integers[entry.valueID] ?? 0
      case NativeSwiftDataMapType.float:
        values[lookup.outputID] = values[entry.valueID] ?? floats[entry.valueID] ?? 0
      default:
        throw NativeSwiftCoreError.malformed(
          offset: lookup.offset, reason: "Unknown data-map type")
      }
    }
    // Text operations and lookups update retained state, so a previously cached static frame is
    // no longer a valid result for a later snapshot.
    staticSnapshotCache = nil
    // Integer expressions are part of the state, not only of an action's result: the reference
    // evaluates them as it resolves, so a probe reads what an expression computed rather than the
    // empty slot it started in. Order matters, and the document's own declaration order is what it
    // declares.
    return NativeSwiftProbeValues(
      floats: values, integers: integers, texts: texts,
      colors: resolveColors(values: values))
  }

  /// Resolves a static or dynamic float list for conformance state probes.
  @_spi(Conformance) public func probeFloatList(
    id: Int, dynamic: Bool, timeSeconds: TimeInterval
  ) throws -> [Float]? {
    stateLock.lock()
    defer { stateLock.unlock() }
    let values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: nil, measuredComponents: [:])
    if !dynamic {
      if var result = document.floatLists[id]?.map({
        NativeSwiftFloatExpression.resolve($0, values: values)
      }) {
        for update in document.floatListUpdates[id] ?? [] {
          let index = nativeSwiftClampedInt(NativeSwiftFloatExpression.resolve(update.index, values: values))
          if result.indices.contains(index) {
            result[index] = NativeSwiftFloatExpression.resolve(update.value, values: values)
          }
        }
        return result
      }
      // The corpus calls both backing forms "data" lists. Fall through to the dynamic allocation
      // form when no literal DataListFloat with this id exists.
    }
    guard let list = document.dynamicFloatLists[id] else { return nil }
    let length = nativeSwiftClampedInt(NativeSwiftFloatExpression.resolve(list.lengthWord, values: values))
    guard (0...2_000).contains(length) else { return nil }
    var result = Array(repeating: Float(0), count: length)
    for update in list.updates {
      let index = nativeSwiftClampedInt(NativeSwiftFloatExpression.resolve(update.index, values: values))
      if result.indices.contains(index) {
        result[index] = NativeSwiftFloatExpression.resolve(update.value, values: values)
      }
    }
    return result
  }

  /// Returns a matrix in the exact shape the wire declares (nine values for a 3x3 constant,
  /// sixteen for a 4x4 constant or expression).
  @_spi(Conformance)
  public func probeMatrix(id: Int, timeSeconds: TimeInterval) throws -> [Float]? {
    stateLock.lock()
    defer { stateLock.unlock() }
    let values = try resolvedFloats(timeSeconds: timeSeconds, wallClock: nil, measuredComponents: [:])
    if let constant = document.matrixConstants[id] { return constant.values }
    return document.matrixExpressions[id].flatMap {
      NativeSwiftMatrixExpression.evaluate4x4($0, values: values)
    }
  }

  /// The slot a document's named variable occupies, or nil when it declared no such name.
  ///
  /// A probe that names a variable the document never declared is genuinely unobservable; one that
  /// addresses a numeric slot the document left empty is an observation, and the caller has to keep
  /// the two apart.
  @_spi(Conformance)
  public func namedVariableID(_ name: String) -> Int? { namedVariable(for: name)?.id }

  /// The authoring API addresses user values without the wire format's `USER:` prefix. Keep the
  /// wire spelling available too, so hosts can use either form when a document names it explicitly.
  private func namedVariable(for name: String) -> ParsedNamedVariable? {
    document.namedVariables[name] ?? document.namedVariables["USER:\(name)"]
  }

  /// Writes one float slot by id, for a gesture that moves a document's own value — a scroll's
  /// offset is addressed by id rather than by name.
  @discardableResult
  public func setFloat(_ value: Float, forID id: Int) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.isFinite else { return false }
    floatOverrides[id] = value
    staticSnapshotCache = nil
    return true
  }

  public func setFloat(_ value: Float, for name: String) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.isFinite, let variable = namedVariable(for: name),
      variable.type == NativeSwiftNamedVariableType.float
    else {
      return false
    }
    floatOverrides[variable.id] = value
    staticSnapshotCache = nil
    return true
  }

  public func setString(_ value: String, for name: String) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes,
      let variable = namedVariable(for: name), variable.type == NativeSwiftNamedVariableType.string
    else { return false }
    texts[variable.id] = value
    staticSnapshotCache = nil
    return true
  }

  public func setColor(_ value: UInt32, for name: String) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard let variable = namedVariable(for: name), variable.type == NativeSwiftNamedVariableType.color else {
      return false
    }
    colors[variable.id] = value
    hostSetColors.insert(variable.id)
    staticSnapshotCache = nil
    return true
  }

  /// Updates a named RemoteInt. RemoteBoolean uses this same wire type, with `0` and `1` standing
  /// for false and true respectively.
  public func setInteger(_ value: Int, for name: String) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard let variable = namedVariable(for: name), variable.type == NativeSwiftNamedVariableType.int else {
      return false
    }
    integers[variable.id] = value
    staticSnapshotCache = nil
    return true
  }

  public func returnCustomText(_ value: String, componentID: Int, propertyID: Int) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.utf8.count <= NativeSwiftDocumentDecoder.maximumStringBytes,
      let node = document.nodes[componentID], node.kind == .custom,
      let property = node.custom?.properties.first(where: {
        $0.type == propertyID && $0.dataType == NativeSwiftCustomPropertyType.textReturn
      })
    else { return false }
    texts[property.valueBits] = value
    staticSnapshotCache = nil
    return true
  }

  public func returnCustomFloat(_ value: Float, componentID: Int, propertyID: Int) -> Bool {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard value.isFinite, let node = document.nodes[componentID], node.kind == .custom,
      let property = node.custom?.properties.first(where: {
        $0.type == propertyID && $0.dataType == NativeSwiftCustomPropertyType.floatReturn
      }),
      let targetID = NativeSwiftFloatExpression.referenceID(
        UInt32(bitPattern: Int32(property.valueBits)))
    else { return false }
    floatOverrides[targetID] = value
    staticSnapshotCache = nil
    return true
  }

  private func canReuseStaticSnapshot(
    timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock?
  ) -> Bool {
    wallClock == nil
      && !document.needsContinuousFrames
      && document.impulses.isEmpty
      && document.particleLoops.isEmpty
      && !floatAnimationRuntimes.contains {
        floatOverrides[$0.key] == nil && $0.value.isAnimating(at: Float(timeSeconds))
      }
  }

  private func resolve(
    _ node: ParsedNode, values: [Int: Float], colors resolvedColors: [Int: UInt32],
    visibilityOverride: Int? = nil, stateBranchActive: Int? = nil
  ) throws -> NativeSwiftNodeSnapshot {
    let text: NativeSwiftTextSnapshot?
    if let source = node.text {
      // `size > 0` used to be a parse-time guard. It still holds, but a computed size has no value
      // to check until here, so the check moved with it rather than being dropped.
      let size = try resolvedFloat(source.sizeWord, "text size", values: values, positive: true)
      let weight = try resolvedFloat(source.weightWord, "text weight", values: values)
      text = NativeSwiftTextSnapshot(
        value: texts[source.textID] ?? "",
        colorARGB: source.colorID.flatMap { resolvedColors[$0] } ?? source.colorARGB,
        size: size,
        style: source.style,
        weight: min(max(weight, 1), 1_000),
        familyID: source.familyID,
        familyName: texts[source.familyID],
        alignment: source.alignment,
        overflow: source.overflow,
        maximumLines: source.maximumLines)
    } else {
      text = nil
    }

    let custom: NativeSwiftCustomSnapshot?
    if let source = node.custom {
      guard let config = texts[source.configID] else {
        throw NativeSwiftCoreError.malformed(
          offset: source.offset,
          reason: "Custom component \(node.componentID) has no config text")
      }
      let properties = source.properties.map { property in
        let floatValue =
          property.dataType == NativeSwiftCustomPropertyType.floatProperty
          ? NativeSwiftFloatExpression.resolve(
            UInt32(bitPattern: Int32(property.valueBits)), values: values)
          : 0
        let integerValue: Int
        let textValue: String?
        switch property.dataType {
        case NativeSwiftCustomPropertyType.stringProperty:
          integerValue = property.valueBits
          textValue = texts[property.valueBits]
        case NativeSwiftCustomPropertyType.colorIDProperty:
          integerValue = Int(Int32(bitPattern: resolvedColors[property.valueBits] ?? 0))
          textValue = nil
        default:
          integerValue = property.valueBits
          textValue = nil
        }
        return NativeSwiftCustomPropertySnapshot(
          id: property.type, dataType: property.dataType, floatValue: floatValue,
          integerValue: integerValue, textValue: textValue)
      }
      custom = NativeSwiftCustomSnapshot(config: config, properties: properties)
    } else {
      custom = nil
    }

    let maximumWidth = try resolvedFloat(node.maximumWidthWord, "maximum width", values: values)
    let maximumHeight = try resolvedFloat(node.maximumHeightWord, "maximum height", values: values)
    let cornerRadii = try node.cornerRadiusWords.map {
      try resolvedFloat($0, "corner radius", values: values)
    }
    let cornerRadius = cornerRadii.max() ?? 0

    return NativeSwiftNodeSnapshot(
      kind: node.kind,
      componentKind: node.componentKind,
      componentID: node.componentID,
      children: try resolvedChildren(
        of: node, values: values, colors: resolvedColors,
        stateBranchActive: stateBranchActive),
      commands: try node.commands.compactMap { command -> NativeSwiftDrawCommandSnapshot? in
        guard impulseAllows(command.impulseGate) else { return nil }
        return try command.resolve(
          values: values, colors: resolvedColors, texts: texts,
          matrices: document.matrixExpressions)
      },
      isClickable: node.isClickable,
      supportedGestures: node.actions.keys.sorted { $0.rawValue < $1.rawValue },
      accessibility: node.accessibility.map {
        NativeSwiftAccessibilitySnapshot(
          contentDescriptionID: $0.contentDescriptionID, role: $0.role, textID: $0.textID,
          stateDescriptionID: $0.stateDescriptionID, mode: $0.mode,
          contentDescription: texts[$0.contentDescriptionID], text: texts[$0.textID],
          stateDescription: texts[$0.stateDescriptionID], isEnabled: $0.isEnabled,
          isClickable: $0.isClickable)
      },
      // A state layout takes the active child's size whatever the document asks for, matching the
      // reference: a fill modifier on the container itself is dropped rather than honoured, which
      // is what makes the container's own background and border cover the branch and not the
      // parent.
      widthType: stateLayoutDimension(node, isWidth: true),
      widthValue: try dimensionValue(
        node.widthWord, type: stateLayoutDimension(node, isWidth: true), "width", values: values),
      heightType: stateLayoutDimension(node, isWidth: false),
      heightValue: try dimensionValue(
        node.heightWord, type: stateLayoutDimension(node, isWidth: false), "height",
        values: values),
      // A state layout takes the active child's size, so its own padding is dropped with its fill:
      // the reference reports an 80x80 container for an 80x80 child behind a 20pt padding modifier,
      // not 120x120, and the child sits at the container's origin. Keeping the padding made the
      // container's background and border cover 40 points more than the branch.
      padding: node.stateIndexID != nil
        ? NativeSwiftInsets()
        : NativeSwiftInsets(
          left: try resolvedFloat(node.paddingWords.left, "padding left", values: values),
          top: try resolvedFloat(node.paddingWords.top, "padding top", values: values),
          right: try resolvedFloat(node.paddingWords.right, "padding right", values: values),
          bottom: try resolvedFloat(node.paddingWords.bottom, "padding bottom", values: values)),
      minimumWidth: try resolvedFloat(node.minimumWidthWord, "minimum width", values: values),
      maximumWidth: maximumWidth > 1_000_000 ? -1 : maximumWidth,
      minimumHeight: try resolvedFloat(node.minimumHeightWord, "minimum height", values: values),
      maximumHeight: maximumHeight > 1_000_000 ? -1 : maximumHeight,
      cornerRadius: cornerRadius,
      clipsToBounds: node.clipsToBounds,
      graphicsLayer: node.graphicsLayer.isEmpty
        ? nil
        : NativeSwiftGraphicsLayerSnapshot(
          // AndroidX's `GraphicsLayerModifierOperation` ids (#423): 5/6 are the transform origin,
          // 7/8 the translation and 11 the alpha. An absent origin is the centre, as
          // `RcGraphicsLayerValues` in `rc-player-runtime` reads it.
          scaleX: node.graphicsLayer[NativeSwiftGraphicsLayerAttribute.scaleX] ?? 1,
          scaleY: node.graphicsLayer[NativeSwiftGraphicsLayerAttribute.scaleY] ?? 1,
          translationX: node.graphicsLayer[NativeSwiftGraphicsLayerAttribute.translationX] ?? 0,
          translationY: node.graphicsLayer[NativeSwiftGraphicsLayerAttribute.translationY] ?? 0,
          rotationZ: node.graphicsLayer[NativeSwiftGraphicsLayerAttribute.rotationZ] ?? 0,
          alpha: node.graphicsLayer[NativeSwiftGraphicsLayerAttribute.alpha] ?? 1,
          transformOriginX: node.graphicsLayer[NativeSwiftGraphicsLayerAttribute.transformOriginX]
            ?? 0.5,
          transformOriginY: node.graphicsLayer[NativeSwiftGraphicsLayerAttribute.transformOriginY]
            ?? 0.5),
      offsetX: node.offsetXWord.map { NativeSwiftFloatExpression.resolve($0, values: values) } ?? 0,
      offsetY: node.offsetYWord.map { NativeSwiftFloatExpression.resolve($0, values: values) } ?? 0,
      zIndex: node.zIndexWord.map { NativeSwiftFloatExpression.resolve($0, values: values) } ?? 0,
      visibility: visibilityOverride ?? node.visibilityID.map { resolvedVisibility(of: $0) }
        ?? NativeSwiftVisibility.visible,
      backgroundARGB: node.backgroundColorID.flatMap { resolvedColors[$0] } ?? node.backgroundARGB,
      borderARGB: node.borderColorID.flatMap { resolvedColors[$0] } ?? node.borderARGB,
      borderWidth: try resolvedFloat(node.borderWidthWord, "border width", values: values),
      horizontalPositioning: node.horizontalPositioning,
      verticalPositioning: node.verticalPositioning,
      animationID: node.animationID,
      animationSpecID: node.animationSpecID,
      spacing: try resolvedFloat(node.spacingWord, "spacing", values: values),
      stateIndex: node.stateIndexID.flatMap { indexID in
        // Clamped against the *branches*, not the wrapper: the normal shape wraps every
        // alternative in one content node, and clamping against that would always report 0.
        let branches = stateBranches(of: node)
        return branches.isEmpty
          ? nil : min(max(integers[indexID] ?? 0, 0), branches.count - 1)
      },
      flowMaximumItems: node.flowMaximumItems,
      flowMaximumLines: node.flowMaximumLines,
      isCollapsible: node.isCollapsible,
      collapsiblePriority: try node.collapsiblePriorityWord.map {
        try resolvedFloat($0, "collapsible priority", values: values)
      },
      collapsiblePriorityOrientation: node.collapsiblePriorityOrientation,
      scrollDirection: node.scrollDirection,
      scrollPositionID: node.scrollPositionWord.flatMap {
        NativeSwiftFloatExpression.referenceID($0)
      },
      scrollOffset: node.scrollPositionWord.map {
        NativeSwiftFloatExpression.resolve($0, values: values)
      } ?? 0,
      scrollMaximum: node.scrollMaximumWord.map {
        NativeSwiftFloatExpression.resolve($0, values: values)
      } ?? 0,
      text: text,
      custom: custom)
  }

  /// A state layout's branches, unwrapping the bare content node they usually share.
  private func stateBranches(of node: ParsedNode) -> [ParsedNode] {
    guard node.children.count == 1, node.children[0].kind == .content,
      !node.children[0].children.isEmpty
    else { return node.children }
    return node.children[0].children
  }

  /// The width or height type a state layout resolves with; every other node keeps its own.
  ///
  /// The reference's `StateLayout` adopts its active child's size, so **its own dimension is
  /// dropped whatever kind it is** — a `FILL` modifier made the container cover the whole parent and
  /// paint its background over the branch, and a fixed one held the container at a size the branch
  /// never had. `interaction_click_button` declares `height(100)` on a container whose branches are
  /// 40 and 80 tall, and the reference reports 40 and then 80. Bounds (`widthIn`/`heightIn`) still
  /// apply: they constrain the child's size rather than replacing it.
  /// A width or height modifier's value. A bare `fillMaxWidth()`/`fillMaxHeight()` writes the
  /// canonical NaN as its fraction, which the reference reads as 1 (`fillFraction()` in
  /// `RcNativeSnapshot.kt`); resolved as an expression reference it would become 0 and be
  /// indistinguishable from an explicit zero fraction, so the raw word is checked here, where it
  /// still exists.
  private func dimensionValue(
    _ word: UInt32, type: Int, _ field: String, values: [Int: Float]
  ) throws -> Float {
    if NativeSwiftDimensionType.isFill(type) && word == Float.nan.bitPattern { return 1 }
    return try resolvedFloat(word, field, values: values)
  }

  private func stateLayoutDimension(_ node: ParsedNode, isWidth: Bool) -> Int {
    guard node.stateIndexID != nil else { return isWidth ? node.widthType : node.heightType }
    return NativeSwiftDimensionType.wrap
  }

  /// A node's children, with a state layout's inactive branches marked GONE.
  ///
  /// A `StateLayout` shows one child — the one its index integer selects — and keeps the others
  /// GONE so they can take part in a transition. Laid out as a plain box it showed *every* branch
  /// stacked, which is the single largest remaining raster difference on the native lane.
  private func resolvedChildren(
    of node: ParsedNode, values: [Int: Float], colors resolvedColors: [Int: UInt32],
    stateBranchActive: Int? = nil
  ) throws -> [NativeSwiftNodeSnapshot] {
    // A state layout's own index decides which of *its* branches is shown, and the branches are
    // usually wrapped in the same bare content node every other container uses.
    if let indexID = node.stateIndexID, !node.children.isEmpty {
      let wrapper = node.children.count == 1 && node.children[0].kind == .content
        ? node.children[0] : nil
      let branches = stateBranches(of: node)
      guard !branches.isEmpty else {
        return try node.children.map { try resolve($0, values: values, colors: resolvedColors) }
      }
      let active = min(max(integers[indexID] ?? 0, 0), branches.count - 1)
      if let wrapper {
        return [
          try resolve(
            wrapper, values: values, colors: resolvedColors, stateBranchActive: active)
        ]
      }
      return try node.children.enumerated().map { position, child in
        try resolve(
          child, values: values, colors: resolvedColors,
          visibilityOverride: position == active ? 1 : 0)
      }
    }
    return try node.children.enumerated().map { position, child in
      try resolve(
        child, values: values, colors: resolvedColors,
        visibilityOverride: stateBranchActive.map { position == $0 ? 1 : 0 })
    }
  }

  /// Resolves a float field that a document may either state outright or compute.
  ///
  /// A literal word is its own bit pattern, so `resolve` is a strict generalisation of the eager
  /// `Float` these fields used to hold; a NaN-boxed word is a reference into `values`. Validation
  /// that used to run while parsing runs here, because a computed field has nothing to validate
  /// until it resolves — and failing here is what keeps a garbage value from reaching UIKit.
  private func resolvedFloat(
    _ word: UInt32, _ field: String, values: [Int: Float], positive: Bool = false
  ) throws -> Float {
    let value = NativeSwiftFloatExpression.resolve(word, values: values)
    guard value.isFinite else {
      throw NativeSwiftCoreError.malformed(offset: 0, reason: "\(field) resolved to \(value)")
    }
    guard !positive || value > 0 else {
      throw NativeSwiftCoreError.malformed(
        offset: 0, reason: "\(field) resolved to \(value), which is not positive")
    }
    return value
  }

  /// Integer expressions are retained document state. Re-evaluate them in wire declaration order
  /// whenever a frame or gesture observes state, so an action that changes an input propagates to
  /// conditionals, text lookup and integer-to-float projections in that same frame.
  private func resolveIntegerExpressions() throws {
    for id in document.integerExpressionOrder {
      guard let expression = document.integerExpressions[id] else { continue }
      integers[id] = try NativeSwiftIntegerExpression.evaluate(
        mask: expression.mask, tokens: expression.tokens, values: integers,
        offset: expression.offset)
    }
  }

  /// AndroidX `TimeAttribute.paint`: one calendar field or interval, read from an instant.
  ///
  /// The instant is the `LongConstant` the operation names, or the host's wall clock when the
  /// document names none. Fields are read in the wall clock's zone, as the reference reads the
  /// system zone. Month and day of week are zero-based, as they are in the reference. A value
  /// with nothing to measure from — no wall clock for a `NOW` or `LOAD` interval, a missing
  /// argument — leaves the slot unwritten rather than inventing an epoch.
  private func timeAttribute(
    _ attribute: ParsedTimeAttribute, timeSeconds: TimeInterval,
    wallClock: NativeSwiftWallClock?
  ) -> Float? {
    guard let selectedMillis = document.longConstants[attribute.timeID] ?? wallClock?.epochMillis
    else { return nil }
    let selected = NativeSwiftWallClock(
      epochMillis: selectedMillis,
      offsetSeconds: wallClock?.offsetSeconds(atEpochMillis: selectedMillis) ?? 0)
    let fields = selected.fields
    typealias TimeType = NativeSwiftTimeAttributeType
    // Intervals are taken in Double, not Int64: a document picks both instants, and two
    // `LongConstant`s as far apart as Int64.max and Int64.min overflow -- and trap -- an Int64
    // subtraction. Epoch milliseconds are exact in Double well beyond any real date, so an ordinary
    // instant gives exactly what the integer difference did.
    func interval(since start: Double, unit: Double) -> Float {
      Float((Double(selectedMillis) - start) * 0.001 / unit)
    }
    switch attribute.type {
    case TimeType.fromNowSeconds, TimeType.fromNowMinutes, TimeType.fromNowHours:
      guard let now = wallClock?.epochMillis else { return nil }
      let unit: Double = attribute.type == TimeType.fromNowSeconds
        ? 1 : attribute.type == TimeType.fromNowMinutes ? 60 : 3600
      return interval(since: Double(now), unit: unit)
    case TimeType.fromArgumentSeconds, TimeType.fromArgumentMinutes, TimeType.fromArgumentHours:
      guard let argumentID = attribute.argumentIDs.first,
        let argument = document.longConstants[argumentID]
      else { return nil }
      let unit: Double = attribute.type == TimeType.fromArgumentSeconds
        ? 1 : attribute.type == TimeType.fromArgumentMinutes ? 60 : 3600
      return interval(since: Double(argument), unit: unit)
    case TimeType.second: return Float(fields.second)
    case TimeType.minute: return Float(fields.minute)
    case TimeType.hour: return Float(fields.hour)
    case TimeType.dayOfMonth: return Float(fields.dayOfMonth)
    case TimeType.monthValue: return Float(fields.month - 1)
    case TimeType.dayOfWeek: return Float(fields.isoDayOfWeek - 1)
    case TimeType.year: return Float(fields.year)
    case TimeType.fromLoadSeconds:
      // The document loaded `timeSeconds` before the wall clock's instant.
      guard let now = wallClock?.epochMillis else { return nil }
      // In Double too: `Int64(_:)` traps on a non-finite or out-of-range elapsed time.
      let loadMillis = Double(now) - (timeSeconds * 1000).rounded()
      return interval(since: loadMillis, unit: 1)
    case TimeType.dayOfYear: return Float(fields.dayOfYear)
    default: return nil
    }
  }

  private func resolvedFloats(
    timeSeconds: TimeInterval, wallClock: NativeSwiftWallClock? = nil,
    measuredComponents: [Int: NativeSwiftMeasuredSize] = [:], resolveAnimatedValues: Bool = true
  ) throws -> [Int: Float] {
    // `EPOCH_SECOND` is an integer the player owns, so it is installed before the integer
    // expressions that read it, not after them. Whether the document claims the id instead is
    // learned below from what the document itself writes there, not from the slot being empty:
    // after the first frame the slot holds the previous frame's epoch.
    let epochSecond = NativeSwiftSystemVariables.epochSecond
    let hostOwnsEpochSecond =
      !documentClaimsEpochSecond && document.integers[epochSecond] == nil
      && document.integerExpressions[epochSecond] == nil && floatOverrides[epochSecond] == nil
    if hostOwnsEpochSecond, let wallClock {
      integers[epochSecond] = Int(NativeSwiftWallClock.floorDiv(wallClock.epochMillis, 1000))
    }
    try resolveIntegerExpressions()
    var result = floats
    result.merge(floatOverrides) { _, override in override }
    // RemoteBoolean is encoded as a named RemoteInt, then projected into float/color expressions by
    // RemoteInt.toRemoteFloat(). Expose integer slots to the float evaluator without overriding a
    // real float that deliberately shares an id.
    var projectedIntegers: Set<Int> = []
    for (id, value) in integers where result[id] == nil {
      // The player's own epoch is published after the document's producers have had their say.
      if hostOwnsEpochSecond, id == epochSecond { continue }
      result[id] = Float(value)
      projectedIntegers.insert(id)
    }
    // A copied dynamic colour is encoded as colour expression → channel attributes → colour
    // expression. Resolve the source colours before extracting their channels so the final colour
    // pass can preserve copies such as a selected tint with reduced alpha. Only a channel attribute
    // reads this preliminary table, so a document without one does not pay for a second colour
    // resolution per frame; `resolveColors` has no side effects to preserve.
    if !document.colorAttributes.isEmpty {
      let preliminaryColors = resolveColors(values: result)
      for attribute in document.colorAttributes {
        guard floatOverrides[attribute.outputID] == nil else { continue }
        result[attribute.outputID] = colorAttribute(
          attribute.type, of: preliminaryColors[attribute.colorID] ?? 0)
      }
    }
    // Player-supplied clocks. A document that declares its own value at one of these ids keeps it,
    // matching the reference player's claimed-id rule — `floats` seeds `result`.
    //
    // The animation clock is the host's logical timeline, not the wall clock: it is what a capture
    // can hold still. `CONTINUOUS_SEC` is the wall clock's seconds within the current hour when a
    // host supplies one; without one the elapsed time is the only clock this core has, and a
    // document that animates off it still moves rather than standing at the 1970 epoch.
    let animationTime = Float(timeSeconds)
    if result[NativeSwiftSystemVariables.animationTime] == nil {
      result[NativeSwiftSystemVariables.animationTime] = animationTime
    }
    if let wallClock {
      let fields = wallClock.fields
      if result[NativeSwiftSystemVariables.continuousSeconds] == nil {
        result[NativeSwiftSystemVariables.continuousSeconds] =
          Float(fields.secondOfHour) + Float(fields.millisOfSecond) * 0.001
      }
      if result[NativeSwiftSystemVariables.timeInSeconds] == nil {
        result[NativeSwiftSystemVariables.timeInSeconds] = Float(fields.secondOfHour)
      }
      if result[NativeSwiftSystemVariables.timeInMinutes] == nil {
        result[NativeSwiftSystemVariables.timeInMinutes] =
          Float(fields.hour * 60 + fields.minute)
      }
      if result[NativeSwiftSystemVariables.timeInHours] == nil {
        result[NativeSwiftSystemVariables.timeInHours] = Float(fields.hour)
      }
      if result[NativeSwiftSystemVariables.calendarMonth] == nil {
        result[NativeSwiftSystemVariables.calendarMonth] = Float(fields.month)
      }
      if result[NativeSwiftSystemVariables.offsetToUTC] == nil {
        result[NativeSwiftSystemVariables.offsetToUTC] = Float(wallClock.offsetSeconds)
      }
      if result[NativeSwiftSystemVariables.weekDay] == nil {
        result[NativeSwiftSystemVariables.weekDay] = Float(fields.isoDayOfWeek)
      }
      if result[NativeSwiftSystemVariables.dayOfMonth] == nil {
        result[NativeSwiftSystemVariables.dayOfMonth] = Float(fields.dayOfMonth)
      }
      if result[NativeSwiftSystemVariables.dayOfYear] == nil {
        result[NativeSwiftSystemVariables.dayOfYear] = Float(fields.dayOfYear)
      }
      if result[NativeSwiftSystemVariables.year] == nil {
        result[NativeSwiftSystemVariables.year] = Float(fields.year)
      }
      if hostOwnsEpochSecond {
        if result[epochSecond] == nil {
          result[epochSecond] = Float(NativeSwiftWallClock.floorDiv(wallClock.epochMillis, 1000))
        } else {
          // A document operation wrote the id first (the reference's claimed-id rule): it is the
          // document's from now on, and the integer this resolution installed is withdrawn.
          documentClaimsEpochSecond = true
          integers.removeValue(forKey: epochSecond)
        }
      }
    } else if result[NativeSwiftSystemVariables.continuousSeconds] == nil {
      result[NativeSwiftSystemVariables.continuousSeconds] = animationTime
    }
    // Not clocks, but owed by the player for the same reason and with the same failure: an
    // unsupplied reference resolves to 0 here, so a `RemoteDensity.Host` capture's
    // `([33] 14.0 / [27] / 15.0 *)` divides by zero and every size built from it becomes NaN.
    // A document that declares its own value at either id keeps it — `floats` seeds `result`.
    if result[NativeSwiftSystemVariables.density] == nil {
      result[NativeSwiftSystemVariables.density] = hostDensity
    }
    if result[NativeSwiftSystemVariables.fontSize] == nil {
      result[NativeSwiftSystemVariables.fontSize] =
        NativeSwiftSystemVariables.defaultFontSizeSp * hostFontScale * hostDensity
    }
    // A length reads the text as the session last resolved it; the settle pass below brings a
    // derived text's length up to date within the same resolution.
    for length in document.textLengths where floatOverrides[length.outputID] == nil {
      guard let text = texts[length.textID] else { continue }
      result[length.outputID] = Float(text.utf16.count)
    }
    for attribute in document.timeAttributes {
      guard floatOverrides[attribute.outputID] == nil,
        let value = timeAttribute(attribute, timeSeconds: timeSeconds, wallClock: wallClock)
      else { continue }
      result[attribute.outputID] = value
    }
    // Expressions are evaluated twice, deliberately. A component-value binding measures a node, and
    // a node's own geometry can now itself be a reference, so neither ordering is right alone: the
    // first pass gives the measurement something better than zero to read, the second lets an
    // expression that reads a measured value see it. Evaluation is pure, so repeating it is safe.
    //
    // This first pass is deliberately tolerant. An expression that reads a binding the measurement
    // below has not produced yet resolves that reference to zero, which can divide to a non-finite
    // result that `evaluate` rejects — correctly, but not yet. Dropping it here leaves the id
    // unset, exactly as it was before this pass existed; the authoritative pass after the
    // measurement evaluates it for real and throws if it is still bad.
    //
    // The first pass only changes what the second computes when something sits between them to
    // read it -- a component-value binding -- or when an expression reads an id that the same or a
    // later expression writes, which the second pass then sees updated. Without either, the second
    // pass reads exactly what the first did and recomputes the same values (animation runtimes
    // return the same value for the same target at the same instant), so the decoder decides once
    // per document whether the first pass is needed at all.
    for expression in document.expressions where document.needsTolerantExpressionPass {
      guard floatOverrides[expression.id] == nil else { continue }
      if let value = try? NativeSwiftFloatExpression.evaluate(
        expression.words, values: result, opcode: expression.opcode, offset: expression.offset)
      {
        if resolveAnimatedValues, expression.animationWords != nil,
          let runtime = try? animationRuntime(for: expression)
        {
          // Geometry bindings are measured immediately below. They must see the same animated
          // value the final resolver will draw, not the expression's raw target.
          result[expression.id] = runtime.evaluate(target: value, at: Float(timeSeconds))
        } else {
          result[expression.id] = value
        }
      }
    }
    // Subtree estimates are pure in (node, dimension) for one `values` table, so they are shared
    // across bindings -- a parent and child both bound, or a width and height binding on one node,
    // walk the same subtree. A binding writes `result`, though, so when any layout word reads a
    // bound value the table is dropped before each binding's estimate instead.
    var estimates: [NativeSwiftEstimateKey: Float] = [:]
    for binding in document.componentValues {
      guard floatOverrides[binding.valueID] == nil else { continue }
      // A real measurement wins over any estimate. Width and height are the only two types this
      // player resolves, and they are the two a host can report.
      if let measured = measuredComponents[binding.componentID],
        binding.type == NativeSwiftComponentValueType.width
          || binding.type == NativeSwiftComponentValueType.height
      {
        result[binding.valueID] =
          binding.type == NativeSwiftComponentValueType.width ? measured.width : measured.height
        continue
      }
      let available =
        binding.type == NativeSwiftComponentValueType.width
        ? Float(document.width) : Float(document.height)
      guard let node = document.nodes[binding.componentID] else {
        // Matching the reference player: a width or height binding to a component this document
        // does not describe resolves to the document's own, rather than leaving the id unset and
        // taking every expression built on it down with it.
        if binding.type == NativeSwiftComponentValueType.width
          || binding.type == NativeSwiftComponentValueType.height
        {
          result[binding.valueID] = available
        }
        continue
      }
      let measuredNode = node.parent ?? node
      if document.layoutReadsComponentValues { estimates.removeAll(keepingCapacity: true) }
      result[binding.valueID] = estimatedDimension(
        of: measuredNode, type: binding.type, available: available, values: result,
        estimates: &estimates)
    }
    func evaluateExpressions() throws {
      for expression in document.expressions {
        guard floatOverrides[expression.id] == nil else { continue }
        let target = try NativeSwiftFloatExpression.evaluate(
          expression.words, values: result, opcode: expression.opcode, offset: expression.offset)
        if resolveAnimatedValues, expression.animationWords != nil {
          let runtime = try animationRuntime(for: expression)
          result[expression.id] = runtime.evaluate(target: target, at: Float(timeSeconds))
        } else {
          result[expression.id] = target
        }
      }
    }
    try evaluateExpressions()
    // Text lengths and `ID_LOOKUP` feed expressions but are produced from state the expressions
    // themselves decide (a derived text, a computed index). The reference runs them in wire order;
    // this settles them instead: produce, and if any output moved, re-run what reads it. Chains are
    // short, so a few rounds reach the fixed point wire order would.
    if !document.textLengths.isEmpty || !document.idLookups.isEmpty {
      for _ in 0..<4 {
        var changed = false
        if !document.textLengths.isEmpty {
          resolveTextOperations(values: result)
          for length in document.textLengths where floatOverrides[length.outputID] == nil {
            guard let text = texts[length.textID] else { continue }
            let value = Float(text.utf16.count)
            if result[length.outputID] != value {
              result[length.outputID] = value
              changed = true
            }
          }
        }
        // `ID_LOOKUP` publishes an integer, and integers are visible to float expressions. A list
        // that does not exist, or an index outside it, leaves the slot as it was.
        for lookup in document.idLookups {
          guard let ids = document.idLists[lookup.listID] else { continue }
          let index = nativeSwiftClampedInt(
            NativeSwiftFloatExpression.resolve(lookup.index, values: result))
          guard ids.indices.contains(index), integers[lookup.outputID] != ids[index] else {
            continue
          }
          integers[lookup.outputID] = ids[index]
          if floatOverrides[lookup.outputID] == nil { result[lookup.outputID] = Float(ids[index]) }
          changed = true
        }
        guard changed else { break }
        try resolveIntegerExpressions()
        for id in projectedIntegers {
          if let value = integers[id] { result[id] = Float(value) }
        }
        try evaluateExpressions()
      }
    }
    for operation in document.matrixVectorMath {
      guard let matrix = resolvedMatrix(operation.matrixID, values: result) else { continue }
      let input = operation.inputWords.map { NativeSwiftFloatExpression.resolve($0, values: result) }
      var output = [Float](repeating: 0, count: operation.outputIDs.count)
      if operation.type == 0 {
        for row in output.indices {
          var value = matrix[3 + row * 4]
          for column in input.indices { value += matrix[column + row * 4] * input[column] }
          output[row] = value
        }
      } else {
        var input4 = [Float](repeating: 0, count: 4)
        input4[3] = 1
        for index in input.indices { input4[index] = input[index] }
        var output4 = [Float](repeating: 0, count: 4)
        for row in 0..<4 {
          for column in 0..<4 { output4[row] += matrix[column + row * 4] * input4[column] }
        }
        guard output4[3] != 0 else { continue }
        for index in output.indices { output[index] = output4[index] / output4[3] }
      }
      for (index, outputID) in operation.outputIDs.enumerated() { result[outputID] = output[index] }
    }
    return result
  }

  private func resolvedMatrix(_ id: Int, values: [Int: Float]) -> [Float]? {
    if let constant = document.matrixConstants[id] {
      if constant.values.count == 16 { return constant.values }
      guard constant.values.count == 9 else { return nil }
      let v = constant.values
      return [v[0], v[1], 0, v[2], v[3], v[4], 0, v[5], v[6], v[7], v[8], 0, 0, 0, 0, 1]
    }
    return document.matrixExpressions[id].flatMap {
      NativeSwiftMatrixExpression.evaluate4x4($0, values: values)
    }
  }

  /// The retained runtime for an expression that carries animation words.
  private func animationRuntime(
    for expression: ParsedFloatExpression
  ) throws -> NativeSwiftFloatAnimationRuntime {
    if let existing = floatAnimationRuntimes[expression.id] { return existing }
    let runtime = try NativeSwiftFloatAnimationRuntime(
      animationWords: expression.animationWords ?? [], offset: expression.offset)
    floatAnimationRuntimes[expression.id] = runtime
    return runtime
  }

  private func advanceParticles(values: [Int: Float], timeSeconds: TimeInterval) {
    guard !document.particleLoops.isEmpty, lastParticleFrameTime != timeSeconds else { return }
    lastParticleFrameTime = timeSeconds
    for definition in document.particleDefinitions where particleSystems[definition.id] == nil {
      particleSystems[definition.id] = NativeSwiftParticleSystemRuntime(definition: definition, values: values)
    }
    for loop in document.particleLoops {
      guard let system = particleSystems[loop.id] else { continue }
      system.advance(loop: loop, baseValues: values)
      // The ordinary resolver sees the latest particle while drawing a loop body. UIKit's loop
      // renderer replaces these for each child; publishing the last value is still the reference's
      // externally observable register state when no loop child is painted.
      for (index, variableID) in system.variableIDs.enumerated() {
        floats[variableID] = system.particles.last?[index] ?? 0
      }
    }
    staticSnapshotCache = nil
  }

  /// Resolves text producers in wire order. The output of an operation is immediately visible to
  /// the next operation, including one of a different concrete type.
  private func resolveTextOperations(values: [Int: Float]) {
    for operation in document.textOperations {
      switch operation {
      case .fromFloat(let conversion):
        let value = NativeSwiftFloatExpression.resolve(conversion.value, values: values)
        texts[conversion.outputID] = NativeSwiftTextFormatter.format(
          value, digitsBefore: conversion.digitsBefore, digitsAfter: conversion.digitsAfter,
          flags: conversion.flags)
      case .merge(let merge):
        texts[merge.outputID] = (texts[merge.leftID] ?? "") + (texts[merge.rightID] ?? "")
      case .lookupInt(let lookup):
        guard let ids = document.idLists[lookup.listID], !ids.isEmpty else { continue }
        let index = min(max(integers[lookup.indexID] ?? 0, 0), ids.count - 1)
        texts[lookup.outputID] = texts[ids[index]] ?? ""
      case .lookup(let lookup):
        guard let ids = document.idLists[lookup.listID], !ids.isEmpty else { continue }
        let index = min(
          max(nativeSwiftClampedInt(NativeSwiftFloatExpression.resolve(lookup.index, values: values)), 0), ids.count - 1)
        texts[lookup.outputID] = texts[ids[index]] ?? ""
      case .transform(let transform):
        resolveTextTransform(transform, values: values)
      case .subtext(let subtext):
        let units = Array((texts[subtext.textID] ?? "").utf16)
        let start = min(
          max(nativeSwiftClampedInt(NativeSwiftFloatExpression.resolve(subtext.start, values: values)), 0),
          units.count)
        let length = nativeSwiftClampedInt(
          NativeSwiftFloatExpression.resolve(subtext.length, values: values))
        // Clamp against the remaining units before adding, so a saturated length cannot overflow.
        let end = length == -1 ? units.count : start + min(max(length, 0), units.count - start)
        texts[subtext.outputID] = String(decoding: units[start..<end], as: UTF16.self)
      }
    }
  }

  /// The protocol's slicing coordinates are UTF-16 code-unit offsets, as in Android's String.
  /// Decode the selected units directly instead of stepping Swift character indices, which would
  /// move differently for emoji and combining sequences.
  private func resolveTextTransform(_ transform: ParsedTextTransform, values: [Int: Float]) {
    let source = texts[transform.textID] ?? ""
    let units = Array(source.utf16)
    let start = max(0, nativeSwiftClampedInt(NativeSwiftFloatExpression.resolve(transform.start, values: values)))
    let requestedLength = nativeSwiftClampedInt(NativeSwiftFloatExpression.resolve(transform.length, values: values))
    let startOffset = min(start, units.count)
    let remaining = units.count - startOffset
    let length = requestedLength <= 0 ? remaining : min(requestedLength, remaining)
    let selected = String(decoding: units[startOffset..<(startOffset + length)], as: UTF16.self)
    switch transform.operation {
    case NativeSwiftTextTransformOperation.lowercase: texts[transform.outputID] = selected.lowercased()
    case NativeSwiftTextTransformOperation.uppercase: texts[transform.outputID] = selected.uppercased()
    case NativeSwiftTextTransformOperation.trim:
      texts[transform.outputID] = selected.trimmingCharacters(in: .whitespacesAndNewlines)
    case NativeSwiftTextTransformOperation.capitalizeWords:
      var startsWord = true
      texts[transform.outputID] = selected.reduce(into: "") { result, character in
        result += startsWord ? String(character).uppercased() : String(character)
        startsWord = character.isWhitespace
      }
    case NativeSwiftTextTransformOperation.capitalizeFirst:
      var result = ""
      var capitalized = false
      for character in selected {
        if !capitalized && !character.isWhitespace {
          result += String(character).uppercased()
          capitalized = true
        } else {
          result.append(character)
        }
      }
      texts[transform.outputID] = result
    default: texts[transform.outputID] = selected
    }
  }

  /// Visibility as the protocol encodes it, including its override bits.
  ///
  /// Above 15 the value is a mask -- OVERRIDE_GONE 16, OVERRIDE_VISIBLE 32, OVERRIDE_INVISIBLE 64 --
  /// and the plain 0/1/2 meanings apply only below that. The order matters and is the reference's:
  /// visible wins over gone, gone over invisible, and anything unrecognised is gone rather than
  /// visible, so an unknown state hides a component instead of drawing it in an undefined one.
  private func resolvedVisibility(of id: Int) -> Int {
    // An unset integer reads 0, and 0 is GONE -- not visible. That is the reference's behaviour and
    // the corpus asserts it: `column_child_visibility` binds three children to integers the document
    // never assigns, and expects all three GONE while their unmodified ancestors stay VISIBLE.
    // Defaulting to visible instead looks safer and is wrong; a component whose visibility nobody
    // has decided is hidden, not shown.
    let value = integers[id] ?? NativeSwiftVisibility.gone
    if value >> 4 > 0 {
      if value & NativeSwiftVisibility.overrideVisible == NativeSwiftVisibility.overrideVisible {
        return NativeSwiftVisibility.visible
      }
      if value & NativeSwiftVisibility.overrideGone == NativeSwiftVisibility.overrideGone {
        return NativeSwiftVisibility.gone
      }
      if value & NativeSwiftVisibility.overrideInvisible == NativeSwiftVisibility.overrideInvisible {
        return NativeSwiftVisibility.invisible
      }
      return NativeSwiftVisibility.gone
    }
    switch value {
    case NativeSwiftVisibility.visible: return NativeSwiftVisibility.visible
    case NativeSwiftVisibility.gone: return NativeSwiftVisibility.gone
    case NativeSwiftVisibility.invisible: return NativeSwiftVisibility.invisible
    default: return NativeSwiftVisibility.gone
    }
  }

  private func colorAttribute(_ type: Int, of color: UInt32) -> Float {
    let red = Float((color >> 16) & 0xff) / 255
    let green = Float((color >> 8) & 0xff) / 255
    let blue = Float(color & 0xff) / 255
    let maximum = max(red, green, blue)
    let minimum = min(red, green, blue)
    let delta = maximum - minimum
    switch type {
    case NativeSwiftColorAttributeType.hue:
      let sector: Float
      if maximum == minimum { sector = 0 }
      else if maximum == red { sector = (green - blue) / delta }
      else if maximum == green { sector = (blue - red) / delta + 2 }
      else { sector = (red - green) / delta + 4 }
      var hue = (sector * 60).truncatingRemainder(dividingBy: 360)
      if hue < 0 { hue += 360 }
      return hue / 360
    case NativeSwiftColorAttributeType.saturation: return maximum == minimum ? 0 : delta / maximum
    case NativeSwiftColorAttributeType.brightness: return maximum
    case NativeSwiftColorAttributeType.red: return red
    case NativeSwiftColorAttributeType.green: return green
    case NativeSwiftColorAttributeType.blue: return blue
    default: return Float((color >> 24) & 0xff) / 255
    }
  }

  private func resolveColors(values: [Int: Float]) -> [Int: UInt32] {
    var result = colors
    // Not where the host has set the slot: that value is the host's, whatever the theme, even when
    // it happens to equal the document's light fallback.
    if requestedTheme == NativeSwiftTheme.dark {
      for (id, dark) in document.darkColors where !hostSetColors.contains(id) {
        result[id] = dark
      }
    }
    for expression in document.colorExpressions {
      let mode = expression.modeAndAlpha & 0xff
      switch mode {
      case NativeSwiftColorExpressionMode.colorColorInterpolate...NativeSwiftColorExpressionMode.idIDInterpolate:
        let first = mode & 1 != 0 ? result[expression.first] ?? 0 : UInt32(bitPattern: Int32(expression.first))
        let second = mode & 2 != 0 ? result[expression.second] ?? 0 : UInt32(bitPattern: Int32(expression.second))
        let tween = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.third)), values: values)
        result[expression.outputID] = interpolateColor(first, second, tween: tween)
      case NativeSwiftColorExpressionMode.hsv...NativeSwiftColorExpressionMode.idARGB:
        let alpha: Float
        if mode == NativeSwiftColorExpressionMode.hsv {
          alpha = Float(expression.modeAndAlpha >> 16) / 255
        } else if mode == NativeSwiftColorExpressionMode.argb {
          alpha = Float(expression.modeAndAlpha >> 16) / 1024
        }
        else {
          alpha = NativeSwiftFloatExpression.resolve(
            0x7fc0_0000 | UInt32(expression.modeAndAlpha >> 16), values: values)
        }
        let first = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.first)), values: values)
        let second = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.second)), values: values)
        let third = NativeSwiftFloatExpression.resolve(
          UInt32(bitPattern: Int32(expression.third)), values: values)
        result[expression.outputID] =
          mode == NativeSwiftColorExpressionMode.hsv
          ? hsvColor(alpha: alpha, hue: first, saturation: second, brightness: third)
          : argbColor(alpha: alpha, red: first, green: second, blue: third)
      default: break
      }
    }
    return result
  }

  private func interpolateColor(_ first: UInt32, _ second: UInt32, tween: Float) -> UInt32 {
    if !tween.isFinite || tween == 0 { return first }
    if tween == 1 { return second }
    func channel(_ color: UInt32, shift: UInt32) -> Float {
      powf(Float((color >> shift) & 0xff) / 255, 2.2)
    }
    func encoded(_ value: Float) -> UInt32 {
      let encoded = powf(max(value, 0), 1 / 2.2)
      guard encoded.isFinite else { return value > 0 ? 255 : 0 }
      return UInt32((min(max(encoded, 0), 1) * 255).rounded())
    }
    let alpha = Float((first >> 24) & 0xff) + tween * Float(Int((second >> 24) & 0xff) - Int((first >> 24) & 0xff))
    let red = channel(first, shift: 16) + tween * (channel(second, shift: 16) - channel(first, shift: 16))
    let green = channel(first, shift: 8) + tween * (channel(second, shift: 8) - channel(first, shift: 8))
    let blue = channel(first, shift: 0) + tween * (channel(second, shift: 0) - channel(first, shift: 0))
    let clampedAlpha = min(max(alpha, 0), 255)
    return UInt32(clampedAlpha.isFinite ? clampedAlpha.rounded() : 0) << 24
      | encoded(red) << 16 | encoded(green) << 8 | encoded(blue)
  }

  /// A colour byte, clamped rather than converted.
  ///
  /// `Int(value)` traps for a non-finite or out-of-range float, and a document is free to compute a
  /// channel from expressions this player cannot bound — a structure-aware mutant found exactly
  /// that. A colour is not worth failing a frame over, so a non-finite channel saturates instead.
  private func argbColor(alpha: Float, red: Float, green: Float, blue: Float) -> UInt32 {
    func byte(_ value: Float) -> UInt32 {
      guard value.isFinite else { return value > 0 ? 255 : 0 }
      return UInt32((min(max(value, 0), 1) * 255).rounded())
    }
    return byte(alpha) << 24 | byte(red) << 16 | byte(green) << 8 | byte(blue)
  }

  private func hsvColor(
    alpha: Float, hue: Float, saturation: Float, brightness: Float
  ) -> UInt32 {
    guard hue.isFinite, saturation.isFinite, brightness.isFinite else {
      return argbColor(alpha: alpha, red: 0, green: 0, blue: 0)
    }
    // Wrapped into [0, 1) before the sector is taken, because `Int(hue * 6)` traps on a hue a
    // document computed beyond Float's integral range.
    let wrapped = hue - floorf(hue)
    let section = Int(wrapped * 6)
    let fraction = wrapped * 6 - Float(section)
    let p = brightness * (1 - saturation)
    let q = brightness * (1 - fraction * saturation)
    let t = brightness * (1 - (1 - fraction) * saturation)
    let rgb: (Float, Float, Float)
    switch section {
    case 0: rgb = (brightness, t, p)
    case 1: rgb = (q, brightness, p)
    case 2: rgb = (p, brightness, t)
    case 3: rgb = (p, q, brightness)
    case 4: rgb = (t, p, brightness)
    case 5: rgb = (brightness, p, q)
    default: rgb = (0, 0, 0)
    }
    return argbColor(alpha: alpha, red: rgb.0, green: rgb.1, blue: rgb.2)
  }

  /// A node's estimated width or height, memoised in `estimates`.
  ///
  /// The estimate is a pure function of the node, the dimension, `available` (fixed per dimension)
  /// and `values`, so a caller may keep `estimates` for as long as `values` does not change.
  private func estimatedDimension(
    of node: ParsedNode, type: Int, available: Float, values: [Int: Float],
    estimates: inout [NativeSwiftEstimateKey: Float]
  ) -> Float {
    let key = NativeSwiftEstimateKey(node: ObjectIdentifier(node), type: type)
    if let cached = estimates[key] { return cached }
    let estimate = uncachedEstimatedDimension(
      of: node, type: type, available: available, values: values, estimates: &estimates)
    estimates[key] = estimate
    return estimate
  }

  private func uncachedEstimatedDimension(
    of node: ParsedNode, type: Int, available: Float, values: [Int: Float],
    estimates: inout [NativeSwiftEstimateKey: Float]
  ) -> Float {
    // Measurement runs before the final resolution pass and must not throw: an unresolvable field
    // reads as zero here and is rejected properly by `resolvedFloat` when the snapshot is built.
    func float(_ word: UInt32) -> Float {
      let value = NativeSwiftFloatExpression.resolve(word, values: values)
      return value.isFinite ? value : 0
    }
    let dimensionType = type == NativeSwiftComponentValueType.width ? node.widthType : node.heightType
    let dimensionWord = type == NativeSwiftComponentValueType.width ? node.widthWord : node.heightWord
    let dimensionValue = float(dimensionWord)
    if dimensionType == NativeSwiftDimensionType.exact || dimensionType == NativeSwiftDimensionType.exactDp {
      return max(dimensionValue, 0)
    }
    if NativeSwiftDimensionType.isFill(dimensionType) {
      // A bare fill writes the canonical NaN, which `float` would resolve to 0; the reference
      // reads it as a fraction of 1.
      let fraction = dimensionWord == Float.nan.bitPattern ? 1 : max(dimensionValue, 0)
      return ancestorDimension(
        of: node, type: type, available: available, values: values, estimates: &estimates)
        * fraction
    }
    // A fill child contributes nothing to a wrapping parent's intrinsic size: the parent decides
    // the child's size, not the other way round. Including one used to make a wrap box that
    // contains a fill canvas measure the whole document, because the fill's own estimate resolved
    // to `available`.
    let children = flattenedChildren(of: node, values: values).filter {
      !NativeSwiftDimensionType.isFill(
        type == NativeSwiftComponentValueType.width ? $0.widthType : $0.heightType)
    }
    var childDimensions: [Float] = []
    childDimensions.reserveCapacity(children.count)
    for child in children {
      childDimensions.append(
        estimatedDimension(
          of: child, type: type, available: available, values: values, estimates: &estimates))
    }
    let intrinsic: Float
    if let text = node.text {
      if type == NativeSwiftComponentValueType.width {
        intrinsic = Float(texts[text.textID]?.count ?? 0) * float(text.sizeWord) * 0.6
      } else {
        intrinsic = float(text.sizeWord) * 1.2
      }
    } else if type == NativeSwiftComponentValueType.width {
      intrinsic = node.kind == .row ? childDimensions.reduce(0, +) : childDimensions.max() ?? 0
    } else {
      intrinsic =
        node.kind == .column
        ? childDimensions.reduce(0, +)
          + float(node.spacingWord) * Float(max(childDimensions.count - 1, 0))
        : childDimensions.max() ?? 0
    }
    let padding =
      type == NativeSwiftComponentValueType.width
      ? float(node.paddingWords.left) + float(node.paddingWords.right)
      : float(node.paddingWords.top) + float(node.paddingWords.bottom)
    let minimum = type == NativeSwiftComponentValueType.height ? float(node.minimumHeightWord) : 0
    return max(intrinsic + padding, minimum)
  }

  /// The size a fill resolves to: the nearest ancestor that has one, not the document.
  ///
  /// A fill's size is its parent's, so a chain of transparent wrappers and fills has to be walked
  /// up until something determinate is found. Returning `available` at the first fill is what made
  /// an icon inside a 52-unit button measure the 454-unit canvas; the reference measures the
  /// component the layout actually gave it. Falls back to the document when no ancestor decides.
  private func ancestorDimension(
    of node: ParsedNode, type: Int, available: Float, values: [Int: Float],
    estimates: inout [NativeSwiftEstimateKey: Float]
  ) -> Float {
    func float(_ word: UInt32) -> Float {
      let value = NativeSwiftFloatExpression.resolve(word, values: values)
      return value.isFinite ? value : 0
    }
    var current = node.parent
    var depth = 0
    while let candidate = current, depth < 64 {
      let dimensionType =
        type == NativeSwiftComponentValueType.width ? candidate.widthType : candidate.heightType
      let dimensionValue = float(
        type == NativeSwiftComponentValueType.width ? candidate.widthWord : candidate.heightWord)
      if dimensionType == NativeSwiftDimensionType.exact || dimensionType == NativeSwiftDimensionType.exactDp {
        return max(dimensionValue, 0)
      }
      if !NativeSwiftDimensionType.isFill(dimensionType) {
        let intrinsic = estimatedDimension(
          of: candidate, type: type, available: available, values: values,
          estimates: &estimates)
        if intrinsic > 0 { return intrinsic }
      }
      current = candidate.parent
      depth += 1
    }
    return available
  }


  /// Flattens a bare content wrapper into its parent for measurement.
  ///
  /// The padding test runs on resolved values, not on words: a wrapper whose padding is computed
  /// and comes out zero is just as bare as one that says `0`, and comparing the encoded words
  /// would keep it — and so measure a row's grandchildren as one child's maximum rather than
  /// their sum.
  private func flattenedChildren(of node: ParsedNode, values: [Int: Float]) -> [ParsedNode] {
    func isZero(_ word: UInt32) -> Bool {
      NativeSwiftFloatExpression.resolve(word, values: values) == 0
    }
    return node.children.flatMap { child in
      if child.kind == .content, child.widthType == NativeSwiftDimensionType.wrap, child.heightType == NativeSwiftDimensionType.wrap,
        isZero(child.paddingWords.left), isZero(child.paddingWords.top),
        isZero(child.paddingWords.right), isZero(child.paddingWords.bottom)
      {
        return flattenedChildren(of: child, values: values)
      }
      return [child]
    }
  }
}

/// Memo key for `estimatedDimension`: one parsed node's width or height estimate.
struct NativeSwiftEstimateKey: Hashable {
  let node: ObjectIdentifier
  let type: Int
}
