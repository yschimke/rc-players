// An Android XR app that plays a document using the draft 3D operations and hands its meshes to
// the XR runtime (SceneCore) as real objects in the room, while the 2D document stays on the main
// panel. On a device without XR it falls back to the player drawing everything on the panel.
// Build-only; it is never published. See docs/design/RC_3D_EXTENSION.md, "XR".
plugins {
  id("composeai.base-conventions")
  id("composeai.android-conventions")
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "ee.schimke.composeai.rcplayer.xrsample"
  defaultConfig {
    applicationId = "ee.schimke.composeai.rcplayer.xrsample"
    // Jetpack XR's own floor.
    minSdk = 24
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }
  // The player's tracing touches `android.os` on the unit-test JVM, as the player modules' own
  // host tests do; stubs returning defaults keep it a no-op there.
  testOptions { unitTests.isReturnDefaultValues = true }
  kotlin { jvmToolchain(17) }
}

dependencies {
  implementation(project(":rc-player-3d"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation.cmp)
  implementation(libs.xr.scenecore)

  testImplementation(libs.junit)
  testImplementation(kotlin("test-junit"))
}

kotlin {
  compilerOptions {
    // The sample is a consumer of the experimental extension API; it opts in once, here.
    optIn.addAll(
      "ee.schimke.composeai.rcplayer.compose.ExperimentalRcPlayerApi",
      "ee.schimke.composeai.rcplayer.protocol.ExperimentalRcWireExtension",
    )
  }
}
