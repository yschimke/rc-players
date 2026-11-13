package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocument

/**
 * This document as it plays on one display, with as much as that display fixes hardcoded:
 * [hardcodeDensity] at [density] and [fontScale], every named value outside [bound] frozen at the
 * document's own ([hardcodeUnboundNamedValues]), the state layouts that leaves constant collapsed
 * ([collapseConstantStateLayouts]) and the result [optimize]d, which folds the expressions those
 * constants feed.
 *
 * What a host that plays many cards of one template does once per template and display, off the
 * main thread, so that every card evaluates fewer expressions. [bound] is every name a card may
 * still bind; by default every `USER:` name the document declares, so nothing a host could set is
 * frozen. Like [hardcodeDensity], the result is right only at [density] and [fontScale].
 */
public fun RcDocument.forDisplay(
  density: Float,
  fontScale: Float = 1f,
  bound: Set<String> = namedVariables.map { it.name }.filter { it.startsWith("USER:") }.toSet(),
): RcDocument =
  hardcodeDensity(density, fontScale)
    .hardcodeUnboundNamedValues(bound)
    .collapseConstantStateLayouts()
    .optimize()
