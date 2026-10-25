pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        val validationRepository = providers.gradleProperty("validationRepository").orNull
        if (validationRepository != null) maven { url = uri(validationRepository) }
        google()
        mavenCentral()
    }
}
rootProject.name = "independent-consumer"
include(":app")
