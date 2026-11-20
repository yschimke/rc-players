package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBinaryData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
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
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RcInlineEmbeddedDocumentsTest {

  private fun childDoc(label: String = "default"): RcDocument =
    RcDocument(
      header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 120, legacyHeight = 32),
      operations =
        listOf(
          RcTextData(id = 42, text = label),
          RcNamedVariable(id = 42, type = RcNamedVariable.STRING_TYPE, name = "USER:label"),
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
          RcBackgroundModifier(
            0,
            0,
            0,
            0,
            RcFloatWord.literal(0.8f),
            RcFloatWord.literal(0.8f),
            RcFloatWord.literal(0.8f),
            RcFloatWord.literal(1f),
            0,
          ),
          RcLayoutContent(componentId = 4),
          RcTextLayout(
            componentId = 5,
            animationId = -1,
            textId = 42,
            color = 0xFF000000.toInt(),
            fontSize = RcFloatWord.literal(14f),
            fontStyle = 0,
            fontWeight = RcFloatWord.literal(400f),
            fontFamilyId = -1,
            textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
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

  private fun parentDoc(
    childBytes: ByteArray,
    mode: Int = RcMapRcDocVariables.MAP_FORWARD,
  ): RcDocument =
    RcDocument(
      header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 300, legacyHeight = 100),
      operations =
        listOf(
          RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
          RcTextData(id = 43, text = "Parent Badge"),
          RcNamedVariable(id = 43, type = RcNamedVariable.STRING_TYPE, name = "USER:badge"),
          RcTextData(id = 45, text = "USER:label"),
          RcRootLayout(componentId = 1),
          RcLayoutContent(componentId = 2),
          RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(120f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(32f)),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcMapRcDocVariables(docId = 10, mode = mode, localId = 43, remoteTarget = 45),
        ),
    )

  @Test
  fun inlinesStaticInlineChildAndRewiresForwardMapping() {
    val childBytes = RcDocumentCodec.encode(childDoc())
    val parent = parentDoc(childBytes)

    val inlined = parent.inlineEmbeddedDocuments()
    assertFalse(inlined.operations.any { it is RcDocLayout }, "RcDocLayout should be inlined")
    assertFalse(
      inlined.operations.any { it is RcBinaryData },
      "Unused BinaryData should be dropped",
    )
    assertFalse(
      inlined.operations.any { it is RcMapRcDocVariables },
      "Consumed MapRcDocVariables should be dropped",
    )

    // The inlined child's TextLayout should now directly reference parent's localId (43).
    val inlinedText = inlined.operations.filterIsInstance<RcTextLayout>().single()
    assertEquals(43, inlinedText.textId)

    // Must round-trip cleanly through wire codec, linker, and layout tree.
    val decoded = RcDocumentCodec.decode(RcDocumentCodec.encode(inlined))
    assertNotNull(RcLayoutTree.build(RcDocumentLinker.link(decoded)))
  }

  @Test
  fun leavesReverseMappedEmbeddedDocUnchanged() {
    val childBytes = RcDocumentCodec.encode(childDoc())
    val parent = parentDoc(childBytes, mode = RcMapRcDocVariables.MAP_REVERSE)

    val result = parent.inlineEmbeddedDocuments()
    assertTrue(result.operations.any { it is RcDocLayout })
    assertTrue(result.operations.any { it is RcMapRcDocVariables })
  }

  @Test
  fun inlinesUrlResolvedChildWhenResolverProvided() {
    val childBytes = RcDocumentCodec.encode(childDoc("from-url"))
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 300, legacyHeight = 100),
        operations =
          listOf(
            RcTextData(id = 50, text = "rc://badge"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    // Without resolver, URL doc stays as RcDocLayout.
    assertTrue(parent.inlineEmbeddedDocuments().operations.any { it is RcDocLayout })

    // With resolver, URL doc is inlined and gets intrinsic 120x32 size.
    val inlined = parent.inlineEmbeddedDocuments { url ->
      if (url == "rc://badge") childBytes else null
    }
    assertFalse(inlined.operations.any { it is RcDocLayout })
    assertNotNull(RcLayoutTree.build(RcDocumentLinker.link(inlined)))
  }
}
