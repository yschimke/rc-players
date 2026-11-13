// Robolectric's Android 15, 16 and 17 platforms, for the host tests that play on the platform's own
// Remote Compose player (`rc-widget/launcher*`), so they run offline. Applied with
// `apply(from = rootProject.file("gradle/robolectric-platforms.gradle.kts"))`.
//
// One configuration each: one would resolve the three versions of the module to the newest.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val robolectricPlatforms =
  listOf("robolectric-sdk35", "robolectric-sdk36", "robolectric-sdk37").mapIndexed { index, alias ->
    configurations.create("robolectricPlatform$index") {
      isCanBeConsumed = false
      isTransitive = false
      dependencies.add(project.dependencies.create(catalog.findLibrary(alias).get().get()))
    }
  }

val prepareRobolectricPlatforms by
  tasks.registering(Sync::class) {
    robolectricPlatforms.forEach { from(it) }
    into(layout.buildDirectory.dir("robolectric-sdk"))
  }

val javaToolchains = extensions.getByType<JavaToolchainService>()

tasks.withType<Test>().configureEach {
  // Robolectric runs Android 16 and later only on Java 21; the library itself stays Java 17.
  javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
  dependsOn(prepareRobolectricPlatforms)
  systemProperty("robolectric.offline", "true")
  // `-Prc.render.out=<dir>` keeps the platform player's renders of the tests that save them.
  providers.gradleProperty("rc.render.out").orNull?.let { systemProperty("rc.render.out", it) }
  systemProperty(
    "robolectric.dependency.dir",
    layout.buildDirectory.dir("robolectric-sdk").get().asFile.absolutePath,
  )
  // Robolectric's Android 16 shares memory through FileDescriptor internals JDK 17+ hides.
  jvmArgs(
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
  )
}
