package ee.schimke.composeai.buildlogic

/**
 * The artifact id each published project carries, addressed by project path.
 *
 * A table rather than a rule. The sibling repositories derive the id by flattening the project path
 * (`:data-theme-core` -> `data-theme-core`) and that holds for seven of the eight projects here —
 * but `:third-party-remote-compose-player-dist` publishes as `remote-compose-player-js-dist`, and a
 * rule with one exception silently written into it is worse than a list.
 *
 * Something has to know this mapping outside the module that declares it: `project.version` is set
 * while the plugin is applied, long before `composeAiMavenPublishing { artifactId = … }` has run,
 * and both the publish set and `:bom`'s constraints name modules by artifact id while Gradle
 * addresses them by path. `PublishedArtifactIdsTest` reads the real build files and fails if this
 * table and the declarations disagree in either direction, so the duplication cannot rot.
 */
object PublishedArtifactIds {
  val byProjectPath: Map<String, String> =
    mapOf(
      ":rc-player-trace" to "rc-player-trace",
      ":rc-player-protocol" to "rc-player-protocol",
      ":rc-player-runtime" to "rc-player-runtime",
      ":rc-player-compose" to "rc-player-compose",
      ":rc-player-wasm-dist" to "rc-player-wasm-dist",
      ":third-party-rc-embedded-player" to "third-party-rc-embedded-player",
      ":third-party-rc-embedded-player-jvm" to "third-party-rc-embedded-player-jvm",
      // The vendored TypeScript player's bundle. Named for what it contains rather than for the
      // project that wraps it, and published under that name since 1.53.x — a coordinate cannot be
      // renamed without stranding every consumer that resolves it.
      ":third-party-remote-compose-player-dist" to "remote-compose-player-js-dist",
    )

  /** The artifact id for [projectPath], or null when that project publishes nothing. */
  fun forProjectPath(projectPath: String): String? = byProjectPath[projectPath]
}
