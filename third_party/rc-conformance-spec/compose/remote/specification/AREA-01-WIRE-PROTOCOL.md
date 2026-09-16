# AREA-01: Wire Protocol, Binary Encoding & Materialization Pipeline

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

The RemoteCompose Wire Protocol defines the serialization of UI commands, expressions, layouts, and assets into a compact, streaming binary buffer. 

A conforming RemoteCompose Player must parse this binary stream deterministically, negotiate protocol versioning and feature profiles, instantiate runtime operational representations, and execute the materialization pipeline.

---

## 2. Binary Encoding & Type Representation

All multi-byte numerical primitives in the wire format are encoded strictly in **Big-Endian (Network Byte Order)**. Host architectures running in Little-Endian mode (e.g. x86_64, ARM64) MUST swap byte orders when reading or writing multi-byte fields. There are NO padding or alignment bytes between fields or operations; bytes are packed contiguously.

| Type | Size (Bytes) | Wire Encoding & Range | Invariant / Extraction Formula |
| :--- | :--- | :--- | :--- |
| `BYTE` | 1 | 8-bit unsigned integer `[0, 255]` | Read raw byte: `0xFF & buffer[index++]` |
| `BOOLEAN` | 1 | Encoded as `0x00` (false) or `0x01` (true) | `buffer[index++] == 1` |
| `SHORT` | 2 | 16-bit signed integer (Big-Endian) | `(buffer[index] & 0xFF) << 8 \| (buffer[index+1] & 0xFF)` |
| `INT` | 4 | 32-bit signed integer (Big-Endian) | `(B0 << 24) \| (B1 << 16) \| (B2 << 8) \| B3` |
| `FLOAT` | 4 | IEEE 754 32-bit single-precision float | Read `INT` bits, then bitcast: `Float.intBitsToFloat(readInt())` |
| `LONG` | 8 | 64-bit signed integer (Big-Endian) | 8 contiguous bytes combined via bitwise-OR with 64-bit masks |
| `DOUBLE` | 8 | IEEE 754 64-bit double-precision float | Read `LONG` bits, then bitcast: `Double.longBitsToDouble(readLong())` |
| `BUFFER` | 4 + N | Prefixed with `INT` length $N$, followed by $N$ bytes | If $N < 0$ or $N > \text{maxSize}$, abort with read error |
| `UTF8` | 4 + N | Serialized as a `BUFFER` of UTF-8 bytes | **No null terminator `\0`**; string length is determined solely by $N$ |
| `FLOAT_ID` | 4 | Encoded as an IEEE 754 `NaN`, mantissa stores ID | Unboxed via `(Float.floatToRawIntBits(f) & 0x3FFFFF)` |
| `LONG_NAN_ID` | 8 | 64-bit encoded ID reference | `((long) id) + 0x100000000L` (values $\ge 2^{32}$ are IDs) |

### 2.1 NaN-Encoded ID Packing & Address Space Partitioning

When dynamic variables or operations are passed in place of constant floats, RemoteCompose packs integer identifiers into IEEE 754 single-precision `NaN` bit patterns (`NanMap` and `Utils`):

* **Packing (`Utils.asNan(v)`)**: An integer ID $v$ is packed into a 32-bit float via:
  $$f = \text{Float.intBitsToFloat}(v \mid \text{0xFF800000})$$
  Because the exponent bits are all 1s (`0x7F800000`) and the mantissa contains non-zero ID bits, the IEEE 754 standard defines this bit pattern as a Quiet `NaN`.
* **Unpacking (`Utils.idFromNan(f)`)**: The variable/component identifier is extracted by masking the lower 22 bits:
  $$\text{id} = \text{Float.floatToRawIntBits}(f) \ \& \ \text{0x3FFFFF}$$
* **Extended Domain Unpacking (`NanMap.fromNaN(f)`)**: For full 23-bit mantissa operations (path and expression operator tokens):
  $$\text{id} = \text{Float.floatToRawIntBits}(f) \ \& \ \text{0x7FFFFF}$$
* **Detection**: A float value is recognized as an encoded dynamic variable/ID if and only if `Float.isNaN(f)` evaluates to true. If `Float.isNaN(f)` is false, the value must be treated as a literal constant float.

The 23-bit ID space is strictly segmented by the top 3 bits (`id >> 20`):

