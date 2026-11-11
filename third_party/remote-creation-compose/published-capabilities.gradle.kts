// Applied by the three creation modules beside this file (`expression`, `compose`, `foundation`).
//
// They are in rc-players' own package (`ee.schimke.composeai.rccreation`) and declare no capability
// of AndroidX's, so each can be on one classpath with AndroidX's artifacts. What is still checked is
// Gradle's own rule, which a module that once declared extra capabilities got wrong (2.13.0).

// Every published variant must offer the capability of the module it is published in, or no
// consumer can select it. Checked against the Gradle module metadata the publications write.
val verifyPublishedCapabilities by
    tasks.registering {
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
