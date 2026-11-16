// `:rc-creation-wear-material3`: AndroidX Remote Compose Wear Material 3 (`wear/compose/remote/
// remote-material3`) for Compose Multiplatform, in the package
// `ee.schimke.composeai.rccreation.wear.material3`. Upstream's module is Android's alone and reads
// a few Wear Compose Material 3 types (text configuration, the default colour scheme, shapes,
// typography and motion); the fork carries those as KMP patches rather than depending on Wear
// Compose, which is Android's alone. See ../PROVENANCE.md.
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
        namespace = "ee.schimke.composeai.rccreation.wear.material3"
        compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
        minSdk = libs.versions.rc.player.minSdk.get().toInt()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("upstream/wear/compose/remote/remote-material3/src/main/java")
            dependencies {
                api(project(":rc-creation-foundation"))
                api(project(":rc-creation-compose"))
                @Suppress("DEPRECATION") api(compose.runtime)
                @Suppress("DEPRECATION") api(compose.ui)
            }
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        getByName("jvmTest").dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // The player's reader, to read back what the components write.
            implementation(project(":rc-player-protocol"))
        }
    }
}

// AndroidX formats with ktfmt's kotlinlang style (4-space), and most of these sources are a copy of
// theirs; formatting them in Google style would turn every upstream refresh into a full rewrite.
ktfmt { kotlinLangStyle() }

// Every published variant offers its own module's capability, and none of AndroidX's.
apply(from = "../published-capabilities.gradle.kts")

// The ABI gate: a change to the published surface shows up as a diff to `api/` in review.
// Regenerate with `./gradlew updateKotlinAbi`.
tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
    coordinates(
        artifactId = "rc-creation-wear-material3",
        displayName = "Remote Compose Creation — Wear Material 3 (Compose Multiplatform)",
        description =
            "AndroidX Remote Compose Wear Material 3 for Compose Multiplatform: RemoteMaterialTheme, buttons, cards, progress indicators, RemoteText and the rest of wear/compose/remote/remote-material3 over rc-creation-compose and rc-creation-foundation, in the package ee.schimke.composeai.rccreation.wear.material3.",
    )
}
