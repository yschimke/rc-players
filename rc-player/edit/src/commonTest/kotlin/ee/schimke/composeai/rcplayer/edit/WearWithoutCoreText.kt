package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcOpcodes

/**
 * Wear widgets as AndroidX's own list has them, without core text (b/485509245): the target the
 * core-text conversions are tested against, now that [RcTargetProfiles.WEAR_WIDGETS] carries it.
 */
internal val WearWithoutCoreText: RcTargetProfile =
  RcTargetProfile(
    name = "Wear widgets, without core text",
    apiLevel = RcTargetProfiles.WEAR_WIDGETS.apiLevel,
    profiles = RcTargetProfiles.WEAR_WIDGETS.profiles,
    supportedOpcodes =
      RcTargetProfiles.WEAR_WIDGETS.supportedOpcodes - RcOpcodes.CORE_TEXT - RcOpcodes.TEXT_STYLE,
    fixedOperations = true,
  )
