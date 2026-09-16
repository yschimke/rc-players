# AREA-04: Expression Engine & Reactive State Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose includes an embedded, stack-based **Reverse Polish Notation (RPN)** expression evaluator. 

The Expression Engine allows the player runtime to perform mathematical calculations, state evaluation, boolean logic, and dynamic coordinate transformations locally on each frame. This enables smooth animations and responsive touch feedback at 60/120 FPS without requiring round-trip communication with the document server.

---

## 2. Reverse Polish Notation (RPN) Evaluation Models

RemoteCompose features two optimized evaluation pipelines: **Float RPN** (`AnimatedFloatExpression`) and **Integer RPN** (`IntegerExpressionEvaluator`).

### 2.1 Float Expression Pipeline (`AnimatedFloatExpression`)
Float expressions (`Operations.ANIMATED_FLOAT = 81`) are serialized as a flat array of IEEE 754 32-bit floats in postfix order:
* **Literal Constants**: Any float where `!Float.isNaN(v)` is a literal number pushed directly onto the evaluation stack.
* **Variable References**: Any float where `Float.isNaN(v)` and `(id & ID_REGION_MASK) == TYPE_SYSTEM` or `TYPE_VARIABLE`. The 22-bit ID is unboxed via `Utils.idFromNan(v)`:
  $$\text{id} = \text{Float.floatToRawIntBits}(v) \ \& \ \text{0x3FFFFF}$$
  The current value is fetched dynamically from the host context (`context.getFloat(id)`) and pushed onto the stack.
* **Array / Data Collections**: Any float where `(id & ID_REGION_MASK) == TYPE_ARRAY`. The NaN float itself is pushed onto the stack so subsequent array operators (e.g. `A_DEREF`, `A_MAX`, `A_SPLINE`) can pop the token and inspect the array.
* **Operators**: Encoded as special NaNs in the operation partition (`TYPE_OPERATION`, region $3$):
  $$\text{opFloat} = \text{asNan}(\text{OFFSET} + \text{opId}), \quad \text{OFFSET} = \text{0x310000}$$
  Operators pop their declared arity of arguments from the stack, perform the computation, and push the resulting float back onto the stack.
* **Evaluation Stack**: Local stack of 128 elements. Operands are pushed; operators pop arguments, evaluate, and push the result.

### 2.2 Integer Expression Pipeline (`IntegerExpressionEvaluator`)
Integer expressions (`Operations.INTEGER_EXPRESSION = 144`) are serialized as an `int[] exp` paired with a 32-bit `mask`:
* **Operator Flag**: Element `exp[i]` is an operator if and only if `(mask & (1 << i)) != 0`.
* **Operators**: Encoded with base offset `0x10000` (`I_ADD = 0x10001`, etc.).
* **Literal vs Variable**: If bit $i$ is not set in `mask`, values $\ge 0x10000$ are variable references; values $< 0x10000$ are literal integers.

### 2.3 Language-Agnostic RPN Evaluator Loop Pseudocode

