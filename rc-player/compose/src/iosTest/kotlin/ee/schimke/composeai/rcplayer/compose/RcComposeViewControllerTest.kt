package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.text.font.FontFamily
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
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
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
            listOf(RcTextStyleProperty.IntValue(1, 100), RcTextStyleProperty.IntValue(8, 42)),
          ),
        ),
      )
    val errors = mutableListOf<String>()

    RcComposeViewController(RcDocumentCodec.encode(document), onError = errors::add)

    assertTrue(errors.single().contains("Missing Face"), errors.single())
  }

  @Test
  fun defaultAppleControllerAcceptsAppleSystemFont() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          RcTextData(42, "apple:system"),
          RcTextStyle(
            listOf(RcTextStyleProperty.IntValue(1, 100), RcTextStyleProperty.IntValue(8, 42)),
          ),
        ),
      )
    val errors = mutableListOf<String>()

    RcComposeViewController(RcDocumentCodec.encode(document), onError = errors::add)

    assertTrue(errors.isEmpty(), errors.joinToString())
    assertTrue("apple:system" in RcAppleTypefaceLoader.families)
  }

  @Test
  fun appleLoaderMapsBuiltInTypefaceIdsToSystemDesignsAndAllowsOverrides() {
    assertSame(FontFamily.Default, RcAppleTypefaceLoader.typeface("default"))
    assertSame(FontFamily.Default, RcAppleTypefaceLoader.typeface("sans-serif"))
    assertSame(FontFamily.Serif, RcAppleTypefaceLoader.typeface("serif"))
    assertSame(FontFamily.Monospace, RcAppleTypefaceLoader.typeface("monospace"))

    val overriding =
      object : RcTypefaceLoader {
        override val families: Set<String> = setOf("serif")

        override fun typeface(
          family: String,
          variations: RcFontVariations?,
        ): FontFamily? = if (family == "serif") FontFamily.Cursive else null
      }
    assertSame(FontFamily.Cursive, rcAppleTypefaceLoader(overriding).typeface("serif"))
  }

  @Test
  fun appleLoaderAdvertisesAndResolvesInstalledFamiliesByName() {
    val semanticFamilies =
      setOf(
        "apple:system",
        "apple:sf pro",
        "apple:sf pro text",
        "apple:sf pro display",
        "apple:serif",
        "apple:new york",
        "apple:monospaced",
        "apple:sf mono",
        "apple:*",
      )
    val installed =
      RcAppleTypefaceLoader.families.first { it.startsWith("apple:") && it !in semanticFamilies }

    assertNotNull(RcAppleTypefaceLoader.typeface(installed))
    assertSame(
      FontFamily.Default,
      RcAppleTypefaceLoader.typeface("apple:not an installed font"),
    )
  }

  @Test
  fun downloadableFontBridgeFindsGoogleFamiliesAndBuildsLoader() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          RcTextData(42, "google:Orbitron"),
          RcTextData(43, "Not a font: google:Unused"),
          RcTextStyle(
            listOf(RcTextStyleProperty.IntValue(1, 100), RcTextStyleProperty.IntValue(8, 42)),
          ),
        ),
      )

    val requests = rcDownloadableFontRequests(RcDocumentCodec.encode(document))
    assertEquals(listOf("Orbitron"), requests.map { it.family })

    val loader =
      rcDownloadedTypefaceLoader(
        listOf(RcDownloadedFont("Orbitron", "test-orbitron", byteArrayOf(1, 2, 3))),
      )
    assertEquals(setOf("orbitron"), loader.families)
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
      ),
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

    controller.attach(
      RcDocumentCapabilities(
        namedValues =
          mapOf(
            "USER:progress" to RcNamedVariable.STRING_TYPE,
            "USER:title" to RcNamedVariable.STRING_TYPE,
          ),
        colorThemeGroups = emptySet(),
      ),
    )

    assertEquals(listOf("USER:progress", "USER:title"), controller.names)
    assertFalse("USER:progress" in controller.values)
    assertEquals(RcNamedValue.Text("Ready"), controller.values["USER:title"])
  }

  @Test
  fun appleControllerDrivesIntegerAndBooleanVariablesIntoTheDocument() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          RcNamedVariable(50, RcNamedVariable.INT_TYPE, "USER:count"),
          RcNamedVariable(51, RcNamedVariable.INT_TYPE, "USER:enabled"),
          RcNamedVariable(52, RcNamedVariable.LONG_TYPE, "USER:epoch"),
          RcNamedVariable(53, RcNamedVariable.FLOAT_TYPE, "USER:progress"),
          RcNamedVariable(54, RcNamedVariable.INT_TYPE, "light.is_on"),
        ),
      )
    val controller = RcComposePlayerController()
    controller.attach(RcDocumentCapabilities.of(document))

    assertTrue(controller.setInteger("count", 7))
    assertTrue(controller.setBoolean("USER:enabled", true))
    assertFalse(controller.setInteger("progress", 1))
    assertFalse(controller.setBoolean("progress", true))
    assertFalse(controller.setInteger("epoch", 1))
    assertFalse(controller.setInteger("missing", 1))
    assertFalse(controller.setBoolean("missing", true))
    assertFalse(controller.setFloat("count", 1f))
    assertEquals(RcNamedValue.Integer(7), controller.values["USER:count"])
    assertEquals(RcNamedValue.BooleanValue(true), controller.values["USER:enabled"])
    assertFalse("USER:progress" in controller.values)

    // A name declared without a namespace is addressed bare, as RcPlayerState resolves it.
    assertTrue(controller.setBoolean("light.is_on", true))
    assertEquals(RcNamedValue.BooleanValue(true), controller.values["light.is_on"])

    val state = RcPlayerState(document, namedValues = controller.values)
    assertEquals(7, state.integer(50))
    assertEquals(1, state.integer(51))
    assertEquals(1, state.integer(54))

    assertTrue(controller.setBoolean("enabled", false))
    assertEquals(0, RcPlayerState(document, namedValues = controller.values).integer(51))

    controller.attach(
      RcDocumentCapabilities(
        namedValues =
          mapOf(
            "USER:count" to RcNamedVariable.FLOAT_TYPE,
            "USER:enabled" to RcNamedVariable.INT_TYPE,
          ),
        colorThemeGroups = emptySet(),
      ),
    )

    assertFalse("USER:count" in controller.values)
    assertEquals(RcNamedValue.BooleanValue(false), controller.values["USER:enabled"])
  }

  /**
   * The controller must address the variable RcPlayerState itself would for the same host name, so
   * this compares against the runtime rather than a table of expectations: each name is written
   * once through the controller and once straight into an RcPlayerState, and both must land on the
   * same variable — or, for a name the document does not declare, on none.
   */
  @Test
  fun appleControllerResolvesNamesAsTheRuntimeDoes() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          // Declared both in USER: and bare: a bare host name means the USER: one.
          RcNamedVariable(60, RcNamedVariable.INT_TYPE, "USER:both"),
          RcNamedVariable(61, RcNamedVariable.INT_TYPE, "both"),
          // Declared bare only: reachable bare, not through a USER: spelling.
          RcNamedVariable(62, RcNamedVariable.INT_TYPE, "light.is_on"),
          RcNamedVariable(63, RcNamedVariable.INT_TYPE, "USER:count"),
        ),
      )
    val sentinel = 42
    val declaredNames =
      document.operations.filterIsInstance<RcNamedVariable>().associate { it.id to it.name }
    fun landedOn(state: RcPlayerState): Int? =
      declaredNames.keys.singleOrNull { state.integer(it) == sentinel }

    val names =
      listOf(
        "both",
        "USER:both",
        "light.is_on",
        "USER:light.is_on",
        "count",
        "USER:count",
        "missing",
        "theme:missing",
      )
    for (name in names) {
      val runtime = RcPlayerState(document)
      runtime.setNamedValue(name, RcNamedValue.Integer(sentinel))
      val expected = landedOn(runtime)

      val controller = RcComposePlayerController()
      controller.attach(RcDocumentCapabilities.of(document))
      val accepted = controller.setInteger(name, sentinel)
      val actual = landedOn(RcPlayerState(document, namedValues = controller.values))

      assertEquals(expected != null, accepted, "accepted '$name'")
      assertEquals(expected, actual, "variable '$name' addresses")
      // The key itself, not only where RcPlayerState applies it: the runtime resolves a key again,
      // which would hide a controller that stored a bare name the runtime then re-routes.
      assertEquals(
        setOfNotNull(expected?.let(declaredNames::getValue)),
        controller.values.keys.toSet(),
        "key '$name' is stored under",
      )
    }
    // The cases the comparison above relies on, spelled out.
    assertEquals(
      60,
      landedOn(
        RcPlayerState(document).apply { setNamedValue("both", RcNamedValue.Integer(sentinel)) },
      ),
    )
    assertEquals(
      62,
      landedOn(
        RcPlayerState(document).apply {
          setNamedValue("light.is_on", RcNamedValue.Integer(sentinel))
        },
      ),
    )
  }

  /**
   * A bare key stored for one document is re-resolved against the next, as `RcPlayerState` will
   * resolve it. Checking the key as written kept `count` (INT in the old document), which the new
   * document's `USER:count` (FLOAT) then rejected by throwing.
   */
  @Test
  fun appleControllerReResolvesStoredNamesWhenTheDocumentChanges() {
    fun document(vararg variables: RcNamedVariable) =
      RcDocument(RcHeader(RcVersion(1, 0, 0)), variables.toList())

    val controller = RcComposePlayerController()
    controller.attach(
      RcDocumentCapabilities.of(document(RcNamedVariable(1, RcNamedVariable.INT_TYPE, "count"))),
    )
    assertTrue(controller.setInteger("count", 1))
    assertEquals(setOf("count"), controller.values.keys.toSet())

    val conflicting =
      document(
        RcNamedVariable(1, RcNamedVariable.INT_TYPE, "count"),
        RcNamedVariable(2, RcNamedVariable.FLOAT_TYPE, "USER:count"),
      )
    controller.attach(RcDocumentCapabilities.of(conflicting))
    assertEquals(emptySet(), controller.values.keys.toSet())
    // Built exactly as the player builds its state; this threw before the fix.
    RcPlayerState(conflicting, namedValues = controller.values)

    // The value is carried over, under the resolved name, when the types agree.
    controller.attach(
      RcDocumentCapabilities.of(document(RcNamedVariable(1, RcNamedVariable.INT_TYPE, "count"))),
    )
    assertTrue(controller.setInteger("count", 2))
    val retyped = document(RcNamedVariable(3, RcNamedVariable.INT_TYPE, "USER:count"))
    controller.attach(RcDocumentCapabilities.of(retyped))
    assertEquals(setOf("USER:count"), controller.values.keys.toSet())
    assertEquals(2, RcPlayerState(retyped, namedValues = controller.values).integer(3))
  }
}