| Region | Bit Range | Type Constant | Description |
| :--- | :--- | :--- | :--- |
| `0x000000`–`0x0FFFFF` | `0` | `TYPE_SYSTEM` | System variables (IDs $0$–$41$ are system globals; `START_VARIABLE_ID = 42`) |
| `0x100000`–`0x1FFFFF` | `1` | `TYPE_VARIABLE` | Normal document variables (`START_VAR = (1 << 20) + 42 = 0x10002A`) |
| `0x200000`–`0x2FFFFF` | `2` | `TYPE_ARRAY` | Data collections, lists, and maps (`ID_REGION_ARRAY = 0x200000`) |
| `0x300000`–`0x3FFFFF` | `3` | `TYPE_OPERATION` | Path operators (`0x300000`–`0x300006`) & RPN Math operators (`OFFSET = 0x310000`) |
| `0x400000`–`0x7FFFFF` | `4..7` | *Reserved* | Reserved for future engine extensions |

### 2.2 Long NaN ID Unpacking

For 64-bit integer variables or long attributes, an ID is encoded as:
$$\text{wireValue} = ((\text{long}) \text{id}) + \text{0x100000000L}$$
The player resolves the original ID via:
$$\text{id} = \text{wireValue} - \text{0x100000000L}$$
Values where $\text{wireValue} < \text{0x100000000L}$ are interpreted as raw literal integers.

### 2.3 Low-Level Wire Framing & Opcode Layout Invariant

Every operation in the stream follows a strict sequential framing:
1. **Opcode Tag**: Exactly 1 byte (`BYTE`, $0..255$) identifying the operation type.
2. **Payload Fields**: Contiguous, type-specific fields immediately follow the opcode tag without alignment padding.
3. **No Inter-Opcode Delimiters**: There are no framing sync words, CRCs, or padding bytes between operations. Deserialization relies on the exact schema length of each operation. For variable-sized operations (e.g. `PathAppend`, `DataListFloat`, `TextLookupInt`), the length of subsequent data is explicitly declared in an early integer or short field of that operation.

---

## 3. Header Specification (`Opcode 0`)

The first operation of every valid RemoteCompose document must be the `Header` operation (`Operations.HEADER = 0`).

RemoteCompose supports two wire layouts: the modern **Map-Based Header** (API Level $\ge 7$) and the **Legacy Flat Header** (API Level $< 7$).

### 3.1 Modern Map-Based Header (API Level $\ge 7$)

```
+-------------------------------------------------------------------------------+
| Opcode (BYTE: 0)                                                              |
| Major Version & Magic Number (INT: MAJOR_VERSION | 0x048C0000)                |
| Minor Version (INT)                                                           |
| Patch Version (INT)                                                           |
| Property Map Entry Count (INT: N, 0 <= N <= MAX_TABLE_SIZE)                   |
| [Repeated N Property Entries: Tag (SHORT), ItemLen (SHORT), Value Payload]    |
+-------------------------------------------------------------------------------+
```

* **Magic Number**: The upper 16 bits of the major version field must equal `0x048C0000`. Conforming players MUST reject streams where `(majorVersion & 0xFFFF0000) != 0x048C0000` with an `IOException`.
* **Major Version**: Extracted via `majorVersion & 0xFFFF`.

#### Property Map Entry Encoding
Each property entry consists of:
1. **`Tag` (`SHORT`)**: 16-bit packed field:
   * Data Type (`Tag >> 10`):
     * `DATA_TYPE_INT = 0`: 4-byte signed `INT`
     * `DATA_TYPE_FLOAT = 1`: 4-byte IEEE 754 `FLOAT`
     * `DATA_TYPE_LONG = 2`: 8-byte signed `LONG`
     * `DATA_TYPE_STRING = 3`: 4-byte length $S$ followed by $S$ bytes of UTF-8 text
   * Key (`Tag & 0x3F`): 6-bit property key identifier

