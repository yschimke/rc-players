# Baseline profiles for the Android players

`rc-player-trace`, `rc-player-protocol`, `rc-player-runtime` and `rc-player-compose` each ship a
baseline profile in their AAR (`src/androidMain/generated/baselineProfiles/`), so an app that plays
documents gets the players' hot paths AOT-compiled at install without profiling them itself.

| Module | Role |
| --- | --- |
| `:rc-player-baselineprofile-host` | App that plays the catalog corpus (`scripts/rc-catalog-corpus/corpus/`) in one scrolling list, first document of each component kind first |
| `:rc-player-baselineprofile` | `com.android.test` module whose `CorpusBaselineProfileGenerator` flings that list end to end |

One collection feeds all four libraries: each applies `androidx.baselineprofile` with a `filter`
on its own package, and normal builds only package the checked-in file
(`automaticGenerationDuringBuild = false`).

## Generating

For now the profile comes from an emulator. `.github/workflows/baseline-profiles.yml` boots one
(API 34), runs the generator and uploads the four profiles as the `baseline-profiles` artifact;
commit its files over each library's `src/androidMain/generated/baselineProfiles/`. The workflow
does not commit: a push made with its token would land without CI.

Locally, with an emulator or device on API 33+ (or rooted) connected:

```sh
./gradlew :rc-player-trace:generateBaselineProfile :rc-player-protocol:generateBaselineProfile \
  :rc-player-runtime:generateBaselineProfile :rc-player-compose:generateBaselineProfile
```

The generator runs once; each task writes its library's filtered share.

or let Gradle boot one (`pixel6Api34`):

```sh
./gradlew <the same four tasks> -Pbaselineprofile.gmd=true
```
