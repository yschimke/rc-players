plugins {
  id("composeai.base-conventions")
  id("composeai.jvm-conventions")
  alias(libs.plugins.kotlin.jvm)
}

// `:rc-codegen` — rc-players' code generation over AndroidX's Remote Compose schema, forked in
// `third_party/remote-generator`. It defines this repository's own targets beside upstream's
// languages, and owns the generated sources the repository commits:
//
//  * `Sources/RcCreation/RemoteWireConstants.swift` (target `rc-swift-wire-constants`)
//  * `Sources/RcWireOperations/RemoteComposeOperations.swift` (target `swift-portable`: upstream's
//    Swift bindings without Foundation)
//
// `./gradlew :rc-codegen:generateSwiftSources` rewrites them; `check` fails while any is stale.
// Build-only: nothing here publishes.

dependencies {
  implementation(project(":third-party-remote-generator"))
  testImplementation(libs.junit)
}

val codegenMain = "ee.schimke.composeai.rccodegen.RcCodegenMain"
val repositoryRoot: String = rootDir.absolutePath

val generateSwiftSources by
  tasks.registering(JavaExec::class) {
    group = "codegen"
    description = "Regenerates the Swift sources rc-codegen owns, in place."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(codegenMain)
    args("--sync=$repositoryRoot")
    outputs.upToDateWhen { false }
  }

val checkSwiftSources by
  tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Fails if a committed Swift source differs from what rc-codegen generates."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(codegenMain)
    args("--sync=$repositoryRoot", "--check")
    inputs.files(
      fileTree(repositoryRoot) {
        include(
          "Sources/RcCreation/RemoteWireConstants.swift",
          "Sources/RcWireOperations/**",
          "rc-player/protocol/src/main/rc-operations.manifest",
        )
      },
    )
    val marker = layout.buildDirectory.file("checkSwiftSources.ok")
    outputs.file(marker)
    doLast { marker.get().asFile.writeText("ok\n") }
  }

tasks.named("check") { dependsOn(checkSwiftSources) }
