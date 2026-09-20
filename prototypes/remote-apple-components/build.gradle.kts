plugins {
  id("composeai.base-conventions")
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "ee.schimke.composeai.remote.apple"
  compileSdk = 37

  defaultConfig {
    minSdk = 29
    aarMetadata { minCompileSdk = 36 }
  }

  // The alpha authoring surface marks several composables as library-group restricted even though
  // third-party component libraries are their intended extension point.
  lint { disable += "RestrictedApi" }
}

dependencies {
  api(libs.compose.remote.creation.compose)

  implementation(platform(libs.compose.bom.compat))
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui)

  testImplementation(project(":rc-player-protocol"))
  testImplementation(libs.compose.remote.creation)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
  (project.findProperty("remote.apple.fixtureDir") as String?)?.let {
    systemProperty("remote.apple.fixtureDir", it)
  }
}
