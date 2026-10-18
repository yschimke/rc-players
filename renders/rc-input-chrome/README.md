# Author-owned input chrome

Actual browser renders of the shared Remote Compose message component, inside the same authored
label, border and tinted container. `default.png` keeps the generic editor chrome; `none.png` uses
CHROME=1 with the same accessible label, editable value, multiline keyboard behavior and Send action.
No service is contacted. Reproduce with `scripts/rc-input-message/capture-chrome.mjs` and the bundled
player (`RC_PLAYER_JS`, `PLAYWRIGHT_MODULE`).
