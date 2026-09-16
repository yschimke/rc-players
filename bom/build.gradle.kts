import ee.schimke.composeai.buildlogic.PublishedArtifactIds
import ee.schimke.composeai.buildlogic.PublishedVersions

plugins {
  id("composeai.maven-publishing-platform")
  // `base-conventions` is what gives this project its ktfmt task — `:bom:ktfmtFormat` /
  // `:bom:ktfmtCheck` — so the BOM is formatted and checked like every other project here.
  id("composeai.base-conventions")
}

// The BOM for every coordinate this repository publishes.
//
// The player stack is a stack: `compose` sits on `runtime` sits on `protocol` sits on `trace`, and
// they are released together on one version line. A consumer naming four versions by hand can get
// them out of step — and a mixed set is not a compile error, it is a `NoSuchMethodError` in
// somebody's app. Importing this platform names one coordinate instead:
//
//     implementation(platform("ee.schimke.composeai:rc-players-bom:<version>"))
//     implementation("ee.schimke.composeai:rc-player-compose")
//
// The constraints come from `PublishedArtifactIds`, the same table that decides what each module
// publishes as — so a module cannot be in the release and missing from the BOM. A BOM that omits a
// coordinate is worse than no BOM: a consumer trusting it gets no version for that module and a
// resolution failure with an empty version.
//
// The KMP modules are constrained at their base coordinate, which is the one a consumer names;
// Gradle module metadata resolves the `-android` / `-jvm` / `-wasm-js` / Apple variants from it.
//
// Each constraint takes that module's EFFECTIVE version, via the same `PublishedVersions.resolve`
// that sets `project.version` — so the versions the BOM promises and the versions the POMs name
// cannot disagree. With no publish set, everything resolves to the tag.
val publishSet =
  PublishedVersions.parsePublishSet(providers.gradleProperty("composeai.publishSet").orNull)

val manifestText =
  rootProject.layout.projectDirectory
    .file("publishing-manifest.json")
    .asFile
    .takeIf { it.isFile }
    ?.readText() ?: "{}"

dependencies {
  constraints {
    PublishedArtifactIds.byProjectPath.values.sorted().forEach { artifactId ->
      val version =
        PublishedVersions.resolve(
          artifactId = artifactId,
          tagVersion = project.version.toString(),
          publishSet = publishSet,
          manifestText = manifestText,
        )
      api("ee.schimke.composeai:$artifactId:$version")
    }
  }
}

composeAiPlatformPublishing {
  coordinates(
    artifactId = "rc-players-bom",
    displayName = "Remote Compose Players - Bill of Materials",
    description =
      "Version constraints for every artifact the Remote Compose players publish, so a consumer " +
        "aligns the player stack with one coordinate.",
  )
  inceptionYear.set("2026")
}
