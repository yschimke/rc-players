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
        unsupportedOpcodes: snapshot.unsupportedOpcodes.map { Int(truncating: $0) },
        notes: snapshot.notes)
    }
  }

  struct NativeNode {
    enum Kind {
      case root
      case content
      case canvas
      case group

      init(rawValue: Int32) {
        switch rawValue {
        case 0: self = .root
        case 1: self = .content
        case 2: self = .canvas
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

    init(snapshot: RcNativeNodeSnapshot) {
      kind = Kind(rawValue: snapshot.kind)
      componentID = Int(snapshot.componentId)
      commands = snapshot.commands.map(NativeDrawCommand.init)
      children = snapshot.children.map(NativeNode.init)
      semanticRole = Int(snapshot.semanticRole)
      isClickable = snapshot.clickable
      isEnabled = snapshot.enabled
      accessibilityLabel = snapshot.semanticLabel
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

  /// One UIView per Remote Compose component. POC layout overlays children in document space;
  /// layout managers can replace this policy without changing the renderer or public controller.
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
      let drawingCommands = node.commands.filter { $0.kind != 17 }
      canvasView = drawingCommands.isEmpty ? nil : NativeCanvasView(commands: drawingCommands)
      textLabels = node.commands.filter { $0.kind == 17 }.map(NativeTextLabel.init)
      componentChildren = node.children.map(NativeComponentView.init)
      semanticView = Self.makeSemanticView(for: node)
      super.init(frame: .zero)
      isOpaque = false
      backgroundColor = .clear
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
      canvasView?.frame = bounds
      textLabels.forEach { $0.layout(in: bounds, documentScale: documentScale) }
      componentChildren.forEach { $0.frame = bounds }
      semanticView?.frame = bounds
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

    func layout(in bounds: CGRect, documentScale: CGFloat) {
      font = .systemFont(ofSize: max(command.textSize * documentScale, 1))
      let fittingSize = sizeThatFits(bounds.size)
      let panX = command.values[2]
      let panY = command.values[3]
      let baseline = command.values[1] * documentScale
      frame = CGRect(
        x: command.values[0] * documentScale - fittingSize.width * ((panX + 1) / 2),
        y: baseline - font.ascender - fittingSize.height * ((panY + 1) / 2),
        width: min(fittingSize.width, bounds.width),
        height: min(fittingSize.height, bounds.height))
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
