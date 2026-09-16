# AREA-03: Document Manipulation, Loom & Procedural Functions

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose incorporates a late-expansion metaprogramming and component templating engine known as **Loom** (technically implemented via binary **Patterns**). 

Loom allows a document to transmit complex UI components, design tokens, and recurring layout patterns once as compact binary definitions (`PatternDefine`), and instantiate them multiple times with dynamic parameter bindings (`PatternInflation`). Expansion is executed client-side on the Player during the **Materialization** phase, ensuring optimal binary compression while preserving dynamic state isolation.

---

## 2. Architecture & The Expansion Pipeline

The Loom lifecycle operates in three strict phases:

```
[1. Registration Phase]
Wire stream parsing encounters PatternDefine -> Binary payload captured in LoomManager
                                |
                                v
[2. Materialization / Inflation Phase]
Document iterates operations; encounters PatternInflation (or IncludeReferencedOperations):
    ├── A. Parameter Seeding: Bind argument IDs to parameter IDs in RemapContext
    ├── B. Buffer Wrapping: Wrap macro body in LoomWireBuffer
    ├── C. Re-reading: Call standard Operation.read(LoomWireBuffer)
    ├── D. Local ID Allocation: Dynamically re-assign macro-local IDs (0x4000 - 0x4FFF)
    ├── E. Slot Substitution: Replace PatternArgument with PatternBlock payloads
    └── F. Collection Expansion: Fork context for each item in PatternForEach
                                |
                                v
[3. Active Hierarchy]
Resulting materialized operations are nested into CoreDocument and layout tree
```

---

## 3. ID Tiering & Remapping Architecture

To guarantee state isolation across multiple instances of the same template, RemoteCompose partitions the 32-bit ID space into three distinct tiers:

### 3.1 ID Tiers

| Tier | ID Range | Scope | Player Remapping Invariant |
| :--- | :--- | :--- | :--- |
| **Tier 1 (Global System IDs)** | `0` to `41` | Player Runtime | **NEVER remapped**. Reserved for predefined system variables (e.g., `ID_WINDOW_WIDTH`, `ID_CONTINUOUS_SEC`, `ID_ANIMATION_TIME`). |
| **Tier 2 (Template-Local IDs)**| `0x4000` to `0x4FFF` | Macro Instance | **MUST be remapped** to a unique document-wide ID for *every* inflation site via `buffer.declareId()`. |
| **Tier 3 (Document IDs)** | All other IDs | Document Scope | When inside a macro expansion (`isInsideMacro == true`), any declared ID that is not Tier 1 is dynamically reallocated via `mDocument.getNextId()` to prevent clashing with outer scopes. |

### 3.2 WireBuffer Remapping Protocol (`LoomWireBuffer` & `RemapContext`)

Operations participate in Loom remapping by interacting with the player's `WireBuffer` through four normative methods:

1. **`buffer.declareId()`**:
   * Invoked when reading an operation that **defines** a new variable or component ID (e.g., `DATA_FLOAT`, `COMPONENT_START`).
   * If the ID has an existing mapping in `RemapContext`, returns the mapped value.
   * If the read ID falls within Tier 2 (`0x4000`–`0x4FFF`), the `RemapContext` allocates a new unique ID via `mDocument.getNextId()`, stores `originalId -> newId`, and returns `newId`.
   * If `isInsideMacro` is active and the ID is not Tier 1 (`!isSystemGlobal(id)`), a fresh ID is allocated and recorded.
   * Otherwise returns the original ID unchanged.
2. **`buffer.readId()` / `buffer.resolveId()`**:
   * Invoked when reading an operation that **references** an existing ID (e.g., `DRAW_PATH(id)`).
   * Looks up the ID in the current `RemapContext`. If mapped, returns the mapped ID; otherwise returns the original ID (allowing global document references).
3. **`buffer.readNanId()` / `buffer.resolveNanId()`**:
   * Inspects float values. If non-NaN, returns the float unchanged.
   * If NaN-encoded, extracts the 23-bit ID (`fromNaN`), resolves it via `resolveId(id)`, and repacks the resolved ID into a NaN float via `asNan(mappedId)`.
4. **`buffer.readLongNanId()` / `buffer.resolveLongNanId()`**:
   * Resolves 64-bit encoded references: decodes via `id = (int)(v - 0x100000000L)`, resolves `mapped = resolveId(id)`, and repacks via `((long) mapped) + 0x100000000L`.

### 3.3 Language-Agnostic ID Remapping Pseudocode

