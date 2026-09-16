# AREA-08: Text Subsystem & Typography Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player
**Normative Status:** Core Specification  

---

## 1. Overview

The RemoteCompose Text Subsystem provides comprehensive typographic rendering, ranging from low-level anchored canvas drawing to modern, multi-line structured text layout. 

The subsystem handles static and dynamically formatted strings, path-warped typography, embedded and system font resolution, style inheritance hierarchies, and high-performance pre-rendered bitmap fonts.

---

## 2. Text Drawing Operations

### 2.1 DrawTextAnchored (`Opcode 133`)
The primary primitive for precise text placement without full layout overhead.
* **Fields**:
  * `textId: INT`: Reference to string resource or dynamic text variable.
  * `x: FLOAT, y: FLOAT`: Anchor target coordinate.
  * `panX: FLOAT`: Relative horizontal alignment factor $\in [-1.0, 1.0]$:
    * `panX = -1.0`: Text left edge aligns with $X$ (Left-aligned).
    * `panX = 0.0`: Text horizontal center aligns with $X$ (Center-aligned).
    * `panX = 1.0`: Text right edge aligns with $X$ (Right-aligned).
  * `panY: FLOAT`: Relative vertical alignment factor $\in [-1.0, 1.0]$:
    * `panY = -1.0`: Text baseline/top aligns with $Y$.
    * `panY = 0.0`: Text vertical center aligns with $Y$.
    * `panY = 1.0`: Text bottom aligns with $Y$.
  * `flags: INT`: Formatting and baseline alignment options.

### 2.2 Curved & Path Typography
* **`DRAW_TEXT_ON_PATH (Opcode 53)`**: Renders text characters rotated and positioned along a vector `PathData` trajectory, offset by `hOffset` and `vOffset`.
* **`DRAW_TEXT_ON_CIRCLE (Opcode 57)`**: Places text characters along a circular perimeter of radius $R$ at starting angle $\theta$.

---

## 3. Structured Text Layout (`CoreText` & `TextStyle`)

For paragraph-level text inside layout containers:

```
[CoreText (Opcode 239)]
    ├── Ingest Text Content & TextStyle (Opcode 242)
    ├── Resolve Inheritance from parentId TextStyle
    ├── Constraints Pass (minWidth, maxWidth from parent layout)
    ├── Line Breaking & Cesure Analysis (word wrapping, hyphenation)
    ├── Truncation (maxLines, TextOverflow: Ellipsis, Clip)
    └── Output measured bounds and line metrics to MeasurePass
```

### 3.1 TextStyle Specification (`Opcode 242`)
`TextStyle` encodes attributes using `CommandParameters` tag-value pairs:

| Tag ID | Parameter Name | Type | Default Value | Description |
| :--- | :--- | :--- | :--- | :--- |
| `1` | `P_ID` | `INT` | `-1` | Style object identifier |
| `2` | `P_ANIMATION_ID` | `INT` | `-1` | Animation identifier for animated styles |
| `3` | `P_COLOR` | `INT` | `0xFF000000` | 32-bit ARGB text color |
| `4` | `P_COLOR_ID` | `INT` | `-1` | Dynamic color variable ID |
| `5` | `P_FONT_SIZE` | `FLOAT` | `36.0` | Font size in pixels/points |
| `6` | `P_FONT_STYLE` | `INT` | `0` | Style: `NORMAL (0)`, `BOLD (1)`, `ITALIC (2)`, `BOLD_ITALIC (3)` |
| `7` | `P_FONT_WEIGHT` | `FLOAT` | `400.0` | Font weight $[100.0, 900.0]$ |
| `8` | `P_FONT_FAMILY` | `INT` | `-1` | Font family ID or system typeface |
| `9` | `P_TEXT_ALIGN` | `INT` | `1` | Alignment: `LEFT (1)`, `RIGHT (2)`, `CENTER (3)`, `START (4)`, `END (5)` |
| `10` | `P_OVERFLOW` | `INT` | `1` | Overflow: `CLIP (1)`, `ELLIPSIS (2)`, `EXPAND (3)` |
| `11` | `P_MAX_LINES` | `INT` | `Integer.MAX_VALUE` | Maximum line count before truncating |
| `12` | `P_LETTER_SPACING` | `FLOAT` | `0.0` | Tracking offset in pixels |
| `13` | `P_LINE_HEIGHT_ADD` | `FLOAT` | `0.0` | Additional leading distance |
| `14` | `P_LINE_HEIGHT_MULT`| `FLOAT` | `1.0` | Line height scale factor |
| `15` | `P_BREAK_STRATEGY` | `INT` | `0` | Strategy: `SIMPLE (0)`, `HIGH_QUALITY (1)`, `BALANCED (2)` |
| `16` | `P_HYPHENATION` | `INT` | `0` | Hyphenation frequency: `OFF (0)`, `NORMAL (1)`, `FULL (2)` |
| `17` | `P_JUSTIFICATION` | `INT` | `0` | Justification: `NONE (0)`, `INTER_WORD (1)` |
| `18` | `P_UNDERLINE` | `BOOL` | `false` | Text underline decoration |
| `19` | `P_STRIKETHROUGH` | `BOOL` | `false` | Strikethrough decoration |
| `20` | `P_FONT_AXIS` | `INT[]` | `null` | Variable font axis 4-character tags |
| `21` | `P_FONT_AXIS_VALUES` | `FLOAT[]`| `null` | Variable font axis values |
| `22` | `P_AUTOSIZE` | `BOOL` | `false` | Automatically scales font size to fit bounds |
| `23` | `P_FLAGS` | `INT` | `0` | Text rendering and formatting flags |
| `24` | `P_PARENT_ID` | `INT` | `-1` | Parent `TextStyle` ID for attribute inheritance |
| `25` | `P_MIN_FONT_SIZE` | `FLOAT` | `-1.0` | Minimum font size when autosizing |
| `26` | `P_MAX_FONT_SIZE` | `FLOAT` | `-1.0` | Maximum font size when autosizing |

