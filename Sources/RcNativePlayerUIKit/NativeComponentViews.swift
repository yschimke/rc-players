#if canImport(UIKit)
  import CoreText
  import RcComposePlayer
  import UIKit

  struct NativeDocument {
    let size: CGSize
    let root: NativeNode
    let images: [NativeImageResource]
    let fonts: [NativeFontResource]
    let diagnostics: RemoteComposeNativePlayerDiagnostics
    let rootSizing: Int
    let rootMode: Int
    let rootAlignment: Int
    let frameSchedule: NativeFrameSchedule
    let executionBudget: NativeFrameBudget

    init(snapshot: RcNativeDocumentSnapshot, limits: RemoteComposeNativeExecutionLimits) throws {
      executionBudget = try Self.validate(snapshot: snapshot, limits: limits)
      size = CGSize(width: Int(snapshot.width), height: Int(snapshot.height))
      root = NativeNode(snapshot: snapshot.root)
      images = snapshot.images.map(NativeImageResource.init)
      fonts = snapshot.fonts.map(NativeFontResource.init)
      diagnostics = RemoteComposeNativePlayerDiagnostics(
        issues: snapshot.diagnostics.map { diagnostic in
          RemoteComposeNativePlayerDiagnostic(
            severity: diagnostic.severity == 0 ? .warning : .unsupported,
            opcode: Int(diagnostic.opcode), operationName: diagnostic.operationName,
            componentID: Int(diagnostic.componentId), reason: diagnostic.reason)
        },
        unsupportedOpcodes: snapshot.unsupportedOpcodes.map { Int(truncating: $0) },
        notes: snapshot.notes)
      rootSizing = Int(snapshot.rootSizing)
      rootMode = Int(snapshot.rootMode)
      rootAlignment = Int(snapshot.rootAlignment)
      frameSchedule = NativeFrameSchedule(
        needsContinuousFrames: snapshot.needsContinuousFrames,
        requestsNextFrame: snapshot.requestsNextFrame,
        wakeAfter: snapshot.wakeAfterSeconds < 0 ? nil : TimeInterval(snapshot.wakeAfterSeconds))
    }

    private static func validate(
      snapshot: RcNativeDocumentSnapshot, limits: RemoteComposeNativeExecutionLimits
    ) throws -> NativeFrameBudget {
      try NativeFrameBudget.validate(limits)
      var budget = NativeFrameBudget()
      try budget.recordStrings(
        snapshot.notes.map { Optional($0) }
          + snapshot.diagnostics.flatMap { [Optional($0.operationName), Optional($0.reason)] },
        limits: limits)
      try budget.validateDocumentDimensions(
        [Double(snapshot.width), Double(snapshot.height)], limits: limits)
      var pending: [(node: RcNativeNodeSnapshot, depth: Int)] = [(snapshot.root, 1)]
      while let current = pending.popLast() {
        let node = current.node
        try budget.recordNode(depth: current.depth, limits: limits)
        try budget.recordStrings(
          [node.semanticLabel, node.semanticText, node.semanticStateDescription], limits: limits)
        try budget.recordWork(node.clickActionTypes.count, limits: limits)
        let maximumWidth = node.maximumWidth < 0 ? 0 : node.maximumWidth
        let maximumHeight = node.maximumHeight < 0 ? 0 : node.maximumHeight
        try budget.validateLayoutDimension(
          value: Double(node.widthValue), type: Int(node.widthType),
          componentID: Int(node.componentId), field: "width", limits: limits)
        try budget.validateLayoutDimension(
          value: Double(node.heightValue), type: Int(node.heightType),
          componentID: Int(node.componentId), field: "height", limits: limits)
        try budget.validateNumbers(
          [
            Double(node.minimumWidth), Double(node.minimumHeight), Double(maximumWidth),
            Double(maximumHeight),
            Double(node.paddingTop), Double(node.paddingLeft), Double(node.paddingBottom),
            Double(node.paddingRight), Double(node.cornerRadius), Double(node.spacing),
            Double(node.offsetX), Double(node.offsetY), Double(node.zIndex),
          ], componentID: Int(node.componentId), field: "layout", limits: limits)
        try budget.validateCanvasDimensions(
          [
            abs(Double(node.minimumWidth)), abs(Double(node.minimumHeight)),
            abs(Double(maximumWidth)), abs(Double(maximumHeight)),
          ], limits: limits)
        for command in node.commands {
          let gradientWork = command.gradient.map { $0.colors.count + $0.stops.count + 4 } ?? 0
          try budget.recordCommand(
            pathElementCount: command.path.count, additionalWork: gradientWork,
            strings: [
              command.text, command.image?.contentDescription, command.textStyle?.fontFamilyName,
            ], limits: limits)
          let style = command.textStyle
          var commandGeometry = [
            command.first, command.second, command.third, command.fourth, command.fifth,
            command.sixth,
          ]
          if Int(command.kind) == 3 {
            if commandGeometry[2].isNaN { commandGeometry[2] = 0 }
            if commandGeometry[3].isNaN { commandGeometry[3] = 0 }
          } else if Int(command.kind) == 4 {
            if commandGeometry[1].isNaN { commandGeometry[1] = 0 }
            if commandGeometry[2].isNaN { commandGeometry[2] = 0 }
          }
          switch Int(command.kind) {
          case 2:
            try budget.validateNumbers(
              commandGeometry.prefix(2).map(Double.init), componentID: Int(node.componentId),
              field: "translation", limits: limits)
            try budget.validateFinite(
              commandGeometry.suffix(4).map(Double.init), componentID: Int(node.componentId),
              field: "translation")
          case 3:
            try budget.validateFinite(
              commandGeometry.prefix(2).map(Double.init), componentID: Int(node.componentId),
              field: "scale")
            try budget.validateNumbers(
              commandGeometry[2..<4].map(Double.init), componentID: Int(node.componentId),
              field: "scale pivot", limits: limits)
            try budget.validateFinite(
              commandGeometry.suffix(2).map(Double.init), componentID: Int(node.componentId),
              field: "scale")
          case 4:
            try budget.validateFinite(
              [Double(commandGeometry[0])], componentID: Int(node.componentId), field: "rotation")
            try budget.validateNumbers(
              commandGeometry[1..<3].map(Double.init), componentID: Int(node.componentId),
              field: "rotation pivot", limits: limits)
            try budget.validateFinite(
              commandGeometry.suffix(3).map(Double.init), componentID: Int(node.componentId),
              field: "rotation")
          case 5:
            try budget.validateFinite(
              commandGeometry.map(Double.init), componentID: Int(node.componentId), field: "skew")
          case 15, 16:
            try budget.validateNumbers(
              commandGeometry.prefix(4).map(Double.init), componentID: Int(node.componentId),
              field: "draw geometry", limits: limits)
            try budget.validateFinite(
              commandGeometry.suffix(2).map(Double.init), componentID: Int(node.componentId),
              field: "arc angles")
          case 17:
            try budget.validateNumbers(
              commandGeometry.prefix(2).map(Double.init), componentID: Int(node.componentId),
              field: "text position", limits: limits)
            try budget.validateFinite(
              commandGeometry.suffix(4).map(Double.init), componentID: Int(node.componentId),
              field: "text anchor")
          default:
            try budget.validateNumbers(
              commandGeometry.map(Double.init), componentID: Int(node.componentId),
              field: "draw geometry", limits: limits)
          }
          switch Int(command.kind) {
          case 6, 10, 11, 13, 14, 15, 16:
            try budget.validateCanvasDimensions(
              [
                abs(Double(commandGeometry[2] - commandGeometry[0])),
                abs(Double(commandGeometry[3] - commandGeometry[1])),
              ], limits: limits)
            if Int(command.kind) == 14 {
              try budget.validateCanvasDimensions(
                [abs(Double(commandGeometry[4] * 2)), abs(Double(commandGeometry[5] * 2))],
                limits: limits)
            }
          case 12:
            try budget.validateCanvasDimensions(
              [abs(Double(commandGeometry[2] * 2))], limits: limits)
          default: break
          }
          try budget.validateFinite(
            [
              command.alpha, command.strokeWidth, command.textSize, command.textWeight,
              style?.letterSpacing ?? 0, style?.lineHeightAdd ?? 0,
              style?.lineHeightMultiplier ?? 1,
            ].map(Double.init), componentID: Int(node.componentId), field: "paint")
          var pathX: [Double] = []
          var pathY: [Double] = []
          for segment in command.path {
            let values =
              [segment.first, segment.second, segment.third, segment.fourth, segment.fifth,
               segment.sixth].map(Double.init)
            let coordinateCount: Int
            switch Int(segment.kind) {
            case 10, 11: coordinateCount = 2
            case 12, 13: coordinateCount = 4
            case 14: coordinateCount = 6
            default: coordinateCount = 0
            }
            try budget.validateNumbers(
              Array(values.prefix(coordinateCount)), componentID: Int(node.componentId),
              field: "path", limits: limits)
            try budget.validateFinite(
              Array(values.dropFirst(coordinateCount)), componentID: Int(node.componentId),
              field: "path")
            for index in stride(from: 0, to: coordinateCount, by: 2) {
              pathX.append(values[index])
              pathY.append(values[index + 1])
            }
          }
          if let minimumX = pathX.min(), let maximumX = pathX.max(),
            let minimumY = pathY.min(), let maximumY = pathY.max()
          {
            try budget.validateCanvasDimensions(
              [maximumX - minimumX, maximumY - minimumY], limits: limits)
          }
          if let gradient = command.gradient {
            try budget.validateNumbers(
              [gradient.first, gradient.second, gradient.third, gradient.fourth].map(Double.init),
              componentID: Int(node.componentId), field: "gradient", limits: limits)
            try budget.validateGradientStops(
              gradient.stops.map { Double(truncating: $0) }, componentID: Int(node.componentId))
          }
          if let image = command.image {
            let sourceWidth = image.sourceRight - image.sourceLeft
            let sourceHeight = image.sourceBottom - image.sourceTop
            let destinationWidth = image.destinationRight - image.destinationLeft
            let destinationHeight = image.destinationBottom - image.destinationTop
            try budget.validateNumbers(
              [
                image.sourceLeft, image.sourceTop, image.sourceRight, image.sourceBottom,
                image.destinationLeft, image.destinationTop, image.destinationRight,
                image.destinationBottom,
              ].map(Double.init), componentID: Int(node.componentId), field: "image", limits: limits)
            try budget.validateFinite(
              [Double(image.scaleFactor)], componentID: Int(node.componentId), field: "image scale")
            try budget.validateCanvasDimensions(
              [
                abs(Double(sourceWidth)), abs(Double(sourceHeight)),
                abs(Double(destinationWidth)), abs(Double(destinationHeight)),
              ], limits: limits)
            let derivedDestination = NativeImageGeometry.destination(
              source: CGRect(
                x: CGFloat(image.sourceLeft), y: CGFloat(image.sourceTop),
                width: CGFloat(sourceWidth), height: CGFloat(sourceHeight)),
              destination: CGRect(
                x: CGFloat(image.destinationLeft), y: CGFloat(image.destinationTop),
                width: CGFloat(destinationWidth), height: CGFloat(destinationHeight)),
              scaleType: Int(image.scaleType), scaleFactor: CGFloat(image.scaleFactor))
            try budget.validateNumbers(
              [derivedDestination.minX, derivedDestination.minY, derivedDestination.maxX,
               derivedDestination.maxY].map(Double.init),
              componentID: Int(node.componentId), field: "derived image geometry", limits: limits)
            try budget.validateCanvasDimensions(
              [abs(Double(derivedDestination.width)), abs(Double(derivedDestination.height))],
              limits: limits)
          }
        }
        pending.append(contentsOf: node.children.map { ($0, current.depth + 1) })
      }
      return budget
    }
  }

  struct NativeImageResource {
    let id: Int
    let width: Int
    let height: Int
    let type: Int
    let encoding: Int
    let data: Data

    init(snapshot: RcNativeImageResource) {
      id = Int(snapshot.id)
      width = Int(snapshot.width)
      height = Int(snapshot.height)
      type = Int(snapshot.type)
      encoding = Int(snapshot.encoding)
      data = RcDataBridgeKt.rcData(bytes: snapshot.data) as Data
    }
  }

  struct NativeFontResource {
    let id: Int
    let type: Int
    let data: Data

    init(snapshot: RcNativeFontResource) {
      id = Int(snapshot.id)
      type = Int(snapshot.type)
      data = RcDataBridgeKt.rcData(bytes: snapshot.data) as Data
    }
  }

  private struct NativeSemanticBehavior {
    let descriptor: NativeAccessibilityDescriptor
    let componentID: Int
    let clickActionTypes: [Int]
    let acceptsPointerAction: Bool
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
        default: self = .group
        }
      }
    }

    let kind: Kind
    let componentID: Int
    let commands: [NativeDrawCommand]
    let children: [NativeNode]
    let semanticRole: Int
    let isClickable: Bool
    let isEnabled: Bool
    let accessibilityLabel: String?
    let accessibilityText: String?
    let accessibilityValue: String?
    let accessibilityMode: NativeAccessibilityMode
    let hasAccessibilitySemantics: Bool
    let clickActionTypes: [Int]
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
    let backgroundColor: UIColor?
    let horizontalPositioning: Int
    let verticalPositioning: Int
    let spacing: CGFloat
    let offset: CGPoint
    let zIndex: CGFloat
    let visibility: Int

    init(snapshot: RcNativeNodeSnapshot) {
      kind = Kind(rawValue: snapshot.kind)
      componentID = Int(snapshot.componentId)
      commands = snapshot.commands.map(NativeDrawCommand.init)
      children = snapshot.children.map(NativeNode.init)
      semanticRole = Int(snapshot.semanticRole)
      isClickable = snapshot.clickable
      isEnabled = snapshot.enabled
      accessibilityLabel = snapshot.semanticLabel
      accessibilityText = snapshot.semanticText
      accessibilityValue = snapshot.semanticStateDescription
      accessibilityMode = NativeAccessibilityMode(rawValue: Int(snapshot.semanticMode)) ?? .set
      hasAccessibilitySemantics = snapshot.hasSemantics
      clickActionTypes = snapshot.clickActionTypes.map { Int(truncating: $0) }
      widthType = Int(snapshot.widthType)
      widthValue = CGFloat(snapshot.widthValue)
      heightType = Int(snapshot.heightType)
      heightValue = CGFloat(snapshot.heightValue)
      minimumHeight = CGFloat(snapshot.minimumHeight)
      minimumWidth = CGFloat(snapshot.minimumWidth)
      maximumWidth = snapshot.maximumWidth < 0 ? nil : CGFloat(snapshot.maximumWidth)
      maximumHeight = snapshot.maximumHeight < 0 ? nil : CGFloat(snapshot.maximumHeight)
      padding = UIEdgeInsets(
        top: CGFloat(snapshot.paddingTop), left: CGFloat(snapshot.paddingLeft),
        bottom: CGFloat(snapshot.paddingBottom), right: CGFloat(snapshot.paddingRight))
      cornerRadius = CGFloat(snapshot.cornerRadius)
      backgroundColor =
        snapshot.hasBackground
        ? UIColor(remoteComposeARGB: UInt32(bitPattern: snapshot.backgroundColor)) : nil
      horizontalPositioning = Int(snapshot.horizontalPositioning)
      verticalPositioning = Int(snapshot.verticalPositioning)
      spacing = CGFloat(snapshot.spacing)
      offset = CGPoint(x: CGFloat(snapshot.offsetX), y: CGFloat(snapshot.offsetY))
      zIndex = CGFloat(snapshot.zIndex)
      visibility = Int(snapshot.visibility)
    }

    var firstText: String? {
      commands.lazy.compactMap(\.text).first ?? children.lazy.compactMap(\.firstText).first
    }

    var localAccessibilityLabels: [String] {
      [accessibilityLabel, accessibilityText].compactMap { $0 }
        + commands.flatMap { command in
          [command.text, command.image?.contentDescription].compactMap { $0 }
        }
    }

    var accessibilityDescriptor: NativeAccessibilityDescriptor? {
      guard hasAccessibilitySemantics || isClickable else { return nil }
      return NativeAccessibilityDescriptor(
        role: NativeAccessibilityRole(rawValue: semanticRole),
        mode: accessibilityMode,
        contentDescription: accessibilityLabel,
        text: accessibilityText,
        stateDescription: accessibilityValue,
        isEnabled: isEnabled,
        isClickable: isClickable)
    }

    var effectiveAccessibilityLabels: [String] {
      guard visibility == 1 else { return [] }
      let descendants = children.flatMap(\.effectiveAccessibilityLabels)
      guard let descriptor = accessibilityDescriptor else {
        return localAccessibilityLabels + descendants
      }
      switch descriptor.mode {
      case .clearAndSet:
        return [descriptor.resolvedLabel(descendantLabels: [])].compactMap { $0 }
      case .merge:
        return [
          descriptor.resolvedLabel(
            descendantLabels: localAccessibilityLabels + descendants)
        ].compactMap { $0 }
      case .set:
        return [descriptor.resolvedLabel(descendantLabels: [])].compactMap { $0 }
          + localAccessibilityLabels + descendants
      }
    }

    var descendantAccessibilityLabels: [String] {
      children.flatMap(\.effectiveAccessibilityLabels)
    }

    fileprivate var semanticBehavior: NativeSemanticBehavior? {
      guard let own = accessibilityDescriptor else { return nil }
      guard own.mode == .merge else {
        return NativeSemanticBehavior(
          descriptor: own, componentID: componentID, clickActionTypes: clickActionTypes,
          acceptsPointerAction: !clickActionTypes.isEmpty)
      }
      let descendants = children.flatMap(\.effectiveSemanticBehaviors)
      let mergedDescriptor = descendants.reduce(own) { descriptor, descendant in
        descriptor.mergingBehavior(from: descendant.descriptor)
      }
      let ownsAction = !clickActionTypes.isEmpty
      let descendantAction = descendants.first { !$0.clickActionTypes.isEmpty }
      return NativeSemanticBehavior(
        descriptor: mergedDescriptor,
        componentID: ownsAction ? componentID : descendantAction?.componentID ?? componentID,
        clickActionTypes: ownsAction ? clickActionTypes : descendantAction?.clickActionTypes ?? [],
        acceptsPointerAction: ownsAction)
    }

    private var effectiveSemanticBehaviors: [NativeSemanticBehavior] {
      guard visibility == 1 else { return [] }
      if let semanticBehavior {
        if semanticBehavior.descriptor.mode == .set {
          return [semanticBehavior] + children.flatMap(\.effectiveSemanticBehaviors)
        }
        return [semanticBehavior]
      }
      return children.flatMap(\.effectiveSemanticBehaviors)
    }
  }

  struct NativeDrawCommand {
    let kind: Int
    let values: [CGFloat]
    let color: UIColor
    let alpha: CGFloat
    let strokeWidth: CGFloat
    let isStroke: Bool
    let strokeCap: Int
    let strokeJoin: Int
    let blendMode: Int
    let textSize: CGFloat
    let textWeight: CGFloat
    let text: String?
    let path: [NativePathElement]
    let pathWinding: Int
    let gradient: NativeGradient?
    let textStyle: NativeTextStyle
    let image: NativeImageDraw?
    let textureImageID: Int?
    let textureTileModeX: Int
    let textureTileModeY: Int

    init(snapshot: RcNativeDrawCommand) {
      kind = Int(snapshot.kind)
      values = [
        snapshot.first, snapshot.second, snapshot.third, snapshot.fourth, snapshot.fifth,
        snapshot.sixth,
      ].map(CGFloat.init)
      color = UIColor(remoteComposeARGB: UInt32(bitPattern: snapshot.color))
      alpha = CGFloat(snapshot.alpha)
      strokeWidth = CGFloat(snapshot.strokeWidth)
      isStroke = snapshot.stroke
      strokeCap = Int(snapshot.strokeCap)
      strokeJoin = Int(snapshot.strokeJoin)
      blendMode = Int(snapshot.blendMode)
      textSize = CGFloat(snapshot.textSize)
      textWeight = CGFloat(snapshot.textWeight)
      text = snapshot.text
      path = snapshot.path.map { segment in
        NativePathElement(
          kind: Int(segment.kind),
          values: [
            segment.first, segment.second, segment.third, segment.fourth, segment.fifth,
            segment.sixth,
          ].map(CGFloat.init))
      }
      pathWinding = Int(snapshot.pathWinding)
      if let value = snapshot.gradient {
        gradient = NativeGradient(
          kind: Int(value.kind),
          colors: value.colors.map {
            UIColor(remoteComposeARGB: UInt32(bitPattern: $0.int32Value)).cgColor
          },
          stops: value.stops.map { CGFloat(truncating: $0) },
          values: [value.first, value.second, value.third, value.fourth].map(CGFloat.init),
          tileMode: Int(value.tileMode))
      } else {
        gradient = nil
      }
      textStyle = NativeTextStyle(snapshot: snapshot.textStyle)
      image = snapshot.image.map(NativeImageDraw.init)
      let rawTextureImageID = Int(snapshot.textureImageId)
      textureImageID = rawTextureImageID == -1 ? nil : rawTextureImageID
      textureTileModeX = Int(snapshot.textureTileModeX)
      textureTileModeY = Int(snapshot.textureTileModeY)
    }
  }

  struct NativeImageDraw {
    let imageID: Int
    let source: CGRect
    let destination: CGRect
    let scaleType: Int
    let scaleFactor: CGFloat
    let contentDescription: String?

    init(snapshot: RcNativeImageDraw) {
      imageID = Int(snapshot.imageId)
      source = CGRect(
        x: CGFloat(snapshot.sourceLeft),
        y: CGFloat(snapshot.sourceTop),
        width: CGFloat(snapshot.sourceRight - snapshot.sourceLeft),
        height: CGFloat(snapshot.sourceBottom - snapshot.sourceTop))
      destination = CGRect(
        x: CGFloat(snapshot.destinationLeft),
        y: CGFloat(snapshot.destinationTop),
        width: CGFloat(snapshot.destinationRight - snapshot.destinationLeft),
        height: CGFloat(snapshot.destinationBottom - snapshot.destinationTop))
      scaleType = Int(snapshot.scaleType)
      scaleFactor = CGFloat(snapshot.scaleFactor)
      contentDescription = snapshot.contentDescription
    }
  }

  struct NativeTextStyle {
    let fontStyle: Int
    let fontFamilyID: Int
    let fontFamilyName: String?
    let alignment: Int
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

    init(snapshot: RcNativeTextStyle?) {
      fontStyle = Int(snapshot?.fontStyle ?? 0)
      fontFamilyID = Int(snapshot?.fontFamilyId ?? -1)
      fontFamilyName = snapshot?.fontFamilyName
      alignment = Int(snapshot?.alignment ?? 1)
      overflow = Int(snapshot?.overflow ?? 1)
      maxLines = Int(snapshot?.maxLines ?? Int32.max)
      letterSpacing = CGFloat(snapshot?.letterSpacing ?? 0)
      lineHeightAdd = CGFloat(snapshot?.lineHeightAdd ?? 0)
      lineHeightMultiplier = CGFloat(snapshot?.lineHeightMultiplier ?? 1)
      breakStrategy = Int(snapshot?.breakStrategy ?? 0)
      hyphenation = Int(snapshot?.hyphenation ?? 0)
      isJustified = snapshot?.justified ?? false
      isUnderlined = snapshot?.underline ?? false
      isStruckThrough = snapshot?.strikeThrough ?? false
    }
  }

  final class NativeDocumentView: UIView {
    private var document: NativeDocument
    private var resources: NativeResourceStore
    private let componentView: NativeComponentView

    init(
      document: NativeDocument,
      resources: NativeResourceStore,
      onClick: @escaping (Int) -> Void
    ) {
      self.document = document
      self.resources = resources
      componentView = NativeComponentView(
        node: document.root,
        images: resources.images,
        fontNames: resources.fontNames,
        onClick: onClick)
      super.init(frame: .zero)
      isOpaque = false
      backgroundColor = .clear
      addSubview(componentView)
      isAccessibilityElement = false
      accessibilityElements = componentView.accessibilityOrder
      accessibilityIdentifier = "rc-native-document"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    func update(document: NativeDocument, resources: NativeResourceStore) -> Bool {
      guard componentView.canUpdate(
        with: document.root, images: resources.images, fontNames: resources.fontNames)
      else { return false }
      self.document = document
      self.resources = resources
      componentView.update(
        node: document.root, images: resources.images, fontNames: resources.fontNames)
      accessibilityElements = componentView.accessibilityOrder
      setNeedsLayout()
      return true
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
      componentView.bounds = CGRect(origin: .zero, size: document.size)
      componentView.center = CGPoint(
        x: root.translateX + document.size.width * root.scaleX / 2,
        y: root.translateY + document.size.height * root.scaleY / 2)
      componentView.transform = CGAffineTransform(scaleX: root.scaleX, y: root.scaleY)
      componentView.documentScale = 1
      componentView.layoutDirection =
        effectiveUserInterfaceLayoutDirection == .rightToLeft ? .rightToLeft : .leftToRight
    }
  }

  /// One UIView per Remote Compose component, with a deliberately small frame-based implementation
  /// of Box, Row, and Column. Structural content wrappers remain visible in the UIKit hierarchy but
  /// are transparent to layout.
  final class NativeComponentView: UIView {
    private var node: NativeNode
    private var canvasView: NativeCanvasView?
    private var textLabels: [NativeTextLabel]
    private var imageViews: [NativeImageView]
    private var componentChildren: [NativeComponentView]
    private var semanticView: UIView?
    private let onClick: (Int) -> Void
    var documentScale: CGFloat = 1 {
      didSet {
        guard documentScale != oldValue else { return }
        canvasView?.documentScale = documentScale
        componentChildren.forEach { $0.documentScale = documentScale }
        setNeedsLayout()
      }
    }
    var layoutDirection: NativeLayoutDirection = .leftToRight {
      didSet {
        guard layoutDirection != oldValue else { return }
        componentChildren.forEach { $0.layoutDirection = layoutDirection }
        setNeedsLayout()
      }
    }

    init(
      node: NativeNode,
      images: [Int: UIImage],
      fontNames: [Int: String],
      onClick: @escaping (Int) -> Void
    ) {
      self.node = node
      self.onClick = onClick
      let promotesText = node.kind == .text
      let promotesImage = node.kind == .image
      let drawingCommands = node.commands.filter {
        !(promotesText && $0.kind == 17) && !(promotesImage && $0.kind == 19)
      }
      canvasView =
        drawingCommands.isEmpty
        ? nil : NativeCanvasView(commands: drawingCommands, images: images, fontNames: fontNames)
      textLabels =
        promotesText
        ? node.commands.filter { $0.kind == 17 }.map {
          NativeTextLabel(command: $0, fontNames: fontNames)
        } : []
      imageViews =
        promotesImage
        ? node.commands.compactMap { command in
          guard command.kind == 19, let draw = command.image, let image = images[draw.imageID]
          else {
            return nil
          }
          return NativeImageView(image: image, draw: draw, alpha: command.alpha)
        } : []
      componentChildren = node.children.map {
        NativeComponentView(
          node: $0, images: images, fontNames: fontNames, onClick: onClick)
      }
      semanticView = Self.makeSemanticView(for: node, onClick: onClick)
      super.init(frame: .zero)
      isOpaque = false
      backgroundColor = node.backgroundColor ?? .clear
      isHidden = node.visibility == 0
      alpha = node.visibility == 2 ? 0 : 1
      clipsToBounds = node.cornerRadius > 0
      accessibilityIdentifier = "rc-native-component-\(node.componentID)"
      if let semanticView { addSubview(semanticView) }
      if let canvasView { addSubview(canvasView) }
      textLabels.forEach(addSubview)
      imageViews.forEach(addSubview)
      componentChildren.forEach(addSubview)
      componentChildren.forEach { $0.layer.zPosition = $0.node.zIndex }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
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
        textLabels.count == local.text.count,
        imageViews.count == local.images.count,
        Self.semanticView(semanticView, matches: next),
        componentChildren.count == next.children.count
      else { return false }
      return zip(componentChildren, next.children).allSatisfy { child, childNode in
        child.canUpdate(with: childNode, images: images, fontNames: fontNames)
      }
    }

    func update(node next: NativeNode, images: [Int: UIImage], fontNames: [Int: String]) {
      precondition(canUpdate(with: next, images: images, fontNames: fontNames))
      let local = Self.localContent(for: next, images: images)
      node = next
      canvasView?.update(commands: local.drawing, images: images, fontNames: fontNames)
      zip(textLabels, local.text).forEach { label, command in
        label.update(command: command, fontNames: fontNames)
      }
      zip(imageViews, local.images).forEach { imageView, item in
        imageView.update(image: item.image, draw: item.draw, alpha: item.alpha)
      }
      zip(componentChildren, next.children).forEach { child, childNode in
        child.update(node: childNode, images: images, fontNames: fontNames)
        child.layer.zPosition = childNode.zIndex
      }
      updateSemanticView(semanticView, from: next)
      backgroundColor = next.backgroundColor ?? .clear
      isHidden = next.visibility == 0
      alpha = next.visibility == 2 ? 0 : 1
      clipsToBounds = next.cornerRadius > 0
      setNeedsLayout()
    }

    private static func localContent(
      for node: NativeNode,
      images: [Int: UIImage]
    ) -> (drawing: [NativeDrawCommand], text: [NativeDrawCommand], images: [(image: UIImage, draw: NativeImageDraw, alpha: CGFloat)]) {
      let promotesText = node.kind == .text
      let promotesImage = node.kind == .image
      let drawing = node.commands.filter {
        !(promotesText && $0.kind == 17) && !(promotesImage && $0.kind == 19)
      }
      let text = promotesText ? node.commands.filter { $0.kind == 17 } : []
      let imageItems =
        promotesImage
        ? node.commands.compactMap { command -> (UIImage, NativeImageDraw, CGFloat)? in
          guard command.kind == 19, let draw = command.image, let image = images[draw.imageID]
          else { return nil }
          return (image, draw, command.alpha)
        } : []
      return (drawing, text, imageItems)
    }

    private static func semanticView(_ view: UIView?, matches node: NativeNode) -> Bool {
      guard let descriptor = node.semanticBehavior?.descriptor else { return view == nil }
      switch descriptor.elementKind {
      case .button: return view is NativeSemanticButton
      case .toggle: return view is NativeSemanticSwitch
      case .image: return view is NativeSemanticImageView
      default:
        return (view as? NativeSemanticControl)?.kind == descriptor.elementKind
      }
    }

    var accessibilityOrder: [Any] {
      guard node.visibility == 1 else { return [] }
      let descendants = componentChildren.flatMap(\.accessibilityOrder)
      let local: [Any] =
        textLabels.filter(\.isAccessibilityElement).map { $0 as Any }
        + imageViews.filter(\.isAccessibilityElement).map { $0 as Any }
      guard let semanticView, let descriptor = node.semanticBehavior?.descriptor else {
        return local + descendants
      }
      let owner = semanticView.isAccessibilityElement ? [semanticView] : []
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
      guard isInside, let semanticView else { return nil }
      return semanticView.hitTest(convert(point, to: semanticView), with: event)
    }

    override func layoutSubviews() {
      super.layoutSubviews()
      layer.cornerRadius = node.cornerRadius * documentScale
      canvasView?.frame = bounds
      prepareStructuralChildren()
      if isStructural {
        updateStructuralSemanticFrames()
        return
      }

      switch node.kind {
      case .column: layoutColumn()
      case .row: layoutRow()
      case .box: layoutOverlay(aligned: true)
      case .text:
        textLabels.forEach {
          $0.layoutInComponent(
            bounds: bounds, documentScale: documentScale, layoutDirection: layoutDirection)
        }
      case .image: imageViews.forEach { $0.frame = bounds }
      default: layoutOverlay(aligned: false)
      }
      semanticView?.frame = bounds
      updateStructuralSemanticFrames()
    }

    func preferredSize(in available: CGSize) -> CGSize {
      let insets = scaledPadding
      let contentAvailable = CGSize(
        width: max(available.width - insets.left - insets.right, 0),
        height: max(available.height - insets.top - insets.bottom, 0))
      let items = flattenedLayoutItems
      let intrinsic: CGSize
      switch node.kind {
      case .text:
        intrinsic =
          textLabels.first?.preferredSize(
            maximumWidth: contentAvailable.width, documentScale: documentScale) ?? .zero
      case .image:
        intrinsic = imageViews.first?.image?.size ?? .zero
      case .column:
        let sizes = items.map { $0.preferredSize(in: contentAvailable) }
        intrinsic = CGSize(
          width: sizes.map(\.width).max() ?? 0,
          height: sizes.reduce(0) { $0 + $1.height }
            + scaledSpacing * CGFloat(max(sizes.count - 1, 0)))
      case .row:
        let sizes = items.map { $0.preferredSize(in: contentAvailable) }
        intrinsic = CGSize(
          width: sizes.reduce(0) { $0 + $1.width }
            + scaledSpacing * CGFloat(max(sizes.count - 1, 0)),
          height: sizes.map(\.height).max() ?? 0)
      default:
        let sizes = items.map { $0.preferredSize(in: contentAvailable) }
        intrinsic = CGSize(
          width: sizes.map(\.width).max() ?? 0,
          height: sizes.map(\.height).max() ?? 0)
      }
      return applyDimensions(
        to: CGSize(
          width: intrinsic.width + insets.left + insets.right,
          height: intrinsic.height + insets.top + insets.bottom),
        available: available)
    }

    private var isStructural: Bool {
      (node.kind == .content || node.kind == .group || node.kind == .canvas)
        && canvasView == nil && textLabels.isEmpty && imageViews.isEmpty
        && node.semanticBehavior?.acceptsPointerAction != true
        && node.backgroundColor == nil
        && node.visibility == 1 && node.widthType == 2 && node.heightType == 2
        && node.minimumWidth == 0 && node.minimumHeight == 0
        && node.maximumWidth == nil && node.maximumHeight == nil
        && node.padding == .zero && node.offset == .zero && node.zIndex == 0
    }

    private var flattenedLayoutItems: [NativeComponentView] {
      componentChildren.filter { $0.node.visibility != 0 }.flatMap { child in
        child.isStructural ? child.flattenedLayoutItems : [child]
      }
    }

    private var scaledPadding: UIEdgeInsets {
      UIEdgeInsets(
        top: node.padding.top * documentScale,
        left: node.padding.left * documentScale,
        bottom: node.padding.bottom * documentScale,
        right: node.padding.right * documentScale)
    }

    private var scaledSpacing: CGFloat { node.spacing * documentScale }

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
      guard isStructural, let semanticView else { return }
      let renderedBounds = flattenedLayoutItems
        .filter { !$0.isHidden && $0.alpha > 0.01 }
        .map { convert($0.bounds, from: $0) }
        .filter { !$0.isEmpty && !$0.isNull }
        .reduce(CGRect.null) { $0.union($1) }
      let clippedBounds = renderedBounds.intersection(bounds)
      semanticView.frame = clippedBounds.isNull ? .zero : clippedBounds
    }

    private func layoutOverlay(aligned: Bool) {
      let insets = scaledPadding
      let content = bounds.inset(by: insets)
      flattenedLayoutItems.forEach { child in
        let size = child.preferredSize(in: content.size)
        child.frame = child.offsetFrame(
          aligned
            ? alignedFrame(size: size, in: content)
            : CGRect(origin: content.origin, size: content.size))
      }
    }

    private func layoutColumn() {
      let content = bounds.inset(by: scaledPadding)
      let items = flattenedLayoutItems
      let sizes = items.map { $0.preferredSize(in: content.size) }
      let weightedHeights = NativeLinearLayout.allocateWeighted(
        available: content.height,
        naturalSizes: sizes.map(\.height),
        weights: items.map { child in
          guard child.node.heightType == 3 else { return nil }
          return max(child.node.heightValue, .leastNonzeroMagnitude)
        })
      let heights = zip(items, zip(sizes, weightedHeights)).map { child, values in
        child.applyDimensions(
          to: values.0,
          available: CGSize(width: content.width, height: values.1)
        ).height
      }
      let positions = NativeLinearLayout.positions(
        total: content.height,
        sizes: heights,
        positioning: node.verticalPositioning,
        spacing: scaledSpacing)
      for (index, child) in items.enumerated() {
        let size = CGSize(width: sizes[index].width, height: heights[index])
        let x = horizontalOrigin(for: size.width, in: content)
        child.frame = child.offsetFrame(
          CGRect(
            x: x, y: content.minY + positions[index], width: size.width, height: size.height))
      }
    }

    private func layoutRow() {
      let content = bounds.inset(by: scaledPadding)
      let items = flattenedLayoutItems
      let natural = items.map { $0.preferredSize(in: content.size) }
      let allocatedWidths = NativeLinearLayout.allocateWeighted(
        available: content.width,
        naturalSizes: natural.map(\.width),
        weights: items.map { child in
          guard child.node.widthType == 3 else { return nil }
          return max(child.node.widthValue, .leastNonzeroMagnitude)
        })
      let widths = zip(items, zip(natural, allocatedWidths)).map { child, values in
        child.applyDimensions(
          to: values.0,
          available: CGSize(width: values.1, height: content.height)
        ).width
      }
      let positions = NativeLinearLayout.positions(
        total: content.width,
        sizes: widths,
        positioning: node.horizontalPositioning,
        spacing: scaledSpacing,
        direction: layoutDirection)
      for (index, child) in items.enumerated() {
        // Weighted children must see their final main-axis constraint before cross-axis placement;
        // wrapping text can be taller at its allocated width than at the row's full width.
        let remeasured =
          child.preferredSize(in: CGSize(width: widths[index], height: content.height))
        let size = CGSize(width: widths[index], height: remeasured.height)
        let y: CGFloat
        switch node.verticalPositioning {
        case 2: y = content.midY - size.height / 2
        case 5: y = content.maxY - size.height
        default: y = content.minY
        }
        child.frame = child.offsetFrame(
          CGRect(
            x: content.minX + positions[index], y: y, width: size.width, height: size.height))
      }
    }

    private func alignedFrame(size: CGSize, in rect: CGRect) -> CGRect {
      let x = horizontalOrigin(for: size.width, in: rect)
      let y: CGFloat
      switch node.verticalPositioning {
      case 2: y = rect.midY - size.height / 2
      case 5: y = rect.maxY - size.height
      default: y = rect.minY
      }
      return CGRect(origin: CGPoint(x: x, y: y), size: size)
    }

    private func horizontalOrigin(for width: CGFloat, in rect: CGRect) -> CGFloat {
      switch node.horizontalPositioning {
      case 2: return rect.midX - width / 2
      case 3: return layoutDirection == .rightToLeft ? rect.minX : rect.maxX - width
      default: return layoutDirection == .rightToLeft ? rect.maxX - width : rect.minX
      }
    }

    private func applyDimensions(to intrinsic: CGSize, available: CGSize) -> CGSize {
      return CGSize(
        width: NativeLayoutDimension(
          type: node.widthType,
          value: node.widthValue * documentScale,
          minimum: node.minimumWidth * documentScale,
          maximum: node.maximumWidth.map { $0 * documentScale }
        ).resolve(intrinsic: intrinsic.width, available: available.width),
        height: NativeLayoutDimension(
          type: node.heightType,
          value: node.heightValue * documentScale,
          minimum: node.minimumHeight * documentScale,
          maximum: node.maximumHeight.map { $0 * documentScale }
        ).resolve(intrinsic: intrinsic.height, available: available.height))
    }

    private func offsetFrame(_ frame: CGRect) -> CGRect {
      frame.offsetBy(
        dx: node.offset.x * documentScale,
        dy: node.offset.y * documentScale)
    }

    private static func makeSemanticView(
      for node: NativeNode,
      onClick: @escaping (Int) -> Void
    ) -> UIView? {
      guard let behavior = node.semanticBehavior else { return nil }
      let descriptor = behavior.descriptor
      let view: UIView
      switch descriptor.elementKind {
      case .button: view = NativeSemanticButton(componentID: behavior.componentID)
      case .toggle: view = NativeSemanticSwitch(componentID: behavior.componentID)
      case .image: view = NativeSemanticImageView(componentID: behavior.componentID)
      default:
        view = NativeSemanticControl(
          componentID: behavior.componentID,
          kind: descriptor.elementKind)
      }
      configureSemanticView(view, node: node, behavior: behavior, onClick: onClick)
      return view
    }

    private func updateSemanticView(_ view: UIView?, from node: NativeNode) {
      guard let view, let behavior = node.semanticBehavior else { return }
      Self.configureSemanticView(view, node: node, behavior: behavior, onClick: onClick)
    }

    private static func configureSemanticView(
      _ view: UIView,
      node: NativeNode,
      behavior: NativeSemanticBehavior,
      onClick: @escaping (Int) -> Void
    ) {
      let descriptor = behavior.descriptor
      let action =
        descriptor.isEnabled && !behavior.clickActionTypes.isEmpty ? onClick : nil
      if let activating = view as? any NativeSemanticActivating {
        activating.componentID = behavior.componentID
        activating.action = action
      }
      if let control = view as? UIControl { control.isEnabled = descriptor.isEnabled }
      view.backgroundColor = .clear
      // Semantic-only elements remain in the accessibility tree without swallowing pointer input.
      view.isUserInteractionEnabled =
        descriptor.isEnabled && action != nil && behavior.acceptsPointerAction
      let mergedLabels = node.localAccessibilityLabels + node.descendantAccessibilityLabels
      let label =
        descriptor.resolvedLabel(
          descendantLabels: descriptor.mode == .merge ? mergedLabels : [])
        ?? (node.hasAccessibilitySemantics ? nil : node.firstText)
      let traits = accessibilityTraits(for: descriptor)
      view.isAccessibilityElement =
        label != nil || descriptor.stateDescription != nil || !traits.isEmpty || action != nil
      view.accessibilityLabel = label
      view.accessibilityValue = descriptor.stateDescription
      view.accessibilityTraits = traits
      view.accessibilityIdentifier =
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

  @MainActor
  private protocol NativeSemanticActivating: AnyObject {
    var componentID: Int { get set }
    var action: ((Int) -> Void)? { get set }
  }

  private final class NativeSemanticButton: UIButton, NativeSemanticActivating {
    var componentID: Int
    var action: ((Int) -> Void)?

    init(componentID: Int) {
      self.componentID = componentID
      action = nil
      super.init(frame: .zero)
      addTarget(self, action: #selector(activate), for: .touchUpInside)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    @objc private func activate() {
      guard isEnabled else { return }
      action?(componentID)
    }

    override func accessibilityActivate() -> Bool {
      guard isEnabled, let action else { return false }
      action(componentID)
      return true
    }
  }

  private final class NativeSemanticSwitch: UISwitch, NativeSemanticActivating {
    var componentID: Int
    var action: ((Int) -> Void)?

    init(componentID: Int) {
      self.componentID = componentID
      action = nil
      super.init(frame: .zero)
      // The document still owns pixels. The native switch owns identity, focus, value semantics,
      // and activation without drawing a second switch over the captured document appearance.
      layer.opacity = 0
      addTarget(self, action: #selector(activate), for: .valueChanged)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    @objc private func activate() {
      guard isEnabled else { return }
      action?(componentID)
    }

    override func accessibilityActivate() -> Bool {
      guard isEnabled, let action else { return false }
      action(componentID)
      return true
    }
  }

  private final class NativeSemanticImageView: UIImageView, NativeSemanticActivating {
    var componentID: Int
    var action: ((Int) -> Void)?

    init(componentID: Int) {
      self.componentID = componentID
      action = nil
      super.init(frame: .zero)
      addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(activate)))
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    @objc private func activate() {
      action?(componentID)
    }

    override func accessibilityActivate() -> Bool {
      guard let action else { return false }
      action(componentID)
      return true
    }
  }

  private final class NativeSemanticControl: UIControl, NativeSemanticActivating {
    var componentID: Int
    var action: ((Int) -> Void)?
    let kind: NativeAccessibilityElementKind

    init(componentID: Int, kind: NativeAccessibilityElementKind) {
      self.componentID = componentID
      self.kind = kind
      action = nil
      super.init(frame: .zero)
      addTarget(self, action: #selector(activate), for: .touchUpInside)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    @objc private func activate() {
      guard isEnabled else { return }
      action?(componentID)
    }

    override func accessibilityActivate() -> Bool {
      guard isEnabled, let action else { return false }
      action(componentID)
      return true
    }
  }

  final class NativeImageView: UIImageView {
    private var drawCommand: NativeImageDraw

    init(image: UIImage, draw: NativeImageDraw, alpha: CGFloat) {
      drawCommand = draw
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

    func update(image: UIImage, draw: NativeImageDraw, alpha: CGFloat) {
      self.image = image
      drawCommand = draw
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
      image.draw(in: destination)
    }
  }

  /// A Remote Compose text primitive promoted to a real UIKit text element. Geometry and font
  /// selection remain approximate until the native lane has a resolved layout/text profile.
  final class NativeTextLabel: UILabel {
    private var command: NativeDrawCommand
    private var fontNames: [Int: String]
    private var documentScale: CGFloat = 1
    private var layoutDirection: NativeLayoutDirection = .leftToRight

    init(command: NativeDrawCommand, fontNames: [Int: String]) {
      self.command = command
      self.fontNames = fontNames
      super.init(frame: .zero)
      text = command.text
      textColor = command.color.withAlphaComponent(command.alpha)
      backgroundColor = .clear
      configureParagraph(layoutDirection: .leftToRight)
      adjustsFontForContentSizeCategory = true
      isAccessibilityElement = true
      accessibilityIdentifier = "rc-native-text"
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
      configureParagraph(layoutDirection: layoutDirection)
      configureFont(documentScale: documentScale)
      setNeedsLayout()
    }

    func preferredSize(maximumWidth: CGFloat, documentScale: CGFloat) -> CGSize {
      self.documentScale = documentScale
      configureFont(documentScale: documentScale)
      return sizeThatFits(CGSize(width: maximumWidth, height: .greatestFiniteMagnitude))
    }

    func layoutInComponent(
      bounds: CGRect, documentScale: CGFloat, layoutDirection: NativeLayoutDirection
    ) {
      configureParagraph(layoutDirection: layoutDirection)
      let preferred = preferredSize(maximumWidth: bounds.width, documentScale: documentScale)
      frame = CGRect(
        origin: .zero,
        size: CGSize(width: bounds.width, height: min(preferred.height, bounds.height)))
    }

    private func configureFont(documentScale: CGFloat) {
      font = NativeTextAttributes.font(
        for: command, scale: documentScale, fontNames: fontNames, scalesForDynamicType: true)
      attributedText = NativeTextAttributes.string(
        for: command, font: font, scale: documentScale,
        layoutDirection: effectiveUserInterfaceLayoutDirection)
    }

    private func configureParagraph(layoutDirection: NativeLayoutDirection) {
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
      clipsToBounds = style.overflow != 2
      semanticContentAttribute =
        layoutDirection == .rightToLeft ? .forceRightToLeft : .forceLeftToRight
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
      case "serif": descriptor = descriptor.withDesign(.serif) ?? descriptor
      case "monospace": descriptor = descriptor.withDesign(.monospaced) ?? descriptor
      case "sans-serif", "default", nil: break
      default: break
      }
      if let postScriptName = fontNames[command.textStyle.fontFamilyID],
        let embedded = UIFont(name: postScriptName, size: size)
      {
        descriptor = embedded.fontDescriptor
      }
      if command.textStyle.fontStyle & 2 != 0 {
        descriptor =
          descriptor.withSymbolicTraits(descriptor.symbolicTraits.union(.traitItalic)) ?? descriptor
      }
      let resolved = UIFont(descriptor: descriptor, size: size)
      return scalesForDynamicType ? UIFontMetrics.default.scaledFont(for: resolved) : resolved
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
      paragraph.lineBreakMode = lineBreakMode(command.textStyle.overflow)
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

  final class NativeCanvasView: UIView {
    private var commands: [NativeDrawCommand]
    private var images: [Int: UIImage]
    private var fontNames: [Int: String]
    private var renderSignature: Int
    var documentScale: CGFloat = 1 {
      didSet { if documentScale != oldValue { setNeedsDisplay() } }
    }

    init(commands: [NativeDrawCommand], images: [Int: UIImage], fontNames: [Int: String]) {
      self.commands = commands
      self.images = images
      self.fontNames = fontNames
      renderSignature = Self.signature(commands: commands, images: images, fontNames: fontNames)
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
      let nextSignature = Self.signature(commands: commands, images: images, fontNames: fontNames)
      self.commands = commands
      self.images = images
      self.fontNames = fontNames
      guard nextSignature != renderSignature else { return }
      renderSignature = nextSignature
      setNeedsDisplay()
    }

    private static func signature(
      commands: [NativeDrawCommand], images: [Int: UIImage], fontNames: [Int: String]
    ) -> Int {
      var hasher = Hasher()
      for command in commands {
        hasher.combine(command.kind)
        command.values.forEach { hasher.combine($0) }
        command.color.cgColor.components?.forEach { hasher.combine($0) }
        hasher.combine(command.alpha)
        hasher.combine(command.strokeWidth)
        hasher.combine(command.isStroke)
        hasher.combine(command.strokeCap)
        hasher.combine(command.strokeJoin)
        hasher.combine(command.blendMode)
        hasher.combine(command.textSize)
        hasher.combine(command.textWeight)
        hasher.combine(command.text)
        hasher.combine(command.textStyle.fontStyle)
        hasher.combine(command.textStyle.fontFamilyID)
        hasher.combine(command.textStyle.fontFamilyName)
        hasher.combine(command.textStyle.alignment)
        hasher.combine(command.textStyle.overflow)
        hasher.combine(command.textStyle.maxLines)
        hasher.combine(command.textStyle.letterSpacing)
        hasher.combine(command.textStyle.lineHeightAdd)
        hasher.combine(command.textStyle.lineHeightMultiplier)
        hasher.combine(command.textStyle.breakStrategy)
        hasher.combine(command.textStyle.hyphenation)
        hasher.combine(command.textStyle.isJustified)
        hasher.combine(command.textStyle.isUnderlined)
        hasher.combine(command.textStyle.isStruckThrough)
        for segment in command.path {
          hasher.combine(segment.kind)
          segment.values.forEach { hasher.combine($0) }
        }
        hasher.combine(command.pathWinding)
        if let gradient = command.gradient {
          hasher.combine(gradient.kind)
          gradient.colors.forEach { $0.components?.forEach { hasher.combine($0) } }
          gradient.stops.forEach { hasher.combine($0) }
          gradient.values.forEach { hasher.combine($0) }
          hasher.combine(gradient.tileMode)
        }
        if let image = command.image {
          hasher.combine(image.imageID)
          hasher.combine(image.source.origin.x)
          hasher.combine(image.source.origin.y)
          hasher.combine(image.source.size.width)
          hasher.combine(image.source.size.height)
          hasher.combine(image.destination.origin.x)
          hasher.combine(image.destination.origin.y)
          hasher.combine(image.destination.size.width)
          hasher.combine(image.destination.size.height)
          hasher.combine(image.scaleType)
          hasher.combine(image.scaleFactor)
        }
        hasher.combine(command.textureImageID)
      }
      for id in commands.compactMap({ $0.image?.imageID ?? $0.textureImageID }).sorted() {
        hasher.combine(id)
        if let image = images[id] { hasher.combine(ObjectIdentifier(image)) }
      }
      for (id, name) in fontNames.sorted(by: { $0.key < $1.key }) {
        hasher.combine(id)
        hasher.combine(name)
      }
      return hasher.finalize()
    }

    override func draw(_ rect: CGRect) {
      guard let context = UIGraphicsGetCurrentContext() else { return }
      context.scaleBy(x: documentScale, y: documentScale)
      commands.forEach { draw($0, in: context) }
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

      switch command.kind {
      case 0: context.saveGState()
      case 1: context.restoreGState()
      case 2: context.translateBy(x: v[0], y: v[1])
      case 3:
        let pivot = CGPoint(x: v[2].isNaN ? 0 : v[2], y: v[3].isNaN ? 0 : v[3])
        context.translateBy(x: pivot.x, y: pivot.y)
        context.scaleBy(x: v[0], y: v[1])
        context.translateBy(x: -pivot.x, y: -pivot.y)
      case 4:
        let pivot = CGPoint(x: v[1].isNaN ? 0 : v[1], y: v[2].isNaN ? 0 : v[2])
        context.translateBy(x: pivot.x, y: pivot.y)
        context.rotate(by: v[0] * .pi / 180)
        context.translateBy(x: -pivot.x, y: -pivot.y)
      case 5: context.concatenate(CGAffineTransform(a: 1, b: v[1], c: v[0], d: 1, tx: 0, ty: 0))
      case 6: context.clip(to: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]))
      case 7:
        context.addPath(NativePathBuilder.make(command.path))
        context.clip(using: command.pathWinding == 1 ? .evenOdd : .winding)
      case 10:
        paint(CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]), command, context)
      case 11:
        paintEllipse(
          CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]), command, context)
      case 12:
        paintEllipse(
          CGRect(x: v[0] - v[2], y: v[1] - v[2], width: v[2] * 2, height: v[2] * 2), command,
          context)
      case 13:
        let path = CGMutablePath()
        path.move(to: CGPoint(x: v[0], y: v[1]))
        path.addLine(to: CGPoint(x: v[2], y: v[3]))
        paint(path, command, context)
      case 14:
        let path = UIBezierPath(
          roundedRect: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]),
          cornerRadius: max(v[4], v[5]))
        paint(path.cgPath, command, context)
      case 15, 16: drawArc(command, context)
      case 17: drawText(command)
      case 18:
        paint(
          NativePathBuilder.make(command.path), command, context,
          fillRule: command.pathWinding == 1 ? .evenOdd : .winding)
      case 19: drawImage(command, context)
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
      fillRule: CGPathFillRule = .winding
    ) {
      // Destination leaves the existing buffer unchanged, but must not suppress ordered
      // transforms, clipping, or save/restore commands around the draw.
      guard command.blendMode != 2 else { return }
      context.addPath(path)
      if let textureImageID = command.textureImageID, let image = images[textureImageID] {
        context.saveGState()
        if command.isStroke { context.replacePathWithStrokedPath() }
        context.clip(using: command.isStroke ? .winding : fillRule)
        UIColor(patternImage: image).setFill()
        context.fill(context.boundingBoxOfClipPath)
        context.restoreGState()
        return
      }
      guard let gradient = command.gradient else {
        context.drawPath(
          using: command.isStroke ? .stroke : (fillRule == .evenOdd ? .eoFill : .fill))
        return
      }
      context.saveGState()
      if command.isStroke { context.replacePathWithStrokedPath() }
      context.clip(using: command.isStroke ? .winding : fillRule)
      NativeGradientRenderer.draw(gradient, in: context)
      context.restoreGState()
    }

    private func drawArc(_ command: NativeDrawCommand, _ context: CGContext) {
      let v = command.values
      let center = CGPoint(x: (v[0] + v[2]) / 2, y: (v[1] + v[3]) / 2)
      let radius = min(v[2] - v[0], v[3] - v[1]) / 2
      let start = v[4] * .pi / 180
      let end = (v[4] + v[5]) * .pi / 180
      if command.kind == 16 { context.move(to: center) }
      context.addArc(
        center: center, radius: radius, startAngle: start, endAngle: end, clockwise: false)
      if command.kind == 16 { context.closePath() }
      let path = context.path
      context.beginPath()
      if let path { paint(path, command, context) }
    }

    private func drawText(_ command: NativeDrawCommand) {
      guard command.blendMode != 2 else { return }
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
      let baseline = command.values[1] - height * ((panY + 1) / 2)
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
        command.blendMode != 2,
        let draw = command.image,
        let source = images[draw.imageID]?.cgImage,
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
