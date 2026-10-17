# Creation in rc-players, and the host bridge

**Status: published, experimental.** This note records the design of `third_party/remote-creation-*`,
`rc-creation/` and `rc-player/embedded/`, what it was measured against, and what comes next. It
follows the measurements in yschimke/remote-m3-catalog#133.

## Decision

Remote content should be written once and run in two modes:

- **Document mode**: produce a self-contained `.rc` for another process.
- **Live mode**: feed an `RcComposePlayer` in the same process. Use cases are an editor canvas, a
  preview pane, or an app that renders its own remote UI.

The writer and the player both know which mode they are in, so they can bridge intelligently. Live
mode does not invent a second representation. **The document stays canonical in both modes.** What
changes is the route a host-owned value takes to the screen.

To make that a single change instead of a cross-repository dance, creation moves here, beside the
player. It is split in two: one fork, plus a layer of rc-players' own.

| Module | Package | Contents |
| --- | --- | --- |
| `:third-party-remote-creation-writer` | `androidx.compose.remote.creation.common` | **Fork.** `RemoteWriter` and the common byte writer. |
| `:third-party-remote-creation-compose` | `androidx.compose.remote.creation.compose`, `androidx.compose.remote.foundation` | **Fork.** `@RemoteComposable` creation, kept in AndroidX's package with marked local patches only ([PROVENANCE](../../third_party/remote-creation-compose/PROVENANCE.md)). |
| `:rc-creation-bridge` | `ee.schimke.composeai.rccreation` | **Layer.** rc-players' own creation API: the host bridge contract and explicit component ids. |
| `:rc-player-embedded` | `ee.schimke.composeai.rcplayer.embedded` | **Layer.** `RcLiveBridge` and `RcEmbeddedPlayer`; the only module that knows both creation and the player. |

### Why one fork in AndroidX's package, not two

A second fork, relocated and free to restructure, was considered and rejected.

Component libraries are written against `androidx.compose.remote.creation.*`: Wear
`remote-material3`, the catalog's components, and the Kotlin a UI builder exports. With the fork in
that package, the same source compiles against the fork in a CMP host (editor, preview, desktop)
and against real AndroidX on a device. A relocated fork would need a second build of every
component library, and exported code would no longer match what ships.

Structural work therefore goes in the layer, in rc-players' own package. When the layer needs
something only the fork's internals can give (a capture seam, an id reset, an applier hook), the
fork gets a small marked patch that is generic enough to offer upstream. The fork's diff from
upstream stays reviewable, and refreshes stay mechanical.

The one cost is on Android: the fork and AndroidX's own `remote-creation-compose` share class
names. Publishing it needs a Gradle capability that both provide, so Gradle reports the conflict
when both are present instead of silently loading duplicate classes.

Creation does not depend on the player, and the player does not depend on creation.

Targets are the player's own: JVM, Android, iOS, macOS and Wasm. The port's legacy JVM writer
(`vendor/remote-core`, about 340 Java files) and its AndroidX `remote-creation` Android integration
are not carried. Every target, Android included, captures through the common `RemoteWriter`.
`remote-material3` stays in remote-m3-catalog; it is that repository's component catalog, and it
would consume these modules.

## The contract: names

Each creation-side declaration already has a player-side input with the same name:

| Creation declares | Document mode | Live mode | Player input it reaches |
| --- | --- | --- | --- |
| `bridge.float/color/text/boolean(name) { value }` | named variable, default = value read during composition | same named variable; later values go to the player directly | `namedValues: SnapshotStateMap` |
| `bridge.action(name) { handler }` | `hostAction(name)` | same, and `handler` runs | `onEvent` → `HostNamedAction` |
| `bridge.HostContent(name, modifier, fallback) { content }` | `fallback` (remote content) | a `LAYOUT_CUSTOM` slot drawing `content` (real Compose) | `customComponents` |

`RcHostBridge` is the interface. `RcDocumentBridge` (the default for `LocalRcHostBridge`) is
document mode, and `RcLiveBridge` is live mode.

Every declaration is `@Composable` and lasts as long as its call is in the composition. In live mode
a declaration registers when it enters and is removed when it leaves, so content that stops declaring
a name (an editor deleting a node) leaves no reader, handler or host content behind.

The bridging is "intelligent" in one specific way. In live mode a bridged value is read **without
snapshot observation** inside the remote composition. A host-side change therefore never
recomposes the remote content or rebuilds the document. A `snapshotFlow` carries it into the
player's named values, so the player keeps scroll, animation and touch state. In document mode the
same call is an observed read, so a change recaptures, which is what a document needs.

Structure and data take different routes in live mode:

```
host state ──(bridged value)──────────────────────────────► player namedValues
          └─(read by remote content)─► recompose ─► render ─► RcDocument ─► player
```

`HostContent` is the one place where the two modes write different documents. It is deliberate and
local. It lets an editor put a real Compose stand-in exactly where an unsupported remote component
would go, inside an otherwise real document, instead of choosing between a fully real surface and a
fully faked one.

