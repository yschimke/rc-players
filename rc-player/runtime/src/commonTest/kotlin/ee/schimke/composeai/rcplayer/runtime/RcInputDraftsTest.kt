package ee.schimke.composeai.rcplayer.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RcInputDraftsTest {
  @Test
  fun liveSnapshotsAndDocumentReplacementDoNotDiscardDrafts() {
    val drafts = RcInputDrafts()
    assertEquals("initial", drafts.value("message", "initial"))
    drafts.update("message", "Hello 🏡\nRoom")
    drafts.update("channel", "matrix")
    assertEquals("Hello 🏡\nRoom", drafts.value("message", "stale snapshot"))
    val submitted = drafts.snapshot()
    drafts.update("message", "next")
    assertEquals("Hello 🏡\nRoom", submitted["message"])
    assertEquals("matrix", submitted["channel"])
    drafts.reset("message")
    assertEquals("server", drafts.value("message", "server"))
    assertEquals("matrix", drafts.value("channel", "other"))
  }

  @Test
  fun unnamedFieldsCannotShareADraft() {
    assertFailsWith<IllegalArgumentException> { RcInputDrafts().update(" ", "hello") }
  }
}
