plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("composeai.maven-publishing")
  id("org.jetbrains.kotlin.plugin.compose")
}

// `:rc-widget-creation-androidx` — `:rc-widget-creation`'s helpers against AndroidX's own creation
// API (`androidx.compose.remote.creation.compose`), in the package
// `ee.schimke.composeai.rcwidget.creation.androidx`, so an app can hold both bindings. Android
// only: it is for code that composes with AndroidX-only libraries, which is what a launcher's
// `RemoteViews` player and Glance Wear take (`:rc-widget-launcher`, `:rc-widget-wear`).
//
// One source, two bindings. Every file of `:rc-widget-creation`'s `commonMain` but
// `RcCreationCompat.kt` is copied here at build time with its packages rewritten
// (`generateAndroidxBinding`); nothing generated is committed. What is written by hand, under
// `src/androidMain`, is what has no counterpart there or cannot be the same text:
// `RcCreationCompat.kt` (the two functions the pinned AndroidX release spells differently from
// androidx-main) and the AndroidX capture profiles. See docs/design/RC_WIDGETS.md.

/**
 * Copies Kotlin sources, replacing each package prefix in [packages] (where an identifier starts
 * and the prefix is not part of a longer name) with its value.
 */
@CacheableTask
abstract class RewritePackages : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ConfigurableFileCollection

  /** The directory [sources] are relative to; a file keeps its path below it. */
  @get:Internal abstract val sourceRoot: DirectoryProperty

  @get:Input abstract val packages: MapProperty<String, String>

  /** The package directory of [sourceRoot] and the one its files are written under. */
  @get:Input abstract val fromDirectory: Property<String>

  @get:Input abstract val toDirectory: Property<String>

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun rewrite() {
    val out = outputDirectory.get().asFile
    out.deleteRecursively()
    val root = sourceRoot.get().asFile
    val rules =
      packages.get().map { (from, to) ->
        Regex("(?<![A-Za-z0-9_.])" + Regex.escape(from) + "(?![A-Za-z0-9_])") to to
      }
    sources.files.sorted().forEach { file ->
      val relative = file.relativeTo(root).invariantSeparatorsPath
      check(relative.startsWith(fromDirectory.get() + "/")) { "$relative is outside the package" }
      val target = out.resolve(toDirectory.get() + relative.removePrefix(fromDirectory.get()))
      target.parentFile.mkdirs()
      val text = rules.fold(file.readText()) { text, (from, to) -> from.replace(text, to) }
      target.writeText(
        "// Generated from :rc-widget-creation's $relative by generateAndroidxBinding. Do not edit.\n" +
          text,
      )
    }
  }
}

val commonSources = layout.projectDirectory.dir("../creation/src/commonMain/kotlin")

val generateAndroidxBinding by
  tasks.registering(RewritePackages::class) {
    description = "Writes :rc-widget-creation's common sources against AndroidX's creation API."
    sourceRoot.set(commonSources)
    // `RcCreationCompat.kt` is the seam: this module has its own, under `src/androidMain`.
    sources.from(
      commonSources.asFileTree.matching {
        include("**/*.kt")
        exclude("**/RcCreationCompat.kt")
      },
    )
    packages.put(
      "ee.schimke.composeai.rccreation.compose",
      "androidx.compose.remote.creation.compose",
    )
    packages.put("ee.schimke.composeai.rccreation.foundation", "androidx.compose.remote.foundation")
    packages.put(
      "ee.schimke.composeai.rcwidget.creation",
      "ee.schimke.composeai.rcwidget.creation.androidx",
    )
    fromDirectory.set("ee/schimke/composeai/rcwidget/creation")
    toDirectory.set("ee/schimke/composeai/rcwidget/creation/androidx")
    outputDirectory.set(layout.buildDirectory.dir("generated/androidxBinding/kotlin"))
  }

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  android {
    namespace = "ee.schimke.composeai.rcwidget.creation.androidx"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
  }

  sourceSets {
    androidMain {
      kotlin.srcDir(generateAndroidxBinding.flatMap { it.outputDirectory })
      dependencies {
        api(project(":rc-widget-core"))
        api(libs.compose.remote.creation.compose)
        api(libs.compose.remote.foundation)
        // The capture profiles name `Profile`, `RemoteComposeWriterAndroid` and `CoreDocument`.
        api(libs.compose.remote.core)
        api(libs.compose.remote.creation)
        api(libs.compose.remote.creation.core)
        api(libs.compose.runtime)
        api(libs.compose.ui)
      }
    }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
  coordinates(
    artifactId = "rc-widget-creation-androidx",
    displayName = "Remote Compose Widgets — Creation (AndroidX binding)",
    description =
      "rc-widget-creation's helpers against AndroidX's own Remote Compose creation API, for Android code that composes with AndroidX-only libraries such as launcher RemoteViews and Glance Wear. Generated from the same source, in the package ee.schimke.composeai.rcwidget.creation.androidx.",
  )
}
