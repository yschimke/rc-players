plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  id("org.jetbrains.kotlin.plugin.compose")
}

// `:rc-widget-wear` — Wear OS widgets drawn by Remote Compose through Glance Wear: a template
// captured once on the device with its container frame left to the watch, instantiated for each
// widget from the `WearWidgetParams` its host hands it (Glance Wear's own header, the watch's
// density, the Wear widget profile's rewrites), taps that open the app, the host-framed document a
// design tool exports, the frame a watch last reported, and the provider-configuration contract.
// See docs/design/RC_WIDGETS.md.

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  android {
    namespace = "ee.schimke.composeai.rcwidget.wear"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
    withHostTest { isIncludeAndroidResources = true }
  }

  sourceSets {
    androidMain.dependencies {
      api(project(":rc-widget-creation"))
      api(libs.widgets.remote.core)
      api(libs.widgets.remote.creation)
      api(libs.widgets.remote.creation.core)
      api(libs.widgets.remote.creation.compose)
      api(libs.glance.wear)
      api(libs.glance.wear.core)
      implementation(libs.compose.runtime)
      implementation(libs.compose.ui)
      implementation(libs.compose.foundation)
      implementation(libs.kotlinx.coroutines.core)
    }
    getByName("androidHostTest").dependencies {
      implementation(kotlin("test"))
      implementation(libs.junit)
      implementation(libs.robolectric)
    }
  }
}

// The fork `:rc-widget-creation` compiles against declares AndroidX's capabilities; on Android take
// AndroidX's own.
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
    artifactId = "rc-widget-wear",
    displayName = "Remote Compose Widgets — Wear OS",
    description =
      "Wear OS widgets drawn by Remote Compose through Glance Wear: host-framed templates captured on the device and instantiated per widget and watch, taps that open the app, and the provider-configuration contract.",
  )
}
