// `:rc-creation-writer`: the common write core under the creation modules, `RemoteWriter` and the
// `.rc` byte writer, in the package `ee.schimke.composeai.rccreation.common`. The Compose
// Multiplatform port's own code, standing where AndroidX has `remote-core` and
// `remote-creation-core`; see ../remote-creation-compose/PROVENANCE.md.
plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
}

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  android {
    namespace = "ee.schimke.composeai.rccreation.writer"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
  }

  sourceSets {
    // AndroidX's operation tables, by API level and profile (`RcProfileOperations`): what a
    // `Profile` lets its writer write is what `:rc-player-edit` checks a document against.
    commonMain.dependencies { implementation(project(":rc-player-protocol")) }
    commonTest.dependencies { implementation(kotlin("test")) }
  }
}

// The ABI gate, as the player modules have it: a change to the published surface shows up as a
// diff to `api/` in review. Regenerate with `./gradlew updateKotlinAbi`.
tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-creation-writer",
    displayName = "Remote Compose Creation — Common Writer",
    description =
      "The common RemoteWriter and .rc byte writer under rc-players' Compose Multiplatform Remote Compose creation modules, in the package ee.schimke.composeai.rccreation.common.",
  )
}
