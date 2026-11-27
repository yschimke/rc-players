import RcCreation
import RcCreationFoundation

/// How a ``RemoteProgressView`` is drawn.
public enum RemoteProgressViewStyle: Hashable, Sendable {
  /// A bar across the available width.
  case linear
  /// A ring, or a spinner while the progress is indeterminate.
  case circular
}

/// Progress towards completion, as SwiftUI's `ProgressView`: determinate from a live value, or an
/// indeterminate spinner.
///
/// ```swift
/// let download = RemoteVariable("download", default: 0.4)
/// RemoteProgressView("Downloading", value: RemoteFloat(download))
/// RemoteProgressView().progressViewStyle(.circular)
/// ```
public struct RemoteProgressView: RemoteView {
  let title: RemoteString?
  /// The completed fraction, 0 to 1, or `nil` while indeterminate.
  let fraction: RemoteFloat?
  var style: RemoteProgressViewStyle

  /// Determinate progress: `value` out of `total`, clamped to 0...`total`.
  public init(_ title: RemoteString? = nil, value: RemoteFloat, total: Double = 1) {
    self.title = title
    fraction = (value / RemoteFloat(total)).clamped(to: 0...1)
    style = .linear
  }

  /// An indeterminate spinner, which turns as long as the document plays.
  public init(_ title: RemoteString? = nil) {
    self.title = title
    fraction = nil
    style = .circular
  }

  public func progressViewStyle(_ style: RemoteProgressViewStyle) -> RemoteProgressView {
    var copy = self
    copy.style = style
    return copy
  }

  /// A linear bar's thickness, and a ring's diameter and stroke, in points.
  static let barHeight: RemoteFloat = 4
  static let ringSize: RemoteFloat = 24
  static let ringLineWidth: Double = 3
  /// A spinner's arc, and how many degrees it turns a second.
  static let spinnerSweep: RemoteFloat = 270
  static let spinnerDegreesPerSecond: RemoteFloat = 360

  public var body: some RemoteView {
    let progress = self
    return ThemedView { theme, tint in
      RemoteVStack(alignment: .leading, spacing: 6) {
        if let title = progress.title {
          RemoteText(title).font(.subheadline).foregroundStyle(theme.label)
        }
        switch (progress.style, progress.fraction) {
        case (.linear, let fraction?):
          RemoteCanvas { context, size in
            let radius = Double(2)
            context.fill(.roundedRect(size.bounds, cornerRadius: radius), with: theme.fill)
            context.fill(
              .roundedRect(
                RemoteRect(x: 0, y: 0, width: size.width * fraction, height: size.height),
                cornerRadius: radius),
              with: tint)
          }
          .frame(maxWidth: .infinity, height: Self.barHeight)
        case (_, let fraction):
          RemoteCanvas { context, size in
            let radius = size.width / 2 - RemoteFloat(Self.ringLineWidth / 2)
            if let fraction {
              context.stroke(
                .circle(center: size.center, radius: radius), with: theme.fill,
                lineWidth: Self.ringLineWidth)
              // From twelve o'clock, clockwise.
              context.stroke(
                .arc(center: size.center, radius: radius, startAngle: -90, sweep: fraction * 360),
                with: tint, lineWidth: Self.ringLineWidth, lineCap: .round)
            } else {
              let start =
                (RemoteFloat(.animationTime) * Self.spinnerDegreesPerSecond)
                % 360
              context.stroke(
                .arc(
                  center: size.center, radius: radius, startAngle: start, sweep: Self.spinnerSweep),
                with: theme.secondaryLabel, lineWidth: Self.ringLineWidth, lineCap: .round)
            }
          }
          .frame(width: Self.ringSize, height: Self.ringSize)
        }
      }
      .accessibilityElement(children: .ignore)
      .accessibilityLabel(progress.title ?? "Progress")
      .accessibilityValue(
        progress.fraction.map { "\($0 * 100, decimals: 0) percent" } ?? "In progress")
    }
  }
}

/// How a ``RemoteGauge`` is drawn, as SwiftUI's gauge styles.
public enum RemoteGaugeStyle: Hashable, Sendable {
  /// An open ring with the value in the middle: a circular complication or Lock Screen widget.
  case accessoryCircular
  /// A closed ring that fills: capacity, such as a battery.
  case accessoryCircularCapacity
  /// A bar with the label above and the range's ends below.
  case linearCapacity
}

