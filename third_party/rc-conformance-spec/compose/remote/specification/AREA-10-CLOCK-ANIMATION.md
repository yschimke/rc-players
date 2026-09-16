# AREA-10: Clock, Time & Animation Engine Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose is designed for high-efficiency, continuous dynamic playback. 

To power fluid procedural animations while respecting battery and thermal envelopes, the player features a synchronized timekeeping subsystem (`TimeVariables`, `RemoteClock`), layout and visibility transition specifications (`AnimationSpec`), dynamic frame rate budgeting with sliding-window average FPS limits (`Limiter`), and interaction-driven frame rate boosting (`touchBoost`).

---

## 2. Clocks & Time Representation

The player maintains synchronized clock sources mapped to global system variables in `RemoteContext`:

```
+-------------------------------------------------------------------------------+
| Player vsync Tick                                                             |
+-------------------------------------------------------------------------------+
       |
       ├── 1. Monotonic Render Clock (RemoteClock)                              |
       |      - Updates ID_ANIMATION_TIME (ID 30) in continuous seconds         |
       |      - Updates ID_ANIMATION_DELTA_TIME (ID 31) (frame delta dt)        |
       |      - Monotonically increasing; immune to wall-clock time jumps       |
       |                                                                        |
       └── 2. Calendar / Wall Clock (CalendarSystemClock)                       |
              - Populates TimeVariables on RemoteContext:                       |
              - ID_CONTINUOUS_SEC (ID 1) [0.0, 3600.0)                          |
              - ID_TIME_IN_SEC (ID 2), ID_TIME_IN_MIN (ID 3), ID_TIME_IN_HR (4) |
              - ID_EPOCH_SECOND (ID 32), Calendar Month, Day, Year              |
```

### 2.1 Time System Variables (`RemoteContext.java`)

| Variable ID | Constant | Type | Unit / Range | Description |
| :--- | :--- | :--- | :--- | :--- |
| `1` | `ID_CONTINUOUS_SEC` | Float | Seconds $[0.0, 3600.0)$ | Continuous fractional seconds within current hour |
| `2` | `ID_TIME_IN_SEC` | Float | Seconds $[0.0, 60.0)$ | Fractional seconds of the current minute |
| `3` | `ID_TIME_IN_MIN` | Float | Minutes $[0.0, 60.0)$ | Fractional minutes of the current hour |
| `4` | `ID_TIME_IN_HR` | Float | Hours $[0.0, 24.0)$ | Hour of the day |
| `9` | `ID_CALENDAR_MONTH` | Float | Month $[1.0, 12.0]$ | Month of year ($1.0 = \text{January}$) |
| `10` | `ID_OFFSET_TO_UTC` | Float | Seconds | Timezone offset from UTC |
| `11` | `ID_WEEK_DAY` | Float | Day $[1.0, 7.0]$ | Day of week ($1.0 = \text{Monday}$) |
| `12` | `ID_DAY_OF_MONTH` | Float | Day $[1.0, 31.0]$ | Day of month |
| `28` | `ID_API_LEVEL` | Float | Version number | Document API level + build |
| `30` | `ID_ANIMATION_TIME` | Float | Seconds | Monotonic render clock elapsed time |
| `31` | `ID_ANIMATION_DELTA_TIME` | Float | Seconds | Delta time between successive frames ($\Delta t$) |
| `32` | `ID_EPOCH_SECOND` | Integer | Seconds | Unix epoch timestamp |
| `34` | `ID_DAY_OF_YEAR` | Float | Day $[1.0, 366.0]$ | Day of year |
| `35` | `ID_YEAR` | Float | Year | Calendar year (e.g. $2026.0$) |

### 2.2 Time Invariants
* **Monotonicity**: `ID_ANIMATION_TIME` must never jump backwards during active playback, even if device system time is adjusted by network sync.
* **Delta Clamping**: Delta time between successive frames ($\Delta t$) must be clamped to a maximum ceiling (default: **$0.1$ seconds**) to prevent physics explosion when resuming from sleep or backgrounding.

---

## 3. Animation Specifications (`AnimationSpec`, Opcode 14)

Component layout animations and interpolated state transitions are governed by `AnimationSpec`.

### 3.1 Specification Parameters
* `animationId: INT`: Unique spec identifier (`-1` for default, `0` for disabled).
* `motionDuration: FLOAT`: Transition duration for position and size changes in milliseconds (default: `300 ms`).
* `motionEasingType: INT`: Mathematical easing curve for spatial movement (default: `GeneralEasing.CUBIC_STANDARD = 1`).
* `visibilityDuration: FLOAT`: Duration for enter/exit visibility transitions in milliseconds (default: `300 ms`).
* `visibilityEasingType: INT`: Mathematical easing curve for alpha/visibility (default: `GeneralEasing.CUBIC_STANDARD = 1`).
* `enterAnimation: INT`: Ordinal of `AnimationSpec.ANIMATION` enum.
* `exitAnimation: INT`: Ordinal of `AnimationSpec.ANIMATION` enum.

