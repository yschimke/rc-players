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
      api(project(":rc-widget-creation-androidx"))
      api(libs.compose.remote.core)
      api(libs.compose.remote.creation)
      api(libs.compose.remote.creation.core)
      api(libs.compose.remote.creation.compose)
      api(libs.compose.remote.player.core)
      api(libs.compose.remote.player.view)
      implementation(libs.compose.runtime)
      implementation(libs.compose.ui)
      implementation(libs.compose.foundation)
      implementation(libs.kotlinx.coroutines.core)
    }
    getByName("androidHostTest").dependencies {
      // rc-players' own capture profiles, to hold them to this module's AndroidX ones.
      implementation(project(":rc-widget-creation"))
      implementation(kotlin("test"))
      implementation(libs.junit)
      implementation(libs.robolectric)
    }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }

// Robolectric's Android 15, 16 and 17 platforms, so the tests that play on the platform's own
// player run offline.
//
// One configuration each: one would resolve the three versions of the module to the newest.
val robolectricPlatforms =
  listOf(libs.robolectric.sdk35, libs.robolectric.sdk36, libs.robolectric.sdk37).mapIndexed {
    index,
    platform ->
    configurations.create("robolectricPlatform$index") {
      isCanBeConsumed = false
      isTransitive = false
      dependencies.add(project.dependencies.create(platform.get()))
    }
  }

val prepareRobolectricPlatforms by
  tasks.registering(Sync::class) {
    robolectricPlatforms.forEach { from(it) }
    into(layout.buildDirectory.dir("robolectric-sdk"))
  }

tasks.withType<Test>().configureEach {
  // Robolectric runs Android 16 and later only on Java 21; the library itself stays Java 17.
  javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
  dependsOn(prepareRobolectricPlatforms)
  systemProperty("robolectric.offline", "true")
  // `-Prc.render.out=<dir>` keeps the platform player's renders of the tests that save them.
  providers.gradleProperty("rc.render.out").orNull?.let { systemProperty("rc.render.out", it) }
  systemProperty(
    "robolectric.dependency.dir",
    layout.buildDirectory.dir("robolectric-sdk").get().asFile.absolutePath,
  )
  // Robolectric's Android 16 shares memory through FileDescriptor internals JDK 17+ hides.
  jvmArgs(
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
  )
}

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-widget-launcher",
    displayName = "Remote Compose Widgets — Android Launcher",
    description =
      "Android launcher widgets drawn by Remote Compose: capture profiles, the Android 16 compatibility pass, local capture with embedded images and numeric host actions, RemoteViews and PendingIntent assembly, launcher sizing and an in-app resizable preview.",
  )
}
