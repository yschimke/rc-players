package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.*
import ee.schimke.composeai.rcplayer.runtime.RcInputDrafts
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RcInputComponentsTest {
  @Test
  fun typingSurvivesLiveDocumentReplacementAndSubmitsOnce() =
    runSkikoComposeUiTest(size = Size(360f, 240f), density = Density(1f)) {
      val drafts = RcInputDrafts()
      val document =
        mutableStateOf(
          rcInputDocument(listOf(RcInputField("message", "Message", submit = "configured")))
        )
      val events = mutableListOf<Pair<String, String>>()
      val submissions = mutableListOf<Map<String, String>>()
      val registry =
        rcInputComponents(
          drafts,
          editable = true,
          onChange = { key, value -> events += key to value },
          onSubmit = { action, values ->
            assertEquals("configured", action)
            submissions += values
          },
        )
      setContent { RcComposePlayer(document.value, customComponents = registry) }
      onNode(hasSetTextAction()).performTextInput("Hello 🏡")
      runOnIdle {
        assertEquals("Hello 🏡", drafts.snapshot()["message"])
        assertEquals("message" to "Hello 🏡", events.last())
        document.value =
          rcInputDocument(
            listOf(RcInputField("message", "Message", "stale live state", submit = "configured"))
          )
      }
      onNode(hasSetTextAction()).assertTextEquals("Hello 🏡")
      onNode(hasSetTextAction()).performImeAction()
      runOnIdle { assertEquals(listOf(mapOf("message" to "Hello 🏡")), submissions) }
    }

  @Test
  fun readOnlyHostShowsAlternativeAndNeverAcceptsInput() =
    runSkikoComposeUiTest(size = Size(360f, 240f), density = Density(1f)) {
      val registry =
        rcInputComponents(RcInputDrafts(), onSubmit = { _, _ -> fail("Read-only submit") })
      setContent {
        RcComposePlayer(
          rcInputDocument(
            listOf(RcInputField("message", "Message", "Hello", submit = "configured"))
          ),
          customComponents = registry,
        )
      }
      onAllNodes(hasSetTextAction()).assertCountEquals(0)
      onNodeWithText("Open in app to edit").assertExists()
      onNodeWithText("Send").assertDoesNotExist()
    }

  @Test
  fun choiceUsesOnlyDeclaredOptions() =
    runSkikoComposeUiTest(size = Size(360f, 240f), density = Density(1f)) {
      val drafts = RcInputDrafts()
      setContent {
        RcComposePlayer(
          rcInputDocument(
            listOf(
              RcInputField("room", "Contact / Room", "Kitchen", listOf("Kitchen", "Living room"))
            )
          ),
          customComponents = rcInputComponents(drafts, editable = true),
        )
      }
      onAllNodes(hasSetTextAction()).assertCountEquals(0)
      onNodeWithText("Kitchen").performClick()
      onNodeWithText("Living room").performClick()
      runOnIdle { assertEquals("Living room", drafts.snapshot()["room"]) }
    }

  @Test
  fun opcodeSupportAloneDoesNotAdvertiseInputCapability() {
    val document = rcInputDocument(listOf(RcInputField("message", "Message")))
    assertFalse(document.composeSupportReport().fullyRenderable)
    val registry = rcInputComponents(RcInputDrafts(), editable = false)
    assertTrue(
      document.composeSupportReport(availableCustomComponents = registry.names).fullyRenderable
    )
  }
}
