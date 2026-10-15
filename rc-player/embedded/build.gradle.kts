// PROTOTYPE — joins creation (`rc-creation-compose`) to the player (`rc-player-compose`): one
// remote composition that can either write a document or feed a player in the same process. Not
// for landing; see docs/design/RC_CREATION_BRIDGE.md.
plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  alias(libs.plugins.compose.multiplatform)
  id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
  explicitApi()

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  android {
    namespace = "ee.schimke.composeai.rcplayer.embedded"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":rc-creation-compose"))
      api(project(":rc-player-compose"))
      @Suppress("DEPRECATION") api(compose.runtime)
      implementation(libs.kotlinx.coroutines.core)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
      @Suppress("DEPRECATION") implementation(compose.desktop.currentOs)
      implementation(libs.jetbrains.compose.ui.test)
    }
  }
}
