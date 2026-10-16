// The Compose Multiplatform creation fork: AndroidX `remote-creation-compose` and
// `remote-foundation`
// (commonMain), in AndroidX's own package, with marked local patches and nothing else. rc-players'
// own creation API lives in `:rc-creation-bridge`. See PROVENANCE.md.
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
        commonMain.dependencies {
            api(project(":third-party-remote-creation-writer"))
            api("androidx.annotation:annotation:1.9.1")
            api(libs.androidx.collection)
            @Suppress("DEPRECATION") api(compose.runtime)
            @Suppress("DEPRECATION") api(compose.ui)
            @Suppress("DEPRECATION") implementation(compose.foundation)
            implementation("androidx.graphics:graphics-shapes:1.1.0")
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

// AndroidX formats with ktfmt's kotlinlang style (4-space), and most of these sources are a copy of
// theirs; formatting them in Google style would turn every upstream refresh into a full rewrite.
// The same choice `third_party/rc-embedded-player` makes.
ktfmt { kotlinLangStyle() }

// The fork carries AndroidX's own class names. On Android a consumer could also resolve AndroidX's
// `remote-creation-compose` (or `remote-foundation`, through it), and two jars defining the same
// classes fail late and confusingly. Declaring AndroidX's capabilities makes Gradle refuse the pair
// at resolution, naming both. The module's own capability is restated, since declaring any replaces
// the implicit one.
val androidxCapabilities =
    listOf(
        "androidx.compose.remote:remote-creation-compose",
        "androidx.compose.remote.foundation:foundation",
    )

configurations.configureEach {
    // AGP publishes Android from separate `…Elements-published` copies that are not consumable
    // within the build, so a name match, not `isCanBeConsumed`, is what reaches every variant.
    if (!isCanBeResolved && name.substringBefore("-published").endsWith("Elements")) {
        outgoing.capability("${project.group}:${project.name}:${project.version}")
        androidxCapabilities.forEach { outgoing.capability("$it:${project.version}") }
    }
}

// The ABI gate, as the player modules have it: a change to the published surface shows up as a
// diff to `api/` in review. Regenerate with `./gradlew updateKotlinAbi`.
tasks.named("check") { dependsOn("checkKotlinAbi") }

composeAiMavenPublishing {
    coordinates(
        artifactId = "third-party-remote-creation-compose",
        displayName = "Remote Compose Creation — Compose Multiplatform (fork)",
        description =
            "AndroidX remote-creation-compose and remote-foundation for Compose Multiplatform, in AndroidX's package with marked local patches. Declares AndroidX's capabilities, so it cannot sit beside them on Android.",
    )
}
