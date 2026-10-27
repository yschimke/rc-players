package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.edit.RcProfiles
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.namedVariables
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedAction
import ee.schimke.composeai.rcplayer.protocol.RcHostNamedActionValue
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRoundedClipRectModifier
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val END: RcOperation = RcNoArg(RcOpcodes.CONTAINER_END)

private fun document(vararg operations: RcOperation): RcDocument =
  RcDocument(RcHeader(RcVersion(1, 1, 0)), operations.toList())

private fun roundTrip(document: RcDocument): RcDocument =
  RcDocumentCodec.decode(RcDocumentCodec.encode(document))

class RcPlayerCapabilitiesTest {
  @Test
  fun headerValueRoundTrips() {
    val caps = RcPlayerCapabilities(7, RcProfiles.WIDGETS, density = 2.625f, fontScale = 1.15f)
    assertEquals("api=7; profiles=0x100; density=2.625; font-scale=1.15", caps.headerValue())
    assertEquals(caps, RcPlayerCapabilities.parse(caps.headerValue()))
  }

  @Test
  fun parseIgnoresUnknownFieldsAndDefaultsProfiles() {
    assertEquals(
      RcPlayerCapabilities(6, RcProfiles.BASELINE),
      RcPlayerCapabilities.parse(" API=6 ; colour=blue"),
    )
    assertEquals(RcPlayerCapabilities(8, 0x201), RcPlayerCapabilities.parse("api=8; profiles=513"))
    assertNull(RcPlayerCapabilities.parse("profiles=0x100"))
  }

  @Test
  fun negotiatePicksTheHighestLevelThePlayerDraws() {
    assertSame(
      RcWidgetTargets.LauncherAndroid16,
      RcPlayerCapabilities.of(RcTargetProfiles.WIDGETS_V6).negotiate(),
    )
    assertSame(
      RcWidgetTargets.Launcher,
      RcPlayerCapabilities.of(RcTargetProfiles.WIDGETS_V7).negotiate(),
    )
    assertSame(
      RcWidgetTargets.WearWidget,
      RcPlayerCapabilities.of(RcTargetProfiles.WEAR_WIDGETS).negotiate(),
    )
    assertSame(
      RcWidgetTargets.App,
      RcPlayerCapabilities.of(RcTargetProfiles.ANDROIDX_EXPERIMENTAL).negotiate(),
    )
    assertNull(RcPlayerCapabilities(5, 0).negotiate())
  }

  @Test
  fun targetsAreFoundById() {
    RcWidgetTargets.All.forEach { assertSame(it, RcWidgetTargets.byId(it.id)) }
  }
}

class RcWidgetDocumentsTest {
  private val template =
    document(
      RcNamedVariable(52, RcNamedVariable.FLOAT_TYPE, "USER:progress"),
      RcFloatConstant(52, RcFloatWord.literal(0.6f)),
      RcNamedVariable(65, RcNamedVariable.STRING_TYPE, "USER:label"),
      RcTextData(65, "On"),
    )

  @Test
  fun bakeWritesEveryValueIn() {
    val baked =
      RcWidgetDocuments.bake(
        template,
        RcNamedValues(strings = mapOf("label" to "Off"), floats = mapOf("progress" to 0.1f))
          .toHardcoded(),
      )
    assertTrue(baked.namedVariables.isEmpty())
    assertEquals(baked, roundTrip(baked))
  }

  @Test
  fun bakeLeavesAnUnboundValueBound() {
    val baked =
      RcWidgetDocuments.bake(template, mapOf("label" to RcHardcodedValue.Text("Off")), { null })
    assertNotNull(baked)
    assertEquals(listOf("USER:progress"), baked.namedVariables.map { it.name })
  }

  @Test
  fun serveKeepsBindingsForAnAppAndBakesThemForAWidget() {
    val values = mapOf("label" to RcHardcodedValue.Text("Off"))
    val app = RcWidgetDocuments.serve(template, RcWidgetTargets.App, values = values)
    assertEquals(2, app.document.namedVariables.size)
    val wear = RcWidgetDocuments.serve(template, RcWidgetTargets.WearWidget, values = values)
    assertEquals(listOf("USER:progress"), wear.document.namedVariables.map { it.name })
  }

  @Test
  fun namedValuesMerge() {
    val merged = RcNamedValues(ints = mapOf("a" to 1)) + RcNamedValues(ints = mapOf("a" to 2))
    assertEquals(mapOf("a" to RcHardcodedValue.Integer(2)), merged.toHardcoded())
  }
}

