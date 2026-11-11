package ee.schimke.composeai.rccreation.bridge

import ee.schimke.composeai.rccreation.common.AnimatedFloatExpression
import ee.schimke.composeai.rccreation.common.RemoteDocumentWriter
import ee.schimke.composeai.rccreation.common.RemoteModifierData
import ee.schimke.composeai.rccreation.common.Utils
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
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
import ee.schimke.composeai.rcplayer.protocol.RcTouchExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
}
