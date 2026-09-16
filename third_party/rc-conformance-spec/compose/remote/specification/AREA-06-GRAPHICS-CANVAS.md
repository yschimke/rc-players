# AREA-06: 2D Graphics Canvas & Paint State Machine Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player
**Normative Status:** Core Specification  

---

## 1. Overview

The RemoteCompose 2D Graphics Canvas is the low-level rendering foundation of the player runtime. It defines how geometric primitives, vector paths, matrix transformation hierarchies, clipping regions, and styling attributes are rasterized onto the target rendering surface.

---

## 2. The Paint State Machine

Rendering operations do not carry inline color and stroke parameters. Instead, they rely on a stateful **`PaintBundle`** maintained by the player's `PaintContext`.

```
[Server/Writer]
Mutate paint properties (color, strokeWidth, cap, shader)
        |
Commit boundary: emit PAINT_VALUES (Opcode 40)
        |
        v
[Player Runtime]
When PAINT_VALUES encountered:
    ├── Deserialize PaintBundle payload
    ├── Apply properties to active native Paint/Canvas state
    └── Retain active paint state across subsequent draw calls
        |
[Canvas Save / Restore]
MatrixSave (130) pushes paint state; MatrixRestore (131) restores prior paint state
```

### 2.1 PaintBundle Property Tags

`PaintBundle` serializes attributes as a tagged stream:

| Tag ID | Constant | Type | Description |
| :--- | :--- | :--- | :--- |
| `1` | `TEXT_SIZE` | `FLOAT` | Text font size in pixels |
| `4` | `COLOR` | ARGB `INT` | 32-bit ARGB paint color (default: `0xFF000000`) |
| `5` | `STROKE_WIDTH` | `FLOAT` | Perimeter stroke thickness in px |
| `6` | `STROKE_MITER` | `FLOAT` | Stroke miter limit ratio |
| `7` | `STROKE_CAP` | `INT` | Stroke cap: `BUTT (0)`, `ROUND (1)`, `SQUARE (2)` |
| `8` | `STYLE` | `INT` | Paint style: `STYLE_FILL (0)`, `STYLE_STROKE (1)`, `STYLE_FILL_AND_STROKE (2)` |
| `9` | `SHADER` | `INT` | Reference ID to a runtime shader or texture |
| `10` | `IMAGE_FILTER_QUALITY`| `INT` | Quality filter: `NONE (0)`, `LOW (1)`, `MEDIUM (2)`, `HIGH (3)` |
| `11` | `GRADIENT` | `BUFFER` | Gradient definition (`LINEAR (0)`, `RADIAL (1)`, `SWEEP (2)`) |
| `12` | `ALPHA` | `FLOAT` | Alpha transparency multiplier $[0.0, 1.0]$ |
| `13` | `COLOR_FILTER` | `INT` | Direct color filter mode |
| `14` | `ANTI_ALIAS` | `INT` | Anti-aliasing flag (`0=Off, 1=On`) |
| `15` | `STROKE_JOIN` | `INT` | Stroke join: `MITER (0)`, `ROUND (1)`, `BEVEL (2)` |
| `16` | `TYPEFACE` | `INT` | Font typeface identifier |
| `17` | `FILTER_BITMAP` | `INT` | Bitmap bilinear filtering toggle |
| `18` | `BLEND_MODE` | `INT` | Blend mode (`SRC_OVER = 3`, etc.) |
| `19` | `COLOR_ID` | `INT` | Dynamic color variable ID |
| `20` | `COLOR_FILTER_ID` | `INT` | Dynamic color filter ID |
| `21` | `CLEAR_COLOR_FILTER` | *(none)* | Resets color filter |
| `22` | `SHADER_MATRIX` | `INT` | Local transformation matrix ID for active shader |
| `23` | `FONT_AXIS` | `BUFFER` | Variable font axis settings |
| `24` | `TEXTURE` | `INT` | Texture image identifier |
| `25` | `PATH_EFFECT` | `BUFFER` | Dash intervals or corner rounding path effects |
| `26` | `FALLBACK_TYPEFACE` | `INT` | Secondary fallback typeface identifier |

---

## 3. Transformation Matrix Stack

RemoteCompose maintains a 3x3 affine transformation matrix stack for 2D graphics:

$$\begin{bmatrix} x' \\ y' \\ 1 \end{bmatrix} = \begin{bmatrix} S_x & K_x & T_x \\ K_y & S_y & T_y \\ 0 & 0 & 1 \end{bmatrix} \begin{bmatrix} x \\ y \\ 1 \end{bmatrix}$$

