package ee.schimke.composeai.rccreation.bridge

import ee.schimke.composeai.rccreation.common.AnimatedFloatExpression
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.common.RemoteModifierData
import ee.schimke.composeai.rccreation.common.Utils
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComponent
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.layout.remoteComponent
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcFloatList
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextFromFloat
import ee.schimke.composeai.rcplayer.protocol.RcTextSubtext
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Where `RemoteDocumentWriter` writes a declaration: where it is made, as AndroidX's
 * `RemoteComposeWriter` does, with the two exceptions its own notes give (`beginGlobal`, and the
 * kinds a player does not keep among a layout component's operations).
 */
class DeclarationPlacementTest {
  private fun written(block: RemoteDocumentWriter.() -> Unit): List<RcOperation> =
    RcDocumentCodec.decode(RemoteDocumentWriter(200, 100).apply(block).encodeToByteArray())
      .operations

  private inline fun <reified T : RcOperation> List<RcOperation>.at(): Int = indexOfFirst {
    it is T
  }

  /** An expression and text made from it, asked for inside a box, are in the box. */
  @Test
  fun anExpressionAndItsTextAreWrittenInTheComponentThatAsksForThem() {
    val operations = written {
      root {
        startBox(RemoteModifierData(), 1, 4)
        val sum = floatExpression(1f, 2f, AnimatedFloatExpression.ADD)
        createTextFromFloat(sum, 3, 0, 0)
        addText("label")
        endBox()
      }
    }
    val content = operations.indexOfLast { it is RcLayoutContent }
    assertTrue(operations.at<RcBoxLayout>() < content)
    assertTrue(content < operations.at<RcFloatExpression>())
    assertTrue(operations.at<RcFloatExpression>() < operations.at<RcTextFromFloat>())
    assertTrue(operations.at<RcTextFromFloat>() < operations.at<RcTextData>())
  }

  /** Text made from a touch expression's value is written after it, in its canvas. */
  @Test
  fun textMadeFromATouchExpressionFollowsItInItsCanvas() {
    val operations = written {
      root {
        startCanvas(RemoteModifierData())
        val value = reserveFloatVariable()
        addTouchExpression(Utils.idFromNan(value), 0f, 0f, 1f, 0f, 0, floatArrayOf(value), 0)
        createTextFromFloat(value, 3, 0, 0)
        endCanvas()
      }
    }
    assertTrue(operations.at<RcCanvasLayout>() < operations.at<RcTouchExpression>())
    assertTrue(operations.at<RcTouchExpression>() < operations.at<RcTextFromFloat>())
  }

  /** As AndroidX's writer: the same expression asked for twice is declared twice. */
  @Test
  fun anExpressionIsDeclaredEachTimeItIsAskedFor() {
    lateinit var ids: List<Float>
    val operations = written {
      ids = List(2) { floatExpression(1f, 2f, AnimatedFloatExpression.ADD) }
      assertNotEquals(addInteger(7), addInteger(7))
      // Text is the document's wherever it is written, and is shared.
      assertEquals(addText("a"), addText("a"))
    }
    assertNotEquals(ids[0].toRawBits(), ids[1].toRawBits())
    assertEquals(2, operations.count { it is RcFloatExpression })
    assertEquals(2, operations.count { it is RcIntegerConstant })
    assertEquals(1, operations.count { it is RcTextData })
  }

  /** A global section goes ahead of the root, in the order it was written, from wherever. */
  @Test
  fun aGlobalSectionGoesAheadOfTheRoot() {
    val operations = written {
      addText("before")
      root {
        startBox(RemoteModifierData(), 1, 4)
        beginGlobal()
        floatExpression(1f, 2f, AnimatedFloatExpression.ADD)
        addText("global")
        endGlobal()
        addText("in the box")
        endBox()
      }
    }
    assertEquals(
      listOf("before", "global", "in the box"),
      operations.filterIsInstance<RcTextData>().map { it.text },
    )
    val root = operations.at<RcRootLayout>()
    assertTrue(operations.at<RcFloatExpression>() < root)
    assertTrue(operations.indexOfFirst { it is RcTextData && it.text == "global" } < root)
    assertTrue(operations.indexOfFirst { it is RcTextData && it.text == "in the box" } > root)
  }

  /**
   * What AndroidX's player does not keep among a layout component's operations (anything but text,
   * bitmaps and `ComponentData`) is written ahead of the root when it is declared in a layout, and
   * where it is declared in a canvas, whose drawing a player keeps whole.
   */
  @Test
  fun whatALayoutWouldDropIsWrittenAheadOfTheRootAndWhatACanvasKeepsStays() {
    val operations = written {
      root {
        startBox(RemoteModifierData(), 1, 4)
        addNamedInt("USER:mode", 0)
        addPathData(floatArrayOf(10f, 0f, 0f))
        endBox()
        startCanvas(RemoteModifierData())
        addFloatArray(floatArrayOf(1f, 2f))
        endCanvas()
      }
    }
    val root = operations.at<RcRootLayout>()
    assertTrue(operations.at<RcNamedVariable>() < root)
    assertTrue(operations.at<RcIntegerConstant>() < root)
    assertTrue(operations.at<RcPathData>() < root)
    assertTrue(operations.at<RcFloatList>() > operations.at<RcCanvasLayout>())
  }

  /**
   * One of those kinds that reads what a component declares stays with it: a text cut at a position
   * an expression in the box gives is written after the expression, not ahead of the root, where it
   * would be worked out before the expression it reads.
   */
  @Test
  fun whatReadsAComponentsValueIsNotMovedAheadOfIt() {
    val operations = written {
      root {
        startBox(RemoteModifierData(), 1, 4)
        val months = addText("JanFebMar")
        val start = floatExpression(1f, 3f, AnimatedFloatExpression.MUL)
        textSubtext(months, start, 3f)
        textSubtext(months, 0f, 3f)
        endBox()
      }
    }
    val root = operations.at<RcRootLayout>()
    val cuts = operations.withIndex().filter { it.value is RcTextSubtext }.map { it.index }
    assertEquals(2, cuts.size)
    // The one that reads only the document's own values goes ahead of the root; the text it cuts
    // is the box's, so it too stays.
    assertTrue(cuts.all { it > root }, "$cuts, root at $root")
    assertTrue(operations.at<RcFloatExpression>() < cuts.first())
  }

  /** A function is defined where it is made, whole. */
  @Test
  fun aFunctionIsDefinedWhereItIsMade() {
    val operations = written {
      root {
        startCanvas(RemoteModifierData())
        val args = FloatArray(1)
        createFloatFunction(args)
        floatExpression(args[0], 2f, AnimatedFloatExpression.MUL)
        endFloatFunction()
        endCanvas()
      }
    }
    assertTrue(operations.at<RcCanvasLayout>() < operations.at<RcFloatFunctionDefine>())
    assertTrue(operations.at<RcFloatFunctionDefine>() < operations.at<RcFloatExpression>())
  }

  /**
   * A component's size asked for only after the component has been written (a canvas that draws a
   * bar as wide as the box before it): nothing more can be written in the box, and written in the
   * canvas AndroidX's player would take it for the canvas's size. It is written after the root,
   * naming the box, and AndroidX's `CoreDocument` resolves it to the box's 40 px. (Ahead of the
   * root it finds no component: "Component not found for id".)
   */
  @Test
  fun aSizeAskedForAfterItsComponentIsWrittenAfterTheRootForIt() {
    var box: RemoteComponent? = null
    val bytes = runBlocking {
      captureCommonRemoteDocument(RemoteCreationDisplayInfo(200, 100, 160)) {
        RemoteColumn {
          RemoteBox(RemoteModifier.size(40.rdp, 10.rdp)) { box = remoteComponent() }
          RemoteCanvas(RemoteModifier.size(100.rdp, 20.rdp)) {
            drawRect(
              RemotePaint(),
              RemoteOffset(0f.rf, 0f.rf),
              RemoteSize(checkNotNull(box).width, 5f.rf),
            )
          }
        }
      }
    }
    val operations = RcDocumentCodec.decode(bytes).operations
    val value = operations.filterIsInstance<RcComponentValue>().single()
    assertEquals(operations.lastIndex, operations.indexOf(value))
    assertEquals(operations.filterIsInstance<RcBoxLayout>().single().componentId, value.componentId)
    val bar = androidxPaints(bytes, 200, 100).single { it.name == "rect" }
    assertEquals(listOf(0f, 0f, 40f, 5f), bar.arguments)
  }
}
