package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyle
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.runtime.RcDocumentCapabilities
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RcComposeViewControllerTest {
  @Test
  fun malformedDocumentReportsAHostErrorAndReturnsAFallbackController() {
    val errors = mutableListOf<String>()

    val controller = RcComposeViewController(byteArrayOf(1, 2, 3), onError = errors::add)

    assertNotNull(controller)
    assertEquals(1, errors.size)
  }

  @Test
  fun unavailableExternalFontReportsAResourceError() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          RcTextData(42, "google:Missing Face"),
          RcTextStyle(
            listOf(RcTextStyleProperty.IntValue(1, 100), RcTextStyleProperty.IntValue(8, 42))
          ),
        ),
      )
    val errors = mutableListOf<String>()

    RcComposeViewController(RcDocumentCodec.encode(document), onError = errors::add)

    assertTrue(errors.single().contains("Missing Face"), errors.single())
  }

  @Test
  fun playerEventsReachTheIosHostCallback() {
    val events = mutableListOf<RcPlayerEvent>()
    val event = RcPlayerEvent.HostAction(17)

    forwardIosPlayerEvent(events::add, event)

    assertEquals(event, events.single())
  }

  @Test
  fun transparentFallbackControllerPreservesAlpha() {
    val controller =
      RcComposeViewController(
        byteArrayOf(1, 2, 3),
        RcPlayerTheme.System,
        {},
        RcTypefaceLoader.Default,
        {},
        lenient = false,
        opaque = false,
      )

    assertFalse(controller.view.opaque)
  }

  @Test
  fun appleControllerPublishesNamesAndTypeChecksLiveValues() {
    val controller = RcComposePlayerController()
    controller.attach(
      RcDocumentCapabilities(
        namedValues =
          mapOf(
            "USER:progress" to RcNamedVariable.FLOAT_TYPE,
            "USER:title" to RcNamedVariable.STRING_TYPE,
            "theme:accent" to RcNamedVariable.COLOR_TYPE,
          ),
        colorThemeGroups = emptySet(),
      )
    )

    assertEquals(listOf("USER:progress", "USER:title", "theme:accent"), controller.names)
    assertTrue(controller.setFloat("progress", 0.5f))
    assertTrue(controller.setString("title", "Ready"))
    assertTrue(controller.setColor("theme:accent", 0xff336699.toInt()))
    assertFalse(controller.setString("progress", "wrong type"))
    assertFalse(controller.setFloat("missing", 1f))
    assertEquals(RcNamedValue.FloatValue(0.5f), controller.values["USER:progress"])
    assertEquals(RcNamedValue.Text("Ready"), controller.values["USER:title"])
    assertEquals(RcNamedValue.Color(0xff336699.toInt()), controller.values["theme:accent"])
  }
}
