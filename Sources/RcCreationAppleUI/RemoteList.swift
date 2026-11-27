import RcCreation
import RcCreationFoundation

/// Builds a section's rows, one per statement, so the section can put a separator between each:
/// `if`, `switch` and `for` work as in any builder.
@resultBuilder
public enum RemoteRowsBuilder {
  public static func buildExpression(_ row: some RemoteView) -> [AnyRemoteView] {
    [AnyRemoteView(row)]
  }
  public static func buildBlock(_ rows: [AnyRemoteView]...) -> [AnyRemoteView] {
    rows.flatMap { $0 }
  }
  public static func buildOptional(_ rows: [AnyRemoteView]?) -> [AnyRemoteView] { rows ?? [] }
  public static func buildEither(first rows: [AnyRemoteView]) -> [AnyRemoteView] { rows }
  public static func buildEither(second rows: [AnyRemoteView]) -> [AnyRemoteView] { rows }
  public static func buildArray(_ rows: [[AnyRemoteView]]) -> [AnyRemoteView] {
    rows.flatMap { $0 }
  }
}

/// Sections on the grouped background, as SwiftUI's `List` with the inset-grouped style: a
/// settings screen.
///
/// ```swift
/// RemoteList {
///   RemoteSection("Network") {
///     RemoteToggle("Wi-Fi", isOn: wifi)
///     RemoteNavigationRow("Bluetooth", symbol: .boltFill, value: "On", action: .host("bt"))
///   }
/// }
/// ```
public struct RemoteList<Content: RemoteView>: RemoteView {
  let content: Content

  public init(@RemoteViewBuilder content: () -> Content) { self.content = content() }

  /// Between sections, and around the list, in points.
  static var sectionSpacing: Double { 35 }
  static var margin: RemoteFloat { 16 }

  public var body: some RemoteView {
    let content = content
    return ThemedView { theme, _ in
      RemoteVStack(alignment: .leading, spacing: Self.sectionSpacing) { content }
        .padding(.horizontal, Self.margin)
        .padding(.vertical, Self.margin)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.groupedBackground)
    }
  }
}

/// A group of rows on a rounded background, with an optional header and footer, as SwiftUI's
/// `Section` in an inset-grouped list. Rows are separated by an inset hairline.
public struct RemoteSection: RemoteView {
  let header: RemoteString?
  let footer: RemoteString?
  let rows: [AnyRemoteView]

  public init(
    _ header: RemoteString? = nil, footer: RemoteString? = nil,
    @RemoteRowsBuilder rows: () -> [AnyRemoteView]
  ) {
    self.header = header
    self.footer = footer
    self.rows = rows()
  }

  /// A row's insets, and the separator's leading inset, in points: iOS's.
  static var rowHorizontalPadding: RemoteFloat { 16 }
  static var rowVerticalPadding: RemoteFloat { 11 }
  static var cornerRadius: Double { 10 }

  public var body: some RemoteView {
    let section = self
    return ThemedView { theme, _ in
      RemoteVStack(alignment: .leading, spacing: 6) {
        if let header = section.header {
          RemoteText(header).font(.footnote).foregroundStyle(theme.secondaryLabel)
            .padding(.horizontal, Self.rowHorizontalPadding)
        }
        RemoteVStack(alignment: .leading, spacing: 0) {
          RemoteForEach(section.rows.enumerated()) { index, row in
            RemoteVStack(alignment: .leading, spacing: 0) {
              if index > 0 {
                RemoteDivider(color: theme.separator).padding(.leading, Self.rowHorizontalPadding)
              }
              // The row fills the width inside its insets, so a flexible child (a title, a
              // spacer) takes what is left within them rather than the section's full width.
              row
                .frame(maxWidth: .infinity)
                .padding(.horizontal, Self.rowHorizontalPadding)
                .padding(.vertical, Self.rowVerticalPadding)
            }
          }
        }
        .frame(maxWidth: .infinity)
        .background(
          theme.groupedRowBackground, in: .roundedRectangle(cornerRadius: Self.cornerRadius))
        if let footer = section.footer {
          RemoteText(footer).font(.footnote).foregroundStyle(theme.secondaryLabel)
            .padding(.horizontal, Self.rowHorizontalPadding)
        }
      }
    }
  }
}

/// A row that leads somewhere, as a `NavigationLink` row in a settings list: an icon tile, a
/// title, an optional value, and a chevron. Tapping it runs `action`, which a host routes.
public struct RemoteNavigationRow: RemoteView {
  let title: RemoteString
  let symbol: RemoteSymbol?
  let iconBackground: RemoteColor?
  let value: RemoteString?
  let action: RemoteAction

  /// - Parameters:
  ///   - symbol: An icon, drawn white on a rounded tile of `iconBackground` (the tint unless
  ///     given), as Settings draws its rows.
  ///   - value: Secondary text before the chevron: the current setting.
  public init(
    _ title: RemoteString, symbol: RemoteSymbol? = nil, iconBackground: RemoteColor? = nil,
    value: RemoteString? = nil, action: RemoteAction
  ) {
    self.title = title
    self.symbol = symbol
    self.iconBackground = iconBackground
    self.value = value
    self.action = action
  }

  static var tileSize: RemoteFloat { 29 }

  public var body: some RemoteView {
    let row = self
    return ThemedView { theme, tint in
      RemoteHStack(alignment: .center, spacing: 12) {
        if let symbol = row.symbol {
          RemoteZStack { RemoteIcon(symbol, size: 18).foregroundStyle(theme.onAccent) }
            .frame(width: Self.tileSize, height: Self.tileSize)
            .background(row.iconBackground ?? tint, in: .roundedRectangle(cornerRadius: 7))
        }
        RemoteText(row.title).foregroundStyle(theme.label).flexWeight(1)
        if let value = row.value {
          RemoteText(value).foregroundStyle(theme.secondaryLabel)
        }
        RemoteIcon(.chevronRight, size: 14).foregroundStyle(theme.tertiaryLabel)
      }
      .onTapGesture(row.action)
      .accessibilityElement(children: .combine)
      .accessibilityRole(.button)
    }
  }
}
