# Input controls contract v1

Inputs use `LAYOUT_CUSTOM` (93), typed custom properties and the existing `TEXT_RETURN` channel.
There is no new wire opcode. The portable producer declares API 1.2 and the AndroidX experimental
profile required by `LAYOUT_CUSTOM`; capture writers must check their operation profile and author an
open-app alternative when it excludes that operation. `rc-player-edit` rewrites documents; it is not an input-event API.
Opcode support alone cannot establish input capability: the custom config must have a registered
renderer, and the host must separately opt into editing. Registration does not grant service access.

| Config | Control |
| --- | --- |
| `rc:input/text:v1` | Plain text, single line or multiline |
| `rc:input/choice:v1` | A single value from the declared options |
| `rc:input/toggle:v1` | Boolean checkbox/switch; exactly `true` or `false` |
| `rc:input/number:v1` | Numeric text entry with decimal keyboard and optional bounds/step |
| `rc:input/range:v1` | Bounded slider, snapped to a declared finite step grid |

| Property | Wire type | Meaning |
| --- | --- | --- |
| 1 VALUE | STRING_PROP | Current document text |
| 2 VALUE_RETURN | TEXT_RETURN | Declared target text id, normally the same as VALUE |
| 3 KEY | STRING_PROP | Stable key within one card, independent of changing text/component ids |
| 4 LABEL | STRING_PROP | Accessible field label |
| 5 MULTILINE | INT_PROP | 0 single line; 1 multiline |
| 6 OPTIONS | STRING_PROP | Newline-separated unique nonempty choice values; no embedded newlines |
| 7 SUBMIT | STRING_PROP | Opaque host action descriptor; empty means no submit button |
| 8 MIN | STRING_PROP | Optional finite JSON-number string lower bound; empty/absent = none |
| 9 MAX | STRING_PROP | Optional finite JSON-number string upper bound; empty/absent = none |
| 10 STEP | STRING_PROP | Optional positive finite JSON-number string step; origin MIN or zero |
| 11 ENABLED | INT_PROP | Absent/1 = available subject to host policy; other values disable |
| 12 SUBMIT_LABEL | STRING_PROP | Optional action button caption; absent/empty = Send |
| 13 CHROME | INT_PROP | 0 default; 1 none; absent/unknown = default |

Dates/times, secrets/passwords, rich text, multi-select and arbitrary entity autocomplete
are outside this version. Exact money/large-integer arithmetic is also outside the numeric contract;
use text plus domain validation for those values. A choice entity supplies actual options; it must not be presented as a
free-text field. Host/domain validation remains mandatory before dispatching actions.

## Normative semantics and compatibility

This document defines the cross-player wire contract. Kotlin creation APIs are public and shipped
in the existing Maven artifacts below; the Compose creation layer retains its experimental Kotlin
API opt-in. The wire identities and property meanings above are versioned independently of those
Kotlin signatures. An incompatible wire meaning requires a new config version. Players ignore
unknown optional properties; they must never guess the meaning of an unknown config/version.
Capability negotiation lists exact config strings, not just opcode 93. A host must verify every
required config for a form before enabling the whole flow, or offer an open/update-app alternative.

- KEY and LABEL must be nonblank. Keys must be unique within a form/document instance. Resource and
  component ids are document-local; keys are host draft identities, not action names or entity ids.
- VALUE is a string for **every** control; VALUE_RETURN must be a declared TEXT_RETURN property.
  Toggle events are exactly `true`/`false`. Choice events are literal declared options. Numeric/range
  values use finite IEEE-754 numbers with JSON-number spelling (no whitespace, NaN, infinity, leading
  plus, or leading zeros). Hosts explicitly convert validated values to domain types; strings are
  never evaluated as templates/code. No new float/integer return-channel interpretation is implied.
- Number fields may emit incomplete raw edits such as empty text or `-`; those are invalid values,
  retained for correction. Submit is blocked while an enabled current field is invalid. Editing,
  focus loss and validation do **not** dispatch a domain action. Only explicit submit does that.
