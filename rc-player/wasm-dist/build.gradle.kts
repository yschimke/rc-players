plugins {
  id("composeai.base-conventions")
  alias(libs.plugins.kotlin.jvm)
  id("composeai.maven-publishing")
}

// The Wasm player distribution, as a Maven artifact.
//
// `:rc-player-wasm:wasmPlayerDist` produces the browser bundle as a *directory*, which is exactly
// what a consumer wants to drop into a static sidecar — and exactly what Maven cannot carry. This
// module is the wrapper that makes it resolvable: one zip, one coordinate, resolved by Gradle like
// anything else.
//
// It exists because of a real consumer. compose-ai-tools' `compose-preview` CLI ships this bundle
// in its install dist (`rc-player-wasm/`) so every CLI and container image can serve the player
// without a source checkout. Before the player stack moved into this repository that was a
// `files(project(":rc-player-wasm")...)` dependency; across a repository boundary it has to be a
// published artifact.
//
// The npm package (`:rc-player-wasm:rcPlayerNpmPackage`) is the same bytes for a web consumer.
// Neither replaces the other: npm is how a page consumes it, Maven is how a Gradle build does, and
// publishing to only one would strand the other. Both are cut from `wasmPlayerDist`, so the size
// ratchet in that task gates both.
//
// A separate module rather than a second publication on `:rc-player-wasm`: that project is a Kotlin
// Multiplatform build whose publication is a wasmJs klib, and attaching an unrelated zip to it
// would put a browser bundle inside KMP module metadata that resolves per-target. This module has
// no Kotlin sources at all — the empty jar is the price of a conventional POM, and the zip is the
// artifact anyone actually wants.

val wasmDistZip =
  tasks.register<Zip>("wasmDistZip") {
    description = "Packages the Wasm player distribution for publication."
    group = "distribution"
    dependsOn(":rc-player-wasm:wasmPlayerDist")
    from(project(":rc-player-wasm").layout.buildDirectory.dir("wasmDist"))
    archiveBaseName.set("rc-player-wasm-dist")
    archiveClassifier.set("dist")
  }

// `artifacts { archives(...) }` is not enough — vanniktech builds the publication from the
// `java` component, so the zip has to be attached to that component to reach the POM and be
// resolvable as `ee.schimke.composeai:rc-player-wasm-dist:<version>:dist@zip`.
configurations.consumable("wasmDistElements") {
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
  }
}

artifacts { add("wasmDistElements", wasmDistZip) }

(components["java"] as AdhocComponentWithVariants).addVariantsFromConfiguration(
  configurations["wasmDistElements"]
) {
  mapToMavenScope("runtime")
}

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-player-wasm-dist",
    displayName = "Remote Compose Player — Wasm distribution",
    description =
      "The Compose Multiplatform / Wasm Remote Compose player as a browser bundle, zipped for Gradle consumers that stage it as a static sidecar. The same bytes ship to npm as @yschimke/remote-compose-player-cmp for web consumers.",
  )
}