class RcBindingContractTest {
  private val template =
    document(
      RcNamedVariable(52, RcNamedVariable.FLOAT_TYPE, "USER:progress"),
      RcFloatConstant(52, RcFloatWord.literal(0.6f)),
      RcNamedVariable(65, RcNamedVariable.STRING_TYPE, "USER:label"),
      RcTextData(65, "On"),
    )

  @Test
  fun contractIsReadFromTheDocument() {
    val contract = RcBindingContract.of(template)
    assertEquals(
      listOf(RcBinding("progress", RcBindingType.FLOAT), RcBinding("label", RcBindingType.TEXT)),
      contract.bindings,
    )
    assertEquals(contract, RcBindingContract.decode(contract.encode()))
  }

  @Test
  fun missingAndMistypedValues() {
    val contract = RcBindingContract.of(template)
    val values = mapOf("label" to RcHardcodedValue.FloatValue(1f))
    assertEquals(listOf(RcBinding("progress", RcBindingType.FLOAT)), contract.missing(values))
    assertEquals(emptyList(), contract.missing(values, ignoredPrefixes = setOf("prog")))
    assertEquals(listOf("label"), contract.mistyped(values))
  }

  @Test
  fun aTemplateReadingAnUnknownValueIsRejected() {
    val candidate =
      document(
        RcNamedVariable(52, RcNamedVariable.STRING_TYPE, "USER:progress"),
        RcTextData(52, "x"),
        RcNamedVariable(53, RcNamedVariable.STRING_TYPE, "USER:secret"),
        RcTextData(53, "x"),
        RcNamedVariable(54, RcNamedVariable.FLOAT_TYPE, "USER:wear.container.corner_radius"),
        RcFloatConstant(54, RcFloatWord.literal(1f)),
      )
    val problems =
      RcTemplateCheck.problems(
        candidate,
        template,
        RcTargetProfiles.ANDROIDX_EXPERIMENTAL,
        allowedPrefixes = setOf("wear.container."),
      )
    assertEquals(
      listOf(
        RcTemplateProblem.TypeMismatch("progress", RcBindingType.FLOAT, RcBindingType.TEXT),
        RcTemplateProblem.UnknownBinding(RcBinding("secret", RcBindingType.TEXT)),
      ),
      problems,
    )
    assertEquals(
      emptyList(),
      RcTemplateCheck.problems(template, template, RcTargetProfiles.ANDROIDX_EXPERIMENTAL),
    )
  }
}

class RcWearTemplatesTest {
  private fun named(id: Int, name: String, value: Float): List<RcOperation> =
    listOf(
      RcNamedVariable(id, RcNamedVariable.FLOAT_TYPE, "USER:$name"),
      RcFloatConstant(id, RcFloatWord.literal(value)),
    )

  private val template =
    document(
      *named(40, RcWearContainer.HORIZONTAL_PADDING, 8f).toTypedArray(),
      *named(41, RcWearContainer.VERTICAL_PADDING, 8f).toTypedArray(),
      *named(42, RcWearContainer.CORNER_RADIUS, 26f).toTypedArray(),
      RcNamedVariable(43, RcNamedVariable.STRING_TYPE, "USER:label"),
      RcTextData(43, "On"),
    )

  @Test
  fun aTemplateNamesItsFrame() {
    assertTrue(RcWearContainer.namesFrame(template))
    assertFalse(RcWearContainer.namesFrame(document(RcTextData(1, "x"))))
  }

  @Test
  fun aTemplatesOwnFillStaysWithoutATint() {
    val withFill =
      document(
        *template.operations.toTypedArray(),
        RcColorConstant(44, 0xFF101820.toInt()),
        RcNamedVariable(44, RcNamedVariable.COLOR_TYPE, "USER:${RcWearContainer.COLOR}"),
      )
    val widget =
      RcWearTemplates.instantiate(withFill, RcWearHost.Pixel.large, RcDisplay(2f)).document
    assertTrue(widget.operations.contains(RcColorConstant(44, 0xFF101820.toInt())))
    assertTrue(widget.namedVariables.none { it.name.endsWith(RcWearContainer.COLOR) })
    val tinted =
      RcWearTemplates.instantiate(
        withFill,
        RcWearHost.Pixel.large,
        RcDisplay(2f),
        0xFF00FF00.toInt(),
      )
    assertTrue(tinted.document.operations.contains(RcColorConstant(44, 0xFF00FF00.toInt())))
    // The app's player can still push a tint.
    assertTrue(
      RcWearTemplates.forApp(withFill, RcWearHost.Pixel.large).namedVariables.any {
        it.name.endsWith(RcWearContainer.COLOR)
      }
    )
  }

