plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  alias(libs.plugins.compose.multiplatform)
  id("org.jetbrains.kotlin.plugin.compose")
}

// `:rc-widget-creation` — creation helpers for documents served to widgets: reproducible expression
// names, size breakpoints a launcher capture resolves and a player evaluates, numeric widget host
// actions, and the Wear widget container whose frame is left to the watch (`RcWearContainer`'s
// names). Written once against AndroidX's creation API, the way component libraries are: on Android
// it compiles against AndroidX's own `remote-creation-compose` (the stack a launcher or Glance Wear
// capture uses), everywhere else against rc-players' Compose Multiplatform fork of it, so a design
// server on the JVM captures the same template a phone does. See docs/design/RC_WIDGETS.md.

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  android {
    namespace = "ee.schimke.composeai.rcwidget.creation"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
  }

  // Every target but Android captures with the fork.
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    common {
      group("fork") {
        withJvm()
        withWasmJs()
        withIosArm64()
        withIosSimulatorArm64()
        withMacosArm64()
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":rc-widget-core"))
      // The creation API is AndroidX's in both cases; which library provides it is per target.
      compileOnly(project(":third-party-remote-creation-compose"))
      @Suppress("DEPRECATION") api(compose.runtime)
      @Suppress("DEPRECATION") api(compose.ui)
    }
    androidMain.dependencies {
      api(libs.compose.remote.creation.compose)
      api(libs.compose.remote.foundation)
    }
    getByName("forkMain").dependencies { api(project(":third-party-remote-creation-compose")) }
    commonTest.dependencies { implementation(kotlin("test")) }
  }
}

// The fork declares AndroidX's capabilities, so on Android, where `commonMain`'s compile-only fork
// meets AndroidX's real library, take AndroidX's.
configurations.configureEach {
  resolutionStrategy.capabilitiesResolution {
    listOf(
        "androidx.compose.remote:remote-creation-compose",
        "androidx.compose.remote.foundation:foundation",
      )
      .forEach { capability ->
        withCapability(capability) {
          candidates
            .firstOrNull {
              (it.id as? ModuleComponentIdentifier)?.group?.startsWith("androidx.") == true
            }
            ?.let { select(it) }
        }
      }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-widget-creation",
    displayName = "Remote Compose Widgets — Creation",
    description =
      "Creation helpers for Remote Compose widgets: reproducible expression names, size breakpoints, numeric widget host actions and the host-framed Wear widget container. AndroidX's creation API on Android, the Compose Multiplatform fork elsewhere.",
  )
}