### 3.2 Transition Types (`AnimationSpec.ANIMATION`)
| Enum Name | Ordinal | Behavior |
| :--- | :--- | :--- |
| `FADE_IN` | `0` | Alpha fades in from 0.0 to 1.0 |
| `FADE_OUT` | `1` | Alpha fades out from 1.0 to 0.0 |
| `SLIDE_LEFT` | `2` | Slides horizontally from/to the left edge |
| `SLIDE_RIGHT` | `3` | Slides horizontally from/to the right edge |
| `SLIDE_TOP` | `4` | Slides vertically from/to the top edge |
| `SLIDE_BOTTOM` | `5` | Slides vertically from/to the bottom edge |
| `ROTATE` | `6` | Rotational angular transition |
| `PARTICLE` | `7` | Particle system disintegration / formation transition |

### 3.3 Easing Curves Catalog (`Easing.java`)

| Easing Type | Value | Constant | Description / Formula |
| :--- | :--- | :--- | :--- |
| `CUBIC_STANDARD` | `1` | `Easing.CUBIC_STANDARD` | Material standard curve: $\text{CubicBezier}(0.4, 0.0, 0.2, 1.0)$ |
| `CUBIC_ACCELERATE`| `2` | `Easing.CUBIC_ACCELERATE` | Acceleration curve: $\text{CubicBezier}(0.4, 0.0, 1.0, 1.0)$ |
| `CUBIC_DECELERATE`| `3` | `Easing.CUBIC_DECELERATE` | Deceleration curve: $\text{CubicBezier}(0.0, 0.0, 0.2, 1.0)$ |
| `CUBIC_LINEAR` | `4` | `Easing.CUBIC_LINEAR` | Linear interpolation: $f(t) = t$ |
| `CUBIC_ANTICIPATE`| `5` | `Easing.CUBIC_ANTICIPATE` | Anticipate: pulls backward slightly before accelerating forward |
| `CUBIC_OVERSHOOT` | `6` | `Easing.CUBIC_OVERSHOOT` | Overshoot: flings past destination before springing back |
| `CUBIC_CUSTOM` | `11` | `Easing.CUBIC_CUSTOM` | Parametric cubic curve with explicit control points $(x_1, y_1, x_2, y_2)$ |
| `SPLINE_CUSTOM` | `12` | `Easing.SPLINE_CUSTOM` | Monotonic spline curve with arbitrary knot points |
| `EASE_OUT_BOUNCE`| `13` | `Easing.EASE_OUT_BOUNCE` | Inelastic surface bounce simulation |
| `EASE_OUT_ELASTIC`| `14`| `Easing.EASE_OUT_ELASTIC` | Damped harmonic spring oscillation |

---

## 4. Frame Rate Budgeting & Thermal Throttling (`Limiter.java`)

To prevent battery depletion when displaying passive ambient content (e.g., watch faces, widgets, ambient displays):

### 4.1 Frame Budgeting Parameters (`Limits.java`)
1. **Default Maximum FPS (`DEFAULT_MAX_FPS = 60`)**: Standard upper bound for active playback.
2. **Absolute Maximum FPS (`MAX_FPS = 120`)**: Hardware display ceiling for high-refresh screens.
3. **Sustained Average FPS (`DEFAULT_MAX_AVG_FPS = 10`)**: Target average frame rate sustained over the rolling window.
4. **Rolling Average Window (`DEFAULT_WINDOW_SEC = 10`)**: Duration of the sliding monitoring window in seconds.

### 4.2 Ring-Buffer Limiting Algorithm (`Limiter.java`)
* The `Limiter` partitions the time window into a fixed array of 64 buckets (`BUCKETS = 64`, power of two with bitmask `MASK = 63`).
* Frame limit ceiling is computed as:
  $$\text{mFrameLimit} = \text{mMaxAvgFps} \times \text{mWindowSec}$$
* As time advances, aged buckets roll off in $O(1)$ amortized time.
* If the total frames rendered within the sliding span reach `mFrameLimit`, subsequent frame dispatches are paced with `mAvgIntervalNs` delay until older buckets expire.

### 4.3 Interaction Frame Rate Boosting (`touchBoost`)
* Whenever a pointer `DOWN` or `MOVE` event is received, the player calls `touchBoost()`.
* **Behavior**: Clears all 64 buckets and resets the active frame counter to zero (`Arrays.fill(mBuckets, 0); mCount = 0;`).
* **Result**: The rolling average instantaneously drops to zero, opening the limiter gate so the player renders at the full instantaneous `maxFps` (up to 60 or 120 FPS). Because the window refills from empty, the player enjoys smooth, unthrottled frames for the entire interaction duration plus approximately one full window period (`DEFAULT_WINDOW_SEC = 10` seconds) after the user releases.

---

## 5. Conformance Requirements

1. **Monotonic Progression**: Evaluating `ID_ANIMATION_TIME` across two successive render frames $F_k, F_{k+1}$ must satisfy $T_{k+1} \ge T_k$.
2. **Touch Boost Responsiveness**: Receiving a touch event while the player is throttled must immediately reset the frame limiter and allow the next frame dispatch at $\le 1 / \text{maxFps}$ interval.
3. **Easing Boundary Guarantees**: Any conforming easing function must strictly satisfy $f(0.0) = 0.0$ and $f(1.0) = 1.0$.
