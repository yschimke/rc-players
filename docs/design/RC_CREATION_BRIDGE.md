# Creation in rc-players, and the host bridge

**Status: prototype, not for landing.** This note records what the prototype in `rc-creation/` and
`rc-player/embedded/` shows, and what landing it would take. It follows the measurements in
yschimke/remote-m3-catalog#133.

## Decision

Remote content should be written once and run in two modes:

- **Document mode**: produce a self-contained `.rc` for another process.
- **Live mode**: feed an `RcComposePlayer` in the same process. Use cases are an editor canvas, a
  preview pane, or an app that renders its own remote UI.

The writer and the player both know which mode they are in, so they can bridge intelligently. Live
mode does not invent a second representation. **The document stays canonical in both modes.** What
changes is the route a host-owned value takes to the screen.

To make that a single change instead of a cross-repository dance, creation moves here, beside the
player:

| Module | Contents |
| --- | --- |
| `:rc-creation-writer` (`rc-creation/writer`) | `RemoteWriter` and the common byte writer, from remote-m3-catalog `vendor/remote-write-core`. |
| `:rc-creation-compose` (`rc-creation/compose`) | `@RemoteComposable` creation (`remote-creation-compose` + `remote-foundation`, commonMain only), `RemoteDocumentSession`, and the bridge contract (`ee.schimke.composeai.rccreation.bridge`). |
| `:rc-player-embedded` (`rc-player/embedded`) | `RcLiveBridge` and `RcEmbeddedPlayer`: the only module that knows both sides. |

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

## What the prototype proves

`RcEmbeddedPlayerTest` (JVM, Skiko UI test) covers five behaviours:

- **`aBridgedValueReachesThePlayerWithoutANewDocument`**: a host colour change shows in the
  player's pixels, and exactly one document was ever produced.
- **`aStructuralEditProducesANewDocument`**: editing what the remote content reads produces a new
  document, equal to a fresh capture of the same content.
- **`liveAndDocumentModesWriteTheSameDocument`**: the live document equals
  `captureCommonRemoteDocument` with the default document bridge.
- **`aBridgedActionCallsItsHandler`**: a click in the player runs the Kotlin handler.
- **`hostContentIsRealComposeLaidOutByTheRemoteTree`**: Compose UI is drawn in a remote-laid-out
  slot and updates without a new document.

`rc-creation-compose` and `rc-player-embedded` compile for JVM, Wasm and Android. The Apple targets
are declared but not built here, because they need a macOS host.

## Bugs found on the way

- **Custom component ids.** The port's `RemoteDocumentWriter.startCustom` wrote its content section
  with the custom layout's own component id. Upstream's `addContentStart` allocates a fresh one. The
  result was two components with one id, which the player rejects ("Duplicate layout component id").
  It is fixed here, and remote-m3-catalog's `vendor/remote-write-core` has the same bug.
- **Component ids drift in a live composition.** `RemoteComposeNode` allocates render ids from a
  counter that was never reset, so every re-render renumbered every component.
  `RemoteDocumentSession` resets it per render. Ids are still positional, so an insertion renumbers
  later siblings. Anything keyed on ids across structural edits needs explicit ids; see below.

## Landing it

1. **Refresh the port to the current androidx snapshot** in remote-m3-catalog before moving it. The
   delta is listed in remote-m3-catalog#133. Bump this repository's AndroidX lane to
   `alpha21`/`alpha13` alongside.
2. **Publish creation from here.** remote-m3-catalog deletes `vendor/remote-write-core`,
   `remote-creation-compose`, `remote-foundation` and `remote-core`, and takes `rc-creation-*` from
   the rc-players BOM. The writer and player become one version, not a pinned pair.
3. **Gate the creation API like the player.** Today `rc-creation-*` is not `explicitApi()` or
   ABI-dumped. The imported sources are not explicit-API clean, and the AndroidX-shaped surface is
   large. Mark it experimental and dump it, so churn is reviewed.
4. **Decide the package question.** The modules keep upstream's `androidx.compose.remote.creation.*`
   packages so refreshes stay a diff. On Android, a consumer that also takes AndroidX's own
   `remote-creation-compose` would get duplicate classes. The options are to relocate, or to make
   Android consumers choose one. yschimke/wear-m3-catalog#652 is the precedent for how badly a
   duplicate FQN fails.
5. **Player state across structural edits.** `RcComposePlayer` keys its state on the document, so a
   structural edit still resets scroll and animation. Live mode wants a host-owned state that
   carries variables and per-component state over by component id. That needs **explicit component
   ids** from creation: an editor derives them from authored node ids.
6. **The rest of the bridge.** Add images (`bridge.image(name) { ImageBitmap }`): live mode hands the
   bitmap to the player without a PNG encode/decode, which, unlike text decode, is real cost. Also
   add ints and float lists, and two-way values (the player's `HostNamedAction` value feeding host
   state).
7. **UI builder.** Its Browser Preview captures from scratch on every edit; this replaces that with
   `RcEmbeddedPlayer`. Its Visual Editor can then use `HostContent` per component instead of a
   wholly faked surface. That needs per-node bounds from the player (via explicit ids) and an
   unrolled authoring mode in creation.