- Numeric bounds are inclusive. A supplied STEP requires `(value - (MIN or 0)) / STEP` to be an integer
  within `1e-7 * max(1, abs(grid index))` to tolerate binary rounding. Number has no default step.
  Range requires MIN < MAX, STEP > 0, an aligned maximum, and 1–10,000 intervals. Range gestures,
  arrow keys and accessibility adjustment snap/clamp to that same grid. The authoring APIs reject
  invalid metadata/defaults; choice/number may start empty and require user completion.
- OPTIONS applies only to choice, MULTILINE only to text, numeric constraints only to number/range.
  Choice options must be unique, nonempty and newline-free; empty choice is an unselected draft.
  Current widgets never create editors. ENABLED cannot override host permission: disabled fields
  show their value/Unavailable, never edit, and are omitted from submit/validation. Host read-only
  policy overrides every field, including enabled fields.
- A successful input change writes its declared return channel **before** emitting `(key, value)`.
  Submit emits `(opaque action, immutable current enabled-field values)`, including untouched
  defaults and excluding removed fields. Values/changes alone do not authorize service access.
  Keyboard Next/Tab follows focus order; text Enter submits only when single-line with an action;
  multiline Enter inserts a newline. IME composition is preserved and never submits prematurely.

The same conformance vectors in `Tests/RcNativePlayerCoreTests/Fixtures/input-controls-v1.tsv`
run against Kotlin, Swift and TypeScript validators. `input-controls.rc` is a portable expanded form
fixture. Player tests additionally verify value returns, submit snapshots, lifecycle, read-only and
unsupported-writer behavior. Applications must still validate their domain constraints and current
write permission at dispatch time.

## Public creation API

Use the protocol artifact from any Kotlin target, without Home Assistant or Compose dependencies:

```kotlin
implementation("ee.schimke.composeai:rc-player-protocol:2.16.0")

val fields = listOf(
  RcInputs.text("message", "Message", multiline = true),
  RcInputs.choice("room", "Room", listOf("Kitchen", "Living room"), "Kitchen"),
  RcInputs.toggle("urgent", "Urgent"),
  RcInputs.number("temperature", "Temperature", 21.5, minimum = 0.0, maximum = 30.0, step = 0.5),
  RcInputs.range("brightness", "Brightness", 50.0, submit = "example:apply").copy(submitLabel = "Apply"),
)
val bytes = RcDocumentCodec.encode(rcInputDocument(fields))
// Inside another document: reserve component/text ids, append the fragment, dimensions and end.
val component = rcInputComponent(fields[2], componentId = 800, textBaseId = 900)
// Validate raw host values before binding them to domain actions.
val valid = rcInputValueIsValid(RcInput.NUMBER, "22.5", minimum = 0.0, maximum = 30.0, step = 0.5)
```

`rcInputComponent` emits its text resources and LAYOUT_CUSTOM; callers allocate ten consecutive
text ids and append modifiers/dimensions/CONTAINER_END in their surrounding tree. `rcInputDocument`
allocates all ids, enforces unique keys, and supplies recommended form dimensions. `RcInputField`
can also be constructed directly; `requireValid()` exposes the same authoring checks. Factories
return immutable fields, so `.copy(enabled = false)` defines a disabled control.

For common Compose creation, add the **published** `ee.schimke.composeai:rc-creation-bridge:2.16.0`
artifact (Android, desktop/JVM, Wasm, iOS and macOS):

```kotlin
@OptIn(ExperimentalRcCreationApi::class)
@Composable @RemoteComposable
fun RemoteMessageForm() {
  val field = RcInputs.text("message", "Message", multiline = true, submit = "example:send")
  RemoteInput(field, field.value.rs, RemoteModifier.fillMaxWidth().height(136.rdp),
    fallback = { RemoteText("Open app to edit".rs) })
}
```

