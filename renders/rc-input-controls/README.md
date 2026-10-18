# Input controls contract v1

Actual 640 × 900 Chromium renders of the same `input-controls.rc` through the previous player
(`151d059`) and the expanded player. No HA connection, credentials or real domain actions.

- `before.png`: two-config player, with host capability checking and an update/open-app alternative.
- `after.png`: text, choice, boolean checkbox, decimal numeric entry and bounded slider. The checkbox
  is checked and temperature edited locally; these changes traverse declared TEXT_RETURN channels.

Reproduce with `scripts/rc-input-message/capture-controls.mjs`; set `PLAYWRIGHT_MODULE`,
`RC_PLAYER_JS` (current bundle) and `RC_PLAYER_BASELINE_JS` (151d059 bundle). The script also writes
an identical native Swift test fixture. Shared validation vectors live beside that fixture, consumed
by Kotlin, Swift and TS tests. The browser regression harness checks change/submit values, numeric
validation, range arrow steps, disabled/read-only controls and lifecycle. Platform UI tests cover CMP;
UIKit/AppKit compilation and physical keyboard behavior require platform CI/device checks.
