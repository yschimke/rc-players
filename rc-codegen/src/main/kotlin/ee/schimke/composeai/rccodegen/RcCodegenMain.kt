package ee.schimke.composeai.rccodegen

import androidx.compose.remote.generator.RemoteCodegenTarget
import androidx.compose.remote.generator.RemoteSerializationCodeGenerator
import androidx.compose.remote.generator.builtInTargets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Runs code generation targets over the forked AndroidX schema.
 *
 * ```
 * rc-codegen --target=<id> --out=<directory>
 * rc-codegen --list
 * rc-codegen --sync=<repository root> [--check]
 * ```
 *
 * `--sync` writes every output the repository commits ([committedOutputs]) into the checkout; with
 * `--check` it writes nothing and fails if any committed file differs from what the targets
 * generate now.
 *
 * The targets are upstream's languages (`cpp`, `rust`, `python`, `typescript`), the fork's Swift 6
 * bindings (`swift`), and rc-players' own ([rcPlayersTargets]).
 */
object RcCodegenMain {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = args.associate { argument ->
      val (key, value) =
        argument.removePrefix("--").split("=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
      key to value
    }
    val targets = allTargets()
    options["sync"]?.let { root ->
      val problems = sync(Path.of(root).toAbsolutePath(), check = "check" in options)
      if (problems.isNotEmpty()) {
        System.err.println(problems.joinToString("\n"))
        System.err.println(
          "Run `./gradlew :rc-codegen:generateSwiftSources` and commit the result.",
        )
        exitProcess(1)
      }
      return
    }
    if ("list" in options) {
      targets.forEach { println(it.id) }
      return
    }
    val id = options["target"] ?: usage("--target is required")
    val out = options["out"]?.let(Path::of) ?: usage("--out is required")
    val target = targets.firstOrNull { it.id == id } ?: usage("unknown target $id")
    RemoteSerializationCodeGenerator.generate(target, out.toAbsolutePath())
  }

  /**
   * The generated files this repository commits: each target, with the directory (relative to the
   * repository root) its paths are resolved against.
   */
  fun committedOutputs(root: Path): List<Pair<RemoteCodegenTarget, Path>> {
    val targets = allTargets().associateBy { it.id }
    return listOf(targets.getValue("swift") to root.resolve("Sources/RcWireOperations"))
  }

  /** Writes, or with [check] compares, every committed output. Returns the differences found. */
  fun sync(root: Path, check: Boolean): List<String> {
    val schema = RemoteSerializationCodeGenerator.loadBundledSchema()
    val problems = mutableListOf<String>()
    for ((target, directory) in committedOutputs(root)) {
      for ((relative, contents) in target.generate(schema)) {
        val file = directory.resolve(relative)
        val committed = if (Files.exists(file)) Files.readString(file) else null
        when {
          committed == contents -> Unit
          check -> problems += "${root.relativize(file)} is out of date (target ${target.id})"
          else -> {
            Files.createDirectories(file.parent)
            Files.writeString(file, contents)
            println("wrote ${root.relativize(file)}")
          }
        }
      }
    }
    return problems
  }

  /** Every target this tool knows, upstream's and the fork's first. */
  fun allTargets(): List<RemoteCodegenTarget> = builtInTargets() + rcPlayersTargets()

  private fun usage(problem: String): Nothing {
    System.err.println(
      "rc-codegen: $problem\n" +
        "usage: rc-codegen --target=<id> --out=<dir>\n" +
        "       rc-codegen --list",
    )
    exitProcess(2)
  }
}

/**
 * rc-players' own targets: platforms or outputs upstream does not have. None today; RcCreation
 * writes through the `swift` bindings, which carry every constant it needs. Add one here, and to
 * [RcCodegenMain.committedOutputs] if its output is committed.
 */
fun rcPlayersTargets(): List<RemoteCodegenTarget> = emptyList()
