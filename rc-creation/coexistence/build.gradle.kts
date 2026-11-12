import java.util.zip.ZipFile

// `:rc-creation-coexistence-check`: proof that rc-players' creation modules and AndroidX's own can
// be in one app. Never published; it exists for `check`.
//
// Two things are checked, on Android and on the JVM:
//
//   1. One source set (`commonMain`, compiled for both targets) names a type of each library:
//      `ee.schimke.composeai.rccreation.compose.state.RemoteFloat` from `:rc-creation-compose` and
//      `androidx.compose.remote.creation.compose.state.RemoteFloat` from AndroidX's
//      `remote-creation-compose`, with both `foundation` modules on the classpath too. They
//      resolve together, which Gradle refused while the fork declared AndroidX's capabilities.
//   2. `verifyNoSharedClasses`: no class file is in both. Compiling proves less than it seems to
// (of
//      two classes with one name the first on the classpath wins, silently), and a library module
//      gets no duplicate-class check from the Android Gradle plugin.
//
// The AndroidX release this repository pins publishes `remote-creation-compose` and `foundation`
// for
// Android only, as AARs. For the JVM half their `classes.jar` is taken out of the AAR and put on
// the
// JVM compile classpath: enough to name their types beside the JVM jars of the creation modules,
// which is what a JVM consumer will have once AndroidX releases its JVM target.
plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

// AndroidX's Android-only creation artifacts, as files, for the JVM half and the class comparison.
val androidxCreationAars by configurations.creating {
  isCanBeConsumed = false
  isTransitive = false
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    attribute(
      LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
      objects.named(LibraryElements::class.java, "aar"),
    )
  }
}

// AndroidX's JVM creation artifacts (`remote-creation-core`, and `remote-core` with it).
val androidxCreationJars by configurations.creating {
  isCanBeConsumed = false
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    attribute(
      LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
      objects.named(LibraryElements::class.java, LibraryElements.JAR),
    )
  }
}

// The creation modules' own JVM jars.
val rcCreationJars by configurations.creating {
  isCanBeConsumed = false
  isTransitive = false
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    attribute(
      org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute,
      org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm,
    )
  }
}

dependencies {
  androidxCreationAars(libs.compose.remote.creation.compose)
  androidxCreationAars(libs.compose.remote.foundation)
  androidxCreationJars(libs.compose.remote.creation.core)
  rcCreationJars(project(":rc-creation-writer"))
  rcCreationJars(project(":rc-creation-expression"))
  rcCreationJars(project(":rc-creation-compose"))
  rcCreationJars(project(":rc-creation-foundation"))
}

/** Takes `classes.jar` out of each AAR, named after the AAR. */
@CacheableTask
abstract class ExtractAarClasses : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val aars: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun extract() {
    val out = outputDirectory.get().asFile
    out.deleteRecursively()
    out.mkdirs()
    aars.files.forEach { aar ->
      ZipFile(aar).use { zip ->
        val classes = checkNotNull(zip.getEntry("classes.jar")) { "No classes.jar in $aar" }
        zip.getInputStream(classes).use { input ->
          out.resolve(aar.nameWithoutExtension + ".jar").outputStream().use(input::copyTo)
        }
      }
    }
  }
}

val extractAndroidxCreationClasses by
  tasks.registering(ExtractAarClasses::class) {
    aars.from(androidxCreationAars)
    outputDirectory.set(layout.buildDirectory.dir("androidx-creation-classes"))
  }

val androidxCreationClasses =
  files(extractAndroidxCreationClasses.flatMap { it.outputDirectory }.map { it.asFileTree })

/** Fails if a class file is in a jar of [ours] and in a jar of [theirs]. */
@CacheableTask
abstract class VerifyNoSharedClasses : DefaultTask() {
  @get:Classpath abstract val ours: ConfigurableFileCollection

  @get:Classpath abstract val theirs: ConfigurableFileCollection

  @get:OutputFile abstract val report: RegularFileProperty

  private fun classes(jars: Iterable<File>): Map<String, String> = buildMap {
    jars
      .filter { it.isFile }
      .forEach { jar ->
        ZipFile(jar).use { zip ->
          zip
            .entries()
            .asSequence()
            .map { it.name }
            .filter { it.endsWith(".class") && !it.startsWith("META-INF/") }
            .forEach { put(it, jar.name) }
        }
      }
  }

  @TaskAction
  fun verify() {
    val ours = classes(ours.files)
    val theirs = classes(theirs.files)
    check(ours.isNotEmpty() && theirs.isNotEmpty()) {
      "Nothing to compare: ${ours.size} classes of rc-players', ${theirs.size} of AndroidX's"
    }
    val shared = ours.keys.intersect(theirs.keys).sorted()
    check(shared.isEmpty()) {
      "${shared.size} classes are in both rc-players' creation modules and AndroidX's:\n" +
        shared.take(40).joinToString("\n") { "  $it (${ours[it]}, ${theirs[it]})" }
    }
    report
      .get()
      .asFile
      .writeText(
        "No class in common: ${ours.size} classes in ${this.ours.files.map { it.name }.sorted()}, " +
          "${theirs.size} in ${this.theirs.files.map { it.name }.sorted()}\n",
      )
  }
}

val verifyNoSharedClasses by
  tasks.registering(VerifyNoSharedClasses::class) {
    group = "verification"
    description = "Checks rc-players' creation modules and AndroidX's define no class in common."
    ours.from(rcCreationJars)
    theirs.from(androidxCreationClasses, androidxCreationJars)
    report.set(layout.buildDirectory.file("reports/coexistence/classes.txt"))
  }

kotlin {
  jvm()

  android {
    namespace = "ee.schimke.composeai.rccreation.coexistence"
    compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
    minSdk = libs.versions.rc.player.minSdk.get().toInt()
    // The parity tests: one content written by both libraries, compared and played by AndroidX's
    // View player under Robolectric.
    withHostTest { isIncludeAndroidResources = true }
  }

  sourceSets {
    // Both libraries, for both targets: the source that names them is `commonMain`'s.
    commonMain.dependencies {
      implementation(project(":rc-creation-compose"))
      implementation(project(":rc-creation-foundation"))
    }
    androidMain.dependencies {
      implementation(libs.compose.remote.creation.compose)
      implementation(libs.compose.remote.foundation)
    }
    jvmMain.dependencies {
      compileOnly(androidxCreationClasses)
      compileOnly(libs.compose.remote.creation.core)
    }
    getByName("androidHostTest").dependencies {
      implementation(kotlin("test"))
      implementation(libs.junit)
      implementation(libs.robolectric)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.compose.runtime)
      implementation(libs.compose.remote.core)
      implementation(libs.compose.remote.creation.core)
      implementation(libs.compose.remote.player.core)
      implementation(libs.compose.remote.player.view)
      implementation(project(":rc-creation-writer"))
      implementation(project(":rc-player-protocol"))
    }
  }
}

tasks.named("check") { dependsOn(verifyNoSharedClasses, "compileKotlinJvm", "compileAndroidMain") }
