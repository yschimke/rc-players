package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBinaryData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcHostMetadataAction
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcMapRcDocVariables
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

class RcEmbeddedDocumentsTest {

  private fun bg(argb: Int): RcBackgroundModifier {
    val a = ((argb ushr 24) and 0xff) / 255f
    val r = ((argb ushr 16) and 0xff) / 255f
    val g = ((argb ushr 8) and 0xff) / 255f
    val b = (argb and 0xff) / 255f
    return RcBackgroundModifier(
      flags = 0,
      colorId = 0,
      reserved1 = 0,
      reserved2 = 0,
      red = RcFloatWord.literal(r),
      green = RcFloatWord.literal(g),
      blue = RcFloatWord.literal(b),
      alpha = RcFloatWord.literal(a),
      shapeType = 0,
    )
  }

  private fun solidColorDoc(width: Int, height: Int, argb: Int): ByteArray {
    val doc =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = width, legacyHeight = height),
        operations =
          listOf(
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
            RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
            bg(argb),
            RcLayoutContent(componentId = 4),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    return RcDocumentCodec.encode(doc)
  }

  private fun parentWithUrlDoc(
    url: String,
    bgArgb: Int = 0xFFFFFFFF.toInt(),
    docWidth: Int = 60,
    docHeight: Int = 60,
  ): RcDocument =
    RcDocument(
      header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 100),
      operations =
        listOf(
          RcTextData(id = 42, text = url),
          RcRootLayout(componentId = 1),
          RcLayoutContent(componentId = 2),
          RcBoxLayout(
            componentId = 3,
            animationId = -1,
            horizontalPositioning = 2,
            verticalPositioning = 2,
          ),
          RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
          RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
          bg(bgArgb),
          RcLayoutContent(componentId = 4),
          RcDocLayout(componentId = 10, animationId = -1, urlId = 42),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(docWidth.toFloat())),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(docHeight.toFloat())),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
    )

  private fun pixelAt(image: Image, x: Int, y: Int): Int {
    val bitmap = Bitmap()
    bitmap.allocPixels(
      ImageInfo(image.width, image.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL),
    )
    image.readPixels(bitmap, 0, 0)
    return bitmap.getColor(x, y)
  }

  private fun renderDoc(
    document: RcDocument,
    embedded: RcEmbeddedDocuments? = null,
    width: Int = 100,
    height: Int = 100,
    namedValues: androidx.compose.runtime.snapshots.SnapshotStateMap<String, RcNamedValue> =
      mutableStateMapOf(),
    onEvent: (RcPlayerEvent) -> Unit = {},
  ): Image {
    val scene =
      ImageComposeScene(width = width, height = height, density = Density(1f)) {
        val body =
          @androidx.compose.runtime.Composable {
            RcComposePlayer(
              document = document,
              modifier = Modifier.size(width.dp, height.dp),
              namedValues = namedValues,
              onEvent = onEvent,
            )
          }
        if (embedded != null) {
          CompositionLocalProvider(LocalRcEmbeddedDocuments provides embedded) { body() }
        } else {
          body()
        }
      }
    return try {
      scene.render(0L)
    } finally {
      scene.close()
    }
  }

  @Test
  fun disabledByDefaultDoesNotRenderOrResolve() {
    val parent = parentWithUrlDoc("doc://red")
    val image = renderDoc(parent, embedded = null)
    // Center stays parent's white background when embedded documents are not opted into.
    assertEquals(0xFFFFFFFF.toInt(), pixelAt(image, 50, 50))
  }

  @Test
  fun inlineBinaryDataDirectIdRendersChild() {
    val redBytes = solidColorDoc(60, 60, 0xFFFF0000.toInt())
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 100),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = redBytes),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 2,
              verticalPositioning = 2,
            ),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
            bg(0xFFFFFFFF.toInt()),
            RcLayoutContent(componentId = 4),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    val image = renderDoc(parent, embedded = RcEmbeddedDocuments())
    assertEquals(0xFFFFFFFF.toInt(), pixelAt(image, 5, 5))
    assertEquals(0xFFFF0000.toInt(), pixelAt(image, 50, 50))
  }

  @Test
  fun inlineBinaryDataPrefixUrlRendersChild() {
    val greenBytes = solidColorDoc(60, 60, 0xFF00FF00.toInt())
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 100),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = greenBytes),
            RcTextData(id = 51, text = "rc:embedded/50"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 2,
              verticalPositioning = 2,
            ),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
            bg(0xFFFFFFFF.toInt()),
            RcLayoutContent(componentId = 4),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 51),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(60f)),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    val image = renderDoc(parent, embedded = RcEmbeddedDocuments())
    assertEquals(0xFF00FF00.toInt(), pixelAt(image, 50, 50))
  }

  @Test
  fun urlResolverDefaultsToOneLevelOfNestingAndRespectsMaxDepth() {
    val level2Bytes = solidColorDoc(30, 30, 0xFF0000FF.toInt())
    val level1Bytes =
      RcDocumentCodec.encode(
        parentWithUrlDoc(
          url = "doc://level2",
          bgArgb = 0xFF00FF00.toInt(),
          docWidth = 30,
          docHeight = 30,
        ),
      )
    val level0 =
      parentWithUrlDoc(
        url = "doc://level1",
        bgArgb = 0xFFFFFFFF.toInt(),
        docWidth = 60,
        docHeight = 60,
      )

    val requestedDefault = mutableListOf<String>()
    val defaultResolver = RcDocumentResolver { url ->
      requestedDefault += url
      when (url) {
        "doc://level1" -> level1Bytes
        "doc://level2" -> level2Bytes
        else -> null
      }
    }

    // Default maxDepth = 1: level1 renders (green), level2 is blocked (center stays green, not
    // blue).
    val depth1Image = renderDoc(level0, embedded = RcEmbeddedDocuments(resolver = defaultResolver))
    assertEquals(listOf("doc://level1"), requestedDefault)
    assertEquals(0xFF00FF00.toInt(), pixelAt(depth1Image, 50, 50))

    // Explicit maxDepth = 2: both level1 (green ring) and level2 (blue center) render.
    val requestedDepth2 = mutableListOf<String>()
    val depth2Resolver = RcDocumentResolver { url ->
      requestedDepth2 += url
      when (url) {
        "doc://level1" -> level1Bytes
        "doc://level2" -> level2Bytes
        else -> null
      }
    }
    val depth2Image =
      renderDoc(level0, embedded = RcEmbeddedDocuments(maxDepth = 2, resolver = depth2Resolver))
    assertEquals(listOf("doc://level1", "doc://level2"), requestedDepth2)
    assertEquals(0xFF0000FF.toInt(), pixelAt(depth2Image, 50, 50))
    assertEquals(0xFF00FF00.toInt(), pixelAt(depth2Image, 25, 25))
  }

  @Test
  fun forwardVariableMappingUpdatesChildRendering() {
    val childDoc =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 80, legacyHeight = 40),
        operations =
          listOf(
            RcTextData(id = 42, text = "default"),
            RcNamedVariable(id = 42, type = RcNamedVariable.STRING_TYPE, name = "USER:label"),
            RcColorConstant(id = 43, argb = 0xFF000000.toInt()),
            RcNamedVariable(id = 43, type = RcNamedVariable.COLOR_TYPE, name = "USER:ink"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
            RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
            bg(0xFFFFFFFF.toInt()),
            RcLayoutContent(componentId = 4),
            RcTextLayout(
              componentId = 5,
              animationId = -1,
              textId = 42,
              color = 43,
              fontSize = RcFloatWord.literal(24f),
              fontStyle = 0,
              fontWeight = RcFloatWord.literal(700f),
              fontFamilyId = -1,
              textAlignAndFlags =
                (RcTextLayout.FLAG_DYNAMIC_COLOR shl 16) or RcTextLayout.ALIGN_LEFT,
              overflow = 1,
              maxLines = 1,
            ),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    val childBytes = RcDocumentCodec.encode(childDoc)

    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 60),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcTextData(id = 60, text = "MMMM"),
            RcNamedVariable(id = 60, type = RcNamedVariable.STRING_TYPE, name = "USER:title"),
            RcTextData(id = 61, text = "USER:label"),
            RcColorConstant(id = 62, argb = 0xFFFF0000.toInt()),
            RcNamedVariable(id = 62, type = RcNamedVariable.COLOR_TYPE, name = "USER:cardInk"),
            RcTextData(id = 63, text = "USER:ink"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcMapRcDocVariables(
              10,
              RcMapRcDocVariables.MAP_FORWARD,
              localId = 60,
              remoteTarget = 61,
            ),
            RcMapRcDocVariables(
              10,
              RcMapRcDocVariables.MAP_FORWARD,
              localId = 62,
              remoteTarget = 63,
            ),
          ),
      )

    val values =
      mutableStateMapOf<String, RcNamedValue>(
        "USER:cardInk" to RcNamedValue.Color(0xFFCC0000.toInt()),
      )
    val image =
      renderDoc(
        parent,
        embedded = RcEmbeddedDocuments(),
        width = 100,
        height = 60,
        namedValues = values,
      )

    // Verify at least one red-dominant glyph pixel was drawn from the forwarded text & color.
    var hasRedGlyph = false
    for (y in 0 until 40) {
      for (x in 0 until 80) {
        val c = pixelAt(image, x, y)
        val r = (c ushr 16) and 0xff
        val g = (c ushr 8) and 0xff
        val b = c and 0xff
        if (r > 180 && g < 60 && b < 60) {
          hasRedGlyph = true
          break
        }
      }
      if (hasRedGlyph) break
    }
    assertTrue(hasRedGlyph, "Expected forwarded text and color to render in embedded child")
  }

  @Test
  fun hostActionMappingInterceptsMappedAndForwardsUnmapped() {
    val childDoc =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 100),
        operations =
          listOf(
            RcTextData(id = 42, text = "picked"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(50f)),
            RcClickModifier,
            RcHostMetadataAction(actionId = 7, metadataTextId = 42),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcLayoutContent(componentId = 4),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcBoxLayout(
              componentId = 5,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(50f)),
            RcClickModifier,
            RcHostAction(actionId = 99),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcLayoutContent(componentId = 6),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    val childBytes = RcDocumentCodec.encode(childDoc)

    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 100),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcTextData(id = 70, text = "initial"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(100f)),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcMapRcDocVariables(
              10,
              RcMapRcDocVariables.MAP_HOST_ACTION,
              localId = 70,
              remoteTarget = 7,
            ),
          ),
      )

    val events = mutableListOf<RcPlayerEvent>()
    val scene =
      ImageComposeScene(width = 100, height = 100, density = Density(1f)) {
        CompositionLocalProvider(LocalRcEmbeddedDocuments provides RcEmbeddedDocuments()) {
          RcComposePlayer(
            document = parent,
            modifier = Modifier.size(100.dp, 100.dp),
            onEvent = events::add,
          )
        }
      }
    try {
      scene.render(0L)
      // Tap first box (y=25): actionId 7 is intercepted by MAP_HOST_ACTION and NOT forwarded.
      scene.sendPointerEvent(PointerEventType.Press, Offset(50f, 25f))
      scene.sendPointerEvent(PointerEventType.Release, Offset(50f, 25f))
      scene.render(16_000_000L)
      assertFalse(events.any { it is RcPlayerEvent.HostActionMetadata && it.actionId == 7 })

      // Tap second box (y=75): actionId 99 is unmapped and forwarded to parent onEvent.
      scene.sendPointerEvent(PointerEventType.Press, Offset(50f, 75f))
      scene.sendPointerEvent(PointerEventType.Release, Offset(50f, 75f))
      scene.render(32_000_000L)
      assertTrue(events.any { it is RcPlayerEvent.HostAction && it.actionId == 99 })
    } finally {
      scene.close()
    }
  }

  @Test
  fun cacheReusesPreparedChildAcrossMultipleCards() {
    val childBytes = solidColorDoc(60, 40, 0xFF3366CC.toInt())
    val parentPrepared =
      RcPreparedDocument(parentWithUrlDoc("doc://badge", docWidth = 60, docHeight = 40))
    val cache = RcPreparedDocumentCache()
    val embedded =
      RcEmbeddedDocuments(
        resolver = { if (it == "doc://badge") childBytes else null },
        cache = cache,
      )

    val scene =
      ImageComposeScene(width = 100, height = 400, density = Density(1f)) {
        CompositionLocalProvider(LocalRcEmbeddedDocuments provides embedded) {
          Column {
            repeat(4) {
              RcComposePlayer(prepared = parentPrepared, modifier = Modifier.size(100.dp, 100.dp))
            }
          }
        }
      }
    try {
      scene.render(0L)
      assertEquals(
        1,
        cache.prepared,
        "Expected shared cache to prepare child document once across 4 cards",
      )
    } finally {
      scene.close()
    }
  }

  @Test
  fun reverseVariableMappingSyncsFromCanvasOnlyChild() {
    // Canvas-only child (no RcRootLayout) exposes a named color "USER:childInk" = blue.
    val canvasChild =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 20, legacyHeight = 20),
        operations =
          listOf(
            RcColorConstant(id = 42, argb = 0xFF0000FF.toInt()),
            RcNamedVariable(id = 42, type = RcNamedVariable.COLOR_TYPE, name = "USER:childInk"),
          ),
      )
    val childBytes = RcDocumentCodec.encode(canvasChild)

    // Parent starts with red ink (id 62) and reverse-maps child's "USER:childInk" into id 62.
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 60),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcTextData(id = 60, text = "MMMM"),
            RcTextData(id = 61, text = "USER:childInk"),
            RcColorConstant(id = 62, argb = 0xFFFF0000.toInt()),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
            RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
            bg(0xFFFFFFFF.toInt()),
            RcLayoutContent(componentId = 4),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcTextLayout(
              componentId = 11,
              animationId = -1,
              textId = 60,
              color = 62,
              fontSize = RcFloatWord.literal(24f),
              fontStyle = 0,
              fontWeight = RcFloatWord.literal(700f),
              fontFamilyId = -1,
              textAlignAndFlags =
                (RcTextLayout.FLAG_DYNAMIC_COLOR shl 16) or RcTextLayout.ALIGN_LEFT,
              overflow = 1,
              maxLines = 1,
            ),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcMapRcDocVariables(
              10,
              RcMapRcDocVariables.MAP_REVERSE,
              localId = 62,
              remoteTarget = 61,
            ),
          ),
      )

    val scene =
      ImageComposeScene(width = 100, height = 60, density = Density(1f)) {
        CompositionLocalProvider(LocalRcEmbeddedDocuments provides RcEmbeddedDocuments()) {
          RcComposePlayer(document = parent, modifier = Modifier.size(100.dp, 60.dp))
        }
      }
    try {
      scene.render(0L)
      val image = scene.render(16_000_000L)
      var hasBlueGlyph = false
      for (y in 0 until 60) {
        for (x in 0 until 100) {
          val c = pixelAt(image, x, y)
          val r = (c ushr 16) and 0xff
          val g = (c ushr 8) and 0xff
          val b = c and 0xff
          if (b > 180 && r < 60 && g < 60) {
            hasBlueGlyph = true
            break
          }
        }
        if (hasBlueGlyph) break
      }
      assertTrue(hasBlueGlyph, "Expected MAP_REVERSE from canvas-only child to update parent color")
    } finally {
      scene.close()
    }
  }
}
