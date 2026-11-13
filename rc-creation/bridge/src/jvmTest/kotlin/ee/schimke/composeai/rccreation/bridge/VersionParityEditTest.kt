package ee.schimke.composeai.rccreation.bridge

import androidx.collection.MutableIntSet
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.common.CreationDisplayInfo
import ee.schimke.composeai.rccreation.common.Profile
import ee.schimke.composeai.rccreation.common.RcPlatformProfiles
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.compose.capture.Profile
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.RemoteDensityBehavior
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.border
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rcplayer.edit.RcApproximation
import ee.schimke.composeai.rcplayer.edit.RcDisplay
import ee.schimke.composeai.rcplayer.edit.RcProfileViolation
import ee.schimke.composeai.rcplayer.edit.RcProfiles
import ee.schimke.composeai.rcplayer.edit.RcTargetProfile
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.adaptTo
import ee.schimke.composeai.rcplayer.edit.validateFor
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The version-dependent writes of the creation writer (`docs/design/VERSION_QUIRKS.md`), held to
 * `rc-player-edit`'s `adaptTo`: a document written for AndroidX's player at API level 8, rewritten
 * for a level 7 or level 6 target, is the document the writer writes for that target directly,
 * operation for operation and header for header, but for the ids and what [KnownDivergence] says.
 *
 * The same pairs played on the platform's own player are `:rc-widget-launcher`'s
 * `PlatformPlayerAdaptedDocumentTest`.
 */
class VersionParityEditTest {
  @Composable
  private fun Content() {
    RemoteColumn(RemoteModifier.fillMaxSize()) {
      RemoteBox(
        RemoteModifier.size(40.rdp)
          .border(2.rdp, RemoteColor(Color.Red))
          .padding(4.rdp)
          .background(RemoteColor(Color.Blue)),
      )
      RemoteText("Hi".rs, fontSize = 14.rsp)
      RemoteCanvas(RemoteModifier.fillMaxWidth().height(10.rdp)) {
        drawRect(
          RemotePaint { color = RemoteColor(Color.Green) },
          RemoteOffset(0f.rf, 0f.rf),
          RemoteSize(width * 0.5f.rf, height),
        )
        drawCircle(
          RemotePaint { color = RemoteColor(Color.Green) },
          4f.rf,
          RemoteOffset(10f.rf, 10f.rf),
        )
      }
    }
  }

  private fun write(
    profile: Profile,
    behavior: RemoteDensityBehavior = RemoteDensityBehavior.Legacy,
  ): RcDocument = RcDocumentCodec.decode(writeBytes(profile, behavior))

  private fun writeBytes(
    profile: Profile,
    behavior: RemoteDensityBehavior = RemoteDensityBehavior.Legacy,
  ): ByteArray = runBlocking {
    captureCommonRemoteDocument(
      RemoteCreationDisplayInfo(360, 200, 320, densityBehavior = behavior),
      profile = profile,
    ) {
      Content()
    }
  }

  private val display = RcDisplay(density = 2f)
  private val androidx7 = RcTargetProfile.of(7, RcProfiles.ANDROIDX)

  /**
   * The profiles that read documents up to level 7, with the target `adaptTo` is given for each.
   */
  private val levelSevenTargets =
    listOf(
      Triple("ANDROIDX7", RcPlatformProfiles.ANDROIDX7, androidx7),
      Triple("WIDGETS_V7", RcPlatformProfiles.WIDGETS_V7, RcTargetProfiles.WIDGETS_V7),
    )

  private fun adapt(document: RcDocument, target: RcTargetProfile): RcDocument {
    val adaptation = document.adaptTo(target, RcApproximation.values().toSet(), display)
    assertEquals(emptyList(), adaptation.violations, target.name)
    return adaptation.document
  }

  private fun shape(document: RcDocument): List<String> =
    document.operations.map { it::class.simpleName.orEmpty() }

  /** Where the writer's document for a level 6 target differs, and `adaptTo`'s does not. */
  private object KnownDivergence {
    /**
     * `PaintTracker` writes a `wght` font axis, with its name as a `TextData` inside the canvas,
     * wherever the profile has no `CoreText`; where it has, an empty axis. `adaptTo` leaves the
     * empty axis. The platform's player draws both alike (`PlatformPlayerAdaptedDocumentTest`).
     */
    fun withoutAxisName(shape: List<String>, document: RcDocument): List<String> =
      document.operations
        .withIndex()
        .filterNot { (_, operation) -> operation is RcTextData && operation.text == "wght" }
        .map { (index, _) -> shape[index] }
  }

  // --- CanvasContent, and the ComponentValue that names it (level 7 and below) -----------------

