package ee.schimke.composeai.rcplayer.wasm

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * JavaScript's `encodeURIComponent` for well-formed text: UTF-8, every byte but its unreserved set
 * escaped. The rows the tests build stand for what `Main.kt`'s `flattenNamedValuesFromLocation`
 * writes; whether that JavaScript really encodes every field is the smoke run's to catch (its
 * `named-values-elements` case, "a kind that spells another row"), not these tests'.
 */
internal fun encodeUriComponent(text: String): String = buildString {
  for (byte in text.toByteArray(Charsets.UTF_8)) {
    val c = byte.toInt().toChar()
    if (byte >= 0 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-_.!~*'()")) {
      append(c)
    } else {
      append('%').append("%02X".format(byte.toInt() and 0xFF))
    }
  }
}

/**
 * JavaScript's `decodeURIComponent`, which the browser hands [namedValuesFromRows]: unlike
 * `java.net.URLDecoder` it keeps `+` as `+`, and it throws (a URIError there) on a `%` not followed
 * by two hex digits and on escapes that are not well-formed UTF-8, rather than substituting.
 */
internal fun decodeUriComponent(text: String): String {
  val out = StringBuilder()
  var i = 0
  while (i < text.length) {
    if (text[i] != '%') {
      out.append(text[i++])
      continue
    }
    val bytes = ByteArrayOutputStream()
    while (i < text.length && text[i] == '%') {
      val hex = text.substring(i + 1, minOf(i + 3, text.length))
      val byte =
        hex
          .takeIf { it.length == 2 && it.all { c -> c in "0123456789abcdefABCDEF" } }
          ?.toIntOrNull(16)
      requireNotNull(byte) { "URIError: malformed escape at $i" }
      bytes.write(byte)
      i += 3
    }
    val decoder =
      Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    try {
      out.append(decoder.decode(ByteBuffer.wrap(bytes.toByteArray())))
    } catch (error: CharacterCodingException) {
      throw IllegalArgumentException("URIError: not UTF-8", error)
    }
  }
  return out.toString()
}

/** The test decoder is held to `decodeURIComponent`'s results, as V8 (Node) gives them. */
class JsUriComponentTest {
  @Test
  fun decodesAsDecodeUriComponent() {
    assertEquals("a+b c", decodeUriComponent("a+b%20c"))
    assertEquals("é😀", decodeUriComponent("%C3%A9%F0%9F%98%80"))
    assertEquals("%", decodeUriComponent("%25"))
    for (text in listOf("é😀 +%#\u0000\u0001", "😀ש́‮", "")) {
      assertEquals(text, decodeUriComponent(encodeUriComponent(text)), text)
    }
  }

  @Test
  fun refusesWhatDecodeUriComponentRefuses() {
    // Each throws a URIError in the browser.
    for (text in listOf("%", "%2", "%G0", "a%", "%FF", "%C3", "%E0%A4%A", "%ED%A0%80", "%C0%AF")) {
      assertFailsWith<IllegalArgumentException>(text) { decodeUriComponent(text) }
    }
  }
}