A conforming clean-room player (e.g. Swift or Windows C#) can implement `RemapContext` using the following reference logic:

```python
class RemapContext:
    def __init__(self, document, is_inside_macro: bool = False):
        self.id_map: dict[int, int] = {}
        self.document = document
        self.is_inside_macro = is_inside_macro

    def declare_id(self, original_id: int) -> int:
        if original_id == -1:
            return -1
        if original_id in self.id_map:
            return self.id_map[original_id]
        
        # Tier 2: Macro-Local IDs (0x4000 to 0x4FFF) always require a fresh unique ID
        if 0x4000 <= original_id <= 0x4FFF:
            new_id = self.document.get_next_id()
            self.id_map[original_id] = new_id
            return new_id
            
        # Regular IDs inside macro expansion (except Tier 1 System Globals 0..41)
        if self.is_inside_macro and not (0 <= original_id <= 41):
            new_id = self.document.get_next_id()
            self.id_map[original_id] = new_id
            return new_id
            
        return original_id

    def resolve_id(self, original_id: int) -> int:
        if original_id == -1:
            return -1
        return self.id_map.get(original_id, original_id)

    def resolve_nan_id(self, float_val: float) -> float:
        if not is_nan(float_val):
            return float_val
        raw_id = float_to_raw_int_bits(float_val) & 0x3FFFFF
        mapped_id = self.resolve_id(raw_id)
        if mapped_id == raw_id:
            return float_val
        return int_bits_to_float(mapped_id | 0xFF800000)

    def resolve_long_nan_id(self, long_val: int) -> int:
        if long_val < 0x100000000:
            return long_val  # Literal integer value
        raw_id = long_val - 0x100000000
        mapped_id = self.resolve_id(raw_id)
        return mapped_id + 0x100000000 if mapped_id != raw_id else long_val
```

---

## 4. Loom Operations Specification

### 4.1 PatternDefine (`MACRO_DEFINE`, Opcode 246)
Declares a named template.
* **Fields**:
  * `name: UTF8`: Unique identifier string for the template.
  * `parameterCount: INT`: Number of formal parameters.
  * `parameterIds: INT[]`: Array of parameter IDs declared inside the macro.
  * `bodySize: INT`: Byte length of the serialized macro body.
  * `body: BUFFER`: Binary operations comprising the macro.
* **Player Behavior**: Stores the binary slice in `LoomManager`. Does **not** instantiate components or execute operations during initial parse.

### 4.2 PatternInflation (`MACRO_CALL`, Opcode 247)
Instantiates a template at the current location in the document tree.
* **Fields**:
  * `name: UTF8`: Identifier of the macro to inflate.
  * `argumentCount: INT`: Number of argument bindings.
  * `argumentIds: INT[]`: Concrete argument IDs provided at call site.
  * `blockCount: INT`: Number of slot block replacements.
  * `blocks: PatternBlock[]`: Array of concrete child operations for slots.
* **Player Behavior**:
  1. Retrieves the binary body from `LoomManager`.
  2. Creates a `RemapContext` with `isInsideMacro = true`, seeded with `parameterIds[i] -> argumentIds[i]`.
  3. Re-reads the body through `LoomWireBuffer`.
  4. Appends resulting operations into the current container or modifier list.

### 4.3 Slot-Based Composition (`PatternArgument` & `PatternBlock`)
* **`PatternArgument (MACRO_ARGUMENT, Opcode 248)`**: Declared within a macro body as a placeholder slot.
* **`PatternBlock (MACRO_BLOCK, Opcode 249)`**: Emitted at the `PatternInflation` call site.
* **Resolution**: When inflation encounters `PatternArgument(slotId)`, the player swaps the argument operation with the list of operations supplied in the matching `PatternBlock`.

### 4.4 Collection Iteration (`PatternForEach`, Opcode 244)
Repeats a template block across each element of a collection (e.g., `ID_LIST`).
* **Fields**:
  * `collectionId: INT`: Reference to an `ID_LIST` or array variable.
  * `localItemId: INT`: The synthetic ID used inside the loop body to reference the current item.
  * `body: CONTAINER`: Child operations repeated per item.
* **Execution Algorithm**:
  1. Resolves `collectionId` to obtain item count $N$ and item array $[I_0, I_1, \dots, I_{N-1}]$.
  2. For iteration index $k \in [0, N-1]$:
     * Forks `RemapContext` via `context.fork()`.
     * Maps `localItemId -> I_k`.
     * Materializes loop body operations with the remapped context.

---

## 5. Procedural Functions Subsystem

For non-structural mathematical logic, RemoteCompose provides procedural float subroutines.

### 5.1 FunctionDefine (`Opcode 168`)
* **Fields**:
  * `funcId: INT`: Function identifier.
  * `paramCount: INT`: Number of float parameters popped from stack.
  * `body: BUFFER`: RPN expression bytecode.
* **Execution**: Stored in `RemoteContext` function table.

### 5.2 FunctionCall (`Opcode 166`)
* **Fields**:
  * `funcId: INT`: Target function to invoke.
  * `argCount: INT`: Number of arguments passed.
  * `args: INT[]`: Float expression IDs passed as parameters.
* **Call Stack Invariants**:
  * Arguments are evaluated and pushed onto a private stack frame.
  * Subroutines MUST NOT exceed a call depth of **8 frames** (to prevent unbounded recursion).
  * Return value is left on the top of the evaluation stack.

---

## 6. Incremental Document Updates (`updateDocument`)

A player may receive incremental document buffers to patch an already running document:
1. **In-Place Mutation**: Operations updating existing variable IDs (e.g., `DATA_FLOAT`, `DATA_TEXT`) immediately update values in `RemoteContext` and mark dependent expressions dirty.
2. **Macro Invalidation**: If an incoming patch defines a macro with an existing name, the old macro in `LoomManager` is overwritten, and `player.reinflate()` is triggered to reconstruct affected UI subtrees.

---

## 7. Conformance Requirements

1. **Independent State Isolation**: Instantiating a macro with an internal Tier 2 counter ID twice must yield two separate, non-colliding variable entries in `RemoteContext`.
2. **Recursion Defense**: Circular macro inflation (Template A calls Template B calls Template A) MUST be aborted when expansion depth exceeds **16 levels**.
3. **Slot Replacement Invariant**: A `PatternArgument` slot must preserve the parent container's layout constraints when populated by a `PatternBlock`.
