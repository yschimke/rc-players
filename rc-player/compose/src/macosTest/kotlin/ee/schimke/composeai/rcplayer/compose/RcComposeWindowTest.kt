package ee.schimke.composeai.rcplayer.compose

import kotlin.test.Test
import kotlin.test.assertEquals

class RcComposeWindowTest {
  @Test
  fun invalidDocumentReportsErrorWithoutOpeningWindow() {
    val errors = mutableListOf<String>()

    RcComposeWindow(byteArrayOf(), onError = errors::add)

    assertEquals(1, errors.size)
  }
}
