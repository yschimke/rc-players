# @yschimke/remote-compose-player-cmp

The Compose Multiplatform / Wasm [Remote Compose](https://developer.android.com/jetpack/androidx/releases/compose-remote)
player, as a browser bundle. It renders a `.rc` document into a page, driven entirely by query
parameters and one `window` function — no JavaScript API to learn and no bundler step.

```html
<iframe src="/rc-player/index.html?src=/documents/watch-face.rc&theme=dark"></iframe>
```

Or include the embed library beside it, which owns the iframe and turns the contract into promises:

```html
<script src="/rc-player/rc-cmp-player.js"></script>
<rc-cmp-player src="/documents/watch-face.rc" width="400" height="400"></rc-cmp-player>
<script>
  const player = RcCmp.createPlayer(container, { width: 400, height: 400 });
  await player.loadFromArrayBuffer(bytes); // e.g. a dropped file; resolves once it is on screen
</script>
```

`rc-cmp-player.js` talks to the player only by `postMessage`, so `dist/` may be served from any
origin, a CDN included. It finds `index.html` next to itself (override with `playerUrl`), and
`loadFromUrl` fetches on your page and sends the bytes, so documents need no CORS headers. The
server, whichever it is, must send `index.html` as `text/html` and `.wasm` as `application/wasm`,
and must allow the page to be framed.

## Install

```
npm install @yschimke/remote-compose-player-cmp
```

The package ships a `dist/` directory: `index.html`, the compiled Wasm module, the Skiko runtime,
a fonts manifest and the `rc-cmp-player.js` embed library. Serve it as static files — copy `dist/` into your public directory, or point
your server at `node_modules/@yschimke/remote-compose-player-cmp/dist`. Nothing here is meant to be
imported into an app bundle; the player runs in its own document, and `rc-cmp-player.js` is a plain
`<script>`.

Every file must be served from the same directory, and `.wasm` must be served as
`application/wasm` — the module is instantiated by streaming.

## Which player is this?

**There are two.** This one is the Compose Multiplatform renderer. The other is a TypeScript player
vendored inside `compose-preview`'s CLI, and today **it supports more operations**. Reach for this
package when you want the same renderer that runs on Android and iOS — one implementation, one set
of pixels across platforms. Reach for the TypeScript player when coverage matters more than
cross-platform parity. `RC_CMP_WASM_PLAYER.md` in the repository tracks which gates remain before
this one replaces it.

## The embed contract

Versioned separately from the release it ships in, because a host cares whether `?src=` still means
what it coded against — not which release it happens to have. `window.rcPlayerContractVersion` and
`document.documentElement.dataset.rcPlayerContract` both carry it, and this package's **major**
tracks it.

Full reference: [RC_PLAYER_EMBED.md](https://github.com/yschimke/rc-players/blob/main/docs/design/RC_PLAYER_EMBED.md).
Summary:

| parameter | meaning |
|---|---|
| `?src=` | URL of the `.rc` document. Required. |
| `?theme=light\|dark` | Force a mode. Anything else follows `prefers-color-scheme`. |
| `?fontsBase=` | Directory holding `fonts.json` and its faces. Default `./fonts/`. |
| `?namedValues=` | Host overrides for the document's named variables. |
| `?rcTrace=1` | Emit User Timing marks for a DevTools performance profile. |
| `?allowExternalImagePlaceholders=1` | Render a placeholder instead of failing on an external image. |
| `?handoffDelayMs=` | Cold-start tail before `ready`. Only lower it if you composite the result yourself. |

- `window.rcPlayerLoad(src)` swaps the document without reloading the page, keeping the Wasm module,
  the Compose runtime and the fetched fonts warm.
- `window.rcPlayerLoadBytes(bytes)` does the same from an `ArrayBuffer` or `Uint8Array` the page
  already holds, such as a dropped file. Open the page without `?src=`, wait for the marker to
  settle on `error` ("Missing ?src"), then call it. Feature-detect it: it was added within
  contract version 1.
- `document.documentElement.dataset.rcPlayerState` is `loading`, `ready` or `error`. Wait for
  `ready` before revealing the frame; `rcPlayerError` carries the message on `error`.
- From another origin, `postMessage` the iframe `{type: 'rc-player-hello', id}` until it answers,
  then `{type: 'rc-player-load', id, bytes}`; each load is answered by
  `{type: 'rc-player-state', id, state, error?}`.
- The player also `postMessage`s `cp-rc-wasm-ready` / `cp-rc-wasm-error:<message>` and structured
  host-action and debug-message events to `window.parent`: same-origin, and to the origin of a
  parent that has sent it a message.

## Size

The bundle is around 23 MB, nearly all of it the Skiko WebAssembly runtime. That is the cost of
running the real Compose renderer in a browser rather than a reimplementation of it. The repository
enforces a budget so an unintended jump fails the build; the budget is **not** a published
guarantee, and it moves when a deliberate payload lands.

## License

Apache 2.0.
