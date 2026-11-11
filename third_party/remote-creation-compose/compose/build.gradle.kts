// `:rc-creation-compose`: AndroidX `remote-creation-compose` for Compose Multiplatform, the
// `@RemoteComposable` layouts, modifiers, actions, canvas and capture, in the package
// `ee.schimke.composeai.rccreation.compose` with marked local patches and nothing else. The state
// types are `:rc-creation-expression`'s, the basic text and image components
// `:rc-creation-foundation`'s, as upstream divides them. rc-players' own creation API lives in
// `:rc-creation-bridge`. See ../PROVENANCE.md.
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
        namespace = "ee.schimke.composeai.rccreation.compose"
        compileSdk = libs.versions.rc.player.compileSdk.get().toInt()
        minSdk = libs.versions.rc.player.minSdk.get().toInt()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withWasmJs()
                withApple()
            }
        }
    }

    sourceSets {
        // The files with an AndroidX counterpart sit under `upstream/`, each at its upstream path:
        // the module and source set it comes from, then the package path with the fork's package
        // in place of AndroidX's. Both roots compile into `commonMain`, `androidMain`'s too: what
        // the fork carries from there needs only Compose UI, which Compose Multiplatform has on
        // every target. `src/` holds what the fork adds. See ../PROVENANCE.md.
        commonMain {
            kotlin.srcDir("upstream/remote-creation-compose/src/jvmAndAndroidMain/kotlin")
            kotlin.srcDir("upstream/remote-creation-compose/src/androidMain/kotlin")
        }
        commonMain.dependencies {
            // As upstream: `remote-creation-compose` -> `remote-creation-expression` -> the writer.
            api(project(":rc-creation-expression"))
            api("androidx.annotation:annotation:1.11.0")
            api(libs.androidx.collection)
            @Suppress("DEPRECATION") api(compose.runtime)
            @Suppress("DEPRECATION") api(compose.ui)
            @Suppress("DEPRECATION") implementation(compose.foundation)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
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
        artifactId = "rc-creation-compose",
        displayName = "Remote Compose Creation — Compose (Compose Multiplatform)",
        description =
            "AndroidX remote-creation-compose for Compose Multiplatform: @RemoteComposable layouts, modifiers, actions, canvas and capture for the JVM, Android, iOS, macOS and Wasm, in the package ee.schimke.composeai.rccreation.compose, so it can sit beside AndroidX's own artifacts.",
    )
}
