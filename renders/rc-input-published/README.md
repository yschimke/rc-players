# Published JS input bundle regression

Both 640 × 900 images use the actual Home Assistant message-card adapter and
Playwright fixture from consumer PR #910 (`de805ae7643567088818571dfa504642a2259667`).
No real Home Assistant action is dispatched.

- `2.16.0-before.png`: Maven Central's
  `ee.schimke.composeai:remote-compose-player-js-dist:2.16.0:dist@zip` bundle.
  The message test times out waiting for the editor, and the card displays its
  explicit upgrade alternative because `RC.inputDocument` is absent. A screenshot
  was captured in the existing `message.waitFor().catch(...)` failure handler.
- `regenerated-after.png`: the committed `dist/bundle.js` regenerated from the
  same player source as the public input contract. The unchanged consumer test
  edits choices and a multiline Unicode message, preserves focus/drafts across
  live updates, then verifies the configured authenticated action payload.
  This is its `after-entry.png` capture. The Linux fixture font lacks the emoji
  glyph, but the asserted action payload preserves the Unicode text.

Regenerate the browser bundle using `third_party/remote-compose-player/dist/README.md`.
`bash scripts/check-typescript-dist.sh` verifies that Maven's committed bundle
matches that source build. It fails against the previous bundle and passes after
regeneration. `:third-party-remote-compose-player-dist:jsDistZip` is the actual
publication archive task; compare its `bundle.js` with the committed file.

Run the consumer's existing tests with `PLAYWRIGHT_MODULE` pointing at the installed
Playwright module and `RC_PLAYER_JS` pointing at each bundle:

```sh
node --test homeassistant-remotecompose/embed/test/message-inputs.test.mjs \
  homeassistant-remotecompose/home-assistant/test/message-inputs.test.mjs \
  homeassistant-remotecompose/home-assistant/test/frontend.test.mjs \
  homeassistant-remotecompose/home-assistant/test/card-config.test.mjs
```

Published 2.16.0: 10 pass, message-card test fails. Regenerated distribution:
all 11 pass. The corrected JS artifact requires a new release; published 2.16.0
cannot be overwritten. Consumers can retain Kotlin 2.16.0 while updating their
separate JS distribution pin after publication.