  @Test
  fun forAppWritesTheFrameAndKeepsOtherBindings() {
    val frame = RcWearHost.Galaxy.large
    val app = RcWearTemplates.forApp(template, frame)
    assertEquals(listOf("USER:label"), app.namedVariables.map { it.name })
    assertTrue(app.operations.contains(RcFloatConstant(40, RcFloatWord.literal(35f))))
    assertTrue(app.operations.contains(RcFloatConstant(41, RcFloatWord.literal(16f))))
    assertTrue(app.operations.contains(RcFloatConstant(42, RcFloatWord.literal(999f))))
  }

  @Test
  fun frameSizes() {
    val frame = RcWearHost.Pixel.large
    assertEquals(216f, frame.outerWidthDp)
    assertEquals(124f, frame.outerHeightDp)
    assertEquals(RcWearContainerType.LARGE, RcWearContainerType.ofGlance(1))
    assertEquals(RcWearContainerType.SMALL, RcWearContainerType.ofGlance(2))
  }

  @Test
  fun aCardDesignsSurfaceBecomesTheContainer() {
    val design =
      document(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(3, -1, 0, 4),
        RcRoundedClipRectModifier(
          RcFloatWord.literal(12f),
          RcFloatWord.literal(12f),
          RcFloatWord.literal(12f),
          RcFloatWord.literal(12f),
        ),
        RcBackgroundModifier(
          0,
          0,
          0,
          0,
          RcFloatWord.literal(1f),
          RcFloatWord.literal(1f),
          RcFloatWord.literal(1f),
          RcFloatWord.literal(1f),
          0,
        ),
        RcPaddingModifier(
          RcFloatWord.literal(16f),
          RcFloatWord.literal(16f),
          RcFloatWord.literal(16f),
          RcFloatWord.literal(16f),
        ),
        RcLayoutContent(4),
        END,
        END,
        END,
        END,
      )
    val reframed = assertNotNull(RcWearTemplates.fromCardDesign(design))
    assertTrue(RcWearContainer.namesFrame(reframed))
    assertEquals(
      RcWearContainer.FRAME_NAMES,
      reframed.namedVariables.map { it.name.removePrefix("USER:") }.toSet(),
    )
    // Centred vertically, as Glance Wear's container centres its content.
    assertEquals(
      2,
      reframed.operations.filterIsInstance<RcBoxLayout>().single().verticalPositioning,
    )
    assertEquals(reframed, roundTrip(reframed))
  }

  @Test
  fun aDesignWithNoCardBoxHasNoTemplate() {
    assertNull(
      RcWearTemplates.fromCardDesign(document(RcRootLayout(1), RcLayoutContent(2), END, END))
    )
  }
}

class RcHostActionRewritesTest {
  @Test
  fun namedTapsSendThePendingIntent() {
    val source =
      document(
        RcTextData(10, "ha"),
        RcTextData(11, "{\"toggle\":\"light.kitchen\"}"),
        RcTextData(12, "other"),
        RcTextData(13, "payload"),
        RcHostNamedAction(10, RcHostNamedActionValue.TextValue(11)),
        RcHostNamedAction(12, RcHostNamedActionValue.TextValue(13)),
      )
    assertEquals(setOf("ha", "other"), RcHostActionRewrites.actionNames(source))
    val rewritten = RcHostActionRewrites.toPendingIntent(source, setOf("ha"), index = 3)
    val name =
      rewritten.operations.filterIsInstance<RcTextData>().single {
        it.text == RcHostActionRewrites.SEND_PENDING_INTENT
      }
    val index = rewritten.operations.filterIsInstance<RcIntegerConstant>().single()
    assertEquals(3, index.value)
    assertEquals(
      listOf(
        RcHostNamedAction(name.id, RcHostNamedActionValue.IntegerValue(index.id)),
        RcHostNamedAction(12, RcHostNamedActionValue.TextValue(13)),
      ),
      rewritten.operations.filterIsInstance<RcHostNamedAction>(),
    )
    // The tap's own name and payload stay: a writer may share them with text the document shows.
    assertTrue(source.operations.filterIsInstance<RcTextData>().all { it in rewritten.operations })
    assertEquals(rewritten, roundTrip(rewritten))
    assertSame(source, RcHostActionRewrites.toPendingIntent(source, setOf("none")))
  }
}

