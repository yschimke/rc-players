import Charts
import SwiftUI

@MainActor
enum NativeCustomComponentSamples {
  static let registry =
    RemoteComposeNativeCustomComponentRegistry()
    .registerSwiftUI("demo:EditableText") { component in
      NativeEditableText(component: component)
    }
    .registerSwiftUI("demo:SwiftControls") { component in
      NativeBoundControls(component: component)
    }
    .registerSwiftUI("demo:SwiftPulse") { component in
      NativePulse(component: component)
    }
    .registerSwiftUI("demo:SwiftChart") { component in
      NativeMomentumChart(component: component)
    }
}

private struct NativeEditableText: View {
  @ObservedObject var component: RemoteComposeNativeCustomComponent
  @State private var editing: String

  private static let text = RemoteComposeNativeTextProperty(1)
  private static let textReturn = RemoteComposeNativeTextReturnProperty(2)
  private static let textColor = RemoteComposeNativeColorProperty(3, default: 0xff20_2124)

  init(component: RemoteComposeNativeCustomComponent) {
    self.component = component
    _editing = State(initialValue: component.text(Self.text))
  }

  var body: some View {
    TextField("Document text", text: $editing)
      .textFieldStyle(.roundedBorder)
      .foregroundStyle(Color(argb: component.color(Self.textColor)))
      .padding(.horizontal, 8)
      .padding(.vertical, 4)
      .onChange(of: editing) { _, value in
        _ = component.send(value, to: Self.textReturn)
      }
      .onChange(of: component.text(Self.text)) { _, value in
        if editing != value { editing = value }
      }
  }
}

/// Two ordinary SwiftUI controls backed by values owned by the Remote Compose document.
private struct NativeBoundControls: View {
  @ObservedObject var component: RemoteComposeNativeCustomComponent
  @State private var name: String
  @State private var level: Float

  private static let name = RemoteComposeNativeTextProperty(1)
  private static let nameReturn = RemoteComposeNativeTextReturnProperty(2)
  private static let level = RemoteComposeNativeFloatProperty(3, default: 0.5)
  private static let levelReturn = RemoteComposeNativeFloatReturnProperty(4)
  private static let accent = RemoteComposeNativeColorProperty(5, default: 0xff67_50a4)

  init(component: RemoteComposeNativeCustomComponent) {
    self.component = component
    _name = State(initialValue: component.text(Self.name))
    _level = State(initialValue: component.float(Self.level))
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      Label("Native SwiftUI controls", systemImage: "slider.horizontal.3")
        .font(.headline)
      TextField("Name", text: $name)
        .textFieldStyle(.roundedBorder)
        .accessibilityIdentifier("swift-demo-name")
      HStack {
        Image(systemName: "speaker.wave.1.fill")
          .foregroundStyle(accent)
        Slider(value: $level, in: 0...1)
          .tint(accent)
          .accessibilityIdentifier("swift-demo-level")
        Text(level, format: .percent.precision(.fractionLength(0)))
          .font(.caption.monospacedDigit())
          .frame(width: 42, alignment: .trailing)
          .accessibilityIdentifier("swift-demo-level-value")
      }
    }
    .padding(12)
    .background(.background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    .onChange(of: name) { _, value in
      _ = component.send(value, to: Self.nameReturn)
    }
    .onChange(of: level) { _, value in
      _ = component.send(value, to: Self.levelReturn)
    }
    .onChange(of: component.text(Self.name)) { _, value in
      if name != value { name = value }
    }
    .onChange(of: component.float(Self.level)) { _, value in
      if level != value { level = value }
    }
  }

  private var accent: Color { Color(argb: component.color(Self.accent)) }
}

/// A real SwiftUI animation whose visual parameters remain document-authored values.
private struct NativePulse: View {
  @ObservedObject var component: RemoteComposeNativeCustomComponent

  private static let title = RemoteComposeNativeTextProperty(1, default: "Remote heartbeat")
  private static let tint = RemoteComposeNativeColorProperty(2, default: 0xffff_4f87)
  private static let duration = RemoteComposeNativeFloatProperty(3, default: 0.72)
  private static let amplitude = RemoteComposeNativeFloatProperty(4, default: 0.18)

  var body: some View {
    PhaseAnimator([false, true]) { expanded in
      VStack(spacing: 12) {
        ZStack {
          Circle()
            .fill(tint.opacity(expanded ? 0.08 : 0.25))
            .frame(width: 126, height: 126)
            .scaleEffect(expanded ? 1.18 : 0.72)
          Circle()
            .fill(
              RadialGradient(
                colors: [Color.white.opacity(0.95), tint], center: .topLeading,
                startRadius: 2, endRadius: 58)
            )
            .frame(width: 76, height: 76)
            .shadow(color: tint.opacity(0.65), radius: expanded ? 24 : 8)
            .scaleEffect(expanded ? 1 + amplitude : 1 - amplitude / 2)
          Image(systemName: "waveform.path.ecg")
            .font(.system(size: 30, weight: .semibold))
            .foregroundStyle(.white)
        }
        Text(component.text(Self.title))
          .font(.headline)
          .foregroundStyle(.white)
      }
      .frame(maxWidth: .infinity, maxHeight: .infinity)
    } animation: { _ in
      .easeInOut(duration: duration)
    }
  }

