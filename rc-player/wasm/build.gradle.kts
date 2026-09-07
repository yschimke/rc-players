plugins {
  id("composeai.base-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  alias(libs.plugins.compose.multiplatform)
  id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    outputModuleName.set("rcPlayer")
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":rc-player-compose"))
      @Suppress("DEPRECATION") implementation(compose.runtime)
      @Suppress("DEPRECATION") implementation(compose.foundation)
      @Suppress("DEPRECATION") implementation(compose.ui)
      implementation(libs.kotlinx.coroutines.core)
    }
  }
}

tasks.register<Sync>("wasmPlayerDist") {
  description = "Assemble and size-check the optimized CMP Remote Compose Wasm distribution."
  group = "distribution"
  dependsOn("wasmJsProductionExecutableCompileSync", "processSkikoRuntimeForKWasm")
  from(layout.buildDirectory.dir("compileSync/wasmJs/main/productionExecutable/kotlin")) {
    exclude("*.map", "custom-formatters.js")
  }
  from(layout.buildDirectory.dir("compose/skiko-runtime-processed-wasmjs")) {
    include("skiko.mjs", "skiko.wasm")
  }
  from(layout.projectDirectory.dir("src/wasmJsMain/resources")) { include("index.html") }
  // The font faces and the js-joda ESM shim used to be read out of `:samples:cmp-wasm-catalog` in
  // compose-ai-tools, which is where they were first vendored. They live here now — this lane is
  // manifest-only and never fetches, so a named family the distribution doesn't carry fails
  // `RcComposeSupport.fontFamilyIssue`'s availability check outright, and reaching across a repo
  // boundary for a load-bearing payload is not something the extraction could keep. Licences ride
  // with the faces (`fonts/*-OFL.txt`, `fonts/LICENSE.txt`).
  from(layout.projectDirectory.dir("dist-assets/fonts")) { into("fonts") }
  from(layout.projectDirectory.file("dist-assets/js-joda.esm.js"))
  into(layout.buildDirectory.dir("wasmDist"))
  // Ratchet, not a target: it exists to make an unintended size jump fail the build, so it should
  // only move when the growth is understood. Raised from 23_000_000 for the Compose Multiplatform
  // 1.10.3 -> 1.11.1 bump (#3447), which took the measured distribution to 23_236_608 bytes — about
  // +237 KB, ~1%. Nearly all of it is `skiko.wasm`: that bump crosses skiko 0.9.37.4 -> 0.144.6, a
  // large Skia jump (it is the release that added `org.jetbrains.skia.PathBuilder`), so a bigger
  // binary is expected rather than a leak. The value keeps roughly the same slack the old one did.
  //
  // Raised again from 23_500_000 for the Google Sans Flex face vendored into
  // `:samples:cmp-wasm-catalog`'s fonts manifest, which this task copies wholesale (see the `from`
  // above — the budget counts the fonts dir, not just the binary). The two weights are 256_316
  // bytes and took the measured distribution to 23_513_207, 13_207 over. It is a deliberate
  // payload, not drift: this lane is manifest-only and never fetches, so a named family it doesn't
  // carry fails `RcComposeSupport.fontFamilyIssue`'s availability check outright — without the
  // face, the `remote-m3` catalog's (yschimke/wear-m3-catalog) Google Sans Flex typeface theme is
  // unrenderable
  // here while the other four lanes resolve it. Slack is kept at roughly the ~257 KB the previous
  // value had, so an unintended jump still fails.
  //
  // Raised again from 23_500_000 -> 24_600_000 for the `StateLayout` / `FitBox` transitions, which
  // put `compose.animation` on the player's compile classpath for `SharedTransitionLayout` and
  // `AnimatedContent`. Measured on the same runner, `origin/main` against the branch: the
  // distribution goes 23_639_257 -> 24_361_708 bytes, and every one of those 722_451 bytes is
  // `rcPlayer.wasm` (10_091_434 -> 10_813_885) — nothing else in the payload moved, so this is the
  // linked animation code and not drift somewhere else. It is what the feature costs: dead-code
  // elimination means only the shared-transition and animated-content machinery the player actually
  // calls is in there. Slack is kept at ~238 KB, roughly what the previous value carried, so an
  // unintended jump still fails.
  //
  // Raised again from 24_600_000 -> 25_280_000 for the Inter faces vendored into the fonts
  // manifest (#58), for the same reason Google Sans Flex was: this lane is manifest-only and never
  // fetches, so a named family it does not carry cannot be drawn — four of the `remote-m3`
  // catalog's typeface themes name `google:Inter`, and the CMP player's own
  // `composeSupportReport` reported it as the single capability gap across all 478 documents.
  // Measured on the runner, the two weights are 651_288 bytes and took the distribution to
  // 25_035_432. A deliberate payload, not drift: nothing but the fonts directory moved.
  //
  // Inter is the heaviest named family here at ~326 KB a weight, because Google Fonts serves the
  // full charset (Latin, Latin-ext, Cyrillic, Greek, Vietnamese) and these specimens only set
  // Latin. Subsetting would cut most of it, and is worth doing if this payload ever needs to come
  // back down — but every other face here is vendored exactly as the CSS2 endpoint serves it, and
  // silently shipping a processed one would break that and the reproducibility the README
  // documents. Slack is kept at ~245 KB, in line with the previous values.
  inputs.property(
    "maximumDistributionBytes",
    providers.gradleProperty("rcPlayerWasmMaxBytes").orElse("25280000"),
  )
  doLast {
    val maximumBytes = inputs.properties.getValue("maximumDistributionBytes").toString().toLong()
    val distribution = destinationDir
    val actualBytes = distribution.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    check(actualBytes <= maximumBytes) {
      "CMP Remote Compose Wasm distribution is $actualBytes bytes; budget is $maximumBytes bytes " +
        "(override deliberately with -PrcPlayerWasmMaxBytes=<bytes>)"
    }
    logger.lifecycle("CMP Remote Compose Wasm distribution: $actualBytes / $maximumBytes bytes")
  }
}

