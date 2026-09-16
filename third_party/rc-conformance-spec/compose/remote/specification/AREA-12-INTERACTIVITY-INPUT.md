# AREA-12: Interactivity, Input & Gestures Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose supports rich, physics-based, interactive touch and pointer experiences. 

The Interactivity Subsystem handles hierarchical hit-testing in transformed coordinate spaces, gesture recognition (taps, multi-clicks, long-presses, phase transitions), nested scroll delegation with overscroll physics (`ScrollModifierOperation`, `ScrollingEdgeEffect`), and client-side physics binding via `TouchExpression`.

---

## 2. Hit-Testing & Event Routing Architecture

Touch events enter the player through standard OS pointer streams (`DOWN`, `MOVE`, `UP`, `CANCEL`, `HOVER`).

```
[OS MotionEvent: (X_viewport, Y_viewport)]
                   |
                   v
[Update Global System Variables in RemoteContext:
   ID_TOUCH_POS_X (13), ID_TOUCH_POS_Y (14),
   ID_TOUCH_VEL_X (15), ID_TOUCH_VEL_Y (16),
   ID_TOUCH_EVENT_TIME (29)]
[Trigger Player touchBoost()]
                   |
                   v
[CoreDocument Root Dispatch]
    └── Recursive Backward Search (Component.dispatchTouchEvent):
          ├── Invert transformation matrix & translate by -mX, -mY
          ├── Check bounds containment [0 <= x <= mWidth, 0 <= y <= mHeight]
          ├── If children present:
          │     Sort by Z-Index (highest first); test in reverse child order
          └── If child handles event:
                Consume event & stop propagation
```

### 2.1 Coordinate Inversion Invariant
When testing a point $(X, Y)$ against a component transformed by a matrix $M$:
1. The player calculates the inverse matrix $M^{-1}$.
2. The point is transformed into local component space:
   $$\begin{bmatrix} x_{\text{local}} \\ y_{\text{local}} \\ 1 \end{bmatrix} = M^{-1} \begin{bmatrix} X \\ Y \\ 1 \end{bmatrix}$$
3. Hit-testing is evaluated strictly against the untransformed rectangular bounds $[0, W] \times [0, H]$.

---

## 3. Gestures & Click Modifiers

Components declare interactivity through modifier operations:

### 3.1 Click Modifiers
* **`ClickModifierOperation (Opcode 59)`**:
  * Implements `Container`, `ModifierOperation`, `ClickHandler`, `DecoratorComponent`, `AccessibleComponent`.
  * Executes nested action operations upon click release within touch boundaries.
  * Contains built-in visual ripple animation (`mAnimateRippleDuration = 1000 ms`).
  * Hit target expansion can be adjusted via `ClickArea (Opcode 64)`.
* **`MultiClickModifier (Opcode 83)`**:
  * Tracks tap sequences within a sliding window `timeoutMs` (e.g., detecting double or triple taps).

### 3.2 Discrete Touch Phase Modifiers
* **`MODIFIER_TOUCH_DOWN (Opcode 219)`**: Executes immediate nested actions upon pointer contact (`onTouchDown`).
* **`MODIFIER_TOUCH_UP (Opcode 220)`**: Executes nested actions upon pointer release (`onTouchUp`).
* **`MODIFIER_TOUCH_CANCEL (Opcode 225)`**: Resets state and executes fallback actions if a gesture is intercepted by a parent scroll container (`onTouchCancel`).

---

## 4. Scrolling Architecture & Nested Scroll Delegation

RemoteCompose provides a physics-based scrolling system supporting orthogonal nesting (e.g., horizontal carousels nested within a vertically scrolling column).

```
[Touch Drag Delta: dy]
           |
           v
[Inner Child Component (ScrollModifierOperation)]
    ├── Can child scroll in this direction?
    │     ├── YES: Consume delta -> Update scrollOffset -> dy_remaining = 0
    │     └── NO / At boundary: Pass unconsumed dy to Parent ScrollDelegate
    └── If child reached boundary: Trigger ScrollingEdgeEffect (overscroll bounce)
```

