package ee.schimke.composeai.buildlogic

import com.ncorti.ktfmt.gradle.KtfmtExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

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

    // Kotlin/Native links stay out of the build cache. Their outputs (frameworks and test
    // executables, the release ones hundreds of MB) are only ever reusable by another macOS run,
    // and a link's inputs are every klib under it, so any change to the player stack changes
    // the key anyway. Pushing them was most of what CI uploaded to the remote cache. Gradle has
    // no per-task push switch, so this keeps them out of the cache entirely; the XCFramework and
    // fat-framework tasks that assemble them are not cacheable to begin with.
    project.tasks.withType<KotlinNativeLink>().configureEach {
      outputs.doNotCacheIf("Kotlin/Native link outputs are large and reusable only on macOS") {
        true
      }
    }

    // Reproducible klibs for the builds that publish them. A Native or Wasm klib records every
    // source file's absolute path, so the published bytes depended on where the checkout sat
    // (and named the CI runner's directories). Relative to the root they are the same anywhere.
    //
    // Only when publishing (`-Pcomposeai.reproducibleKlibs=true`, passed by release.yml's Maven
    // Central job): the flag's value is the root's absolute path, and compiler arguments are part
    // of the task's build cache key, so on every build it would tie the klib compiles' keys to
    // the checkout location and cost every other checkout those hits.
    val reproducibleKlibs =
      project.providers.gradleProperty("composeai.reproducibleKlibs").orNull.toBoolean()
    if (reproducibleKlibs) {
      val klibPathArgs =
        listOf(
          "-Xklib-relative-path-base=${project.rootDir.absolutePath}",
          "-Xklib-normalize-absolute-path",
        )
      project.tasks.withType<KotlinCompilationTask<*>>().configureEach {
        val options = compilerOptions
        if (options is KotlinNativeCompilerOptions || options is KotlinJsCompilerOptions) {
          options.freeCompilerArgs.addAll(klibPathArgs)
        }
      }
    }
  }
}
