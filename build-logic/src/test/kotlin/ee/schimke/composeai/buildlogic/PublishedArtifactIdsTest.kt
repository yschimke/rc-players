package ee.schimke.composeai.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [PublishedArtifactIds], pinned against the build files it duplicates.
 *
 * The table exists because `project.version` is decided while the publishing plugin is applied,
 * before the module's own `composeAiMavenPublishing { artifactId = … }` has run — so the mapping
 * has to be knowable from the project path alone. That makes it a second copy of something the
 * build files already say, and a second copy that nothing checks is how a module goes missing from
 * the BOM, or gets a version from the wrong manifest entry, without anything failing.
 *
 * Checked in both directions on purpose: a module missing from the table is one the publish plan
 * cannot address, and an entry with no module behind it is a constraint the BOM promises for a
 * coordinate nobody publishes.
 */
class PublishedArtifactIdsTest {

  private val repoRoot =
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .first { it.resolve("settings.gradle.kts").isFile && it.resolve("rc-player").isDirectory }

  /** `project path -> declared artifact id` for every project that applies the publishing plugin. */
  private fun declaredByProjectPath(): Map<String, String> {
    val settings = repoRoot.resolve("settings.gradle.kts").readText()
    val paths = Regex("""^include\("(:[^"]+)"\)""", RegexOption.MULTILINE).findAll(settings).map {
      it.groupValues[1]
    }
    // `\s*=\s*`, not " = ": ktfmt wraps the longer assignments onto a second line, and a regex
    // that missed those read the directory off the project path instead — which is how two
    // modules disappeared from the publish plan.
    val dirs =
      Regex("""project\("(:[^"]+)"\)\.projectDir\s*=\s*file\("([^"]+)"\)""")
        .findAll(settings)
        .associate { it.groupValues[1] to it.groupValues[2] }

    return paths
      .mapNotNull { path ->
        val dir = dirs[path] ?: path.removePrefix(":").replace(':', '/')
        val buildFile = repoRoot.resolve(dir).resolve("build.gradle.kts")
        if (!buildFile.isFile) return@mapNotNull null
        val text = buildFile.readText()
        if (!text.contains("""id("composeai.maven-publishing")""")) return@mapNotNull null
        val declared =
          Regex("""artifactId\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
            ?: error("$path applies composeai.maven-publishing but declares no artifactId")
        path to declared
      }
      .toMap()
  }

  @Test
  fun `the table is exactly what the build files declare`() {
    assertEquals(declaredByProjectPath(), PublishedArtifactIds.byProjectPath)
  }

  @Test
  fun `every published coordinate has a recorded version`() {
    // The manifest is what a skipped module's POM takes its version from. An id in one file and
    // not the other fails the release during Gradle configuration at best, and publishes a POM
    // naming a coordinate that does not exist at worst.
    val manifest = repoRoot.resolve("publishing-manifest.json").readText()
    val missing =
      PublishedArtifactIds.byProjectPath.values.filter {
        PublishedVersions.recordedVersion(it, manifest) == null
      }
    assertEquals(emptyList(), missing)
  }

  @Test
  fun `the root build's publish list agrees with the table`() {
    // `build.gradle.kts` keeps its own copy so `publishPlayers` can map ids back to project paths
    // without a build-logic plugin on the root classpath. Three copies is two too many to trust.
    val rootBuild = repoRoot.resolve("build.gradle.kts").readText()
    val block = rootBuild.substringAfter("val publishedProjects =").substringBefore("\n\n")
    val pairs =
      Regex(""""(:[^"]+)"\s+to\s+"([^"]+)"""").findAll(block).associate {
        it.groupValues[1] to it.groupValues[2]
      }
    assertEquals(PublishedArtifactIds.byProjectPath, pairs)
  }
}
