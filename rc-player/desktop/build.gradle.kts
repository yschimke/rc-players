import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

// `:rc-player-desktop` — the Linux desktop viewer: the CMP player in a window, opening local `.rc`
// files. It is the JVM counterpart of `samples/macos-player`, and like that app it is a release
// utility rather than a library: nothing here publishes to Maven Central. The release job attaches
// what `packageRcDesktopLinux` builds — a `.deb` and a self-contained `tar.gz`, both bundling a JRE
// — to the GitHub Release.
//
// `./gradlew :rc-player-desktop:run --args="path/to/doc.rc"` opens it from a checkout.

// jpackage needs MAJOR.MINOR.PATCH, so the release passes the tag's version; a local build falls
// back to the version release-please last recorded.
val viewerVersion: String =
  providers.gradleProperty("rc.desktop.version").orNull
    ?: Regex("\"\\.\"\\s*:\\s*\"([0-9]+\\.[0-9]+\\.[0-9]+)\"")
      .find(rootProject.file(".release-please-manifest.json").readText())
      ?.groupValues
      ?.get(1)
    ?: error("No version: pass -Prc.desktop.version or fix .release-please-manifest.json")

dependencies {
  implementation(project(":rc-player-compose"))
  implementation(project(":rc-player-protocol"))
  implementation(project(":rc-player-runtime"))

  @Suppress("DEPRECATION") implementation(compose.desktop.currentOs)
  @Suppress("DEPRECATION") implementation(compose.runtime)
  @Suppress("DEPRECATION") implementation(compose.foundation)
  @Suppress("DEPRECATION") implementation(compose.ui)

  testImplementation(libs.junit)
  testImplementation(kotlin("test"))
}

tasks.jar { manifest { attributes("Implementation-Version" to viewerVersion) } }

tasks.test {
  // The player's own fixtures, read in place rather than copied into a second resources tree.
  systemProperty(
    "rc.fixtures",
    rootProject.file("rc-player/compose/src/jvmTest/resources/rc-fixtures").absolutePath,
  )
  systemProperty("java.awt.headless", "true")
}

compose.desktop {
  application {
    mainClass = "ee.schimke.composeai.rcplayer.desktop.RcDesktopMainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Deb)
      packageName = "remote-compose-player"
      packageVersion = viewerVersion
      description = "Remote Compose (.rc) document viewer"
      vendor = "rc-players"
      licenseFile.set(rootProject.file("LICENSE"))
      // `java.desktop` for AWT/Swing, `jdk.unsupported` for Skiko; the rest is what the player's
      // coroutines and Coil's HTTP client reach for.
      modules("java.net.http", "jdk.unsupported", "java.naming")
      linux {
        packageName = "remote-compose-player"
        debMaintainer = "rc-players@users.noreply.github.com"
        menuGroup = "Development"
        appCategory = "Development"
        shortcut = true
      }
    }
  }
}

// The two Linux release assets, named without a version so a download link can be written once
// per release pattern. The tarball is `createDistributable`'s app image (launcher + bundled JRE).
val rcDesktopDistributions = layout.buildDirectory.dir("rc-desktop-dist")

val packageRcDesktopTarball by
  tasks.registering(Tar::class) {
    description = "Tar the self-contained Linux app image."
    group = "distribution"
    // `createDistributable` writes `<app dir>/remote-compose-player/{bin,lib}`, so the archive's
    // top-level directory comes from there and the README joins it.
    from(tasks.named("createDistributable"))
    from(file("distribution/README.txt")) { into("remote-compose-player") }
    compression = Compression.GZIP
    // Gradle 9 archives every file 0644 by default, which would strip the execute bit from the
    // launcher and the bundled JRE's helpers (`jspawnhelper`); keep what jpackage wrote.
    useFileSystemPermissions()
    archiveFileName.set("RemoteComposePlayer-linux-x64.tar.gz")
    destinationDirectory.set(rcDesktopDistributions)
  }

val packageRcDesktopDeb by
  tasks.registering(Copy::class) {
    description = "Copy the .deb under a version-free release asset name."
    group = "distribution"
    from(tasks.named("packageDeb")) { include("*.deb") }
    into(rcDesktopDistributions)
    rename { "RemoteComposePlayer-linux-amd64.deb" }
  }

tasks.register("packageRcDesktopLinux") {
  description = "Build the Linux viewer's release assets: a .deb and a self-contained tar.gz."
  group = "distribution"
  dependsOn(packageRcDesktopTarball, packageRcDesktopDeb)
}