class RcWidgetActionsTest {
  @Test
  fun idsAreStableAndNeverZero() {
    val first = RcWidgetActionIds()
    val second = RcWidgetActionIds()
    val a = first.register("a")
    assertEquals(a, first.register("a"))
    assertEquals(a, second.register("a"))
    assertNotEquals(a, first.register("b"))
    assertNotEquals(0, a)
    assertEquals(listOf("a", "b"), first.entries.values.toList())
  }

  @Test
  fun metadataRoundTrips() {
    val metadata = RcWidgetActionMetadata("{}", "lock.front", "locked", "12:00:01")
    assertEquals(metadata, RcWidgetActionMetadata.decode(RcWidgetActionMetadata.encode(metadata)))
    assertEquals(RcWidgetActionMetadata("raw"), RcWidgetActionMetadata.decode("raw"))
    assertNull(RcWidgetActionMetadata.decode(null))
    assertIs<RcWidgetActionMetadata>(
      RcWidgetActionMetadata.decode("x" + RcWidgetActionMetadata.METADATA_MARKER + "broken")
    )
  }
}

class RcDocumentCacheTest {
  @Test
  fun evictsTheLeastRecentlyUsed() {
    val cache = RcDocumentCache<String>(maxBytes = 10)
    cache.put("a", ByteArray(4))
    cache.put("b", ByteArray(4))
    assertNotNull(cache["a"])
    cache.put("c", ByteArray(4))
    assertNull(cache["b"])
    assertNotNull(cache["a"])
    assertEquals(8, cache.sizeBytes)
    assertEquals(4, cache.getOrPut("d") { ByteArray(4) }.size)
    assertEquals(2, cache.size)
    cache.clear()
    assertEquals(0, cache.sizeBytes)
  }
}

class RcTemplateLibraryTest {
  private class MemoryStore : RcTemplateStore {
    val map = HashMap<String, ByteArray>()

    override fun read(key: String): ByteArray? = map[key]

    override fun write(key: String, bytes: ByteArray) {
      map[key] = bytes
    }
  }

  @Test
  fun capturesOnceOnAMissAndPersists() =
    kotlinx.coroutines.test.runTest {
      val store = MemoryStore()
      var captures = 0
      val library =
        RcTemplateLibrary(listOf(store)) { key ->
          captures++
          key.encodeToByteArray()
        }
      assertEquals("a", library.get("a")?.decodeToString())
      assertEquals("a", library.get("a")?.decodeToString())
      assertEquals(1, captures)
      assertEquals("a", store.map["a"]?.decodeToString())
      // A new process reads the store instead of capturing.
      val restarted = RcTemplateLibrary(listOf(store)) { error("captured") }
      assertEquals("a", restarted.get("a")?.decodeToString())
    }

  @Test
  fun aFailedCaptureIsReportedOnceAndNotRetried() =
    kotlinx.coroutines.test.runTest {
      val failures = mutableListOf<String>()
      var captures = 0
      val library =
        RcTemplateLibrary(onCaptureFailure = { key, _ -> failures += key }) {
          captures++
          error("no")
        }
      assertNull(library.get("a"))
      assertNull(library.get("a"))
      assertEquals(listOf("a"), failures)
      assertEquals(1, captures)
      library.invalidate("a")
      assertNull(library.get("a"))
      assertEquals(2, captures)
    }

  @Test
  fun bundledTemplatesAreCheckedAgainstTheirIndex() {
    val files =
      RcBundledTemplates.layout(
        mapOf("tile:large" to byteArrayOf(1, 2), "tile:small" to byteArrayOf(3))
      )
    val bundle = RcBundledTemplates { files[it] }
    assertEquals(setOf("tile:large", "tile:small"), bundle.keys)
    assertEquals(listOf<Byte>(1, 2), bundle.read("tile:large")?.toList())
    val tampered = files.mapValues { (path, bytes) ->
      if (path.startsWith("blobs/")) byteArrayOf(9) else bytes
    }
    assertNull(RcBundledTemplates { tampered[it] }.read("tile:large"))
    assertNull(RcBundledTemplates { null }.read("tile:large"))
  }

  @Test
  fun sha256MatchesTheStandard() {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      RcSha256.hex(ByteArray(0)),
    )
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      RcSha256.hex("abc".encodeToByteArray()),
    )
  }
}
