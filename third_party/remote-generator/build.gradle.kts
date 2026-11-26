plugins {
    id("composeai.base-conventions")
    id("composeai.jvm-conventions")
    alias(libs.plugins.kotlin.jvm)
}

// `:third-party-remote-generator` — AndroidX's `compose/remote/remote-generator`, forked. The
// sources under `src/main` are upstream's at the commit in PROVENANCE.md, at their upstream paths,
// with every local change marked `LOCAL PATCH (rc-players)`. Upstream's own build file, README and
// API dumps are kept under `upstream/` for reference; this file replaces the build file.
//
// Build-only: nothing here publishes. rc-players' targets live in `:rc-codegen`, which depends on
// this module and generates the committed Swift sources.

dependencies {
    api(libs.remote.generator.jspecify)
    api(libs.remote.generator.annotation)
    implementation(libs.remote.generator.json)
    implementation(libs.remote.generator.javapoet)
    implementation(libs.remote.generator.kotlinpoet)
}

// AndroidX formats with ktfmt's kotlinlang style (4-space), and these sources are a direct copy of
// AndroidX's. Unlike the creation fork, they are not run through this repository's ktfmt at all:
// its version re-wraps about 1,600 of upstream's lines, and keeping every unpatched file
// byte-identical makes a re-sync a plain copy and the local patches the only diff. The patches are
// written in upstream's style by hand.
ktfmt { kotlinLangStyle() }

tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask>().configureEach {
    exclude("androidx/compose/remote/generator/**")
}