## Why the document stays canonical

A separate live representation (handing the player a pre-linked tree, say) was considered and
rejected. Measured on a 37-op screen (remote-m3-catalog#133):

| Step | Time |
| --- | --- |
| decode | 10–30 µs |
| recompose + render in a live session | 0.3–0.55 ms |
| fresh capture | 0.7–3 ms |

Skipping the bytes saves 1–3%. The win is the live session (no fresh composition per edit) and the
bridge (no document at all for value edits). A typed writer emitting `RcOperation`s directly is
still worth doing, because it would retire the second encoder. It is a cleanup, not a speed-up.

## What the tests prove

`RcEmbeddedPlayerTest` (JVM, Skiko UI test) covers the bridge and the session's lifecycle, including:

- **`aBridgedValueReachesThePlayerWithoutANewDocument`**: a host colour change shows in the
  player's pixels, and exactly one document was ever produced.
- **`aStructuralEditProducesANewDocument`**: editing what the remote content reads produces a new
  document, equal to a fresh capture of the same content.
- **`liveAndDocumentModesWriteTheSameDocument`**: the live document equals
  `captureCommonRemoteDocument` with the default document bridge.
- **`aBridgedActionCallsItsHandler`**: a click in the player runs the Kotlin handler.
- **`hostContentIsRealComposeLaidOutByTheRemoteTree`**: Compose UI is drawn in a remote-laid-out
  slot and updates without a new document.
- **`aFailingDesignIsReportedAndRecoversOnTheNextEdit`**: a design that throws is reported once, the
  last good document stays up, and the next `key` recovers.
- **`contentCapturingAHostValueFollowsItsKey`**: content capturing a plain host value is recomposed
  when its `key` changes.

`RcEmbeddedPlayerRecompositionTest` covers every kind of recomposition the session follows, after
AndroidX's `CaptureRemoteDocumentRecompositionTest` for the streaming capture. Each new document is
compared with a fresh capture of the same content, and renders are counted by a `RemoteCanvas` draw
lambda, because comparing documents would hide a render that changed nothing:

| Recomposition | Expected |
| --- | --- |
| A state read in composition | a new document |
| A state an effect in the remote content writes | a new document per change |
| A flow collected in the remote content (no manual apply notification) | a new document |
| A state read only while rendering (a `RemoteCanvas` draw lambda) | a new document |
| A write nothing reads | no render |
| A recomposition that applies nothing | no render, no document |
| Writes applied in one snapshot | one document |
| Keyed children moved or removed | a document equal to a fresh capture |
| An edit beside `remember`ed state | the state survives (one composition, not a capture per edit) |
| The host recomposing for its own reasons, same content and key | nothing inside recomposes |

The fork, `rc-creation-bridge` and `rc-player-embedded` compile for JVM, Wasm and Android. The Apple
targets are declared but not built here, because they need a macOS host.

**In a real host.** remote-m3-catalog's Browser Preview runs on `RcEmbeddedPlayer` behind
`-PrcPlayersDir` (yschimke/remote-m3-catalog#133). Its existing UI suite passes as it does on the
capture path: 25 of 26, and the remaining one fails the same way on both, for a font absent from the
checkout. That covers every golden widget design in every host shape, icons, backgrounds, edits,
failures and clocks, and every published Remote Material 3 component recording. The published
`remote-material3` binary links against this fork unchanged.

## How the live session has to run

The UI builder integration settled four things the unit tests alone did not:

- **Off the host, on its own clock.** The first version composed the remote content as a child of
  the host composition. A design that throws would then take the host down. The second used its own
  `Recomposer` on the host's frame clock, which made a loop: every snapshot change (the player's own
  frame time included) had the remote recomposer ask the host for a frame, which advanced the
  player, which changed a snapshot again. The pane never idled. Now the session runs as the one-shot
  capture does: its own recomposer, single-threaded dispatcher and immediate frame clock, with
  results handed back to the host's dispatcher. Snapshot state is what crosses the boundary, and
  `RcLiveBridge` keeps its maps in snapshot state for that reason.
- **An explicit `key`.** A composable lambda keeps its identity across recompositions and updates
  its body in place. That update invalidates the scopes that invoked it only within its own
  composition, so it never reaches the remote one. The host passes what the content describes (an
  editor, its document) as `key`, and the remote content recomposes when that changes.
- **"Rendered" is "composed", not "changed".** An edit that changes no remote node applies nothing,
  so the applier never asks for a render. `onRendered` is a `SideEffect` at the top of the remote
  composition, so it fires once the latest content is composed whether or not a document changed.
  Hosts that report readiness per edit need exactly that.
- **A bridge per session, frozen on the way out.** A failed edit leaves the last good document up,
  and that document still plays through its session's bridge. Each session declares into its own
  bridge, and the player plays through the bridge of the session whose document is on screen. A
  session's bridge is frozen before the session is disposed, so its values, handlers and host
  content outlive it until a replacement session has rendered.
- **Key and content as one state.** The host updates a composable lambda's body in place, so the
  remote composition can run a new edit's content before it sees the new key. Key and content are
  handed over together, and a failure is pinned to the key the session composed.
- **Recompose like Compose UI, render only on change.** There is no throttle: the session follows
  every change as an ordinary composition would. It renders when an apply changed the tree, or when
  a state the last render read changed (a `RemoteCanvas` draw lambda runs at render time, so a state
  read only there applies nothing); upstream's streaming capture decides the same way. A
  recomposition that applies nothing renders nothing.
- **The recomposer runs before the content is set.** Content composed while the recomposer was not
  yet running was composed again as soon as it started, and rendered twice.
- **Dispose quietly.** Disposing the session clears the tree, which the applier reports as a change.
  The change hook is detached first, or a dead session would replace the last good document with an
  empty one.

## Explicit component ids and player geometry

`RemoteModifier.componentId(id)` (a fork patch) asks for a component id instead of a positional
one. `RcComponentIds` (`:rc-creation-bridge`) hands out stable ids per host key, and
`LocalRcComponentCoordinates` (`:rc-player-compose`) reports each laid-out component's
`LayoutCoordinates` by id. Together, a host can say where each of its own nodes was drawn.

`RcComponentIds` is deliberately not snapshot state: a widget's content and background are two
remote compositions composing concurrently in their own snapshots, and snapshot maps let both hand
out the same first id.

remote-m3-catalog's Visual Editor uses this (behind `-PrcPlayersDir`) to run its authoring surface
on the real recording, played, instead of Compose UI stand-ins: every authored node of the golden
designs gets bounds from the player, nested as the design nests them.

## Bugs found on the way

- **Custom component ids.** The port's `RemoteDocumentWriter.startCustom` wrote its content section
  with the custom layout's own component id. Upstream's `addContentStart` allocates a fresh one. The
  result was two components with one id, which the player rejects ("Duplicate layout component id").
  It is fixed here, and in remote-m3-catalog's `vendor/remote-write-core` (yschimke/remote-m3-catalog#133).
- **Component ids drift in a live composition.** `RemoteComposeNode` allocates render ids from a
  counter that was never reset, so every re-render renumbered every component.
  `RemoteDocumentSession` resets it per render. Ids are still positional, so an insertion renumbers
  later siblings. Anything keyed on ids across structural edits needs explicit ids; see below.

## Publishing and stability

All four modules publish with the player, under the same version and BOM.

- **The fork** (`third-party-remote-creation-writer`, `third-party-remote-creation-compose`) is
  `explicitApi()` and ABI-dumped like the player. Its surface is AndroidX's, so it follows AndroidX's
  stability; the dumps make a refresh's changes reviewable rather than gating them.
- **The layer** (`rc-creation-bridge`, `rc-player-embedded`) is ABI-dumped and
  `@ExperimentalRcCreationApi`: callers opt in, and its shape may change between minor releases.
  The player's geometry hook (`LocalRcComponentCoordinates`) is `@ExperimentalRcPlayerApi` in
  `rc-player-compose` for the same reason.
- **Android.** The fork and AndroidX's own `remote-creation-compose` share class names. The compose
  fork declares AndroidX's capabilities (`androidx.compose.remote:remote-creation-compose`,
  `androidx.compose.remote.foundation:foundation`), so a consumer that resolves both gets Gradle's
  capability conflict, naming them, instead of duplicate classes at dex time.
  yschimke/wear-m3-catalog#652 is the precedent for how badly a silent duplicate FQN fails.

