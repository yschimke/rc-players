#if canImport(UIKit)
  import CoreText
  import RcComposePlayer
  import UIKit

  struct NativeDocument {
    let size: CGSize
    let root: NativeNode
    let diagnostics: RemoteComposeNativePlayerDiagnostics
    let rootSizing: Int
    let rootMode: Int
    let rootAlignment: Int

    init(snapshot: RcNativeDocumentSnapshot) {
      size = CGSize(width: Int(snapshot.width), height: Int(snapshot.height))
      root = NativeNode(snapshot: snapshot.root)
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
    }
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

      init(rawValue: Int32) {
        switch rawValue {
        case 0: self = .root
        case 1: self = .content
        case 2: self = .canvas
        case 4: self = .box
        case 5: self = .row
        case 6: self = .column
        case 7: self = .text
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
    private let document: NativeDocument
    private let componentView: NativeComponentView

    init(document: NativeDocument) {
      self.document = document
      componentView = NativeComponentView(node: document.root)
      super.init(frame: .zero)
      isOpaque = false
      backgroundColor = .clear
      addSubview(componentView)
      accessibilityIdentifier = "rc-native-document"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
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
    private let node: NativeNode
    private let canvasView: NativeCanvasView?
    private let textLabels: [NativeTextLabel]
    private let componentChildren: [NativeComponentView]
    private let semanticView: UIView?
    var documentScale: CGFloat = 1 {
      didSet {
        canvasView?.documentScale = documentScale
        componentChildren.forEach { $0.documentScale = documentScale }
        setNeedsLayout()
      }
    }
    var layoutDirection: NativeLayoutDirection = .leftToRight {
      didSet {
        componentChildren.forEach { $0.layoutDirection = layoutDirection }
        setNeedsLayout()
      }
    }

    init(node: NativeNode) {
      self.node = node
      let promotesText = node.kind == .text
      let drawingCommands = promotesText ? node.commands.filter { $0.kind != 17 } : node.commands
      canvasView = drawingCommands.isEmpty ? nil : NativeCanvasView(commands: drawingCommands)
      textLabels =
        promotesText
        ? node.commands.filter { $0.kind == 17 }.map(NativeTextLabel.init) : []
      componentChildren = node.children.map(NativeComponentView.init)
      semanticView = Self.makeSemanticView(for: node)
      super.init(frame: .zero)
      isOpaque = false
      backgroundColor = node.backgroundColor ?? .clear
      isHidden = node.visibility == 0
      alpha = node.visibility == 2 ? 0 : 1
      clipsToBounds = node.cornerRadius > 0
      accessibilityIdentifier = "rc-native-component-\(node.componentID)"
      if let canvasView { addSubview(canvasView) }
      textLabels.forEach(addSubview)
      componentChildren.forEach(addSubview)
      componentChildren.forEach { $0.layer.zPosition = $0.node.zIndex }
      if let semanticView { addSubview(semanticView) }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
    }

    override func layoutSubviews() {
      super.layoutSubviews()
      layer.cornerRadius = node.cornerRadius * documentScale
      canvasView?.frame = bounds
      prepareStructuralChildren()
      if isStructural {
        semanticView?.frame = bounds
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
      default: layoutOverlay(aligned: false)
      }
      semanticView?.frame = bounds
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
        && canvasView == nil && textLabels.isEmpty && semanticView == nil
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

    private static func makeSemanticView(for node: NativeNode) -> UIView? {
      // AndroidX role 0 is Button. A click modifier without explicit semantics is promoted too.
      guard node.semanticRole == 0 || node.isClickable else { return nil }
      let button = UIButton(type: .custom)
      button.backgroundColor = .clear
      button.isEnabled = node.isEnabled
      button.isAccessibilityElement = true
      button.accessibilityLabel = node.accessibilityLabel ?? node.firstText
      button.accessibilityIdentifier = "rc-native-button-\(node.componentID)"
      // Action dispatch is intentionally not wired in the static POC. Keeping this as a real
      // UIButton proves native hit testing, focus, traits, and hierarchy without redrawing it.
      return button
    }
  }

  /// A Remote Compose text primitive promoted to a real UIKit text element. Geometry and font
  /// selection remain approximate until the native lane has a resolved layout/text profile.
  final class NativeTextLabel: UILabel {
    private let command: NativeDrawCommand

    init(command: NativeDrawCommand) {
      self.command = command
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

    func preferredSize(maximumWidth: CGFloat, documentScale: CGFloat) -> CGSize {
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
        for: command, scale: documentScale, scalesForDynamicType: true)
      attributedText = NativeTextAttributes.string(
        for: command, font: font, scale: documentScale,
        layoutDirection: effectiveUserInterfaceLayoutDirection)
    }

    private func configureParagraph(layoutDirection: NativeLayoutDirection) {
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
      for command: NativeDrawCommand, scale: CGFloat, scalesForDynamicType: Bool = false
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
    private let commands: [NativeDrawCommand]
    var documentScale: CGFloat = 1 {
      didSet { setNeedsDisplay() }
    }

    init(commands: [NativeDrawCommand]) {
      self.commands = commands
      super.init(frame: .zero)
      isOpaque = false
      backgroundColor = .clear
      contentMode = .redraw
      accessibilityIdentifier = "rc-native-canvas"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
      fatalError("init(coder:) is not supported")
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
      let font = NativeTextAttributes.font(for: command, scale: 1)
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
