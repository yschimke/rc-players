plugins {
  id("composeai.base-conventions")
  alias(libs.plugins.kotlin.jvm)
  id("composeai.maven-publishing")
}

// The vendored TypeScript player's committed bundle, as a Maven artifact.
//
// Same wrapper pattern, and the same reason, as `:rc-player-wasm-dist` next door: a consumer in
// another repository needs these bytes and Gradle is how it resolves things. compose-ai-tools'
// `rc-*` browser tests and its design-artifacts lane point `--player` at
// `third_party/remote-compose-player/dist/bundle.js`; before the players moved that was a path in
// the same checkout, and now it has to be a coordinate.
//
// Nothing is built here. `dist/bundle.js` is committed upstream-built output — the vendored player
// has no Node build in this repo, deliberately (`BUILDING.md` records how to regenerate it against
// the upstream checkout). This module only packages what is already in the tree.
//
// Not an npm publish. The upstream project is where a JS consumer should get this player from; what
// this coordinate exists for is the one Gradle consumer that stages it as a static asset.

val jsDistZip =
  tasks.register<Zip>("jsDistZip") {
    description = "Packages the vendored TypeScript player bundle for publication."
    group = "distribution"
    // A sibling directory, not a sibling *project*: `remote-compose-player/` has no build file —
    // there is nothing to compile — so this module reaches it by path.
    from(layout.projectDirectory.dir("../remote-compose-player/dist"))
    archiveBaseName.set("remote-compose-player-js")
    archiveClassifier.set("dist")
  }

configurations.consumable("jsDistElements") {
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
  }
}

artifacts { add("jsDistElements", jsDistZip) }

(components["java"] as AdhocComponentWithVariants).addVariantsFromConfiguration(
  configurations["jsDistElements"]
) {
  mapToMavenScope("runtime")
}

composeAiMavenPublishing {
  coordinates(
    artifactId = "remote-compose-player-js-dist",
    displayName = "Remote Compose Player — vendored TypeScript player (browser bundle)",
    description =
      "This repository's vendored build of the TypeScript Remote Compose player: parses an .rc document and paints it to Canvas2D, with a WebGL path for shader operations. Published so tooling can stage it as a static asset; upstream is yschimke/remotecompose-experiments. Not a supported API.",
  )
}
