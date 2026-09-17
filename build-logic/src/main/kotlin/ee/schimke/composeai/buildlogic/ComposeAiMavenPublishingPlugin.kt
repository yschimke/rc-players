package ee.schimke.composeai.buildlogic

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import java.io.File
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.configure

abstract class ComposeAiMavenPublishingExtension
@Inject
constructor(objects: ObjectFactory) {
  val artifactId: Property<String> = objects.property(String::class.java)
  val displayName: Property<String> = objects.property(String::class.java)
  val description: Property<String> = objects.property(String::class.java)
  val inceptionYear: Property<String> = objects.property(String::class.java).convention("2026")

  fun coordinates(artifactId: String, displayName: String, description: String) {
    this.artifactId.set(artifactId)
    this.displayName.set(displayName)
    this.description.set(description)
  }
}

class ComposeAiMavenPublishingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("composeai.android-conventions")
    project.pluginManager.apply("composeai.jvm-conventions")
    project.pluginManager.apply("composeai.kotlin-conventions")
    project.pluginManager.apply("maven-publish")
    project.pluginManager.apply("com.vanniktech.maven.publish")

    val extension =
      project.extensions.create(
        "composeAiMavenPublishing",
        ComposeAiMavenPublishingExtension::class.java,
      )

    project.group = "ee.schimke.composeai"
    project.version = project.publishedVersion()

    project.configureAndroidLibraryPublication()

    project.afterEvaluate {
      project.configureComposeAiPublication(
        artifactId =
          extension.artifactId.orNull ?: error("composeAiMavenPublishing.artifactId is required"),
        displayName =
          extension.displayName.orNull
            ?: error("composeAiMavenPublishing.displayName is required"),
        artifactDescription =
          extension.description.orNull
            ?: error("composeAiMavenPublishing.description is required"),
        inceptionYear = extension.inceptionYear,
      )
    }
  }
}

/**
 * The coordinates, signing and POM metadata every artifact this repository publishes carries.
 *
 * Shared by [ComposeAiMavenPublishingPlugin] and [ComposeAiPlatformPublishingPlugin] rather than
 * duplicated: the BOM describes the same release as the players it constrains, so if the two
 * disagreed about the group, the licence or the SCM block, the index and the things it indexes
 * would be published under different metadata.
 */
internal fun Project.configureComposeAiPublication(
  artifactId: String,
  displayName: String,
  artifactDescription: String,
  inceptionYear: org.gradle.api.provider.Property<String>,
) {
  extensions.configure<MavenPublishBaseExtension> {
    publishToMavenCentral(automaticRelease = true)
    if (!version.toString().endsWith("SNAPSHOT")) {
      signAllPublications()
    }
    coordinates("ee.schimke.composeai", artifactId, version.toString())
    pom {
      name.set(displayName)
      description.set(artifactDescription)
      url.set("https://github.com/yschimke/rc-players")
      inceptionYear.set(inceptionYear)
      licenses {
        license {
          name.set("The Apache License, Version 2.0")
          url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
          distribution.set("repo")
        }
      }
      developers {
        developer {
          id.set("yschimke")
          name.set("Yuri Schimke")
          url.set("https://github.com/yschimke")
        }
      }
      scm {
        url.set("https://github.com/yschimke/rc-players")
        connection.set("scm:git:https://github.com/yschimke/rc-players.git")
        developerConnection.set("scm:git:ssh://git@github.com/yschimke/rc-players.git")
      }
    }
  }
}

/**
 * Publish an Android library as its single `release` variant, with real sources and an empty
 * javadoc jar — Maven Central requires *a* javadoc artifact but not a useful one for a Kotlin
 * library whose docs live in the repo.
 *
 * This used to be copy-pasted into all 25 Android modules that publish, each carrying the same
 * three imports, the same `@file:Suppress("DEPRECATION")` header, and the same nine-line
 * `mavenPublishing { configure(...) }` block. Twenty-five copies of one decision is twenty-five
 * places to miss when the plugin's API moves — which the suppression comment itself predicted
 * ("the replacement types vary between plugin versions"). Now it moves here, once.
 *
 * `withPlugin` rather than an `afterEvaluate` check so the JVM modules that share this convention
 * plugin (65 of the 90) are untouched — vanniktech's own default handles them correctly.
 */
