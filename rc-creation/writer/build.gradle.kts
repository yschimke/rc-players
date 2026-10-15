// PROTOTYPE — moved from yschimke/remote-m3-catalog `vendor/remote-write-core` (AndroidX change
// 4307936 ps17, refreshed to snapshot 16547072). Not for landing; see
// docs/design/RC_CREATION_BRIDGE.md.
plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  // Not yet `explicitApi()` / ABI-gated like the player modules: the imported sources are not
  // explicit-API clean (PaintBundleData constants). Landing makes them so; see the design doc.

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  android {
    namespace = "ee.schimke.composeai.rccreation.writer"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
  }

  sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}
