package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/**
 * Inspection of a running player — the laid-out component tree and the live document state —
 * published through Compose's **semantics** tree.
 *
 * ### Why this exists
 *
 * A player's pixels can be checked from outside: render a frame, compare it. Its layout cannot.
 * Component ids, resolved geometry and expression slots are interior state with no host-visible
 * name, so a harness measuring conformance has to report most of what the corpus asserts as
 * unobservable — which scores the player as failing for reasons that have nothing to do with the
 * player. `Modifier.trackComponentGeometry` already publishes geometry, but only for the components
 * a document binds a `ComponentValue` to, because that is all a *document* can read. That is a
 * small minority of nodes and deliberately so; it is not a tree.
 *
 * ### Why semantics rather than a bespoke channel
 *
 * Semantics is not an accessibility-only tree — it is Compose's general description of what the UI
 * *means*, read by accessibility services, by the testing framework (`fetchSemanticsNode`,
 * `SemanticsMatcher`) and by autofill, with custom [SemanticsPropertyKey]s as its documented
 * extension point. Putting player inspection there rather than in a private side-channel buys three
 * things:
 *
 * * **Geometry for free, pull-based.** `SemanticsNode.positionInRoot`, `size` and `boundsInRoot`
 *   read from the layout node on demand. There is no per-layout callback at all — no
 *   `onGloballyPositioned`, which runs after every layout pass of the whole tree and is the usual
 *   way an inspection seam becomes a performance problem.
 * * **Depth for free.** The semantics tree is a tree, so a reader derives nesting by walking it.
 *   Nothing has to be threaded down through the player's own recursion.
 * * **One reader, many consumers.** A Compose UI test, `ImageComposeScene.semanticsOwners`, and a
 *   standalone harness all read the same data the same way, with no type owned by this module in
 *   between.
 *
 * Custom keys are not mapped into platform accessibility node info, so a screen reader does not see
 * them.
 *
 * ### Cost
 *
 * Gated on [LocalRcInspection], which is false by default: with inspection off no semantics
 * modifier is added and the player behaves exactly as it did before this existed. What every
 * component still pays, always, is one static composition-local read — small, and unmeasured;
 * `:rc-player-profile` is the thing to measure it with before anyone calls it negligible on the
 * record.
 *
 * ### Reading it
 *
 * ```
 * CompositionLocalProvider(LocalRcInspection provides true) { RcComposePlayer(document) }
 * // …after a frame:
 * val root = scene.semanticsOwners.first().unmergedRootSemanticsNode
 * // walk `root.children`, keeping nodes whose config carries `RcComponentIdKey`
 * ```
 *
 * Walk the **unmerged** tree. The merged one folds a subtree's properties into its nearest merging
 * ancestor, which would collapse several components into one entry.
 */
public val LocalRcInspection: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf {
  false
}

/** The Remote Compose component id this node draws — the document's own id, a negative integer. */
public val RcComponentIdKey: SemanticsPropertyKey<Int> = SemanticsPropertyKey("RcComponentId")

/**
 * The component's class in AndroidX's vocabulary — `BoxLayout`, `ColumnLayout`,
 * `RootLayoutComponent` — rather than this player's Kotlin type name.
 *
 * Deliberately the portable name: the wire format is AndroidX's, so a consumer comparing two
 * players should not have to know that this one spells its box `RcLayoutNode.Box`.
 */
public val RcComponentKindKey: SemanticsPropertyKey<String> =
  SemanticsPropertyKey("RcComponentKind")

/**
 * AndroidX visibility: 0 gone, 1 visible, 2 invisible but measured.
 *
 * A gone component still reports, at zero size. Dropping it would make "laid out at nothing" and
 * "not in the tree" the same observation, and they are not.
 */
public val RcComponentVisibilityKey: SemanticsPropertyKey<Int> =
  SemanticsPropertyKey("RcComponentVisibility")

/**
 * How far this component insets its children — its accumulated padding, in pixels.
 *
 * Published because AndroidX's tree encoding needs it and cannot derive it. `x`/`y` there are
 * **parent-relative and reflect only the layout manager's assignment**: modifier-induced
 * translation is applied at paint time and is not in them, so a padded child reports `x: 0` and the
 * padding shows up as the parent being *larger* (`CONFORMANCE_FORMAT.md` §2.7).
 *
 * Compose does not work that way — padding is a layout node, so the child really is placed at the
 * inset. A reader converting to the corpus's convention subtracts the parent's inset from the
 * child's position, which is exactly what this key is for. The component's own reported size stays
 * the outer, padded box, because the semantics node sits outside the padding in the modifier chain.
 */
public val RcContentInsetKey: SemanticsPropertyKey<Offset> = SemanticsPropertyKey("RcContentInset")

/**
 * The live document state, published once on the player's root.
 *
 * Float, integer, colour, text and matrix slots, named variables and particle arrays — the half of
 * the player's behaviour that never reaches the screen and so cannot be checked by rendering. It
 * rides the same channel as the tree so a reader needs one traversal and no second API.
 *
 * This is an **observation** handle. Nothing here changes what the player draws, and it is not the
 * way to drive a document: a host that wants to set values has `namedValues` and the document's own
 * action channels, which go through the invalidation path this deliberately does not.
 */
public val RcDocumentStateKey: SemanticsPropertyKey<RcPlayerState> =
  SemanticsPropertyKey("RcDocumentState")

/** Sets [RcComponentIdKey]. */
public var SemanticsPropertyReceiver.rcComponentId: Int by RcComponentIdKey

/** Sets [RcComponentKindKey]. */
public var SemanticsPropertyReceiver.rcComponentKind: String by RcComponentKindKey

/** Sets [RcComponentVisibilityKey]. */
public var SemanticsPropertyReceiver.rcComponentVisibility: Int by RcComponentVisibilityKey

/**
 * Selects the **closed-form Ahem text model** instead of the real text stack.
 *
 * The model AndroidX's conformance corpus measures its golds with (`CONFORMANCE_FORMAT.md` §2.3):
 * one em of advance per glyph, ascent `0.8em`, descent `0.2em`, greedy word wrap on character
 * counts. Deliberately naive, and not how you would lay out real text even in Ahem — so it is a
 * switch, off by default, for a harness replaying that corpus and nothing else.
 */
public val LocalRcAhemTextMetrics: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf {
  false
}

/** Sets [RcContentInsetKey]. */
public var SemanticsPropertyReceiver.rcContentInset: Offset by RcContentInsetKey

/** Sets [RcDocumentStateKey]. */
public var SemanticsPropertyReceiver.rcDocumentState: RcPlayerState by RcDocumentStateKey