#### Standard Header Property Keys
| Key ID | Constant | Data Type | Description |
| :--- | :--- | :--- | :--- |
| `5` | `DOC_WIDTH` | `INT` | Document canvas width in pixels (default: `256`) |
| `6` | `DOC_HEIGHT` | `INT` | Document canvas height in pixels (default: `256`) |
| `7` | `DOC_DENSITY_AT_GENERATION` | `FLOAT` | Display density at document generation (default: `1.0`) |
| `8` | `DOC_DESIRED_FPS` | `INT` | Desired document frame rate |
| `9` | `DOC_CONTENT_DESCRIPTION` | `STRING` | Root accessible content description |
| `11` | `DOC_SOURCE` | `STRING` | Source / generator origin of the document |
| `12` | `DOC_DATA_UPDATE` | `INT` | Flag indicating document is an incremental state update |
| `13` | `HOST_EXCEPTION_HANDLER` | `INT` | Host action ID to dispatch if an unhandled exception occurs |
| `14` | `DOC_PROFILES` | `INT` | Required profile capabilities bitmask |
| `15` | `FEATURE_PAINT_MEASURE` | `INT` | Direct measure in paint pass toggle |
| `16` | `DEBUG` | `INT` | Verbosity debug logging level (`0=Off, 1..3=Verbose`) |
| `17` | `FEATURE_MEASURE_VERSION` | `INT` | Layout measure policy version (`0..4`, default: `4`) |
| `18` | `FEATURE_TOUCH_VERSION` | `INT` | Touch event processing version (`0=Legacy, 1=FixTouch`) |
| `19` | `TEST_TIME` | `LONG` | Test harness capture time (ms since epoch) |
| `20` | `TEST_AFTER` | `FLOAT` | Test harness capture offset in seconds |
| `21` | `TEST_COLOR_THEME` | `STRING` | Test harness simulated theme overrides |
| `22` | `TEST_ACTIONS` | `STRING` | Test harness simulated touch action script |
| `23` | `FEATURE_PRIORITY_FIX` | `INT` | Fix priority logic in collapsible layouts toggle |
| `24` | `FEATURE_LT_RESIZE` | `INT` | Enable origin-aware resizing animations |
| `25` | `FEATURE_ARRAY_LISTENERS` | `INT` | Enable listener pattern for arrays in TextLookup |
| `26` | `FEATURE_CLICK_VERSION` | `INT` | Click behavior version (`0=Single/Double/Long, 1=SingleOnly`) |
| `27` | `DOC_DENSITY_BEHAVIOR` | `INT` | Density behavior (`0=Mixed, 1=Pixels, 2=Dp`) |
| `28` | `FEATURE_OPTIMIZATION_LEVEL` | `INT` | Layout optimization bitmask (`0=None, 1=MeasureCache, 2=LayoutBoundaries, 4=FlatMeasurePass, 8=ConstraintsCache, 15=All`) |

### 3.2 Legacy Flat Header (API Level $< 7$)

For documents with `majorVersion < 0x10000`:
```
+-------------------------------------------------------------------------------+
| Opcode (BYTE: 0)                                                              |
| Major Version (INT)                                                           |
| Minor Version (INT)                                                           |
| Patch Version (INT)                                                           |
| Width (INT)                                                                   |
| Height (INT)                                                                  |
| Capabilities (LONG)                                                           |
+-------------------------------------------------------------------------------+
```

### 3.3 Minimal Valid Document Wire Sequence

To construct a minimal valid RemoteCompose document (e.g. for parser unit tests), the wire stream contains at minimum:
1. **Header Operation (`Opcode 0`)**: Encodes version and canvas dimensions.
2. **Root Layout / Container (`ROOT_LAYOUT_COMPONENT = 142` or `CANVAS_LAYOUT = 169`)**: Establishes root document bounds and child coordinate space.
3. **Optional Content Operations**: e.g., drawing primitives, paint operations, or child components.
4. **Container End (`COMPONENT_END = 143`)**: Balances the root layout container.

```
+-------------------------------------------------------------------------------+
| [0x00] Header Opcode                                                          |
|   - Major/Magic (INT), Minor (INT), Patch (INT), Property Map Entries ...    |
+-------------------------------------------------------------------------------+
| [0xC8] LAYOUT_ROOT (Opcode 200)                                               |
|   - componentId (INT: declareId)                                              |
+-------------------------------------------------------------------------------+
| [0xD6] CONTAINER_END (Opcode 214)                                             |
+-------------------------------------------------------------------------------+
```

---

## 4. Version & Profile Negotiation

