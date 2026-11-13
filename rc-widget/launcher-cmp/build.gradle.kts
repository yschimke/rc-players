plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  id("org.jetbrains.kotlin.plugin.compose")
}

// `:rc-widget-launcher-cmp` — Android launcher widgets authored through rc-players' own Compose
// Multiplatform creation API (`ee.schimke.composeai.rccreation`, via `:rc-widget-creation`): the
// capture entry point `captureLauncherWidget`, which captures on the device without a `Context`,
// and `launcherCaptureProfile`. Everything after the capture is `:rc-widget-launcher-core`, shared
// with `:rc-widget-launcher` (AndroidX's creation API), so the same widget comes out whichever API
// wrote it. It has no `androidx.compose.remote.creation*` of its own. See
// docs/design/LAUNCHER_EITHER_API.md.

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  android {
    namespace = "ee.schimke.composeai.rcwidget.launcher.cmp"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
    withHostTest { isIncludeAndroidResources = true }
  }

  sourceSets {
    androidMain.dependencies {
      api(project(":rc-widget-launcher-core"))
      api(project(":rc-widget-creation"))
      implementation(libs.kotlinx.coroutines.core)
    }
    getByName("androidHostTest").dependencies {
      implementation(kotlin("test"))
      implementation(libs.junit)
      implementation(libs.robolectric)
    }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }

apply(from = rootProject.file("gradle/robolectric-platforms.gradle.kts"))

// What a consumer of this module must not be made to take along: see the script.
extra["forbidCreationApi"] = "androidx"

apply(from = rootProject.file("gradle/creation-isolation.gradle.kts"))

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-widget-launcher-cmp",
    displayName = "Remote Compose Widgets — Android Launcher (Compose Multiplatform creation)",
    description =
      "Android launcher widgets authored through rc-players' Compose Multiplatform creation API: a local capture that needs no Context, with embedded images and numeric host actions, over rc-widget-launcher-core. No AndroidX creation library.",
  )
}
