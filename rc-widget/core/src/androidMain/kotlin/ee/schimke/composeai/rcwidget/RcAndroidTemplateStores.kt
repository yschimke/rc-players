package ee.schimke.composeai.rcwidget

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/** Template stores for an Android app (a phone's or a watch's). */
public object RcAndroidTemplateStores {
  /**
   * The app's capture cache: under `files/<directory>`, scoped to this install's update time (which
   * changes with every build installed, and so with the app's own composables) and the display's
   * density and font scale. Call [RcFileTemplateStore.prune] once at start.
   */
  public fun captures(context: Context, directory: String = "rc-widget/templates"): RcFileTemplateStore {
    val app = context.applicationContext
    val installed by lazy {
      runCatching { app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime }
        .getOrDefault(0L)
    }
    return RcFileTemplateStore(File(app.filesDir, directory)) {
      val configuration = app.resources.configuration
      "$installed-${configuration.densityDpi}-${configuration.fontScale}"
    }
  }

  /** Templates bundled in [assets] under [directory] ([RcBundledTemplates.layout]). */
  public fun bundled(assets: AssetManager, directory: String = "rc-widget-templates"): RcBundledTemplates =
    RcBundledTemplates { path -> runCatching { assets.open("$directory/$path").use { it.readBytes() } }.getOrNull() }
}
