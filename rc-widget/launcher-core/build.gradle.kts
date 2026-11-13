plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  id("org.jetbrains.kotlin.plugin.compose")
}

// `:rc-widget-launcher-core` — what publishing a Remote Compose launcher widget needs that does not
// depend on which creation API wrote the document: the Android 16 compatibility pass over the final
// bytes (`platformWidgetDocument`), `RemoteViews` / `PendingIntent` assembly and the taps that come
// back (`RcPlatformWidgets`), the recovery for the platform's player (`forDisplay`), the bake and
// image embedding that finish a capture (`finishLauncherCapture`), launcher sizing, and the
// in-app resizable preview and adaptive player. It names neither creation API: no
// `androidx.compose.remote.creation*` and no `ee.schimke.composeai.rccreation`, so an app takes
// either capture entry point (`:rc-widget-launcher` for AndroidX's, `:rc-widget-launcher-cmp` for
// rc-players') without the other's creation libraries. See docs/design/LAUNCHER_EITHER_API.md.

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  android {
    namespace = "ee.schimke.composeai.rcwidget.launcher.core"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
    withHostTest { isIncludeAndroidResources = true }
  }

  sourceSets {
    androidMain.dependencies {
      api(project(":rc-widget-core"))
      // The platform's wire format and the in-app player: the runtime, not the creation API.
      api(libs.compose.remote.core)
      api(libs.compose.remote.player.core)
      api(libs.compose.remote.player.view)
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

tasks.named("check") { dependsOn("checkKotlinAbi") }

apply(from = rootProject.file("gradle/robolectric-platforms.gradle.kts"))

// What a consumer of this module must not be made to take along: see the script.
extra["forbidCreationApi"] = "both"

apply(from = rootProject.file("gradle/creation-isolation.gradle.kts"))

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-widget-launcher-core",
    displayName = "Remote Compose Widgets — Android Launcher (core)",
    description =
      "The creation-API-neutral half of Remote Compose launcher widgets on Android: the Android 16 compatibility pass over a captured document, RemoteViews and PendingIntent assembly, recovery for the platform player, launcher sizing and an in-app resizable preview. No Remote Compose creation library; pair with rc-widget-launcher (AndroidX's creation API) or rc-widget-launcher-cmp (rc-players').",
  )
}
