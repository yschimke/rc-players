# Portable message inputs

Actual Chromium renders, 640 × 900, produced by `scripts/rc-input-message/capture.mjs`.
`message-inputs.rc` is the same input document in both images. No Home Assistant credentials or
real service calls are used.

- `before.png`: baseline TS player from rc-players `533b435`, which has no registered input renderer.
  The evidence host checks capability and shows its useful open-Home-Assistant alternative. This
  is a forward-compatibility comparison, not a reproduction of the user's unavailable entity data.
- `after.png`: current TS player, native browser choice and multiline controls backed by opcode 93
  properties and the document's declared `TEXT_RETURN` channels.

The paired HA consumer has separate `before-entry`, `after-entry` and read-only screenshots from
its actual adapter, with synthetic HA state and an asserted authenticated service callback. Those
exercise live updates, changed choice options and focused drafts; no real HA message was sent.

To reproduce, build a baseline and current TS bundle with esbuild, then set `RC_PLAYER_BASELINE_JS`,
`RC_PLAYER_JS` and `PLAYWRIGHT_MODULE` and run `node scripts/rc-input-message/capture.mjs`.
