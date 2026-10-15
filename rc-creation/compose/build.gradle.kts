// PROTOTYPE — moved from yschimke/remote-m3-catalog `vendor/remote-creation-compose` and
// `vendor/remote-foundation` (commonMain only). The legacy JVM writer path and the AndroidX
// `remote-creation` Android integration are deliberately not carried: every target, Android
// included, captures through the common `RemoteWriter`. Not for landing; see
// docs/design/RC_CREATION_BRIDGE.md.
plugins {
    id("composeai.base-conventions")
    id("composeai.jvm-conventions")
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.compose.multiplatform)
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
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
            api(project(":rc-creation-writer"))
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
