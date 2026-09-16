# AREA-14: Platform Services & Hardware Integration Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose bridges declarative UI with physical device hardware. 

The Platform Services Subsystem handles sensory haptic pulses (`HapticFeedback`, `HapticSupport`), audio playback and procedural tone synthesis (`DATA_SOUND`, `SOUND_EXPRESSION`, `PLAY_SOUND`), continuous device telemetry from onboard sensors (`SensorSupport`), and system theme adaptation (`Theme`, `ColorTheme`).

---

## 2. Haptic Feedback Subsystem (`HapticSupport`)

Interactions can trigger tactile physical confirmation via **`HAPTIC_FEEDBACK` (Opcode 177)**, invoking `context.hapticEffect(mHapticFeedbackType)`.

### 2.1 Haptic Pattern Constants (`HapticSupport.java`)

| Constant Index | Android Platform Constant | Description |
| :--- | :--- | :--- |
| `0` | `NO_HAPTICS` | No vibration executed. |
| `1` | `LONG_PRESS` | Long-press tactile confirmation. |
| `2` | `VIRTUAL_KEY` | Virtual software keypress feedback. |
| `3` | `KEYBOARD_TAP` | Standard keyboard tap. |
| `4` | `CLOCK_TICK` | Mechanical clock tick sensation. |
| `5` | `CONTEXT_CLICK` | Contextual click feedback. |
| `6` | `KEYBOARD_PRESS` | Physical keypress downward travel. |
| `7` | `KEYBOARD_RELEASE` | Key release upward rebound. |
| `8` | `VIRTUAL_KEY_RELEASE` | Virtual key release pulse. |
| `9` | `TEXT_HANDLE_MOVE` | Text cursor / handle movement drag detent. |
| `10` | `GESTURE_START` | Initial recognition of a gesture. |
| `11` | `GESTURE_END` | Completion of a gesture. |
| `12` | `CONFIRM` | Success / affirmation confirmation pulse. |
| `13` | `REJECT` | Failure / rejection buzz. |
| `14` | `TOGGLE_ON` | Switch toggled to ON state. |
| `15` | `TOGGLE_OFF` | Switch toggled to OFF state. |
| `16` | `GESTURE_THRESHOLD_ACTIVATE` | Crossing drag activation threshold. |
| `17` | `GESTURE_THRESHOLD_DEACTIVATE` | Falling back below threshold. |
| `18` | `DRAG_START` | Drag initiation tactile pickup. |
| `19` | `SEGMENT_TICK` | Rotary or segmented slider tick. |
| `20` | `SEGMENT_FREQUENT_TICK` | Dense rapid tick sequence. |

* **Gating**: Subject to `Limits.ENABLE_HAPTIC_FEEDBACK = true`.

---

## 3. Audio Playback & Procedural Tone Synthesis

RemoteCompose provides both sampled audio playback and real-time procedural waveform synthesis.

### 3.1 Sampled Audio Resource (`DATA_SOUND`, Opcode 169)
* Encapsulates raw encoded audio payloads (e.g., WAV, OGG, MP3) registered under an asset ID.

### 3.2 Procedural Tone Synthesizer (`SOUND_EXPRESSION`, Opcode 206)
Defines a procedural tone synthesis recipe stored as a resource:
* **Fields**:
  * `id: INT`: Unique sound resource identifier.
  * `leftVolume: FLOAT`: Channel volume ($[0.0, 1.0]$).
  * `rightVolume: FLOAT`: Channel volume ($[0.0, 1.0]$).
  * `rate: FLOAT`: Playback pitch / speed multiplier.
  * `params: FLOAT[]`: Parameter array (up to `MAX_PARAMS = 64`).
* **Synthesis Types (`params[0]`)**:
  * `TYPE_TONE = 10`: Standard oscillator tone.
    * `params[1]`: `frequency` (Hz).
    * `params[2]`: `durationSeconds` (seconds).
    * `params[3]`: Waveform type:
      * `WAVEFORM_SINE = 0.0f`: Smooth sine wave.
      * `WAVEFORM_SQUARE = 1.0f`: Rich, buzzy square wave.
      * `WAVEFORM_SAWTOOTH = 2.0f`: Harsh sawtooth wave.
      * `WAVEFORM_TRIANGLE = 3.0f`: Mellow triangle wave.

