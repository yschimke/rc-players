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
            listOf(
              RcInputField(
                "message",
                "Message",
                "stale live state",
                submit = "configured",
                chrome = RcInputChrome.NONE,
              )
            )
          )
      }
      onNodeWithText("Message").assertDoesNotExist()
      onNode(hasContentDescription("Message")).assertIsFocused()
      onNode(hasSetTextAction()).assertTextEquals("Hello 🏡")
      onNode(hasSetTextAction()).performImeAction()
      runOnIdle { assertEquals(listOf(mapOf("message" to "Hello 🏡")), submissions) }
    }

  @Test
  fun submitOmitsRemovedFieldsButRetainsRemainingDrafts() =
    runSkikoComposeUiTest(size = Size(360f, 360f), density = Density(1f)) {
      val drafts = RcInputDrafts()
      val document =
        mutableStateOf(
          rcInputDocument(
            listOf(
              RcInputField("obsolete", "Old field", "old"),
              RcInputField("room", "Room", "Kitchen"),
              RcInputField("message", "Message", submit = "send"),
            )
          )
        )
      val submissions = mutableListOf<Map<String, String>>()
      setContent {
        RcComposePlayer(
          document.value,
          customComponents =
            rcInputComponents(
              drafts,
              editable = true,
              onSubmit = { _, values -> submissions += values },
            ),
        )
      }
      onNode(hasContentDescription("Message")).performTextInput("Keep me")
      runOnIdle {
        document.value =
          rcInputDocument(
            listOf(
              RcInputField("room", "Room", "Living room"),
              RcInputField("message", "Message", submit = "send"),
            )
          )
      }
      onNode(hasContentDescription("Message")).performImeAction()
      runOnIdle {
        assertEquals(listOf(mapOf("room" to "Living room", "message" to "Keep me")), submissions)
      }
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
  fun expandedControlsReturnStringsAndRejectInvalidNumericSubmit() =
    runSkikoComposeUiTest(size = Size(360f, 440f), density = Density(1f)) {
      val drafts = RcInputDrafts()
      val submissions = mutableListOf<Map<String, String>>()
      val registry =
        rcInputComponents(
          drafts,
          editable = true,
          onSubmit = { _, values -> submissions += values },
        )
      setContent {
        RcComposePlayer(
          rcInputDocument(
            listOf(
              RcInputs.toggle("urgent", "Urgent"),
              RcInputs.number("temperature", "Temperature", 21.5, 0.0, 30.0, 0.5, "apply"),
              RcInputs.range("brightness", "Brightness", 10.0, 10.0, 20.0, 2.0),
            )
          ),
          customComponents = registry,
        )
      }
      onNode(hasContentDescription("Urgent")).performClick()
      onNode(hasContentDescription("Temperature")).performTextReplacement("-")
      onNodeWithText("Send").performClick()
      runOnIdle { assertTrue(submissions.isEmpty()) }
      onNode(hasContentDescription("Temperature")).performTextReplacement("22.5")
      onNode(hasContentDescription("Brightness")).performSemanticsAction(
        androidx.compose.ui.semantics.SemanticsActions.SetProgress
      ) {
        it(3f)
      }
      onNodeWithText("Send").performClick()
      runOnIdle {
        assertEquals(
          listOf(mapOf("urgent" to "true", "temperature" to "22.5", "brightness" to "16.0")),
          submissions,
        )
      }
    }

  @Test
  fun disabledControlAndReadOnlyExpandedControlsStayNonInteractive() =
    runSkikoComposeUiTest(size = Size(360f, 440f), density = Density(1f)) {
      setContent {
        RcComposePlayer(
          rcInputDocument(
            listOf(
              RcInputs.toggle("urgent", "Urgent"),
              RcInputs.number("temperature", "Temperature"),
              RcInputs.range("brightness", "Brightness", 50.0),
            )
          ),
          customComponents = rcInputComponents(RcInputDrafts()),
        )
      }
      onAllNodes(hasSetTextAction()).assertCountEquals(0)
      onAllNodes(hasClickAction()).assertCountEquals(0)
      onAllNodesWithText("Open in app to edit").assertCountEquals(3)
    }

  @Test
  fun disabledFieldsAreExcludedAndActionCaptionsAreAuthored() =
    runSkikoComposeUiTest(size = Size(360f, 280f), density = Density(1f)) {
      val submissions = mutableListOf<Map<String, String>>()
      val registry =
        rcInputComponents(
          RcInputDrafts(),
          editable = true,
          onSubmit = { _, values -> submissions += values },
        )
      setContent {
        RcComposePlayer(
          rcInputDocument(
            listOf(
              RcInputs.toggle("disabled", "Disabled").copy(enabled = false),
              RcInputs.text("message", "Message", submit = "apply").copy(submitLabel = "Apply"),
            )
          ),
          customComponents = registry,
        )
      }
      onNodeWithText("Unavailable").assertExists()
      onNode(hasContentDescription("Message")).performTextInput("hello")
      onNodeWithText("Apply").performClick()
      runOnIdle { assertEquals(listOf(mapOf("message" to "hello")), submissions) }
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
