plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.android.library) apply false
  // ktfmt is not declared here: `ComposeAiBaseConventionsPlugin` (build-logic) applies and
  // configures it on every module. ktfmt + the Kotlin Gradle plugin it links against ride on the
  // build-logic classpath, so declaring the alias here too would put a second ktfmt on a different
  // classloader.
  //
  // The publish plugin IS loaded into the root scope so the sibling publishing modules share the
  // plugin's ClassLoader. Without it, each sibling instantiates its own MavenCentralBuildService
  // class and Gradle refuses to share the build service across them.
  alias(libs.plugins.maven.publish) apply false
}

// The root's aggregate and release tasks (ktfmt, build-logic's tests under `check`,
// `publishPlayers`, `printPublishSet`) live in `root-tasks.gradle.kts`, not here. This file is a
// shared build input to `.github/scripts/maven-publish-plan.sh` -- the plugins above reach every
// module, so a change to it publishes all of them -- while those tasks decide which tasks run and
// build nothing. Keeping them apart means a change to the release wiring stops re-uploading every
// player.
apply(from = "root-tasks.gradle.kts")