### 3.3 Audio Playback (`PLAY_SOUND`, Opcode 141)
* Triggers playback of a referenced `SoundExpression` or `SoundData` resource (`mSoundExpressionId`).
* Valid within action blocks, click handlers, touch gestures, or layout transitions.

---

## 4. Hardware Sensor Telemetry (`SensorSupport.java`)

The player integrates with host device sensors to populate dynamic system variables in `RemoteContext`:

```
+-------------------------------------------------------------------------------+
| Device Hardware Sensors                                                       |
| (Accelerometer, Gyroscope, Magnetometer, Ambient Light Sensor)                |
+-------------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------------+
| SensorSupport (RemoteComposePlayer)                                           |
| - Inspects document variable listeners during setup                           |
| - Registers OS SensorEventListener ONLY for active sensors                    |
| - Populates RemoteContext variables:                                          |
|     ACCELERATION_X (17), ACCELERATION_Y (18), ACCELERATION_Z (19)             |
|     GYRO_ROT_X (20), GYRO_ROT_Y (21), GYRO_ROT_Z (22)                         |
|     MAGNETIC_X (23), MAGNETIC_Y (24), MAGNETIC_Z (25)                         |
|     LIGHT (26)                                                                |
| - Battery Protection: Unregisters listeners onDetachedFromWindow / pause      |
+-------------------------------------------------------------------------------+
```

### 4.1 Sensor Variable Mapping (`RemoteContext.java`)
| ID | Constant | Unit | Description |
| :--- | :--- | :--- | :--- |
| `17` | `ID_ACCELERATION_X` | $\text{m/s}^2$ | Accelerometer X-axis |
| `18` | `ID_ACCELERATION_Y` | $\text{m/s}^2$ | Accelerometer Y-axis |
| `19` | `ID_ACCELERATION_Z` | $\text{m/s}^2$ | Accelerometer Z-axis ($\approx +9.81\text{ m/s}^2$ flat) |
| `20` | `ID_GYRO_ROT_X` | $\text{rad/s}$ | Gyroscope angular velocity around X |
| `21` | `ID_GYRO_ROT_Y` | $\text{rad/s}$ | Gyroscope angular velocity around Y |
| `22` | `ID_GYRO_ROT_Z` | $\text{rad/s}$ | Gyroscope angular velocity around Z |
| `23` | `ID_MAGNETIC_X` | $\mu\text{T}$ | Geomagnetic field strength along X |
| `24` | `ID_MAGNETIC_Y` | $\mu\text{T}$ | Geomagnetic field strength along Y |
| `25` | `ID_MAGNETIC_Z` | $\mu\text{T}$ | Geomagnetic field strength along Z |
| `26` | `ID_LIGHT` | Lux | Ambient light illuminance |

---

## 5. Theming & Dynamic System Palettes

### 5.1 Theme Mode Tagging (`THEME`, Opcode 63)
Governed by `Theme.java` to tag subsequent operations for selective execution during playback:

| Value | Constant | Behavior |
| :--- | :--- | :--- |
| `0` | `Theme.SYSTEM` | Default: Player follows the active host system theme (light vs. dark). |
| `-1` | `Theme.UNSPECIFIED` | Theme filtering disabled; all instructions are executed regardless of active theme. |
| `-2` | `Theme.DARK` | Instructions in this section execute only when dark theme is active. |
| `-3` | `Theme.LIGHT` | Instructions in this section execute only when light theme is active. |

### 5.2 Dynamic Color Mapping (`ColorTheme.java`)
* Allows documents to bind color definitions to system theme roles (primary, surface, on-surface, error, etc.).
* When host configuration changes, the player automatically triggers variable re-evaluation and repaints the document.

---

## 6. Conformance Requirements

1. **Sensor Cleanup**: Sensor listeners must be unregistered when the player is detached from its window or paused.
2. **Audio Clamping**: Procedural audio volume must be strictly clamped within $[0.0, 1.0]$.
3. **Theme Filtering Invariant**: Operations scoped under `Theme.DARK` (`-2`) must be completely skipped during the paint pass when `Theme.LIGHT` (`-3`) is enforced.
