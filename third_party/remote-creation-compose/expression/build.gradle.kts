// `:rc-creation-expression`: AndroidX `remote-creation-expression` for Compose Multiplatform, the
// remote state types (`RemoteFloat`, `RemoteColor`, `RemoteState`, …), their expressions and the
// creation state that lowers them, in the package `ee.schimke.composeai.rccreation.compose` with
// marked local patches. Upstream's module is the JVM's alone; this one compiles for every
// rc-players target. See ../PROVENANCE.md.
plugins {
    id("composeai.base-conventions")
    id("composeai.jvm-conventions")
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("composeai.maven-publishing")
    alias(libs.plugins.compose.multiplatform)
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()
    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

    android {
        namespace = "ee.schimke.composeai.rccreation.expression"
        compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
        minSdk = libs.versions.rc.player.minSdk.get().toInt()
    }

    sourceSets {
        // The files with an AndroidX counterpart sit under `upstream/`, each at its upstream path:
        // the module and source root it comes from, then the package path with the fork's package
        // in place of AndroidX's. `src/` holds what the fork adds. See ../PROVENANCE.md.
        commonMain {
            kotlin.srcDir("upstream/remote-creation-expression/src/main/java")
            dependencies {
                api(project(":rc-creation-writer"))
                api("androidx.annotation:annotation:1.11.0")
                api(libs.androidx.collection)
                // Upstream needs `runtime-annotation` alone. This module is still built with the
                // Compose compiler, as it was inside the one module the fork used to be, so its
                // classes keep the stability metadata they were published with.
                @Suppress("DEPRECATION") api(compose.runtime)
            }
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        // AndroidX's own tests of `remote-creation-expression`, at their upstream path. JVM only:
        // they are JUnit 4 and Truth, and read what they wrote with AndroidX's reader
        // (`remote-core`, which comes with `remote-creation-core`).
        jvmTest {
            kotlin.srcDir("upstream/remote-creation-expression/src/test/java")
            dependencies {
                implementation(libs.junit)
                implementation(libs.truth)
                implementation(libs.test.parameter.injector)
                implementation(libs.compose.remote.creation.core)
            }
        }
    }
}

// AndroidX formats with ktfmt's kotlinlang style (4-space), and most of these sources are a copy of
// theirs; formatting them in Google style would turn every upstream refresh into a full rewrite.
// The same choice `third_party/rc-embedded-player` makes.
ktfmt { kotlinLangStyle() }

// Every published variant offers its own module's capability, and none of AndroidX's.
apply(from = "../published-capabilities.gradle.kts")

// The ABI gate, as the player modules have it: a change to the published surface shows up as a
// diff to `api/` in review. Regenerate with `./gradlew updateKotlinAbi`.
tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
    coordinates(
        artifactId = "rc-creation-expression",
        displayName = "Remote Compose Creation — Expressions (Compose Multiplatform)",
        description =
            "AndroidX remote-creation-expression for Compose Multiplatform: the remote state types, their expressions and the creation state, in the package ee.schimke.composeai.rccreation.compose, so it can sit beside AndroidX's own artifacts.",
    )
}
