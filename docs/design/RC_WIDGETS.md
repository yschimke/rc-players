# Remote Compose widgets

`rc-widget/` is four published libraries for apps that draw launcher widgets, Wear OS widgets and
their own in-app cards from one Remote Compose design. They were extracted from
[homeassistant-remotecompose](https://github.com/yschimke/homeassistant-remotecompose), where they
were the most complete widget pipeline built on this stack, with everything Home Assistant-specific
left behind.

**Local generation is the main path.** An app captures its own composables on the device, caches
the templates it captured, and bakes them for each widget. Serving templates from a server
(server-driven UI) is an optional extra on top of that path. It does not replace it: every API here
works without a server, and the server-side pieces run the same code.

## The modules

| Module | Artifact | Targets | What it holds |
| --- | --- | --- | --- |
| `rc-widget/core` | `rc-widget-core` | JVM · Android · iOS · macOS · wasmJs | What a phone, a watch and a server have to agree on about a document. No Compose and no Android; safe on a server. |
| `rc-widget/creation` | `rc-widget-creation` | JVM · Android · iOS · macOS · wasmJs | Creation helpers. Compiled against AndroidX's `remote-creation-compose` on Android and against rc-players' CMP fork everywhere else. |
| `rc-widget/launcher` | `rc-widget-launcher` | Android | Android launcher widgets (`RemoteViews.DrawInstructions`). |
| `rc-widget/wear` | `rc-widget-wear` | Android | Wear OS widgets through Glance Wear. |

```
rc-player-edit ─▶ rc-widget-core ─▶ rc-widget-creation ─▶ rc-widget-launcher
                                                     └──▶ rc-widget-wear
```

### `rc-widget-core`

- **Targets and negotiation.** `RcWidgetTarget` names a surface: the player that draws it (an
  `RcTargetProfile`), what may be approximated to make a document play there, and whether the host
  can bind named values. `RcWidgetTargets` has four: `App`, `Launcher` (API 7+), `LauncherAndroid16`
  and `WearWidget`.
  - A client reports its player as `RcPlayerCapabilities` (`RC-Player: api=7; profiles=0x100;
    density=2.625; font-scale=1.0`). `negotiate()` picks the best target that player draws: the
    highest API level it supports, not a guess from the app version.
  - homeassistant-remotecompose's design notes describe this negotiation, but its code never had
    it.
- **The document pipeline.** `RcWidgetDocuments` turns a template into the bytes one host plays:
  - `bake` is for a host that cannot bind values: it writes the values in, embeds images, collapses
    constant state layouts and optimises.
  - `forDisplay` is for one that can: it folds in the display's density and font scale.
  - `serve` picks between them for a target and adapts the result to the target's player.
  - `RcNamedValues` is a typed value bag.
- **Binding contracts.** `RcBindingContract` is the set of named values a template reads, derived
  from the document itself so it cannot drift. `RcTemplateCheck.problems` checks a template someone
  else authored before it stands in for a known one. It finds:
  - violations the original didn't have,
  - values no host supplies,
  - values read with another type.
- **The Wear container contract.** `RcWearContainer` names the frame values a template leaves to the
  watch: `wear.container.horizontal_padding`, `vertical_padding`, `corner_radius` and `color`.
  - `RcWearFrame` and `RcWearHost` hold a host's containers (Pixel, Galaxy, Glance Wear's default).
  - `RcWearTemplates` writes a host's frame in: `instantiate` for a widget, `forApp` for the app's
    own player.
  - `fromCardDesign` turns a phone card design into a Wear template by making its card box the
    container.
- **Taps.**
  - `RcHostActionRewrites.toPendingIntent` turns an app's named taps into the one action a Wear
    widget host runs.
  - `RcWidgetActionIds` derives a launcher widget's numeric action ids from the payload, so they are
    stable across recaptures.
  - `RcWidgetActionMetadata` encodes and decodes the metadata the launcher's player delivers with a
    tap.
- **Local template library.** `RcTemplateLibrary` reads a template from the first store that has
  it. Otherwise it captures it, writes it to the stores and remembers failures.
  - Captures are single-flight per key.
  - `RcFileTemplateStore` (JVM and Android) is a disk cache scoped to what a capture depends on.
    `RcAndroidTemplateStores.captures` scopes it by build, density and font scale.
  - `RcBundledTemplates` is a read-only, sha256-checked bundle an app ships in its assets, so a
    fresh install draws without capturing first.