A clean-room player implementation (e.g. Swift or Windows C#) can execute `AnimatedFloatExpression` using the following reference algorithm:

```python
def evaluate_rpn(expression_tokens: list[float], context: RemoteContext) -> float:
    stack = [0.0] * 128
    sp = -1  # Stack pointer points to top element
    
    for token in expression_tokens:
        if not is_nan(token):
            # 1. Literal constant float
            sp += 1
            stack[sp] = token
        else:
            raw_bits = float_to_raw_int_bits(token)
            id = raw_bits & 0x7FFFFF  # 23-bit mantissa
            region = (id >> 20) & 0x7
            
            if region == 3:
                # 2. Operator token (OFFSET = 0x310000)
                op = id - 0x310000
                if op == 1:  # ADD
                    stack[sp - 1] = stack[sp - 1] + stack[sp]
                    sp -= 1
                elif op == 2:  # SUB
                    stack[sp - 1] = stack[sp - 1] - stack[sp]
                    sp -= 1
                elif op == 3:  # MUL
                    stack[sp - 1] = stack[sp - 1] * stack[sp]
                    sp -= 1
                elif op == 4:  # DIV
                    b = stack[sp]
                    stack[sp - 1] = (stack[sp - 1] / b) if b != 0.0 else 0.0
                    sp -= 1
                elif op == 26:  # IFELSE (ternary: cond, true_val, false_val)
                    # Expression stack has [..., cond, true_val, false_val]
                    false_val = stack[sp]
                    true_val = stack[sp - 1]
                    cond = stack[sp - 2]
                    stack[sp - 2] = true_val if cond != 0.0 else false_val
                    sp -= 2
                elif op == 27:  # CLAMP (min, max, val)
                    val = stack[sp]
                    max_v = stack[sp - 1]
                    min_v = stack[sp - 2]
                    stack[sp - 2] = max(min_v, min(max_v, val))
                    sp -= 2
                # ... other operators from catalog ...
            elif region == 2:
                # 3. Array / Collection token: push as operand
                sp += 1
                stack[sp] = token
            else:
                # 4. Variable reference (System or User variable)
                var_id = raw_bits & 0x3FFFFF
                sp += 1
                stack[sp] = context.get_float(var_id)
                
    return stack[sp] if sp >= 0 else 0.0
```

---

## 3. Operator Catalog & Math Semantics

### 3.1 AnimatedFloatExpression Operators (`OFFSET = 0x310000`)

| OpId | Constant | Arity | Description / Semantics |
| :--- | :--- | :--- | :--- |
| `1` | `ADD` | 2 | $a + b$ |
| `2` | `SUB` | 2 | $a - b$ |
| `3` | `MUL` | 2 | $a \times b$ |
| `4` | `DIV` | 2 | $a / b$ (If $b = 0$, returns $0.0$ to prevent exceptions) |
| `5` | `MOD` | 2 | $a \pmod b$ |
| `6` | `MIN` | 2 | $\min(a, b)$ |
| `7` | `MAX` | 2 | $\max(a, b)$ |
| `8` | `POW` | 2 | $a^b$ |
| `9` | `SQRT` | 1 | $\sqrt{\max(0, a)}$ |
| `10` | `ABS` | 1 | $\|a\|$ |
| `11` | `SIGN` | 1 | $\text{signum}(a)$ |
| `12` | `COPY_SIGN` | 2 | $\text{copySign}(a, b)$ |
| `13` | `EXP` | 1 | $e^a$ |
| `14` | `FLOOR` | 1 | $\lfloor a \rfloor$ |
| `15` | `LOG` | 1 | $\log_{10}(a)$ |
| `16` | `LN` | 1 | $\ln(a)$ |
| `17` | `ROUND` | 1 | $\text{round}(a)$ |
| `18` | `SIN` | 1 | $\sin(a)$ (radians) |
| `19` | `COS` | 1 | $\cos(a)$ (radians) |
| `20` | `TAN` | 1 | $\tan(a)$ (radians) |
| `21` | `ASIN` | 1 | $\arcsin(\text{clamp}(a, -1, 1))$ |
| `22` | `ACOS` | 1 | $\arccos(\text{clamp}(a, -1, 1))$ |
| `23` | `ATAN` | 1 | $\arctan(a)$ |
| `24` | `ATAN2` | 2 | $\text{atan2}(y, x)$ |
| `25` | `MAD` | 3 | Multiply-Add: $a \times b + c$ |
| `26` | `IFELSE` | 3 | $c \ne 0 \ ? \ t : f$ |
| `27` | `CLAMP` | 3 | $\max(\min, \min(\max, v))$ |
| `28` | `CBRT` | 1 | $\sqrt[3]{a}$ |
| `29` | `DEG` | 1 | Radians to degrees ($a \times 57.29578$) |
| `30` | `RAD` | 1 | Degrees to radians ($a \times 0.017453292$) |
| `31` | `CEIL` | 1 | $\lceil a \rceil$ |
| `32` | `A_DEREF` | 2 | Array element lookup: $\text{array}[\text{index}]$ |
| `33` | `A_MAX` | 1 | Maximum element in array |
| `34` | `A_MIN` | 1 | Minimum element in array |
| `35` | `A_SUM` | 1 | Sum of elements in array |
| `36` | `A_AVG` | 1 | Average of elements in array |
| `37` | `A_LEN` | 1 | Length of array |
| `38` | `A_SPLINE` | 2 | Evaluates monotonic cubic spline across array at position $t$ |
| `39` | `RAND` | 0 | Pseudo-random float $\in [0.0, 1.0)$ |
| `40` | `RAND_SEED` | 1 | Sets random generator seed |
| `41` | `NOISE_FROM` | 1 | Deterministic 1D noise from seed value |
| `42` | `RAND_IN_RANGE`| 2 | Random float between $[min, max]$ |
| `43` | `SQUARE_SUM` | 2 | $a^2 + b^2$ |
| `44` | `STEP` | 2 | $x > \text{edge} \ ? \ 1.0 : 0.0$ |
| `45` | `SQUARE` | 1 | $a \times a$ |
| `46` | `DUP` | 1 | Duplicates top of stack |
| `47` | `HYPOT` | 2 | $\sqrt{x^2 + y^2}$ |
| `48` | `SWAP` | 2 | Swaps top two stack values |
| `49` | `LERP` | 3 | Linear interpolation: $(1 - t) x + t y$ |
| `50` | `SMOOTH_STEP` | 3 | Hermite smooth interpolation between edges |
| `51` | `LOG2` | 1 | $\log_2(a)$ |
| `52` | `INV` | 1 | $1 / a$ |
| `53` | `FRACT` | 1 | Fractional part: $a - \lfloor a \rfloor$ |
| `54` | `PINGPONG` | 2 | Periodic ping-pong bounce between $[0, y]$ |
| `55` | `NOP` | 0 | No operation |
| `56..59` | `STORE_R0..R3` | 1 | Stores top of stack into scratch register $R_0 \dots R_3$ |
| `60..63` | `LOAD_R0..R3` | 0 | Loads value from scratch register $R_0 \dots R_3$ |
| `64..67` | `CMD1..CMD4` | * | Reserved for domain-specific operators |
| `70` | `VAR1` | 0 | Context variable 1 (loop index / particle index) |
| `71` | `VAR2` | 0 | Context variable 2 (delta time $\Delta t$) |
| `72` | `VAR3` | 0 | Context variable 3 |
| `73` | `CHANGE_SIGN` | 1 | Negates top of stack: $-a$ |
| `74` | `CUBIC` | 5 | Cubic bezier evaluation: $(x_1, y_1, x_2, y_2, t) \to y$ |
| `75` | `A_SPLINE_LOOP`| 2 | Periodic looping spline evaluation |
| `76` | `A_SUM_TILL` | 2 | Sums array elements up to index $N$ |
| `77` | `A_SUM_XY` | 2 | Dot-product sum of two arrays $\sum A_i B_i$ |
| `78` | `A_SUM_SQR` | 1 | Sum of squared elements $\sum A_i^2$ |
| `79` | `A_LERP` | 2 | Piecewise linear interpolation across array |

### 3.2 IntegerExpression Evaluator Operators (`OFFSET = 0x10000`)

| OpId | Constant | Arity | Description |
| :--- | :--- | :--- | :--- |
| `1` | `I_ADD` | 2 | $a + b$ |
| `2` | `I_SUB` | 2 | $a - b$ |
| `3` | `I_MUL` | 2 | $a \times b$ |
| `4` | `I_DIV` | 2 | $a / b$ (Returns 0 if $b = 0$) |
| `5` | `I_MOD` | 2 | $a \pmod b$ |
| `6` | `I_SHL` | 2 | Bitwise shift left: $a \ll b$ |
| `7` | `I_SHR` | 2 | Bitwise arithmetic shift right: $a \gg b$ |
| `8` | `I_USHR` | 2 | Bitwise logical shift right: $a \ggg b$ |
| `9` | `I_OR` | 2 | Bitwise OR: $a \mid b$ |
| `10` | `I_AND` | 2 | Bitwise AND: $a \ \& \ b$ |
| `11` | `I_XOR` | 2 | Bitwise XOR: $a \oplus b$ |
| `12` | `I_COPY_SIGN` | 2 | Copy sign |
| `13` | `I_MIN` | 2 | $\min(a, b)$ |
| `14` | `I_MAX` | 2 | $\max(a, b)$ |
| `15` | `I_NEG` | 1 | Negate: $-a$ |
| `16` | `I_ABS` | 1 | Absolute value: $\|a\|$ |
| `17` | `I_INCR` | 1 | Increment: $a + 1$ |
| `18` | `I_DECR` | 1 | Decrement: $a - 1$ |
| `19` | `I_NOT` | 1 | Bitwise complement: $\sim a$ |
| `20` | `I_SIGN` | 1 | Signum: $1, 0, -1$ |
| `21` | `I_CLAMP` | 3 | Clamp integer between $[min, max]$ |
| `22` | `I_IFELSE` | 3 | Conditional select: $c \ne 0 \ ? \ t : f$ |
| `23` | `I_MAD` | 3 | Integer multiply-add: $a \times b + c$ |
| `24` | `I_VAR1` | 0 | Context variable 1 |
| `25` | `I_VAR2` | 0 | Context variable 2 |

### 3.3 Conditional Branching (`ConditionalOperations`, Opcode 178)
For structural branching in operation streams, `ConditionalOperations` compares two float operands ($A, B$):
* `TYPE_EQ = 0`: $A == B$
* `TYPE_NEQ = 1`: $A \ne B$
* `TYPE_LT = 2`: $A < B$
* `TYPE_LTE = 3`: $A \le B$
* `TYPE_GT = 4`: $A > B$
* `TYPE_GTE = 5`: $A \ge B$
* `TYPE_CHANGED = 6`: Triggered when either $A$ or $B$ changes value from previous pass

---

## 4. Reactive Dependency Graph & Dirty Tracking

To avoid evaluating every expression on every frame:

```
[System Input: ANIMATION_TIME or TOUCH_X changed]
                       |
                       v
[Mark Source Variable ID as Dirty in RemoteContext]
                       |
                       v
[Propagate Dirty State to Direct Dependent Expressions]
                       |
                       v
[Topological Evaluation Pass prior to Layout & Draw Passes]
                       |
                       v
[Resolved Variable Cache Updated; Dirty Flags Cleared]
```

### 4.1 Dependency Registration
When `FloatExpression (ANIMATED_FLOAT)` is initialized:
1. The player scans `mSrcValue` for NaN-encoded IDs that are neither math operators nor data variables.
2. The expression registers as a listener with `context.listensTo(id, this)`.
3. If an input variable updates, `updateVariables(context)` evaluates the RPN expression and updates the internal cache.

---

## 5. Predefined Global System Variables

The following system variables are pre-allocated in `RemoteContext` and automatically updated by the runtime:

| Variable ID | Constant | Data Type | Description |
| :--- | :--- | :--- | :--- |
| `1` | `ID_CONTINUOUS_SEC` | `FLOAT` | Seconds from midnight looping every hour $[0.0, 3600.0)$ |
| `2` | `ID_TIME_IN_SEC` | `FLOAT` | Quantized seconds from midnight $[0, 3599]$ |
| `3` | `ID_TIME_IN_MIN` | `FLOAT` | Quantized minutes from midnight $[0, 1439]$ |
| `4` | `ID_TIME_IN_HR` | `FLOAT` | Quantized hours from midnight $[0, 23]$ |
| `5` | `ID_WINDOW_WIDTH` | `FLOAT` | Surface width in pixels |
| `6` | `ID_WINDOW_HEIGHT` | `FLOAT` | Surface height in pixels |
| `7` | `ID_COMPONENT_WIDTH` | `FLOAT` | Local component width in pixels |
| `8` | `ID_COMPONENT_HEIGHT` | `FLOAT` | Local component height in pixels |
| `9` | `ID_CALENDAR_MONTH` | `FLOAT` | Month of year $[1..12]$ ($1 = \text{January}$) |
| `10` | `ID_OFFSET_TO_UTC` | `FLOAT` | Timezone offset from UTC in seconds |
| `11` | `ID_WEEK_DAY` | `FLOAT` | Day of week $[1..7]$ ($1 = \text{Monday}$) |
| `12` | `ID_DAY_OF_MONTH` | `FLOAT` | Day of month $[1..31]$ |
| `13` | `ID_TOUCH_POS_X` | `FLOAT` | Current touch pointer X coordinate |
| `14` | `ID_TOUCH_POS_Y` | `FLOAT` | Current touch pointer Y coordinate |
| `15` | `ID_TOUCH_VEL_X` | `FLOAT` | Pointer velocity in X direction |
| `16` | `ID_TOUCH_VEL_Y` | `FLOAT` | Pointer velocity in Y direction |
| `17` | `ID_ACCELERATION_X` | `FLOAT` | Acceleration sensor X ($m/s^2$) |
| `18` | `ID_ACCELERATION_Y` | `FLOAT` | Acceleration sensor Y ($m/s^2$) |
| `19` | `ID_ACCELERATION_Z` | `FLOAT` | Acceleration sensor Z ($m/s^2$) |
| `20` | `ID_GYRO_ROT_X` | `FLOAT` | Gyroscope rotation rate X (rad/s) |
| `21` | `ID_GYRO_ROT_Y` | `FLOAT` | Gyroscope rotation rate Y (rad/s) |
| `22` | `ID_GYRO_ROT_Z` | `FLOAT` | Gyroscope rotation rate Z (rad/s) |
| `23` | `ID_MAGNETIC_X` | `FLOAT` | Ambient magnetic field X ($\mu T$) |
| `24` | `ID_MAGNETIC_Y` | `FLOAT` | Ambient magnetic field Y ($\mu T$) |
| `25` | `ID_MAGNETIC_Z` | `FLOAT` | Ambient magnetic field Z ($\mu T$) |
| `26` | `ID_LIGHT` | `FLOAT` | Ambient light sensor level (SI lux) |
| `27` | `ID_DENSITY` | `FLOAT` | Screen display density factor |
| `28` | `ID_API_LEVEL` | `FLOAT` | Supported document API level |
| `29` | `ID_TOUCH_EVENT_TIME` | `FLOAT` | Timestamp of last touch event |
| `30` | `ID_ANIMATION_TIME` | `FLOAT` | Monotonic animation elapsed time in seconds |
| `31` | `ID_ANIMATION_DELTA_TIME` | `FLOAT` | Delta time since preceding frame ($\Delta t$) |
| `32` | `ID_EPOCH_SECOND` | `INT` | Unix epoch time in seconds |
| `33` | `ID_FONT_SIZE` | `FLOAT` | Default system font size |
| `34` | `ID_DAY_OF_YEAR` | `FLOAT` | Day of year $[1..366]$ |
| `35` | `ID_YEAR` | `FLOAT` | Current calendar year (e.g. $2026.0$) |

---

## 6. Conformance Requirements

1. **Deterministic Division by Zero**: Executing `DIV` with divisor $0.0$ must return $0.0$ and MUST NOT throw an exception.
2. **Stack Depth Clamping**: Expressions exceeding stack capacity must clamp or terminate cleanly without memory corruption.
3. **Register Isolation**: Scratch registers $R_0 \dots R_3$ are private to each expression evaluation invocation.
4. **NaN Safety**: Mathematical operators encountering unexpected input NaNs must treat them as $0.0$ in visual calculations.
