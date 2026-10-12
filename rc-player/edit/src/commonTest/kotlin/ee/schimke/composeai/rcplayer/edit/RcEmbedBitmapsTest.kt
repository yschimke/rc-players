package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RcEmbedBitmapsTest {
  private fun urlBitmap(id: Int, url: String) =
    RcBitmapData(
      imageId = id,
      width = 10,
      height = 20,
      type = RcBitmapData.TYPE_PNG_8888,
      encoding = RcBitmapData.ENCODING_URL,
      data = url.encodeToByteArray(),
    )

  /** The first 24 bytes of a PNG: signature, then an IHDR declaring [width] x [height]. */
  private fun png(width: Int, height: Int): ByteArray =
    byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d) +
      "IHDR".encodeToByteArray() +
      byteArrayOf(0, 0, (width shr 8).toByte(), width.toByte()) +
      byteArrayOf(0, 0, (height shr 8).toByte(), height.toByte())

  private val image = RcImageLayout(7, -1, bitmapId = 42, scaleType = 1, RcFloatWord.literal(1f))

  @Test
  fun aUrlBitmapBecomesAnInlineBitmapWithTheSameIdAndThePngSize() {
    val source =
      document(urlBitmap(42, "https://example.com/a.png"), *rootLayout(image, END).toTypedArray())
    val bytes = png(64, 32)

    val embedded =
      assertRoundTrips(source.embedBitmapUrls(mapOf("https://example.com/a.png" to bytes)))

    val bitmap = embedded.operations.filterIsInstance<RcBitmapData>().single()
    assertEquals(42, bitmap.imageId)
    assertEquals(RcBitmapData.ENCODING_INLINE, bitmap.encoding)
    assertEquals(RcBitmapData.TYPE_PNG_8888, bitmap.type)
    assertEquals(64 to 32, bitmap.width to bitmap.height)
    assertContentEquals(bytes, bitmap.data)
    // Everything else, the image layout naming the bitmap included, is untouched.
    assertEquals(source.operations.drop(1), embedded.operations.drop(1))
  }

  @Test
  fun bytesThatAreNotAPngKeepTheDeclaredSize() {
    val source = document(urlBitmap(42, "u"))
    val bitmap =
      source.embedBitmapUrls(mapOf("u" to byteArrayOf(1, 2, 3))).operations.single() as RcBitmapData
    assertEquals(10 to 20, bitmap.width to bitmap.height)
    assertEquals(RcBitmapData.ENCODING_INLINE, bitmap.encoding)
  }

  @Test
  fun fetchIsCalledOncePerUrlAndANullKeepsTheUrl() {
    val source = document(urlBitmap(42, "a"), urlBitmap(43, "a"), urlBitmap(44, "b"))
    assertEquals(listOf("a", "b"), source.bitmapUrls)
    val fetched = mutableListOf<String>()

    val embedded = source.embedBitmapUrls { url ->
      fetched += url
      if (url == "a") png(1, 1) else null
    }

    assertEquals(listOf("a", "b"), fetched)
    val encodings = embedded.operations.map { (it as RcBitmapData).encoding }
    assertEquals(
      listOf(RcBitmapData.ENCODING_INLINE, RcBitmapData.ENCODING_INLINE, RcBitmapData.ENCODING_URL),
      encodings,
    )
    assertEquals(listOf("b"), embedded.bitmapUrls)
  }

  @Test
  fun nothingToEmbedReturnsTheSameDocument() {
    val source = document(urlBitmap(42, "a"))
    assertSame(source, source.embedBitmapUrls(mapOf("other" to png(1, 1))))
  }
}
