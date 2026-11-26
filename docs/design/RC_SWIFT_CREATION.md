# Swift creation API (`RcCreation`)

`RcCreation` writes Remote Compose documents from Swift. It is the authoring side of the Apple
story: the native Swift player and the XCFramework read `.rc`; this writes it, so a Swift server, a
command-line tool or an app can produce documents without Kotlin.

The API has Compose's vocabulary and SwiftUI's syntax. Layouts, modifiers and arrangements are named
as `rc-creation-compose` names them (`RemoteBox`, `RemoteModifier.fillMaxWidth()`,
`RemoteArrangement.spacedBy`), because that is what a reader of the document thinks in. Composition
uses result builders and `RemoteView` structs with a `body`, because that is how Swift composes.

## Example

```swift
import RcCreation

struct Chip: RemoteView {
  let label: String
  let selected: Bool
  var body: some RemoteView {
    RemoteBox(
      modifier: .clip(.rounded(16))
        .background(selected ? RemoteColor(rgb: 0x3D5AFE) : RemoteColor(rgb: 0xE8EAF6))
        .padding(horizontal: 12, vertical: 6),
      contentAlignment: .center
    ) {
      RemoteText(
        label, color: selected ? .white : RemoteColor(rgb: 0x1A237E), fontSize: 13,
        fontWeight: .medium)
    }
  }
}

let progress = RemoteFloat.named("progress", 0.62)
let document = RemoteDocument(width: 720, height: 400, density: 2) {
  RemoteColumn(
    modifier: .fillMaxSize().background(RemoteColor(rgb: 0xF5F5F7)).padding(16),
    verticalArrangement: .spacedBy(12)
  ) {
    RemoteRow(modifier: .fillMaxWidth(), verticalAlignment: .center) {
      RemoteCanvas(modifier: .size(40)) { scope in
        scope.drawCircle(RemoteColor(rgb: 0x3D5AFE), radius: 20, center: RemotePoint(20, 20))
        scope.drawCircle(.white, radius: 12, center: RemotePoint(20, 20), style: .stroke(width: 3))
      }
      RemoteSpacer(modifier: .width(12))
      RemoteColumn(modifier: .weight(1)) {
        RemoteText("Swift creation API", fontSize: 18, fontWeight: .bold)
        RemoteText(
          .named("subtitle", "Composed in Swift, played anywhere"), color: .gray, fontSize: 13)
      }
    }
    RemoteRow(horizontalArrangement: .spacedBy(8)) {
      for (index, label) in ["Box", "Row", "Column", "Canvas"].enumerated() {
        Chip(label: label, selected: index == 0)
      }
    }
    RemoteText("Upload " + RemoteString(progress * 100, decimals: 0) + "%", fontSize: 13)
    RemoteBox(
      modifier: .fillMaxWidth().height(8).clip(.rounded(4)).background(RemoteColor(rgb: 0xDADCE0))
    ) {
      RemoteBox(
        modifier: .width(progress * 328).height(8).clip(.rounded(4)).background(
          RemoteColor(rgb: 0x3D5AFE)))
    }
  }
}
let bytes: [UInt8] = document.encode()
```

Rendered by the CMP player (`remote-compose-player --render-png`), from
[`renders/swift-creation/card.rc`](../../renders/swift-creation/card.rc):

![Swift-authored card rendered by the CMP player](../../renders/swift-creation/card-cmp-jvm.png)

The host then writes `progress` and `subtitle` by name, and the player updates the bar, the
percentage text and the subtitle without a new document.

## What it covers

| Area | API | Operations written |
| --- | --- | --- |
| Document | `RemoteDocument(width:height:density:contentDescription:)` | modern `Header` with `DOC_WIDTH`, `DOC_HEIGHT`, `DOC_DENSITY_AT_GENERATION` |
| Layouts | `RemoteBox`, `RemoteRow`, `RemoteColumn`, `RemoteSpacer` | `BoxLayout`, `RowLayout`, `ColumnLayout`, `LayoutContent`, `ContainerEnd` |
| Text | `RemoteText` (size, weight, italic, align, overflow, max lines) | `TextLayout` |
| Drawing | `RemoteCanvas` with `drawRect`, `drawRoundRect`, `drawOval`, `drawCircle`, `drawLine` | `CanvasLayout`, `CanvasContent`, `PaintData`, draw ops |
| Modifiers | size, fill, wrap, `weight`, padding, offset, `zIndex`, background, border, clip, visibility, clickable | the matching modifier ops |
| State | `RemoteFloat`, `RemoteInt`, `RemoteString`, `RemoteColor` constants and `.named(...)` | `FloatConstant`, `IntegerConstant`, `TextData`, `ColorConstant`, `NamedVariable` |
| Expressions | `+ - * /`, `min`, `max`, `absoluteValue`, `clamped(to:)` on `RemoteFloat` | one `FloatExpression` per expression, in reverse-Polish order |
| Text from values | `RemoteString(float, decimals:)`, `+` on `RemoteString` | `TextFromFloat`, `TextMerge` |
| Actions | `.host(id)`, `.host(name, value:)`, `.set(named, to:)` | `HostAction`, `HostNamedAction`, value-change actions |

## Decisions

- **Units.** The API takes dp, and sp for text, as Compose does. The document is written in pixels at
  `density`, which the header records as the density at generation, as AndroidX's capture does. A
  computed dimension is scaled by an expression, so `progress * 328` stays live.
- **Declarations come first.** Every value a component reads is declared between the header and the
  root, once per distinct value. Ids are therefore defined before any reader, and the body is only
  layout.
- **Operation shapes follow AndroidX's writer.** A layout is its operation, its modifiers,
  `LayoutContent` and two `ContainerEnd`s; a canvas adds `CanvasContent`; `TextLayout` is used
  rather than `CoreText` because every player profile reads it. Field layouts were checked against
  `operations.json` from androidx-main `remote-generator`.
- **`weight` needs no scope.** Compose gets `weight` from `RowScope` and `ColumnScope`. Here the
  writer knows the enclosing layout and writes a width or a height weight; outside a row or column it
  is a precondition failure.
- **Constants fold.** Arithmetic on two constants is evaluated in Swift and costs the document
  nothing; constant strings joined with `+` become one string.
- **No player dependency.** `RcCreation` declares its own opcode constants and depends on nothing,
  so it builds for every Apple platform and on Linux. `RcCreationTests` depends on the native player
  core to read documents back.

## Verification

- `RcCreationTests` (14 tests) opens every document with `NativeSwiftDocumentSession` and checks the
  decoded tree: sizes in pixels, spacing, padding, colours, weights, text, canvas commands. It also
  writes named values through the session and checks that the text, width, visibility and colour
  follow them, and that a click runs its actions.
- The example above decodes byte-exactly with a decoder driven only by AndroidX's `operations.json`,
  and the CMP player renders it as shown.
- On Linux CI, `native-core-linux` runs `RcCreationTests` beside the core's suite, and `swift-format`
  lints the new sources.

## Not yet

- Images (`ImageLayout` and bitmap data), state layouts, flow, scroll, `CoreText` styling (letter
  spacing, line height, font axes), animation specs, paths, gradients and touch-down/up handlers.
- A `ComponentValue`-based draw scope, so a canvas can draw relative to its measured size.
- Integer expressions. `RemoteInt` is a constant or a named value only.