- **`RcDocumentCache`** is a byte-bounded LRU.

### `rc-widget-creation`

- **`ProvideRcExpressionNames` / `rememberRcExpressionName`** name expressions in composition order,
  so recapturing a design gives the same bytes. Templates can then be cached and served by content
  hash.
- **`RcSizeBreakpoint` and `RcMinSizeGate`** switch layouts on the live component size, so one
  document fits every slot.
  - Inside a launcher capture (`ProvideRcWidgetCaptureSize`) they pick the layout at capture time.
    Android 16's widget player doesn't reliably re-evaluate dimension-driven state layouts, and the
    launcher recaptures on every resize anyway.
  - `ProvideRcChildCaptureShare` narrows the size for grid cells.
- **`rcWidgetHostAction`** is a named host action in the app and a numeric one inside a launcher
  capture (`ProvideRcWidgetActions`). `rcWidgetActionMetadata` adds values the player resolves at
  tap time, such as a PIN typed into the widget or the value it showed.
- **Custom Wear containers.**
  - `RcWearTemplateContainer` leaves the frame to the watch.
  - `RcWearContainerFrame` draws a known frame.
  - `RcWearDecoratedContainer` paints an `RcWearBackground`: a base, then gradient, diagonal, glow
    and band layers, then an edge. It can also show an `RcWearLevel` as a ring or a fill in an
    `RcWearLevelLook` (track, pointer, sweep, segments, ghosts, marker, fade).
  - Everything is drawn to fit the host's rounded rect at the host's own radius, so one style fits
    a pill, a 26 dp corner and a 4 dp one.
  - `rcReadableAlpha` caps a fill under text at the alpha that keeps WCAG contrast.
- **AndroidX capture profiles for Android:** `androidXExperimental`, and `androidXExperimentalWrap`
  (paint-measure off).

### `rc-widget-launcher`

- **Capture profiles.**
  - `widgetsProfile` (Android 16) and `systemThemedWidgetsProfile` (API 37+, `ColorTheme`).
    `RcLauncherWidgets.profileFor` picks the right one.
  - Both drop `CoreText`, `TextStyle` and `FlowLayout`, which the platform decoder lacks.
  - Both bake `FEATURE_PAINT_MEASURE = 0`, so a resized widget re-measures instead of going blank.
- **`platformWidgetDocument`** is the Android 16 compatibility pass over the final bytes:
  - resources and expressions are lifted ahead of the layout,
  - component values sit on their component,
  - the wire minor version is zeroed.
- **`captureLauncherWidget`** is the local capture. It runs at the widget's slot size and embeds the
  document's images. Taps become numeric actions, and the result is the bytes plus each action's
  payload.
- **`RcLauncherWidgets.remoteViews`** attaches one `PendingIntent` per action.
  - `actionBroadcast` builds a mutable broadcast intent per tap, with a unique data URI. It is
    mutable because the player's metadata arrives as a fill-in extra.
  - `actionActivity` is for taps that need an activity in front.
  - `tapOf` reads a delivered tap back.
- **`RcWidgetSizing`** gives the slot from a widget's options and the cell grid.
  `RcLauncherGridBounds.fromProviderXml` reads a provider's resize range.
- **`RcLauncherWidgetPreview` and `RcLauncherGrid`** are the in-app resizable preview. They show the
  real `RemoteViews`, recaptured per size and inflated as the launcher would, on a draggable cell
  grid with accessibility actions.
- **`RcAdaptiveRemoteDocumentPlayer`** plays a document in the AndroidX View player. It plays at the
  exact size for pinned slots, rebuilding on resize, and wraps to the content's intrinsic size
  otherwise. It seeds live values before the first frame.
- **`RcWidgetImages`** downsamples images and re-encodes them as PNG within a byte budget, for
  Binder-bound payloads.

### `rc-widget-wear`

- **`RcWearWidgetTemplate`**
  - `capture` captures a template once, on the watch or the phone, inside a host-framed container
    with an optional custom background and level.
  - `instantiate` makes it each widget's `WearWidgetRawContent`. It takes the header Glance Wear
    writes for the widget's `WearWidgetParams`, writes in the host's frame and the widget's values,
    folds in the watch's density and font scale, and adapts the document to the Wear widget
    profile.
