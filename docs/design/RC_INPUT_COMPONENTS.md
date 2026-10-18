# Portable editable inputs, version 1

Inputs use `LAYOUT_CUSTOM` (93), typed custom properties and the existing `TEXT_RETURN` channel.
There is no new wire opcode. `rc-player-edit` rewrites documents; it is not an input-event API.
Opcode support alone cannot establish input capability: the custom config must have a registered
renderer, and the host must separately opt into editing. Registration does not grant service access.

| Config | Control |
| --- | --- |
| `rc:input/text:v1` | Plain text, single line or multiline |
| `rc:input/choice:v1` | A single value from the declared options |

| Property | Wire type | Meaning |
| --- | --- | --- |
| 1 VALUE | STRING_PROP | Current document text |
| 2 VALUE_RETURN | TEXT_RETURN | Declared target text id, normally the same as VALUE |
| 3 KEY | STRING_PROP | Stable key within one card, independent of changing text/component ids |
| 4 LABEL | STRING_PROP | Accessible field label |
| 5 MULTILINE | INT_PROP | 0 single line; 1 multiline |
| 6 OPTIONS | STRING_PROP | Newline-separated unique nonempty choice values; no embedded newlines |
| 7 SUBMIT | STRING_PROP | Opaque host action descriptor; empty means no submit button |

Numbers, dates/times, secrets/passwords, rich text, multi-select and arbitrary entity autocomplete
are outside this version. A choice entity supplies actual options; it must not be presented as a
free-text field. Host/domain validation remains mandatory before dispatching actions.

Edits first write the document's declared return channel, then notify the host. An undeclared or
wrongly typed return target makes a field read-only. The host retains one `RcInputDrafts` (Swift:
`NativeSwiftInputDrafts`; TS: `Map<string,string>`) per card across document replacements. Incoming
initial values refresh untouched fields; edits win until explicit reset. Drafts are in memory only.
Do not share a draft store across unrelated cards or accounts. Reset/remove it on config/account
changes. A submit carries a snapshot; subsequent typing cannot mutate an in-flight payload.

Single-line Send/Enter submits, Next/Tab follows platform focus handling, and multiline Enter inserts
a newline. Browser IME composition suppresses Enter submission. CMP retains `TextFieldValue` and
native controls avoid replacing unchanged or marked text so ordinary live updates preserve focus,
selection and composition. Explicit Send remains available for multiline fields.

## Host coverage

| Player/host | Opt-in | Unsupported/read-only behavior |
| --- | --- | --- |
| CMP Android, desktop, Wasm, CMP Apple | `rcInputComponents(drafts, editable = true, …)` and `availableCustomComponents = registry.names` in the support report | Register with `editable = false` to show value + “Open in app to edit” |
| CMP UIKit controller / Swift overlay | Controller `configureInputs` before attachment | Built-in read-only input registry |
| CMP Wasm iframe | `editableInputs: true`, `onInput` on embed factory (or explicit `?editableInputs=1`) | Read-only registry by default; `window.rcPlayerInputCapabilities` reports configs and editing policy |
| Swift UIKit | `registry.registerInputs(drafts:editable:onChange:onSubmit:)` | Same registry with editing false |
| Swift AppKit | `NativeSwiftInputConfiguration` passed to `makeView` / `openNative` | Built-in read-only views |
| TS canvas browser | `player.configureInputs({editable:true,drafts,onChange,onSubmit})` | Read-only DOM renderers by default; `inputCapabilities()` reports configs and editing policy |
| Wear foreground app | Same CMP controls; authenticated phone action bridge in HA consumer | Demo/offline/session failure retains drafts and reports inability to send |
| Wear widgets / launcher widgets | No editing capability | Consumer authors a view/open-app alternative at widget capture time |

Canvas browser controls are positioned using the actual canvas transform and CSS/backing-store
ratio. They are removed when absent from the current frame or when the player is destroyed. They
use native labels, selects, input/textarea, keyboard and focus behavior. Other custom hosts retain
their existing draw API; the extra resolved-property argument is optional.

The player never performs an HA service request. An authenticated consumer validates the configured
action and binds draft values as data, then uses its existing authenticated dispatcher. Read-only,
demo and widget policy must come from that host, not from document properties or opcode support.

## Release order

1. Land/release rc-players with the protocol constants/producer, runtime drafts, CMP renderers and
   ABI dumps, TS bundle, Swift sources/XCFramework and additive embed APIs together (expected 2.16.0).
2. Land the dependent homeassistant-remotecompose consumption change after those Maven/JS artifacts
   exist; its Kotlin and JS player versions are pinned together. For local development use the
   rc-players checkout as an included build. Its native source package consumes the additive input
   files separately; release-owned Package.swift URLs/checksums must remain generated by release CI.
3. Publish/package the HA frontend with the matching JS player. An older player reports an update
   requirement rather than receiving host-only editable controls.

Apple UIKit/AppKit compilation and device keyboard behavior require macOS/Apple hosts. Linux JVM
and browser results cannot establish those outcomes. See the paired HA PR for installation/action
and message-card evidence, and `renders/rc-input-message` for the player evidence.

The shipped macOS convenience window exposes the same explicit input callbacks through
`RcComposeInputWindow` / Swift `RemoteComposePlayerWindow.open(inputEditing:onInputChange:onInputSubmit:)`;
ordinary windows install the read-only registry. UIKit controller input configuration is applied
before attaching its view controller.
