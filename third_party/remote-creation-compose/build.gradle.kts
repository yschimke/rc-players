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
            api("androidx.annotation:annotation:1.11.0")
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
// at resolution, naming both. AndroidX publishes those only for Android, so only the Android
// variants declare them.
//
// Declaring any capability replaces the implicit one, so the Android variants restate theirs: the
// coordinates of the module they are published in, `…-android`, which is what a consumer resolving
// through the root module's `available-at` asks for. Restating the root's coordinates instead
// (2.13.0)
// left no variant a consumer could select, on any platform.
val androidxCapabilities =
    listOf(
        "androidx.compose.remote:remote-creation-compose",
        "androidx.compose.remote.foundation:foundation",
    )

configurations.configureEach {
    // AGP publishes Android from separate `…Elements-published` copies that are not consumable
    // within the build, so a name match, not `isCanBeConsumed`, is what reaches every variant.
    if (
        !isCanBeResolved &&
            name.startsWith("android") &&
            name.substringBefore("-published").endsWith("Elements")
    ) {
        outgoing.capability("${project.group}:${project.name}-android:${project.version}")
        androidxCapabilities.forEach { outgoing.capability("$it:${project.version}") }
    }
}

// Every published variant must offer the capability of the module it is published in, or no
// consumer can select it. Checked against the Gradle module metadata the publications write.
val verifyPublishedCapabilities by tasks.registering {
    group = "verification"
    description = "Checks every published variant offers its own module's capability."
    val metadataTasks = tasks.matching { it.name.startsWith("generateMetadataFileFor") }
    dependsOn(metadataTasks)
    val publications = layout.buildDirectory.dir("publications")
    val group = project.group.toString()
    // Each publication writes `publications/<name>/module.json`; the module it describes is the
    // publication's own artifact (a platform's `component` names the root module instead).
    val modules =
        project.extensions
            .getByType<PublishingExtension>()
            .publications
            .withType<MavenPublication>()
            .associate { it.name to it.artifactId }
    inputs.property("modules", modules)
    doLast {
        check(modules.isNotEmpty()) { "No Maven publications to check" }
        val problems = mutableListOf<String>()
        modules.forEach { (publication, artifactId) ->
            val file = publications.get().file("$publication/module.json").asFile
            check(file.isFile) { "No Gradle module metadata for $publication at $file" }
            @Suppress("UNCHECKED_CAST")
            val module = groovy.json.JsonSlurper().parse(file) as Map<String, Any?>
            val own = "$group:$artifactId"
            (module["variants"] as List<*>?).orEmpty().forEach { entry ->
                val variant = entry as Map<*, *>
                if (variant["available-at"] != null) return@forEach
                val capabilities =
                    (variant["capabilities"] as List<*>?)?.map {
                        val capability = it as Map<*, *>
                        "${capability["group"]}:${capability["name"]}"
                    }
                // No capabilities means the implicit one, which is the module's own.
                if (capabilities != null && own !in capabilities) {
                    problems += "$own ${variant["name"]} offers $capabilities"
                }
            }
        }
        check(problems.isEmpty()) {
            "Published variants without their module's capability ($group):\n" +
                problems.joinToString("\n")
        }
    }
}

tasks.named("check") { dependsOn(verifyPublishedCapabilities) }

// And before any upload: a release with unselectable variants cannot be taken back.
tasks
    .matching { it.name.startsWith("publish") }
    .configureEach { dependsOn(verifyPublishedCapabilities) }

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