### 4.1 ScrollModifierOperation (`Opcode 226`)
* Implements `TouchHandler`, `ScrollDelegate`, `DecoratorComponent`, `ScrollableComponent`, `VariableSupport`.
* Manages `mScrollX`, `mScrollY`, `mMaxScrollX`, `mMaxScrollY`, content dimensions, and clipping.
* Can contain an embedded `TouchExpression` child operation to drive touch physics, inertial fling velocity, and snap notches.

### 4.2 Overscroll Physics & Edge Effects (`ScrollingEdgeEffect`)
* Manages edge effect states (`mEdgeEffectA`, `mEdgeEffectB`) at opposing ends of the scroll axis.
* When dragged beyond boundary limits, the delegate applies rubber-band resistance:
  $$\text{displacement} = \text{delta} \times \left(1.0 - \frac{\text{overscroll}}{\text{maxOverscroll}}\right)$$
* Upon release, an exponential spring decay restores offset to the boundary edge.
* Conforming players render pre-draw and post-draw visual edge glows or stretch shaders.

---

## 5. Dynamic Touch Physics (`TouchExpression`, Opcode 157)

`TouchExpression` allows direct client-side binding of finger drag gestures to dynamic float variables without host round-trips.

### 5.1 Wire Format & Parameters
* `id: INT`: Target float variable ID updated continuously by touch interactions.
* `exp: FLOAT[]`: RPN expression mapping raw pointer coordinates (`TOUCH_POS_X`, `TOUCH_POS_Y`) to variable increments.
* `defValue: FLOAT`: Default/initial value.
* `min: FLOAT, max: FLOAT`: Clamp boundaries for the output value.
* `touchEffects: INT`: Configuration bitmask (e.g., wrap mode).
* `velocityId: INT`: Optional variable receiving instantaneous velocity.
* `stopMode: INT`: Behavior applied when the pointer is released (`onTouchUp`).
* `stopSpec: FLOAT[]`: Array defining notch positions/intervals (up to `Limits.MAX_TOUCH_STOPS = 200`).
* `easingSpec: FLOAT[4]`: Dynamics configuration (`[maxTime, maxAcceleration, maxVelocity, unused]`).

### 5.2 Stop Modes (`TouchExpression`)
| Stop Mode | Value | Behavior |
| :--- | :--- | :--- |
| `STOP_GENTLY` | `0` | Natural inertial deceleration using `VelocityEasing`. |
| `STOP_INSTANTLY`| `1` | Motion freezes immediately at touch-up coordinate. |
| `STOP_ENDS` | `2` | Springs directly to the nearest boundary edge (minimum or maximum). |
| `STOP_NOTCHES_EVEN` | `3` | Snaps smoothly to evenly spaced notch intervals. |
| `STOP_NOTCHES_PERCENTS` | `4` | Snaps to discrete notches defined as percentages of the range. |
| `STOP_NOTCHES_ABSOLUTE` | `5` | Snaps to discrete notches defined as absolute coordinates. |
| `STOP_ABSOLUTE_POS` | `6` | Jumps directly to the absolute touch-down position. |
| `STOP_NOTCHES_SINGLE_EVEN` | `7` | Snaps to the nearest adjacent notch, restricting travel to at most one notch per swipe. |

---

## 6. Conformance Requirements

1. **Z-Index Hit Priority**: When two overlapping siblings are hit-tested, the child with higher `ZIndex` must receive the event first, regardless of declaration order.
2. **Reverse Modifier Traversal**: Modifier click handlers must be evaluated in reverse declaration order, ensuring inner padding/margins correctly offset the touch target.
3. **Cancellation Safety**: When a parent container intercepts a gesture for scrolling, it must issue a `TouchCancel` to any descendant that previously received `TouchDown`.
4. **Touch Stop Limits**: Conforming players must reject or truncate `stopSpec` arrays exceeding `Limits.MAX_TOUCH_STOPS` (200).