  @Test
  fun aCanvasDrawsInACanvasContentForTheTargetsUpToLevelSeven() {
    val written = write(RcPlatformProfiles.ANDROIDX8)
    assertTrue(written.operations.none { it is RcCanvasContent }, "level 8 writes none")
    for ((name, profile, target) in levelSevenTargets) {
      val adapted = adapt(written, target)
      val direct = write(profile)
      assertEquals(shape(direct), shape(adapted), name)
      val content = adapted.operations.filterIsInstance<RcCanvasContent>().single()
      val values =
        adapted.operations.filterIsInstance<RcComponentValue>().filter {
          it.componentId == content.componentId
        }
      assertEquals(2, values.size, "$name: the canvas's width and height name its content")
      assertEquals(
        direct.operations.filterIsInstance<RcComponentValue>().count { it.componentId < -1 },
        adapted.operations.filterIsInstance<RcComponentValue>().count { it.componentId < -1 },
        name,
      )
    }
  }

  @Test
  fun aCanvasValueNamingTheCanvasIsRenamedNotDuplicated() {
    val written = write(RcPlatformProfiles.ANDROIDX8)
    val canvas =
      written.operations
        .filterIsInstance<ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout>()
        .single()
    val adapted = adapt(written, RcTargetProfiles.WIDGETS_V7)
    assertTrue(
      adapted.operations.filterIsInstance<RcComponentValue>().none {
        it.componentId == canvas.componentId
      },
      "no value still names the canvas",
    )
  }

