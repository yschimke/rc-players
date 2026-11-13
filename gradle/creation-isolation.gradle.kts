// `verifyCreationApiIsolation`: the launcher modules take one creation API each, or none, and the
// other's libraries must not arrive with them (docs/design/LAUNCHER_EITHER_API.md). Applied with
// `extra["forbidCreationApi"] = "androidx"` (or "cmp", or "both"), then
// `apply(from = rootProject.file("gradle/creation-isolation.gradle.kts"))`.
//
// Checked on the resolved main compile and runtime classpaths of the Android variant: what a
// consumer of the published module gets. Test classpaths are free to hold both (a parity test has
// to), and the two APIs are built to coexist in one app (`:rc-creation-coexistence-check`); this is
// about a consumer that chose one not being made to ship the other.
//
//  - "androidx": AndroidX's creation libraries, `androidx.compose.remote:remote-creation*` and
//    `androidx.compose.remote.foundation`, and `:rc-widget-creation-androidx`.
//  - "cmp": rc-players' creation API, `:rc-creation-*` and `:rc-widget-creation`.
//
// `androidx.compose.remote:remote-core` and the player libraries are neither: they are the wire
// format and the player, which the platform's own player and `rc-player-edit` share.
val forbidden = (extra["forbidCreationApi"] as String)

val classpaths = listOf("androidCompileClasspath", "androidRuntimeClasspath")

val verifyCreationApiIsolation by
  tasks.registering {
    group = "verification"
    description = "Fails if the other creation API's libraries are on this module's classpath."
    val projectPath = project.path
    val forbidden = forbidden
    val roots = classpaths.associateWith { configurations.getByName(it).incoming.resolutionResult.rootComponent }
    doLast {
      // Local, not script functions: a task action can't hold references to the script.
      fun isAndroidxCreation(id: String) =
        id.startsWith("androidx.compose.remote:remote-creation") ||
          id.startsWith("androidx.compose.remote.foundation:") ||
          id == ":rc-widget-creation-androidx"
      fun isCmpCreation(id: String) =
        id.startsWith(":rc-creation-") || id == ":rc-widget-creation"
      fun label(component: org.gradle.api.artifacts.result.ResolvedComponentResult): String {
        val id = component.id
        return if (id is org.gradle.api.artifacts.component.ProjectComponentIdentifier) id.projectPath
        else component.moduleVersion?.let { "${it.group}:${it.name}:${it.version}" } ?: id.displayName
      }
      val wrong = mutableListOf<String>()
      val present = sortedSetOf<String>()
      roots.forEach { (name, root) ->
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(root.get()))
        val visited = hashSetOf<org.gradle.api.artifacts.result.ResolvedComponentResult>()
        while (queue.isNotEmpty()) {
          val component = queue.removeFirst()
          if (!visited.add(component)) continue
          val id = label(component)
          seen.add(id)
          if (isAndroidxCreation(id)) present.add("androidx: $id")
          if (isCmpCreation(id)) present.add("cmp: $id")
          if ((forbidden != "cmp" && isAndroidxCreation(id)) ||
            (forbidden != "androidx" && isCmpCreation(id))
          ) wrong.add("$name: $id")
          component.dependencies
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
            .forEach { queue.add(it.selected) }
        }
      }
      logger.lifecycle(
        "$projectPath creation libraries on its classpath: " +
          (present.joinToString().ifEmpty { "none" })
      )
      check(wrong.isEmpty()) {
        "$projectPath must not bring in the $forbidden creation API:\n  " +
          wrong.joinToString("\n  ")
      }
    }
  }

tasks.named("check") { dependsOn(verifyCreationApiIsolation) }
