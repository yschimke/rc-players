package ee.schimke.composeai.rcplayer.desktop

import ee.schimke.composeai.rcplayer.edit.RcKotlinOptions
import ee.schimke.composeai.rcplayer.edit.RcUiBuilderOptions
import ee.schimke.composeai.rcplayer.edit.toRemoteComposeKotlin
import ee.schimke.composeai.rcplayer.edit.toUiBuilderDesign
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File

/**
 * `--to-kotlin <in.rc> [out.kt]`: the document as a Remote Compose `@Composable`, written to
 * `out.kt` or printed.
 *
 * `--to-uid <in.rc> [out.uid]`: the document as a Compose UI Builder design, with its images beside
 * it in `<out>.assets/`, or printed when there are none to write.
 *
 * Both take `--density <d>`, the density a pixel document was captured at. What the output could
 * not say goes to standard error, one line each; the exit status is 0 either way, since a partial
 * translation is still the useful result.
 */
internal fun decompileCommand(args: List<String>, uid: Boolean): Int {
  val density = args.indexOf("--density").takeIf { it >= 0 }
  val densityValue = density?.let {
    args.getOrNull(it + 1)?.toFloatOrNull()
      ?: run {
        System.err.println("--density needs a number")
        return 2
      }
  }
  val files = args.filterIndexed { i, _ -> density == null || (i != density && i != density + 1) }
  if (files.size !in 1..2) {
    System.err.println(DECOMPILE_USAGE)
    return 2
  }
  val input = File(files[0])
  val document =
    try {
      RcDocumentCodec.decode(input.readBytes())
    } catch (e: Exception) {
      System.err.println("Could not read ${input.path}: ${e.message ?: e}")
      return 1
    }
  val output = files.getOrNull(1)?.let(::File)
  val name = functionName(input)
  return if (uid) writeDesign(document, name, input, output, densityValue)
  else writeKotlin(document, name, output, densityValue)
}

private fun writeKotlin(document: RcDocument, name: String, output: File?, density: Float?): Int {
  val source =
    document.toRemoteComposeKotlin(RcKotlinOptions(functionName = name, density = density))
  emit(source.code, output)
  report(source.unsupported)
  return 0
}

private fun writeDesign(
  document: RcDocument,
  name: String,
  input: File,
  output: File?,
  density: Float?,
): Int {
  val design =
    document.toUiBuilderDesign(
      RcUiBuilderOptions(
        designId = input.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9]+"), "-"),
        title = name,
        density = density,
      )
    )
  if (output == null && design.assets.isNotEmpty()) {
    System.err.println(
      "The design has images; give an output file so they can be written beside it"
    )
    return 2
  }
  emit(design.json, output)
  if (output != null && design.assets.isNotEmpty()) {
    val directory =
      File(output.parentFile, output.nameWithoutExtension + ".assets").apply { mkdirs() }
    design.assets.forEach { (key, bytes) -> File(directory, key).writeBytes(bytes) }
  }
  report(design.unsupported)
  return 0
}

private fun emit(text: String, output: File?) {
  if (output == null) {
    print(text)
    return
  }
  output.parentFile?.mkdirs()
  output.writeText(text)
  println(output.path)
}

private fun report(unsupported: List<String>) {
  if (unsupported.isEmpty()) return
  System.err.println("${unsupported.size} construct(s) could not be expressed:")
  unsupported.forEach { System.err.println("  $it") }
}

/** `button-filled__default.rc` → `ButtonFilledDefault`. */
internal fun functionName(file: File): String {
  val words = file.nameWithoutExtension.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
  val name = words.joinToString("") { word -> word.replaceFirstChar { it.uppercase() } }
  return when {
    name.isEmpty() -> "RemoteDocument"
    name[0].isDigit() -> "Document$name"
    else -> name
  }
}

internal const val DECOMPILE_USAGE =
  """       remote-compose-player --to-kotlin [--density d] <in.rc> [out.kt]
       remote-compose-player --to-uid [--density d] <in.rc> [out.uid]"""
