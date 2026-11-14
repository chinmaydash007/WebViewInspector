import com.diffplug.spotless.LineEnding
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.publishing) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.spotless)
}

spotless {
    encoding("UTF-8")
    lineEndings = LineEnding.UNIX
    val excludedTargets =
        arrayOf(
            "**/build/**",
            "**/generated/**",
            "**/.gradle/**",
            "**/.kotlin/**",
            "**/.tools/**",
            "**/.idea/**",
            "**/.git/**",
        )

    kotlin {
        target("**/*.kt")
        targetExclude(*excludedTargets)
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(*excludedTargets)
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    java {
        target("**/*.java")
        targetExclude(*excludedTargets)
        googleJavaFormat(libs.versions.gjf.get()).aosp()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}
tasks.register("stageRelease") {
    description = "Stage both release publications locally; never uploads to Central."
    dependsOn(":webview-inspector:publishMavenPublicationToValidationRepository")
    dependsOn(":webview-inspector-no-op:publishMavenPublicationToValidationRepository")
}

subprojects {
    val module = this
    pluginManager.withPlugin("com.vanniktech.maven.publish.base") {
        module.extensions.configure<MavenPublishBaseExtension> {
            configure(
                AndroidSingleVariantLibrary(
                    variant = "release",
                    sourcesJar = true,
                    publishJavadocJar = false,
                ),
            )
            coordinates(module.group.toString(), module.name, module.version.toString())
            publishToMavenCentral()
            if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
            pom {
                name.set(if (module.name.endsWith("no-op")) "WebView Inspector No-op" else "WebView Inspector")
                description.set(
                    "An Android floating WebView URL and navigation history inspector with a matching production no-op facade.",
                )
                url.set("https://github.com/chinmaydash007/WebViewInspector")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set(
                            "repo",
                        )
                    }
                }
                developers {
                    developer {
                        id.set("chinmaydash007")
                        name.set("chinmaydash007")
                        url.set(
                            "https://github.com/chinmaydash007",
                        )
                    }
                }
                scm {
                    url.set("https://github.com/chinmaydash007/WebViewInspector")
                    connection.set("scm:git:https://github.com/chinmaydash007/WebViewInspector.git")
                    developerConnection.set("scm:git:ssh://git@github.com/chinmaydash007/WebViewInspector.git")
                }
            }
        }
        val apiDocsJar =
            module.tasks.register<Jar>("apiDocsJar") {
                dependsOn("dokkaGeneratePublicationHtml")
                archiveClassifier.set("javadoc")
                from(layout.buildDirectory.dir("dokka/html"))
                from(rootProject.file("USAGE.md")) { rename { "README.md" } }
                from(rootProject.file("LICENSE"))
                from(rootProject.file("NOTICE"))
                from(rootProject.file("CONTRIBUTING.md"))
                from(rootProject.file("AGENTS.md"))
                from(rootProject.file("VALIDATION.md"))
                from(rootProject.file("docs")) { into("docs") }
                from(rootProject.file("webview-inspector/docs")) { into("webview-inspector/docs") }
                isPreserveFileTimestamps = false
                isReproducibleFileOrder = true
            }
        module.extensions.configure<PublishingExtension> {
            repositories.maven {
                name = "Validation"
                url =
                    rootProject.layout.buildDirectory
                        .dir("validation-repository")
                        .get()
                        .asFile
                        .toURI()
            }
            publications.withType<MavenPublication>().configureEach { artifact(apiDocsJar) }
        }
        module.tasks.withType<Jar>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            if (name == "sourceReleaseJar" || name.contains("sources", ignoreCase = true)) {
                from(rootProject.file("USAGE.md")) { rename { "README.md" } }
                from(rootProject.file("LICENSE"))
                from(rootProject.file("NOTICE"))
                from(rootProject.file("CONTRIBUTING.md"))
                from(rootProject.file("AGENTS.md"))
                from(rootProject.file("VALIDATION.md"))
                from(rootProject.file("docs")) { into("docs") }
                from(rootProject.file("webview-inspector/docs")) { into("webview-inspector/docs") }
            }
        }
    }
}
