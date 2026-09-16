# AREA-17: Conformance, Diagnostics & Debugging Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

To guarantee uniform behavior and visual equivalence across player runtimes (Android View Player and Jetpack Compose Player), RemoteCompose provides standardized runtime diagnostics, performance telemetry APIs, multi-level debug modes, wire-level debug operations (`DEBUG_MESSAGE`), and a normative conformance verification test protocol.

---

## 2. Runtime Telemetry & Diagnostics APIs

A conforming player exposes performance telemetry APIs for real-time profiling:

### 2.1 Telemetry Methods (`RemoteComposePlayer`)
* **`getOpsPerFrame(): INT`**: Returns the exact count of operations processed during the most recent render frame cycle. Incremented via `context.incrementOpCount()` across paint operations, modifier executions, and child traversals.
* **`getAnimationTime(): FLOAT`**: Returns the active monotonic animation clock time in continuous seconds.

### 2.2 Threshold Invariants
* The total operations executed per frame must not exceed `Limits.MAX_OP_COUNT = 20,000`. If exceeded, playback is halted for that frame to prevent UI freezing.

---

## 3. Debug Modes & Diagnostics Logging (`setDebug`)

The player provides graduated debug modes toggled via `player.setDebug(int level)`:

```
+-------------------------------------------------------------------------------+
| Player Debug Levels (RemoteContext)                                           |
+-------------------------------------------------------------------------------+
       |
       ├── DEBUG_NONE (0)                                                       |
       |      - Normal production playback; zero debug overhead                 |
       |                                                                        |
       ├── DEBUG_BASIC (1) [isBasicDebug()]                                     |
       |      - Logs performClick dispatches and action ID payloads to logcat   |
       |      - Traces exception handlers and component click routing           |
       |                                                                        |
       ├── DEBUG_VISUAL (2) [isVisualDebug()]                                   |
       |      - Renders visual overlays on ClickAreaView and interactive bounds |
       |                                                                        |
       └── DEBUG_LAYOUT (3) [isLayoutDebug()]                                   |
              - Sets DebugLog.DEBUG_LAYOUT_ON = true                            |
              - Verbose console tracing of 2-pass layout, measure policies,     |
              - cache hits/misses, and relayout boundary propagation            |
```

### 3.1 Wire-Level Diagnostic Operation (`DEBUG_MESSAGE`, Opcode 179)
Documents can embed non-production diagnostic probes via `DebugMessage.java`:
* **Wire Fields**:
  * `textId: INT`: Text variable ID containing the diagnostic label.
  * `value: FLOAT`: Dynamic float expression or variable reference evaluated at runtime.
  * `flags: INT`: Diagnostic flags (`SHOW_USAGE = 1` to dump memory/variable table statistics).
* **Behavior**: When executed, prints formatted debug output to the player's diagnostic console.

---

## 4. Conformance Verification Protocol & Machine-Readable Test Vectors

Any RemoteCompose player implementation (Android View, Jetpack Compose, or clean-room native ports such as Swift/CoreGraphics or Windows Direct2D) must achieve conformance by passing the following standardized verification suites.

### 4.1 Minimal Valid Document Reference Byte Stream (Hex Fixture)

The following 68-byte sequence represents an authoritative, minimal valid RemoteCompose document containing a Map-Based Header ($256 \times 256$ pixels), a root `ComponentStart` container, a `DrawRect` primitive ($10, 10$ to $100, 100$), and a closing `ContainerEnd`. Implementations can use this exact byte array to validate their binary deserializer:

```
Offset (Hex)  Byte Values (Hex)                      Semantic Decoding
0000          00                                     Opcode 0: Operations.HEADER
0001..0004    04 8C 00 07                            Magic 0x048C0000 | Major Version 7
0005..0008    00 00 00 00                            Minor Version: 0
0009..000C    00 00 00 00                            Patch Version: 0
000D..0010    00 00 00 02                            Property Map Entries: 2
0011..0012    00 05                                  Entry 1 Tag: Type 0 (INT), Key 5 (DOC_WIDTH)
0013..0014    00 04                                  Entry 1 Length: 4 bytes
0015..0018    00 00 01 00                            Entry 1 Value: 256 (INT)
0019..001A    00 06                                  Entry 2 Tag: Type 0 (INT), Key 6 (DOC_HEIGHT)
001B..001C    00 04                                  Entry 2 Length: 4 bytes
001D..0020    00 00 01 00                            Entry 2 Value: 256 (INT)
0021          02                                     Opcode 2: Operations.COMPONENT_START
0022..0025    00 00 00 00                            Component Type: 0 (DEFAULT)
0026..0029    00 00 00 2A                            Component ID: 42 (declareId)
002A..002D    43 80 00 00                            Width: 256.0f (FLOAT)
002E..0031    43 80 00 00                            Height: 256.0f (FLOAT)
0032          2A                                     Opcode 42: Operations.DRAW_RECT
0033..0036    41 20 00 00                            Left (x1): 10.0f (FLOAT)
0037..003A    41 20 00 00                            Top (y1): 10.0f (FLOAT)
003B..003E    42 C8 00 00                            Right (x2): 100.0f (FLOAT)
003F..0042    42 C8 00 00                            Bottom (y2): 100.0f (FLOAT)
0043          D6                                     Opcode 214: Operations.CONTAINER_END
```

#### Raw Contiguous Hex String (Copy-Pasteable for Unit Tests)
```
00048C00070000000000000000000000020005000400000100000600040000010002000000000000002A43800000438000002A412000004120000042C8000042C80000D6
```

