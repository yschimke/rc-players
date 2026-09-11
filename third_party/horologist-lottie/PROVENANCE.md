# Vendored: `horologist-lottie` (Horologist's Lottie → Remote Compose compiler)

A **Lottie compiler**, not a Lottie player. `LottieAnimation(json = …)` is a `@RemoteComposable`:
it parses a Lottie JSON animation, and re-emits every layer, shape, gradient and transform as
Remote Compose *creation* operations whose animated properties are expressions over the document's
own animation clock (`Rc.Time.ANIMATION_TIME`) or over an author-supplied `progress`.

The consequence is the reason this is here: the animation ends up **inside the `.rc` document**.
Nothing on the device needs a Lottie runtime, or the JSON, or a network fetch — a player that can
read a `.rc` document can play the animation, which is exactly what a Wear widget host does.

## Upstream

- Repository: <https://github.com/google/horologist>
- Path: `remotecompose/lottie`
- Commit: `5a5e0cda24170f581711f8c678f028420d79dbfe` (`main`, 2026-09-04)
- License: Apache-2.0 (see the per-file headers, kept verbatim)

Horologist publishes no artifact for this module — it carries no `maven-publishing` plugin, and
nothing under `com.google.android.horologist:horologist-remotecompose-*` exists on Maven Central.
So a consumer either vendors it or does without.

## We publish this snapshot

`ee.schimke.composeai:third-party-horologist-lottie`, on this repository's release line, under the
same terms as `:third-party-rc-embedded-player`: **our snapshot at the commit pinned above, not an
artifact Horologist released, and not a supported API.**

Vendoring makes the compiler checkable inside *this* build. It does nothing for a consumer in
another repository, and there is one: yschimke/wear-m3-catalog's `:remote-catalog` needs to draw a
Lottie sticker so the published `remote-m3` catalog can offer `remote-m3/lottie` — the last
component the cutover loses to the synthesised shelf (compose-preview-server#674). That sticker
calls `LottieAnimation(json = …)`, and before this coordinate there was nothing for it to call.

The alternative was a second vendored copy over there, which is worse in the way two pinned
snapshots of one upstream always are: they drift, and this file stops being able to say which
commit is authoritative. One copy, one pin, published.

## Why this repository vendors it

`yschimke/compose-preview-server`'s UI builder gained a Wear `LottiePlayer` element, whose Kotlin
export calls `LottieAnimation(json = …)`. Exported source that compiles against nothing is a
promise nobody checks; pinning the compiler here — beside the other upstream sources this
repository measures itself against — makes it checkable in CI at a *known* revision, rather than
against whichever Horologist working copy happened to be on a developer's disk.

**Temporarily**, and the word is load-bearing: the moment Horologist publishes this module, the
vendored copy should be deleted and replaced with the coordinate. Nothing here is a local
improvement worth keeping — see *Local modifications*, which is deliberately almost empty.

## What is vendored

`src/main/` only — 40 Kotlin files, ~3.6k lines, in two halves:

| Package | What it is |
| --- | --- |
| `…/lottie/format/` | The Lottie JSON model and its `kotlinx.serialization` decoder: layers, shapes, keyframed properties, beziers, gradients. |
| `…/lottie/renderer/` | The other half of each of those types, emitting Remote Compose operations for it. |
| `…/lottie/LottieAnimation.kt` | The entry points — `LottieAnimation(json)` and `LottieAnimation(@RawRes)` — plus the frame expression and the fit-and-centre scale modifier. |
| `…/lottie/SlotMap.kt` | Lottie slot id → `RemoteColor`, for theming an animation from the host. |

Deliberately **not** vendored:

- `src/debug/` — upstream's `@Preview` harness and its raw-resource sample animations. It exists to
  look at the output in Android Studio, and pulls in `remote-player-*` and the tooling previews.
- `src/test/` and `src/test/screenshots/` — Robolectric screenshot tests bound to upstream's
  `:roboscreenshots` module, which is Horologist infrastructure this repository does not have. The
  parity question they answer (does the compiled document match `lottie-compose`'s own render?) is
  upstream's to answer at the pinned commit; ours is whether the exported source compiles and the
  builder's element round-trips.
- `api/current.api` — a metalava dump for a module Horologist publishes an API surface for. This
  copy is an implementation detail of an export lane, not a published API, and this repository's
  ABI gate covers `rc-player/*` only.

## What is ours

One file, and it is not in `src/main`:
`src/test/java/ee/schimke/composeai/horologist/lottie/ExportedCallShape.kt`. It asserts nothing at
runtime — it is a *compilation* of exactly the call the UI builder's `remote-m3/lottie` export
writes, so a renamed argument or a moved package in a newer Horologist breaks the build here
instead of in the file somebody pasted into their app.

## Copyright

Every file keeps its `Copyright … The Android Open Source Project` header and the Apache-2.0
notice verbatim. No header was rewritten, and no file was re-attributed.

## Local modifications

**One line, and it is whitespace.** `format/graphicelement/geometry/Ellipse.kt` had a 101-column
declaration that this repository's `ktfmtCheckAll` gate — ktfmt in Google style, applied to every
module — wraps onto two lines. Nothing else differs: `diff -r` against upstream's
`remotecompose/lottie/src/main` at the pinned commit reports that hunk and no other, and it should
stay that way. A behaviour fix belongs upstream, where the screenshot tests that can prove it live.

The build file is ours (`build.gradle.kts`), because upstream's applies Horologist's own
convention plugins, metalava and roborazzi. It differs from upstream's in three ways worth naming:

1. `namespace` is `ee.schimke.composeai.horologist.lottie`, not upstream's package. The *sources*
   keep `com.google.android.horologist.remotecompose.lottie` so the diff above stays empty; only
   the manifest namespace moves, so the vendored AAR can never be mistaken for a published
   Horologist one.
2. No `metalava`, no `roborazzi`, no `composeAiPreview` — the modules those need are not here.
3. Not published. `:third-party-rc-embedded-player` is published for parity testing because a
   parity lane has to be selectable from outside; this has no such consumer.

## Version skew

Compiled against whichever `androidx.compose.remote` line `composeai.remoteCompose` selects
(`release` by default, `snapshot` with `-Pcomposeai.remoteCompose=snapshot`), like every other
Remote Compose consumer in this build. Upstream compiles against Horologist's own catalog, so a
creation-API change can break this module here before it breaks upstream — which is the same early
warning `:third-party-rc-embedded-player` gives on the player side, and is worth having.
