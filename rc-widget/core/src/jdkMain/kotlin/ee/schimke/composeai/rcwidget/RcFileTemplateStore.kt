package ee.schimke.composeai.rcwidget

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Templates on disk under [root], in a directory named by [scope]: what a capture depends on beyond
 * its key (the build, the display's density and font scale). Any other directory under [root] is
 * stale, and [prune] deletes it. Files are named by the key's SHA-256 and written atomically.
 */
public class RcFileTemplateStore(
  private val root: File,
  /** The directory name for what captures depend on now; read on every access. */
  private val scope: () -> String = { "templates" },
) : RcTemplateStore {
  private fun scopeDir(): File = File(root, scope())

  private fun file(key: String): File =
    File(scopeDir(), RcSha256.hex(key.encodeToByteArray()) + ".rc")

  override fun read(key: String): ByteArray? = runCatching {
    file(key).takeIf { it.isFile }?.readBytes()
  }
    .getOrNull()

  override fun write(key: String, bytes: ByteArray) {
    runCatching {
      val target = file(key)
      val dir = requireNotNull(target.parentFile).apply { mkdirs() }
      val tmp = File.createTempFile("${target.name}.", ".tmp", dir)
      try {
        tmp.writeBytes(bytes)
        Files.move(
          tmp.toPath(),
          target.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE,
        )
      } finally {
        tmp.delete()
      }
    }
  }

  /**
   * Deletes every scope directory but the current one: templates an earlier build or display made.
   */
  public fun prune() {
    val current = scopeDir()
    root.listFiles()?.filter { it != current }?.forEach { it.deleteRecursively() }
  }

  /** Deletes every template it holds. */
  public fun clear() {
    root.deleteRecursively()
  }
}