### 3.1 Matrix Operations
* **`MATRIX_SAVE (Opcode 130)`**: Pushes a copy of the current transformation matrix and current clipping bounds onto the player's matrix stack.
* **`MATRIX_RESTORE (Opcode 131)`**: Pops the matrix and clipping bounds from the stack.
* **`MATRIX_TRANSLATE (Opcode 127)`**: Pre-concatenates translation $(dx, dy)$ onto the current matrix.
* **`MATRIX_SCALE (Opcode 126)`**: Pre-concatenates scaling $(sx, sy)$ around pivot point $(px, py)$.
* **`MATRIX_ROTATE (Opcode 129)`**: Pre-concatenates clockwise rotation $\theta$ (in degrees) around pivot $(px, py)$.
* **`MATRIX_SKEW (Opcode 128)`**: Pre-concatenates shearing factors $(kx, ky)$.
* **`MATRIX_FROM_PATH (Opcode 181)`**: Samples a vector path at distance $D$ and derives a tangent transformation matrix that aligns elements along the path normal/tangent vector.

---

## 4. Drawing Primitives Specification

All coordinate fields support dynamic float variables via NaN-encoded IDs.

### 4.1 Basic Geometries
* **`DRAW_RECT (Opcode 42)`**: Rasterizes axis-aligned rectangle from $(x_1, y_1)$ to $(x_2, y_2)$.
* **`DRAW_ROUND_RECT (Opcode 51)`**: Rasterizes rectangle with corner radii $(r_x, r_y)$. If $2 r_x > \text{width}$, radii are proportionally scaled down to prevent visual overlap.
* **`DRAW_CIRCLE (Opcode 46)`**: Rasterizes circle centered at $(cx, cy)$ with radius $R$.
* **`DRAW_OVAL (Opcode 56)`**: Rasterizes axis-aligned ellipse inscribed within $(L, T, R, B)$.
* **`DRAW_LINE (Opcode 47)`**: Renders a straight stroke segment between $(x_1, y_1)$ and $(x_2, y_2)$.
* **`DRAW_ARC (Opcode 152)`** & **`DRAW_SECTOR (Opcode 52)`**:
  * Sweeps an arc from `startAngle` by `sweepAngle` (degrees, clockwise from positive X-axis).
  * `DRAW_ARC`: Perimeter stroke only.
  * `DRAW_SECTOR`: Pie-wedge connecting arc endpoints to the center point.

---

## 5. Vector Path Subsystem

Paths represent complex vector curves and shapes (`PathData`).

### 5.1 Dynamic Path Operations
* **`PATH_CREATE (Opcode 159)`**: Allocates an empty mutable path with an assigned ID.
* **`PATH_ADD (Opcode 160)`**: Appends segments using standard SVG-style commands:
  * `MOVE_TO (0)`: $(x, y)$
  * `LINE_TO (1)`: $(x, y)$
  * `QUAD_TO (2)`: $(x_1, y_1, x_2, y_2)$
  * `CUBIC_TO (3)`: $(x_1, y_1, x_2, y_2, x_3, y_3)$
  * `CLOSE (4)`: Closes path contour.
* **`PATH_COMBINE (Opcode 175)`**: Performs constructive area geometry (Boolean path operations):
  * `DIFFERENCE (0)`, `INTERSECT (1)`, `UNION (2)`, `XOR (3)`, `REVERSE_DIFFERENCE (4)`.
* **`PATH_TWEEN (Opcode 158)` & `DRAW_TWEEN_PATH (Opcode 125)`**:
  * Morphs between two topologically compatible paths given interpolation parameter $t \in [0.0, 1.0]$:
    $$P(t) = (1 - t) \times P_1 + t \times P_2$$
  * If point counts differ, the player must subdivide segments to achieve topological parity.

---

## 6. Conformance Requirements

1. **Matrix Stack Balancing**: A document popping the matrix stack (`MATRIX_RESTORE`) more times than it pushed (`MATRIX_SAVE`) must not crash the player; excess restores must be ignored.
2. **Path Morphing Continuity**: `DRAW_TWEEN_PATH` with $t=0.0$ must match `DRAW_PATH(P1)` exactly; $t=1.0$ must match `DRAW_PATH(P2)` exactly.
3. **Clip Rect Containment**: Operations issued after `CLIP_RECT` must be strictly bounded within the declared rectangle; no pixels may leak into neighboring viewports.