- **`RcTemplatedWearWidgetData`** draws a widget from its template and falls back (to a Glance Wear
  capture or a placeholder) when the widget has no template or it can't play there.
- **`withTaps` and `RcWearWidgetTaps`** make the widget's taps open the app.
- **`RcHostFramedWearWidgetDocument`** is Glance Wear's `WearWidgetDocument` with the frame left to
  the device: the producer-side twin of `instantiate`, matching compose-ai-tools'
  `HostFramedWearWidgetDocument`.
- **`RcWearFrameStore`** keeps the frames the watch's host last reported, so the app draws a card as
  its widget will look.
- **`RcWearWidgetConfiguration`** implements Glance Wear's provider-configuration contract (the
  request a configuration activity is started with) and lists placed widgets, for an app that
  configures each widget on the watch.

## Local generation, end to end

Launcher widget, from an `AppWidgetProvider` (off the main thread, through `goAsync`):

```kotlin
val size = RcWidgetSizing.slotOf(appWidgetManager, widgetId)
val capture = captureLauncherWidget(context, size, fetchImage = ::smallPng) { MyWidget(state) }
val intents = capture.actions.mapValues { (actionId, payload) ->
  RcLauncherWidgets.actionBroadcast(context, MyTapReceiver::class.java, widgetId, actionId, payload)
}
appWidgetManager.updateAppWidget(widgetId, RcLauncherWidgets.remoteViews(capture.bytes, intents))
```

To reuse one template across states, capture it once through an `RcTemplateLibrary` and bake each
state with `RcWidgetDocuments.bake`. A design that reads named values then needs no recapture when
only its values change.

Wear widget, from a `GlanceWearWidget`:

```kotlin
val library = RcTemplateLibrary(listOf(RcAndroidTemplateStores.captures(context))) { key ->
  RcWearWidgetTemplate.capture(context, background = myStyle) { MyCard(key) }
}
override suspend fun provideWidgetData(context: Context, params: WearWidgetParams): WearWidgetData {
  frames.remember(params)
  return RcTemplatedWearWidgetData(
      template = { _, _ -> library.get(cardKey) },
      fallback = WearWidgetDocument(WearWidgetBrush.Companion) { Placeholder() },
      values = currentValues(),
    )
    .withTaps(setOf("open")) { ctx, p -> openAppIntent(ctx, p.instanceId.id) }
}
```

The same template plays in the app with live values:
`RcWearTemplates.forApp(template, frames.frameFor(type))`.

## Server-driven, optionally

A server running the JVM artifacts can do everything above for a client that asks:

- capture with `rc-widget-creation` on the fork;
- read the client's `RC-Player` header, `negotiate()` a target and `serve()` the template for it;
- publish `RcBundledTemplates.layout(...)` as static, content-addressed files;
- check user-pushed designs with `RcTemplateCheck.problems`.

The client treats the server as one more `RcTemplateStore` ahead of its own captures, never as the
only source.

## Versioning

AndroidX's Remote Compose creation stack is alpha, and its pieces have to agree:

- `remote-creation*`, `glance-wear` and `remote-foundation` move as one set;
- the launcher's and the watch's players read a subset of the operations creation writes.

This library handles that skew at two levels:

- **Build time.** The widget modules compile against a widget lane in
  `gradle/libs.versions.toml`: `compose-remote-widgets` 1.0.0-alpha22, `glance-wear` 1.0.0-alpha21
  and `compose-remote-foundation-widgets` 1.0.0-alpha05. This is separate from the `compose-remote`
  line the vendored player and compat tests pin, and it stays on the versions widget hosts ship so
  one app never resolves two lines.
- **Run time.** Documents are captured with the app's full profile and rewritten for each host's
  player (`rc-player-edit`'s `adaptTo`, plus `platformWidgetDocument` for Android 16), rather than
  authored per host. The cases this covers:
  - a new design on an old launcher;
  - a template captured once and served to every Wear host;
  - an old app's template on a new player.

## What stayed in homeassistant-remotecompose

The card converters and Lovelace model, the Home Assistant action model and its dispatch, the
per-style container tables (which Home Assistant style gets which `RcWearBackground`), entity-state
tints, the design server's routes and GitHub integration, and the apps' screens.

homeassistant-remotecompose moves onto these modules once a release publishes them.
