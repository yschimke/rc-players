import com.android.build.api.dsl.ManagedVirtualDevice

// Collects the baseline profile `trace`, `protocol`, `runtime` and `compose` ship in their AARs, by
// scrolling `:rc-player-baselineprofile-host` through the catalog corpus.
//
//   ./gradlew :rc-player-{trace,protocol,runtime,compose}:generateBaselineProfile
//
// against a connected emulator (API 33+), or add -Pbaselineprofile.gmd=true to let Gradle boot one.
//
// CI does the same in `.github/workflows/baseline-profiles.yml`. Build-only; never published.
plugins {
  id("composeai.base-conventions")
  id("com.android.test")
  alias(libs.plugins.androidx.baselineprofile)
}

android {
  namespace = "ee.schimke.composeai.rcplayer.baselineprofile"
  compileSdk = 37
  defaultConfig {
    minSdk = 28
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  targetProjectPath = ":rc-player-baselineprofile-host"
  kotlin { jvmToolchain(17) }
  testOptions.managedDevices.allDevices {
    create("pixel6Api34", ManagedVirtualDevice::class.java) {
      device = "Pixel 6"
      apiLevel = 34
      systemImageSource = "aosp"
    }
  }
}

// Only the plugin's profile-capture variant; the debug and release test variants have nothing to
// run.
androidComponents { beforeVariants { it.enable = it.buildType == "nonMinifiedRelease" } }

baselineProfile {
  val gmd = providers.gradleProperty("baselineprofile.gmd").orNull == "true"
  if (gmd) managedDevices += "pixel6Api34"
  useConnectedDevices = !gmd
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