### 4.1 Version Negotiation Algorithm
The player maintains `PLAYER_MAJOR_VERSION` and `PLAYER_MINOR_VERSION`:
1. If `Header.MajorVersion > PLAYER_MAJOR_VERSION`, playback **MUST FAIL** with `UnsupportedDocumentException`. The player cannot safely parse unknown major structural changes.
2. If `Header.MajorVersion < PLAYER_MAJOR_VERSION`, the player enters **Legacy Compatibility Mode**, activating version-specific measure policies and operation shims.
3. If `Header.MajorVersion == PLAYER_MAJOR_VERSION`:
   * If `Header.MinorVersion <= PLAYER_MINOR_VERSION`, the document is fully supported.
   * If `Header.MinorVersion > PLAYER_MINOR_VERSION`, the player may proceed in degraded mode, skipping unknown opcodes if the document profiles permit.

### 4.2 Profiles Negotiation (`RcProfiles`)
The `DOC_PROFILES` bitmask declares required feature profiles:

| Bitmask | Constant | Category | Description |
| :--- | :--- | :--- | :--- |
| `0x000` | `PROFILE_BASELINE` | Baseline | Standard baseline operations supported by all players |
| `0x001` | `PROFILE_EXPERIMENTAL` | Additive | Experimental operations beyond baseline |
| `0x002` | `PROFILE_DEPRECATED` | Additive | Operations deprecated in newer versions |
| `0x004` | `PROFILE_OEM` | Additive | Custom OEM features agreed out-of-band |
| `0x008` | `PROFILE_LOW_POWER` | Additive | Low-power optimized operation subset |
| `0x100` | `PROFILE_WIDGETS` | Intersected | Extended operations for launcher and system widget hosts |
| `0x200` | `PROFILE_ANDROIDX` | Intersected | Extended operations supported in AndroidX libraries |
| `0x800` | `PROFILE_WEAR_WIDGETS`| Intersected | Targeted operation subset for Wear widgets / tiles |

If a document requires a profile bit that the player runtime does not support, the player must immediately abort playback or activate its documented fallback handler.

---

## 5. The Materialization Pipeline

The player ingests raw bytes and transforms them through five discrete stages:

```
[Raw WireBuffer]
       |
       v (Stage 1: Header Ingestion & Capability Check)
[Header Validated]
       |
       v (Stage 2: Sequential Read & Registration)
[Raw Operation Stream]
       |
       v (Stage 3: Late Expansion / Loom Materialization)
[Expanded Operation Stream (Macros resolved, local IDs remapped)]
       |
       v (Stage 4: Structural Nesting & Tree Assembly)
[CoreDocument Hierarchy (RootLayoutComponent, Components, Modifiers)]
       |
       v (Stage 5: State & Variable Linking)
[Active Ready Document (Expressions bound, listeners registered)]
```

### 5.1 Unknown Opcode Handling
During Stage 2, if an unrecognized opcode is encountered:
* If the opcode format specifies an explicit `size` payload, the player advances `mIndex += size` and logs a warning.
* If the opcode length is undefined in the player's lookup table, parsing is terminated to prevent byte alignment corruption.

### 5.2 Structural Nesting (`nestContainers`)
Raw streams represent nested components linearly:
* A `ComponentStart` (or specialized container) pushes a new container node onto the hierarchy stack.
* Operations are added as children of the container currently at the top of the stack.
* A `ComponentEnd` or `ContainerEnd` pops the container, attaching it to its enclosing parent.
* If the stack is empty upon stream completion and the root node is not `RootLayoutComponent`, a synthetic root is constructed to encapsulate top-level operations.

---

## 6. Conformance Requirements

1. **Big-Endian Verification**: Reading the integer `0x12345678` across bytes `[0x12, 0x34, 0x56, 0x78]` must produce exactly `305419896`.
2. **NaN Float ID Extraction**: Packing ID `0x004005` into a NaN float and unpacking it via `fromNaN()` must yield `0x004005`.
3. **Magic Number Verification**: Rejecting any map-based header whose upper 16 bits do not match `0x048C0000`.
4. **Major Version Gate**: A document with major version higher than the player's supported version must be rejected before allocating scene buffers.
5. **Buffer Truncation Defense**: If the buffer runs out of bytes before completing an operation's declared length, the player must cleanly fail with an `EOFException` without memory leaks.
