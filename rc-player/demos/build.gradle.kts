plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

// `:rc-player-demos` — runnable examples of the things a host has to supply itself.
//
// Nothing here publishes. The player draws a `Custom` component only if the host registers a
// renderer for its config name (`RcCustomComponentRegistry`), which makes custom components the one
// part of the stack a consumer cannot learn from the published API alone: the interesting half is
// the host's. These demos are that half, written the way a consumer would write it, and they double
// as the regression tests for the two directions a custom component runs in — reading document
// state (`SupportSpannableString`) and writing back to it (the editable text field).
//
// `./gradlew :rc-player-demos:run` opens them in a desktop window; the `@Preview` functions render
// in the IDE; `RcDemoRenderTest` rasterizes both headless, which is what CI exercises and what the
// committed PNGs under `renders/` come from.

dependencies {
  implementation(project(":rc-player-compose"))
  implementation(project(":rc-player-protocol"))
  implementation(project(":rc-player-runtime"))

  @Suppress("DEPRECATION") implementation(compose.desktop.currentOs)
  @Suppress("DEPRECATION") implementation(compose.runtime)
  @Suppress("DEPRECATION") implementation(compose.foundation)
  @Suppress("DEPRECATION") implementation(compose.ui)
  implementation(libs.jetbrains.compose.components.ui.tooling.preview)

  testImplementation(libs.junit)
  testImplementation(kotlin("test"))
  testImplementation(libs.jetbrains.compose.ui.test)
}

tasks.register<JavaExec>("run") {
  description = "Open the custom-component demos in a desktop window."
  group = "application"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("ee.schimke.composeai.rcplayer.demos.RcDemoMainKt")
}
