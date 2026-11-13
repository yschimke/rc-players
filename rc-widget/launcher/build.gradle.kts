plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  id("org.jetbrains.kotlin.plugin.compose")
}

// `:rc-widget-launcher` — Android launcher widgets drawn by Remote Compose, authored through
// AndroidX's own creation API: the AndroidX capture profiles (`widgetsProfile`,
// `systemThemedWidgetsProfile`), `captureLauncherWidget` for an AndroidX composable, and
// `RcLauncherWidgets`. It is the AndroidX-flavoured façade of `:rc-widget-launcher-core`, which
// holds everything that works on the captured bytes (the Android 16 compatibility pass,
// `RemoteViews` / `PendingIntent` assembly, sizing, the resizable preview, the adaptive player) and
// is the same for both creation APIs; `:rc-widget-launcher-cmp` is the same capture entry point on
// rc-players' Compose Multiplatform creation API. Existing consumers keep this artifact and its
// package. See docs/design/LAUNCHER_EITHER_API.md.

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  android {
    namespace = "ee.schimke.composeai.rcwidget.launcher"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
    withHostTest { isIncludeAndroidResources = true }
  }

  sourceSets {
    androidMain.dependencies {
      api(project(":rc-widget-launcher-core"))
      api(project(":rc-widget-creation-androidx"))
      api(libs.compose.remote.core)
      api(libs.compose.remote.creation)
      api(libs.compose.remote.creation.core)
      api(libs.compose.remote.creation.compose)
      implementation(libs.compose.runtime)
      implementation(libs.kotlinx.coroutines.core)
    }
    getByName("androidHostTest").dependencies {
      // rc-players' own capture profiles, to hold them to this module's AndroidX ones, and its
      // capture entry point, to hold a widget authored through it to one authored through
      // AndroidX's.
      implementation(project(":rc-widget-creation"))
      implementation(project(":rc-widget-launcher-cmp"))
      implementation(kotlin("test"))
      implementation(libs.junit)
      implementation(libs.robolectric)
    }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }

apply(from = rootProject.file("gradle/robolectric-platforms.gradle.kts"))

// What a consumer of this module must not be made to take along: see the script.
extra["forbidCreationApi"] = "cmp"

apply(from = rootProject.file("gradle/creation-isolation.gradle.kts"))

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-widget-launcher",
    displayName = "Remote Compose Widgets — Android Launcher",
    description =
      "Android launcher widgets drawn by Remote Compose, authored through AndroidX's creation API: the AndroidX capture profiles and a local capture with embedded images and numeric host actions, over rc-widget-launcher-core's Android 16 compatibility pass, RemoteViews and PendingIntent assembly, launcher sizing and in-app preview.",
  )
}
