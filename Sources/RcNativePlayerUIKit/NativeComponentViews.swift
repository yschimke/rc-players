#if canImport(UIKit)
  import CoreText
  #if canImport(RcNativePlayerCore)
    import RcNativePlayerCore
  #endif
  #if canImport(RcPlayerAppleFonts)
    import RcPlayerAppleFonts
  #endif
  import UIKit

  struct NativeDocument {
    let size: CGSize
    let density: CGFloat
    let densityBehavior: Int
    let androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility
    let root: NativeNode
    let images: [NativeImageResource]
    let fonts: [NativeFontResource]
    let downloadableFonts: [NativeDownloadableFont]
    let diagnostics: RemoteComposeNativePlayerDiagnostics
    let rootSizing: Int
    let rootMode: Int
    let rootAlignment: Int
    let frameSchedule: NativeFrameSchedule
    let executionBudget: NativeFrameBudget
    /// Components whose measured geometry the document binds to a float. Empty for almost every
    /// document; when it is not, the root view reports their real sizes back for a second pass.
    let boundComponents: Set<Int>
    /// Set only when the document binds component geometry. See `refined(measuredComponents:)`.
    private(set) var refiner: NativeSwiftDocumentSession?
    /// The frame time this document was resolved at.
    ///
    /// A refinement re-resolves the session against real geometry, and it must do so at the same
    /// instant the frame it is refining was taken at — otherwise an animated document's refinement
    /// snaps back to zero, and a player that refines after every layout pass then alternates
    /// between the animated frame and a static one.
    let timeSeconds: TimeInterval
    /// The host's absolute time at that instant, for the same reason: a calendar field must not
    /// jump when the refinement re-resolves.
    let wallClock: NativeSwiftWallClock?
    private let limits: RemoteComposeNativeExecutionLimits

    init(
      frame: NativeSnapshotSessionHandle.Frame, timeSeconds: TimeInterval,
      limits: RemoteComposeNativeExecutionLimits,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility = .disabled
    ) throws {
      try self.init(
        swiftSnapshot: frame.snapshot, timeSeconds: timeSeconds, wallClock: frame.wallClock,
        limits: limits, androidCompatibility: androidCompatibility)
      refiner = frame.refiner
    }

    /// This document re-resolved against what the host actually laid its components out at.
    ///
    /// Returns nil when there is nothing to re-resolve, or when the refined snapshot fails a budget
    /// check the first one passed — a refinement is an improvement, never a reason to fail a
    /// document that already rendered.
    func refined(measuredComponents: [Int: NativeSwiftMeasuredSize]) -> NativeDocument? {
      guard let refiner, !measuredComponents.isEmpty else { return nil }
      guard
        let snapshot = try? refiner.snapshot(
          timeSeconds: timeSeconds, wallClock: wallClock, measuredComponents: measuredComponents),
        var document = try? NativeDocument(
          swiftSnapshot: snapshot, timeSeconds: timeSeconds, wallClock: wallClock, limits: limits,
          androidCompatibility: androidCompatibility)
      else { return nil }
      document.refiner = refiner
      return document
    }

    private init(
      swiftSnapshot: NativeSwiftDocumentSnapshot, timeSeconds: TimeInterval,
      wallClock: NativeSwiftWallClock?, limits: RemoteComposeNativeExecutionLimits,
      androidCompatibility: RemoteComposeNativePlayerAndroidCompatibility
    ) throws {
      self.timeSeconds = timeSeconds
      self.wallClock = wallClock
      try NativeFrameBudget.validate(limits)
      var budget = NativeFrameBudget()
      var downloadableByID: [Int: NativeDownloadableFont] = [:]
      try budget.validateDocumentDimensions(
        [Double(swiftSnapshot.width), Double(swiftSnapshot.height)], limits: limits)
      var pending: [(NativeSwiftNodeSnapshot, Int)] = [(swiftSnapshot.root, 1)]
      while let (node, depth) = pending.popLast() {
        try budget.recordNode(depth: depth, limits: limits)
        try budget.validateLayoutDimension(
          value: Double(node.widthValue), type: node.widthType, componentID: node.componentID,
          field: "width", limits: limits)
        try budget.validateLayoutDimension(
          value: Double(node.heightValue), type: node.heightType, componentID: node.componentID,
          field: "height", limits: limits)
        try budget.validateNumbers(
          [
            node.padding.left, node.padding.top, node.padding.right, node.padding.bottom,
            node.spacing,
          ]
          .map(Double.init), componentID: node.componentID, field: "layout", limits: limits)
        if let text = node.text {
          try budget.recordCommand(pathElementCount: 0, strings: [text.value], limits: limits)
          try budget.validateFinite(
            [text.size, text.weight].map(Double.init), componentID: node.componentID,
            field: "text")
          if let family = text.familyName?.trimmingCharacters(in: .whitespacesAndNewlines),
            family.lowercased().hasPrefix("google:")
          {
            let name = String(family.dropFirst("google:".count)).trimmingCharacters(
              in: .whitespacesAndNewlines)
            if !name.isEmpty {
              downloadableByID[text.familyID] = NativeDownloadableFont(id: text.familyID, family: name)
            }
          }
        }
        for command in node.commands {
          try budget.recordCommand(pathElementCount: command.path.count, limits: limits)
          try budget.validateNumbers(
            command.geometryValues.map(Double.init)
              + [Double(command.strokeWidth), Double(command.alpha)]
              + command.path.flatMap { $0.values.map(Double.init) },
            componentID: node.componentID, field: "draw", limits: limits)
        }
        if let custom = node.custom {
          try budget.recordWork(custom.properties.count, limits: limits)
          try budget.recordStrings(
            [Optional(custom.config)] + custom.properties.map(\.textValue), limits: limits)
        }
        pending.append(contentsOf: node.children.map { ($0, depth + 1) })
      }
      executionBudget = budget
      boundComponents = swiftSnapshot.boundComponents
      self.limits = limits
      density = CGFloat(swiftSnapshot.density)
      guard density.isFinite, density > 0 else {
        throw RemoteComposeNativePlayerError.decode("Document density must be finite and positive")
      }
      densityBehavior = swiftSnapshot.densityBehavior
      self.androidCompatibility = androidCompatibility
      size = CGSize(width: swiftSnapshot.width, height: swiftSnapshot.height)
      root = NativeNode(
        swiftSnapshot: swiftSnapshot.root,
        densityBehavior: swiftSnapshot.densityBehavior)
      images = swiftSnapshot.images.map {
        NativeImageResource(
          id: $0.id, width: $0.width, height: $0.height, type: $0.type,
          encoding: $0.encoding, data: $0.data)
      }
      fonts = []
      downloadableFonts = downloadableByID.values.sorted { $0.id < $1.id }
      diagnostics = RemoteComposeNativePlayerDiagnostics(
        issues: NativeDensityPolicy.diagnostics(
          density: swiftSnapshot.density,
          densityBehavior: swiftSnapshot.densityBehavior,
          androidCompatibility: androidCompatibility,
          componentID: swiftSnapshot.root.componentID),
        unsupportedOpcodes: [], notes: [])
      rootSizing = 2
      rootMode = 4
      rootAlignment = 34
      frameSchedule = NativeFrameSchedule(
        needsContinuousFrames: swiftSnapshot.needsContinuousFrames,
        requestsNextFrame: false,
        // A document that reads a discrete wall-clock field has to be re-resolved at least once a
        // second, or its clock freezes on the first frame; one with a WAKE_IN or an impulse asks
        // for its own time. The driver re-arms this after each wake.
        wakeAfter: swiftSnapshot.hostWakeAfter)
    }

    func diagnostics(availableCustomComponents: Set<String>)
      -> RemoteComposeNativePlayerDiagnostics
    {
      var issues = diagnostics.issues
      var pending = [root]
      while let node = pending.popLast() {
        if let custom = node.custom, !availableCustomComponents.contains(custom.config) {
          issues.append(
            RemoteComposeNativePlayerDiagnostic(
              severity: .unsupported, opcode: NativeSwiftWireOpcode.layoutCustom, operationName: "Custom",
              componentID: node.componentID,
              reason: "Host custom component '\(custom.config)' is not registered"))
        }
        pending.append(contentsOf: node.children)
      }
      return RemoteComposeNativePlayerDiagnostics(
        issues: issues,
        unsupportedOpcodes: Array(
          Set(
            diagnostics.unsupportedOpcodes + (issues.count > diagnostics.issues.count ? [93] : []))
        ).sorted(),
        notes: diagnostics.notes)
    }

  }

  struct NativeImageResource {
    let id: Int
    let width: Int
    let height: Int
    let type: Int
    let encoding: Int
    let data: Data

  }

  struct NativeFontResource {
    let id: Int
    let type: Int
    let data: Data

  }

  struct NativeDownloadableFont {
    let id: Int
    let family: String
  }

  struct NativeNode {
    enum Kind: Equatable {
      case root
      case content
      case canvas
      case group
      case box
      case row
      case column
      case text
      case image
      case custom

      init(rawValue: Int32) {
        switch rawValue {
        case 0: self = .root
        case 1: self = .content
        case 2: self = .canvas
        case 4: self = .box
        case 5: self = .row
        case 6: self = .column
        case 7: self = .text
        case 8: self = .image
        case 9: self = .custom
        default: self = .group
        }
      }
    }

    let kind: Kind
    let componentID: Int
    let commands: [NativeDrawCommand]
    let children: [NativeNode]
    /// What the shared accessibility policy reads of this node, resolved by the document core's
    /// snapshot so both hosts start from the same descriptor, labels and actions.
    let semanticDescriptor: NativeAccessibilityDescriptor?
    let semanticLocalLabels: [String]
    let semanticClickActionTypes: [NativeSwiftGestureKind]
    let gestureTypes: [NativeSwiftGestureKind]
    let widthType: Int
    let widthValue: CGFloat
    let heightType: Int
    let heightValue: CGFloat
    let minimumHeight: CGFloat
    let minimumWidth: CGFloat
    let maximumWidth: CGFloat?
    let maximumHeight: CGFloat?
    let padding: UIEdgeInsets
    let cornerRadius: CGFloat
    let clipsToBounds: Bool
    let graphicsLayer: NativeSwiftGraphicsLayerSnapshot?
    let backgroundColor: UIColor?
    let horizontalPositioning: Int
    let verticalPositioning: Int
    let spacing: CGFloat
    /// Set on `FlowLayout`: children wrap onto further lines, bounded by these. Nil for every other
    /// container.
    let flowMaximumItems: Int?
    let flowMaximumLines: Int?
    /// Set on the collapsible row/column family; see `NativeSwiftCollapsible`.
    let isCollapsible: Bool
    let collapsiblePriority: CGFloat?
    let collapsiblePriorityOrientation: Int?
    let offset: CGPoint
    let zIndex: CGFloat
    let visibility: Int
    let custom: NativeCustomComponent?
    let densityBehavior: Int
    /// The AndroidX class name of the operation that produced this node — `BoxLayout`, `CoreText`,
    /// `FitBoxLayout`. Read by the `FitBox` layout, which is the one class that does not behave like
    /// the box it decodes as; empty for a structural content wrapper.
    let componentKind: String
    /// The children of a scrolled container are laid out against their content, not against the
    /// viewport that clips them, so this is what decides a container's layout space.
    let scrollDirection: NativeSwiftScrollDirection?
    /// What the shared layout engine reads of this node.
    let layout: NativeLayoutNode

    init(swiftSnapshot snapshot: NativeSwiftNodeSnapshot, densityBehavior: Int) {
      layout = NativeLayoutNode(snapshot: snapshot)
      switch snapshot.kind {
      case .root: kind = .root
      case .content: kind = .content
      case .canvas: kind = .canvas
      case .box: kind = .box
      case .row: kind = .row
      case .column: kind = .column
      case .text: kind = .text
      case .image: kind = .image
      case .custom: kind = .custom
      }
      componentID = snapshot.componentID
      componentKind = snapshot.componentKind
      scrollDirection = snapshot.scrollDirection
      commands =
        snapshot.commands.map(NativeDrawCommand.init)
        + (snapshot.text.map { [NativeDrawCommand(text: $0)] } ?? [])
      children = snapshot.children.map {
        NativeNode(swiftSnapshot: $0, densityBehavior: densityBehavior)
      }
      semanticDescriptor = snapshot.semanticDescriptor
      semanticLocalLabels = snapshot.semanticLocalLabels
      semanticClickActionTypes = snapshot.semanticClickActionTypes
      gestureTypes = snapshot.supportedGestures
      widthType = snapshot.widthType
      widthValue = CGFloat(snapshot.widthValue)
      heightType = snapshot.heightType
      heightValue = CGFloat(snapshot.heightValue)
      minimumHeight = CGFloat(snapshot.minimumHeight)
      minimumWidth = CGFloat(snapshot.minimumWidth)
      maximumWidth = snapshot.maximumWidth < 0 ? nil : CGFloat(snapshot.maximumWidth)
      maximumHeight = snapshot.maximumHeight < 0 ? nil : CGFloat(snapshot.maximumHeight)
      padding = UIEdgeInsets(
        top: CGFloat(snapshot.padding.top), left: CGFloat(snapshot.padding.left),
        bottom: CGFloat(snapshot.padding.bottom), right: CGFloat(snapshot.padding.right))
      cornerRadius = CGFloat(snapshot.cornerRadius)
      clipsToBounds = snapshot.clipsToBounds
      graphicsLayer = snapshot.graphicsLayer
      backgroundColor = snapshot.backgroundARGB.map(UIColor.init(remoteComposeARGB:))
      horizontalPositioning = snapshot.horizontalPositioning
      verticalPositioning = snapshot.verticalPositioning
      spacing = CGFloat(snapshot.spacing)
      flowMaximumItems = snapshot.flowMaximumItems
      flowMaximumLines = snapshot.flowMaximumLines
      isCollapsible = snapshot.isCollapsible
      collapsiblePriority = snapshot.collapsiblePriority.map(CGFloat.init)
      collapsiblePriorityOrientation = snapshot.collapsiblePriorityOrientation
      offset = CGPoint(x: CGFloat(snapshot.offsetX), y: CGFloat(snapshot.offsetY))
      zIndex = CGFloat(snapshot.zIndex)
      visibility = snapshot.visibility
      custom = snapshot.custom.map(NativeCustomComponent.init)
      self.densityBehavior = densityBehavior
    }

    var firstText: String? {
      commands.lazy.compactMap(\.text).first ?? children.lazy.compactMap(\.firstText).first
    }
  }

  /// The document as it was written. The component views resolve from what they display instead;
  /// see their conformance below.
  extension NativeNode: NativeAccessibilityNode {
    var semanticComponentID: Int { componentID }
    var isSemanticallyVisible: Bool { visibility == NativeSwiftVisibility.visible }
    var semanticChildren: [NativeNode] { children }
  }

  /// The accessibility policy reads each component as it is displayed rather than as the document
  /// wrote it: a container can hide a child the document shows (a collapsed row, a FitBox's other
  /// alternatives), and a FitBox shows an alternative whose own visibility modifier is GONE. A
  /// merging ancestor has to take the displayed children's labels and actions, as AppKit does.
  extension NativeComponentView: @preconcurrency NativeAccessibilityNode {
    var semanticComponentID: Int { node.semanticComponentID }
    var semanticDescriptor: NativeAccessibilityDescriptor? { node.semanticDescriptor }
    var semanticLocalLabels: [String] { node.semanticLocalLabels }
    var semanticClickActionTypes: [NativeSwiftGestureKind] { node.semanticClickActionTypes }
    var isSemanticallyVisible: Bool {
      !isHidden && (node.isSemanticallyVisible || ignoresOwnVisibility)
    }
    var semanticChildren: [NativeComponentView] { componentChildren }
  }

  struct NativeCustomComponent: Equatable {
    let config: String
    let properties: [RemoteComposeNativeCustomProperty]

    init(_ snapshot: NativeSwiftCustomSnapshot) {
      config = snapshot.config
      properties = snapshot.properties.map {
        RemoteComposeNativeCustomProperty(
          id: $0.id, dataType: $0.dataType, floatValue: $0.floatValue,
          integerValue: $0.integerValue, textValue: $0.textValue)
      }
    }
  }

  /// `==` decides whether a `NativeCanvasView` redraws, so it is written out rather than
  /// synthesised: every float member compares NaN-stably (`NativeRedrawEquality`), since
  /// `values` carries the anchored-text `panY` NaN sentinel and IEEE `==` would call an unchanged
  /// command different every frame. Every other member uses its own `==`, `UIColor`'s being
  /// `isEqual`. A new stored property must be added to `==`; `NativeCanvasRedrawTests` counts them.
  struct NativeDrawCommand: Equatable {
    let kind: Int
    let values: [CGFloat]
    let color: UIColor
    let alpha: CGFloat
    let strokeWidth: CGFloat
    let isStroke: Bool
    let strokeCap: NativeSwiftStrokeCap
    let strokeJoin: NativeSwiftStrokeJoin
    let blendMode: NativeSwiftPaintBlendMode
    let textSize: CGFloat
    let textWeight: CGFloat
    let text: String?
    let path: [NativePathElement]
    let pathWinding: NativeSwiftPathWinding
    let gradient: NativeGradient?
    let textStyle: NativeTextStyle
    let image: NativeImageDraw?
    let textureImageID: Int?
    let textureTileModeX: Int
    let textureTileModeY: Int
    let shaderMatrix: [Float]?
    let filterQuality: Int?
    let usesComponentGeometry: Bool
    let offscreenTarget: NativeSwiftOffscreenTargetSnapshot?

    init(_ snapshot: NativeSwiftDrawCommandSnapshot) {
      kind = snapshot.kind
      values = snapshot.values.map(CGFloat.init)
      color = UIColor(remoteComposeARGB: snapshot.colorARGB)
      alpha = CGFloat(snapshot.alpha)
      strokeWidth = CGFloat(snapshot.strokeWidth)
      isStroke = snapshot.isStroke
      strokeCap = snapshot.strokeCap
      strokeJoin = snapshot.strokeJoin
      blendMode = snapshot.blendMode
      textSize = 16
      textWeight = 400
      text = nil
      path = snapshot.path.map {
        NativePathElement(kind: $0.kind, values: $0.values.map(CGFloat.init))
      }
      pathWinding = snapshot.pathWinding
      gradient = snapshot.gradient.map {
        NativeGradient(
          kind: $0.kind,
          colors: $0.colorsARGB.map { UIColor(remoteComposeARGB: $0).cgColor },
          stops: $0.stops.map(CGFloat.init),
          values: $0.values.map(CGFloat.init),
          tileMode: $0.tileMode)
      }
      textStyle = NativeTextStyle.default
      image = snapshot.image.map {
        NativeImageDraw(
          imageID: $0.imageID,
          source: CGRect(
            x: CGFloat($0.sourceLeft), y: CGFloat($0.sourceTop),
            width: CGFloat($0.sourceRight - $0.sourceLeft),
            height: CGFloat($0.sourceBottom - $0.sourceTop)),
          destination: CGRect(
            x: CGFloat($0.destinationLeft), y: CGFloat($0.destinationTop),
            width: CGFloat($0.destinationRight - $0.destinationLeft),
            height: CGFloat($0.destinationBottom - $0.destinationTop)),
          scaleType: $0.scaleType, scaleFactor: CGFloat($0.scaleFactor),
          contentDescription: $0.contentDescription)
      }
      textureImageID = snapshot.textureImageID
      textureTileModeX = snapshot.textureTileModeX
      textureTileModeY = snapshot.textureTileModeY
      shaderMatrix = snapshot.shaderMatrix
      filterQuality = snapshot.filterQuality
      usesComponentGeometry = snapshot.usesComponentGeometry
      offscreenTarget = snapshot.offscreenTarget
    }

    init(text snapshot: NativeSwiftTextSnapshot) {
      kind = 17
      values = [0, CGFloat(snapshot.size), -1, -1, 0, 0]
      color = UIColor(remoteComposeARGB: snapshot.colorARGB)
      alpha = 1
      strokeWidth = 1
      isStroke = false
      strokeCap = .butt
      strokeJoin = .miter
      blendMode = .sourceOver
      textSize = CGFloat(snapshot.size)
      textWeight = CGFloat(snapshot.weight)
      text = snapshot.value
      path = []
      pathWinding = .nonZero
      gradient = nil
      textStyle = NativeTextStyle(swiftSnapshot: snapshot)
      image = nil
      textureImageID = nil
      textureTileModeX = 0
      textureTileModeY = 0
      shaderMatrix = nil
      filterQuality = nil
      usesComponentGeometry = false
      offscreenTarget = nil
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
      typealias Floats = NativeRedrawEquality
      return lhs.kind == rhs.kind && Floats.same(lhs.values, rhs.values)
        && lhs.color == rhs.color && Floats.same(lhs.alpha, rhs.alpha)
        && Floats.same(lhs.strokeWidth, rhs.strokeWidth) && lhs.isStroke == rhs.isStroke
        && lhs.strokeCap == rhs.strokeCap && lhs.strokeJoin == rhs.strokeJoin
        && lhs.blendMode == rhs.blendMode && Floats.same(lhs.textSize, rhs.textSize)
        && Floats.same(lhs.textWeight, rhs.textWeight) && lhs.text == rhs.text
        && lhs.path == rhs.path && lhs.pathWinding == rhs.pathWinding
        && lhs.gradient == rhs.gradient && lhs.textStyle == rhs.textStyle
        && lhs.image == rhs.image && lhs.textureImageID == rhs.textureImageID
        && lhs.textureTileModeX == rhs.textureTileModeX
        && lhs.textureTileModeY == rhs.textureTileModeY
        && Floats.same(lhs.shaderMatrix, rhs.shaderMatrix)
        && lhs.filterQuality == rhs.filterQuality
        && lhs.usesComponentGeometry == rhs.usesComponentGeometry
        && lhs.offscreenTarget == rhs.offscreenTarget
    }

    /// The shader's local matrix as a Core Graphics affine transform, or identity when the paint
    /// never named one.
    var textureTransform: CGAffineTransform {
      NativeTexturePolicy.transform(shaderMatrix)
    }

    /// How Core Graphics should sample this paint's image or texture.
    var interpolationQuality: CGInterpolationQuality {
      NativeTexturePolicy.interpolationQuality(forFilterQuality: filterQuality)
    }
  }

  struct NativeImageDraw: Equatable {
    let imageID: Int
    let source: CGRect
    let destination: CGRect
    let scaleType: Int
    let scaleFactor: CGFloat
    let contentDescription: String?

  }

  struct NativeTextStyle: Equatable {
    let fontStyle: Int
    let fontFamilyID: Int
    let fontFamilyName: String?
    let alignment: NativeSwiftTextAlignment
    let overflow: Int
    let maxLines: Int
    let letterSpacing: CGFloat
    let lineHeightAdd: CGFloat
    let lineHeightMultiplier: CGFloat
    let breakStrategy: Int
    let hyphenation: Int
    let isJustified: Bool
    let isUnderlined: Bool
    let isStruckThrough: Bool

    static let `default` = NativeTextStyle(
      swiftSnapshot: NativeSwiftTextSnapshot(
        value: "", colorARGB: 0xff00_0000, size: 16, style: 0, weight: 400,
        familyID: -1, alignment: .left, overflow: NativeSwiftTextOverflow.clip,
        maximumLines: Int.max))

    init(swiftSnapshot snapshot: NativeSwiftTextSnapshot) {
      fontStyle = snapshot.style
      fontFamilyID = snapshot.familyID
      fontFamilyName = snapshot.familyName
      alignment = snapshot.alignment
      overflow = snapshot.overflow
      maxLines = snapshot.maximumLines
      letterSpacing = 0
      lineHeightAdd = 0
      lineHeightMultiplier = 1
      breakStrategy = 0
      hyphenation = 0
      isJustified = false
      isUnderlined = false
      isStruckThrough = false
    }
  }

  final class NativeDocumentView: UIView {
    private var document: NativeDocument
    private var resources: NativeResourceStore
    /// The measurements the current tree was refined from, so a settled layout stops re-resolving.
    private var appliedMeasurements: [Int: NativeSwiftMeasuredSize] = [:]
    private let customComponents: RemoteComposeNativeCustomComponentRegistry
    private let customComponentsRevision: UInt
    private let componentView: NativeComponentView
    /// The document's `DrawToBitmap` targets, shared by every canvas in the component tree so a
    /// bitmap id is one target for the whole document, as the core's resource budget counts it.
    private let offscreenTargets: NativeOffscreenTargets

    init(
      document: NativeDocument,
      resources: NativeResourceStore,
      customComponents: RemoteComposeNativeCustomComponentRegistry,
      onGesture: @escaping (Int, NativeSwiftGestureKind, NativeSwiftPointerSample?) -> Void,
      onCustomReturn: @escaping (Int, Int, NativeCustomReturnValue) -> Void
    ) {
      self.document = document
      self.resources = resources
      self.customComponents = customComponents
      customComponentsRevision = customComponents.revision
      let offscreenTargets = NativeOffscreenTargets()
      self.offscreenTargets = offscreenTargets
      componentView = NativeComponentView(
        node: document.root,
        images: resources.images,
        fontNames: resources.fontNames,
        offscreenTargets: offscreenTargets,
        customComponents: customComponents,
        onGesture: onGesture,
        onCustomReturn: onCustomReturn)
      super.init(frame: .zero)
      isOpaque = false
      backgroundColor = .clear
      addSubview(componentView)
      isAccessibilityElement = false
      publishAccessibilityElements()
      accessibilityIdentifier = "rc-native-document"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    func update(
      document: NativeDocument,
      resources: NativeResourceStore,
      customComponents: RemoteComposeNativeCustomComponentRegistry
    ) -> Bool {
      guard
        self.customComponents === customComponents,
        customComponentsRevision == customComponents.revision
      else { return false }
      guard
        componentView.canUpdate(
          with: document.root, images: resources.images, fontNames: resources.fontNames),
        componentView.update(
          node: document.root, images: resources.images, fontNames: resources.fontNames)
      else { return false }
      self.document = document
      self.resources = resources
      appliedMeasurements = [:]
      publishAccessibilityElements()
      setNeedsLayout()
      return true
    }

    /// The document is the one accessibility container: every semantic element it lists names it as
    /// its container, so VoiceOver and XCUITest walk the same parent chain they enumerate.
    ///
    /// Layout republishes it too, through the component views' refresh, because a container that
    /// hides or restores a child at layout changes which elements are listed.
    func publishAccessibilityElements() {
      let elements = componentView.accessibilityOrder
      for case let element as NativeSemanticElement in elements {
        element.accessibilityContainer = self
      }
      accessibilityElements = elements
    }

    override func layoutSubviews() {
      super.layoutSubviews()
      guard document.size.width > 0, document.size.height > 0 else {
        componentView.transform = .identity
        componentView.frame = bounds
        return
      }
      let root = NativeRootTransform.resolve(
        document: document.size,
        viewport: bounds.size,
        sizing: document.rootSizing,
        mode: document.rootMode,
        alignment: document.rootAlignment)
      componentView.transform = .identity
      componentView.layoutDensityScale = NativeDensityPolicy.layoutDensityScale(
        androidCompatibility: document.androidCompatibility,
        playbackDensityScale: root.scaleX > 0 ? 1 / root.scaleX : 1)
      componentView.bounds = CGRect(origin: .zero, size: document.size)
      componentView.center = CGPoint(
        x: root.translateX + document.size.width * root.scaleX / 2,
        y: root.translateY + document.size.height * root.scaleY / 2)
      componentView.transform = CGAffineTransform(scaleX: root.scaleX, y: root.scaleY)
      componentView.documentScale = 1
      componentView.layoutDirection =
        effectiveUserInterfaceLayoutDirection == .rightToLeft ? .rightToLeft : .leftToRight
      refineBoundGeometry()
      if var path = ProcessInfo.processInfo.environment["RC_NATIVE_DUMP_TREE"] {
        if !path.hasPrefix("/") {
          let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
          path = documents.first.map { $0.appendingPathComponent(path).path } ?? path
        }
        var lines: [String] = []
        componentView.dumpTree(depth: 0, into: &lines)
        let text = lines.joined(separator: "\n") + "\n"
        if let existing = FileHandle(forWritingAtPath: path) {
          existing.seekToEndOfFile()
          existing.write(Data(text.utf8))
          try? existing.close()
        } else {
          try? text.write(toFile: path, atomically: true, encoding: .utf8)
        }
      }
    }

    /// Re-resolves the document against the geometry its components were just laid out at.
    ///
    /// A document may bind a component's measured size to a float and derive geometry from it — an
    /// icon scaled by `componentWidth / 24`, say. The core has to resolve that binding before layout
    /// exists, and its estimate cannot match a host that applies density scaling and layout rules
    /// the core does not model. So the first pass draws from an estimate and this corrects it from
    /// the real thing.
    ///
    /// Once per layout, and only while the sizes keep changing. A refinement can change a size —
    /// that is the point — so this would otherwise oscillate; comparing against the sizes the
    /// current tree was built from is what terminates it.
    private func refineBoundGeometry() {
      guard !document.boundComponents.isEmpty, document.refiner != nil else { return }
      var measured: [Int: NativeSwiftMeasuredSize] = [:]
      componentView.collectMeasuredSizes(of: document.boundComponents, into: &measured)
      guard measured != appliedMeasurements else { return }
      guard let refined = document.refined(measuredComponents: measured),
        componentView.canUpdate(
          with: refined.root, images: resources.images, fontNames: resources.fontNames)
      else {
        // Remember what was measured even when the refinement is refused, so a document whose
        // refinement cannot be applied is not re-measured on every single layout pass.
        appliedMeasurements = measured
        return
      }
      appliedMeasurements = measured
      document = refined
      componentView.update(
        node: refined.root, images: resources.images, fontNames: resources.fontNames)
      setNeedsLayout()
    }
  }

  /// One UIView per Remote Compose component, with a deliberately small frame-based implementation
  /// of Box, Row, and Column. Structural content wrappers remain visible in the UIKit hierarchy but
  /// are transparent to layout.
  final class NativeComponentView: UIView, UIGestureRecognizerDelegate,
    @preconcurrency NativeLayoutItem
  {
    private var node: NativeNode
    private var canvasView: NativeCanvasView?
    private var textLabels: [NativeTextLabel]
    private var imageViews: [NativeImageView]
    private var customView: NativeCustomComponentView?
    private var componentChildren: [NativeComponentView]
    private var semanticElement: NativeSemanticElement?
    /// Set by a `FitBox` on its alternatives. The reference ignores an alternative's own visibility
    /// modifier (a document switches alternatives with it), so accessibility reads the box's choice
    /// rather than the modifier's.
    private var ignoresOwnVisibility = false
    // Repeated requests with the same constraint occur while rows and flows first determine
    // natural sizes and then place siblings. The cache deliberately keys only identical
    // constraints; a weighted child's final width remains a separate measurement.
    let layoutCache = NativeLayoutSizeCache()
    private let onGesture: (Int, NativeSwiftGestureKind, NativeSwiftPointerSample?) -> Void
    var documentScale: CGFloat = 1 {
      didSet {
        guard documentScale != oldValue else { return }
        invalidatePreferredSizes()
        canvasView?.documentScale = documentScale
        componentChildren.forEach { $0.documentScale = documentScale }
        setNeedsLayout()
      }
    }
    var layoutDensityScale: CGFloat = 1 {
      didSet {
        guard layoutDensityScale != oldValue else { return }
        invalidatePreferredSizes()
        componentChildren.forEach { $0.layoutDensityScale = layoutDensityScale }
        setNeedsLayout()
      }
    }
    var layoutDirection: NativeLayoutDirection = .leftToRight {
      didSet {
        guard layoutDirection != oldValue else { return }
        invalidatePreferredSizes()
        componentChildren.forEach { $0.layoutDirection = layoutDirection }
        setNeedsLayout()
      }
    }

    init(
      node: NativeNode,
      images: [Int: UIImage],
      fontNames: [Int: String],
      offscreenTargets: NativeOffscreenTargets,
      customComponents: RemoteComposeNativeCustomComponentRegistry,
      onGesture: @escaping (Int, NativeSwiftGestureKind, NativeSwiftPointerSample?) -> Void,
      onCustomReturn: @escaping (Int, Int, NativeCustomReturnValue) -> Void
    ) {
      self.node = node
      self.onGesture = onGesture
      let promotesText = node.kind == .text
      let promotesImage = node.kind == .image
      let drawingCommands = node.commands.filter {
        !(promotesText && $0.kind == NativeSwiftDrawKind.text)
          && !(promotesImage && $0.kind == NativeSwiftDrawKind.bitmap)
      }
      canvasView =
        drawingCommands.isEmpty
        ? nil
        : NativeCanvasView(
          commands: drawingCommands, images: images, fontNames: fontNames,
          offscreenTargets: offscreenTargets)
      textLabels =
        promotesText
        ? node.commands.enumerated().compactMap { index, command in
          guard command.kind == NativeSwiftDrawKind.text else { return nil }
          return NativeTextLabel(
            componentID: node.componentID, commandIndex: index, command: command,
            fontNames: fontNames)
        } : []
      imageViews =
        promotesImage
        ? node.commands.compactMap { command in
          guard command.kind == NativeSwiftDrawKind.bitmap, let draw = command.image,
            let image = images[draw.imageID]
          else {
            return nil
          }
          return NativeImageView(
            image: image, draw: draw, alpha: command.alpha,
            filterQuality: command.filterQuality)
        } : []
      customView = node.custom.flatMap {
        NativeCustomComponentView(
          snapshot: $0, componentID: node.componentID, registry: customComponents,
          onReturn: onCustomReturn)
      }
      componentChildren = node.children.map {
        NativeComponentView(
          node: $0, images: images, fontNames: fontNames,
          offscreenTargets: offscreenTargets,
          customComponents: customComponents, onGesture: onGesture,
          onCustomReturn: onCustomReturn)
      }
      super.init(frame: .zero)
      semanticElement = makeSemanticElement()
      isOpaque = false
      backgroundColor = node.backgroundColor ?? .clear
      isHidden = node.visibility == NativeSwiftVisibility.gone
      alpha = node.visibility == NativeSwiftVisibility.invisible ? 0 : 1
      // A scrolled container's children are laid out against their content, which is larger than the
      // viewport by design, so the viewport has to clip them or the overflow paints outside it.
      // A marquee's content is laid out wider than its box, as a scroll's is, so both clip.
      clipsToBounds =
        node.cornerRadius > 0 || node.clipsToBounds || node.scrollDirection != nil
        || node.layout.marquee
      accessibilityIdentifier = "rc-native-component-\(node.componentID)"
      if let canvasView { addSubview(canvasView) }
      textLabels.forEach(addSubview)
      imageViews.forEach(addSubview)
      if let customView { addSubview(customView) }
      componentChildren.forEach(addSubview)
      componentChildren.forEach { $0.layer.zPosition = $0.node.zIndex }
      installGestureRecognizers()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    private func installGestureRecognizers() {
      var doubleTapRecognizer: UITapGestureRecognizer?
      if node.gestureTypes.contains(.doubleTap) {
        let recognizer = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
        recognizer.numberOfTapsRequired = 2
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        doubleTapRecognizer = recognizer
      }
      if node.gestureTypes.contains(.tap) {
        let recognizer = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        recognizer.delegate = self
        if let doubleTapRecognizer { recognizer.require(toFail: doubleTapRecognizer) }
        addGestureRecognizer(recognizer)
      }
      if node.gestureTypes.contains(.longPress) {
        let recognizer = UILongPressGestureRecognizer(
          target: self, action: #selector(handleLongPress(_:)))
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
      }
      let pointerGestures: Set<NativeSwiftGestureKind> = [.touchDown, .touchUp, .touchCancel]
      if node.gestureTypes.contains(where: pointerGestures.contains) {
        let recognizer = UILongPressGestureRecognizer(
          target: self, action: #selector(handlePointerLifecycle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
      }
    }

    @objc private func handleTap(_ recognizer: UITapGestureRecognizer) {
      guard recognizer.state == .recognized else { return }
      onGesture(node.componentID, .tap, pointerSample(recognizer))
    }

    func gestureRecognizer(
      _ gestureRecognizer: UIGestureRecognizer,
      shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
      true
    }

    @objc private func handleLongPress(_ recognizer: UILongPressGestureRecognizer) {
      guard recognizer.state == .began else { return }
      onGesture(node.componentID, .longPress, pointerSample(recognizer))
    }

    @objc private func handleDoubleTap(_ recognizer: UITapGestureRecognizer) {
      guard recognizer.state == .recognized else { return }
      onGesture(node.componentID, .doubleTap, pointerSample(recognizer))
    }

    @objc private func handlePointerLifecycle(_ recognizer: UILongPressGestureRecognizer) {
      let sample = pointerSample(recognizer)
      switch recognizer.state {
      case .began:
        if node.gestureTypes.contains(.touchDown) {
          onGesture(node.componentID, .touchDown, sample)
        }
      case .ended:
        if node.gestureTypes.contains(.touchUp) { onGesture(node.componentID, .touchUp, sample) }
      case .cancelled, .failed:
        if node.gestureTypes.contains(.touchCancel) {
          onGesture(node.componentID, .touchCancel, sample)
        }
      default: break
      }
    }

    private func pointerSample(
      _ recognizer: UIGestureRecognizer, velocity: CGPoint = .zero
    ) -> NativeSwiftPointerSample {
      let point = recognizer.location(in: self)
      return NativeSwiftPointerSample(
        x: Float(point.x), y: Float(point.y), velocityX: Float(velocity.x),
        velocityY: Float(velocity.y))
    }

    func canUpdate(
      with next: NativeNode,
      images: [Int: UIImage],
      fontNames: [Int: String]
    ) -> Bool {
      guard node.componentID == next.componentID, node.kind == next.kind else { return false }
      let local = Self.localContent(for: next, images: images)
      guard
        (canvasView != nil) == !local.drawing.isEmpty,
        node.gestureTypes == next.gestureTypes,
        textLabels.count == local.text.count,
        imageViews.count == local.images.count,
        node.custom?.config == next.custom?.config,
        // Whether the node has an element depends only on its own semantics; the element's kind
        // and label are re-resolved from the displayed views on every update.
        (semanticElement != nil) == (next.semanticDescriptor != nil),
        componentChildren.count == next.children.count
      else { return false }
      return zip(componentChildren, next.children).allSatisfy { child, childNode in
        child.canUpdate(with: childNode, images: images, fontNames: fontNames)
      }
    }

    /// Updates this view in place and returns true, or returns false without changing anything when
    /// `next` cannot be applied here, so the caller rebuilds instead. Callers check `canUpdate`
    /// first; a refusal here is a bug, but not one worth terminating a release build over.
    @discardableResult
    func update(node next: NativeNode, images: [Int: UIImage], fontNames: [Int: String]) -> Bool {
      guard canUpdate(with: next, images: images, fontNames: fontNames) else {
        assertionFailure("update(node:) called with a node canUpdate(with:) rejects")
        return false
      }
      let local = Self.localContent(for: next, images: images)
      node = next
      // A FitBox parent sets this again during its layout, exactly as it does on a fresh view.
      ignoresOwnVisibility = false
      invalidatePreferredSizes()
      canvasView?.update(commands: local.drawing, images: images, fontNames: fontNames)
      zip(textLabels, local.text).forEach { label, command in
        label.update(command: command, fontNames: fontNames)
      }
      zip(imageViews, local.images).forEach { imageView, item in
        imageView.update(
          image: item.image, draw: item.draw, alpha: item.alpha,
          filterQuality: item.filterQuality)
      }
      if let custom = next.custom { customView?.update(custom) }
      zip(componentChildren, next.children).forEach { child, childNode in
        child.update(node: childNode, images: images, fontNames: fontNames)
        child.layer.zPosition = childNode.zIndex
      }
      // After the children: a merging or unlabeled node resolves its label, role and action from
      // its descendants' views, which now hold the new node.
      configureSemanticElement()
      backgroundColor = next.backgroundColor ?? .clear
      isHidden = next.visibility == NativeSwiftVisibility.gone
      alpha = next.visibility == NativeSwiftVisibility.invisible ? 0 : 1
      clipsToBounds =
        next.cornerRadius > 0 || next.clipsToBounds || next.scrollDirection != nil
        || next.layout.marquee
      setNeedsLayout()
      return true
    }

    private static func localContent(
      for node: NativeNode,
      images: [Int: UIImage]
    ) -> (
      drawing: [NativeDrawCommand], text: [NativeDrawCommand],
      images: [(image: UIImage, draw: NativeImageDraw, alpha: CGFloat, filterQuality: Int?)]
    ) {
      let promotesText = node.kind == .text
      let promotesImage = node.kind == .image
      let drawing = node.commands.filter {
        !(promotesText && $0.kind == NativeSwiftDrawKind.text)
          && !(promotesImage && $0.kind == NativeSwiftDrawKind.bitmap)
      }
      let text = promotesText ? node.commands.filter { $0.kind == NativeSwiftDrawKind.text } : []
      let imageItems =
        promotesImage
        ? node.commands.compactMap {
          command -> (UIImage, NativeImageDraw, CGFloat, Int?)? in
          guard command.kind == NativeSwiftDrawKind.bitmap, let draw = command.image,
            let image = images[draw.imageID]
          else { return nil }
          return (image, draw, command.alpha, command.filterQuality)
        } : []
      return (drawing, text, imageItems)
    }

    var accessibilityOrder: [Any] {
      // The *effective* state, not the document's field: a FitBox displays an alternative whose own
      // visibility modifier is GONE, and a displayed button has to be reachable by VoiceOver rather
      // than filtered out by the modifier the box deliberately ignored.
      guard !isHidden, alpha > 0.01 else { return [] }
      let descendants = componentChildren.flatMap(\.accessibilityOrder)
      let local: [Any] =
        textLabels.filter(\.isAccessibilityElement).map { $0 as Any }
        + imageViews.filter(\.isAccessibilityElement).map { $0 as Any }
        + (customView.map { [$0 as Any] } ?? [])
      guard let semanticElement, let descriptor = semanticBehavior?.descriptor else {
        return local + descendants
      }
      let owner = semanticElement.isAccessibilityElement ? [semanticElement] : []
      return descriptor.hidesDescendants ? owner : owner + local + descendants
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
      guard isUserInteractionEnabled, !isHidden, alpha > 0.01 else { return nil }
      let isInside = self.point(inside: point, with: event)
      if clipsToBounds, !isInside { return nil }
      if clipsToBounds, layer.cornerRadius > 0,
        !UIBezierPath(roundedRect: bounds, cornerRadius: layer.cornerRadius).contains(point)
      {
        return nil
      }

      // Core Animation zPosition controls painting, while UIView.hitTest normally only observes
      // subview insertion order. Follow the rendered ordering explicitly so overlapping Remote
      // Compose components activate the same top-most component that the user sees.
      let frontToBack = componentChildren.enumerated().sorted { left, right in
        if left.element.layer.zPosition == right.element.layer.zPosition {
          return left.offset > right.offset
        }
        return left.element.layer.zPosition > right.element.layer.zPosition
      }
      for (_, child) in frontToBack {
        if let hit = child.hitTest(convert(point, to: child), with: event) { return hit }
      }
      if isInside, let customView,
        let hit = customView.hitTest(convert(point, to: customView), with: event)
      {
        return hit
      }
      return isInside && !node.gestureTypes.isEmpty ? self : nil
    }

    /// MODIFIER_GRAPHICS_LAYER's 2D attributes.
    ///
    /// The origin is the part that needed deciding. No document on the catalog sheet carries
    /// `TRANSFORM_ORIGIN`, so the default decides every transform on it, and the declared
    /// `remote-core` default -- 0, the top-left -- is not what any AndroidX lane actually draws for
    /// an absent attribute. The core reports an absent origin as the centre (0.5, 0.5), and
    /// `graphicsLayerTransformOrigin` below carries the derivation and the measurement.
    ///
    /// CALayer applies `transform` about its own anchor point, so the document's origin has to be
    /// folded into the matrix rather than assumed. For a linear part S and an anchor at A, the
    /// transform that behaves as if applied about p is S with a translation of (I - S)(p - A).
    /// Recomputed from `bounds` on every layout pass, so it stays correct through resizes and
    /// never compounds.
    ///
    /// ### The transform origin
    ///
    /// The snapshot carries `TRANSFORM_ORIGIN_X`/`_Y` (ids 5/6) as fractions of the component's
    /// size, the way Compose's `TransformOrigin` reads them, and 0 when the document does not
    /// state one.
    ///
    /// That is `remote-core`'s declared default and what AndroidX's embedded Compose player reads.
    /// This used to centre an absent origin (#155, #153), because writers before androidx-main
    /// `4969cdd96c6` omitted the attribute at `0.5f`. The current writer writes a centre origin
    /// explicitly and omits only `0f`, so the players now follow AndroidX rather than guess.
    private static func graphicsLayerTransformOrigin(
      of graphicsLayer: NativeSwiftGraphicsLayerSnapshot, in size: CGSize
    ) -> CGPoint {
      CGPoint(
        x: CGFloat(graphicsLayer.transformOriginX) * size.width,
        y: CGFloat(graphicsLayer.transformOriginY) * size.height)
    }

    private func applyGraphicsLayer() {
      guard let graphicsLayer = node.graphicsLayer, !graphicsLayer.isIdentity else { return }
      let anchor = CGPoint(
        x: layer.anchorPoint.x * bounds.width, y: layer.anchorPoint.y * bounds.height)
      let linear = CGAffineTransform(
        rotationAngle: CGFloat(graphicsLayer.rotationZ) * .pi / 180
      ).scaledBy(x: CGFloat(graphicsLayer.scaleX), y: CGFloat(graphicsLayer.scaleY))
      let origin = Self.graphicsLayerTransformOrigin(of: graphicsLayer, in: bounds.size)
      let dx = origin.x - anchor.x, dy = origin.y - anchor.y
      let originX = dx - (linear.a * dx + linear.c * dy)
      let originY = dy - (linear.b * dx + linear.d * dy)
      layer.transform = CATransform3DMakeAffineTransform(
        linear.concatenating(
          CGAffineTransform(
            translationX: originX + CGFloat(graphicsLayer.translationX),
            y: originY + CGFloat(graphicsLayer.translationY))))
      if graphicsLayer.alpha != 1, node.visibility != NativeSwiftVisibility.invisible {
        alpha = CGFloat(max(0, min(1, graphicsLayer.alpha)))
      }
    }

    /// The laid-out size of each named component, gathered from this subtree.
    /// This view's size, when layout actually gave it one.
    ///
    /// Empty bounds mean layout never sized this view — a structural view is flattened into its
    /// parent's arrangement and never given a frame. Real bounds are trusted whether the view is
    /// structural or not: distrusting them cost twelve exact matches when tried, because plenty of
    /// structural views are sized perfectly well and only the unsized ones need an ancestor's size.
    private var laidOutSize: CGSize? {
      bounds.isEmpty ? nil : bounds.size
    }

    /// Prints this subtree's laid-out geometry, for comparing against the reference player's.
    ///
    /// Gated on `RC_NATIVE_DUMP_TREE` because it is a diagnostic, not a feature: the reference
    /// publishes the same information through Compose semantics, and the two dumps side by side are
    /// how a layout divergence gets localised to one component.
    func dumpTree(depth: Int, into lines: inout [String]) {
      let indent = String(repeating: "  ", count: depth)
      let f = frame
      lines.append(
        "\(indent)id=\(node.componentID) \(node.kind) "
          + "pos=(\(Int(f.minX)),\(Int(f.minY))) size=\(Int(f.width))x\(Int(f.height))"
          + " w=\(node.widthType)/\(node.widthValue) h=\(node.heightType)/\(node.heightValue)"
          + " wIn=\(node.minimumWidth)..\(String(describing: node.maximumWidth))"
          + " hIn=\(node.minimumHeight)..\(String(describing: node.maximumHeight))"
          + " structural=\(isStructural)")
      for child in componentChildren { child.dumpTree(depth: depth + 1, into: &lines) }
    }

    func collectMeasuredSizes(
      of wanted: Set<Int>, into result: inout [Int: NativeSwiftMeasuredSize],
      inherited: CGSize? = nil
    ) {
      if wanted.contains(node.componentID) {
        // A structural view is transparent to layout: its children are flattened into its parent's
        // arrangement and it is never given a frame of its own, so its bounds are not a size. The
        // size such a component *has* is the area it was flattened into, which is the nearest
        // ancestor that layout did size. Reporting its own empty bounds instead resolves the binding
        // to zero, and a scale of zero draws exactly as little as the pre-layout estimate did.
        let measurable = laidOutSize ?? inherited
        if let measurable {
          result[node.componentID] = NativeSwiftMeasuredSize(
            width: Float(measurable.width), height: Float(measurable.height))
        }
      }
      let forChildren = laidOutSize ?? inherited
      for child in componentChildren {
        child.collectMeasuredSizes(of: wanted, into: &result, inherited: forChildren)
      }
    }

    override func layoutSubviews() {
      super.layoutSubviews()
      applyGraphicsLayer()
      layer.cornerRadius = min(
        node.cornerRadius * layoutUnitScale,
        max(min(bounds.width, bounds.height) / 2, 0))
      canvasView?.frame = bounds
      prepareStructuralChildren()
      if isStructural {
        updateStructuralSemanticFrames()
        return
      }

      switch node.kind {
      case .text:
        textLabels.forEach {
          $0.layoutInComponent(
            bounds: bounds, documentScale: documentScale, layoutDirection: layoutDirection)
        }
      case .image: imageViews.forEach { $0.frame = bounds }
      case .custom: customView?.frame = bounds
      // Rows, columns, flows, boxes, FitBoxes and the root: the shared engine decides, this view
      // only writes the result onto its children.
      default: apply(layoutEngine.arrange(self, in: bounds))
      }
      semanticElement?.frameInOwner = bounds
      updateStructuralSemanticFrames()
    }

    // MARK: NativeLayoutItem

    var layoutNode: NativeLayoutNode { node.layout }

    var layoutChildren: [NativeComponentView] { componentChildren }

    /// What this view draws itself, for the engine's text, image and custom measurements.
    func layoutContentSize(fitting available: CGSize) -> CGSize {
      switch node.kind {
      case .text:
        // Measuring only reads the label, so the scale it is measured at is applied first.
        guard let label = textLabels.first else { return .zero }
        label.apply(documentScale: documentScale)
        return label.preferredSize(maximumWidth: available.width)
      case .image:
        return imageViews.first?.image?.size ?? .zero
      case .custom:
        // A registered custom view can change its fitting size independently of the decoded node
        // (asynchronous content and internal state are both normal), which is why the engine never
        // caches a measurement of it or of any ancestor.
        return customView?.sizeThatFits(available) ?? .zero
      default:
        return .zero
      }
    }

    /// The shared layout engine, at this view's scales and direction.
    private var layoutEngine: NativeLayoutEngine {
      NativeLayoutEngine(
        context: NativeLayoutContext(
          documentScale: documentScale,
          layoutDensityScale: layoutDensityScale,
          layoutUnitScale: layoutUnitScale,
          dimensionConstraintScale: dimensionConstraintScale,
          layoutDirection: layoutDirection))
    }

    /// Writes an arrangement the engine produced for this container onto its children.
    private func apply(_ arrangement: NativeLayoutArrangement<NativeComponentView>) {
      // Semantic elements resolve from the displayed views, so any change layout makes to what is
      // displayed (a collapsed or restored child, a flow's discarded item, a FitBox's choice) is
      // re-resolved here rather than at the next document update.
      var changedItems: [NativeComponentView] = []
      for change in arrangement.visibilityChanges {
        var changed = false
        if change.item.isHidden != change.isHidden {
          change.item.isHidden = change.isHidden
          changed = true
        }
        if change.resetsAlpha { change.item.alpha = 1 }
        if change.isFitBoxAlternative, !change.item.ignoresOwnVisibility {
          change.item.ignoresOwnVisibility = true
          changed = true
        }
        if changed { changedItems.append(change.item) }
      }
      var containerChanged = false
      if let containerIsHidden = arrangement.containerIsHidden, isHidden != containerIsHidden {
        isHidden = containerIsHidden
        containerChanged = true
      }
      for placement in arrangement.placements { placement.item.frame = placement.frame }
      guard !changedItems.isEmpty || containerChanged else { return }
      // An arranged item can sit below a structural wrapper, whose own element also resolves from
      // it, so the path from each changed item up to this container is refreshed too.
      var refreshed = Set<ObjectIdentifier>()
      for item in changedItems {
        var view = item.superview
        while let current = view, current !== self {
          if let component = current as? NativeComponentView,
            refreshed.insert(ObjectIdentifier(component)).inserted
          {
            component.configureSemanticElement()
          }
          view = current.superview
        }
      }
      refreshSemanticElements()
    }

    /// Re-resolves every semantic element from here up: a merging ancestor's label, role and
    /// action come from its displayed descendants. The document then republishes the elements it
    /// lists, since what is displayed decides that too.
    func refreshSemanticElements() {
      var view: UIView? = self
      while let current = view {
        if let component = current as? NativeComponentView {
          component.configureSemanticElement()
        } else if let document = current as? NativeDocumentView {
          document.publishAccessibilityElements()
          return
        }
        view = current.superview
      }
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
      super.traitCollectionDidChange(previousTraitCollection)
      guard traitCollection != previousTraitCollection else { return }
      invalidatePreferredSizes()
      setNeedsLayout()
    }

    private func invalidatePreferredSizes() {
      layoutCache.removeAll()
    }

    private var isStructural: Bool {
      NativeLayoutEngine.isStructural(node.layout)
    }

    private var flattenedLayoutItems: [NativeComponentView] {
      NativeLayoutEngine.flattenedLayoutItems(of: self)
    }

    private var layoutUnitScale: CGFloat {
      node.densityBehavior == NativeDensityPolicy.dpBehavior ? layoutDensityScale : documentScale
    }

    /// What `widthIn`/`heightIn` bounds are measured in — see `NativeDensityPolicy`.
    private var dimensionConstraintScale: CGFloat {
      NativeDensityPolicy.dimensionConstraintScale(
        densityBehavior: node.densityBehavior,
        layoutDensityScale: layoutDensityScale,
        documentScale: documentScale)
    }

    private func prepareStructuralChildren() {
      componentChildren.forEach { child in
        if child.isStructural {
          child.frame = bounds
          child.prepareStructuralChildren()
        }
      }
    }

    private func updateStructuralSemanticFrames() {
      componentChildren.forEach { $0.updateStructuralSemanticFrames() }
      guard isStructural, let semanticElement else { return }
      let renderedBounds =
        flattenedLayoutItems
        .filter { !$0.isHidden && $0.alpha > 0.01 }
        .map { convert($0.bounds, from: $0) }
        .filter { !$0.isEmpty && !$0.isNull }
        .reduce(CGRect.null) { $0.union($1) }
      let clippedBounds = renderedBounds.intersection(bounds)
      semanticElement.frameInOwner = clippedBounds.isNull ? .zero : clippedBounds
    }

    private func makeSemanticElement() -> NativeSemanticElement? {
      guard let behavior = semanticBehavior else { return nil }
      let element = NativeSemanticElement(
        owner: self, kind: behavior.descriptor.elementKind, componentID: behavior.componentID)
      configure(element, behavior: behavior)
      return element
    }

    private func configureSemanticElement() {
      guard let semanticElement, let behavior = semanticBehavior else { return }
      configure(semanticElement, behavior: behavior)
    }

    /// Writes the semantics this view resolves from what it and its descendants display onto its
    /// element. Activation dispatches the same tap the pointer path does, for this component or,
    /// for a merging node whose action belongs to a descendant, that descendant's.
    private func configure(
      _ element: NativeSemanticElement, behavior: NativeAccessibilityBehavior
    ) {
      let descriptor = behavior.descriptor
      let onGesture = onGesture
      let action: ((Int) -> Void)? =
        behavior.activatesTap ? { componentID in onGesture(componentID, .tap, nil) } : nil
      element.kind = descriptor.elementKind
      element.componentID = behavior.componentID
      element.action = action
      // An image never gated activation on enablement; every control-backed kind did.
      element.requiresEnabledToActivate = descriptor.elementKind != .image
      element.isEnabled = descriptor.isEnabled
      let label = resolvedSemanticLabel
      let traits = Self.accessibilityTraits(for: descriptor)
      element.isAccessibilityElement =
        label != nil || descriptor.stateDescription != nil || !traits.isEmpty || action != nil
      element.accessibilityLabel = label
      element.accessibilityValue = descriptor.stateDescription
      element.accessibilityTraits = traits
      element.accessibilityIdentifier =
        "rc-native-\(String(describing: descriptor.elementKind))-\(node.componentID)"
    }

    private static func accessibilityTraits(
      for descriptor: NativeAccessibilityDescriptor
    ) -> UIAccessibilityTraits {
      var traits: UIAccessibilityTraits
      switch descriptor.elementKind {
      case .button, .checkbox, .toggle, .radioButton, .tab, .dropdownList: traits = .button
      case .image: traits = .image
      case .picker, .carousel, .generic: traits = []
      }
      if descriptor.isClickable { traits.insert(.button) }
      if !descriptor.isEnabled { traits.insert(.notEnabled) }
      return traits
    }
  }

  /// A component's VoiceOver identity: role, label, value and activation, without a view.
  ///
  /// The document owns every pixel and every pointer gesture, so the semantic node is a plain
  /// accessibility element rather than a control overlaid on the component. It announces the
  /// owning component view's area (the structural union for a flattened component), and activation
  /// dispatches the same tap event the pointer path does.
  private final class NativeSemanticElement: UIAccessibilityElement {
    var kind: NativeAccessibilityElementKind
    var componentID: Int
    var action: ((Int) -> Void)?
    var isEnabled = true
    var requiresEnabledToActivate = true
    /// The area this element announces, in `owner`'s coordinate space.
    var frameInOwner: CGRect = .zero
    private weak var owner: UIView?

    init(owner: UIView, kind: NativeAccessibilityElementKind, componentID: Int) {
      self.owner = owner
      self.kind = kind
      self.componentID = componentID
      super.init(accessibilityContainer: owner)
    }

    /// Resolved on every read so transforms, scrolled ancestors and the document's root scale are
    /// always current; a frame cached in container space would go stale when an ancestor moves
    /// without this component being laid out again.
    override var accessibilityFrame: CGRect {
      get {
        guard let owner, owner.window != nil else { return .zero }
        return UIAccessibility.convertToScreenCoordinates(frameInOwner, in: owner)
      }
      set {}
    }

    override var accessibilityValue: String? {
      get {
        // UISwitch reported its on-state ("0": the overlay was never switched on) whenever the
        // document carried no state description of its own.
        if kind == .toggle, super.accessibilityValue == nil { return "0" }
        return super.accessibilityValue
      }
      set { super.accessibilityValue = newValue }
    }

    override var accessibilityTraits: UIAccessibilityTraits {
      get {
        var traits = super.accessibilityTraits
        if kind == .toggle, #available(iOS 17.0, *) { traits.insert(.toggleButton) }
        return traits
      }
      set { super.accessibilityTraits = newValue }
    }

    override func accessibilityActivate() -> Bool {
      guard !requiresEnabledToActivate || isEnabled, let action else { return false }
      action(componentID)
      return true
    }
  }

  final class NativeImageView: UIImageView {
    private var drawCommand: NativeImageDraw
    private var filterQuality: Int?

    init(image: UIImage, draw: NativeImageDraw, alpha: CGFloat, filterQuality: Int?) {
      drawCommand = draw
      self.filterQuality = filterQuality
      super.init(image: image)
      self.alpha = alpha
      clipsToBounds = true
      contentMode = .redraw
      isAccessibilityElement = draw.contentDescription != nil
      accessibilityLabel = draw.contentDescription
      accessibilityIdentifier = "rc-native-image-\(draw.imageID)"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    func update(
      image: UIImage, draw: NativeImageDraw, alpha: CGFloat, filterQuality: Int?
    ) {
      self.image = image
      drawCommand = draw
      self.filterQuality = filterQuality
      self.alpha = alpha
      isAccessibilityElement = draw.contentDescription != nil
      accessibilityLabel = draw.contentDescription
      accessibilityIdentifier = "rc-native-image-\(draw.imageID)"
      setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
      guard let image else { return }
      let source = CGRect(origin: .zero, size: image.size)
      let destination = NativeImageGeometry.destination(
        source: source,
        destination: bounds,
        scaleType: drawCommand.scaleType,
        scaleFactor: drawCommand.scaleFactor)
      UIGraphicsGetCurrentContext()?.interpolationQuality =
        NativeTexturePolicy.interpolationQuality(forFilterQuality: filterQuality)
      image.draw(in: destination)
    }
  }

  /// A Remote Compose text primitive promoted to a real UIKit text element. Geometry and font
  /// selection remain approximate until the native lane has a resolved layout/text profile.
  ///
  /// Configuring and measuring are separate steps. `apply(documentScale:)` builds the font and
  /// attributed string the label draws; `preferredSize(maximumWidth:)` only reads them, so a
  /// measurement never changes what the label shows. The owner applies the scale before it
  /// measures, at the same points in layout where measuring used to configure the label itself.
  final class NativeTextLabel: UILabel {
    private var command: NativeDrawCommand
    private var fontNames: [Int: String]
    private var documentScale: CGFloat = 1
    private var layoutDirection: NativeLayoutDirection = .leftToRight
    // Text labels are measured while determining their parent's natural size and again after row
    // and flow layouts assign their final width. Keep the TextKit result only for an identical
    // width and drop every entry whenever the attributed string or its line limits change, so the
    // width is the whole key. Bounded: a label measured at many widths starts over, not grows.
    private var measurementCache: [CGFloat: CGSize] = [:]
    private static let measurementCacheLimit = 16
    /// The TextKit stack the current text is measured with, built at the first measurement after
    /// the text changes and reused for every width until it changes again.
    private var textLayout: NativeTextKitLayout?
    private var configuredTextScale: CGFloat?
    private var configuredInterfaceDirection: UIUserInterfaceLayoutDirection?
    private var configuredParagraph = false

    init(
      componentID: Int, commandIndex: Int, command: NativeDrawCommand,
      fontNames: [Int: String]
    ) {
      self.command = command
      self.fontNames = fontNames
      super.init(frame: .zero)
      text = command.text
      textColor = command.color.withAlphaComponent(command.alpha)
      backgroundColor = .clear
      configureParagraph(layoutDirection: .leftToRight)
      adjustsFontForContentSizeCategory = true
      isAccessibilityElement = true
      accessibilityIdentifier = "rc-native-text-\(componentID)-\(commandIndex)"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    func update(command: NativeDrawCommand, fontNames: [Int: String]) {
      self.command = command
      self.fontNames = fontNames
      text = command.text
      textColor = command.color.withAlphaComponent(command.alpha)
      invalidateTextLayout()
      configureParagraph(layoutDirection: layoutDirection)
      configureFont(documentScale: documentScale)
      setNeedsLayout()
    }

    /// Builds the label's font and attributed string at `documentScale`, unless they were already
    /// built at that scale and the current layout direction. Call it before `preferredSize`.
    func apply(documentScale: CGFloat) {
      self.documentScale = documentScale
      configureFont(documentScale: documentScale)
    }

    /// The size the label's configured text takes within `maximumWidth`, laid out by TextKit 1
    /// with no line-fragment padding, the label's line cap and its line-break mode.
    ///
    /// Reads the label and changes nothing a caller can observe: it does not configure the font
    /// or the attributed string (see `apply(documentScale:)`), and only fills its own cache.
    func preferredSize(maximumWidth: CGFloat) -> CGSize {
      guard let attributedText, maximumWidth > 0 else { return .zero }
      if let cached = measurementCache[maximumWidth] { return cached }
      let layout =
        textLayout
        ?? NativeTextKitLayout(
          text: attributedText, maximumNumberOfLines: numberOfLines,
          lineBreakMode: lineBreakMode)
      textLayout = layout
      let measured = layout.usedRect(width: maximumWidth)
      let size = CGSize(
        width: min(ceil(measured.width), maximumWidth),
        height: ceil(measured.height))
      if measurementCache.count >= Self.measurementCacheLimit {
        measurementCache.removeAll(keepingCapacity: true)
      }
      measurementCache[maximumWidth] = size
      return size
    }

    func layoutInComponent(
      bounds: CGRect, documentScale: CGFloat, layoutDirection: NativeLayoutDirection
    ) {
      configureParagraph(layoutDirection: layoutDirection)
      apply(documentScale: documentScale)
      let preferred = preferredSize(maximumWidth: bounds.width)
      frame = CGRect(
        origin: .zero,
        size: CGSize(width: bounds.width, height: min(preferred.height, bounds.height)))
    }

    private func configureFont(documentScale: CGFloat) {
      let interfaceDirection = effectiveUserInterfaceLayoutDirection
      guard
        configuredTextScale != documentScale
          || configuredInterfaceDirection != interfaceDirection
      else { return }
      font = NativeTextAttributes.font(
        for: command, scale: documentScale, fontNames: fontNames, scalesForDynamicType: true)
      attributedText = NativeTextAttributes.string(
        for: command, font: font, scale: documentScale,
        layoutDirection: interfaceDirection)
      configuredTextScale = documentScale
      configuredInterfaceDirection = interfaceDirection
      discardMeasurements()
    }

    private func configureParagraph(layoutDirection: NativeLayoutDirection) {
      let changed = !configuredParagraph || self.layoutDirection != layoutDirection
      self.layoutDirection = layoutDirection
      let style = command.textStyle
      numberOfLines = NativeTextPolicy.numberOfLines(
        overflow: style.overflow, maximum: style.maxLines)
      switch NativeTextPolicy.lineBreak(overflow: style.overflow) {
      case .clip: lineBreakMode = .byClipping
      case .wordWrap: lineBreakMode = .byWordWrapping
      case .tail: lineBreakMode = .byTruncatingTail
      case .head: lineBreakMode = .byTruncatingHead
      case .middle: lineBreakMode = .byTruncatingMiddle
      }
      clipsToBounds = style.overflow != NativeSwiftTextOverflow.visible
      semanticContentAttribute =
        layoutDirection == .rightToLeft ? .forceRightToLeft : .forceLeftToRight
      configuredParagraph = true
      if changed { invalidateTextLayout() }
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
      super.traitCollectionDidChange(previousTraitCollection)
      guard traitCollection.preferredContentSizeCategory
        != previousTraitCollection?.preferredContentSizeCategory
      else { return }
      invalidateTextLayout()
      setNeedsLayout()
    }

    private func invalidateTextLayout() {
      configuredTextScale = nil
      configuredInterfaceDirection = nil
      discardMeasurements()
    }

    /// Forgets every measurement and the stack they came from, for a new string or line limits.
    private func discardMeasurements() {
      measurementCache.removeAll(keepingCapacity: true)
      textLayout = nil
    }
  }

  /// One TextKit 1 stack for one attributed string, laid out at whatever width is asked.
  ///
  /// Built exactly as the label used to build one per measurement — the storage from the string,
  /// a layout manager, and a container without line-fragment padding capped at the label's line
  /// count and line-break mode — and then reused across widths: setting the container's size
  /// invalidates its layout, so each width is laid out afresh. A new string gets a new stack
  /// rather than an edit of this one, so text-storage editing never enters the measurement.
  @MainActor final class NativeTextKitLayout {
    private let storage: NSTextStorage
    private let manager: NSLayoutManager
    private let container: NSTextContainer

    init(text: NSAttributedString, maximumNumberOfLines: Int, lineBreakMode: NSLineBreakMode) {
      storage = NSTextStorage(attributedString: text)
      manager = NSLayoutManager()
      container = NSTextContainer(size: .zero)
      container.lineFragmentPadding = 0
      container.maximumNumberOfLines = maximumNumberOfLines
      container.lineBreakMode = lineBreakMode
      manager.addTextContainer(container)
      storage.addLayoutManager(manager)
    }

    /// The rectangle the text uses laid out within `width` and an unbounded height.
    func usedRect(width: CGFloat) -> CGRect {
      container.size = CGSize(width: width, height: .greatestFiniteMagnitude)
      manager.ensureLayout(for: container)
      return manager.usedRect(for: container)
    }
  }

  enum NativeTextAttributes {
    static func font(
      for command: NativeDrawCommand,
      scale: CGFloat,
      fontNames: [Int: String] = [:],
      scalesForDynamicType: Bool = false
    ) -> UIFont {
      let weightValue =
        command.textStyle.fontStyle & 1 != 0 ? max(command.textWeight, 700) : command.textWeight
      let normalizedWeight = min(max((weightValue - 400) / 500, -1), 1)
      let size = max(command.textSize * scale, 1)
      let base = UIFont.systemFont(ofSize: size, weight: UIFont.Weight(rawValue: normalizedWeight))
      var descriptor = base.fontDescriptor
      let familyName = command.textStyle.fontFamilyName
      switch familyName?.lowercased() {
      case "serif", "apple:serif", "apple:new york":
        descriptor = descriptor.withDesign(.serif) ?? descriptor
      case "monospace", "apple:monospaced", "apple:sf mono":
        descriptor = descriptor.withDesign(.monospaced) ?? descriptor
      case "sans-serif", "default", "apple:system", "apple:sf pro", "apple:sf pro text",
        "apple:sf pro display", nil:
        break
      default:
        if let familyName,
          familyName.lowercased().hasPrefix("apple:"),
          let local = localFontDescriptor(
            familyName: String(familyName.dropFirst("apple:".count)), size: size)
        {
          descriptor = weightedDescriptor(local, weight: weightValue, size: size)
        }
      }
      if let postScriptName = fontNames[command.textStyle.fontFamilyID],
        let embedded = UIFont(name: postScriptName, size: size)
      {
        descriptor = weightedDescriptor(
          embedded.fontDescriptor, weight: weightValue, size: size)
      }
      if command.textStyle.fontStyle & 2 != 0 {
        descriptor =
          descriptor.withSymbolicTraits(descriptor.symbolicTraits.union(.traitItalic)) ?? descriptor
      }
      let resolved = UIFont(descriptor: descriptor, size: size)
      return scalesForDynamicType ? UIFontMetrics.default.scaledFont(for: resolved) : resolved
    }

    /// Resolves an installed Apple font by family or PostScript name without falling back.
    private static func localFontDescriptor(
      familyName: String, size: CGFloat
    ) -> UIFontDescriptor? {
      if let installedFamily = UIFont.familyNames.first(where: {
        $0.caseInsensitiveCompare(familyName) == .orderedSame
      }) {
        return UIFontDescriptor(fontAttributes: [.family: installedFamily])
      }
      let postScriptName = UIFont.familyNames.lazy.compactMap { family in
        UIFont.fontNames(forFamilyName: family).first {
          $0.caseInsensitiveCompare(familyName) == .orderedSame
        }
      }.first
      return postScriptName.flatMap { UIFont(name: $0, size: size)?.fontDescriptor }
    }

    /// A resolved face that keeps the run's weight.
    ///
    /// A document carries its weight on the text run, so a family resolved once is asked for several
    /// weights. Replacing the descriptor with the face wholesale discarded the weight, which made
    /// every run render at the face's default. A variable face expresses the weight through its
    /// `wght` axis; a static instance has no axis to set, so the weight is carried as the bold
    /// symbolic trait instead.
    private static func weightedDescriptor(
      _ descriptor: UIFontDescriptor, weight: CGFloat, size: CGFloat
    ) -> UIFontDescriptor {
      if let varied = RemoteComposeFontVariation.descriptor(
        descriptor as CTFontDescriptor, applyingWeight: weight)
      {
        return varied as UIFontDescriptor
      }
      let existing = CTFontSymbolicTraits(rawValue: descriptor.symbolicTraits.rawValue)
      let traits = RemoteComposeFontVariation.symbolicTraits(
        forWeight: weight, existing: existing)
      guard traits.rawValue != existing.rawValue else { return descriptor }
      return descriptor.withSymbolicTraits(
        UIFontDescriptor.SymbolicTraits(rawValue: traits.rawValue)) ?? descriptor
    }

    static func string(
      for command: NativeDrawCommand,
      font: UIFont,
      scale: CGFloat,
      layoutDirection: UIUserInterfaceLayoutDirection
    ) -> NSAttributedString {
      let paragraph = NSMutableParagraphStyle()
      paragraph.alignment = alignment(
        command.textStyle, layoutDirection: layoutDirection)
      paragraph.lineHeightMultiple = max(command.textStyle.lineHeightMultiplier, 0)
      paragraph.lineSpacing = command.textStyle.lineHeightAdd * scale
      paragraph.hyphenationFactor = command.textStyle.hyphenation > 0 ? 1 : 0
      // Foundation's truncating paragraph modes measure the entire value as one line. UIKit owns
      // last-line truncation through UILabel.lineBreakMode; the attributed paragraph must still
      // wrap when Remote Compose permits multiple lines.
      paragraph.lineBreakMode =
        command.textStyle.maxLines > 1
        ? .byWordWrapping : lineBreakMode(command.textStyle.overflow)
      var attributes: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: command.color.withAlphaComponent(command.alpha),
        .paragraphStyle: paragraph,
        .kern: command.textStyle.letterSpacing * font.pointSize,
      ]
      if command.textStyle.isUnderlined {
        attributes[.underlineStyle] = NSUnderlineStyle.single.rawValue
      }
      if command.textStyle.isStruckThrough {
        attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
      }
      if command.isStroke {
        attributes[.strokeColor] = command.color.withAlphaComponent(command.alpha)
        attributes[.strokeWidth] = max(command.strokeWidth / font.pointSize * 100, 0.1)
      }
      return NSAttributedString(string: command.text ?? "", attributes: attributes)
    }

    private static func alignment(
      _ style: NativeTextStyle, layoutDirection: UIUserInterfaceLayoutDirection
    ) -> NSTextAlignment {
      switch NativeTextPolicy.alignment(
        value: style.alignment,
        justified: style.isJustified,
        direction: layoutDirection == .rightToLeft ? .rightToLeft : .leftToRight
      ) {
      case .left: return .left
      case .right: return .right
      case .center: return .center
      case .justified: return .justified
      }
    }

    private static func lineBreakMode(_ overflow: Int) -> NSLineBreakMode {
      switch NativeTextPolicy.lineBreak(overflow: overflow) {
      case .clip: return .byClipping
      case .wordWrap: return .byWordWrapping
      case .tail: return .byTruncatingTail
      case .head: return .byTruncatingHead
      case .middle: return .byTruncatingMiddle
      }
    }
  }

  /// Everything a `NativeCanvasView` draw depends on, compared exactly so no change is missed.
  ///
  /// Images are compared by identity, and only those a command references. The key holds the
  /// images themselves rather than `ObjectIdentifier`s so a freed image's address cannot be reused
  /// by a new one and read as unchanged.
  private struct NativeCanvasRenderKey: Equatable {
    let commands: [NativeDrawCommand]
    let images: [Int: UIImage]
    let fontNames: [Int: String]

    init(commands: [NativeDrawCommand], images: [Int: UIImage], fontNames: [Int: String]) {
      self.commands = commands
      var referenced: [Int: UIImage] = [:]
      for command in commands {
        // A `DrawToBitmap` target starts from its declared image, so that image is read too.
        if let id = command.image?.imageID ?? command.textureImageID
          ?? command.offscreenTarget?.bitmapID,
          let image = images[id]
        {
          referenced[id] = image
        }
      }
      self.images = referenced
      self.fontNames = fontNames
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
      lhs.commands == rhs.commands && lhs.fontNames == rhs.fontNames
        && lhs.images.count == rhs.images.count
        && lhs.images.allSatisfy { id, image in rhs.images[id] === image }
    }
  }

  final class NativeCanvasView: UIView {
    private var commands: [NativeDrawCommand]
    private var images: [Int: UIImage]
    private var fontNames: [Int: String]
    /// What the view last drew: `setNeedsDisplay()` runs only when the next update differs.
    private var rendered: NativeCanvasRenderKey
    /// The bitmaps `DrawToBitmap` commands draw into: the document view's pool, shared by every
    /// canvas in its tree and kept between draws.
    private let offscreenTargets: NativeOffscreenTargets
    var documentScale: CGFloat = 1 {
      didSet { if documentScale != oldValue { setNeedsDisplay() } }
    }

    init(
      commands: [NativeDrawCommand], images: [Int: UIImage], fontNames: [Int: String],
      offscreenTargets: NativeOffscreenTargets
    ) {
      self.commands = commands
      self.images = images
      self.fontNames = fontNames
      self.offscreenTargets = offscreenTargets
      rendered = NativeCanvasRenderKey(commands: commands, images: images, fontNames: fontNames)
      super.init(frame: .zero)
      isOpaque = false
      backgroundColor = .clear
      contentMode = .redraw
      isUserInteractionEnabled = false
      accessibilityIdentifier = "rc-native-canvas"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    func update(
      commands: [NativeDrawCommand], images: [Int: UIImage], fontNames: [Int: String]
    ) {
      let next = NativeCanvasRenderKey(commands: commands, images: images, fontNames: fontNames)
      self.commands = commands
      self.images = images
      self.fontNames = fontNames
      guard next != rendered else { return }
      rendered = next
      setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
      guard let context = UIGraphicsGetCurrentContext() else { return }
      context.scaleBy(x: documentScale, y: documentScale)
      // A `DrawToBitmap` sends the commands after it to an offscreen bitmap until the next one;
      // drawing returns to this canvas at the end of the node's commands, as the CMP player's
      // draw-target scope does. The target is pushed as UIKit's current context because text and
      // image draws go through `UIGraphicsGetCurrentContext()` rather than the context passed.
      // A target that cannot be allocated drops its commands rather than drawing them here.
      var redirect = NativeOffscreenRedirect.canvas
      for command in commands {
        guard command.kind == NativeSwiftDrawKind.drawToBitmap else {
          switch redirect {
          case .canvas: draw(command, in: context)
          case .offscreen(let target): draw(command, in: target)
          case .dropped: break
          }
          continue
        }
        if case .offscreen = redirect { UIGraphicsPopContext() }
        redirect =
          command.offscreenTarget.map { next in
            offscreenTargets.begin(next, seed: images[next.bitmapID]?.cgImage)
          } ?? NativeOffscreenRedirect.canvas
        if case .offscreen(let target) = redirect { UIGraphicsPushContext(target) }
      }
      if case .offscreen = redirect { UIGraphicsPopContext() }
    }

    private func draw(_ command: NativeDrawCommand, in context: CGContext) {
      let v = command.values
      context.setAlpha(command.alpha)
      context.setStrokeColor(command.color.cgColor)
      context.setFillColor(command.color.cgColor)
      NativeGraphicsState.apply(
        to: context,
        strokeWidth: command.strokeWidth,
        strokeCap: command.strokeCap,
        strokeJoin: command.strokeJoin,
        blendMode: command.blendMode)
      // Filter quality is paint state, so a command that does not name one takes the reference
      // default rather than whatever the previous command left behind.
      context.interpolationQuality = command.interpolationQuality

      switch command.kind {
      case NativeSwiftDrawKind.matrixSave: context.saveGState()
      case NativeSwiftDrawKind.matrixRestore: context.restoreGState()
      case NativeSwiftDrawKind.matrixTranslate: context.translateBy(x: v[0], y: v[1])
      case NativeSwiftDrawKind.matrixScale:
        let pivot = CGPoint(x: v[2].isNaN ? 0 : v[2], y: v[3].isNaN ? 0 : v[3])
        context.translateBy(x: pivot.x, y: pivot.y)
        context.scaleBy(x: v[0], y: v[1])
        context.translateBy(x: -pivot.x, y: -pivot.y)
      case NativeSwiftDrawKind.matrixRotate:
        let pivot = CGPoint(x: v[1].isNaN ? 0 : v[1], y: v[2].isNaN ? 0 : v[2])
        context.translateBy(x: pivot.x, y: pivot.y)
        context.rotate(by: v[0] * .pi / 180)
        context.translateBy(x: -pivot.x, y: -pivot.y)
      case NativeSwiftDrawKind.matrixSkew:
        context.concatenate(CGAffineTransform(a: 1, b: v[1], c: v[0], d: 1, tx: 0, ty: 0))
      case NativeSwiftDrawKind.matrixFromPath:
        // The core measured the path; its six values are the affine matrix, in this order.
        context.concatenate(
          CGAffineTransform(a: v[0], b: v[1], c: v[2], d: v[3], tx: v[4], ty: v[5]))
      case NativeSwiftDrawKind.clipRect:
        context.clip(to: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]))
      case NativeSwiftDrawKind.clipPath:
        NativeGraphicsState.clip(
          context, to: NativePathBuilder.make(command.path), winding: command.pathWinding,
          regionOp: v.first.map { Int($0) } ?? NativeSwiftClipRegionOp.intersect)
      case NativeSwiftDrawKind.rect:
        paint(CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]), command, context)
      case NativeSwiftDrawKind.oval:
        paintEllipse(
          CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]), command, context)
      case NativeSwiftDrawKind.circle:
        paintEllipse(
          CGRect(x: v[0] - v[2], y: v[1] - v[2], width: v[2] * 2, height: v[2] * 2), command,
          context)
      case NativeSwiftDrawKind.line:
        let path = CGMutablePath()
        path.move(to: CGPoint(x: v[0], y: v[1]))
        path.addLine(to: CGPoint(x: v[2], y: v[3]))
        // DrawLine is a stroke regardless of the paint style: a fill of an open path draws nothing.
        paint(path, command, context, forceStroke: true)
      case NativeSwiftDrawKind.roundRect:
        let rect = CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1])
        let radii = NativeGraphicsState.clampedCornerRadii(
          in: rect, cornerWidth: v[4], cornerHeight: v[5])
        let path = UIBezierPath(roundedRect: rect, cornerRadius: max(radii.width, radii.height))
        paint(path.cgPath, command, context)
      case NativeSwiftDrawKind.arc, NativeSwiftDrawKind.sector: drawArc(command, context)
      case NativeSwiftDrawKind.text: drawText(command)
      case NativeSwiftDrawKind.path, NativeSwiftDrawKind.tweenPath:
        paint(
          NativePathBuilder.make(command.path), command, context,
          fillRule: NativeGraphicsState.fillRule(command.pathWinding))
      case NativeSwiftDrawKind.bitmap: drawImage(command, context)
      default: break
      }
    }

    private func paint(_ rect: CGRect, _ command: NativeDrawCommand, _ context: CGContext) {
      paint(CGPath(rect: rect, transform: nil), command, context)
    }

    private func paintEllipse(_ rect: CGRect, _ command: NativeDrawCommand, _ context: CGContext) {
      paint(CGPath(ellipseIn: rect, transform: nil), command, context)
    }

    private func paint(
      _ path: CGPath,
      _ command: NativeDrawCommand,
      _ context: CGContext,
      fillRule: CGPathFillRule = .winding,
      forceStroke: Bool = false
    ) {
      let isStroke = command.isStroke || forceStroke
      // Destination leaves the existing buffer unchanged, but must not suppress ordered
      // transforms, clipping, or save/restore commands around the draw.
      guard command.blendMode != NativeSwiftPaintBlendMode.destination else { return }
      // Component-value expressions are resolved before UIKit performs its intrinsic-size pass, so
      // a background path built from them is stale in whichever dimension layout later decided.
      // The owning component has the final bounds now; use them for this background case and let
      // the component's rounded clip preserve its shape.
      //
      // Either dimension can be the stale one, and both can be at once. This originally required
      // the width to match and only rescued a stale height, which covered a wrap-content component
      // whose height had not been measured yet. A component that is stale in width as well fell
      // straight through to the pre-layout path: on the catalog corpus that drew `button-filled`
      // and its filled siblings at roughly half size, anchored at the right origin, while the
      // label drawn over them sat at full scale.
      //
      // The origin still has to match. That is what separates a background whose size layout has
      // not yet decided from a shape genuinely inset within its component, which must be left
      // alone.
      let pathBounds = path.boundingBoxOfPath
      let isDeferredComponentBackground =
        !isStroke && command.usesComponentGeometry
        && abs(pathBounds.minX - bounds.minX) <= 1 && abs(pathBounds.minY - bounds.minY) <= 1
        && (abs(pathBounds.width - bounds.width) > 1 || abs(pathBounds.height - bounds.height) > 1)
      let isDeferredShaderBackground =
        !isStroke && (command.textureImageID != nil || command.gradient != nil)
        && (pathBounds.width <= 0 || pathBounds.height <= 0) && !bounds.isEmpty
      let effectivePath =
        isDeferredComponentBackground || isDeferredShaderBackground
        ? CGPath(rect: bounds, transform: nil) : path
      context.addPath(effectivePath)
      if let textureImageID = command.textureImageID, let image = images[textureImageID]?.cgImage {
        context.saveGState()
        if isStroke { context.replacePathWithStrokedPath() }
        context.clip(using: isStroke ? .winding : fillRule)
        NativeTexturePolicy.paint(
          image: image,
          transform: command.textureTransform,
          tileModeX: command.textureTileModeX,
          tileModeY: command.textureTileModeY,
          in: context)
        context.restoreGState()
        return
      }
      guard let gradient = command.gradient else {
        context.drawPath(
          using: isStroke ? .stroke : (fillRule == .evenOdd ? .eoFill : .fill))
        return
      }
      context.saveGState()
      if isStroke { context.replacePathWithStrokedPath() }
      context.clip(using: isStroke ? .winding : fillRule)
      NativeGradientRenderer.draw(gradient, in: context)
      context.restoreGState()
    }

    private func drawArc(_ command: NativeDrawCommand, _ context: CGContext) {
      let v = command.values
      let center = CGPoint(x: (v[0] + v[2]) / 2, y: (v[1] + v[3]) / 2)
      let radius = min(v[2] - v[0], v[3] - v[1]) / 2
      let start = v[4] * .pi / 180
      let end = (v[4] + v[5]) * .pi / 180
      if command.kind == NativeSwiftDrawKind.sector { context.move(to: center) }
      context.addArc(
        center: center, radius: radius, startAngle: start, endAngle: end, clockwise: false)
      if command.kind == NativeSwiftDrawKind.sector { context.closePath() }
      let path = context.path
      context.beginPath()
      if let path { paint(path, command, context) }
    }

    private func drawText(_ command: NativeDrawCommand) {
      guard command.blendMode != NativeSwiftPaintBlendMode.destination else { return }
      guard command.text != nil, let context = UIGraphicsGetCurrentContext() else { return }
      let font = NativeTextAttributes.font(for: command, scale: 1, fontNames: fontNames)
      let attributed = NativeTextAttributes.string(
        for: command, font: font, scale: 1,
        layoutDirection: effectiveUserInterfaceLayoutDirection)
      let line = CTLineCreateWithAttributedString(attributed)
      var ascent: CGFloat = 0
      var descent: CGFloat = 0
      var leading: CGFloat = 0
      let width = CGFloat(
        CTLineGetTypographicBounds(line, &ascent, &descent, &leading))
      let height = ascent + descent + leading
      let panX = command.values[2]
      let panY = command.values[3]
      let x = command.values[0] - width * ((panX + 1) / 2)
      // A NaN panY is the reference's "no vertical pan": the y given is the baseline.
      let baseline =
        panY.isNaN ? command.values[1] : command.values[1] - height * ((panY + 1) / 2)
      context.saveGState()
      context.textMatrix = .identity
      context.translateBy(x: 0, y: baseline * 2)
      context.scaleBy(x: 1, y: -1)
      context.textPosition = CGPoint(x: x, y: baseline)
      CTLineDraw(line, context)
      context.restoreGState()
    }

    private func drawImage(_ command: NativeDrawCommand, _ context: CGContext) {
      guard
        command.blendMode != NativeSwiftPaintBlendMode.destination,
        let draw = command.image,
        // A bitmap a `DrawToBitmap` drew into shows what it holds now.
        let source = offscreenTargets.image(draw.imageID) ?? images[draw.imageID]?.cgImage,
        let cropped = source.cropping(to: draw.source)
      else { return }
      let destination = NativeImageGeometry.destination(
        source: draw.source,
        destination: draw.destination,
        scaleType: draw.scaleType,
        scaleFactor: draw.scaleFactor)
      guard !destination.isEmpty else { return }
      context.saveGState()
      context.clip(to: draw.destination)
      UIImage(cgImage: cropped, scale: 1, orientation: .up).draw(
        in: destination,
        blendMode: NativeGraphicsState.blendMode(command.blendMode),
        alpha: 1)
      context.restoreGState()
    }

  }

  extension UIColor {
    fileprivate convenience init(remoteComposeARGB value: UInt32) {
      self.init(
        red: CGFloat((value >> 16) & 0xff) / 255,
        green: CGFloat((value >> 8) & 0xff) / 255,
        blue: CGFloat(value & 0xff) / 255,
        alpha: CGFloat((value >> 24) & 0xff) / 255)
    }
  }
#endif
