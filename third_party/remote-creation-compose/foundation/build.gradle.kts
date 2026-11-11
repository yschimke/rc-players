// `:rc-creation-foundation`: AndroidX Remote Compose `foundation` for Compose Multiplatform,
// `RemoteBasicText`, `RemoteBasicImage`, `RemoteBasicIcon` and `RemoteSpacer`, in the package
// `ee.schimke.composeai.rccreation.foundation`. Upstream's module is Android's alone; its four
// files are carried unchanged apart from their package. See ../PROVENANCE.md.
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
        namespace = "ee.schimke.composeai.rccreation.foundation"
        compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
        minSdk = libs.versions.rc.player.minSdk.get().toInt()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("upstream/foundation/foundation/src/main/java")
            dependencies {
                api(project(":rc-creation-compose"))
                @Suppress("DEPRECATION") api(compose.runtime)
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
        artifactId = "rc-creation-foundation",
        displayName = "Remote Compose Creation — Foundation (Compose Multiplatform)",
        description =
            "AndroidX Remote Compose foundation for Compose Multiplatform: RemoteBasicText, RemoteBasicImage, RemoteBasicIcon and RemoteSpacer over rc-creation-compose, in the package ee.schimke.composeai.rccreation.foundation.",
    )
}
