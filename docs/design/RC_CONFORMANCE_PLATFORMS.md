# Running the conformance lane on the other platforms

The lane measures the CMP player on the JVM. The same player also compiles to iOS, macOS and wasmJs,
and the question is what it takes to score those too. This is the plan, the parts that are already
free, and the one platform that cannot be done at all yet.

## What is already portable, and why

Three of the four pieces need no work.

**The corpus model and the comparator** (`corpus/`, `runner/`) are ordinary Kotlin. They touch the
platform in exactly two places — `java.io.File` to read golds, and `javax.imageio` to decode a gold's
reference PNG — and both have a straightforward `expect`/`actual`: skiko targets decode through
`org.jetbrains.skia.Image.makeFromEncoded`, Android through `BitmapFactory`.

**The inspection seam is already multiplatform.** Publishing the tree and the document state through
Compose **semantics** rather than a bespoke observer was worth it for reasons of its own, and this is
the unplanned payoff: `RcComponentIdKey` and friends live in `commonMain`, and every Compose target
resolves them the same way. Had the seam stayed a JVM-side observer, each platform would need its
own.

**The driving surface is common too, which is the part worth knowing.** `runComposeUiTest` is
declared in `ComposeUiTest.skiko.kt` and exists on every skiko target, with an Android equivalent. It
provides exactly what the corpus's timeline needs:

| corpus needs | `ComposeUiTest` gives |
| --- | --- |
| load a document, paint it | `setContent`, `waitForIdle` |
| advance animation deterministically | `mainClock` — `advanceTimeBy`, `autoAdvance` |
| read the laid-out tree and the state | `onRoot().fetchSemanticsNode()`, the unmerged tree |
| a rendered frame for `raster` | `onRoot().captureToImage()` |
| gestures | `performTouchInput` |

So a single engine written against `ComposeUiTest` in `commonMain` can serve JVM, iOS, macOS and
wasmJs — and Android, if it had a target. That is a rewrite of `CmpEngine`, not a new engine per
platform.