`RemoteInput` binds the supplied RemoteString as both VALUE and TEXT_RETURN. Creation is write-only:
callbacks live in `rcInputComponents`/the native or JS host, rather than executing inside capture.
`canCreateRcInputs()` checks writer operation support only (an empty common-writer operation set
means unrestricted); `RemoteInput` invokes its fallback when that set excludes LAYOUT_CUSTOM.
The common writer declares the experimental profile/API required by custom components when they
are emitted. On Android, select the creation fork **or** upstream AndroidX creation, never both;
apps keeping upstream AndroidX can use the toolkit-independent protocol/fragment API and the same
property contract. The bridge and its transitive writer/creation artifacts participate in the
existing Maven publication/release set and ABI gates.

TypeScript authors use exported `InputField`/`InputKind`, `inputDocument(fields)` and
`inputValueIsValid(config, value, options, minimum, maximum, step)` from the player bundle. For
example `{kind:'toggle', key:'urgent', label:'Urgent', value:'false'}` or
`{kind:'range', key:'brightness', label:'Brightness', value:'50', minimum:0, maximum:100, step:1}`.
The JS builder validates metadata/defaults before writing the same stream as the Kotlin API.
Swift/native hosts expose `NativeSwiftInput` constants and `isValid` for the same contract.

The versions above are the expected **next release**, not artifacts already published from this PR.
Land and publish the player/creation APIs first, then update dependent applications. The paired HA
message schema continues to use the text/multiline/choice subset; the additional controls are common
creation/player capabilities, with no implicit new HA service/type conversion rules.

## Drafts and events

Edits first write the document's declared return channel, then notify the host. An undeclared or
wrongly typed return target makes a field read-only. The host retains one `RcInputDrafts` (Swift:
`NativeSwiftInputDrafts`; TS: `Map<string,string>`) per card across document replacements. Incoming
initial values refresh untouched fields; edits win until explicit reset. Drafts are in memory only.
Do not share a draft store across unrelated cards or accounts. Reset/remove it on config/account
changes. A submit carries a snapshot of the currently rendered form fields; removed fields are excluded even
while the host draft store retains dormant edits. Subsequent typing cannot mutate an in-flight payload.

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
ratio. Rectangular ancestor clips intersect their CSS hit regions, including transformed clips.
Arbitrary path clips, partially intersecting rounded clips and offscreen surfaces use a clipped
canvas “Open in app to edit” alternative instead of placing an unsafe DOM overlay. Nested document
instances namespace overlay identities/drafts; submit snapshots include untouched sibling defaults.
They are removed when absent from the current frame or when the player is destroyed. They
use native labels, selects, input/textarea, keyboard and focus behavior. Canvas-only screenshot APIs
repaint and export a deterministic control/value representation beneath those overlays; browser-native
focus rings, IME windows and open dropdown menus require a whole-page screenshot. Other custom hosts retain
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

## Author-owned chrome

`RcInputs.text("message", "Message", multiline = true).copy(chrome = RcInputChrome.NONE)`
works with both `RemoteInput` and `rcInputComponent`; place that component inside your own Remote
Box with themed background, border, padding and visible label. In TypeScript use
`{key: 'message', label: 'Message', multiline: true, chrome: 'none'}` with `inputDocument`.
The control remains transparent and omits its generic border, padding and visible caption.
LABEL still supplies the accessible name. Authors must supply a visible label in their wrapper.
Intrinsic choice menus, toggle/slider indicators, keyboard focus feedback, selection, validation and
unsupported-host alternatives remain. Chrome changes do not replace an active editor or alter drafts.

The property is optional and additive within v1. Existing players ignore it and draw default chrome;
release the updated players before depending on a borderless themed appearance. It grants no write
permission and does not override the widget/demo/read-only policy. Canvas exports honor transparency.
See `renders/rc-input-chrome` and `scripts/rc-input-message/capture-chrome.mjs` for actual default/none
renders of the same message control inside author-owned chrome.
