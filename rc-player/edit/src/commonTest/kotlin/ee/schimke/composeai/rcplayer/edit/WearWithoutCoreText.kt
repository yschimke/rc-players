package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcOpcodes

/**
 * Wear widgets as AndroidX's own list has them, without core text (b/485509245): the target the
 * core-text conversions are tested against, now that [RcTargetProfiles.WEAR_WIDGETS] carries it.
 */
internal val WearWithoutCoreText: RcTargetProfile =
  RcTargetProfiles.WEAR_WIDGETS.without(
    RcOpcodes.CORE_TEXT,
    RcOpcodes.TEXT_STYLE,
    name = "Wear widgets, without core text",
  )
