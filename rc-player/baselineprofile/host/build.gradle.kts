// The app `:rc-player-baselineprofile` drives to collect the published players' baseline profile:
// a scrolling list that plays every document of the catalog corpus through `RcComposePlayer`.
// Build-only; it is never published.
plugins {
  id("composeai.base-conventions")
  id("composeai.android-conventions")
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  // Gives the host the `nonMinifiedRelease` variant the generator runs against.
  alias(libs.plugins.androidx.baselineprofile)
}

android {
  namespace = "ee.schimke.composeai.rcplayer.baselineprofile.host"
  defaultConfig {
    applicationId = "ee.schimke.composeai.rcplayer.baselineprofile.host"
    // Profile capture without root needs API 33; the floor only has to cover the emulator.
    minSdk = 28
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      // Never distributed; debug signing lets the benchmark variants install.
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  // The catalog corpus (`scripts/rc-catalog-corpus/`), served as assets rather than copied.
  sourceSets.getByName("main").assets.directories.add("../../../scripts/rc-catalog-corpus/corpus")
  kotlin { jvmToolchain(17) }
}

dependencies {
  implementation(project(":rc-player-compose"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation.cmp)
}
