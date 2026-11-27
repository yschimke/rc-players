import RcCreation
import RcCreationFoundation

/// A widget's size class, as WidgetKit's `WidgetFamily`.
///
/// Sizes are in points on a 6.1-inch iPhone (iPhone 15, 16): the sizes Apple's Human Interface
/// Guidelines list for that screen. Other devices differ by a few points; pass
/// `RemoteWidget(size:)` for an exact one.
public enum RemoteWidgetFamily: Hashable, Sendable, CaseIterable {
  case systemSmall, systemMedium, systemLarge
  /// Lock Screen and complication families.
  case accessoryCircular, accessoryRectangular, accessoryInline

  public var size: (width: Double, height: Double) {
    switch self {
    case .systemSmall: (170, 170)
    case .systemMedium: (364, 170)
    case .systemLarge: (364, 382)
    case .accessoryCircular: (76, 76)
    case .accessoryRectangular: (172, 76)
    case .accessoryInline: (257, 26)
    }
  }

  /// The margin WidgetKit puts around a widget's content (`contentMargins`).
  public var contentMargin: Double {
    switch self {
    case .systemSmall, .systemMedium, .systemLarge: 16
    case .accessoryCircular, .accessoryRectangular, .accessoryInline: 0
    }
  }

  /// The corner radius the Home Screen clips a widget to; a circular accessory is a circle.
  public var cornerRadius: Double {
    switch self {
    case .systemSmall, .systemMedium, .systemLarge: 22
    case .accessoryCircular: size.width / 2
    case .accessoryRectangular, .accessoryInline: 0
    }
  }

  var isAccessory: Bool { contentMargin == 0 }
}

/// A WidgetKit-style widget as a Remote Compose document: the family's size, its content margin,
/// a container background, and a URL a tap opens.
///
/// ```swift
/// let widget = RemoteWidget(family: .systemSmall, url: "weather://today") {
///   RemoteVStack(alignment: .leading, spacing: 4) {
///     RemoteText.time().font(.system(size: 34, weight: .semibold))
///     RemoteText("Partly Cloudy").font(.caption).foregroundStyle(.secondaryLabel)
///   }
/// }
/// let bytes = try widget.encode()
/// ```
///
/// The document plays live: a clock, a gauge or a variable the host writes updates without a new
/// timeline entry. The host routes the tap as a `HostNamedAction` named ``openURLAction`` whose
/// value is the URL.
public struct RemoteWidget: Sendable {
  /// The name of the action a tap on a widget with a URL sends.
  public static let openURLAction = "openURL"

  public let document: RemoteDocument

  /// - Parameters:
  ///   - family: The size class; sets the size, margin, corner radius and default background.
  ///   - displayScale: Pixels per point: 3 for an iPhone, unless given.
  ///   - url: Opened when the widget is tapped, as `widgetURL(_:)`.
  ///   - background: The container background (`containerBackground(_:for:)`): the theme's
  ///     widget background for Home Screen families, clear for accessory ones.
  public init(
    family: RemoteWidgetFamily, displayScale: Double = 3, url: String? = nil,
    background: RemoteColor? = nil, accessibilityLabel: String? = nil,
    @RemoteViewBuilder content: () -> some RemoteView
  ) {
    self.init(
      size: family.size, contentMargin: family.contentMargin, cornerRadius: family.cornerRadius,
      vibrant: family.isAccessory, displayScale: displayScale, url: url, background: background,
      accessibilityLabel: accessibilityLabel, content: content)
  }

  /// A widget of an exact size, in points.
  ///
  /// - Parameter vibrant: Draws the content white on clear, as WidgetKit renders an accessory
  ///   widget: the ``RemoteAppleTheme/vibrant`` theme, white text and a white tint.
  public init(
    size: (width: Double, height: Double), contentMargin: Double = 16, cornerRadius: Double = 22,
    vibrant: Bool = false, displayScale: Double = 3, url: String? = nil,
    background: RemoteColor? = nil, accessibilityLabel: String? = nil,
    @RemoteViewBuilder content: () -> some RemoteView
  ) {
    let container = WidgetContainer(
      content: content(), margin: contentMargin, cornerRadius: cornerRadius, vibrant: vibrant,
      url: url, background: background)
    document = RemoteDocument(
      width: size.width, height: size.height, displayScale: displayScale,
      accessibilityLabel: accessibilityLabel
    ) { container }
  }

  /// The widget's `.rc` bytes.
  public func encode() throws(RemoteEncodingError) -> [UInt8] { try document.encode() }
}

/// A widget's content, in its margin, on its background, clipped to its corners.
struct WidgetContainer<Content: RemoteView>: RemoteView {
  let content: Content
  let margin: Double
  let cornerRadius: Double
  let vibrant: Bool
  let url: String?
  let background: RemoteColor?

  var body: some RemoteView {
    if vibrant {
      themed.appleTheme(.vibrant).foregroundStyle(.white).tint(.white)
    } else {
      themed
    }
  }

  private var themed: some RemoteView {
    let container = self
    return ThemedView { theme, _ in
      let framed = RemoteZStack(alignment: .topLeading) { container.content }
        .padding(RemoteFloat(container.margin))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(container.background ?? theme.widgetBackground)
        .clipShape(.roundedRectangle(cornerRadius: container.cornerRadius))
      if let url = container.url {
        framed.onTapGesture(.host(RemoteWidget.openURLAction, value: RemoteString(url)))
      } else {
        framed
      }
    }
  }
}

/// How ``RemoteText/time(_:)`` shows the time.
public enum RemoteClockFormat: Hashable, Sendable {
  /// `09:41`.
  case hourMinute24
  /// `9:41`.
  case hourMinute12
}

extension RemoteText {
  /// The current time, live, as `Text(date, style: .time)`: the player's clock drives it, so a
  /// widget's clock keeps time without a timeline.
  public static func time(_ format: RemoteClockFormat = .hourMinute12) -> RemoteText {
    let hour = RemoteFloat(.hour)
    let minute = RemoteFloat(.minuteOfDay) % 60
    switch format {
    case .hourMinute24:
      return RemoteText(
        "\(hour, integerDigits: 2, zeroPadded: true):\(minute, integerDigits: 2, zeroPadded: true)")
    case .hourMinute12:
      // 0 → 12, 13 → 1: one more than (hour + 11) mod 12.
      let twelveHour = (hour + 11) % 12 + 1
      return RemoteText("\(twelveHour):\(minute, integerDigits: 2, zeroPadded: true)")
    }
  }

  /// Today's day of the month, live.
  public static func dayOfMonth() -> RemoteText { RemoteText("\(RemoteFloat(.dayOfMonth))") }
}