  private var tint: Color { Color(argb: component.color(Self.tint)) }
  private var duration: Double { max(0.15, Double(component.float(Self.duration))) }
  private var amplitude: CGFloat {
    min(0.45, max(0, CGFloat(component.float(Self.amplitude))))
  }
}

/// Swift Charts is hosted as a custom component; its gesture writes through to document text.
private struct NativeMomentumChart: View {
  private struct Point: Identifiable {
    let id: Int
    let day: String
    let value: Float
  }

  @ObservedObject var component: RemoteComposeNativeCustomComponent
  @State private var selection: Int?

  private static let title = RemoteComposeNativeTextProperty(1, default: "Momentum")
  private static let selectionReturn = RemoteComposeNativeTextReturnProperty(2)
  private static let tint = RemoteComposeNativeColorProperty(3, default: 0xff5b_5bd6)
  private static let values = (0..<7).map { RemoteComposeNativeFloatProperty(10 + $0) }
  private static let days = ["M", "T", "W", "T", "F", "S", "S"]

  private var points: [Point] {
    Self.values.enumerated().map { index, property in
      Point(id: index, day: Self.days[index], value: component.float(property))
    }
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      HStack {
        Text(component.text(Self.title))
          .font(.headline)
        Spacer()
        if let point = selectedPoint {
          Text("\(point.day) · \(point.value, format: .number.precision(.fractionLength(0)))")
            .font(.caption.bold().monospacedDigit())
            .foregroundStyle(tint)
        }
      }
      Chart(points) { point in
        AreaMark(
          x: .value("Day", point.id),
          yStart: .value("Baseline", 0),
          yEnd: .value("Momentum", point.value)
        )
        .foregroundStyle(
          LinearGradient(
            colors: [tint.opacity(0.5), tint.opacity(0.04)],
            startPoint: .top,
            endPoint: .bottom)
        )
        LineMark(x: .value("Day", point.id), y: .value("Momentum", point.value))
          .foregroundStyle(tint)
          .lineStyle(.init(lineWidth: 3, lineCap: .round, lineJoin: .round))
          .interpolationMethod(.catmullRom)
        if selection == point.id {
          PointMark(x: .value("Day", point.id), y: .value("Momentum", point.value))
            .foregroundStyle(tint)
            .symbolSize(90)
        }
      }
      .chartYScale(domain: 0...50)
      .chartXAxis {
        AxisMarks(values: points.map(\.id)) { value in
          AxisValueLabel {
            if let index = value.as(Int.self), Self.days.indices.contains(index) {
              Text(Self.days[index])
            }
          }
        }
      }
      .chartXSelection(value: $selection)
      // Charts' built-in selection recognizer did not consistently receive XCUITest's press-drag
      // on the iOS 26 simulator. Keep the native chart selection API for ordinary input, while this
      // overlay makes the custom component's return-channel gesture explicit and deterministic.
      .chartOverlay { proxy in
        GeometryReader { geometry in
          let plotArea = geometry[proxy.plotAreaFrame]
          Rectangle()
            .fill(.clear)
            .contentShape(Rectangle())
            .gesture(
              DragGesture(minimumDistance: 0)
                .onChanged { gesture in
                  let x = gesture.location.x - plotArea.origin.x
                  if let value = proxy.value(atX: x, as: Int.self), points.indices.contains(value) {
                    selection = value
                  }
                })
        }
      }
      .accessibilityIdentifier("swift-demo-chart")
    }
    .padding(12)
    .background(.background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    .onChange(of: selection) { _, _ in
      guard let point = selectedPoint else { return }
      _ = component.send(
        "\(point.day) · \(point.value.formatted(.number.precision(.fractionLength(0)))) momentum",
        to: Self.selectionReturn)
    }
  }

  private var tint: Color { Color(argb: component.color(Self.tint)) }
  private var selectedPoint: Point? {
    selection.flatMap { selected in points.first { $0.id == selected } }
  }
}

extension Color {
  fileprivate init(argb: UInt32) {
    self.init(
      .sRGB,
      red: Double((argb >> 16) & 0xff) / 255,
      green: Double((argb >> 8) & 0xff) / 255,
      blue: Double(argb & 0xff) / 255,
      opacity: Double((argb >> 24) & 0xff) / 255)
  }
}
