// Joins creation (`rc-creation-bridge`) to the player (`rc-player-compose`): one remote composition
// that can write a document or feed a player in the same process. See
// docs/design/RC_CREATION_BRIDGE.md.
plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  alias(libs.plugins.compose.multiplatform)
  id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
  explicitApi()
  // The module's own experimental API is used freely inside it; callers opt in.
  sourceSets.all {
    languageSettings.optIn("ee.schimke.composeai.rccreation.bridge.ExperimentalRcCreationApi")
    languageSettings.optIn("ee.schimke.composeai.rcplayer.compose.ExperimentalRcPlayerApi")
  }
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  android {
    namespace = "ee.schimke.composeai.rcplayer.embedded"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":rc-creation-bridge"))
      api(project(":rc-player-compose"))
      @Suppress("DEPRECATION") api(compose.runtime)
      implementation(libs.kotlinx.coroutines.core)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
      @Suppress("DEPRECATION") implementation(compose.desktop.currentOs)
      implementation(libs.jetbrains.compose.ui.test)
    }
  }
}

// The ABI gate, as the player modules have it: a change to the published surface shows up as a
// diff to `api/` in review. Regenerate with `./gradlew updateKotlinAbi`.
tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-player-embedded",
    displayName = "Remote Compose Player — Embedded",
    description =
      "RcEmbeddedPlayer: Remote Compose content composed live in-process and played by RcComposePlayer, with host values, actions and Compose content bridged by name. Experimental.",
  )
}
