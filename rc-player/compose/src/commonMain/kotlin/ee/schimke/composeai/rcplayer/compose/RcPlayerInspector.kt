package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/**
 * A window into a running player: the laid-out component tree, and the live document state behind
 * it.
 *
 * ### Why this exists
 *
 * A player's *pixels* can be checked from outside — render a frame, compare it. Its *layout*
 * cannot: the component ids, the resolved geometry and the expression slots are all interior state,
 * and a host has no way to name them. That gap is not academic. AndroidX's conformance corpus
 * asserts the laid-out tree in 69% of its golds and reads float slots in another 10%, and without a
 * seam like this one a conformance run has to report both as unobservable — which scores the player
 * as failing for a reason that has nothing to do with the player.
 *
 * `Modifier.trackComponentGeometry` already publishes geometry, but only for components the
 * *document* binds a `ComponentValue` to, because that is all the document needs. That is a small
 * minority of nodes and deliberately so; it is not a tree.
 *
 * ### What it is for, and what it is not
 *
 * This is an **observation** API: a harness, a layout inspector, a debugging overlay. It reports
 * what the player did, and nothing here changes what the player draws. It is not a way to drive the
 * player — [state] is exposed because a conformance probe has to read a slot by id or by variable
 * name, not so a host can write one; a host that wants to set values has `namedValues` and the
 * document's own action channels, which go through the invalidation path this does not.
 *
 * ### Threading and lifetime
 *
 * [nodes] is derived on read from the latest geometry of each component, so a reader always sees
 * one entry per component rather than a half-rebuilt list. Read it after a frame has been rendered;
 * read during composition it may hold the previous pass. [state] is set when the player composes
 * and survives until the document changes.
 *
 * Provide one through [LocalRcPlayerInspector]:
 * ```
 * val inspector = remember { RcPlayerInspector() }
 * CompositionLocalProvider(LocalRcPlayerInspector provides inspector) {
 *   RcComposePlayer(document)
 * }
 * ```
 */
public class RcPlayerInspector {
  /**
   * The most recent layout pass, ordered by component id ascending.
   *
   * Ids are descending negatives in creation order, so this is reverse creation order: a node
   * appears *after* all of its descendants. That is the order AndroidX's conformance corpus encodes
   * its trees in, and matching it here saves every consumer from re-deriving it.
   *
   * Only the newest pass is reported. A component that stopped being composed — a `StateLayout`
   * branch that switched away — leaves its last entry behind in [observed], and returning it would
   * describe a tree that is no longer on screen.
   */
  public val nodes: List<RcInspectedNode>
    get() =
      observed.values.filter { it.pass == newestPass }.map { it.node }.sortedBy { it.componentId }

  /**
   * The live document state: float, integer, colour, text and matrix slots, named variables and
   * particle arrays.
   *
   * Null until the player has composed once.
   */
  public var state: RcPlayerState? = null
    internal set

  private class Entry(val node: RcInspectedNode, val pass: Int)

  /**
   * Latest geometry per component id, rather than a list appended to per pass.
   *
   * Compose does not guarantee the order or the completeness of `onGloballyPositioned` callbacks
   * within a pass, so there is no moment at which a list is reliably "finished" and could be
   * committed. Keying by component id sidesteps that: every write is idempotent, a partial pass
   * leaves the previous pass's geometry in place for the nodes it did not reach, and a reader
   * always sees one entry per component.
   */
  private val observed = mutableMapOf<Int, Entry>()

  private var newestPass = 0

  internal fun record(node: RcInspectedNode, pass: Int) {
    if (pass > newestPass) newestPass = pass
    observed[node.componentId] = Entry(node, pass)
  }
}

/**
 * One laid-out component.
 *
 * Position is reported **relative to the player's root**, not to the parent component.
 *
 * That is deliberate, and it is the one thing about this type worth reading twice. A component's
 * Compose position is relative to its immediate *layout node*, and several of this player's layout
 * managers — `CollapsibleRow`/`CollapsibleColumn`, `FitBox`, and any row using `alignBy` — wrap
 * each child in an intermediate node that measures it. A child inside one of those sits at (0, 0)
 * of its wrapper however far across the screen the wrapper was placed, so a parent-relative number
 * is a measurement of the wrapper's internals rather than of the layout. Root-relative has no such
 * ambiguity: it is the same number whatever scaffolding a manager builds.
 *
 * A consumer that wants parent-relative subtracts the parent's position, finding the parent through
 * [depth] — the first later entry whose depth is one less.
 *
 * The geometry of a node whose [isGone] is true is undefined — a gone component is not placed, and
 * two engines will legitimately disagree about where it is not.
 */
public data class RcInspectedNode(
  public val componentId: Int,
  /**
   * The component's class in AndroidX's vocabulary — `BoxLayout`, `ColumnLayout`,
   * `RootLayoutComponent` — rather than this player's Kotlin type name.
   *
   * Deliberately the portable name: the wire format is AndroidX's, so a consumer comparing two
   * players should not have to know that this one calls its box `RcLayoutNode.Box`.
   */
  public val kind: String,
  public val depth: Int,
  /**
   * Root-relative position. See the note on this class before reaching for it as parent-relative.
   */
  public val x: Float,
  public val y: Float,
  public val width: Float,
  public val height: Float,
  public val isGone: Boolean,
  /** AndroidX visibility: 0 gone, 1 visible, 2 invisible but measured. */
  public val visibility: Int,
  public val scrollX: Float = 0f,
  public val scrollY: Float = 0f,
)

/**
 * The inspector the enclosing player reports into, or null when nothing is observing.
 *
 * `static`, because it is read once per component per layout and never changes for the lifetime of
 * a player: a non-static local would invalidate every reader on every provide.
 *
 * Null by default. With no inspector the player allocates nothing and adds no
 * `onGloballyPositioned` to any modifier chain, so an unobserved document does no layout-pass work
 * for this.
 *
 * It is not literally free, and the difference is worth stating rather than rounding away: every
 * component still pays one composition-local read, and `RenderLayoutNode` carries a `depth`
 * parameter that Compose tracks for change regardless. Both are small, neither has been measured,
 * and `:rc-player-profile` is the thing to measure them with before anyone calls it negligible on
 * the record.
 */
public val LocalRcPlayerInspector: ProvidableCompositionLocal<RcPlayerInspector?> =
  staticCompositionLocalOf {
    null
  }
