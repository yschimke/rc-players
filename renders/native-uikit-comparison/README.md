# Native UIKit comparison evidence

The `cmp/` and `uikit/` directories contain the same five manifest-driven documents rendered by
CMP JVM and the packaged native UIKit player. `comparison.json` is the corresponding pixel score.

The focused `before/` and `after/` images record the native progress regression fixed with this
lane: the original UIKit snapshots were transparent because root component geometry and content
colours were unresolved; the corrected snapshots contain the determinate ring and arc.

Regenerate the complete evidence set on macOS with:

```bash
scripts/check-native-uikit-comparison.sh
```