---

## 4. Dynamic Text Formatting & String Operations

RemoteCompose allows documents to format and transform strings client-side without re-encoding:

### 4.1 TextFromFloat (`Opcode 135`)
Formats a dynamic floating point variable into a human-readable text string.
* **Fields**:
  * `textId: INT`: Target text variable ID.
  * `floatId: INT`: Source float variable ID.
  * `minIntegerDigits: INT`: Minimum digits before decimal point (zero-padded).
  * `maxDecimalDigits: INT`: Maximum fractional precision digits.
  * `flags: INT`:
    * `FLAG_GROUPING (1 << 0)`: Inserts locale-specific thousand separators.
    * `FLAG_SHOW_SIGN (1 << 1)`: Forces leading `+` sign for positive values.
* **Example**: Float value `42.5` with `minInteger=3, maxDecimal=2` yields `"042.50"`.

### 4.2 String Manipulation Operations
* **`TEXT_MERGE (Opcode 136)`**: Concatenates strings from two source IDs into a destination ID.
* **`TEXT_LOOKUP (Opcode 151)`**: Looks up string in string list using float/ID index.
* **`TEXT_LOOKUP_INT (Opcode 153)`**: Looks up string in string list using integer index.
* **`TEXT_SUBTEXT (Opcode 182)`**: Extracts a substring given `start` index and `length`.
* **`TEXT_TRANSFORM (Opcode 199)`**: Transforms text casing:
  * `TO_UPPERCASE (1)`, `TO_LOWERCASE (2)`, `CAPITALIZE (3)`.
* **`TEXT_LENGTH (Opcode 156)`**: Calculates character count and writes to an integer variable.

---

## 5. Font Resolution & Bitmap Fonts

### 5.1 Font Providers & TypefaceResolver
The player resolves fonts in the following priority order:
1. **Embedded Font Resources (`DATA_FONT`, Opcode 189)**: TrueType or OpenType binary data embedded directly in the document.
2. **Platform Fonts (`TypefaceResolver`)**: Resolves system font names (`FONT_TYPE_DEFAULT = 0`, `FONT_TYPE_SANS_SERIF = 1`, `FONT_TYPE_SERIF = 2`, `FONT_TYPE_MONOSPACE = 3`) to host OS typefaces.
3. **Fallback**: Default system sans-serif font.

### 5.2 Bitmap Font Subsystem (`DATA_BITMAP_FONT`, Opcode 167)
For ultra-low power devices or deterministic pixel-perfect typography:
* A pre-rendered glyph atlas (`DATA_BITMAP`) is paired with an offset/metrics table.
* **`DRAW_BITMAP_FONT_TEXT_RUN (Opcode 48)`** and **`DRAW_BITMAP_TEXT_ANCHORED (Opcode 184)`** blit pre-rendered glyphs without requiring an OS font rasterizer.
* **`BITMAP_TEXT_MEASURE (Opcode 183)`**: Fast table lookup for text width calculations.

---

## 6. Conformance Requirements

1. **Pan Anchor Normalization**: `DRAW_TEXT_ANCHOR` with `panX = 0, panY = 0` must center the text bounding box exactly on the target coordinate $(X, Y)$.
2. **Zero-Precision Float Formatting**: `TEXT_FROM_FLOAT` with `maxDecimalDigits = 0` must round values mathematically (e.g., `4.6 -> "5"`).
3. **Style Inheritance**: When `P_PARENT_ID` is specified on a `TextStyle`, any parameter not explicitly declared on the child style must inherit its value from the parent style.
4. **Font Missing Fallback**: If a referenced `fontId` is unavailable, the player must fallback to system default typography without throwing exceptions.
