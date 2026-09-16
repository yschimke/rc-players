# RemoteCompose Float Expression Engine in Lean 4

A formal, executable, and machine-checked specification of the RemoteCompose Float Expression Engine (`Operations.ANIMATED_FLOAT = 81`), mirroring the reference implementation in:
* `androidx/compose/remote/core/operations/utilities/AnimatedFloatExpression.java`
* `androidx/compose/remote/core/operations/FloatExpression.java`
* `androidx/compose/remote/core/operations/utilities/NanMap.java`
* `androidx/compose/remote/core/operations/Utils.java`
* `specification/AREA-04-EXPRESSION-ENGINE.md`

## Architecture Overview

RemoteCompose expressions are evaluated using a stack-based **Reverse Polish Notation (RPN)** machine.
Expressions enable the player to compute mathematical functions, handle boolean logic, inspect data collections, and drive responsive animations at 60/120 FPS locally without roundtrips to the host.

### Pipeline

1. **Tokens and Wire Format**:
   - Literal float constants are pushed directly onto the stack.
   - Dynamic system and user variables are encoded with NaN tags (`OFFSET = 0x310000`, `TYPE_SYSTEM = 0`, `TYPE_VARIABLE = 1`, `TYPE_ARRAY = 2`, `TYPE_OPERATION = 3`).
   - Bit-exact serialization/deserialization to IEEE-754 32-bit single precision floats (`Token.ofRawBits` and `Token.toRawBits`).
2. **Stack Machine**:
   - 65+ operators across arithmetic, trigonometry, conditional logic (`IFELSE`, `CLAMP`, `MAD`), fast geometry (`HYPOT`, `SQUARE_SUM`, `STEP`, `LERP`, `SMOOTH_STEP`, `PINGPONG`), bezier easing (`CUBIC`), monotonic Hermite splines (`A_SPLINE`, `A_SPLINE_LOOP`), pseudo-random generation (Java LCG compliant `RAND`, `RAND_SEED`, `RAND_IN_RANGE`), and deterministic bit-scrambled noise (`NOISE_FROM`).
   - Scratch registers $R_0 \dots R_3$ private to each evaluation invocation.
   - Context variables `VAR1`, `VAR2`, `VAR3`.
3. **Reactive State**:
   - `FloatExpression` tracks variable dependencies (`dependencies`).
   - Reactive dirty tracking (`updateVariables`) detects when inputs produce an altered output value, marking the component dirty for repainting.

## Module Structure

| File | Purpose |
| :--- | :--- |
| [`Basic.lean`](Basic.lean) | System constants, predefined variable IDs (`ID_WINDOW_WIDTH`, `ID_ANIMATION_TIME`, etc.), `Op` enum (with opcodes, arities, names), `Token` definition, and NaN wire codec. |
| [`Easing.lean`](Easing.lean) | Cubic bezier easing curve solver (bisection inversion) and monotonic cubic Hermite spline interpolation. |
| [`Context.lean`](Context.lean) | `RemoteContext` (variable environment), `CollectionsAccess` (float array access), and `Registers` ($R_0 \dots R_3$). |
| [`Eval.lean`](Eval.lean) | Core RPN stack machine evaluator, LCG pseudo-random generator, deterministic noise, safe division/modulo by zero. |
| [`FloatExpression.lean`](FloatExpression.lean) | Component operation, dependency extraction, variable resolution, and reactive dirty detection. |
| [`Parser.lean`](Parser.lean) | Shunting-yard infix and postfix expression parser for human-readable testing (`"(3+5)*(2-8)"` $\to$ tokens). |
| [`Theorems.lean`](Theorems.lean) | Machine-checked formal proofs of wire format invertibility, operator arity bounds, division by zero safety, register preservation, and operator algebraic equivalences (`SQUARE ≡ DUP, MUL`, etc.). |
| [`Conformance.lean`](Conformance.lean) | Conformance test suite reproducing tests from Android's `AnimatedFloatExpressionTest.java`. Evaluated at compile time with `#guard`. |

## Building & Verification

From `frameworks/support/compose/remote/specification/conformance/lean`:

```bash
lake build RemoteComposeExpression
```

To build both the layout and expression specifications:

```bash
lake build
```
