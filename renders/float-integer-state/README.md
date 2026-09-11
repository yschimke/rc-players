# Float results used as integer expression inputs

Creation-compose Float equality emits a Float expression followed by an integer expression reading
its result by ID. AndroidX `RemoteComposeState.updateFloat` publishes both numeric views; the CMP
player previously published only the Float value, so the integer reader observed zero.

The 100 × 100 fixture declares `page=1.25`, compares against 1.25 and 2.5, and selects red, green or
blue fallback. The [compiled document](ordinary.rc) uses ordinary AndroidX FloatExpression,
IntegerExpression and StateLayout operations.

[Before: wrong fallback](before.png) · [After: initial case](ordinary-0.png) ·
[Host update to 2.5](ordinary-1.png) · [Fallback at 3.75](ordinary-2.png).

Captures come from `FloatSelectionJsonTest` in compose-preview-server's
`experiments/remote-compose-poc`, using the local player composite. The baseline is `7b25f43`.
The same probe checks adjacent Float values, subnormals, opposite extremes, repeated host updates
and a direct comparison with AndroidX alpha19 `RemoteComposeState`.

The runtime regression tests here pin Float-to-integer conversion, preservation of the original
Float and exact integer values, named writes/restoration, click actions and computed results.
All 120 runtime tests, 216 Compose tests, the runtime ABI gate and WASM compilation pass.