tasks.register<Sync>("wasmPlayerTestDist") {
  description = "Assemble the Wasm player with an AndroidX-generated browser smoke fixture."
  group = "verification"
  dependsOn(
    "wasmPlayerDist",
    ":rc-player-compat-tests:generateBaselineFixture",
    ":rc-player-compat-tests:generateComponentValueFixture",
    ":rc-player-compat-tests:generateLayoutFixture",
    ":rc-player-compat-tests:generateScrollFixture",
  )
  from(layout.buildDirectory.dir("wasmDist"))
  from(
    project(":rc-player-compat-tests").layout.buildDirectory.file("fixtures/androidx-baseline.rc")
  )
  from(
    project(":rc-player-compat-tests")
      .layout
      .buildDirectory
      .file("fixtures/androidx-component-value.rc")
  )
  from(project(":rc-player-compat-tests").layout.buildDirectory.file("fixtures/androidx-layout.rc"))
  from(project(":rc-player-compat-tests").layout.buildDirectory.file("fixtures/androidx-scroll.rc"))
  from(
    project(":rc-player-compose")
      .layout
      .projectDirectory
      .file("src/jvmTest/resources/rc-fixtures/ImageBackgroundRemoteButton-454x200.rc")
  )
  into(layout.buildDirectory.dir("wasmTestDist"))
}

// npm package staging (#4067).
//
// `wasmPlayerDist` produces the browser bundle; this lays it out the way npm expects — package
// metadata and README at the root, the bundle under `dist/` — into a directory the release workflow
// runs `npm publish` from. **No Node in the Gradle build**, deliberately: the CLI vendors a
// prebuilt JS player for exactly that reason (docs/design/RC_CMP_WASM_PLAYER.md), and a Sync task
// is all this needs. `npm` only ever runs in CI, on a directory that is already assembled.
//
// The committed `version` is the `0.0.0` placeholder the `design-map` package uses; the release job
// sets it from the tag. The package *major* tracks the embed contract's version, which is a
// different number and lives in `Main.kt` — see docs/design/RC_PLAYER_EMBED.md.
tasks.register<Sync>("rcPlayerNpmPackage") {
  description = "Stage the Wasm player bundle as an npm package directory."
  group = "distribution"
  dependsOn("wasmPlayerDist")
  from(layout.projectDirectory.dir("npm"))
  from(layout.buildDirectory.dir("wasmDist")) { into("dist") }
  into(layout.buildDirectory.dir("npmPackage"))
}
