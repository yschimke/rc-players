package ee.schimke.composeai.rcplayer.desktop

import ee.schimke.composeai.rcplayer.edit.structuralDiff
import ee.schimke.composeai.rcplayer.edit.toStructuralText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File

/**
 * `--dump <in.rc>`: the document as structural text (`toStructuralText`), one operation a line,
 * nested by container. It is a pure function of the bytes, so it serves as a `git diff` textconv:
 * ```
 * git config diff.rc.textconv "remote-compose-player --dump"
 * echo '*.rc diff=rc' >> .gitattributes
 * ```
 *
 * `--diff <before.rc> <after.rc>`: what changed between two versions, explained (`structuralDiff`),
 * then the line diff of their structural text. The exit status follows `diff`'s: 0 when they are
 * identical, 1 when they differ, 2 when one could not be read.
 */
internal fun dumpCommand(args: List<String>): Int {
  if (args.size != 1) {
    System.err.println(STRUCTURE_USAGE)
    return 2
  }
  val document = readDocument(File(args[0])) ?: return 2
  print(document.toStructuralText())
  return 0
}

internal fun diffCommand(args: List<String>): Int {
  if (args.size != 2) {
    System.err.println(STRUCTURE_USAGE)
    return 2
  }
  val before = readDocument(File(args[0])) ?: return 2
  val after = readDocument(File(args[1])) ?: return 2
  val diff = before.structuralDiff(after, beforeName = args[0], afterName = args[1])
  print(diff)
  return if (diff.isIdentical) 0 else 1
}

private fun readDocument(file: File): RcDocument? =
  try {
    RcDocumentCodec.decode(file.readBytes())
  } catch (e: Exception) {
    System.err.println("Could not read ${file.path}: ${e.message ?: e}")
    null
  }

internal const val STRUCTURE_USAGE =
  """       remote-compose-player --dump <in.rc>                 the document as text, one operation a line
       remote-compose-player --diff <before.rc> <after.rc>  what changed, explained, then the line diff"""
