plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
}

// `:rc-widget-core` — what a widget, a watch and a server have to agree on about a document, with
// no Compose and no Android: which player a client has and which target it can be served
// (`RcWidgetTarget`, `RcPlayerCapabilities`), the pipeline that bakes a template for a host that
// cannot bind values (`RcWidgetDocuments`), the named values a template reads
// (`RcBindingContract`),
// the Wear widget container's named frame (`RcWearContainer`, `RcWearTemplates`), host-action
// rewrites and the numeric action ids a launcher widget's `RemoteViews` player sends. It runs on a
// design server as it does on a phone. Extracted from homeassistant-remotecompose; see
// docs/design/RC_WIDGETS.md.

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  jvm()
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  android {
    namespace = "ee.schimke.composeai.rcwidget"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
    withHostTest { isReturnDefaultValues = true }
  }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  sourceSets {
    commonMain.dependencies {
      // `api`: documents in and out are `RcDocument`s, and the targets are `RcTargetProfile`s.
      api(project(":rc-player-edit"))
      implementation(libs.kotlinx.coroutines.core)
    }
    // Android and the JVM share the `java.io` file store, which no hierarchy group holds.
    androidMain { kotlin.srcDir("src/jdkMain/kotlin") }
    jvmMain { kotlin.srcDir("src/jdkMain/kotlin") }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-widget-core",
    displayName = "Remote Compose Widgets — Core",
    description =
      "Serve Remote Compose documents to widgets: target negotiation, baking templates for hosts that cannot bind values, binding contracts, the Wear widget container frame and host-action rewrites. No Compose, no Android; runs on a server.",
  )
}