* **Pass Criterion**: The deserializer must yield exactly 3 top-level materialized structures: `Header` (Width: 256, Height: 256), a root container (ID: 42, Width: 256.0, Height: 256.0) containing one `DrawRect` child ($10.0, 10.0, 100.0, 100.0$), terminated cleanly without buffer underflow or overflow.

---

### 4.2 Mathematical Expression Test Vectors (RPN Evaluation Suite)

All operator tokens below use the standard base offset $\text{opToken} = \text{asNan}(0x310000 + \text{opId})$.

| Test # | Description | Expression Array (Postfix RPN) | Context Variables | Expected Output | Invariant Verified |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RPN-01** | Basic Addition | `[3.0, 4.0, OP_ADD]` | None | `7.0` | $a + b$ |
| **RPN-02** | Operator Precedence | `[3.0, 4.0, OP_ADD, 2.0, OP_MUL]` | None | `14.0` | Postfix stack order $(3 + 4) \times 2$ |
| **RPN-03** | Division by Zero | `[10.0, 0.0, OP_DIV]` | None | `0.0` | Division by zero returns $0.0$ without throwing |
| **RPN-04** | Modulo with Truncation| `[7.0, 3.0, OP_MOD]` | None | `1.0` | Remainder $7 \pmod 3$ |
| **RPN-05** | Ternary Condition (True)| `[1.0, 42.0, 99.0, OP_IFELSE]` | None | `42.0` | Conditional select $c \ne 0 \ ? \ t : f$ |
| **RPN-06** | Ternary Condition (False)| `[0.0, 42.0, 99.0, OP_IFELSE]` | None | `99.0` | Conditional select $c == 0 \ ? \ t : f$ |
| **RPN-07** | Range Clamping (High)| `[0.0, 100.0, 150.0, OP_CLAMP]` | None | `100.0` | $\text{clamp}(val, min, max) = 100$ |
| **RPN-08** | Variable ID Unboxing | `[asNan(42), 5.0, OP_ADD]` | `var[42] = 12.5` | `17.5` | Unbox NaN ID $42 \to 12.5$, then $12.5 + 5.0$ |
| **RPN-09** | Square Root Negative | `[-4.0, OP_SQRT]` | None | `0.0` | $\sqrt{\max(0, -4.0)} = 0.0$ |
| **RPN-10** | Trig Pythagorean Identity | `[1.0, OP_SIN, OP_SQUARE, 1.0, OP_COS, OP_SQUARE, OP_ADD]` | None | `1.0` | $\sin^2(1) + \cos^2(1) = 1.0$ |

---

### 4.3 Layout Constraint Verification Suite

Validates child measurement, positioning, and container dimension resolution across layout managers:

#### Fixture L-01: `RowLayout` under Fixed Parent Constraints ($W=300, H=100$)
* **Parent Constraints**: `minWidth = 300, maxWidth = 300, minHeight = 100, maxHeight = 100`
* **Child Specifications**:
  * **Child 1**: Fixed dimensions: `width = 100, height = 50`
  * **Child 2**: Fixed dimensions: `width = 50, height = 50`
  * **Child 3**: Weighted expand: `weight = 1.0, height = 50`
* **Expected Resolved Bounds**:
  * Container Bounding Box: `[x: 0, y: 0, width: 300, height: 100]`
  * Child 1: `[x: 0, y: 25, width: 100, height: 50]` (vertically centered)
  * Child 2: `[x: 100, y: 25, width: 50, height: 50]`
  * Child 3: `[x: 150, y: 25, width: 150, height: 50]` (remaining 150px assigned to weight 1.0)

#### Fixture L-02: `ColumnLayout` under Wrap-Content Constraints
* **Parent Constraints**: `minWidth = 0, maxWidth = 400, minHeight = 0, maxHeight = 800`
* **Child Specifications**:
  * **Child 1**: Fixed dimensions: `width = 80, height = 40`
  * **Child 2**: Fixed dimensions: `width = 120, height = 60`
* **Expected Resolved Bounds**:
  * Container Bounding Box: `[x: 0, y: 0, width: 120, height: 100]` ($\max(w_i), \sum h_i$)
  * Child 1: `[x: 0, y: 0, width: 80, height: 40]`
  * Child 2: `[x: 0, y: 40, width: 120, height: 60]`

#### Fixture L-03: `FitBoxLayout` (Content Scale FIT / Aspect Fit)
* **Parent Constraints**: `minWidth = 200, maxWidth = 200, minHeight = 200, maxHeight = 200`
* **Content Original Size**: `width = 400, height = 200` (Aspect ratio $2:1$)
* **Scale Factor**: $\min(200 / 400, 200 / 200) = 0.5$
* **Expected Resolved Child Bounds**:
  * Child: `[x: 0, y: 50, width: 200, height: 100]` (aspect preserved, centered vertically with 50px top/bottom margins)

---

## 5. Conformance Requirements

1. **Telemetry Accuracy**: `getOpsPerFrame()` must accurately report the count of operations executed during `apply()` and `paint()`.
2. **Debug Isolation**: Enabling `setDebug()` must alter only visual inspection overlays and diagnostic logging; it MUST NOT alter component layout geometry, measure outcomes, or interaction touch targets.
3. **Golden Test Suite Passing**: An implementation is not certified as a conforming RemoteCompose Player until it achieves a 100% pass rate on the standard test suite.
