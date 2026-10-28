// rc-players' own creation API, layered over the creation fork in its own package. Everything
// rc-players adds to creation lives here; the fork carries only the seams this needs. See
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
  }
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  android {
    namespace = "ee.schimke.composeai.rccreation.bridge"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
  }

  sourceSets {
    commonTest.dependencies { implementation(kotlin("test")) }
    getByName("jvmTest").dependencies {
      implementation(libs.kotlinx.coroutines.core)
      // AndroidX alpha22: its writer for byte-equality tests of what the fork writes, and its
      // reader (remote-core) for read-back tests.
      implementation(libs.compose.remote.creation.core)
    }
    commonMain.dependencies {
      api(project(":third-party-remote-creation-compose"))
      api(project(":rc-player-protocol"))
      @Suppress("DEPRECATION") api(compose.runtime)
      @Suppress("DEPRECATION") api(compose.ui)
    }
  }
}

// The ABI gate, as the player modules have it: a change to the published surface shows up as a
// diff to `api/` in review. Regenerate with `./gradlew updateKotlinAbi`.
tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-creation-bridge",
    displayName = "Remote Compose Creation — Host Bridge",
    description =
      "RcHostBridge: one creation-side contract for host-owned values, actions and Compose content, written into a document or fed straight to an in-process player. Experimental.",
  )
}