> [!NOTE]
> **That rewrite is done.** `CmpEngine` now hosts the player in `SkikoComposeUiTest`, advances time
> with `mainClock` and dispatches gestures with `performTouchInput`, at the same 167/241 core golds
> as the `ImageComposeScene` engine it replaced. It was worth doing even before a second platform
> exists, and it paid for itself twice on the way:
>
> * **Gestures now arrive.** `interaction_scroll_column` and `interaction_scroll_row` previously
>   observed a scroll offset of `null` and a tree that never moved — the pointer events dispatched
>   through `ImageComposeScene.sendPointerEvent` reached the player not at all (#199). Under
>   `performTouchInput` the content actually scrolls. Both golds still fail, at a smaller offset than
>   the corpus expects, but the mechanism works and the remaining gap is a real one rather than a
>   harness that was never delivering the input.
> * **It exposed a player bug the old harness hid.** The old engine could not resize its host, so
>   every `resize` step rebuilt the scene and replayed the timeline — which also discarded any
>   in-flight animation. Driving a live composition instead showed the player animating *every*
>   layout node's bounds over 300ms by default, so a viewport change crossed the screen rather than
>   landing. See the note below.

### What the live composition found

Removing the rebuild-and-replay is the substantive behavioural change, and it immediately
contradicted the corpus in 119 golds: every `resize` step reported a viewport part-way to its new
size.

The corpus is unambiguous about which side is wrong, because it asserts *both* behaviours. §3 says a
`resize` step sets the viewport and then paints — 119 golds expect the new size in the very next
`tree`. And `animation_box_offset`, whose document **declares** an animation spec, captures 18 frames
across a resize and expects `100 → 199.45 → 299.75 → 300`: an animation, interpolated in the tree.

So the player was animating a document that never asked it to: `animateRcBounds` fell back to
`DefaultRcAnimationSpec` for any node without one. Animating only when the document declares a spec
restores all 119 and removes 22 further failing checks with no gold regressing. The default still
applies to shared elements inside a `StateLayout`, which is the case upstream declares it for.

This is the rewrite's real argument. A harness that rebuilds on every resize cannot see a resize
animation at all, and had been quietly scoring the player against a behaviour it does not have.

### Verified, not assumed

The multiplatform Compose test API was probed on the real targets rather than taken on trust, because
the whole plan rests on it:

| | macOS native | wasmJs | iOS simulator | JVM |
| --- | --- | --- | --- | --- |
| compiles against `ui-test` | yes | yes | yes | yes (today) |
| `runComposeUiTest` + `mainClock` | **runs** | not yet run | not yet run | **in use — the lane** |
| unmerged semantics (`fetchSemanticsNode`) | **runs** | — | — | **in use — the lane** |
| `captureToImage()` headless | **runs** — real pixels | — | — | **in use — the lane** |

One thing the rewrite had to work around: `SkikoComposeUiTest` fixes its output **surface** at
construction, so the surface is sized once to the largest viewport a gold's timeline ever asks for
and `scene.size` — reachable, but internal compose-ui API — is what a `resize` moves. Content lays
out from the origin, so a frame is the surface snapshot cropped to the current viewport. Any port to
another platform inherits that arrangement.

`org.jetbrains.compose.ui:ui-test` is declared only in `jvmTest` today; moving it to `commonTest` is
what makes the klib targets resolve it, and they do.

One trap worth recording, because it cost a wrong conclusion: `captureToImage()` on a root that
measures 0×0 fails with `kotlin.RuntimeException: Can't wrap nullptr`, which reads like "native
cannot render headless" and is not. Give the probe real content and it returns a real frame. A
conformance run always has content, so this only bites when writing the probe.

## What is actually hard: getting the corpus in and the results out

The rendering is the easy half. Every remaining problem is logistics, and they differ per platform.

**The corpus is 32 MB across 573 files**, and each gold embeds its reference images as base64, so it
cannot be trimmed much. It is also, deliberately, *not* in this repository — it lives on
`vendor/androidx-rc-conformance` and is resolved as configuration.

| platform | corpus delivery | results extraction |
| --- | --- | --- |
| JVM | filesystem, as today | write a file |
| macOS native | filesystem — same machine | write a file |
| iOS simulator | bundle into the test app, or read the host path the simulator can see | write to the app sandbox, `xcrun simctl` it back |
| wasmJs (browser) | **no filesystem** — fetch over HTTP from a static server | POST back, or print and scrape the console |
| Android | push to the device, or ship as assets | `adb pull` |

The wasm row is the awkward one, and it is awkward in a way that is already solved next door: the
repo serves the Wasm player's `dist` bundle for its smoke lane, so a static server for the corpus is
a configuration away rather than new infrastructure.

**Cost is a real constraint on wasm.** The corpus asserts 633 raster checks, each an
antialiasing-aware pixel walk over a full frame. That is comfortable on a JVM and slow in a browser.
The honest mitigation is to run wasm against the non-raster channels by default and the raster ones
on request — reported as such, never silently skipped.

## Ordering, by what it costs

1. **macOS native.** Same machine, real filesystem, skiko rendering. The cheapest genuine second
   platform, and the one that proves the `ComposeUiTest` rewrite carries.
2. **iOS simulator.** The repo already boots simulators with bounded retries for the native UIKit
   lanes, so the hard part is done; this is bundling and extraction.
3. **wasmJs.** Needs the corpus served and a headless browser driving the run.
4. **Android — blocked, see below.**

## Android is blocked, and not by the harness

`rc-player/compose` declares `jvm()`, `iosArm64()`, `iosSimulatorArm64()`, `macosArm64()` and
`wasmJs()`. **There is no Android target.** So there is nothing to run a conformance lane against,
and no amount of harness work changes that.

This also contradicts the module table in `README.md`, which lists the CMP player's targets as
"JVM · Android · iOS · macOS · wasmJs". One of the two is wrong and it is worth settling which:
either an Android target should exist and does not, or the README is overstating what the player
ships. Compose UI on Android is not served by the JVM artifact, so an Android consumer today is not
getting what that row implies.

Until that is resolved, the Android row of any scorecard should say *not built*, which is a different
statement from *not measured*.

## What a second platform would actually tell us

Worth being clear-eyed, because the cost is non-trivial and three of these targets run the *same
code*.

The Wasm host and the Apple XCFramework are the CMP player in different wrappers — the README is
explicit that the Wasm lane is "the CMP player, not a second implementation". So a second platform's
score is not an independent measurement of layout or document semantics; those will agree with the
JVM lane because they are the same Kotlin. What it does catch is everything the *platform* supplies:
text shaping and metrics, shader compilation, image decoding, and the antialiasing behaviour of a
different skiko backend.

That is precisely where this player's remaining failures already live. Of 208 failing raster checks
on the JVM lane, one is attributable to this player; the rest are shared with the vendored AndroidX
port or unmeasured. A macOS or wasm lane would say whether those shared divergences are a Compose
interpretation or a backend difference — which is the open question behind #207.

So: a second platform is worth building for the **raster** channels, and close to redundant for
`tree`, `float` and the operation census. Scope it that way rather than running all 252 golds
everywhere for the sake of symmetry — and since `captureToImage()` does work headless on native, the
channel that carries the value is the one that is available.