  @Test
  fun aCanvasContentIsKeptForALevelEightTarget() {
    // Level 7 to level 8: nothing is lost. A level 8 player reads a CanvasContent all the same.
    for ((name, profile, _) in levelSevenTargets) {
      val written = write(profile)
      val adaptation = written.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display)
      assertEquals(emptyList(), adaptation.violations, name)
      assertEquals(written.operations, adaptation.document.operations, name)
    }
  }

  @Test
  fun aLevelSixDocumentGetsTheSameCanvasContent() {
    val adapted = adapt(write(RcPlatformProfiles.ANDROIDX8), RcTargetProfiles.WIDGETS_V6)
    val direct = write(RcPlatformProfiles.WIDGETS_V6)
    assertEquals(KnownDivergence.withoutAxisName(shape(direct), direct), shape(adapted))
  }

  // --- The legacy border (level 7 and below) ---------------------------------------------------

  @Test
  fun aBorderIsInTheLegacyFormForTheTargetsUpToLevelSeven() {
    val written = write(RcPlatformProfiles.ANDROIDX8)
    assertEquals(1, written.operations.filterIsInstance<RcBorderModifier>().single().wireVersion)
    for ((name, profile, target) in
      levelSevenTargets +
        Triple("WIDGETS_V6", RcPlatformProfiles.WIDGETS_V6, RcTargetProfiles.WIDGETS_V6)) {
      val adapted = adapt(written, target).operations.filterIsInstance<RcBorderModifier>().single()
      val direct = write(profile).operations.filterIsInstance<RcBorderModifier>().single()
      assertEquals(direct.wireVersion, adapted.wireVersion, name)
      assertEquals(0, adapted.wireVersion, name)
    }
  }

  // --- CoreText becomes TextLayout where the profile has no CoreText ---------------------------

  @Test
  fun textIsATextLayoutWhereTheTargetHasNoCoreText() {
    val written = write(RcPlatformProfiles.ANDROIDX8)
    val direct =
      write(RcPlatformProfiles.WIDGETS_V6).operations.filterIsInstance<RcTextLayout>().single()
    assertTrue(written.operations.single { it is RcCoreText } is RcCoreText)
    val adapted =
      adapt(written, RcTargetProfiles.WIDGETS_V6)
        .operations
        .filterIsInstance<RcTextLayout>()
        .single()
    assertEquals(direct.copy(componentId = 0), adapted.copy(componentId = 0))
  }

  @Test
  fun textIsATextLayoutForATargetWithoutCoreTextAtAnyLevel() {
    // A profile of level 8 whose table lacks CoreText: what a host that cannot take it captures
    // with.
    val operations = MutableIntSet()
    (RcTargetProfiles.ANDROIDX.supportedOpcodes - RcOpcodes.CORE_TEXT).forEach {
      operations.add(it)
    }
    val withoutCoreText = Profile(8, RcProfiles.ANDROIDX, operations)
    val target = RcTargetProfiles.ANDROIDX.without(RcOpcodes.CORE_TEXT)
    val direct = write(withoutCoreText)
    val adapted = adapt(write(RcPlatformProfiles.ANDROIDX8), target)
    assertEquals(
      direct.operations.filterIsInstance<RcTextLayout>().single().copy(componentId = 0),
      adapted.operations.filterIsInstance<RcTextLayout>().single().copy(componentId = 0),
    )
    // The rest of the document: its level 8 forms, a canvas with no CanvasContent.
    assertEquals(
      KnownDivergence.withoutAxisName(shape(direct), direct),
      shape(adapted),
    )
  }

  // --- An operation outside the profile's table -----------------------------------------------

  @Test
  fun anOperationTheProfileLacksIsRefusedByTheWriterAndReportedByValidateFor() {
    fun themed(profile: ee.schimke.composeai.rccreation.common.Profile) =
      RemoteDocumentWriter(CreationDisplayInfo(100, 100, 160), null, profile).also {
        it.addThemedColor("group", 0, 0, 0xff0000ff.toInt(), 0xff00ff00.toInt())
      }
    val written = RcDocumentCodec.decode(themed(RcPlatformProfiles.ANDROIDX8).encodeToByteArray())
    val error = assertFailsWith<RuntimeException> { themed(RcPlatformProfiles.WIDGETS_V6) }
    assertTrue("not supported" in error.message.orEmpty(), error.message)
    val violation =
      written.validateFor(RcTargetProfiles.WIDGETS_V6).single {
        it.kind == RcProfileViolation.Kind.UNSUPPORTED_OPERATION
      }
    assertEquals(RcOpcodes.COLOR_THEME, violation.opcode)
    // `adaptTo` converts it only where asked, to the fallbacks the writer recorded.
    assertTrue(
      written.adaptTo(RcTargetProfiles.WIDGETS_V6, emptySet(), display).violations.any {
        it.opcode == RcOpcodes.COLOR_THEME
      },
    )
    assertEquals(
      emptyList(),
      written
        .adaptTo(RcTargetProfiles.WIDGETS_V6, setOf(RcApproximation.THEME_COLORS), display)
        .violations
        .filter { it.opcode == RcOpcodes.COLOR_THEME },
    )
  }

  // --- The header ------------------------------------------------------------------------------

  @Test
  fun theHeaderIsTheLegacyOneAtLevelSixAndAPropertyHeaderAboveIt() {
    val written = write(RcPlatformProfiles.ANDROIDX8)
    val level6 = adapt(written, RcTargetProfiles.WIDGETS_V6).header
    val direct6 = write(RcPlatformProfiles.WIDGETS_V6).header
    assertEquals(direct6, level6)
    for ((name, profile, target) in levelSevenTargets) {
      val adapted = adapt(written, target).header
      val direct = write(profile).header
      assertTrue(adapted.modern && direct.modern, name)
      assertEquals(direct.version, adapted.version, name)
      assertEquals(direct.width, adapted.width, name)
      assertEquals(direct.height, adapted.height, name)
      assertEquals(direct.densityBehavior, adapted.densityBehavior, name)
    }
  }

  @Test
  fun theHeaderDeclaresTheProfileTheTargetReads() {
    val written = write(RcPlatformProfiles.ANDROIDX8)
    for ((name, profile, target) in levelSevenTargets) {
      assertEquals(
        write(profile).header.profiles(),
        adapt(written, target).header.profiles(),
        name,
      )
    }
  }

  private fun RcHeader.profiles(): Int =
    (properties.single { it.key == RcHeader.DOC_PROFILES }.value as RcHeaderValue.IntValue).value

  // --- Density behaviour (level 8 and above only) ----------------------------------------------

  @Test
  fun aDpDocumentIsRefusedForATargetBelowLevelEightAsTheWriterRefusesIt() {
    assertFailsWith<IllegalArgumentException> {
      write(RcPlatformProfiles.ANDROIDX7, RemoteDensityBehavior.Dp)
    }
    val written = write(RcPlatformProfiles.ANDROIDX8, RemoteDensityBehavior.Dp)
    assertEquals(RcHeader.DENSITY_BEHAVIOR_DP, written.header.densityBehavior)
    for (target in listOf(androidx7, RcTargetProfiles.WIDGETS_V7, RcTargetProfiles.WIDGETS_V6)) {
      val adaptation = written.adaptTo(target, emptySet(), display)
      val violation =
        adaptation.violations.singleOrNull {
          it.kind == RcProfileViolation.Kind.UNSUPPORTED_FEATURE && it.operationIndex == null
        }
      assertTrue(
        violation != null && "density" in violation.message,
        "${target.name}: ${adaptation.violations}",
      )
      assertEquals(false, adaptation.isPlayable, target.name)
    }
    // At level 8 and above the behaviour is read.
    assertEquals(emptyList(), written.validateFor(RcTargetProfiles.ANDROIDX))
  }

  @Test
  fun aDpDocumentWritesItsSizesInDpAndALegacyOneInPixels() {
    fun padding(behavior: RemoteDensityBehavior) =
      write(RcPlatformProfiles.ANDROIDX8, behavior)
        .operations
        .filterIsInstance<RcPaddingModifier>()
        .single()
        .left
        .value
    assertEquals(4f, padding(RemoteDensityBehavior.Dp))
    assertEquals(8f, padding(RemoteDensityBehavior.Legacy))
  }

  // --- The reverse: a document for a lower level, for a higher one -----------------------------

  @Test
  fun aLowerLevelDocumentIsPlayableOnAHigherLevelPlayerAsItIs() {
    // Level 6 or 7 to level 8: the forms of the lower levels (the legacy header, the CanvasContent,
    // the legacy border) are read by every player above, so nothing is rewritten or lost.
    for (profile in listOf(RcPlatformProfiles.WIDGETS_V6, RcPlatformProfiles.ANDROIDX7)) {
      val written = write(profile)
      val adaptation = written.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display)
      assertEquals(emptyList(), adaptation.violations, profile.toString())
      assertEquals(written.operations, adaptation.document.operations, profile.toString())
    }
  }
}
