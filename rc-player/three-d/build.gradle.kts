plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  alias(libs.plugins.compose.multiplatform)
  id("org.jetbrains.kotlin.plugin.compose")
}

// The draft 3D extension: codecs for AndroidX's unmerged 3D operations (ag/4108133), a portable
// software renderer, and a GPU path through Compose's `drawVertices`. See
// docs/design/RC_3D_EXTENSION.md. Same target set as `:rc-player-compose`, which it plugs into.
kotlin {
  explicitApi()
  sourceSets.all {
    languageSettings.optIn("ee.schimke.composeai.rcplayer.compose.ExperimentalRcPlayerApi")
    languageSettings.optIn("ee.schimke.composeai.rcplayer.protocol.ExperimentalRcWireExtension")
  }
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    binaries.executable()
  }

  android {
    namespace = "ee.schimke.composeai.rcplayer.threed"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
    withHostTest { isReturnDefaultValues = true }
    // Instrumented tests for the `android.graphics.Mesh` renderer, which only a device (API 34+)
    // can draw: Robolectric's host graphics have no meshes. Run by the 3D device-test workflow.
    withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
  }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    common {
      group("skiko") {
        withWasmJs()
        // The Skia targets with no GPU depth renderer yet; Wasm has its own, over WebGL2.
        group("skikoNative") {
          withJvm()
          withApple()
        }
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":rc-player-compose"))
      @Suppress("DEPRECATION") api(compose.runtime)
      @Suppress("DEPRECATION") api(compose.ui)
    }
    commonTest.dependencies { implementation(kotlin("test")) }
    named("androidDeviceTest").dependencies {
      implementation(libs.androidx.test.runner)
      implementation(libs.androidx.test.ext.junit)
      implementation(libs.junit)
    }
    jvmTest.dependencies {
      @Suppress("DEPRECATION") implementation(compose.desktop.currentOs)
      implementation(libs.jetbrains.compose.ui.test)
    }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }

// The module has no Compose resources, and the Compose plugin's copy task for the device-test
// variant has no output directory configured, which fails the build: there is nothing to copy.
tasks
  .matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }
  .configureEach { enabled = false }

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-player-3d",
    displayName = "Remote Compose Player — Draft 3D extension",
    description =
      "Opt-in support for the draft Remote Compose 3D operations: meshes, camera, lights and parametric primitives, drawn in software or through the GPU's vertex path.",
  )
}

// Evidence renders: `./gradlew :rc-player-3d:jvmTest --rerun --tests '*Rc3DEvidence*'
// -Prc.3d.out=<abs dir>` writes PNGs; without the property the generator skips.
tasks.withType<Test>().configureEach {
  providers.gradleProperty("rc.3d.out").orNull?.let { systemProperty("rc.3d.out", it) }
}

// The WebGL2 depth renderer's browser test, as a static bundle: the production-compiled `wasmJs`
// test executable, Skiko's runtime and a page that runs the tests and publishes the results.
// `scripts/wasm-smoke/three-d-webgl.mjs` opens it in Chromium (see Rc3DWebGlDepthTest). It is not
// `wasmJsBrowserTest`: Karma's GPU-less Chrome is launched without the software-GL flag WebGL2
// needs (the WebGL2 cases skip there), and the cases' timings mean little in an unoptimized build.
val optimizedTests =
  tasks.named<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec>(
    "compileTestProductionExecutableKotlinWasmJsOptimize"
  )

tasks.register<Sync>("wasmWebGlTestDist") {
  description =
    "Assemble the 3D WebGL2 browser test bundle for scripts/wasm-smoke/three-d-webgl.mjs."
  group = "verification"
  dependsOn("wasmJsTestTestProductionExecutableCompileSync", "processSkikoRuntimeForKWasm")
  from(optimizedTests.flatMap { it.outputDirectory }) { exclude("*.map") }
  from(layout.buildDirectory.dir("compose/skiko-runtime-processed-wasmjs")) {
    include("skiko.mjs", "skiko.wasm")
  }
  from(layout.projectDirectory.dir("src/wasmJsTest/page"))
  from(rootProject.layout.projectDirectory.file("rc-player/wasm/dist-assets/js-joda.esm.js"))
  into(layout.buildDirectory.dir("wasmWebGlTestDist"))
}
