package ee.schimke.composeai.rcplayer.runtime

import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcLoopOperation
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTheme
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The document ROOT is a content scope, like the layout and canvas scopes nested inside it.
 *
 * `applyLayoutContentStateOperations` used to start with `applyDirect = false`, so an operation
 * declared at top level — legal, and what AndroidX executes first, since it runs one flat operation
 * list in wire order — was never evaluated. Its out id stayed unset, `color(outId)` answered 0, and
 * anything reading it drew fully transparent.
 *
 * The reason that is worth a test rather than a comment: it is **invisible from both ends**.
 * `composeSupportReport` counts a `ColorExpression`'s `outId` as a declared colour, so the document
 * passes the renderability gate; and transparent text on a coloured container reads as a rendering
 * bug in the text, not as an unevaluated colour. Same shape as the canvas-scope bug
 * `applyLayoutContentStateOperations` already records, one level up.
 */
class RcTopLevelContentStateTest {

  private val opaqueTeal = 0xFF008080.toInt()

  private fun documentWithTopLevelExpression(): RcDocument {
    val operations = mutableListOf<RcOperation>()
    operations += RcColorConstant(40, opaqueTeal)
    // Declared at top level rather than inside the RootLayout below.
    //
    // `ID_ID_INTERPOLATE`, not mode 0: mode 0 is COLOR_COLOR_INTERPOLATE, where `first`/`second`
    // are literal ARGB rather than ids. Under that mode this expression computes 0x00000028 from
    // the raw number 40 — non-zero, and still fully transparent — so a bare `assertNotEquals(0, …)`
    // would pass while proving nothing about ID-backed evaluation, which is the whole mechanism
    // under test. Reading a stored colour id is what top-level evaluation has to get right.
    operations +=
      RcColorExpression(
        outId = 41,
        modeAndAlpha = RcColorExpression.ID_ID_INTERPOLATE,
        first = 40,
        second = 40,
        third = 0,
      )
    operations += RcRootLayout(1)
    operations += RcLayoutContent(2)
    operations += RcNoArg(RcOpcodes.CONTAINER_END)
    operations += RcNoArg(RcOpcodes.CONTAINER_END)
    return RcDocument(RcHeader(RcVersion(0, 1, 0)), operations)
  }

  @Test
  fun aTopLevelColorExpressionIsEvaluatedRatherThanLeftAtZero() {
    val document = documentWithTopLevelExpression()
    val state = RcPlayerState(document)

    state.applyLayoutContentStateOperations(
      RcDocumentLinker.link(document).operations,
      RcTheme.UNSPECIFIED,
    )

    // The EXACT value, not merely non-zero. Interpolating a colour with itself is that colour, so
    // an evaluated expression can only be `opaqueTeal` — and asserting it pins that the id was
    // actually READ, rather than that something was written. `assertNotEquals(0, …)` would be
    // satisfied by an expression resolving its inputs as raw numbers into some other transparent
    // colour, which is exactly the false pass this test exists to avoid.
    assertEquals(opaqueTeal, state.color(40), "the literal it reads from")
    assertEquals(opaqueTeal, state.color(41), "the top-level expression was never evaluated")
  }

  /**
   * AndroidX's level 6 widget writer (`addThemedColor`) sets a themed colour from two theme
   * sections, each a one-pass loop over a `ColorExpression`. The loop is what makes AndroidX run
   * the expression as it draws, under the theme in force; a replay that skipped loop bodies left
   * the colour unset.
   */
  @Test
  fun aThemedColorSetFromOnePassLoopsFollowsTheTheme() {
    val dark = 0xFF202020.toInt()
    val light = 0xFFE0E0E0.toInt()
    fun section(theme: Int, tween: Float): List<RcOperation> =
      listOf(
        RcTheme(theme),
        RcLoopOperation(
          0,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(1f),
          RcFloatWord.literal(1f),
        ),
        RcColorExpression(
          42,
          RcColorExpression.COLOR_COLOR_INTERPOLATE,
          dark,
          light,
          tween.toRawBits(),
        ),
        RcNoArg(RcOpcodes.CONTAINER_END),
      )
    val document =
      RcDocument(
        RcHeader(RcVersion(0, 1, 0)),
        section(RcTheme.DARK, 0f) +
          section(RcTheme.LIGHT, 1f) +
          listOf(
            RcTheme(RcTheme.UNSPECIFIED),
            RcRootLayout(1),
            RcLayoutContent(2),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcNoArg(RcOpcodes.CONTAINER_END),
          ),
      )
    for ((theme, expected) in listOf(RcTheme.DARK to dark, RcTheme.LIGHT to light)) {
      val state = RcPlayerState(document)
      state.applyLayoutContentStateOperations(RcDocumentLinker.link(document).operations, theme)
      assertEquals(expected, state.color(42), "theme $theme")
    }
  }

  /**
   * Only the themed-colour shape is replayed, and only under its own theme. Any other loop is the
   * draw pass's alone, so a stateful body (an index, random numbers, registers) never runs twice.
   */
  @Test
  fun onlyAThemedColorLoopUnderItsOwnThemeIsReplayed() {
    val colour = 0xFF202020.toInt()
    fun play(theme: Int, vararg operations: RcOperation): RcPlayerState {
      val document =
        RcDocument(
          RcHeader(RcVersion(0, 1, 0)),
          operations.toList() +
            listOf(
              RcTheme(RcTheme.UNSPECIFIED),
              RcRootLayout(1),
              RcLayoutContent(2),
              RcNoArg(RcOpcodes.CONTAINER_END),
              RcNoArg(RcOpcodes.CONTAINER_END),
            ),
        )
      return RcPlayerState(document).apply {
        applyLayoutContentStateOperations(RcDocumentLinker.link(document).operations, theme)
      }
    }
    fun loop(index: Int) =
      RcLoopOperation(
        index,
        RcFloatWord.literal(5f),
        RcFloatWord.literal(1f),
        RcFloatWord.literal(6f),
      )
    val expression =
      RcColorExpression(42, RcColorExpression.COLOR_COLOR_INTERPOLATE, colour, colour, 0)
    val end = RcNoArg(RcOpcodes.CONTAINER_END)

    assertEquals(
      colour,
      play(RcTheme.DARK, RcTheme(RcTheme.DARK), loop(0), expression, end).color(42),
    )
    // Under the other theme's marker.
    assertEquals(0, play(RcTheme.LIGHT, RcTheme(RcTheme.DARK), loop(0), expression, end).color(42))
    // A loop that writes an index is not the themed-colour shape: the replay leaves it alone.
    val indexed = play(RcTheme.DARK, loop(43), expression, end)
    assertEquals(0, indexed.color(42))
    val index = RcFloatWord(0x7fc00000 or 43)
    assertEquals(index.value.toRawBits(), indexed.resolve(index).toRawBits())
  }
}
