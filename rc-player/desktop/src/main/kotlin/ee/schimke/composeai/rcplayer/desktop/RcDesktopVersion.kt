package ee.schimke.composeai.rcplayer.desktop

/** The release this build was cut for, stamped into the jar manifest by the build. */
internal object RcDesktopVersion {
  val value: String
    get() = RcDesktopVersion::class.java.`package`?.implementationVersion ?: "dev"
}
