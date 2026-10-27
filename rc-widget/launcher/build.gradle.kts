plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  id("org.jetbrains.kotlin.plugin.compose")
}

// `:rc-widget-launcher` — Android launcher widgets drawn by Remote Compose: the capture profiles a
// launcher's `RemoteViews.DrawInstructions` player reads, the Android 16 compatibility pass over
// the
// final document, a local capture that embeds its images and collects its numeric host actions,
// `RemoteViews` / `PendingIntent` assembly, launcher sizing, and the in-app resizable preview (the
// real `RemoteViews`, inflated at a chosen cell size) and adaptive player. Android only: it is
// built
// on AndroidX's own creation and player stack, the one the launcher's player is written for. See
// docs/design/RC_WIDGETS.md.

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
      api(project(":rc-widget-creation"))
      api(libs.widgets.remote.core)
      api(libs.widgets.remote.creation)
      api(libs.widgets.remote.creation.core)
      api(libs.widgets.remote.creation.compose)
      api(libs.widgets.remote.player.core)
      api(libs.widgets.remote.player.view)
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
    artifactId = "rc-widget-launcher",
    displayName = "Remote Compose Widgets — Android Launcher",
    description =
      "Android launcher widgets drawn by Remote Compose: capture profiles, the Android 16 compatibility pass, local capture with embedded images and numeric host actions, RemoteViews and PendingIntent assembly, launcher sizing and an in-app resizable preview.",
  )
}
