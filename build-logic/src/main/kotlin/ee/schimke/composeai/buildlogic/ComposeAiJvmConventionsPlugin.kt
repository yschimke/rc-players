package ee.schimke.composeai.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ComposeAiJvmConventionsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.configureCommonJvm()
  }
}

private fun Project.configureCommonJvm() {
  pluginManager.withPlugin("java") {
    extensions.configure<JavaPluginExtension> {
      toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    }
    tasks.withType<Test>().configureEach { useJUnit() }
  }

  // Kotlin Multiplatform applies `java-base`, not `java`, so the block above never runs for a KMP
  // module. Without this, the JVM target follows whichever JDK runs Gradle and a published
  // multiplatform artifact's bytecode level becomes a property of the machine that cut it — the
  // `:rc-player-compose` exposure in #220, where a JDK-17 `JavaExec` consumer refuses class file
  // 65. `jvmToolchain` pins the JVM compilation and leaves the Apple and wasm targets alone.
  pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension> { jvmToolchain(17) }
  }
}
