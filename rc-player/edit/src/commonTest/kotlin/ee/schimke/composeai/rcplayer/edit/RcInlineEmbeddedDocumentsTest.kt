package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBinaryData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcMapRcDocVariables
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
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

  @Test
  fun preservesNestedEmbeddedDocTextReferenceAndBlobAtMaxDepth1() {
    val grandchildBytes = RcDocumentCodec.encode(childDoc("leaf"))
    val midDoc =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 140, legacyHeight = 48),
        operations =
          listOf(
            RcBinaryData(
              id = 50,
              type = RcBinaryData.TYPE_RC_DOC,
              flags = 0,
              data = grandchildBytes,
            ),
            RcTextData(id = 51, text = "rc:embedded/50"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 51),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    val midBytes = RcDocumentCodec.encode(midDoc)
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 80),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = midBytes),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    val inlined = parent.inlineEmbeddedDocuments(maxDepth = 1)
    val nestedDoc = inlined.operations.filterIsInstance<RcDocLayout>().singleOrNull()
    assertNotNull(nestedDoc, "Expected depth-2 RcDocLayout to remain at maxDepth = 1")
    val urlText =
      inlined.operations.filterIsInstance<RcTextData>().firstOrNull { it.id == nestedDoc.urlId }
    assertNotNull(urlText, "Expected hoisted nested url text")
    assertTrue(urlText.text.startsWith(RcDocLayout.EMBEDDED_URL_PREFIX))
    val referencedBlobId =
      urlText.text.substring(RcDocLayout.EMBEDDED_URL_PREFIX.length).toIntOrNull()
    assertNotNull(referencedBlobId)
    val retainedBlob =
      inlined.operations.filterIsInstance<RcBinaryData>().firstOrNull { it.id == referencedBlobId }
    assertNotNull(
      retainedBlob,
      "Expected nested BinaryData blob id $referencedBlobId to be retained and remapped",
    )
    assertTrue(retainedBlob.data.contentEquals(grandchildBytes))
  }

  @Test
  fun leavesChildUninlinedWhenChildWritesForwardMappedVariable() {
    val mutatingChild =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 40),
        operations =
          listOf(
            RcIntegerConstant(id = 42, value = 1),
            RcNamedVariable(id = 42, type = RcNamedVariable.INT_TYPE, name = "USER:count"),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcClickModifier,
            RcValueIntegerChangeAction(targetValueId = 42, value = 99),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcLayoutContent(componentId = 4),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    val childBytes = RcDocumentCodec.encode(mutatingChild)
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 80),
        operations =
          listOf(
            RcIntegerConstant(id = 43, value = 5),
            RcTextData(id = 44, text = "USER:count"),
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcMapRcDocVariables(
              docId = 10,
              mode = RcMapRcDocVariables.MAP_FORWARD,
              localId = 43,
              remoteTarget = 44,
            ),
          ),
      )

    val result = parent.inlineEmbeddedDocuments()
    assertTrue(
      result.operations.any { it is RcDocLayout },
      "Expected child writing a MAP_FORWARD variable to remain un-inlined",
    )
  }

  @Test
  fun preservesChildRootModifiersWhenInlining() {
    val childWithRootPadding =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 40),
        operations =
          listOf(
            RcRootLayout(componentId = 1),
            RcPaddingModifier(
              left = RcFloatWord.literal(12f),
              top = RcFloatWord.literal(8f),
              right = RcFloatWord.literal(12f),
              bottom = RcFloatWord.literal(8f),
            ),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcLayoutContent(componentId = 4),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    val childBytes = RcDocumentCodec.encode(childWithRootPadding)
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 80),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    val inlined = parent.inlineEmbeddedDocuments()
    assertFalse(inlined.operations.any { it is RcDocLayout })
    assertTrue(
      inlined.operations.any {
        it is RcPaddingModifier &&
          it.left == RcFloatWord.literal(12f) &&
          it.top == RcFloatWord.literal(8f)
      },
      "Expected child RootLayout padding modifier to be preserved in inlined document",
    )
    assertNotNull(RcLayoutTree.build(RcDocumentLinker.link(inlined)))
  }

  @Test
  fun allocatedChildIdsStayWithin22BitResourceIdSpace() {
    val childWithFloatRef =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 40),
        operations =
          listOf(
            RcFloatConstant(id = 45, value = RcFloatWord.literal(64f)),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord(0x7fc00000 or 45)),
            RcLayoutContent(componentId = 4),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    val childBytes = RcDocumentCodec.encode(childWithFloatRef)
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 80),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    val inlined = parent.inlineEmbeddedDocuments()
    val floatDecl = inlined.operations.filterIsInstance<RcFloatConstant>().single()
    assertTrue(
      floatDecl.id in 42 until 0x003fffff,
      "Expected allocated float id to stay within 22-bit resource ID space, got ${floatDecl.id}",
    )
    val widthRef =
      inlined.operations.filterIsInstance<RcWidthModifier>().firstNotNullOf {
        it.value.referencedId
      }
    assertEquals(floatDecl.id, widthRef)
  }

  @Test
  fun preservesComponentValueBindingsToChildRootAndContentIds() {
    val childWithBindings =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 40),
        operations =
          listOf(
            RcComponentValue(type = 0, componentId = 1, valueId = 60),
            RcComponentValue(type = 1, componentId = 2, valueId = 61),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcLayoutContent(componentId = 4),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    val childBytes = RcDocumentCodec.encode(childWithBindings)
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 80),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    val inlined = parent.inlineEmbeddedDocuments()
    val bindings = inlined.operations.filterIsInstance<RcComponentValue>()
    assertEquals(2, bindings.size)
    val componentIds = inlined.operations.mapNotNull { it.declaredComponentId() }.toSet()
    for (binding in bindings) {
      assertTrue(
        binding.componentId in componentIds,
        "Expected RcComponentValue binding componentId ${binding.componentId} to exist in $componentIds",
      )
    }
    assertEquals(10, bindings[0].componentId)
  }

  @Test
  fun stripsUnmappedChildNamedVariablesToProtectParentNamespace() {
    val childBytes = RcDocumentCodec.encode(childDoc("child-default"))
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 80),
        operations =
          listOf(
            RcTextData(id = 42, text = "parent-title"),
            RcNamedVariable(id = 42, type = RcNamedVariable.STRING_TYPE, name = "USER:label"),
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    val inlined = parent.inlineEmbeddedDocuments()
    val namedVars =
      inlined.operations.filterIsInstance<RcNamedVariable>().filter { it.name == "USER:label" }
    assertEquals(1, namedVars.size, "Expected unmapped child RcNamedVariable to be stripped")
    assertEquals(42, namedVars.single().id, "Expected parent's RcNamedVariable id to be preserved")
    assertTrue(
      inlined.operations.filterIsInstance<RcTextData>().any { it.text == "child-default" },
      "Expected child's default text constant to remain",
    )
  }

  @Test
  fun preservesExpressionOperatorsAndSystemVariablesWhenInlining() {
    val continuousSecWord = RcFloatWord(0x7fc00000 or 1) // RcSystemVariables.CONTINUOUS_SEC = 1
    val addOperatorWord = RcFloatWord(0x7ff10001) // RcFloatExpressionEvaluator.OFFSET + 1 (ADD)
    val childWithExpr =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 100, legacyHeight = 40),
        operations =
          listOf(
            RcFloatConstant(id = 45, value = RcFloatWord.literal(10f)),
            RcFloatExpression(
              id = 46,
              expression =
                listOf(continuousSecWord, RcFloatWord(0x7fc00000 or 45), addOperatorWord),
              animation = null,
            ),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcBoxLayout(
              componentId = 3,
              animationId = -1,
              horizontalPositioning = 0,
              verticalPositioning = 0,
            ),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord(0x7fc00000 or 46)),
            RcLayoutContent(componentId = 4),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    val childBytes = RcDocumentCodec.encode(childWithExpr)
    val parent =
      RcDocument(
        header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 200, legacyHeight = 80),
        operations =
          listOf(
            RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = childBytes),
            RcRootLayout(componentId = 1),
            RcLayoutContent(componentId = 2),
            RcDocLayout(componentId = 10, animationId = -1, urlId = 50),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )

    val inlined = parent.inlineEmbeddedDocuments()
    val expr = inlined.operations.filterIsInstance<RcFloatExpression>().single()
    val floatDecl = inlined.operations.filterIsInstance<RcFloatConstant>().single()
    // System variable CONTINUOUS_SEC (id 1) must NOT be remapped to docOp.componentId (10).
    assertEquals(continuousSecWord, expr.expression[0])
    // Child float resource 45 must be remapped to floatDecl.id.
    assertEquals(floatDecl.id, expr.expression[1].referencedId)
    // Arithmetic operator ADD (0x7ff10001) must remain untouched.
    assertEquals(addOperatorWord, expr.expression[2])
  }
}