/// A value within a range, as SwiftUI's `Gauge`.
///
/// ```swift
/// let temperature = RemoteVariable("temperature", default: 21.0)
/// RemoteGauge(value: RemoteFloat(temperature), in: 10...30, label: "Indoor",
///             currentValueLabel: "\(temperature)°")
/// ```
public struct RemoteGauge: RemoteView {
  let fraction: RemoteFloat
  let label: RemoteString?
  let currentValueLabel: RemoteString?
  let minimumValueLabel: RemoteString?
  let maximumValueLabel: RemoteString?
  var style: RemoteGaugeStyle = .accessoryCircular

  public init(
    value: RemoteFloat, in range: ClosedRange<Double> = 0...1, label: RemoteString? = nil,
    currentValueLabel: RemoteString? = nil, minimumValueLabel: RemoteString? = nil,
    maximumValueLabel: RemoteString? = nil
  ) {
    let span = range.upperBound - range.lowerBound
    fraction = ((value - RemoteFloat(range.lowerBound)) / RemoteFloat(span == 0 ? 1 : span))
      .clamped(to: 0...1)
    self.label = label
    self.currentValueLabel = currentValueLabel
    self.minimumValueLabel = minimumValueLabel
    self.maximumValueLabel = maximumValueLabel
  }

  public func gaugeStyle(_ style: RemoteGaugeStyle) -> RemoteGauge {
    var copy = self
    copy.style = style
    return copy
  }

  /// An open gauge's gap is at the bottom: it starts at seven o'clock and sweeps 270 degrees.
  static let openStart: RemoteFloat = 135
  static let openSweep: RemoteFloat = 270
  static let ringLineWidth: Double = 6
  static let circularSize: RemoteFloat = 58

  public var body: some RemoteView {
    let gauge = self
    return ThemedView { theme, tint in
      switch gauge.style {
      case .accessoryCircular, .accessoryCircularCapacity:
        let isOpen = gauge.style == .accessoryCircular
        RemoteZStack {
          RemoteCanvas { context, size in
            let radius = size.width / 2 - RemoteFloat(Self.ringLineWidth / 2)
            let start: RemoteFloat = isOpen ? Self.openStart : -90
            let sweep: RemoteFloat = isOpen ? Self.openSweep : 360
            context.stroke(
              .arc(center: size.center, radius: radius, startAngle: start, sweep: sweep),
              with: theme.fill, lineWidth: Self.ringLineWidth, lineCap: .round)
            context.stroke(
              .arc(
                center: size.center, radius: radius, startAngle: start,
                sweep: sweep * gauge.fraction),
              with: tint, lineWidth: Self.ringLineWidth, lineCap: .round)
          }
          RemoteVStack(spacing: 0) {
            if let value = gauge.currentValueLabel {
              RemoteText(value).font(.system(size: 17, weight: .semibold)).foregroundStyle(
                theme.label)
            }
            if let label = gauge.label {
              RemoteText(label).font(.system(size: 10, weight: .medium)).foregroundStyle(
                theme.secondaryLabel)
            }
          }
        }
        .frame(width: Self.circularSize, height: Self.circularSize)
      case .linearCapacity:
        RemoteVStack(alignment: .leading, spacing: 4) {
          if let label = gauge.label {
            RemoteText(label).font(.subheadline).foregroundStyle(theme.label)
          }
          RemoteCanvas { context, size in
            let radius = Double(3)
            context.fill(.roundedRect(size.bounds, cornerRadius: radius), with: theme.fill)
            context.fill(
              .roundedRect(
                RemoteRect(x: 0, y: 0, width: size.width * gauge.fraction, height: size.height),
                cornerRadius: radius),
              with: tint)
          }
          .frame(maxWidth: .infinity, height: 6)
          RemoteHStack {
            if let minimum = gauge.minimumValueLabel {
              RemoteText(minimum).font(.caption).foregroundStyle(theme.secondaryLabel)
            }
            RemoteSpacer()
            if let maximum = gauge.maximumValueLabel {
              RemoteText(maximum).font(.caption).foregroundStyle(theme.secondaryLabel)
            }
          }
          .frame(maxWidth: .infinity)
        }
      }
    }
  }
}
