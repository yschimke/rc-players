plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  alias(libs.plugins.compose.multiplatform)
  id("org.jetbrains.kotlin.plugin.compose")
}

// `:rc-widget-creation` — creation helpers for documents served to widgets: reproducible expression
// names, size breakpoints a launcher capture resolves and a player evaluates, numeric widget host
// actions, and the Wear widget container whose frame is left to the watch (`RcWearContainer`'s
// names). Written against rc-players' Compose Multiplatform creation API
// (`ee.schimke.composeai.rccreation`) on every target, Android included, so a design server on the
// JVM and an app on a phone capture the same template.
//
// `:rc-widget-creation-androidx` is the same helpers against AndroidX's own creation API, for code
// that has to compose with AndroidX-only libraries (launcher `RemoteViews`, Glance Wear). It is
// generated from this module's `commonMain`, except `RcCreationCompat.kt`, which each binding
// writes for its own library. See docs/design/RC_WIDGETS.md.

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  android {
    namespace = "ee.schimke.composeai.rcwidget.creation"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":rc-widget-core"))
      api(project(":third-party-remote-creation-compose"))
      @Suppress("DEPRECATION") api(compose.runtime)
      @Suppress("DEPRECATION") api(compose.ui)
    }
    commonTest.dependencies { implementation(kotlin("test")) }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-widget-creation",
    displayName = "Remote Compose Widgets — Creation",
    description =
      "Creation helpers for Remote Compose widgets: reproducible expression names, size breakpoints, numeric widget host actions and the host-framed Wear widget container, against the Compose Multiplatform creation API (rc-creation-compose) on every target.",
  )
}
