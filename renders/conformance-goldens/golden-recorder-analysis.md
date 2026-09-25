# Conformance golden recorder analysis — 2026-09-19

Which player should record the corpus's per-step rasters? The 2026-09-18 regeneration (Gerrit
[4305834](https://android-review.googlesource.com/c/platform/frameworks/support/+/4305834), patch
set 6) recorded them with `UPDATE_GOLDENS=true`, which renders through the **View player**
(`remote-player-view`). Under Robolectric NATIVE that player does not converge on a host resize:
`RemoteComposeView.onMeasure` sizes itself to `mDocument.getWidth()/getHeight()`, so a `resize`
step keeps painting the *initial* document size and the newly exposed area stays white.

## The three probes

Same harness arrangement (Box with `requiredSize`, player inside, 200 → 400), Robolectric NATIVE:

| recorded frame | compared against | pixels differing |
| --- | --- | ---: |
| View player, **resized live** | CL3 gold | **639** |
| View player, **built at 400** | old gold | **0** |
| **embedded player, resized live** | old gold | **0** |

So the CL3 resize frames are the View player's stale-size output; a View player constructed at the
target viewport — and the embedded player resized live — both reproduce the old gold exactly.

## Whole-corpus agreement

633 raster steps; agreement = fraction of pixels within 2 levels, alpha composited onto white.

| pair | initial | **resize** | frame | other |
| --- | ---: | ---: | ---: | ---: |
| cmp vs axjvm | 0.912 | 0.908 | – | 0.999 |
| cmp vs typescript | 0.947 | 0.971 | 0.895 | 0.871 |
| axjvm vs typescript | 0.886 | 0.891 | – | 0.995 |
| cmp vs old gold | 0.971 | 0.980 | 0.941 | 0.817 |
| axjvm vs old gold | 0.910 | 0.898 | – | 0.999 |
| typescript vs old gold | 0.959 | 0.974 | 0.913 | 0.795 |
| old gold vs **View-native (CL3)** | 0.991 | **0.643** | 0.908 | 0.927 |
| cmp vs **View-native (CL3)** | 0.971 | **0.633** | 0.873 | 0.833 |
| axjvm vs **View-native (CL3)** | 0.908 | **0.564** | – | 0.999 |
| typescript vs **View-native (CL3)** | 0.959 | **0.631** | 0.940 | 0.789 |

Every independent rendering — CMP, the embedded player, TypeScript, the old software-recorded
golds — agrees with every other on resize at **0.89–0.98**. The View-native recording is the only
outlier, at **0.56–0.64**. On initial frames all of them, CL3 included, agree at 0.96–0.99: the
native re-record is fine there. Animation frames span 0.87–0.94 with no outlier — mid-flight
captures differ by timing and cannot crown anyone.

## The independent reference

The corpus's `tree` checks come from the reference engine, not from any player. At resize steps:

| player | resize trees matched |
| --- | ---: |
| cmp | **287 / 316 = 90.8%** |
| vendored AndroidX player, JVM cut (lane removed 2026-09-25) | 279 / 316 = 88.3% |

Both players lay out resized documents essentially as the reference expects — the reference itself
asserts the resized geometry, which is exactly what the CL3 resize rasters contradict.

## Recommendation

1. **Record with the embedded AndroidX player** (`UPDATE_GOLDENS=embedded`): it is AndroidX's own
   implementation, agrees with the consensus on resize, and matches the reference trees. It keeps
   the CMP lane honestly judged rather than self-fulfilling. Patch:
   [`harness-embedded-recorder.patch`](harness-embedded-recorder.patch).
2. If the embedded lane's own raster checks must stay independent of its recorder, extend the
   reference gold generator to record the per-step rasters with its headless context — the engine
   that produced the trees. Player-independent, and the architecturally clean answer.
3. If the View player must remain the recorder, it has to be re-created per viewport rather than
   resized in place: [`harness-resize-fix.patch`](harness-resize-fix.patch). Note this means the
   harness never exercises a live resize on the reference, which is the thing the resize steps
   exist to test.

Do not record with CMP: it agrees most by raw pixels (0.971 / 0.980 / 0.941), but it is the subject
of this repository's reports — recording with it would make the subject lane pass trivially and
leave the reference lane as the one being judged.

Keep the CL3 **initial** frames: they fixed genuinely stale software baselines (for example
`clock_digital_calendar` dropped from 18,808 to 1,486 differing pixels, `text_anchored_pan_alignment`
from 13,536 to 1,186) and agree with every implementation.
