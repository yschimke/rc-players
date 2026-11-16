package ee.schimke.composeai.rcwidget.launcher

import androidx.compose.ui.layout.ContentScale

// What the AndroidX test content writes differently on the pinned Remote Compose release
// (1.0.0-alpha23) than on androidx-main, for the default `release` lane.
// `src/androidHostTestRemoteSnapshot` has androidx-main's for `-Pcomposeai.remoteCompose=snapshot`;
// `build.gradle.kts` compiles one or the other. Delete both once the pinned release has it.

/** `RemoteImage`'s scale: Compose's `ContentScale` in alpha23, `RemoteContentScale` after it. */
internal val AndroidxFillBounds: ContentScale = ContentScale.FillBounds