## What comes next

1. **Bump this repository's AndroidX lane** to `alpha21`/`alpha13`; the fork is already on
   androidx-main `51b1c49d7` (see PROVENANCE).
2. **remote-m3-catalog takes creation from here.** It deletes `vendor/remote-write-core`,
   `remote-creation-compose`, `remote-foundation` and `remote-core`, and takes these artifacts from the
   BOM, so the writer and player become one version, not a pinned pair.
3. **Player state across structural edits.** `RcComposePlayer` keys its state on the document, so a
   structural edit still resets scroll and animation. Live mode wants a host-owned state that carries
   variables and per-component state over by component id; explicit ids (`RcComponentIds`) are the
   prerequisite, and are in.
4. **The rest of the bridge.** Images (`bridge.image(name) { ImageBitmap }`): live mode hands the
   bitmap to the player without a PNG encode/decode, which, unlike text decode, is real cost. Also
   ints and float lists, and two-way values (the player's `HostNamedAction` value feeding host
   state).
5. **UI builder.** Behind `-PrcPlayersDir`, remote-m3-catalog's Browser Preview and Visual Editor
   surface both run on `RcEmbeddedPlayer` (yschimke/remote-m3-catalog#133). The editor surface still
   lacks text baselines, semantic actions, empty-slot placeholders and horizontal unrolling.
