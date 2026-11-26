package ee.schimke.composeai.rcplayer.compose

import androidx.compose.ui.text.font.FontFamily
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcLongConstant
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

  @Test
  fun appleControllerDrivesLongAndFloatArrayVariablesIntoTheDocument() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          RcFloatList(51, listOf(RcFloatWord.literal(1f), RcFloatWord.literal(2f))),
          RcNamedVariable(51, RcNamedVariable.FLOAT_ARRAY_TYPE, "USER:series"),
          RcLongConstant(52, 5L),
          RcNamedVariable(52, RcNamedVariable.LONG_TYPE, "USER:epoch"),
          RcFloatConstant(53, RcFloatWord.literal(4f)),
          RcNamedVariable(53, RcNamedVariable.FLOAT_TYPE, "USER:progress"),
        ),
      )
    val controller = RcComposePlayerController()
    assertFalse(controller.setLong("epoch", 9L), "before a document is attached")
    controller.attach(RcDocumentCapabilities.of(document))

    assertTrue(controller.setLong("epoch", 1_700_000_000_000L))
    assertTrue(controller.setFloatArray("USER:series", floatArrayOf(5f, 6f, 7f)))
    assertFalse(controller.setLong("progress", 1L))
    assertFalse(controller.setLong("series", 1L))
    assertFalse(controller.setFloatArray("epoch", floatArrayOf(1f)))
    assertFalse(controller.setFloatArray("progress", floatArrayOf(1f)))
    assertFalse(controller.setFloatArray("missing", floatArrayOf(1f)))
    assertFalse(controller.setInteger("epoch", 1))
    assertFalse(controller.setFloat("series", 1f))
    assertEquals(RcNamedValue.LongValue(1_700_000_000_000L), controller.values["USER:epoch"])
    assertEquals(
      RcNamedValue.FloatArrayValue(listOf(5f, 6f, 7f)),
      controller.values["USER:series"],
    )

    // The arrays the native Swift player refuses are refused here too: an oversized one before
    // it is copied, a non-finite one by the same rule on the copy.
    assertFalse(controller.setFloatArray("series", floatArrayOf(1f, Float.NaN)))
    assertFalse(controller.setFloatArray("series", floatArrayOf(Float.POSITIVE_INFINITY)))
    assertFalse(controller.setFloatArray("series", floatArrayOf(Float.NEGATIVE_INFINITY)))
    // Every element is checked, not only the first: one non-finite value anywhere refuses all.
    val full = FloatArray(RcFloatList.MAX_VALUES) { it.toFloat() }
    for (index in intArrayOf(0, 1, RcFloatList.MAX_VALUES / 2, RcFloatList.MAX_VALUES - 1)) {
      for (bad in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
        val values = full.copyOf().also { it[index] = bad }
        assertFalse(controller.setFloatArray("series", values), "$bad at index $index")
      }
    }
    assertFalse(controller.setFloatArray("series", FloatArray(RcFloatList.MAX_VALUES + 1)))
    assertEquals(
      RcNamedValue.FloatArrayValue(listOf(5f, 6f, 7f)),
      controller.values["USER:series"],
      "a refused array replaced the stored one",
    )
    assertTrue(controller.setFloatArray("series", FloatArray(RcFloatList.MAX_VALUES)))
    assertTrue(controller.setFloatArray("series", floatArrayOf(5f, 6f, 7f)))

    val state = RcPlayerState(document, namedValues = controller.values)
    assertEquals(1_700_000_000_000L, state.long(52))
    assertEquals(listOf(5f, 6f, 7f), state.floatValues(51)?.toList())
  }

  /**
   * `clear` removes the stored value, which `RcComposePlayer` hands to `RcPlayerState`'s
   * `clearNamedValue` (`RcNamedValueRenderTest` drives that removal through a live player); the
   * state built from the controller's values then holds the document's own.
   */
  @Test
  fun appleControllerClearRemovesTheValueSoTheDocumentsOwnIsShown() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0)),
        listOf(
          RcFloatConstant(53, RcFloatWord.literal(4f)),
          RcNamedVariable(53, RcNamedVariable.FLOAT_TYPE, "USER:progress"),
          RcFloatList(51, listOf(RcFloatWord.literal(1f), RcFloatWord.literal(2f))),
          RcNamedVariable(51, RcNamedVariable.FLOAT_ARRAY_TYPE, "USER:series"),
          RcNamedVariable(54, RcNamedVariable.INT_TYPE, "light.is_on"),
        ),
      )
    val controller = RcComposePlayerController()
    assertFalse(controller.clear("progress"), "before a document is attached")
    controller.attach(RcDocumentCapabilities.of(document))
    assertTrue(controller.setFloat("progress", 0.5f))
    assertTrue(controller.setFloatArray("series", floatArrayOf(9f)))
    assertTrue(controller.setBoolean("light.is_on", true))

    assertTrue(controller.clear("progress"))
    assertTrue(controller.clear("USER:series"))
    assertTrue(controller.clear("light.is_on"))
    assertTrue(controller.values.isEmpty(), "${controller.values}")
    val state = RcPlayerState(document, namedValues = controller.values)
    assertEquals(4f, state.float(53))
    assertEquals(listOf(1f, 2f), state.floatValues(51)?.toList())

    // Clearing twice, or a name nobody set, is harmless; an undeclared name is refused.
    assertTrue(controller.clear("progress"))
    assertFalse(controller.clear("missing"))
    assertFalse(controller.clear("theme:missing"))

    // The value written before the clear is stored again: the clear forgot it.
    assertTrue(controller.setFloat("progress", 0.5f))
    assertEquals(RcNamedValue.FloatValue(0.5f), controller.values["USER:progress"])

    // Another document that no longer declares the name refuses the clear.
    controller.attach(RcDocumentCapabilities(emptyMap(), emptySet()))
    assertFalse(controller.clear("progress"))
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