@Suppress("DEPRECATION") // AndroidSingleVariantLibrary(Boolean, Boolean); replacement types
// (SourcesJar / JavadocJar) vary between plugin versions. Re-visit when bumping.
private fun Project.configureAndroidLibraryPublication() {
  pluginManager.withPlugin("com.android.library") {
    extensions.configure<MavenPublishBaseExtension> {
      configure(
        AndroidSingleVariantLibrary(
          javadocJar = JavadocJar.Empty(),
          sourcesJar = SourcesJar.Sources(),
          variant = "release",
        )
      )
    }
  }
}

/**
 * The version this module publishes at.
 *
 * On a release the tag's version is not automatically this module's: a release that publishes only
 * the modules it changed leaves the rest where they were, and their POMs — and the BOM's
 * constraints — have to name the version that actually exists on Central. [PublishedVersions] is
 * the one place that rule lives; this is its `project.version` caller.
 *
 * Absent `PLUGIN_VERSION` there is no release in progress, so the publish set and the manifest are
 * irrelevant and every module takes the local snapshot version.
 */
private fun Project.publishedVersion(): String {
  val pluginVersion =
    providers.environmentVariable("PLUGIN_VERSION").orNull?.takeIf { it.isNotBlank() }
      ?: return nextPatchSnapshotVersion()

  val artifactId =
    PublishedArtifactIds.forProjectPath(path)
      ?: error(
        "$path applies composeai.maven-publishing but is not in PublishedArtifactIds. Add it " +
          "there, or it cannot be addressed by a publish plan."
      )
  return PublishedVersions.resolve(
    artifactId = artifactId,
    tagVersion = pluginVersion,
    publishSet =
      PublishedVersions.parsePublishSet(providers.gradleProperty("composeai.publishSet").orNull),
    manifestText = publishingManifestText(),
  )
}

/**
 * `publishing-manifest.json`, or an empty document when there is none.
 *
 * NOT a committed file. The release job's publish plan resolves each coordinate's published version
 * from Maven Central and writes it here (`--write-manifest`) before Gradle runs, so a module the
 * release skips can name the version it is already published at. Outside a release the file is
 * absent and nothing reads it: `publishedVersion` only consults it when `PLUGIN_VERSION` is set.
 */
internal fun Project.publishingManifestText(): String =
  generateSequence(rootDir) { it.parentFile }
    .map { it.resolve("publishing-manifest.json") }
    .firstOrNull(File::isFile)
    ?.readText() ?: "{}"

/**
 * The version a *platform* publishes at: always the tag, never a held-back one.
 *
 * `:bom` is the index of a release, not a member of it. A consumer resolving the BOM at the tag has
 * to find it there whether or not any given module published, so it never takes a recorded version
 * — and it is deliberately not routed through [publishedVersion], whose artifact-id lookup would
 * not find it.
 */
internal fun Project.platformPublishedVersion(): String =
  providers.environmentVariable("PLUGIN_VERSION").orNull?.takeIf { it.isNotBlank() }
    ?: nextPatchSnapshotVersion()

private fun Project.nextPatchSnapshotVersion(): String {
  val manifest =
    generateSequence(rootDir) { it.parentFile }
      .map { it.resolve(".release-please-manifest.json") }
      .firstOrNull(File::isFile)
      ?: error("Could not find .release-please-manifest.json from $rootDir")
  val current = Regex(""""\.":\s*"([^"]+)"""").find(manifest.readText())!!.groupValues[1]
  val (major, minor, patch) = current.split(".").map { it.toInt() }
  return "$major.$minor.${patch + 1}-SNAPSHOT"
}
