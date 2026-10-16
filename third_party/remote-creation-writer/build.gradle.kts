// The common write core under the creation fork: `RemoteWriter` and the `.rc` byte writer. The
// Compose Multiplatform port's own code; see ../remote-creation-compose/PROVENANCE.md.
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

  sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}

// The ABI gate, as the player modules have it: a change to the published surface shows up as a
// diff to `api/` in review. Regenerate with `./gradlew updateKotlinAbi`.
tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
  coordinates(
    artifactId = "third-party-remote-creation-writer",
    displayName = "Remote Compose Creation — Common Writer (fork)",
    description =
      "The common RemoteWriter and .rc byte writer under rc-players' Compose Multiplatform fork of AndroidX Remote Compose creation.",
  )
}
