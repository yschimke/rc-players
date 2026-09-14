#if canImport(UIKit)
  import RcComposePlayer
  import UIKit

  struct NativeDocument {
    let size: CGSize
    let root: NativeNode
    let diagnostics: RemoteComposeNativePlayerDiagnostics

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
    let padding: UIEdgeInsets
    let cornerRadius: CGFloat
    let backgroundColor: UIColor?
    let horizontalPositioning: Int
    let verticalPositioning: Int
    let spacing: CGFloat

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
    let textSize: CGFloat
    let textWeight: CGFloat
    let text: String?

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
      textSize = CGFloat(snapshot.textSize)
      textWeight = CGFloat(snapshot.textWeight)
      text = snapshot.text
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
        componentView.frame = bounds
        return
      }
      let scale = min(bounds.width / document.size.width, bounds.height / document.size.height)
      let size = CGSize(width: document.size.width * scale, height: document.size.height * scale)
      componentView.frame = CGRect(
        x: bounds.midX - size.width / 2,
        y: bounds.midY - size.height / 2,
        width: size.width,
        height: size.height)
      componentView.documentScale = scale
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
      clipsToBounds = node.cornerRadius > 0
      accessibilityIdentifier = "rc-native-component-\(node.componentID)"
      if let canvasView { addSubview(canvasView) }
      textLabels.forEach(addSubview)
      componentChildren.forEach(addSubview)
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
        textLabels.forEach { $0.layoutInComponent(bounds: bounds, documentScale: documentScale) }
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
    }

    private var flattenedLayoutItems: [NativeComponentView] {
      componentChildren.flatMap { child in
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
        child.frame =
          aligned
          ? alignedFrame(size: size, in: content)
          : CGRect(origin: content.origin, size: content.size)
      }
    }

    private func layoutColumn() {
      let content = bounds.inset(by: scaledPadding)
      let items = flattenedLayoutItems
      let sizes = items.map { $0.preferredSize(in: content.size) }
      let totalHeight =
        sizes.reduce(0) { $0 + $1.height }
        + scaledSpacing * CGFloat(max(items.count - 1, 0))
      var y: CGFloat
      switch node.verticalPositioning {
      case 2: y = content.midY - totalHeight / 2
      case 5: y = content.maxY - totalHeight
      default: y = content.minY
      }
      for (index, child) in items.enumerated() {
        let size = sizes[index]
        let x: CGFloat
        switch node.horizontalPositioning {
        case 2: x = content.midX - size.width / 2
        case 3: x = content.maxX - size.width
        default: x = content.minX
        }
        child.frame = CGRect(x: x, y: y, width: size.width, height: size.height)
        y += size.height + scaledSpacing
      }
    }

    private func layoutRow() {
      let content = bounds.inset(by: scaledPadding)
      let items = flattenedLayoutItems
      let spacing = scaledSpacing * CGFloat(max(items.count - 1, 0))
      let natural = items.map { $0.preferredSize(in: content.size) }
      let fixedWidth = zip(items, natural).reduce(CGFloat.zero) { result, pair in
        result + (pair.0.node.widthType == 3 ? 0 : pair.1.width)
      }
      let weightCount = items.filter { $0.node.widthType == 3 }.count
      let weightWidth =
        weightCount == 0
        ? 0 : max(content.width - fixedWidth - spacing, 0) / CGFloat(weightCount)
      let sizes = zip(items, natural).map { child, size in
        child.node.widthType == 3 ? CGSize(width: weightWidth, height: size.height) : size
      }
      let totalWidth = sizes.reduce(0) { $0 + $1.width } + spacing
      var x: CGFloat
      switch node.horizontalPositioning {
      case 2: x = content.midX - totalWidth / 2
      case 3: x = content.maxX - totalWidth
      default: x = content.minX
      }
      for (index, child) in items.enumerated() {
        let size = sizes[index]
        let y: CGFloat
        switch node.verticalPositioning {
        case 2: y = content.midY - size.height / 2
        case 5: y = content.maxY - size.height
        default: y = content.minY
        }
        child.frame = CGRect(x: x, y: y, width: size.width, height: size.height)
        x += size.width + scaledSpacing
      }
    }

    private func alignedFrame(size: CGSize, in rect: CGRect) -> CGRect {
      let x: CGFloat
      switch node.horizontalPositioning {
      case 2: x = rect.midX - size.width / 2
      case 3: x = rect.maxX - size.width
      default: x = rect.minX
      }
      let y: CGFloat
      switch node.verticalPositioning {
      case 2: y = rect.midY - size.height / 2
      case 5: y = rect.maxY - size.height
      default: y = rect.minY
      }
      return CGRect(origin: CGPoint(x: x, y: y), size: size)
    }

    private func applyDimensions(to intrinsic: CGSize, available: CGSize) -> CGSize {
      func resolved(type: Int, value: CGFloat, intrinsic: CGFloat, available: CGFloat) -> CGFloat {
        switch type {
        case 0, 6: return max(value * documentScale, 0)
        case 1, 7, 8: return available
        case 3: return available
        default: return intrinsic
        }
      }
      return CGSize(
        width: min(
          resolved(
            type: node.widthType, value: node.widthValue, intrinsic: intrinsic.width,
            available: available.width), available.width),
        height: min(
          max(
            resolved(
              type: node.heightType, value: node.heightValue, intrinsic: intrinsic.height,
              available: available.height),
            node.minimumHeight * documentScale), available.height))
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
      numberOfLines = 0
      lineBreakMode = .byWordWrapping
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

    func layoutInComponent(bounds: CGRect, documentScale: CGFloat) {
      frame = CGRect(
        origin: .zero,
        size: preferredSize(maximumWidth: bounds.width, documentScale: documentScale))
    }

    private func configureFont(documentScale: CGFloat) {
      let normalizedWeight = min(max((command.textWeight - 400) / 500, -1), 1)
      font = .systemFont(
        ofSize: max(command.textSize * documentScale, 1),
        weight: UIFont.Weight(rawValue: normalizedWeight))
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
      context.setLineWidth(max(command.strokeWidth, 0.5))

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
        context.move(to: CGPoint(x: v[0], y: v[1]))
        context.addLine(to: CGPoint(x: v[2], y: v[3]))
        context.strokePath()
      case 14:
        let path = UIBezierPath(
          roundedRect: CGRect(x: v[0], y: v[1], width: v[2] - v[0], height: v[3] - v[1]),
          cornerRadius: max(v[4], v[5]))
        paint(path.cgPath, command, context)
      case 15, 16: drawArc(command, context)
      case 17: drawText(command)
      default: break
      }
    }

    private func paint(_ rect: CGRect, _ command: NativeDrawCommand, _ context: CGContext) {
      command.isStroke ? context.stroke(rect) : context.fill(rect)
    }

    private func paintEllipse(_ rect: CGRect, _ command: NativeDrawCommand, _ context: CGContext) {
      command.isStroke ? context.strokeEllipse(in: rect) : context.fillEllipse(in: rect)
    }

    private func paint(_ path: CGPath, _ command: NativeDrawCommand, _ context: CGContext) {
      context.addPath(path)
      command.isStroke ? context.strokePath() : context.fillPath()
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
      command.isStroke ? context.strokePath() : context.fillPath()
    }

    private func drawText(_ command: NativeDrawCommand) {
      guard let text = command.text else { return }
      let normalizedWeight = min(max((command.textWeight - 400) / 500, -1), 1)
      let font = UIFont.systemFont(
        ofSize: command.textSize,
        weight: UIFont.Weight(rawValue: normalizedWeight))
      let attributes: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: command.color.withAlphaComponent(command.alpha),
      ]
      let size = (text as NSString).size(withAttributes: attributes)
      let panX = command.values[2]
      let panY = command.values[3]
      let origin = CGPoint(
        x: command.values[0] - size.width * ((panX + 1) / 2),
        y: command.values[1] - font.ascender - size.height * ((panY + 1) / 2))
      (text as NSString).draw(at: origin, withAttributes: attributes)
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
