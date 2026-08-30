package ee.schimke.composeai.buildlogic

import com.ncorti.ktfmt.gradle.KtfmtExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * The conventions every module in this build applies: ktfmt in Google style, and the build-cache
 * salt below.
 *
 * Living in `build-logic` lets the ktfmt extension be configured with its real type
 * (`extensions.configure<KtfmtExtension>`) rather than reflectively — this convention plugin's
 * classpath already carries both ktfmt and the Kotlin Gradle plugin it links against.
 */
class ComposeAiBaseConventionsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("com.ncorti.ktfmt.gradle")
    project.extensions.configure<KtfmtExtension>("ktfmt") { googleStyle() }

    // Build-cache salt. A Gradle cache key is the hash of a task's declared inputs, so an extra
    // declared input property lets us move every Kotlin compilation to a fresh set of keys by
    // bumping one number in gradle.properties.
    //
    // This exists because a build-cache entry can go bad at rest: a truncated entry fails during
    // the *load* ("Could not load from remote cache: Unexpected end of ZLIB input stream") — before
    // the task can execute, so nothing ever pushes a replacement and no amount of re-running clears
    // it. Bumping the salt orphans the poisoned key rather than deleting it: the next pushing main
    // run executes the affected tasks and stores clean entries under the new keys. Cost is one cold
    // build; the stale entries age out via LRU.
    //
    // Applied here rather than in `composeai.kotlin-conventions` deliberately: base-conventions is
    // the plugin *every* module applies, and not every module applies the Kotlin conventions.
    val cacheSalt = project.providers.gradleProperty("composeai.cacheSalt").orElse("0")
    project.tasks.withType<KotlinCompilationTask<*>>().configureEach {
      inputs.property("composeai.cacheSalt", cacheSalt)
    }
  }
}
