/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// `:third-party-horologist-lottie` — a vendored snapshot of Horologist's
// `remotecompose/lottie` module: a Lottie **compiler**, not a Lottie player. It parses a Lottie
// JSON animation and re-emits it as Remote Compose creation-side operations — shapes, paths,
// gradients and transforms whose keyframes are expressions over the document's animation clock —
// so the resulting `.rc` document animates in any player that can read one, with no Lottie runtime
// on the device at all. See `PROVENANCE.md` for the pinned upstream commit and our local deltas.
//
// Why it is vendored rather than depended on: Horologist publishes no artifact for this module
// yet. `ee.schimke.composeai.uibuilder`'s Wear `LottiePlayer` element exports Kotlin that calls
// `LottieAnimation(json = …)`, and the emitted source has to compile against something. Vendoring
// it here — beside the other pinned upstream sources this repository measures itself against —
// keeps that promise checkable in CI at a known revision instead of against whichever Horologist
// snapshot happened to resolve.
//
// Android-library, like upstream: the creation API (`androidx.compose.remote.creation.*`) is
// Android-only, so this is not, and cannot yet be, part of the KMP player stack in `rc-player/`.
// That is what "temporarily" means in the vendoring — see PROVENANCE.md.

plugins {
  id("composeai.base-conventions")
  // Published for the same reason `:third-party-rc-embedded-player` is, and under the same terms:
  // a vendored snapshot, not a supported API. See `composeAiMavenPublishing` below.
  id("composeai.maven-publishing")
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Published so a consumer in ANOTHER repository can compile against the compiler, which vendoring
// alone does not allow.
//
// The need is concrete. yschimke/wear-m3-catalog's `:remote-catalog` has no Lottie sticker, so the
// published `remote-m3` catalog cannot offer `remote-m3/lottie` at all while the synthesised shelf
// does — the last component the cutover loses (compose-preview-server#674). Drawing that sticker
// means calling `LottieAnimation(json = …)`, and there is nothing to call: Horologist ships no
// artifact, and this copy was reachable only from inside this build.
//
// The alternative was a second vendored copy in that repository, which is worse in the way two
// pinned snapshots of the same upstream are always worse — they drift, and the PROVENANCE.md that
// says which commit is authoritative stops being able to answer. One copy, one pin, published.
//
// Deliberately under the compose-ai-tools group rather than `com.google.android.horologist.*`:
// this is our snapshot at our pinned commit, and must never be mistaken for an artifact Horologist
// released. If upstream ever publishes the module, a consumer moves to it and this coordinate goes
// away — PROVENANCE.md is where that is tracked.
composeAiMavenPublishing {
  coordinates(
    artifactId = "third-party-horologist-lottie",
    displayName = "Compose Preview — Horologist Lottie → Remote Compose compiler (vendored)",
    description =
      "Vendored snapshot of Horologist's `remotecompose/lottie`: a Lottie COMPILER that re-emits " +
        "an animation as Remote Compose creation operations, so the animation ships inside the " +
        "`.rc` document and needs no Lottie runtime on the device. Published so catalogs in " +
        "other repositories can draw and export one; not a supported API, and not an artifact " +
        "Horologist released.",
  )
  inceptionYear.set("2026")
}

android {
  // NOT upstream's `com.google.android.horologist.remotecompose.lottie`: the sources keep their
  // package (so a diff against a newer Horologist checkout is a plain `diff -r`), but the
  // manifest namespace is ours, so the vendored copy can never be mistaken for the published
  // Horologist library if one appears.
  namespace = "ee.schimke.composeai.horologist.lottie"

  // The alpha `compose-remote` AARs declare `minCompileSdk = 37`, as
  // `:third-party-rc-embedded-player`
  // notes.
  compileSdk = 37

  defaultConfig {
    // Upstream's floor. Nothing here reaches an API newer than that.
    minSdk = 26
    aarMetadata { minCompileSdk = 36 }
  }

  buildFeatures { compose = true }

  // The renderer reaches `androidx.compose.remote.creation.*` members marked
  // `@RestrictTo(LIBRARY_GROUP)` — unavoidable for an out-of-tree copy of in-tree code, and
  // upstream suppresses the same lint at the call sites.
  lint { disable += "RestrictedApi" }
}

dependencies {
  // The creation API this module writes through: `RemoteBox`, `RemoteCanvas`, `RemoteModifier`,
  // `RemoteFloat` and the expression builders that turn Lottie keyframes into document operations.
  api(libs.compose.remote.creation)
  api(libs.compose.remote.creation.compose)

  implementation(platform(libs.compose.bom.compat))
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui)
  // `CubicBezierEasing`, for keyframe easing.
  implementation(libs.compose.animation.core)
  // `MathUtils.clamp` on the scalar path.
  implementation(libs.androidx.core)
  implementation(libs.kotlinx.serialization.json)
}
